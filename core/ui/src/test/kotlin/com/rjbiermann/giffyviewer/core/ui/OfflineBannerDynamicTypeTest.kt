package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Dynamic Type: the banner's 40dp strip is a MINIMUM — at large system font
 * scales the copy wraps and the strip grows with it instead of clipping
 * (regression: fixed 40dp + `maxLines = 1` truncated the sentence).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineBannerDynamicTypeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `largest text size grows the strip and keeps the whole copy`() {
        rule.setContent {
            GiffyTheme {
                Column {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 3f)) {
                        OfflineBanner(Modifier.testTag("large-scale"))
                    }
                }
            }
        }

        val strip = rule.onNodeWithTag("large-scale").getUnclippedBoundsInRoot()
        val copy = rule.onNodeWithText(OFFLINE_COPY).getUnclippedBoundsInRoot()
        val stripHeight = strip.bottom - strip.top
        val copyHeight = copy.bottom - copy.top
        // The strip is a minimum, never shorter than 40dp …
        assertTrue("expected the 40dp band as a minimum, got $stripHeight", stripHeight >= 40.dp)
        // … and it grows past the copy instead of clipping it into a fixed band.
        assertTrue("strip $stripHeight must contain the $copyHeight tall copy", stripHeight > copyHeight)
        assertTrue("copy clipped at the top", copy.top >= strip.top)
        assertTrue("copy clipped at the bottom", copy.bottom <= strip.bottom)
    }

    private companion object {
        const val OFFLINE_COPY = "You're offline — showing cached gifs"
    }
}
