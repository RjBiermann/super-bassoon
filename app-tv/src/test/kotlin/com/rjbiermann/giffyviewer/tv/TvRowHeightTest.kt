package com.rjbiermann.giffyviewer.tv

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV home row size (slice 11): DataStore pref → effective card height. */
class TvRowHeightTest {
    @Test
    fun `default keeps the current 170dp card and no layout delta`() {
        assertEquals(170f, rowHeightFor("default").value, 0f)
        assertEquals(CARD_ROW_HEIGHT_DP, rowHeightFor(""))
        assertEquals(CARD_ROW_HEIGHT_DP, rowHeightFor("anything"))
    }

    @Test
    fun `large and xl scale the default upward`() {
        assertEquals(238f, rowHeightFor("large").value, 0.01f) // 170 × 1.4f
        assertEquals(306f, rowHeightFor("xl").value, 0.01f) // 170 × 1.8f
        assertTrue(rowHeightFor("default") < rowHeightFor("large"))
        assertTrue(rowHeightFor("large") < rowHeightFor("xl"))
    }

    @Test
    fun `reserved height scales with the row height - caption extra constant`() {
        assertEquals(210.dp, reservedHeight(CARD_ROW_HEIGHT_DP))
        assertEquals(rowHeightFor("large") + 40.dp, reservedHeight(rowHeightFor("large")))
    }
}
