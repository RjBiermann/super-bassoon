package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #65: the creator surface's on-the-wire order. Live probe 2026-10 on
 * `v2/users/perkyandpink/search` — `order=trending` 200s with `gifs: []` /
 * total 0 (a subset order the creator has no items for), an OMITTED param is
 * also empty, `order=latest` returns the catalogue (20 gifs, total 42). So the
 * creator default is the explicit [CREATOR_DEFAULT_ORDER], and an explicit
 * chip sort always wins over it.
 *
 * Same default for every creator call site: the Creator feed, a `creator:` ref
 * inside a Custom feed, and each Favorites sub-fetch — one spelling
 * ([CREATOR_DEFAULT_ORDER]) or the surfaces drift apart again.
 */
class CreatorOrderTest {
    private class FakePageDao : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

        override fun pageFlow(pageKey: String): Flow<FeedPageEntity?> = throw NotImplementedError()

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) = throw NotImplementedError()

        override suspend fun evictFavorites(prefix: String) = throw NotImplementedError()
    }

    private class FakeGifs : GifDao {
        override suspend fun upsertAll(gifs: List<GifEntity>) = Unit

        override suspend fun byIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun byId(id: String): GifEntity? = null

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()
    }

    /** Records (username, order) as sent — the wire shape is the contract. */
    private class OrderApi : FakeApi() {
        val orders = mutableListOf<Pair<String, String?>>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String?,
        ): GifsPageDto {
            orders.add(username to order)
            return GifsPageDto(gifs = listOf(gifDtoShell("$username-p$page", username)))
        }
    }

    private fun fetcher(
        feed: FeedSource,
        api: OrderApi,
        favorites: suspend () -> List<String> = { emptyList() },
    ) = FeedPageFetcher(
        feed,
        FakeGifs(),
        FakePageDao(),
        api,
        20,
        favorites = favorites,
    )

    @Test
    fun `a creator feed with no sort fetches with the creator default order, never trending`() =
        runBlocking {
            val api = OrderApi()
            fetcher(FeedSource.Creator("perkyandpink"), api).fill(1)

            assertEquals(listOf("perkyandpink" to CREATOR_DEFAULT_ORDER), api.orders)
            assertFalse("trending is an empty subset for this creator (#65)", api.orders.any { it.second == "trending" })
        }

    @Test
    fun `an explicit creator sort wins over the default`() =
        runBlocking {
            val api = OrderApi()
            fetcher(FeedSource.Creator("perkyandpink", "oldest"), api).fill(1)

            assertEquals(listOf("perkyandpink" to "oldest"), api.orders)
        }

    @Test
    fun `a creator ref inside a custom feed uses the creator default order`() =
        runBlocking {
            val api = OrderApi()
            fetcher(FeedSource.Custom(1, "blend", listOf("creator:perkyandpink")), api).fill(1)

            assertEquals(listOf("perkyandpink" to CREATOR_DEFAULT_ORDER), api.orders)
        }

    @Test
    fun `a favorites sub-fetch uses the creator default order`() =
        runBlocking {
            val api = OrderApi()
            fetcher(FeedSource.Favorites, api, favorites = { listOf("perkyandpink") }).fill(1)

            assertEquals(listOf("perkyandpink" to CREATOR_DEFAULT_ORDER), api.orders)
        }

    @Test
    fun `only the creator surface reports a default sort`() {
        // The chip fallback's last resort: the order the feed is actually
        // fetched with, so the default view highlights the chip it serves.
        assertEquals(CREATOR_DEFAULT_ORDER, FeedSource.Creator("perkyandpink").defaultSort)
        assertEquals(CREATOR_DEFAULT_ORDER, FeedSource.Creator("perkyandpink", "oldest").defaultSort)
        assertEquals("", FeedSource.Search("cats").defaultSort)
        assertEquals("", FeedSource.Niche("1", "N").defaultSort)
        assertEquals("", FeedSource.Trending.defaultSort)
        assertEquals("", FeedSource.Favorites.defaultSort)
        assertEquals("", FeedSource.Custom(1, "blend", emptyList()).defaultSort)
        assertTrue(FeedSource.Creator("perkyandpink").sortOptions().none { it.second == "trending" })
    }
}
