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
 * #62 favorites fan-out bound (user report: slow first tiles, paging looked
 * stopped): #54's one-sub-fetch-per-favorited-creator mapping costs k
 * limiter-paced requests per fill, so with many favorites a single screenful
 * takes seconds and the dead round at the pool's end costs k² requests.
 *
 * Contract pinned: one fill fetches at most [FAVORITES_CREATORS_PER_FILL]
 * creators — a ROUND of ceil(k / 5) pages walks the whole favorite set at ONE
 * inner page, then the inner page advances (round > 0). Every (creator, inner
 * page) pair still maps to exactly one page, so nothing duplicates and nothing
 * is lost. k <= the fan-out keeps the #54 mapping byte for byte (one page per
 * round, every creator, same lead rotation).
 */
class FavoritesFanOutTest {
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

    /** (user, inner page) → gif ids; records every userGifs call. The ids are
     *  keyed by (user, page) only, mirroring the page→slice contract this test
     *  pins: one page of a creator holds one slice. */
    private class PlanApi : FakeApi() {
        data class Call(
            val username: String,
            val page: Int,
            val count: Int,
        )

        val calls = mutableListOf<Call>()
        val pool = mutableMapOf<Pair<String, Int>, List<String>>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String,
        ): GifsPageDto {
            calls.add(Call(username, page, count))
            return GifsPageDto(gifs = (pool[username to page] ?: emptyList()).map { gifDtoShell(it, username) })
        }
    }

    /** userGifs as the site behaves: `count` gifs starting at (page-1)*count
     *  from the creator's own pool. [PlanApi] keys its ids by (user, page) and
     *  therefore hides a per-slice-size defect; this double honours the count,
     *  so a count that varies across pages overlaps the same creator's pool. */
    private class SliceApi : FakeApi() {
        val calls = mutableListOf<PlanApi.Call>()
        val pools = mutableMapOf<String, List<String>>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String,
        ): GifsPageDto {
            calls.add(PlanApi.Call(username, page, count))
            val pool = pools[username] ?: emptyList()
            val from = (page - 1) * count
            return GifsPageDto(gifs = pool.drop(from).take(count).map { gifDtoShell(it, username) })
        }
    }

    /** k=6 = a 2-page round (5 + 1 slots) over 60-gif pools: round 0 leaves c6
     *  alone in the tail window, later rounds reshuffle. */
    private fun sliceFetcher(
        pageDao: FeedPageDao,
        seed: Long = 0L,
    ): Pair<FeedPageFetcher, SliceApi> {
        val creators = fakeCreators(6)
        val api = SliceApi()
        creators.forEach { api.pools[it] = (1..60).map { n -> "$it-$n" } }
        return fetcher(api, creators, pageDao, seed) to api
    }

    private fun fakeCreators(count: Int): List<String> = (1..count).map { "c$it" }

    /** [perPage] gifs for every (creator, inner page) in [pages]. */
    private fun seedPool(
        api: PlanApi,
        creators: List<String>,
        pages: IntRange,
        perPage: Int = 4,
    ) {
        for (creator in creators) {
            for (page in pages) {
                api.pool[creator to page] = (1..perPage).map { "$creator-p$page-$it" }
            }
        }
    }

    private fun fetcher(
        api: FakeApi,
        creators: List<String>,
        pageDao: FeedPageDao,
        seed: Long = 0L,
    ) = FeedPageFetcher(
        FeedSource.Favorites,
        FakeGifDao(),
        pageDao,
        api,
        20,
        favorites = { creators },
        favoritesSeed = { seed },
    )

    @Test
    fun `a favorites fill issues at most one sub-fetch per creator in its round window`() =
        runBlocking {
            val creators = fakeCreators(12)
            val api = PlanApi().also { seedPool(it, creators, 1..4) }
            val fetcher = fetcher(api, creators, FakeFeedPageDao())

            assertTrue(fetcher.fill(1))

            // One limiter burst = the whole fan-out of a fill (was k = 12).
            assertEquals(FAVORITES_CREATORS_PER_FILL, api.calls.size)
            // Seed 0 / round 0 = DB order, first window.
            assertEquals(creators.take(FAVORITES_CREATORS_PER_FILL), api.calls.map { it.username })
        }

    @Test
    fun `every favorited creator is covered exactly once per round`() =
        runBlocking {
            val creators = fakeCreators(12)
            val api = PlanApi().also { seedPool(it, creators, 1..4) }
            val fetcher = fetcher(api, creators, FakeFeedPageDao())

            val windows = mutableListOf<List<PlanApi.Call>>()
            var seen = 0
            for (page in 1..3) {
                assertTrue(fetcher.fill(page))
                windows += api.calls.drop(seen)
                seen = api.calls.size
            }

            // 12 creators = a 3-page round: 5 + 5 + 2, all at inner page 1.
            assertEquals(listOf(5, 5, 2), windows.map { it.size })
            assertTrue(windows.flatten().all { it.page == 1 })
            val served: List<String> = windows.flatten().map { it.username }
            assertEquals("every creator exactly once per round", creators.sorted(), served.sorted())
            assertEquals("no creator in two windows of the round", served.size, served.toSet().size)

            // Round 2 restarts the windows at inner page 2.
            assertTrue(fetcher.fill(4))
            val round2 = api.calls.drop(seen)
            assertEquals(FAVORITES_CREATORS_PER_FILL, round2.size)
            assertTrue(round2.all { it.page == 2 })
        }

    @Test
    fun `no creator and inner-page pair is served by two pages`() =
        runBlocking {
            val creators = fakeCreators(12)
            val api = PlanApi().also { seedPool(it, creators, 1..6) }
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, creators, pageDao)

            // Three full rounds (9 pages, 36 sub-fetches) over the pools.
            for (page in 1..9) assertTrue(fetcher.fill(page))

            val pairs = api.calls.map { it.username to it.page }
            assertEquals("each (creator, inner page) pair fetched once", pairs.size, pairs.toSet().size)
            assertEquals(3 * creators.size, pairs.size)

            val rows = (1..9).map { pageDao.pages[feedPageKey(FeedSource.Favorites, it)]?.gifIds ?: emptyList() }
            assertTrue("every row of three live rounds has content", rows.all { it.isNotEmpty() })
            val served = rows.flatten()
            assertEquals("a gif id lands on exactly one row", served.size, served.toSet().size)
            // Every sub-fetch contributed its whole 4-gif slice to exactly one row.
            assertEquals(api.calls.size * 4, served.size)
        }

    @Test
    fun `k at or below the fan-out keeps the 54 mapping`() =
        runBlocking {
            val creators = listOf("a", "b", "c", "d", "e")
            val api = PlanApi().also { seedPool(it, creators, 1..3) }
            val fetcher = fetcher(api, creators, FakeFeedPageDao())

            assertTrue(fetcher.fill(1))
            // All 5 creators in one page, inner page = page, ceil(20/5) = 4 each.
            assertEquals(listOf("a", "b", "c", "d", "e"), api.calls.map { it.username })
            assertTrue(api.calls.all { it.page == 1 && it.count == 4 })

            assertTrue(fetcher.fill(2))
            val second = api.calls.drop(5)
            // #54 lead rotation preserved: the window starts one slot further on.
            assertEquals(listOf("b", "c", "d", "e", "a"), second.map { it.username })
            assertTrue(second.all { it.page == 2 && it.count == 4 })
        }

    @Test
    fun `the exhaustion window is the round length, not the creator count`() =
        runBlocking {
            // 12 favorites, every pool dry at inner page 1.
            val creators = fakeCreators(12)
            val api = PlanApi()
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, creators, pageDao)

            assertTrue(fetcher.fill(1))
            assertTrue(fetcher.fill(2))
            assertTrue(fetcher.fill(3))
            // The dead round cost = one round of fills: 5 + 5 + 2 requests
            // (was a k-page round of k requests each = k² = 144).
            assertEquals(12, api.calls.size)

            // The next page is pre-skipped: the whole previous ROUND was empty.
            assertFalse("the dead round ends the feed", fetcher.fill(4))
            assertEquals("the dead round is never probed twice", 12, api.calls.size)
        }

    @Test
    fun `empty favorites fill is a degenerate page, not a divide by zero`() =
        runBlocking {
            val api = PlanApi()
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, emptyList(), pageDao)

            // No favorited creators: no sub-fetch, no row pinned (§5) — the
            // plan's round length is 0 and every division stays guarded.
            assertFalse(fetcher.fill(1))
            assertTrue("an empty favorite set must not hit the API", api.calls.isEmpty())
            assertTrue("an empty favorite set must not pin a page row", pageDao.pages.isEmpty())
        }

    @Test
    fun `an empty mid-round window still chains forward`() =
        runBlocking {
            val creators = fakeCreators(12)
            // Window 1 (c1..c5) is dry, window 2 (c6..c10) is live: an empty
            // slot inside round 0 never ends the feed (the #36 rule on the
            // per-round window — page 1 of a multi-page round must chain).
            val api = PlanApi().also { seedPool(it, creators.drop(FAVORITES_CREATORS_PER_FILL), 1..2) }
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, creators, pageDao)

            assertTrue(fetcher.fill(1))
            val first = pageDao.pages["fav:v2:p1"]!!
            assertTrue("window 1 is dry", first.gifIds.isEmpty())
            assertEquals("a dry window still chains into the next one", "fav:v2:p2", first.nextPageKey)

            assertTrue(fetcher.fill(2))
            assertTrue("the live window carries content", pageDao.pages["fav:v2:p2"]!!.gifIds.isNotEmpty())
        }

    @Test
    fun `perCreator is ceil pageSize over the fan-out, never over the window size`() =
        runBlocking {
            val creators = fakeCreators(12)
            val api = PlanApi().also { seedPool(it, creators, 1..3) }
            val fetcher = fetcher(api, creators, FakeFeedPageDao())

            var seen = 0
            for (page in 1..3) {
                assertTrue(fetcher.fill(page))
                val calls = api.calls.drop(seen)
                seen = api.calls.size
                // A creator's slice must not depend on the window it lands in:
                // ceil(20 / min(12, 5)) = 4 for the 5-slot windows AND the
                // 2-slot tail (a window-sized count made the tail fetch 10).
                assertEquals("page $page: ceil(20 / min(k, 5))", 4, calls.first().count)
            }
        }

    @Test
    fun `a creator moving between windows across rounds never repeats an item`() =
        runBlocking {
            val pageDao = FakeFeedPageDao()
            val (fetcher, api) = sliceFetcher(pageDao)

            var seen = 0
            val rounds = mutableListOf<List<PlanApi.Call>>()
            for (page in 1..6) {
                assertTrue("page $page chains", fetcher.fill(page))
                rounds += api.calls.drop(seen)
                seen = api.calls.size
            }

            // Precondition: round 0 leaves c6 alone in the 1-slot tail window;
            // a later round's reshuffle puts it in a 5-slot window. Its slice
            // then depends on which round it sat in — the device defect.
            assertEquals(listOf("c6"), rounds[1].map { it.username })
            assertTrue(
                "c6 moves from the 1-slot tail into a 5-slot window",
                (2..rounds.lastIndex).any { i -> rounds[i].size == 5 && rounds[i].any { it.username == "c6" } },
            )

            val rows = (1..6).map { pageDao.pages[feedPageKey(FeedSource.Favorites, it)]!!.gifIds }
            for (a in rows.indices) {
                for (b in a + 1 until rows.size) {
                    val shared = rows[a].toSet() intersect rows[b].toSet()
                    assertTrue("pages ${a + 1} and ${b + 1} serve the same item: $shared", shared.isEmpty())
                }
            }
            val owners = rows.flatten().groupingBy { it }.eachCount()
            assertTrue("every served gif has exactly one owning row", owners.values.all { it == 1 })
        }

    @Test
    fun `trailing window stays thin but keeps chaining`() =
        runBlocking {
            val pageDao = FakeFeedPageDao()
            val (fetcher, _) = sliceFetcher(pageDao)

            assertTrue(fetcher.fill(1))
            assertEquals("5 creators x 4 gifs", 20, pageDao.pages["fav:v2:p1"]!!.gifIds.size)
            assertTrue(fetcher.fill(2))
            val trailing = pageDao.pages["fav:v2:p2"]!!
            assertEquals("the 1-slot tail asks for 4, not ceil(20/1)", 4, trailing.gifIds.size)
            assertEquals("a thin trailing window still chains", "fav:v2:p3", trailing.nextPageKey)
        }
}
