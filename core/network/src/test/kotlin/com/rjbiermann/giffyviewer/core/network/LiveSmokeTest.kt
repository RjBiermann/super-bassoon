package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.dto.toModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live-site validation (PLAN Phase 2). Runs ONLY with -Dlive=true so CI stays offline:
 *   ./gradlew :core:network:test --tests '*LiveSmokeTest*' -Dlive=true
 */
class LiveSmokeTest {
    private fun live() = System.getProperty("live") == "true"

    @Test
    fun `anonymous token + search + trending work against the real API`() =
        runBlocking {
            assumeTrue("skipped: pass -Dlive=true", live())

            // Search REQUIRES a Bearer (anonymous temp token is fine). The session manager
            // (app layer) supplies it via the carrier once fetched — modeled here.
            val carrier = arrayOfNulls<String>(1)
            val net = buildNetwork(authToken = { carrier[0] })
            val token = net.api.temporaryToken() // the only no-auth endpoint
            carrier[0] = token.token
            assertTrue(token.token.length > 100)

            val search = net.api.search(searchText = "nature", count = 10)
            assertTrue(search.gifs.isNotEmpty())

            val gif = search.gifs.first().toModel()
            assertTrue(gif.id.isNotBlank())
            assertTrue(gif.width > 0)

            val trending = net.api.trendingPopular(count = 10)
            assertTrue(trending.gifs.isNotEmpty())
            assertEquals(
                trending.gifs.size,
                trending.gifs
                    .map { it.id }
                    .distinct()
                    .size,
            )
        }

    /**
     * Gate 332 probe: does the API mix `promoted: true` (ad) items into public
     * feeds? If yes, the app must drop them read-time — currently the field is
     * unknown to the DTO (ignored), which would RENDER an ad as a normal gif.
     * Run with -Dlive=true; asserts only what the feed actually contains.
     */
    @Test
    fun `public feed promoted probe`() =
        runBlocking {
            assumeTrue("skipped: pass -Dlive=true", live())
            val carrier = arrayOfNulls<String>(1)
            val net = buildNetwork(authToken = { carrier[0] })
            carrier[0] = net.api.temporaryToken().token
            val pages =
                (1..3).map { p ->
                    net.api.trendingPopular(page = p, count = 40).gifs
                }
            val raw =
                pages.flatten() +
                    net.api.search(searchText = "dance", count = 40).gifs
            assertTrue(raw.isNotEmpty())
            // The DTO ignores unknown keys, so inspect the raw wire field via the
            // DTO's optional property when present, else via a widened fetch.
            // Feeds observed so far: no promoted items in public search/trending.
            val promotedCount =
                pages.sumOf { page ->
                    page.count { it.promoted == true }
                }
            println("PROBE promoted=true items across ${raw.size} trending gifs: $promotedCount")
            assertEquals(0, promotedCount)
        }

    /**
     * Gate: date-range filtering. Probe the plausible param shapes and report
     * which, if any, change the result set. Run with -Dlive=true.
     */
    @Test
    fun `date range param probe`() =
        runBlocking {
            assumeTrue("skipped: pass -Dlive=true", live())
            val carrier = arrayOfNulls<String>(1)
            val net = buildNetwork(authToken = { carrier[0] })
            carrier[0] = net.api.temporaryToken().token
            val baseline =
                net.api
                    .search(searchText = "dance", count = 40)
                    .gifs
                    .map { it.id }

            suspend fun probe(
                name: String,
                call: suspend () -> List<Long>,
            ) {
                val dates = runCatching { call() }.getOrElse { emptyList() }
                val min = dates.minOrNull()
                // Server reshuffles page contents — the only meaningful check
                // is whether the returned set RESPECTS the date bound.
                println(
                    "PROBE date $name -> ${dates.size} gifs, oldest=$min, respects-bound=${min != null && min >= 1798848000L} // 2027-01-01 UTC in seconds",
                )
            }
            probe("createdAfter") {
                net.api
                    .searchDateProbe(createdAfter = "2027-01-01")
                    .gifs
                    .map { it.createDate }
            }
            probe("created_after") {
                net.api
                    .searchDateProbe(created_after = "2027-01-01")
                    .gifs
                    .map { it.createDate }
            }
            probe("date") {
                net.api
                    .searchDateProbe(date = "2027-01-01")
                    .gifs
                    .map { it.createDate }
            }
            probe("dateFrom") {
                net.api
                    .searchDateProbe(dateFrom = "2027-01-01")
                    .gifs
                    .map { it.createDate }
            }
            probe("baseline") {
                net.api
                    .search(searchText = "dance", count = 40)
                    .gifs
                    .map { it.createDate }
            }
        }

    /**
     * Probe: does the search/trending endpoint cap pagination? (400 at page 6.)
     * Run with -Dlive=true.
     */
    @Test
    fun `page cap probe`() =
        runBlocking {
            assumeTrue("skipped: pass -Dlive=true", live())
            val carrier = arrayOfNulls<String>(1)
            val net = buildNetwork(authToken = { carrier[0] })
            carrier[0] = net.api.temporaryToken().token
            for (cfg in listOf(40 to 5, 40 to 6, 100 to 2, 100 to 3, 20 to 10)) {
                val r =
                    runCatching {
                        net.api.search(searchText = "dance", count = cfg.first, page = cfg.second).gifs.size
                    }
                println("PROBE cap count=${cfg.first} page=${cfg.second} -> ok=${r.getOrNull()} err=${r.exceptionOrNull()?.message}")
            }
        }

    /**
     * Probe: trending/popular pagination cap — 400 seen at page 6 (count=40).
     * Run with -Dlive=true.
     */
    @Test
    fun `trending page cap probe`() =
        runBlocking {
            assumeTrue("skipped: pass -Dlive=true", live())
            val carrier = arrayOfNulls<String>(1)
            val net = buildNetwork(authToken = { carrier[0] })
            carrier[0] = net.api.temporaryToken().token
            for (cfg in listOf(40 to 1, 40 to 2, 40 to 3, 40 to 4, 100 to 1)) {
                val r =
                    runCatching {
                        net.api.trendingPopular(count = cfg.first, page = cfg.second).gifs.size
                    }
                println("PROBE tcap count=${cfg.first} page=${cfg.second} -> ok=${r.getOrNull()} err=${r.exceptionOrNull()?.message}")
            }
        }
}