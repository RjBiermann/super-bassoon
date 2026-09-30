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
}
