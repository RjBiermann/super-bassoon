package com.rjbiermann.giffyviewer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.Feature
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.core.ui.NetworkMonitor
import com.rjbiermann.giffyviewer.core.ui.OfflineBanner
import com.rjbiermann.giffyviewer.core.ui.offlineBannerVisible
import com.rjbiermann.giffyviewer.feature.auth.DebugSignIn
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.feedSourceNavType
import com.rjbiermann.giffyviewer.feature.feed.gifListNavType
import com.rjbiermann.giffyviewer.feature.feed.nicheNavType
import com.rjbiermann.giffyviewer.feature.feed.parseNicheRef
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import com.rjbiermann.giffyviewer.search.SearchScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.reflect.typeOf
import androidx.tv.material3.Text as TvText

/**
 * Issue #13 nav routes (TV). TV keeps D-pad semantics throughout: the nav
 * stack only changes WHICH composable hosts a screen; CenterHold/FocusPreview/
 * focus rings inside a screen are untouched. The player payload is the loaded
 * window (§4.1): Gif is @Serializable, the route JSON lives in nav saved state
 * only — a fallback startIndex-only route is deliberately NOT built (R3/YAGNI).
 */
@Serializable
data object TvCollections

@Serializable
data object TvCustomFeeds

@Serializable
data object TvHome

@Serializable
data class TvNicheAbout(
    val about: FeedSource.Niche,
)

@Serializable
data object TvNiches

@Serializable
data class TvFeed(
    val source: FeedSource,
)

/** One-shot per opened window: BACK relands the covered grid/list resume-free. */
@Serializable
data class TvPlayer(
    val gifs: List<Gif>,
    val startIndex: Int,
)

@Serializable
data object TvSearch

