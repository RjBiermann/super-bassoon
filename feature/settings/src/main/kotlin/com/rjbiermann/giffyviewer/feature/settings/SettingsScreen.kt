package com.rjbiermann.giffyviewer.feature.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.model.VideoQuality
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.auth.AuthSection
import com.rjbiermann.giffyviewer.feature.auth.AuthViewModel
import com.rjbiermann.giffyviewer.feature.auth.WebViewLoginScreen
import kotlinx.coroutines.launch

/**
 * Issue #17: Settings organized into sub-pages, all in-screen (no NavHost).
 * Root = the account block + one caption row per panel; entering a panel
 * replaces the whole list content, BACK (system key or the top-bar button)
 * pops the panel first — the host `onBack` only fires at root, unchanged.
 *
 * The blocking inventory lives in the Blocked panel (design §1): creators,
 * tags, keywords, favorited creators + the never-rendered favorited tags (M2)
 * and the BLOCKED tag-bundle custom feeds (M1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** TV has a fixed grid — hide the mobile-only Grid columns row. */
    showGridColumns: Boolean = true,
    /** Autoplay in feed is a mobile 1-col-feed surface — hidden on TV
     *  (same gate idiom as showGridColumns). */
    showFeedAutoplay: Boolean = true,
    /** TV: the first row takes D-pad focus on entry (mobile touch doesn't need it). */
    requestInitialFocus: Boolean = false,
    /** TV home row size (slice 11) — TV-visible (mobile screens don't). */
    showTvRowHeight: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val hiddenThisWeek by viewModel.hiddenThisWeek.collectAsStateWithLifecycle(0)
    val favoritedTags by viewModel.favoritedTags.collectAsStateWithLifecycle(emptyList())
    val blockedFeeds by viewModel.blockedFeeds.collectAsStateWithLifecycle(emptyList())
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val pinHash by viewModel.pinHash.collectAsStateWithLifecycle(null)
    val orientationPref by viewModel.orientationFilter.collectAsStateWithLifecycle("any")
    val authViewModel: AuthViewModel = hiltViewModel()
    var webLogin by remember { mutableStateOf<TokenStore.Pkce?>(null) }
    // Issue #17: in-screen sub-pages. One saveable key ("root" or a
    // SettingsPage name) — survives process death; BACK pops the panel before
    // the host's overlay handler because this BackHandler composes above it.
    var page by rememberSaveable { mutableStateOf(ROOT_PAGE) }
    val panel = SettingsPage.entries.firstOrNull { it.name == page }
    BackHandler(enabled = panel != null) { page = ROOT_PAGE }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // SAF pickers (native, no storage permission): create/open a JSON document.
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(viewModel.exportJson().toByteArray())
                        } ?: throw IllegalStateException()
                    }.fold(
                        onSuccess = { snackbarHostState.showSnackbar("Preferences exported") },
                        onFailure = { snackbarHostState.showSnackbar("Export failed") },
                    )
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val json =
                            context.contentResolver.openInputStream(uri)?.use {
                                it.readBytes().decodeToString()
                            } ?: throw IllegalStateException()
                        viewModel.importJson(json)
                    }.fold(
                        onSuccess = { result ->
                            result.fold(
                                onSuccess = {
                                    snackbarHostState.showSnackbar("Imported $it entries")
                                },
                                onFailure = {
                                    snackbarHostState.showSnackbar("Import failed — not a Giffy preferences file")
                                },
                            )
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar("Import failed — file not readable")
                        },
                    )
                }
            }
        }

    // Full-screen WebView over the settings (account sign-in).
    webLogin?.let { pkce ->
        WebViewLoginScreen(
            pkce = pkce,
            onCodeCaptured = { code ->
                webLogin = null
                authViewModel.exchangeCode(code)
            },
            onClose = { webLogin = null },
        )
        return
    }

    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    GiffyScaffold(
        title = panel?.title ?: "Settings",
        onBack = {
            if (panel == null) {
                onBack()
            } else {
                page = ROOT_PAGE
            }
        },
        backIcon = Icons.Outlined.Close,
        backDescription = "close",
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val listModifier = Modifier.fillMaxSize().padding(padding)
        // Initial focus follows the open page (TV-hosted list screens MUST
        // seed focus on the first row — batch-18 lesson): the account row at
        // root, the panel's first focusable once a panel is open, and back to
        // the account row when a panel closes. About is static text — there
        // it is a no-op (runCatching covers "no attached focusable").
        LaunchedEffect(page) {
            if (!requestInitialFocus) return@LaunchedEffect
            runCatching {
                if (panel == null) rootFocus.requestFocus() else panelFocus.requestFocus()
            }
        }
        when (panel) {
            null ->
                RootPanel(
                    modifier = listModifier,
                    rootFocus = rootFocus,
                    requestInitialFocus = requestInitialFocus,
                    authViewModel = authViewModel,
                    onOpenWebView = { webLogin = it },
                    onOpenPanel = { page = it.name },
                    blocked = blocked,
                    blockedFeedCount = blockedFeeds.size,
                    pinHash = pinHash,
                    dataSaver = dataSaver,
                    orientation = orientationPref,
                )
            SettingsPage.General ->
                GeneralPanel(
                    viewModel = viewModel,
                    modifier = listModifier,
                    pinHash = pinHash,
                    amoled = amoled,
                    dynamicColor = dynamicColor,
                    showGridColumns = showGridColumns,
                    showFeedAutoplay = showFeedAutoplay,
                    showTvRowHeight = showTvRowHeight,
                    firstFocus = Modifier.focusRequester(panelFocus),
                )
            SettingsPage.Video ->
                VideoPanel(
                    viewModel = viewModel,
                    modifier = listModifier,
                    dataSaver = dataSaver,
                    firstFocus = Modifier.focusRequester(panelFocus),
                )
            SettingsPage.Feeds ->
                FeedsPanel(
                    viewModel = viewModel,
                    modifier = listModifier,
                    orientation = orientationPref,
                    firstFocus = Modifier.focusRequester(panelFocus),
                )
            SettingsPage.Blocked ->
                BlockedPanel(
                    viewModel = viewModel,
                    modifier = listModifier,
                    blocked = blocked,
                    favoritedTags = favoritedTags,
                    blockedFeeds = blockedFeeds,
                    hiddenThisWeek = hiddenThisWeek,
                    firstFocus = Modifier.focusRequester(panelFocus),
                )
            SettingsPage.Backup ->
                BackupPanel(
                    modifier = listModifier,
                    onExport = { exportLauncher.launch("giffy-prefs.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                )
            SettingsPage.About -> AboutPanel(modifier = listModifier)
        }
    }
}

/**
 * Root list: the account block (unchanged placement) + one plain row per
 * panel with the current-state caption and a chevron — no chips at root,
 * all chip rows moved inside their panel (issue #17 design §3).
 */
@Composable
private fun RootPanel(
    rootFocus: FocusRequester,
    requestInitialFocus: Boolean,
    authViewModel: AuthViewModel,
    onOpenWebView: (TokenStore.Pkce) -> Unit,
    onOpenPanel: (SettingsPage) -> Unit,
    blocked: SettingsViewModel.BlockedUi,
    blockedFeedCount: Int,
    pinHash: String?,
    dataSaver: Boolean,
    orientation: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "account") {
            // Account first (merged 2026-10): one settings surface, no separate page.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Account", style = MaterialTheme.typography.titleSmall)
                AuthSection(
                    viewModel = authViewModel,
                    onOpenWebView = onOpenWebView,
                    modifier = Modifier.padding(top = 4.dp),
                    firstButtonModifier =
                        if (requestInitialFocus) {
                            Modifier.focusRequester(rootFocus)
                        } else {
                            Modifier
                        },
                )
            }
        }
        settingsRow(SettingsPage.General, "Theme, app lock, columns", onOpenPanel)
        settingsRow(
            SettingsPage.Video,
            if (dataSaver) "Data saver on · video fit & quality" else "Video fit & quality",
            onOpenPanel,
        )
        settingsRow(
            SettingsPage.Feeds,
            "Orientation · " +
                when (orientation) {
                    "vertical" -> "Vertical"
                    "horizontal" -> "Horizontal"
                    else -> "Any"
                },
            onOpenPanel,
        )
        settingsRow(
            SettingsPage.Blocked,
            "${blocked.creators.size} creators · ${blocked.tags.size} tags · " +
                "${blocked.keywords.size} keywords · $blockedFeedCount blocked feeds",
            onOpenPanel,
        )
        settingsRow(SettingsPage.Backup, "Export / import JSON", onOpenPanel)
        settingsRow(SettingsPage.About, "App, disclaimer, version", onOpenPanel)
    }
}

