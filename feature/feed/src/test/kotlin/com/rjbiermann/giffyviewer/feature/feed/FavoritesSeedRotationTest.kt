package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #5 shuffle-on-refresh: a stubbed rotation seed changes WHICH creator the
 * round-robin maps page 1 to (different page-1 content order) while the
 * per-seed display shuffle (shuffleOrdered) keeps ordering each page.
 */
class FavoritesSeedRotationTest {
    private class FakeGifDao : GifDao {
        override suspend fun upsertAll(gifs: List<GifEntity>) = Unit

        override suspend fun byIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun byId(id: String): GifEntity? = null

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()
    }

    private class FakeFeedPageDao : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

        override fun pageFlow(pageKey: String): Flow<FeedPageEntity?> = throw NotImplementedError()

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) = throw NotImplementedError()

        override suspend fun evictFavorites(prefix: String) =
            pages.keys
                .filter { it.startsWith(prefix) }
                .forEach { pages.remove(it) }
                .let { }
    }

    private class RecordingApi : FakeApi() {
        val users = mutableListOf<String>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String,
        ): GifsPageDto {
            users.add(username)
            return GifsPageDto(gifs = listOf(gifDtoShell("g" + users.size, username)))
        }
    }

    @Test
    fun `seeded rotation maps page 1 to a different creator (refresh lands a different order)`() =
        runBlocking {
            val pageDao = FakeFeedPageDao()
            val creators = listOf("a", "b", "c")
            val unseeded = RecordingApi()
            runBlocking {
                FeedPageFetcher(FeedSource.Favorites, FakeGifDao(), pageDao, unseeded, 20, favorites = { creators }).fill(1)
            }
            val seeded = RecordingApi()
            runBlocking {
                FeedPageFetcher(
                    FeedSource.Favorites,
                    FakeGifDao(),
                    pageDao,
                    seeded,
                    20,
                    favorites = { creators },
                    favoritesSeed = { 2L },
                ).fill(1)
            }
            // Seed 2 rotates [a,b,c] → [c,a,b]: page 1 maps to creator c, not a.
            assertEquals("a", unseeded.users.first())
            assertEquals("c", seeded.users.first())
            runBlocking {
                FeedPageFetcher(
                    FeedSource.Favorites,
                    FakeGifDao(),
                    pageDao,
                    seeded,
                    20,
                    favorites = { creators },
                    favoritesSeed = { 2L },
                ).fill(3)
            }
            // #54 interleave: fill 3 fetches the WHOLE rotation (chunk order
            // rotates forward → anchored at slot (3-1) % 3 = 2 → "b" first)
            // at inner page 3 — one sub-fetch per creator.
            assertEquals("b", seeded.users[3])
            assertEquals(setOf("a", "b", "c"), seeded.users.drop(3).toSet())
            // Every fill now issues one sub-fetch per favorited creator
            // (3 creators → 3 calls per fill).
            assertTrue(seeded.users.size == 6 && unseeded.users.size == 3)
        }
}
