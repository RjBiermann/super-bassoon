package com.rjbiermann.giffyviewer.mobile

import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.AgeGate
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.core.ui.LayoutHint
import com.rjbiermann.giffyviewer.core.ui.PinLockScreen
import com.rjbiermann.giffyviewer.core.ui.layoutHint
import com.rjbiermann.giffyviewer.feature.auth.DebugSignIn
import com.rjbiermann.giffyviewer.feature.feed.CollectionsScreen
import com.rjbiermann.giffyviewer.feature.feed.CustomFeedsScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.FollowingScreen
import com.rjbiermann.giffyviewer.feature.feed.NicheAboutScreen
import com.rjbiermann.giffyviewer.feature.feed.NichesScreen
import com.rjbiermann.giffyviewer.feature.feed.PlayerScreen
import com.rjbiermann.giffyviewer.feature.feed.feedSourceNavType
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import com.rjbiermann.giffyviewer.search.SearchScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.reflect.typeOf

/**
 * Issue #13 nav routes (mobile). Route args are @Serializable
 * (FeedSource/Gif/NicheRef carry the same annotation family); the serialized
 * form lives only inside nav saved state — never a public format.
 */
@Serializable
data object MainCollections

/** The home feed as seeded (start destination): the VM resolves `seedSource`
 *  (For You when logged in per batch-21, else Trending) with no route arg. */
@Serializable
data object MainFeedDefault

/** Every feed hop after the start (non-null by construction — no encodeNull
 *  against the custom NavType's non-null serializeAsValue Contract). */
@Serializable
data class MainFeed(
    val source: FeedSource,
)

@Serializable
data object MainCustomFeeds

@Serializable
data object MainFollowing

@Serializable
data class MainNicheAbout(
    val id: String,
    val name: String,
)

@Serializable
data object MainNiches

/** Feed-scoped player: pages the SHARED FeedViewModel's live pager (§3.3). */
@Serializable
data class MainPlayer(
    val startIndex: Int,
)

/** Stays on the stack after submit — BACK returns to the search draft (§8.4). */
@Serializable
data object MainSearch

