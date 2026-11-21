package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue-#51 hit-area class, second call site: a clickable [CreatorLabel] used
 * to measure only its caption (~24dp tall) — the tile caption and the player's
 * chips were each grown to Material's 48dp minimum, the player's creator label
 * was missed. The invariant now lives in the component.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CreatorLabelTapTargetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `clickable label exposes at least a 48dp tap target`() {
        rule.setContent {
            GiffyTheme {
                CreatorLabel("someone", verified = true, onClick = {})
            }
        }

        val target = rule.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        val height = target.bottom - target.top
        val width = target.right - target.left
        assertTrue("tap target too short: $height", height >= 48.dp)
        assertTrue("tap target too narrow: $width", width >= 48.dp)
    }

    /** The caption still measures to its own size when it does not navigate. */
    @Test
    fun `non-clickable label keeps its intrinsic height`() {
        rule.setContent {
            GiffyTheme {
                CreatorLabel("someone", verified = true)
            }
        }

        val caption = rule.onNodeWithText("@someone").getUnclippedBoundsInRoot()
        assertTrue("caption grew without a click: ${caption.bottom - caption.top}", caption.bottom - caption.top < 48.dp)
    }
}
