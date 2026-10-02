package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "id|name" niche-ref packing: pins store bare, custom-feed refs are prefixed. */
class NicheRefTest {
    @Test
    fun `parses bare pin format`() {
        assertEquals("7" to "Amateur", parseNicheRef("7|Amateur"))
    }

    @Test
    fun `parses prefixed custom-feed format`() {
        assertEquals("7" to "Amateur", parseNicheRef("niche:7|Amateur"))
    }

    @Test
    fun `name may contain pipes`() {
        assertEquals("7" to "A|B", parseNicheRef("7|A|B"))
    }

    @Test
    fun `non-pair returns null`() {
        assertNull(parseNicheRef("noseparator"))
    }

    @Test
    fun `pack round-trips`() {
        assertEquals("niche:7|Amateur", packNicheRef("7", "Amateur", prefixed = true))
        assertEquals("7|Amateur", packNicheRef("7", "Amateur"))
        assertEquals("7" to "Amateur", parseNicheRef(packNicheRef("7", "Amateur")))
    }
}
