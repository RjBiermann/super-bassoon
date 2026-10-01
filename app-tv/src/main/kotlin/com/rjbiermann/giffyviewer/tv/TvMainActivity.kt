package com.rjbiermann.giffyviewer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.feature.auth.AuthScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThemeHost() }
    }

    /** Borrowed dark theme, AMOLED option shared with mobile (PLAN §9).
     *  tv-material components (Cards etc.) read THEIR OWN MaterialTheme, not
     *  Compose's — without a FULL mapping its default Roboto typography and
     *  un-overridden colors leak (black-on-black text, wrong fonts). */
    @Composable
    private fun ThemeHost() {
        val amoled by settings.amoled.collectAsStateWithLifecycle(initialValue = false)
        GiffyTheme(amoled = amoled) {
            // Surface provides LocalContentColor (M3 defaults to BLACK outside a
            // Surface — the black-on-black titles; mobile screens all sit in a
            // Scaffold, these TV screens compose bare Text).
            androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                val composeColors = androidx.compose.material3.MaterialTheme.colorScheme
                val composeTypo = androidx.compose.material3.MaterialTheme.typography
                androidx.tv.material3.MaterialTheme(
                    colorScheme = giffyTvColors(composeColors),
                    typography = tvTypography(composeTypo),
                ) {
                    // tv-material has its OWN LocalContentColor whose default is
                    // Color.Black — bare tv3 Texts outside tv Surfaces render
                    // black-on-black. Provide the theme content color at the root.
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.tv.material3.LocalContentColor provides composeColors.onBackground,
                    ) { Root() }
                }
            }
        }
    }

    @Composable
    private fun Root() {
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        var player: Pair<List<Gif>, Int>? by remember { mutableStateOf(null) }
        var showAccount by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showNiches by remember { mutableStateOf(false) }
        var openFeed: FeedSource? by remember { mutableStateOf(null) }
        val pinnedNiches by settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
        val pinnedCreators by settings.pinnedCreators.collectAsStateWithLifecycle(initialValue = emptySet())

        // D-pad BACK pops the top screen instead of exiting the activity
        androidx.activity.compose.BackHandler(
            enabled = player != null || showAccount || showSettings || showNiches || openFeed != null,
        ) {
            when {
                player != null -> player = null
                openFeed != null -> openFeed = null
                showNiches -> showNiches = false
                showSettings -> showSettings = false
                else -> showAccount = false
            }
        }

        // PLAN §1: 5% overscan margins on TV — content never touches the bezel.
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            when {
                !confirmed -> AgeGate(onConfirmed = { settings.confirmAge() })
                player != null -> {
                    val (gifs, start) = player ?: listOf<Gif>() to 0
                    TvPlayerScreen(
                        gifs = gifs,
                        startIndex = start,
                        playerFactory = playerFactory,
                        settings = settings,
                        db = db,
                        onBack = { player = null },
                    )
                }
                openFeed != null -> {
                    val source = openFeed
                    if (source != null) {
                        val feedVm: TvNicheFeedViewModel = hiltViewModel()
                        TvSourceFeedScreen(
                            source = source,
                            onOpenGif = { gifs, index -> player = gifs to index },
                            viewModel = feedVm,
                        )
                    }
                }
                showNiches -> {
                    val nichesVm: TvNichesViewModel = hiltViewModel()
                    TvNichesScreen(
                        onOpenNiche = { niche -> openFeed = niche },
                        viewModel = nichesVm,
                    )
                }
                showAccount -> AuthScreen(onBack = { showAccount = false })
                showSettings -> SettingsScreen(onBack = { showSettings = false })
                else -> {
                    val home: TvHomeViewModel = hiltViewModel()
                    val continueVm: ContinueWatchingViewModel = hiltViewModel()
                    val focusRequester = remember { FocusRequester() }
                    // No initial D-pad focus otherwise — header buttons were unreachable
                    // until the user tabbed into a row blindly.
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Pinned niches = TV's version of the mobile home chip tabs;
                        // pills scroll horizontally like the mobile chip row.
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item {
                                AccountButton(
                                    focusRequester = focusRequester,
                                    onClick = { showAccount = true },
                                )
                            }
                            item {
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "Settings",
                                    onClick = { showSettings = true },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            item {
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "Niches",
                                    onClick = { showNiches = true },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            items(pinnedNiches.toList()) { entry ->
                                val id = entry.substringBefore('|')
                                val name = entry.substringAfter('|')
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = name,
                                    onClick = { openFeed = FeedSource.Niche(id, name) },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            // Pinned creators = home pills (PLAN §7 pin-to-tabs), same as mobile chips.
                            items(pinnedCreators.toList()) { username ->
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "@$username",
                                    onClick = { openFeed = FeedSource.Creator(username) },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                        }
                        TvHomeScreen(
                            onOpenGif = { gifs, index -> player = gifs to index },
                            onOpenCreator = { username -> openFeed = FeedSource.Creator(username = username) },
                            homeViewModel = home,
                            continueViewModel = continueVm,
                        )
                    }
                }
            }
        }
    }

    /** PLAN §3: one-time blocking attestation; D-pad focusable buttons. */
    @Composable
    private fun AgeGate(onConfirmed: suspend () -> Unit) {
        val scope = rememberCoroutineScope()
        val gateFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { gateFocus.requestFocus() }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(160.dp))
                Text("Giffy Viewer", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This app shows adult content. You must be 18 or older. " +
                        "Not affiliated with or endorsed by upstream.",
                )
                Spacer(Modifier.height(24.dp))
                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                    text = "I am 18 or older — Enter",
                    onClick = { scope.launch { onConfirmed() } },
                    modifier = Modifier.focusRequester(gateFocus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = ::finishAffinity) { Text("Exit (leaves app)") }
            }
        }
    }
}

@Composable
/** Header buttons need a requested initial focus — nothing else grabs it on this screen. */
private fun AccountButton(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
        text = "Account",
        onClick = onClick,
        modifier = Modifier.padding(16.dp).focusRequester(focusRequester),
    )
}
