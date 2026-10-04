package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import com.rjbiermann.giffyviewer.core.database.ContentPrefsDao
import com.rjbiermann.giffyviewer.core.database.CreatorPrefEntity
import com.rjbiermann.giffyviewer.core.database.CustomFeedDao
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.database.KeywordBlockEntity
import com.rjbiermann.giffyviewer.core.database.TagPrefEntity
import com.rjbiermann.giffyviewer.core.datastore.FeedPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice B regression (user bug): pull-to-refresh on the mobile Favorites feed
 * made it show the "No favorites yet — open any video and use ⋯ → Favorite
 * @creator" empty state. Root cause pinned: the session dedup set lived in a
 * SESSION-WIDE map (FeedRepository.seenFor) that SURVIVED the pager restart —
 * a forced REFRESH re-fetches the same deterministic round-robin pages
 * (page n → creator[(n-1) % n], stable per-creator lists), so every id re-listed
 * was deduped away ("already shown") and the walk drained to a graceful empty
 * page → the helpful empty state for a NON-empty feed.
 *
 * Contract pinned: each pager generation's source instance starts with a CLEAN
 * dedup set (content re-renders on pull-to-refresh), while WITHIN one
 * generation the set still accumulates (re-listed ids can't duplicate grid
 * keys — the original crash class the dedup exists for).
 */
class FavoritesRefreshSurvivalTest {
    private class FakeContentPrefsDao : ContentPrefsDao {
        var favorites: List<String> = emptyList()

        override suspend fun upsertCreator(pref: CreatorPrefEntity) = Unit

        override suspend fun upsertTag(pref: TagPrefEntity) = Unit

        override suspend fun blockKeyword(pattern: KeywordBlockEntity) = Unit

        override suspend fun blockedCreators(): List<String> = emptyList()

        override suspend fun allCreatorPrefs(): List<CreatorPrefEntity> = emptyList()

        override suspend fun favoriteCreators(): List<String> = favorites

        override suspend fun blockedTags(): List<String> = emptyList()

        override suspend fun allTagPrefs(): List<TagPrefEntity> = emptyList()

        override suspend fun blockedKeywords(): List<String> = emptyList()

        override suspend fun allKeywordBlocks(): List<KeywordBlockEntity> = emptyList()

        override suspend fun unblockCreator(username: String) = Unit

        override suspend fun clearTag(tag: String) = Unit

        override suspend fun unfavoriteTag(tag: String) = Unit

        override suspend fun unblockKeyword(pattern: String) = Unit

        override fun blockedCreatorsFlow(): Flow<List<String>> = flowOf(emptyList())

        override fun favoriteCreatorsFlow(): Flow<List<String>> = flowOf(favorites)

        override fun creatorState(username: String): Flow<String?> = flowOf(null)

        override fun tagState(tag: String): Flow<String?> = flowOf(null)

        override fun favoriteTagsFlow(): Flow<List<String>> = flowOf(emptyList())

        override fun blockedTagsFlow(): Flow<List<String>> = flowOf(emptyList())

        override fun blockedKeywordsFlow(): Flow<List<String>> = flowOf(emptyList())

        override fun hideCount(weekStart: Long): Flow<Int?> = flowOf(null)

        override suspend fun addHideCount(
            weekStart: Long,
            delta: Int,
        ) = Unit
    }

    private class FakeCustomFeedDao : CustomFeedDao {
        override fun all(): Flow<List<CustomFeedEntity>> = flowOf(emptyList())

        override suspend fun byId(id: Long): CustomFeedEntity? = null

        override suspend fun upsert(feed: CustomFeedEntity): Long = 0L

        override suspend fun delete(id: Long) = Unit

        override suspend fun blocked(): List<CustomFeedEntity> = emptyList()
    }

    private class FakeGifDao : GifDao {
        val gifs = LinkedHashMap<String, GifEntity>()

        override suspend fun upsertAll(gifs: List<GifEntity>) {
            gifs.forEach { this.gifs[it.id] = it }
        }

        override suspend fun byIds(ids: List<String>): List<GifEntity> = ids.mapNotNull { gifs[it] }

        override suspend fun byId(id: String): GifEntity? = gifs[id]

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()

        override suspend fun randomUnwatched(limit: Int): List<GifEntity> = emptyList()
    }

