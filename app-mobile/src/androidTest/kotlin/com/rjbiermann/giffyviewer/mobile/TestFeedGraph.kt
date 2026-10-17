package com.rjbiermann.giffyviewer.mobile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NetworkComponents
import com.rjbiermann.giffyviewer.core.network.buildNetwork
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.NicheJoinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.io.File
import java.util.UUID

/**
 * Issue #50 stage 3: hand-wired app graph for the instrumented suites — the
 * app's REAL network stack ([buildNetwork], rate limiter + breaker + retry
 * included), the REAL [FeedRepository]/[FeedViewModel] and a REAL Room
 * instance, with only the HTTP server swapped for a local [MockWebServer]
 * (and [startOffline] kills the listener → connection-refused = offline).
 *
 * No production DI is touched: everything the screens take as parameters
 * ([FeedViewModel], [NicheJoinViewModel], repositories, DAOs) is wired here
 * and passed explicitly, so no `hiltViewModel()` default and no Hilt test
 * infrastructure is needed.
 */
internal class TestFeedGraph(
    private val context: Context,
) {
    val server = MockWebServer()

    private lateinit var components: NetworkComponents

    /** Base URL captured while the server is up; survives [server].shutdown(). */
    private lateinit var baseUrl: String

    val db: GiffyDatabase =
        Room
            .inMemoryDatabaseBuilder(context, GiffyDatabase::class.java)
            .build()

    val tokenStore = TokenStore(context)

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore =
        PreferenceDataStoreFactory.create(scope = storeScope) {
            File(context.cacheDir, "test-prefs-${UUID.randomUUID()}.preferences_pb")
        }

    val settings = SettingsRepository(dataStore)

    val api: GifsApi get() = components.api

    val repository: FeedRepository by lazy {
        FeedRepository(api = api, db = db, contentFilter = ContentFilter(), settings = settings)
    }

    val viewModel: FeedViewModel by lazy {
        FeedViewModel(repository = repository, db = db, settings = settings, api = api, tokenStore = tokenStore)
    }

    val joinViewModel: NicheJoinViewModel by lazy { NicheJoinViewModel(api, tokenStore) }

    /**
     * Live server mode: one trending page holding ONE gif whose stream URL
     * (sd+hd) is [mediaPath] — the WAV asset served with its bytes — so the
     * player machine really plays. Likes serve 204 (the optimistic flip is
     * never reverted), the liked mirror serves [] and the JSON body keeps the
     * page at exactly one gif.
     */
    fun startHappy(mediaPath: String, wav: ByteArray) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.target
                    return when {
                        path.contains(mediaPath) ->
                            MockResponse
                                .Builder()
                                .code(200)
                                .setHeader("Content-Type", "audio/wav")
                                .body(Buffer().write(wav))
                                .build()

                        path.contains("v2/feeds/trending/popular") -> respond(200, trendingPageJson())

                        path.contains("v2/likes") -> respond(200, "[]")

                        request.method == "PUT" || request.method == "DELETE" -> MockResponse.Builder().code(204).build()

                        else -> respond(200, "{}")
                    }
                }
            }
        server.start()
        baseUrl = server.url("/").toString()
        components = buildNetwork(baseUrl = baseUrl)
    }

    /**
     * Offline mode: the listener is shut down BEFORE the API client is built,
     * so every request fails fast with connection-refused (IOException). The
     * retry interceptor still walks its 1s/4s/15s backoff, so the honest
     * failure takes ~10-20s to surface — tests wait for it.
     */
    fun startOffline() {
        server.start()
        baseUrl = server.url("/").toString()
        server.close()
        components = buildNetwork(baseUrl = baseUrl)
    }

    fun tearDown() {
        // The in-memory Room instance is NOT closed here: @After runs BEFORE
        // the compose rule tears the composition down, and the still-collected
        // pager flows would race a closed connection pool (crash class). The
        // process death at the end of the run frees everything.
        server.close()
        storeScope.cancel()
    }

    private fun respond(
        code: Int,
        body: String,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(code)
            .setHeader("Content-Type", "application/json")
            .body(body)
            .build()

    /** One-gif trending page (GifDtoShell JSON, same shape the DTOs parse live). */
    private fun trendingPageJson(): String {
        val media = "${baseUrl}media.wav"
        val gif =
            """{"id":"m-test-1","userName":"testcreator","description":"machine-test-gif",""" +
                """"tags":[],"likes":1,"views":1,"duration":5.0,"hasAudio":false,"width":100,"height":100,""" +
                """"createDate":1700000000,"published":true,"avgColor":"0,0,0","urls":{"sd":"$media","hd":"$media"}}"""
        return """{"gifs":[$gif],"page":1,"pages":1,"total":1}"""
    }
}