/** One root row per panel: title + current-value caption + chevron. */
private fun LazyListScope.settingsRow(
    panel: SettingsPage,
    caption: String,
    onOpenPanel: (SettingsPage) -> Unit,
) {
    item(key = panel.name) {
        // Same focus+click pair the TV niche rows use — one interaction source
        // owns both (the two-focusable-modifier race, see giffyFocus docs).
        val rowInteraction = remember { MutableInteractionSource() }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .giffyFocus(rowInteraction, fillOnFocus = MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = ripple(),
                    ) { onOpenPanel(panel) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(panel.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * General panel: app lock, theme and the device-specific display rows
 * (grid columns / autoplay — mobile · TV home row size — TV).
 */
@Composable
private fun GeneralPanel(
    viewModel: SettingsViewModel,
    pinHash: String?,
    amoled: Boolean,
    dynamicColor: Boolean,
    showGridColumns: Boolean,
    showFeedAutoplay: Boolean,
    showTvRowHeight: Boolean,
    modifier: Modifier = Modifier,
    firstFocus: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "pin-lock") {
            // PLAN §6 Phase 6: optional PIN app lock.
            var showSetPin by remember { mutableStateOf(false) }
            if (showSetPin) {
                SetPinDialog(
                    onSet = {
                        viewModel.setPin(it)
                        showSetPin = false
                    },
                    onDismiss = { showSetPin = false },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App lock (PIN)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Require a 4-digit PIN when the app starts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pinHash != null) {
                    TextButton(onClick = { viewModel.removePin() }, modifier = firstFocus) {
                        Text("Remove")
                    }
                } else {
                    TextButton(onClick = { showSetPin = true }, modifier = firstFocus) {
                        Text("Set PIN")
                    }
                }
            }
        }
        if (showGridColumns) {
            item(key = "grid-columns") {
                // §6 Grid columns responsive-first: Auto = width-derived ladder.
                val cols by viewModel.gridColumns.collectAsStateWithLifecycle(0)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Grid columns", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Auto follows window width (1 phone · 2 medium · 3 wide)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // focusGroup + fillMaxWidth: same chip-skip fix.
                        modifier =
                            Modifier
                                .focusGroup()
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        listOf(1, 2, 3).forEach { n ->
                            val src = remember { MutableInteractionSource() }
                            FilterChip(
                                selected = cols == n,
                                onClick = { viewModel.setGridColumns(n) },
                                label = { Text("$n") },
                                interactionSource = src,
                                // giffyFocus ring: M3 chips draw nothing on
                                // D-pad focus (ring-test lesson, AGENTS-APP).
                                modifier =
                                    Modifier
                                        .giffyFocus(src)
                                        .padding(end = 6.dp),
                            )
                        }
                        TextButton(onClick = { viewModel.setGridColumns(0) }) { Text("Auto") }
                    }
                }
            }
        }
        if (showFeedAutoplay) {
            item(key = "feed-autoplay") {
                // Inline feed autoplay (AGENTS-PLAYER.md, built 2026-10-02):
                // 1-column feed's settled tile plays a muted+looped preview.
                // Lives under the grid block — only meaningful for the 1-col grid.
                val feedAutoplay by viewModel.feedAutoplay.collectAsStateWithLifecycle(true)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = feedAutoplay,
                                role = Role.Switch,
                                onValueChange = { viewModel.setFeedAutoplay(it) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autoplay in feed", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Play a muted preview on the settled tile in the 1-column feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = feedAutoplay, onCheckedChange = null)
                }
            }
        }
        if (showTvRowHeight) {
            item(key = "tv-row-height") {
                // TV home row size (slice 11): Default = the current
                // 170dp 10-foot card; Large/XL bigger steps for farther
                // viewports. Chip pattern mirrors the Orientation block
                // (focusGroup + giffyFocus ring — the D-pad chip-skip fix).
                val rowHeight by viewModel.tvRowHeight.collectAsStateWithLifecycle("default")
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("TV home row size", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Bigger video cards in the TV home feed rows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .focusGroup()
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        listOf("Default" to "default", "Large" to "large", "XL" to "xl").forEach { (label, value) ->
                            val src = remember { MutableInteractionSource() }
                            FilterChip(
                                selected = rowHeight == value,
                                onClick = { viewModel.setTvRowHeight(value) },
                                label = { Text(label) },
                                interactionSource = src,
                                // giffyFocus ring: M3 chips draw nothing on D-pad focus.
                                modifier =
                                    Modifier
                                        .giffyFocus(src)
                                        .padding(end = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        item(key = "theme") {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = amoled,
                                role = Role.Switch,
                                onValueChange = { viewModel.setAmoled(it) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AMOLED true-black", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Pure black background — saves power on OLED screens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = amoled, onCheckedChange = null)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Last Switch-only row → whole-row toggleable (a11y parity).
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .toggleable(
                                    value = dynamicColor,
                                    role = Role.Switch,
                                    onValueChange = { viewModel.setDynamicColor(it) },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic color", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Wallpaper-based Material You palette (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

/** Video panel: fit, quality and data saver (the stream/quality trio). */
@Composable
private fun VideoPanel(
    viewModel: SettingsViewModel,
    dataSaver: Boolean,
    modifier: Modifier = Modifier,
    firstFocus: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "video-fit") {
            // §5 Video fit: Fit (default) · Crop — both players (Stretch
            // retired, issue #21: RESIZE_MODE_FILL removed; a legacy stored
            // "stretch" renders as Fit). Keep the focusGroup chip-row shape
            // (batch-12 TV chip-skip fix) untouched.
            val videoFit by viewModel.videoFit.collectAsStateWithLifecycle("fit")
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Video fit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Fit shows the whole video (bars where aspects differ)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier =
                        Modifier
                            .focusGroup()
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    listOf("Fit" to "fit", "Crop" to "crop").forEach { (label, value) ->
                        val src = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = videoFit == value,
                            onClick = { viewModel.setVideoFit(value) },
                            label = { Text(label) },
                            interactionSource = src,
                            // giffyFocus ring: M3 chips draw nothing on D-pad focus.
                            modifier =
                                Modifier
                                    .then(if (value == "fit") firstFocus else Modifier)
                                    .giffyFocus(src)
                                    .padding(end = 6.dp),
                        )
                    }
                }
            }
        }
        item(key = "video-quality") {
            // §5 Preferred quality (issue #4): the value list lives in
            // core/model VideoQuality (THE one place; players + pref read
            // the same constants). Only available choices depend on the
            // video — Auto is the default; a single-rendition stream makes
            // the height caps honest no-ops (clamp guard in GiffyPlayer).
            val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle(VideoQuality.AUTO)
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Video quality", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Available choices depend on the video; Auto is the default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier =
                        Modifier
                            .focusGroup()
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    VideoQuality.VALUES.forEach { value ->
                        val src = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = videoQuality == value,
                            onClick = { viewModel.setVideoQuality(value) },
                            label = { Text(VideoQuality.label(value)) },
                            interactionSource = src,
                            // giffyFocus ring: M3 chips draw nothing on D-pad focus.
                            modifier =
                                Modifier
                                    .giffyFocus(src)
                                    .padding(end = 6.dp),
                        )
                    }
                }
            }
        }
        item(key = "data-saver-toggle") {
            // A11y (audit finding 1): whole-row toggleable — TalkBack reads
            // the switch state instead of a color-only widget.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .toggleable(
                            value = dataSaver,
                            role = Role.Switch,
                            onValueChange = { viewModel.setDataSaver(it) },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Data saver", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Prefer SD streams — lower bandwidth, faster start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dataSaver, onCheckedChange = null)
            }
        }
    }
}

/** Feeds & filters panel: the read-time feed filters (orientation, verified). */
@Composable
private fun FeedsPanel(
    viewModel: SettingsViewModel,
    orientation: String,
    modifier: Modifier = Modifier,
    firstFocus: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "orientation") {
            // §6 Orientation filter (AGENTS-APP 2026-10): read-time pref.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Orientation", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Show only vertical or horizontal gifs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    // focusGroup: D-pad DOWN enters the chip row instead of
                    // skipping past it (Settings chip-skip bug, 2026-10 probe).
                    // fillMaxWidth: the group's focus rect must span the beam
                    // — from right-edge buttons the wrap-content rect loses
                    // the directional search to the full-width row below.
                    modifier =
                        Modifier
                            .focusGroup()
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    listOf("Any" to "any", "Vertical" to "vertical", "Horizontal" to "horizontal").forEach { (label, value) ->
                        val src = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = orientation == value,
                            onClick = { viewModel.setOrientationFilter(value) },
                            label = { Text(label) },
                            interactionSource = src,
                            // giffyFocus ring: M3 chips draw nothing on D-pad focus.
                            modifier =
                                Modifier
                                    .then(if (value == "any") firstFocus else Modifier)
                                    .giffyFocus(src)
                                    .padding(end = 6.dp),
                        )
                    }
                }
            }
        }
        item(key = "verified-only") {
            // Verified-only (2026-10): drop unverified-creator gifs everywhere
            // (spam noise). Same row-toggle a11y pattern as data saver.
            val verifiedOnly by viewModel.verifiedOnly.collectAsStateWithLifecycle(false)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .toggleable(
                            value = verifiedOnly,
                            role = Role.Switch,
                            onValueChange = { viewModel.setVerifiedOnly(it) },
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verified creators only", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hide gifs from unverified accounts — filters spam",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = verifiedOnly, onCheckedChange = null)
            }
        }
    }
}