@Serializable
data object TvSettings

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    @Inject lateinit var tokenStore: TokenStore

    /** Issue #33: OS connectivity behind the offline banner. */
    @Inject lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug-only sign-in injection (Stream-G device gates): the token
        // rides the launch extra in debug builds; release never reaches it.
        if (com.rjbiermann.giffyviewer.tv.BuildConfig.DEBUG) {
            DebugSignIn.apply(tokenStore, intent?.getStringExtra("giffy_debug_bundle"))
        }
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
        // Issue #33: banner is an OVERLAY here (TV never provides LocalIsOnline,
        // so TV-hosted shared screens can't double-banner), inside the overscan
        // margins, non-focusable by construction (no click/D-pad target).
        val online by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        // Issue #13 §4.2: age gate + PIN stay OUTSIDE the NavHost (gating, not
        // navigation). unlocked is deliberately NOT saveable — same as today,
        // a PIN demands re-entry after process death (§8.15).
        val pinHash by settings.pinHash.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by remember { mutableStateOf(false) }
        // PLAN §1: 5% overscan margins on TV — content never touches the bezel.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
            ) {
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
                    else -> TvNav()
                }
            }
            if (offlineBannerVisible(online)) {
                OfflineBanner(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 48.dp, vertical = 27.dp),
                )
            }
        }
    }

    @Composable
    private fun TvNav() {
        val navController = rememberNavController()
        // Issue #13 §4.2: FeedViewModel (quick-action/model for prefs, strip
        // data, likes, favorites) stays ACTIVITY-scoped — acquired at NavHost
        // scope, passed by parameter. Inside a destination hiltViewModel()
        // would silently scope to the NavBackStackEntry.
        val quickVm: FeedViewModel = hiltViewModel()
        NavHost(navController, startDestination = TvHome) {
            composable<TvHome> {
                TvHomeDestination(navController)
            }
            composable<TvFeed>(
                typeMap = mapOf(typeOf<FeedSource>() to feedSourceNavType),
            ) { entry ->
                val route = entry.toRoute<TvFeed>()
                val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
                // Per-feed pager model — entry-scoped now (§4.2): one fresh
                // TvNicheFeedViewModel per pushed feed (the flow pins the
                // source via remember(source.keyBase) inside the screen).
                val feedVm: TvNicheFeedViewModel = hiltViewModel()
                // Issue #13 §4.3: saveable focus position — BACK from a
                // player re-arms D-pad focus on the same card (TvSourceFeedScreen
                // owns the restore; scroll is saveable per entry in-screen).
                var focusItem by rememberSaveable { mutableIntStateOf(-1) }
                TvSourceFeedScreen(
                    source = route.source,
                    onOpenGif = { gifs, index -> navController.navigate(TvPlayer(gifs, index)) },
                    viewModel = feedVm,
                    feedViewModel = quickVm,
                    onOpenNiche = { niche -> navController.navigate(TvFeed(niche)) },
                    // #18: the dialog's sort half re-identifies the feed — a
                    // CHANGED sort REPLACES this entry (no stacking per tap).
                    onSortChange = { sorted ->
                        navController.navigate(TvFeed(sorted)) {
                            popUpTo(entry.id) { inclusive = true }
                        }
                    },
                    // #39: no shared FeedTabStrip on TV (mobile main-tab chrome
                    // reproduced as "TV was showing mobile ui") — the host seams
                    // the strip consumed are gone with it; TV navigation stays
                    // on the home header pills + quick-actions "Open feed".
                    playerFactory = playerFactory,
                    dataSaver = dataSaver,
                    savedFocusItem = focusItem,
                    onFocusSaved = { focusItem = it },
                )
            }
            composable<TvPlayer>(
                typeMap = mapOf(typeOf<List<Gif>>() to gifListNavType),
            ) { entry ->
                val route = entry.toRoute<TvPlayer>()
                TvPlayerScreen(
                    gifs = route.gifs,
                    startIndex = route.startIndex,
                    playerFactory = playerFactory,
                    settings = settings,
                    db = db,
                    feedViewModel = quickVm,
                    onBack = { navController.popBackStack() },
                    // Doc comment keeps its "exits the player" semantics: the
                    // popped-entry replace puts the feed where the player was,
                    // BACK lands the covered surface (home rows / taking grid).
                    onOpenCreator = { username ->
                        navController.navigate(TvFeed(FeedSource.Creator(username = username))) {
                            popUpTo(entry.id) { inclusive = true }
                        }
                    },
                    onOpenNiche = { niche ->
                        navController.navigate(TvFeed(niche)) {
                            popUpTo(entry.id) { inclusive = true }
                        }
                    },
                )
            }
            composable<TvNiches> {
                TvNichesScreen(
                    onOpenNiche = { niche -> navController.navigate(TvFeed(niche)) },
                    onOpenNicheAbout = { id, name ->
                        navController.navigate(TvNicheAbout(FeedSource.Niche(id, name)))
                    },
                )
            }
            composable<TvNicheAbout>(
                typeMap = mapOf(typeOf<FeedSource.Niche>() to nicheNavType),
            ) { entry ->
                val about = entry.toRoute<TvNicheAbout>().about
                com.rjbiermann.giffyviewer.feature.feed.NicheAboutScreen(
                    nicheId = about.id,
                    nicheName = about.name,
                    onBack = { navController.popBackStack() },
                    onOpenCreator = { username ->
                        navController.navigate(TvFeed(FeedSource.Creator(username = username)))
                    },
                    onOpenNiche = { niche -> navController.navigate(TvFeed(niche)) },
                    viewModel = hiltViewModel(),
                    joinViewModel = hiltViewModel(),
                )
            }
            composable<TvSearch> {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onSubmit = { q -> navController.navigate(TvFeed(FeedSource.Search(query = q))) },
                    onOpenCreator = { username ->
                        navController.navigate(TvFeed(FeedSource.Creator(username = username)))
                    },
                    onOpenNiche = { id, name -> navController.navigate(TvFeed(FeedSource.Niche(id, name))) },
                    onOpenFeed = { source -> navController.navigate(TvFeed(source)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<TvCollections> {
                com.rjbiermann.giffyviewer.feature.feed.CollectionsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = hiltViewModel(),
                )
            }
            composable<TvCustomFeeds> {
                com.rjbiermann.giffyviewer.feature.feed.CustomFeedsScreen(
                    onBack = { navController.popBackStack() },
                    requestInitialFocus = true,
                    onOpenFeed = { feed -> navController.navigate(TvFeed(feed)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<TvSettings> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    showGridColumns = false,
                    showFeedAutoplay = false,
                    // TV home has its own row-height setting (mobile grid
                    // columns section is hidden here; this row SHOWS on TV).
                    showTvRowHeight = true,
                    requestInitialFocus = true,
                )
            }
        }
    }

    /** Home rows, pills + More ▾ menu — the standing surface the hosts feed
     *  and Player entries cover (emphasis: home row D-pad focus restore §4.3
     *  uses (rowTitle, itemIndex) saveables; blank row = fresh entry). */
    @Composable
    private fun TvHomeDestination(navController: NavHostController) {
        // §9 TV horizontal pills — pinned niches parse from DataStore refs.
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
        val focusRequester = remember { FocusRequester() }
        // Issue #13 §4.3: (rowTitle, itemIndex) saveables — the row restore
        // wiring lives inside TvHomeScreen; the header-pill initial focus only
        // grabs when no saved row focus exists (fresh entry).
        var focusRow by rememberSaveable { mutableStateOf("") }
        var focusItem by rememberSaveable { mutableIntStateOf(-1) }
        LaunchedEffect(Unit) {
            if (focusRow.isEmpty() || focusItem < 0) focusRequester.requestFocus()
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned niches = TV's version of the mobile home chip tabs;
            // pills scroll horizontally like the mobile chip row.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    TvHeaderPill(
                        text = "Settings",
                        onClick = { navController.navigate(TvSettings) },
                        modifier = Modifier.padding(6.dp).focusRequester(focusRequester),
                    )
                }
                item {
                    TvHeaderPill(
                        text = "Niches",
                        onClick = { navController.navigate(TvNiches) },
                        modifier = Modifier.padding(6.dp),
                    )
                }
                item {
                    TvHeaderPill(
                        text = "Search",
                        onClick = { navController.navigate(TvSearch) },
                        modifier = Modifier.padding(6.dp),
                    )
                }
                items(pinnedNiches.toList().mapNotNull { parseNicheRef(it) }) { (id, name) ->
                    TvHeaderPill(
                        text = name,
                        onClick = { navController.navigate(TvFeed(FeedSource.Niche(id, name))) },
                        modifier = Modifier.padding(6.dp),
                    )
                }
                // Custom feeds + pinned tabs consolidated under a More ▾ menu
                // (user: pinned pills made the row unusable) — D-pad opens the
                // dropdown.
                item {
                    Box {
                        var moreOpen by remember { mutableStateOf(false) }
                        TvHeaderPill(
                            text = "More ▾",
                            onClick = { moreOpen = true },
                            modifier = Modifier.padding(6.dp),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = moreOpen,
                            onDismissRequest = { moreOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { androidx.compose.material3.Text("Collections") },
                                onClick = {
                                    moreOpen = false
                                    navController.navigate(TvCollections)
                                },
                            )
                            // Custom feeds (#10): builder + management disabled
                            // for now; DB rows and the BLOCKED-feed filter stage
                            // keep working underneath (Feature.CUSTOM_FEEDS).
                            if (Feature.CUSTOM_FEEDS) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("My feeds") },
                                    onClick = {
                                        moreOpen = false
                                        navController.navigate(TvCustomFeeds)
                                    },
                                )
                                customFeeds.toList().forEach { def ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { androidx.compose.material3.Text(def.name) },
                                        onClick = {
                                            moreOpen = false
                                            navController.navigate(
                                                TvFeed(
                                                    FeedSource.Custom(
                                                        def.id,
                                                        def.name,
                                                        def.sourcesJson.split(',').filter { it.isNotBlank() },
                                                    ),
                                                ),
                                            )
                                        },
                                    )
                                }
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
                                            navController.navigate(
                                                TvFeed(
                                                    if (ref.startsWith("user:")) {
                                                        FeedSource.Creator(ref.removePrefix("user:"))
                                                    } else {
                                                        FeedSource.Niche(ref, name)
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TvHomeScreen(
                onOpenGif = { gifs, index -> navController.navigate(TvPlayer(gifs, index)) },
                onOpenCreator = { username ->
                    navController.navigate(TvFeed(FeedSource.Creator(username = username)))
                },
                onOpenNiche = { niche -> navController.navigate(TvFeed(niche)) },
                // Slice 10: quick-actions "Open feed" full-page path.
                onOpenFeed = { source -> navController.navigate(TvFeed(source)) },
                playerFactory = playerFactory,
                dataSaver = dataSaver,
                homeViewModel = hiltViewModel(),
                focusRow = focusRow,
                focusItem = focusItem,
                onFocusSaved = { row, item ->
                    if (focusRow != row || focusItem != item) {
                        focusRow = row
                        focusItem = item
                    }
                },
            )
        }
    }
}

/**
 * TV home-header pill. Issue-#7 §4 component audit: the shared
 * GiffyPillButton is replaced on TV by the tv-material Button (native D-pad
 * focus states). Rest look keeps today's: neutral 8% fill + 1dp primary
 * border; focus carries the issue tokens (§3.1): primaryContainer fill,
 * onPrimaryContainer content, 3dp primary border.
 */
@Composable
private fun TvHeaderPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        scale = ButtonScale.None,
        colors =
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        border =
            ButtonDefaults.border(
                border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)),
                focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
            ),
        shape =
            ButtonDefaults.shape(
                shape = RoundedCornerShape(28.dp), // = GiffyShapes.extraLarge
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = modifier,
    ) {
        TvText(text)
    }
}
