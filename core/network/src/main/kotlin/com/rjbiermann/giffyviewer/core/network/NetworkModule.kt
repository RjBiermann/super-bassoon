package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.rate.CircuitBreaker
import com.rjbiermann.giffyviewer.core.network.rate.RateLimitBus
import com.rjbiermann.giffyviewer.core.network.rate.RateLimitInterceptor
import com.rjbiermann.giffyviewer.core.network.rate.RetryInterceptor
import com.rjbiermann.giffyviewer.core.network.rate.RollingWindowRateLimiter
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** APP_USER_AGENT must be a realistic client identity (house rule) — never a bare bot fingerprint. */
const val APP_USER_AGENT: String =
    "Mozilla/5.0 (X11; Linux x86_64; rv:156.0) Gecko/20100101 Firefox/156.0"

const val BASE_URL = "https://upstream-api-host.example/"

/** Json configured per plan: ignoreUnknownKeys so API drift can't crash us. */
val API_JSON: Json =
    Json {
        ignoreUnknownKeys = true
        // @Body DTOs (LikeBody, FollowBody) use all-default fields — without
        // this they serialize to {} and the server 400s (live-verified 2026-09-30)
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

data class NetworkComponents(
    val api: GifsApi,
    val limiter: RollingWindowRateLimiter,
    val breaker: CircuitBreaker,
    val bus: RateLimitBus,
    val client: OkHttpClient,
)

/**
 * Builds the full network stack. [authToken] binds the Bearer for signed-in calls;
 * pass null for anonymous browsing (token added per-request via [AuthCarrier] mutates).
 */
fun buildNetwork(
    baseUrl: String = BASE_URL,
    userAgent: String = APP_USER_AGENT,
    authToken: suspend () -> String? = { null },
    onUnauthorized: () -> Unit = {},
    enableLogging: Boolean = false,
): NetworkComponents {
    val bus = RateLimitBus()
    val limiter = RollingWindowRateLimiter()
    val breaker = CircuitBreaker()

    fun authed(
        request: okhttp3.Request,
        token: String?,
    ): okhttp3.Request {
        val builder =
            request
                .newBuilder()
                .header("User-Agent", userAgent)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    val authInterceptor =
        Interceptor { chain ->
            val request = chain.request()
            // The temp-token call itself must go out unauthenticated (re-entrancy).
            if (request.url.encodedPath.endsWith("v2/auth/temporary")) {
                return@Interceptor chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
            }
            val token = kotlinx.coroutines.runBlocking { authToken() }
            var response = chain.proceed(authed(request, token))
            // Bearer went stale or raced the token fetch → refresh once and retry.
            // Single retry; a second 401 surfaces to the caller as-is.
            if (response.code == 401 && !token.isNullOrBlank()) {
                response.close()
                onUnauthorized()
                val retryToken = kotlinx.coroutines.runBlocking { authToken() }
                if (retryToken != null && retryToken != token) {
                    response = chain.proceed(authed(request, retryToken))
                } else {
                    response = chain.proceed(authed(request, token))
                }
            }
            if (response.code == 401) {
                println("Auth401 ${request.url.encodedPath} body=${response.peekBody(300).string()}")
            }
            response
        }

    val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(RateLimitInterceptor(limiter))
            .addInterceptor(
                RetryInterceptor(
                    limiter = limiter,
                    bus = bus,
                    breaker = breaker,
                ),
            ).apply {
                if (enableLogging) {
                    addInterceptor(
                        okhttp3.logging.HttpLoggingInterceptor().setLevel(
                            okhttp3.logging.HttpLoggingInterceptor.Level.BASIC,
                        ),
                    )
                }
            }.build()

    val retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(API_JSON.asConverterFactory("application/json".toMediaType()))
            .build()

    return NetworkComponents(
        api = retrofit.create(GifsApi::class.java),
        limiter = limiter,
        breaker = breaker,
        bus = bus,
        client = client,
    )
}
