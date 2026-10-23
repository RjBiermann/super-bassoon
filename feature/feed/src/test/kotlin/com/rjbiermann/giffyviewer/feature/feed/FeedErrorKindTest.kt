package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.network.rate.RateLimitedException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Issue #63: the feed error card was classified by ONE boolean (`error is
 * IOException`), so breaker-open (synthetic 503 → HttpException), sign-in-required
 * (401 on For You / Liked) and rate-limited exhaustion (no exception of its own)
 * all read wrong. Pins the honest classification + its copy.
 */
class FeedErrorKindTest {
    @Test
    fun `unknown host IOExceptions are offline`() {
        assertEquals(FeedErrorKind.Offline, feedErrorKind(UnknownHostException("giffy.example")))
        assertEquals("Nothing cached yet", feedErrorCard(FeedErrorKind.Offline).message)
    }

    @Test
    fun `call timeout InterruptedIOException is offline`() {
        assertEquals(FeedErrorKind.Offline, feedErrorKind(InterruptedIOException("timeout")))
    }

    @Test
    fun `rate limited exhaustion is not offline`() {
        assertEquals(FeedErrorKind.RateLimited, feedErrorKind(RateLimitedException(5_000)))
    }

    @Test
    fun `http 401 is sign in required`() {
        assertEquals(FeedErrorKind.SignInRequired, feedErrorKind(httpError(401)))
    }

    @Test
    fun `http 5xx is upstream`() {
        assertEquals(FeedErrorKind.Upstream, feedErrorKind(httpError(503))) // breaker-open synthetic
        assertEquals(FeedErrorKind.Upstream, feedErrorKind(httpError(500)))
    }

    @Test
    fun `offline device with a synthetic breaker 503 reads offline, not upstream`() {
        // Device gate #63 re-run: with the radio off the breaker opens and its
        // synthetic 503 outranked the offline kind — the card named "Server
        // error" while the banner said the device was offline.
        assertEquals(
            FeedErrorKind.Offline,
            feedErrorKind(httpError(503), isOnline = false),
        )
        assertEquals(
            "a device with no network cannot have a real upstream error",
            FeedErrorKind.Offline,
            feedErrorKind(UnknownHostException("giffy.example"), isOnline = false),
        )
    }

    @Test
    fun `online device with a 503 still reads upstream`() {
        assertEquals(FeedErrorKind.Upstream, feedErrorKind(httpError(503), isOnline = true))
        assertEquals(FeedErrorKind.SignInRequired, feedErrorKind(httpError(401), isOnline = true))
    }

    @Test
    fun `serializer failure is generic, not offline`() {
        assertEquals(FeedErrorKind.Generic, feedErrorKind(SerializationException("bad payload")))
        assertEquals(FeedErrorKind.Generic, feedErrorKind(null))
    }

    @Test
    fun `offline and rate limited cards never share copy`() {
        val offline = feedErrorCard(FeedErrorKind.Offline)
        val rateLimited = feedErrorCard(FeedErrorKind.RateLimited)
        assertNotEquals(offline.message, rateLimited.message)
        assertNotEquals(offline.hint, rateLimited.hint)
        assertEquals(
            "every kind owns its own headline",
            FeedErrorKind.entries.size,
            FeedErrorKind.entries
                .map { feedErrorCard(it).message }
                .distinct()
                .size,
        )
        assertEquals(
            "every kind owns its own hint",
            FeedErrorKind.entries.size,
            FeedErrorKind.entries
                .map { feedErrorCard(it).hint }
                .distinct()
                .size,
        )
    }
}