    private class FakeFeedPageDao(
        private val gifStore: LinkedHashMap<String, GifEntity>,
    ) : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

        override fun pageFlow(pageKey: String): Flow<FeedPageEntity?> = throw NotImplementedError()

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = ids.mapNotNull { gifStore[it] }

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) = throw NotImplementedError()

        override suspend fun evictFavorites(prefix: String) =
            pages.keys
                .filter { it.startsWith(prefix) }
                .forEach { pages.remove(it) }
                .let { }
    }

    private fun source(
        contentPrefsDao: FakeContentPrefsDao,
        pageDao: FakeFeedPageDao,
        gifDao: FakeGifDao,
        api: FakeApi,
    ): FeedPagingSource {
        val fetcher =
            FeedPageFetcher(FeedSource.Favorites, gifDao, pageDao, api, 20, favorites = { contentPrefsDao.favorites })
        return FeedPagingSource(
            contentPrefsDao,
            FakeCustomFeedDao(),
            FeedSource.Favorites,
            pageDao,
            20,
            com.rjbiermann.giffyviewer.core.database
                .ContentFilter(),
            fetcher = fetcher,
            prefs = { FeedPrefs() },
        )
    }

    private fun load(
        source: FeedPagingSource,
        page: Int?,
    ): PagingSource.LoadResult<Int, com.rjbiermann.giffyviewer.core.model.Gif> =
        runBlocking { source.load(PagingSource.LoadParams.Refresh(key = page, loadSize = 20, placeholdersEnabled = false)) }

    @Test
    fun `favorites content survives pull-to-refresh (fresh generation re-renders refetched ids)`() {
        val contentPrefsDao = FakeContentPrefsDao()
        contentPrefsDao.favorites = listOf("sweetie.yuko")
        val gifDao = FakeGifDao()
        val pageDao = FakeFeedPageDao(gifDao.gifs)
        val api = FakeApi() // userGifs → one gif "g1" per call, same ids every refetch

        // Generation 1: first load self-fills p1 (mediator + source race aside).
        val first = load(source(contentPrefsDao, pageDao, gifDao, api), null)
        assertTrue(first is PagingSource.LoadResult.Page)
        assertEquals(listOf("g1"), (first as PagingSource.LoadResult.Page).data.map { it.id })

        // Pull-to-refresh: the mediator's forced REFRESH re-fetches p1 — the
        // deterministic round-robin re-lists the SAME id into the same row.
        runBlocking {
            FeedPageFetcher(FeedSource.Favorites, gifDao, pageDao, api, 20, favorites = { contentPrefsDao.favorites }).fill(1)
        }

        // Generation 2: a FRESH source instance (pager restart) must render the
        // refetched content, not dedup it away into the "No favorites yet" state.
        val refreshed = load(source(contentPrefsDao, pageDao, gifDao, api), null)
        assertTrue(refreshed is PagingSource.LoadResult.Page)
        assertEquals(listOf("g1"), (refreshed as PagingSource.LoadResult.Page).data.map { it.id })
    }

    @Test
    fun `within one generation re-listed ids are still deduped (no duplicate grid keys)`() {
        val contentPrefsDao = FakeContentPrefsDao()
        contentPrefsDao.favorites = listOf("sweetie.yuko")
        val gifDao = FakeGifDao()
        val pageDao = FakeFeedPageDao(gifDao.gifs)
        val api = FakeApi()
        val one = source(contentPrefsDao, pageDao, gifDao, api)

        assertTrue(load(one, null) is PagingSource.LoadResult.Page)
        // The original crash class the dedup exists for: the server reshuffles
        // a creator's list between fetches — row p1 gets rewritten with a
        // DIFFERENT id while "g1" is still live in the pager's presented list.
        pageDao.pages["fav:v1:p1"] = FeedPageEntity(pageKey = "fav:v1:p1", gifIds = listOf("g2"), nextPageKey = "fav:v1:p2", fetchedAt = 0)
        // Same instance, next page: the fake API re-lists "g1" (upstream overlap)
        // — the generation's accumulated dedup set drops it (the DB-row dedup
        // can't see the live list anymore: row p1 now holds "g2").
        val second = load(one, 2)
        assertTrue(second is PagingSource.LoadResult.Page)
        assertTrue((second as PagingSource.LoadResult.Page).data.isEmpty())
    }
}
