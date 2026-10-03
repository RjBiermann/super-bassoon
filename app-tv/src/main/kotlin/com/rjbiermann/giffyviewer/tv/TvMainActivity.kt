package com.rjbiermann.giffyviewer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.parseNicheRef
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

    @Inject lateinit var repository: com.rjbiermann.giffyviewer.feature.feed.FeedRepository

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
                androidx.tv.material3.MaterialTheme(
                    colorScheme = giffyTvColors(composeColors),
                    typography = tvTypography(),
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
        var showSettings by remember { mutableStateOf(false) }
        var showNiches by remember { mutableStateOf(false) }
        // TV parity gaps closed (AGENTS-APP.md): shared :feature:* screens hosted
        // the same way Settings/Auth already are.
        var showSearch by remember { mutableStateOf(false) }
        var showCollections by remember { mutableStateOf(false) }
        var showCustomFeeds by remember { mutableStateOf(false) }
        var nicheAbout: FeedSource.Niche? by remember { mutableStateOf(null) }
        var openFeed: FeedSource? by remember { mutableStateOf(null) }
        val pinnedNiches by settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
        val pinnedCreators by settings.pinnedCreators.collectAsStateWithLifecycle(initialValue = emptySet())
        // Preview-on-focus gates on data-saver (posters only — same rule as the
        // mobile inline autoplay).
        val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
        // Custom feed pills (PLAN §7 builder) — from Room, ordered by creation.
        val customFeeds by db
            .customFeedDao()
            .all()
            .collectAsStateWithLifecycle(initialValue = emptyList())

        // D-pad BACK pops the top screen instead of exiting the activity
        val screenOpen =
            player != null ||
                showSettings ||
                showNiches ||
                openFeed != null ||
                showSearch ||
                showCollections ||
                showCustomFeeds ||
                nicheAbout != null
        androidx.activity.compose.BackHandler(
            enabled = screenOpen,
        ) {
            when {
                player != null -> player = null
                openFeed != null -> openFeed = null
                showSearch -> showSearch = false
                showCollections -> showCollections = false
                showCustomFeeds -> showCustomFeeds = false
                nicheAbout != null -> nicheAbout = null
                showNiches -> showNiches = false
                showSettings -> showSettings = false
                else -> showSettings = false
            }
        }

        val pinHash by settings.pinHash.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by remember { mutableStateOf(false) }

        // PLAN §1: 5% overscan margins on TV — content never touches the bezel.
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            when {
                pinHash != null && !unlocked ->
                    com.rjbiermann.giffyviewer.core.ui.PinLockScreen(
                        onUnlock = { unlocked = true },
                        verifyPin = { settings.verifyPin(it) },
                    )
                !confirmed ->
                    com.rjbiermann.giffyviewer.core.ui.AgeGate(
                        onConfirmed = { settings.confirmAge() },
                        onExit = ::finishAffinity,
                        requestInitialFocus = true,
                    )
                player != null -> {
                    val (gifs, start) = player ?: listOf<Gif>() to 0
                    TvPlayerScreen(
                        gifs = gifs,
                        startIndex = start,
                        playerFactory = playerFactory,
                        settings = settings,
                        db = db,
                        feedViewModel = hiltViewModel(),
                        onBack = { player = null },
                        onOpenCreator = { username -> openFeed = FeedSource.Creator(username = username) },
                        onOpenNiche = { niche -> openFeed = niche },
                    )
                }
                openFeed != null -> {
                    val source = openFeed
                    if (source != null) {
                        val feedVm: TvNicheFeedViewModel = hiltViewModel()
                        val quickVm: com.rjbiermann.giffyviewer.feature.feed.FeedViewModel = hiltViewModel()
                        TvSourceFeedScreen(
                            source = source,
                            onOpenGif = { gifs, index -> player = gifs to index },
                            viewModel = feedVm,
                            feedViewModel = quickVm,
                            onOpenNiche = { niche -> openFeed = niche },
                            playerFactory = playerFactory,
                            dataSaver = dataSaver,
                        )
                    }
                }
                nicheAbout != null -> {
                    val about = nicheAbout
                    if (about != null) {
                        com.rjbiermann.giffyviewer.feature.feed.NicheAboutScreen(
                            nicheId = about.id,
                            nicheName = about.name,
                            onBack = { nicheAbout = null },
                            onOpenCreator = { username ->
                                nicheAbout = null
                                openFeed = FeedSource.Creator(username = username)
                            },
                            onOpenNiche = { niche ->
                                nicheAbout = null
                                openFeed = niche
                            },
                            viewModel = hiltViewModel(),
                            joinViewModel = hiltViewModel(),
                        )
                    }
                }
                showNiches -> {
                    TvNichesScreen(
                        onOpenNiche = { niche -> openFeed = niche },
                        onOpenNicheAbout = { id, name -> nicheAbout = FeedSource.Niche(id, name) },
                    )
                }
                showSearch ->
                    com.rjbiermann.giffyviewer.search.SearchScreen(
                        onBack = { showSearch = false },
                        onSubmit = { q ->
                            showSearch = false
                            openFeed = FeedSource.Search(query = q)
                        },
                        onOpenCreator = { username ->
                            showSearch = false
                            openFeed = FeedSource.Creator(username = username)
                        },
                        onOpenNiche = { id, name ->
                            showSearch = false
                            openFeed = FeedSource.Niche(id, name)
                        },
                        viewModel = hiltViewModel(),
                    )
                showCollections ->
                    com.rjbiermann.giffyviewer.feature.feed.CollectionsScreen(
                        onBack = { showCollections = false },
                        viewModel = hiltViewModel(),
                    )
                showCustomFeeds ->
                    com.rjbiermann.giffyviewer.feature.feed.CustomFeedsScreen(
                        onBack = { showCustomFeeds = false },
                        requestInitialFocus = true,
                        onOpenFeed = { feed ->
                            showCustomFeeds = false
                            openFeed = feed
                        },
                        viewModel = hiltViewModel(),
                    )
                showSettings ->
                    SettingsScreen(
                        onBack = { showSettings = false },
                        showGridColumns = false,
                        showFeedAutoplay = false,
                        requestInitialFocus = true,
                    )
                else -> {
                    val home: TvHomeViewModel = hiltViewModel()
                    val continueVm: ContinueWatchingViewModel = hiltViewModel()
                    val homeScope = rememberCoroutineScope()
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
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "Settings",
                                    onClick = { showSettings = true },
                                    modifier = Modifier.padding(6.dp).focusRequester(focusRequester),
                                )
                            }
                            item {
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "Niches",
                                    onClick = { showNiches = true },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            item {
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = "Search",
                                    onClick = { showSearch = true },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            items(pinnedNiches.toList().mapNotNull { parseNicheRef(it) }) { (id, name) ->
                                com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                    text = name,
                                    onClick = { openFeed = FeedSource.Niche(id, name) },
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                            // Custom feeds + pinned tabs consolidated under a
                            // More ▾ menu (user: pinned pills made the row
                            // unusable) — D-pad opens the dropdown.
                            item {
                                androidx.compose.foundation.layout.Box {
                                    var moreOpen by remember { mutableStateOf(false) }
                                    com.rjbiermann.giffyviewer.core.ui.GiffyPillButton(
                                        text = "More ▾",
                                        onClick = { moreOpen = true },
                                        modifier = Modifier.padding(6.dp),
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = moreOpen,
                                        onDismissRequest = { moreOpen = false },
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { androidx.compose.material3.Text("Surprise me") },
                                            onClick = {
                                                moreOpen = false
                                                homeScope.launch {
                                                    val ok = repository.refreshSurprise()
                                                    if (ok) openFeed = FeedSource.Surprise
                                                }
                                            },
                                        )
                                        androidx.compose.material3.HorizontalDivider()
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { androidx.compose.material3.Text("My feeds") },
                                            onClick = {
                                                moreOpen = false
                                                showCustomFeeds = true
                                            },
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { androidx.compose.material3.Text("Collections") },
                                            onClick = {
                                                moreOpen = false
                                                showCollections = true
                                            },
                                        )
                                        customFeeds.toList().forEach { def ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { androidx.compose.material3.Text(def.name) },
                                                onClick = {
                                                    moreOpen = false
                                                    openFeed =
                                                        FeedSource.Custom(
                                                            def.id,
                                                            def.name,
                                                            def.sourcesJson.split(',').filter { it.isNotBlank() },
                                                        )
                                                },
                                            )
                                        }
                                        val pinnedEntries =
                                            pinnedNiches.mapNotNull { parseNicheRef(it) } +
                                                pinnedCreators.map { Pair("user:${it.lowercase()}", "@$it") }
                                        if (pinnedEntries.isNotEmpty()) {
                                            androidx.compose.material3.HorizontalDivider()
                                            pinnedEntries.forEach { (ref, name) ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { androidx.compose.material3.Text(name) },
                                                    onClick = {
                                                        moreOpen = false
                                                        openFeed =
                                                            if (ref.startsWith("user:")) {
                                                                FeedSource.Creator(ref.removePrefix("user:"))
                                                            } else {
                                                                FeedSource.Niche(ref, name)
                                                            }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        TvHomeScreen(
                            onOpenGif = { gifs, index -> player = gifs to index },
                            onOpenCreator = { username -> openFeed = FeedSource.Creator(username = username) },
                            onOpenNiche = { niche -> openFeed = niche },
                            playerFactory = playerFactory,
                            dataSaver = dataSaver,
                            homeViewModel = home,
                            continueViewModel = continueVm,
                        )
                    }
                }
            }
        }
    }
}
