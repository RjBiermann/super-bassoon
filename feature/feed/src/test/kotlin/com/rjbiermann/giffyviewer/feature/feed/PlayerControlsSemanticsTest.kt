package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #50 stage 2: the issue-#51 a11y contract — the player body exposes the
 * "Show playback controls" custom action, and invoking it fires the reveal
 * callback. Extracted as [playerControlsSemantics] so the JVM rung can pin it;
 * the full reveal/hide machine stays a stage-3 instrumented test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerControlsSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun revealNode() =
        rule.onNode(
            SemanticsMatcher("has show-controls custom action") {
                it.config
                    .getOrNull(SemanticsActions.CustomActions)
                    ?.any { a -> a.label == "Show playback controls" } == true
            },
        )

    @Test
    fun `player body exposes the Show playback controls custom action and fires the reveal callback`() {
        val revealed = mutableIntStateOf(0)
        rule.setContent {
            GiffyTheme {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .playerControlsSemantics(onReveal = { revealed.intValue++ }),
                )
            }
        }
        val actions =
            revealNode()
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)!!
        assertEquals(1, actions.size)
        rule.runOnIdle { assertTrue(actions.first().action()) }
        assertEquals(1, revealed.intValue)
    }
}
