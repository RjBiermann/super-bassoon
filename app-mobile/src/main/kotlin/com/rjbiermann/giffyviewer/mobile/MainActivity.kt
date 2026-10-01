package com.rjbiermann.giffyviewer.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.core.ui.LayoutHint
import com.rjbiermann.giffyviewer.core.ui.PinLockScreen
import com.rjbiermann.giffyviewer.core.ui.layoutHint
import com.rjbiermann.giffyviewer.feature.feed.CollectionsScreen
import com.rjbiermann.giffyviewer.feature.feed.CollectionsViewModel
import com.rjbiermann.giffyviewer.feature.feed.CustomFeedsScreen
import com.rjbiermann.giffyviewer.feature.feed.CustomFeedsViewModel
import com.rjbiermann.giffyviewer.feature.feed.ExploreScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.FollowingScreen
import com.rjbiermann.giffyviewer.feature.feed.FollowingViewModel
import com.rjbiermann.giffyviewer.feature.feed.GroupsScreen
import com.rjbiermann.giffyviewer.feature.feed.GroupsViewModel
import com.rjbiermann.giffyviewer.feature.feed.NicheAboutScreen
import com.rjbiermann.giffyviewer.feature.feed.NicheJoinViewModel
import com.rjbiermann.giffyviewer.feature.feed.NichesScreen
import com.rjbiermann.giffyviewer.feature.feed.PlayerScreen
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import com.rjbiermann.giffyviewer.search.SearchScreen
import com.rjbiermann.giffyviewer.search.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    @Inject lateinit var api: com.rjbiermann.giffyviewer.core.network.GifsApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThemeHost() }
    }

    /** Reads PLAN §9 theme options and applies them app-wide. */
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun ThemeHost() {
        val amoled by settings.amoled.collectAsStateWithLifecycle(initialValue = false)
        val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
        // One adaptive seam (PLAN §9): width class computed here, hint passed down.
        val windowSize = calculateWindowSizeClass(this)
        val columnsOverride by settings.gridColumns.collectAsStateWithLifecycle(initialValue = 0)
        val hint = layoutHint(windowSize.widthSizeClass, columnsOverride)
        GiffyTheme(amoled = amoled, dynamicColor = dynamicColor) { Host(hint) }
    }

    @Composable
    private fun Host(hint: LayoutHint) {
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        // Route stack (best practice + PLAN §2 process-death rule): saveable so
        // the open surface survives process death; one BackHandler pops the TOP
        // route instead of letting system back kill the activity.
        var playerStartIndex by rememberSaveable { mutableStateOf<Int?>(null) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showNiches by rememberSaveable { mutableStateOf(false) }
        var showGroups by rememberSaveable { mutableStateOf(false) }
        var showExplore by rememberSaveable { mutableStateOf(false) }
        var showFollowing by rememberSaveable { mutableStateOf(false) }
        var showCollections by rememberSaveable { mutableStateOf(false) }
        var aboutNiche: Pair<String, String>? by rememberSaveable { mutableStateOf(null) }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        var showCustomFeeds by rememberSaveable { mutableStateOf(false) }

        androidx.activity.compose.BackHandler(
            enabled =
                playerStartIndex != null ||
                    aboutNiche != null ||
                    showSettings ||
                    showNiches ||
                    showGroups ||
                    showExplore ||
                    showFollowing ||
                    showCollections ||
                    showSearch ||
                    showCustomFeeds,
        ) {
            when {
                playerStartIndex != null -> playerStartIndex = null
                aboutNiche != null -> aboutNiche = null
                showSearch -> showSearch = false
                showCustomFeeds -> showCustomFeeds = false
                showFollowing -> showFollowing = false
                showExplore -> showExplore = false
                showGroups -> showGroups = false
                showNiches -> showNiches = false
                showCollections -> showCollections = false
                else -> showSettings = false
            }
        }

        val pinHash by settings.pinHash.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by rememberSaveable { mutableStateOf(false) }
        if (pinHash != null && !unlocked) {
            PinLockScreen(
                onUnlock = { unlocked = true },
                verifyPin = { settings.verifyPin(it) },
            )
        } else if (confirmed && playerStartIndex != null) {
            val start = playerStartIndex ?: 0
            PlayerScreen(
                startIndex = start,
                onBack = { playerStartIndex = null },
                onOpenAccount = { showSettings = true },
                viewModel = hiltViewModel(),
                playerFactory = playerFactory,
                settings = settings,
                db = db,
            )
        } else if (confirmed && showExplore) {
            val feedViewModel: FeedViewModel = hiltViewModel()
            ExploreScreen(
                onBack = { showExplore = false },
                onOpenCreator = { username ->
                    showExplore = false
                    feedViewModel.open(FeedSource.Creator(username = username))
                },
                api = api,
            )
        } else if (confirmed && showCollections) {
            val collectionsVm: CollectionsViewModel = hiltViewModel()
            CollectionsScreen(
                onBack = { showCollections = false },
                viewModel = collectionsVm,
            )
        } else if (confirmed && aboutNiche != null) {
            val (id, name) = aboutNiche ?: Pair("", "")
            val aboutFeedVm: FeedViewModel = hiltViewModel()
            val joinVm: NicheJoinViewModel = hiltViewModel()
            NicheAboutScreen(
                nicheId = id,
                nicheName = name,
                onBack = { aboutNiche = null },
                onOpenCreator = { username ->
                    aboutNiche = null
                    aboutFeedVm.open(FeedSource.Creator(username = username))
                },
                onOpenNiche = { niche ->
                    aboutNiche = null
                    aboutFeedVm.open(niche)
                },
                api = api,
                joinViewModel = joinVm,
            )
        } else if (confirmed && showFollowing) {
            val followingVm: FollowingViewModel = hiltViewModel()
            val feedVm: FeedViewModel = hiltViewModel()
            FollowingScreen(
                onBack = { showFollowing = false },
                onOpenCreator = { username ->
                    showFollowing = false
                    feedVm.open(FeedSource.Creator(username = username))
                },
                onOpenNiche = { niche ->
                    showFollowing = false
                    feedVm.open(niche)
                },
                viewModel = followingVm,
            )
        } else if (confirmed && showCustomFeeds) {
            val customFeedsVm: CustomFeedsViewModel = hiltViewModel()
            val feedViewModel: FeedViewModel = hiltViewModel()
            CustomFeedsScreen(
                onBack = { showCustomFeeds = false },
                onOpenFeed = { custom ->
                    showCustomFeeds = false
                    feedViewModel.open(custom)
                },
                viewModel = customFeedsVm,
            )
        } else if (confirmed && showGroups) {
            val groupsViewModel: GroupsViewModel = hiltViewModel()
            val feedViewModel2: FeedViewModel = hiltViewModel()
            GroupsScreen(
                onBack = { showGroups = false },
                onOpenGroup = { group ->
                    showGroups = false
                    feedViewModel2.open(group)
                },
                viewModel = groupsViewModel,
            )
        } else if (confirmed && showSearch) {
            val searchViewModel: SearchViewModel = hiltViewModel()
            val feedViewModel: FeedViewModel = hiltViewModel()
            SearchScreen(
                onBack = { showSearch = false },
                onSubmit = { q ->
                    showSearch = false
                    feedViewModel.open(FeedSource.Search(query = q))
                },
                viewModel = searchViewModel,
            )
        } else if (confirmed && showNiches) {
            val feedViewModel: FeedViewModel = hiltViewModel()
            NichesScreen(
                onBack = { showNiches = false },
                onOpenNiche = { niche: FeedSource.Niche ->
                    showNiches = false
                    feedViewModel.open(niche)
                },
                onOpenAbout = { id, name -> aboutNiche = id to name },
                api = api,
                settings = settings,
            )
        } else if (confirmed && showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else if (confirmed) {
            FeedScreen(
                modifier = Modifier.fillMaxSize(),
                gridColumns = hint.gridColumns,
                onOpenPlayer = { index -> playerStartIndex = index },
                onOpenSettings = { showSettings = true },
                onOpenSearch = { showSearch = true },
                onOpenNiches = { showNiches = true },
                onOpenGroups = { showGroups = true },
                onOpenCustomFeeds = { showCustomFeeds = true },
                onOpenExplore = { showExplore = true },
                onOpenFollowing = { showFollowing = true },
                onOpenCollections = { showCollections = true },
            )
        } else {
            val scope = rememberCoroutineScope()
            AgeGate(
                onConfirmed = { scope.launch { settings.confirmAge() } },
                onExit = ::finishAffinity,
            )
        }
    }
}

/**
 * PLAN §3: one-time full-screen attestation; start destination until confirmed;
 * no content loads before confirmation — FeedScreen isn't even composed here.
 */
@Composable
private fun AgeGate(
    onConfirmed: () -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Giffy Viewer",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    "This app shows adult content. You must be 18 or older. " +
                        "Not affiliated with or endorsed by upstream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = { scope.launch { onConfirmed() } }) {
                Text("I am 18 or older — Enter")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit) { Text("Exit (leaves app)") }
        }
    }
}