/**
 * The blocked inventory (the issue #17 headline): blocked creators/tags/
 * keywords + favorited creators/tags + BLOCKED tag-bundle feeds, one panel.
 * Rows reuse the shared [section] renderer; the feeds list is separate only
 * because its keys come from the row ids (custom-feed names can repeat).
 */
@Composable
private fun BlockedPanel(
    viewModel: SettingsViewModel,
    blocked: SettingsViewModel.BlockedUi,
    favoritedTags: List<String>,
    blockedFeeds: List<CustomFeedEntity>,
    hiddenThisWeek: Int,
    modifier: Modifier = Modifier,
    firstFocus: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (blocked.creators.isEmpty() &&
            blocked.tags.isEmpty() &&
            blocked.keywords.isEmpty() &&
            favoritedTags.isEmpty() &&
            blockedFeeds.isEmpty()
        ) {
            item(key = "nothing-blocked") {
                Text(
                    "Nothing blocked yet. Long-press a tile in the feed to block or favorite creators.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Panel's first focusable takes the initial focus (TV-hosted list
        // lesson): the first populated section's first row — or the first
        // blocked-feed row when only custom feeds are blocked.
        val seedSection =
            if (blocked.favorites.isNotEmpty()) {
                "Favorited creators"
            } else if (favoritedTags.isNotEmpty()) {
                "Favorited tags"
            } else if (blocked.creators.isNotEmpty()) {
                "Blocked creators"
            } else if (blocked.tags.isNotEmpty()) {
                "Blocked tags"
            } else if (blocked.keywords.isNotEmpty()) {
                "Blocked keywords"
            } else if (blockedFeeds.isNotEmpty()) {
                "Blocked feeds"
            } else {
                null
            }
        val seed = { title: String -> if (title == seedSection) firstFocus else Modifier }
        section("Favorited creators", blocked.favorites, seed("Favorited creators")) { viewModel.unfavoriteCreator(it) }
        section("Favorited tags", favoritedTags, seed("Favorited tags")) { viewModel.unfavoriteTag(it) }
        section("Blocked creators", blocked.creators, seed("Blocked creators")) { viewModel.unblockCreator(it) }
        section("Blocked tags", blocked.tags, seed("Blocked tags")) { viewModel.unblockTag(it) }
        section("Blocked keywords", blocked.keywords, seed("Blocked keywords")) { viewModel.unblockKeyword(it) }
        if (blockedFeeds.isNotEmpty()) {
            item(key = "blocked-feeds-title") {
                Text(
                    "Blocked feeds",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            // Keys are the row ids — custom-feed names may repeat (save
            // doesn't dedupe), so this list can't go through [section].
            itemsIndexed(blockedFeeds, key = { _, feed -> "blocked-feeds:${feed.id}" }) { index, feed ->
                val feedInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) firstFocus else Modifier)
                            .giffyFocus(feedInteraction),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(feed.name, style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = { viewModel.unblockFeed(feed) },
                        modifier = Modifier.giffyFocus(feedInteraction),
                        interactionSource = feedInteraction,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "unblock ${feed.name}")
                    }
                }
            }
            item(key = "blocked-feeds-note") {
                Text(
                    "Blocked feeds merge the feed's creators and tags into global blocks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "hidden-this-week") {
            Text(
                "Hidden this week: $hiddenThisWeek",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item(key = "not-blockable") {
            Text(
                "Niches and individual gifs are not blockable — niche filtering runs " +
                    "through the blocked tag-bundle feeds above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Backup panel: the same SAF export/import pair, own page now (issue #17). */
@Composable
private fun BackupPanel(
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "backup") {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Preferences backup",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("Export") }
                    OutlinedButton(onClick = onImport) { Text("Import") }
                }
                Text(
                    "Export saves blocked & favorited creators, tags, keywords and data saver. " +
                        "Import merges them in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** About panel (issue #17 §8.7): name, disclaimer, hosting app's version. */
@Composable
private fun AboutPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version =
        remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().takeUnless { it.isNullOrEmpty() } ?: "unknown"
        }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "about") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Giffy Viewer", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Unofficial viewer for a public GIF-hosting site. Not affiliated with, endorsed " +
                        "by, or connected to the upstream service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    values: List<String>,
    firstFocus: Modifier = Modifier,
    onRemove: (String) -> Unit,
) {
    if (values.isEmpty()) return
    item(key = title) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    itemsIndexed(values, key = { _, value -> "$title:$value" }) { index, value ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) firstFocus else Modifier)
                    .giffyFocus(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onRemove(value) }) {
                Icon(Icons.Outlined.Close, contentDescription = "unblock $value")
            }
        }
    }
}

/** PLAN §6 Phase 6: 4-digit PIN entry when enabling the app lock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetPinDialog(
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("4-digit PIN") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4,
                onClick = { onSet(pin) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
