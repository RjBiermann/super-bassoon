package com.rjbiermann.giffyviewer.mobile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.feature.feed.FeedMediator
import com.rjbiermann.giffyviewer.feature.feed.FeedScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Issue #50 stage 3: the FeedMediator offline-error branches on device.
 *
 * Offline is a connection-refused local server ([TestFeedGraph.startOffline]):
 * no ConnectivityManager/airplane-mode control is needed (those APIs want
 * shell/system permissions from inside an app test), and the app's REAL
 * network stack then walks the retry backoff and surfaces
 * [FeedMediatorResult.Error] with the IOException — the honest offline
 * behavior (cached rows keep rendering; here the cache is empty, so the UI
 * shows the offline empty state).
 *
 * Cost note: the retry interceptor walks 1s/4s/15s backoff before the failure
 * escapes, so the UI test takes ~10-20s of wall time — asserted via
 * [waitUntil] with a generous budget.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class FeedMediatorOfflineTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var graph: TestFeedGraph

    @Before
    fun setUp() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        graph = TestFeedGraph(context)
        graph.startOffline()
    }

    @After
    fun tearDown() = graph.tearDown()

    private fun state() = PagingState<Int, com.rjbiermann.giffyviewer.core.model.Gif>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    @Test
    fun offlineRefresh_surfaces_MediatorResultError_with_IOException() {
        val mediator =
            FeedMediator(
                feed = FeedSource.Trending,
                gifDao = graph.db.gifDao(),
                pageDao = graph.db.feedPageDao(),
                api = graph.api,
                pageSize = 20,
            )
        val result = AtomicReference<RemoteMediator.MediatorResult>()
        runBlocking { result.set(mediator.load(LoadType.REFRESH, state())) }
        val error = result.get() as? RemoteMediator.MediatorResult.Error
        assertTrue("expected MediatorResult.Error, got ${result.get()}", error != null)
        // Connection-refused surfaces as ConnectException, an IOException subclass.
        assertTrue("expected IOException, got ${error!!.throwable::class.java}", error.throwable is IOException)
    }

    @Test
    fun offlineRefresh_rendersHonestOfflineEmptyState() {
        rule.setContent {
            GiffyTheme {
                FeedScreen(
                    gridColumns = 1,
                    viewModel = graph.viewModel,
                    joinViewModel = graph.joinViewModel,
                )
            }
        }
        // IOException-shaped refresh error with zero rows → EmptyState with the
        // honest offline copy (FeedScreen's offlineShaped branch).
        rule.waitUntil(120_000) {
            rule.onAllNodesWithText("Nothing cached yet").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("You're offline — reconnect to load the feed.")
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
