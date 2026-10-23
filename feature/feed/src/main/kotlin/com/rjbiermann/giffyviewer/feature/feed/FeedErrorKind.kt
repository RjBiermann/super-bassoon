package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.network.rate.RateLimitedException
import retrofit2.HttpException
import java.io.IOException

/**
 * Issue #63: the feed error card classified failures with ONE boolean
 * (`error is IOException` → "offline"), so breaker-open (the synthetic 503 arrives
 * as [HttpException]), sign-in-required (401 on For You / Liked) and rate-limited
 * exhaustion all rendered copy that named the wrong cause. One honest kind per
 * failure shape, decided without Compose so it is unit-testable.
 */
enum class FeedErrorKind { Offline, RateLimited, SignInRequired, Upstream, Generic }

/** The card's two lines for [kind] — the phone and the TV render the same copy. */
data class FeedErrorCard(
    val message: String,
    val hint: String,
)

fun feedErrorKind(error: Throwable?): FeedErrorKind =
    when {
        // Checked before IOException: throttling IS an IOException (RetryInterceptor).
        error is RateLimitedException -> FeedErrorKind.RateLimited
        error is HttpException && error.code() == 401 -> FeedErrorKind.SignInRequired
        error is IOException -> FeedErrorKind.Offline
        error is HttpException -> FeedErrorKind.Upstream
        else -> FeedErrorKind.Generic
    }

fun feedErrorCard(kind: FeedErrorKind): FeedErrorCard =
    when (kind) {
        FeedErrorKind.Offline -> FeedErrorCard("Nothing cached yet", "You're offline — reconnect to load the feed.")
        FeedErrorKind.RateLimited -> FeedErrorCard("Too many requests", "Rate limited — retry in a moment.")
        FeedErrorKind.SignInRequired -> FeedErrorCard("Sign in required", "Sign in again to load this feed.")
        FeedErrorKind.Upstream -> FeedErrorCard("Server error", "Server trouble — tap to retry.")
        FeedErrorKind.Generic -> FeedErrorCard("Something went wrong", "Tap to retry, or pull down.")
    }
