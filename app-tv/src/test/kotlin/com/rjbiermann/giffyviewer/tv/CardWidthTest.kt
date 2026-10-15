package com.rjbiermann.giffyviewer.tv

import androidx.compose.ui.unit.dp
import com.rjbiermann.giffyviewer.core.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #14 §A: cardWidth clamps made RELATIVE to the band — the common 9:16 pool
 * renders EXACT at every tv_row_height pref step instead of being cropped by
 * the flat 120dp floor at the default 170dp.
 */
class CardWidthTest {
    /** Minimal Gif for aspect tests — only width/height matter here. */
    private fun gif(
        width: Int,
        height: Int,
    ): Gif =
        Gif(
            id = "$width-$height",
            userName = "u",
            description = null,
            tags = emptyList(),
            likes = 0,
            views = 0,
            durationSeconds = 0.0,
            hasAudio = false,
            width = width,
            height = height,
            createDateEpoch = 0,
            published = true,
            avgColor = "",
            sdUrl = null,
            hdUrl = null,
            posterUrl = null,
            niches = emptyList(),
        )

    @Test
    fun `9-16 portrait renders exact at the default height - formerly cropped to the old 120dp floor`() {
        // 170dp × (1080/1920) = 95.625dp — the doc's exact acceptance figure.
        assertEquals(95.625f, cardWidth(gif(1080, 1920), CARD_ROW_HEIGHT_DP).value, 0.001f)
    }

    @Test
    fun `9-16 portrait stays exact at every row-size pref step`() {
        val xl = rowHeightFor("xl")
        assertEquals(306.dp, xl)
        assertEquals(172.125f, cardWidth(gif(1080, 1920), xl).value, 0.001f)
        val large = rowHeightFor("large")
        assertEquals(238.dp, large)
        assertEquals(133.875f, cardWidth(gif(1080, 1920), large).value, 0.01f)
    }

    @Test
    fun `extreme narrow portrait hits the relative floor (half) - ContentScale Fit letterboxes in-color`() {
        // 170 × 0.35 = 59.5dp → floored to 170 × 0.5 = 85dp.
        assertEquals(85f, cardWidth(gif(350, 1000), CARD_ROW_HEIGHT_DP).value, 0.001f)
        // The floor scales with the band (relative clamps): 238 × 0.5 = 119dp.
        assertEquals(119f, cardWidth(gif(350, 1000), rowHeightFor("large")).value, 0.001f)
    }

    @Test
    fun `super-wide landscape and normal landscape hit the relative cap`() {
        // 170 × 1.65 ≈ the classic 280dp card at the default height.
        assertEquals(280.5f, cardWidth(gif(1000, 200), CARD_ROW_HEIGHT_DP).value, 0.001f)
        // 1920/1080 = 1.78 → 302dp wants past the cap.
        assertEquals(280.5f, cardWidth(gif(1920, 1080), CARD_ROW_HEIGHT_DP).value, 0.01f)
    }

    @Test
    fun `unknown dimensions keep the landscape default - now band-relative`() {
        assertEquals(280.5f, cardWidth(gif(0, 0), CARD_ROW_HEIGHT_DP).value, 0.001f)
        assertEquals(rowHeightFor("xl") * 1.65f, cardWidth(gif(0, 0), rowHeightFor("xl")))
    }
}
