package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T5 un-settle guard: an un-settle only kills the preview it settled. */
class FocusPreviewTest {
    @Test
    fun `un-settle from a different card does not clear a newer settle`() {
        // Card B settled after card A walked away; A's late un-settle (gif A)
        // must NOT clear B's settled preview.
        assertFalse(FocusPreview.clearsSettledPreview(settledId = "b", unsettingId = "a"))
    }

    @Test
    fun `un-settle of the settled card clears it`() {
        assertTrue(FocusPreview.clearsSettledPreview(settledId = "a", unsettingId = "a"))
    }

    @Test
    fun `un-settle with nothing settled never clears`() {
        assertFalse(FocusPreview.clearsSettledPreview(settledId = null, unsettingId = "a"))
    }
}
