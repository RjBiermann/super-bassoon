package com.rjbiermann.giffyviewer.mobile

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.feature.feed.PlayerScreen
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Issue #50 stage 3: the FULL PlayerPage state machine on device — tap
 * toggles controls WITHOUT pausing (issue-#2 semantics), 3s idle auto-hide
 * clears the text cluster, hold engages 2× and restores speed on release,
 * double-tap likes through the VM's optimistic write path, and the issue-#51
 * "Show playback controls" custom accessibility action re-reveals.
 *
 * Runs against the hand-wired app graph ([TestFeedGraph]): real
 * FeedViewModel/repository/network stack over a local MockWebServer that
 * serves one trending gif whose stream is a small WAV asset — ExoPlayer is
 * really READY/playing, so isPlaying/controls state is genuine. No login and
 * no production-DI changes: the synthetic token exercises isLoggedIn purely
 * to open the like write path.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenMachineTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var graph: TestFeedGraph
    private lateinit var player: GiffyPlayer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        graph = TestFeedGraph(context)
        // The asset ships in the TEST apk's source set — read it from the
        // instrumentation context, not the target app's.
        val wav =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open(MEDIA_ASSET)
                .use { it.readBytes() }
        graph.startHappy(mediaPath = "/media.wav", wav = wav)
        // Synthetic sign-in: isLoggedIn drives the double-tap like write path.
        assertTrue(graph.tokenStore.save("test.jwt.token"))
        // Park OUR player in the VM so the test holds the instance PlayerScreen
        // adopts (issue-#9 adoption path) and can read its real state.
        player = sharedFactory.create(context)
        // Media3 requires main-thread access; the park path pauses the player.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            graph.viewModel.returnInlinePlayer(player)
        }
    }

    @After
    fun tearDown() {
        // Player release is deferred to @AfterClass (after the composition is
        // torn down): PlayerScreen's dispose calls returnInlinePlayer → pause,
        // which throws on an already-released ExoPlayer.
        createdPlayers.add(player)
        graph.tearDown()
    }

    private fun setContent() {
        rule.setContent {
            GiffyTheme {
                PlayerScreen(
                    startIndex = 0,
                    onBack = {},
                    onOpenAccount = {},
                    onOpenFeed = {},
                    viewModel = graph.viewModel,
                    playerFactory = sharedFactory,
                    settings = graph.settings,
                    db = graph.db,
                )
            }
        }
    }

    private fun awaitFeedItem() {
        // The feed item (and thus the playing machine) is up when the
        // description text of the fixture gif renders.
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText(DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun isPlayerPlaying(): Boolean {
        val playing = AtomicBoolean(false)
        rule.runOnUiThread { playing.set(player.isPlaying) }
        return playing.get()
    }

    private fun awaitPlayerSpeed(target: Float) {
        rule.waitUntil(10_000) {
            var speed = 0f
            rule.runOnUiThread { speed = player.playbackParameters.speed }
            speed == target
        }
    }

    /** "Show playback controls" custom action (issue #51) — invoke on the player body node. */
    private fun revealViaCustomAction() {
        val node: SemanticsNodeInteraction =
            rule.onNode(
                SemanticsMatcher("has show-controls custom action") {
                    it.config
                        .getOrNull(SemanticsActions.CustomActions)
                        ?.any { a -> a.label == "Show playback controls" } == true
                },
            )
        val actions =
            node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!
        rule.runOnIdle { assertTrue(actions.first().action()) }
    }

    /**
     * Real-time hold: injects DOWN on the decor view and posts the UP after
     * [holdMs] on the main thread — compose's performTouchInput can't hold a
     * pointer across calls, and the player's hold branch keys on real elapsed
     * time (longPressTimeout), not event timestamps.
     */
    private fun holdByTouch(holdMs: Long) {
        val decor = rule.activity.window.decorView
        val x = decor.width / 2f
        val y = decor.height / 2f
        rule.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            decor.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
            decor.postDelayed(
                {
                    val up = SystemClock.uptimeMillis()
                    decor.dispatchTouchEvent(MotionEvent.obtain(up, up, MotionEvent.ACTION_UP, x, y, 0))
                },
                holdMs,
            )
        }
    }

    @Test
    fun tapTogglesControls_and_neverPauses_issue2() {
        setContent()
        awaitFeedItem()
        rule.onRoot().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(DESCRIPTION).fetchSemanticsNodes().isEmpty() }
        // Hidden overlay must not have stopped the player.
        rule.waitUntil(10_000) { isPlayerPlaying() }
        rule.onRoot().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
        }
        // Re-revealed controls show the PAUSE affordance — i.e. playback
        // continued through both taps (the play affordance must be absent).
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("pause").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("play").assertDoesNotExist()
    }

    @Test
    fun controlsAutoHide3s_andA11yRevealAction() {
        setContent()
        awaitFeedItem()
        // Ticker isPlaying=true → the 3s idle timer hides the text cluster;
        // AnimatedVisibility leaves composition, so the node is really gone.
        rule.waitUntil(30_000) { rule.onAllNodesWithText(DESCRIPTION).fetchSemanticsNodes().isEmpty() }
        // Playback continues under the hidden overlay (the progress line is
        // semantics-free — its always-on rendering stays device-gate evidence).
        assertTrue(isPlayerPlaying())
        revealViaCustomAction()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(DESCRIPTION).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun holdEngages2xSpeed_releaseRestores() {
        setContent()
        awaitFeedItem()
        holdByTouch(holdMs = 1_200)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(HOLD_CHIP).fetchSemanticsNodes().isNotEmpty() }
        awaitPlayerSpeed(2f)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(HOLD_CHIP).fetchSemanticsNodes().isEmpty() }
        awaitPlayerSpeed(1f)
        assertTrue(isPlayerPlaying())
    }

    @Test
    fun doubleTap_likes_viaOptimisticWritePath() {
        setContent()
        awaitFeedItem()
        // Give the on-open syncLikes() its turn so it cannot race the flip.
        Thread.sleep(1_000)
        rule.onRoot().performTouchInput { doubleClick() }
        rule.waitUntil(10_000) {
            graph.viewModel.likedIds.value
                .contains(GIF_ID)
        }
        // The write is confirmed (server 204) — the optimistic flip sticks.
        Thread.sleep(1_500)
        assertTrue(
            graph.viewModel.likedIds.value
                .contains(GIF_ID),
        )
    }

    companion object {
        private const val GIF_ID = "m-test-1"
        private const val DESCRIPTION = "machine-test-gif"
        private const val HOLD_CHIP = "2× speed"
        private const val MEDIA_ASSET = "silence_5s.wav"

        /** One SimpleCache for the whole class — a second live instance on the
         *  same folder is rejected, and the factory's players release per test. */
        private lateinit var sharedFactory: GiffyPlayerFactory
        private val createdPlayers = mutableListOf<GiffyPlayer>()

        @BeforeClass
        @JvmStatic
        fun initCache() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cache =
                SimpleCache(
                    File(context.cacheDir, "test-player-machine-cache"),
                    LeastRecentlyUsedCacheEvictor(64L * 1024 * 1024),
                    StandaloneDatabaseProvider(context),
                )
            sharedFactory = GiffyPlayerFactory(cache)
        }

        @AfterClass
        @JvmStatic
        fun releasePlayers() {
            // ExoPlayer release must run on the main thread.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                createdPlayers.forEach { it.release() }
            }
        }
    }
}
