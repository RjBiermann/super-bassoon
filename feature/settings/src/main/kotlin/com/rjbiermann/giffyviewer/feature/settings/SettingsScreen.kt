package com.rjbiermann.giffyviewer.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import kotlinx.coroutines.launch

/** PLAN §6: blocked creators / tags / keywords with unblock actions. */
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val hiddenThisWeek by viewModel.hiddenThisWeek.collectAsStateWithLifecycle(0)
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val authViewModel: com.rjbiermann.giffyviewer.feature.auth.AuthViewModel = hiltViewModel()
    var webLogin by remember { mutableStateOf<com.rjbiermann.giffyviewer.core.auth.TokenStore.Pkce?>(null) }
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
        com.rjbiermann.giffyviewer.feature.auth.WebViewLoginScreen(
            pkce = pkce,
            onCodeCaptured = { code ->
                webLogin = null
                authViewModel.exchangeCode(code)
            },
            onClose = { webLogin = null },
        )
        return
    }

    GiffyScaffold(
        title = "Settings",
        onBack = onBack,
        backIcon = Icons.Outlined.Close,
        backDescription = "close",
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val firstFocus =
            remember {
                androidx.compose.ui.focus
                    .FocusRequester()
            }
        LaunchedEffect(Unit) { if (requestInitialFocus) firstFocus.requestFocus() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "account") {
                // Account first (merged 2026-10): one settings surface, no separate page.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Account", style = MaterialTheme.typography.titleSmall)
                    com.rjbiermann.giffyviewer.feature.auth.AuthSection(
                        viewModel = authViewModel,
                        onOpenWebView = { webLogin = it },
                        modifier = Modifier.padding(top = 4.dp),
                        firstButtonModifier =
                            if (requestInitialFocus) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                    )
                }
            }
            if (blocked.creators.isEmpty() && blocked.tags.isEmpty() && blocked.keywords.isEmpty()) {
                item {
                    Text(
                        "Nothing blocked yet. Long-press a tile in the feed to block or favorite creators.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            section("Favorited creators", blocked.favorites) { viewModel.unfavoriteCreator(it) }
            section("Blocked creators", blocked.creators) { viewModel.unblockCreator(it) }
            section("Blocked tags", blocked.tags) { viewModel.unblockTag(it) }
            section("Blocked keywords", blocked.keywords) { viewModel.unblockKeyword(it) }
            item(key = "hidden-this-week") {
                Text(
                    "Hidden this week: $hiddenThisWeek",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "pin-lock") {
                val pinHash by viewModel.pinHash.collectAsStateWithLifecycle(null)
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
                        TextButton(onClick = { viewModel.removePin() }) { Text("Remove") }
                    } else {
                        TextButton(onClick = { showSetPin = true }) { Text("Set PIN") }
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
            }
            item(key = "orientation") {
                // §6 Orientation filter (AGENTS-APP 2026-10): read-time pref.
                val orientationPref by viewModel.orientationFilter.collectAsStateWithLifecycle("any")
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
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
                                selected = orientationPref == value,
                                onClick = { viewModel.setOrientationFilter(value) },
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
            item(key = "verified-only") {
                // Verified-only (2026-10): drop unverified-creator gifs everywhere
                // (spam noise). Same row-toggle a11y pattern as data saver.
                val verifiedOnly by viewModel.verifiedOnly.collectAsStateWithLifecycle(false)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
            item(key = "video-fit") {
                // §5 Video fit: Fit (default) · Crop · Stretch — both players.
                val videoFit by viewModel.videoFit.collectAsStateWithLifecycle("fit")
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
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
                        listOf("Fit" to "fit", "Crop" to "crop", "Stretch" to "stretch").forEach { (label, value) ->
                            val src = remember { MutableInteractionSource() }
                            FilterChip(
                                selected = videoFit == value,
                                onClick = { viewModel.setVideoFit(value) },
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
            item(key = "data-saver-toggle") {
                // A11y (audit finding 1): whole-row toggleable — TalkBack reads
                // the switch state instead of a color-only widget.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
            item(key = "backup") {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Preferences backup",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("giffy-prefs.json") }) {
                            Text("Export")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Text("Import")
                        }
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
}

private fun LazyListScope.section(
    title: String,
    values: List<String>,
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
    items(values, key = { "$title:$it" }) { value ->
        Row(
            modifier = Modifier.fillMaxWidth().giffyFocus(),
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SetPinDialog(
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("4-digit PIN") },
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
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
