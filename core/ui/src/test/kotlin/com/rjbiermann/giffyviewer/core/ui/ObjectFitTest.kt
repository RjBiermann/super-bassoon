package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #28: the video-fit → tile ContentScale mapping mirrors the
 *  SettingsRepository contract (fit | crop; legacy stretch de-maps upstream). */
class ObjectFitTest {
    @Test
    fun `video-fit pref maps to tile content scale`() {
        assertEquals(ContentScale.Crop, "crop".toTileContentScale())
        assertEquals(ContentScale.Fit, "fit".toTileContentScale())
        // Legacy "stretch" (de-mapped upstream) and anything unknown ride the
        // fit default.
        assertEquals(ContentScale.Fit, "stretch".toTileContentScale())
        assertEquals(ContentScale.Fit, null.toTileContentScale())
        assertEquals(ContentScale.Fit, "".toTileContentScale())
    }
}
