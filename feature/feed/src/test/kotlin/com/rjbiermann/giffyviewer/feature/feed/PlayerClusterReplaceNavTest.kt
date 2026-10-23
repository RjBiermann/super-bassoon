package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Test-local type-safe routes mirroring the app graph shape under test: an
 *  arg-less home, the swipe-player route, the pushed feed route (plain String
 *  arg — the pop mechanics under test key on the route CLASS only). */
@Serializable
data object TestNavHome

@Serializable
data object TestNavPlayer

@Serializable
data class TestNavFeed(
    val source: String,
)

private class NavHostHolder {
    lateinit var nc: NavHostController
}

/**
 * Issue #60 regression: the player cluster-open seam ("click a link to a
 * creator feed" on the player overlay) must REPLACE the player entry with the
 * pushed feed. Before the fix the host popped with `popUpTo(entry.id)` —
 * `NavBackStackEntry.id` is a per-entry UUID String handed to the ROUTE
 * overload, which never matches a destination, so popBackStackInternal logged
 * "Ignoring popBackStack to destination … not found on the current back stack"
 * and kept the player alive UNDER the pushed feed. BACK from the creator feed
 * then relaunched the covered player, which pages the SHARED activity-scoped
 * FeedViewModel — already re-keyed to the creator feed — and played feed 2
 * under feed 1's back position.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerClusterReplaceNavTest {
    @get:Rule
    val rule = createComposeRule()

    private val holder = NavHostHolder()

    private fun setContent(clusterClick: () -> Unit) {
        rule.setContent {
            GiffyTheme {
                val nc = rememberNavController()
                holder.nc = nc
                NavHost(nc, startDestination = TestNavHome) {
                    composable<TestNavHome> { Text("home") }
                    composable<TestNavPlayer> {
                        Text("player")
                        // The player's creator link (PlayerScreen.onOpenCreator):
                        TextButton(onClick = clusterClick) { Text("cluster") }
                    }
                    composable<TestNavFeed> { Text("feed") }
                }
            }
        }
    }

    private fun runRepro(clusterClick: () -> Unit) {
        setContent(clusterClick)
        // "start by playing 1st feed": the player is open over the home feed.
        rule.runOnIdle { holder.nc.navigate(TestNavPlayer) }
        rule.waitUntil(10_000) {
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("cluster"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        // "click a link to 2nd feed (creator feed)" through the host seam.
        rule.onNodeWithText("cluster").performClick()
        rule.runOnIdle {
            val current = holder.nc.currentBackStackEntry!!.destination
            assertTrue(
                "cluster open must replace the player entry with the feed",
                current.hasRoute<TestNavFeed>(),
            )
        }
    }

    @Test
    fun `cluster open replaces the player entry - BACK lands the covered feed`() {
        // The host's seam (MainActivity.kt onOpenFeed / TvMainActivity
        // onOpenCreator/onOpenNiche/onSortChange) — the production helper.
        runRepro {
            holder.nc.navigateReplacing(TestNavFeed(source = "bob"), TestNavPlayer::class)
        }
        rule.runOnIdle {
            holder.nc.popBackStack()
        }
        rule.runOnIdle {
            val current = holder.nc.currentBackStackEntry!!.destination
            assertTrue(
                "BACK from the opened feed must land the covered feed, never the stale player",
                current.hasRoute<TestNavHome>(),
            )
        }
    }
}