/** One destination; E's SettingsPage sub-panels stay in-screen (§6). */
@Serializable
data object MainSettings

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var tokenStore: TokenStore

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug-only sign-in injection (Stream-G device gates): the token
        // rides the launch extra in debug builds; release never reaches it.
        if (com.rjbiermann.giffyviewer.mobile.BuildConfig.DEBUG) {
            DebugSignIn.apply(tokenStore, intent?.getStringExtra("giffy_debug_bundle"))
        }
        enableEdgeToEdge()
        setContent { ThemeHost() }
    }

    /** Reads PLAN §9 theme options and applies them app-wide. */
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun ThemeHost() {
        val amoled by settings.amoled.collectAsStateWithLifecycle(initialValue = false)
        // One adaptive seam (PLAN §9): width class computed here, hint passed down.
        val windowSize = calculateWindowSizeClass(this)
        val columnsOverride by settings.gridColumns.collectAsStateWithLifecycle(initialValue = 0)
        val hint = layoutHint(windowSize.widthSizeClass, columnsOverride)
        GiffyTheme(amoled = amoled) { Host(hint) }
    }

    @Composable
    private fun Host(hint: LayoutHint) {
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        // Age gate + PIN stay OUTSIDE the NavHost (issue #13 §3.2: they gate
        // the app, not pages). The parallel rememberSaveable route flags and
        // the when-chain BackHandler they served are replaced by route graph
        // entries; route state survives process death through the nav stack.
        val pinHash by settings.pinHash.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by rememberSaveable { mutableStateOf(false) }
        if (pinHash != null && !unlocked) {
            PinLockScreen(
                onUnlock = { unlocked = true },
                verifyPin = { settings.verifyPin(it) },
            )
        } else if (confirmed) {
            MobileNavHost(hint)
        } else {
            AgeGate(
                onConfirmed = { settings.confirmAge() },
                onExit = ::finishAffinity,
            )
        }
    }

    @Composable
    private fun MobileNavHost(hint: LayoutHint) {
        // ONE FeedViewModel, ACTIVITY-scoped (issue #13 §3.3): acquired at
        // NavHost scope and passed by parameter — inside a destination's
        // content hiltViewModel() defaults to the NavBackStackEntry store,
        // which would break the PlayerScreen pager/likes seams overnight.
        val feedViewModel: FeedViewModel = hiltViewModel()
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        // §9 player lifecycle (was keyed on the playerStartIndex flag): the
        // screen stays on while the swipe player destination is on top; a
        // covered player still holds the flag — settings overlays may appear
        // above it via onOpenAccount (§8.6) without toggling the flag.
        DisposableEffect(backStackEntry?.destination?.hasRoute<MainPlayer>() == true) {
            if (backStackEntry?.destination?.hasRoute<MainPlayer>() == true) {
                window.addFlags(FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(FLAG_KEEP_SCREEN_ON)
            }
            onDispose { window.clearFlags(FLAG_KEEP_SCREEN_ON) }
        }
        NavHost(navController, startDestination = MainFeedDefault) {
            // Start destination: NO route arg — the VM resolves seedSource
            // (For You when logged-in, else Trending; batch-21 rule). Feed hops
            // use MainFeed(source) with the non-null custom-NavType arg.
            composable<MainFeedDefault> {
                MainFeedDestination(src = null, hint, navController, feedViewModel)
            }
            composable<MainFeed>(
                typeMap = mapOf(typeOf<FeedSource>() to feedSourceNavType),
            ) { entry ->
                MainFeedDestination(
                    src = entry.toRoute<MainFeed>().source,
                    hint,
                    navController,
                    feedViewModel,
                )
            }

            composable<MainPlayer> { entry ->
                val start = entry.toRoute<MainPlayer>().startIndex
                PlayerScreen(
                    startIndex = start,
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { navController.navigate(MainSettings) },
                    onOpenFeed = { source ->
                        // §3.3: the overlay opens ARE exits (pre-nav semantics:
                        // onOpen→onBack). Push the feed and pop this player
                        // entry — BACK then lands the covered feed with its
                        // scroll state, not the dead slot this player left.
                        navController.navigate(MainFeed(source)) {
                            popUpTo(entry.id) { inclusive = true }
                        }
                    },
                    viewModel = feedViewModel,
                    playerFactory = playerFactory,
                    settings = settings,
                    db = db,
                )
            }
            composable<MainSearch> {
                // Issue #13 §8.4: submit STAYS on the stack — the second BACK
                // returns to the draft/history instead of home (pre-nav anyway
                // rendered the feed in place); creator/niche/strip rows open
                // feeds over the preserved search state the same way.
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onSubmit = { q -> navController.navigate(MainFeed(FeedSource.Search(query = q))) },
                    onOpenCreator = { username ->
                        navController.navigate(MainFeed(FeedSource.Creator(username = username)))
                    },
                    onOpenNiche = { id, name -> navController.navigate(MainFeed(FeedSource.Niche(id, name))) },
                    onOpenFeed = { source -> navController.navigate(MainFeed(source)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<MainNiches> {
                NichesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNiche = { niche -> navController.navigate(MainFeed(niche)) },
                    onOpenAbout = { id, name -> navController.navigate(MainNicheAbout(id, name)) },
                )
            }
            composable<MainNicheAbout> { entry ->
                val about = entry.toRoute<MainNicheAbout>()
                NicheAboutScreen(
                    nicheId = about.id,
                    nicheName = about.name,
                    onBack = { navController.popBackStack() },
                    // Issue #13: no manual flag clears — the About screen stays
                    // on the stack and BACK walks it before the feed below.
                    onOpenCreator = { username ->
                        navController.navigate(MainFeed(FeedSource.Creator(username = username)))
                    },
                    onOpenNiche = { niche -> navController.navigate(MainFeed(niche)) },
                    viewModel = hiltViewModel(),
                    joinViewModel = hiltViewModel(),
                )
            }
            composable<MainFollowing> {
                FollowingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCreator = { username ->
                        navController.navigate(MainFeed(FeedSource.Creator(username = username)))
                    },
                    onOpenNiche = { niche -> navController.navigate(MainFeed(niche)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<MainCollections> {
                CollectionsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = hiltViewModel(),
                )
            }
            composable<MainCustomFeeds> {
                CustomFeedsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFeed = { feed -> navController.navigate(MainFeed(feed)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<MainSettings> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    @Composable
    private fun MainFeedDestination(
        src: FeedSource?,
        hint: LayoutHint,
        navController: androidx.navigation.NavHostController,
        feedViewModel: FeedViewModel,
    ) {
        val srcResolved = src ?: feedViewModel.seedSource
        // Issue #13 §3.3: every feed entry re-asserts its source on
        // (re)composition — the VM stays single-sourced (sort adoption,
        // userNavigated, lastGroupSub semantics all inside open()) while
        // each entry keeps its own scroll state. Back into a covered
        // entry whose source already matches is a no-op (same-value
        // StateFlow write → no pager restart, no grid flash).
        LaunchedEffect(srcResolved) {
            if (feedViewModel.source.value != srcResolved) feedViewModel.open(srcResolved)
        }
        FeedScreen(
            modifier = Modifier.fillMaxSize(),
            gridColumns = hint.gridColumns,
            onOpenPlayer = { index -> navController.navigate(MainPlayer(index)) },
            onOpenSettings = { navController.navigate(MainSettings) },
            onOpenSearch = { navController.navigate(MainSearch) },
            onOpenNiches = { navController.navigate(MainNiches) },
            onOpenFollowing = { navController.navigate(MainFollowing) },
            onOpenCollections = { navController.navigate(MainCollections) },
            onOpenCustomFeeds = { navController.navigate(MainCustomFeeds) },
            onFeedNav = { source -> navController.navigate(MainFeed(source)) },
            playerFactory = playerFactory,
            viewModel = feedViewModel,
        )
    }
}
