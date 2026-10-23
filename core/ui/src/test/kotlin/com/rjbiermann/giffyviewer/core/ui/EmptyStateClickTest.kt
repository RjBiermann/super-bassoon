package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #61: the empty+error feed card promises "Tap to retry" but EmptyState
 * was never clickable — pins that the whole card fires [EmptyState]'s onClick
 * and that omitting it keeps the card non-clickable for all other callers.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EmptyStateClickTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tapping the card fires onClick once per tap`() {
        val clicks = mutableIntStateOf(0)
        rule.setContent {
            GiffyTheme {
                EmptyState(
                    message = "Something went wrong",
                    hint = "Tap to retry, or pull down.",
                    onClick = { clicks.intValue++ },
                )
            }
        }
        assertEquals(0, clicks.intValue)
        rule.onNodeWithText("Something went wrong").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks.intValue)
    }

    @Test
    fun `without onClick the card stays non-clickable`() {
        rule.setContent {
            GiffyTheme { EmptyState(message = "No favorites yet") }
        }
        val node = rule.onNodeWithText("No favorites yet").fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsActions.OnClick))
    }
}
