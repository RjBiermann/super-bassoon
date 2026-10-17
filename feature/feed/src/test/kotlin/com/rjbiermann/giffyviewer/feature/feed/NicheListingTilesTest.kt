package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.NichePreviewNicheDto
import com.rjbiermann.giffyviewer.core.network.NichePreviewRowDto
import com.rjbiermann.giffyviewer.core.network.dto.GifDtoShell
import com.rjbiermann.giffyviewer.core.network.dto.UrlsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #47 slice 3 (DESIGN-47s3 §2): the niches listing renders preview-tile
 * rows from `v2/niches/search/previews`. Tile merge rules (leak-zero + dedupe
 * + image fallback) — the same shape feature/search's nicheRows enforces for
 * the search scope.
 */
class NicheListingTilesTest {
    /** allow = everyone with userName "ok"; rest blocked (any reason). */
    private val allow: (Gif) -> Boolean = { it.userName == "ok" }

    private fun row(
        id: String,
        name: String? = id,
        poster: String? = "https://media/$id-poster.jpg",
        creator: String = "ok",
        nicheCover: String? = null,
        nicheThumb: String? = null,
        subs: Long = 10,
        gifs: Long = 20,
    ): NichePreviewRowDto =
        NichePreviewRowDto(
            niche =
                NichePreviewNicheDto(
                    id = id,
                    name = name,
                    cover = nicheCover,
                    thumbnail = nicheThumb,
                    gifs = gifs,
                    subscribers = subs,
                ),
            gif = GifDtoShell(id = "gif-$id", userName = creator, urls = UrlsDto(poster = poster)),
        )

    @Test
    fun `blocked preview gif drops the row (leak-zero)`() {
        val tiles = nicheListingRows(emptyList(), listOf(row("n1", creator = "blocked")), allow)
        assertTrue(tiles.isEmpty())
    }

    @Test
    fun `blank niche id drops the row`() {
        val tiles =
            nicheListingRows(
                emptyList(),
                listOf(row("n1"), NichePreviewRowDto(niche = NichePreviewNicheDto(id = " ", name = "blank"))),
                allow,
            )
        assertEquals(listOf("n1"), tiles.map { it.id })
    }

    @Test
    fun `null gif keeps the niche tile without an image`() {
        val tiles =
            nicheListingRows(emptyList(), listOf(NichePreviewRowDto(niche = NichePreviewNicheDto(id = "n1", name = "Boobs"))), allow)
        assertEquals(1, tiles.size)
        assertEquals("Boobs", tiles.first().name)
        assertNull(tiles.first().imageUrl)
    }

    @Test
    fun `image falls back niche thumbnail then cover when poster is missing`() {
        val tiles =
            nicheListingRows(
                emptyList(),
                listOf(
                    row("poster-missing", poster = null, nicheThumb = "thumb", nicheCover = "cover"),
                    row("no-poster-no-thumb", poster = null, nicheCover = "cover"),
                    row("nothing", poster = null),
                ),
                allow,
            )
        assertEquals(3, tiles.size)
        assertEquals("thumb", tiles[0].imageUrl)
        assertEquals("cover", tiles[1].imageUrl)
        assertNull(tiles[2].imageUrl)
    }

    @Test
    fun `cross-page ids dedupe keeping the first occurrence`() {
        val page1 = nicheListingRows(emptyList(), listOf(row("n1", subs = 5)), allow)
        val merged = nicheListingRows(page1, listOf(row("n1", subs = 99), row("n2")), allow)
        assertEquals(listOf("n1", "n2"), merged.map { it.id })
        assertEquals(5L, merged.first().subscribers)
    }

    @Test
    fun `counts travel from the preview niche object`() {
        val tiles = nicheListingRows(emptyList(), listOf(row("n1", subs = 633012, gifs = 929005)), allow)
        assertEquals(633012L, tiles.first().subscribers)
        assertEquals(929005L, tiles.first().gifs)
    }
}
