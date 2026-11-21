package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.dto.GifDtoShell
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import com.rjbiermann.giffyviewer.core.network.dto.toModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * No-ads rule, stage 0 (AGENTS-CONTENT-FILTER.md): a `promoted: true` payload
 * item is a paid placement — it must never become a model, so no surface can
 * render it and no cache row / hide count is written for it.
 */
class PromotedGifDropTest {
    @Test
    fun `promoted gif never becomes a model`() {
        assertNull(GifDtoShell(id = "paid-1", userName = "sponsor", promoted = true).toModel())
    }

    @Test
    fun `organic and absent-promoted gifs pass through`() {
        assertNotNull(GifDtoShell(id = "g1").toModel())
        assertNotNull(GifDtoShell(id = "g2", promoted = false).toModel())
    }

    @Test
    fun `page mapping keeps only organic items`() {
        val page =
            GifsPageDto(
                gifs =
                    listOf(
                        GifDtoShell(id = "a"),
                        GifDtoShell(id = "paid", promoted = true),
                        GifDtoShell(id = "b", promoted = false),
                    ),
            )
        assertEquals(listOf("a", "b"), page.toModels().map { it.id })
    }
}
