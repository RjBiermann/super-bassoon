package com.rjbiermann.giffyviewer.search

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.network.API_JSON
import com.rjbiermann.giffyviewer.core.network.GifsApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Issue #57 device gate: the search page's Images scope stayed permanently
 * blank while the live API returned 200 with gifs for the exact request —
 * root cause: `loadScope` runs in the screen's `LaunchedEffect(scope, query)`
 * caller coroutine, which cancels on every keystroke/tab change, and the
 * `runCatching` swallow absorbs the mid-fetch CancellationException — the
 * VM's scope map stays empty forever while the tab is parked (no re-key, no
 * retry, no failure state). This test pins the contract: a caller killed
 * mid-fetch must still land the scope's results in the VM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelImagesScopeTest {
    private lateinit var server: MockWebServer
    private lateinit var db: GiffyDatabase

    @Before
    fun setUp() {
        // viewModelScope (Dispatchers.Main.immediate) must not starve behind the
        // test thread's blocking runBlocking — run VM jobs eagerly instead.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    GiffyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        Dispatchers.resetMain()
    }

    /** The Images scope's live response shape (type=i stills: duration=null, jpg urls). */
    private fun enqueueImagesResponse(bodyDelayMs: Long) {
        val fixture = javaClass.getResourceAsStream("/images-scope-search-type-i.json")!!
            .bufferedReader().readText()
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(fixture)
                .bodyDelay(bodyDelayMs, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    @Test
    fun `a caller cancelled mid-fetch still lands the Images scope results`() {
        enqueueImagesResponse(bodyDelayMs = 1_500)
        val vm =
            SearchViewModel(
                api(),
                db,
                ContentFilter(),
                SavedStateHandle(),
            )
        runBlocking {
            // The screen's LaunchedEffect(scope, query) owns this coroutine and
            // cancels it on every keystroke/tab change (DESIGN-search hyp 3).
            val caller =
                launch {
                    vm.loadScope(SearchViewModel.SCOPE_IMAGES, "beach")
                }
            delay(300) // let the caller start the fetch (request hits the wire)
            server.takeRequest(5, TimeUnit.SECONDS) // request is on the wire
            caller.cancelAndJoin() // killed mid-fetch (exactly the device repro)
            // The fetch must complete VM-owned and fill the scope map.
            val results =
                withTimeout(5_000) {
                    vm.images.first { it.containsKey("beach") }["beach"]!!
                }
            assertEquals("the fixture page's rows must survive", 3, results.size)
            assertTrue(results.all { it.durationSeconds == 0.0 && it.sdUrl != null })
        }
        // Rate-limit invariant: one fetch per (query, scope), no duplicate storm.
        assertEquals(1, server.requestCount)
    }

    /** The Images scope's exact app stack minus the interceptor chain: the real
     *  Retrofit + kotlinx-serialization converter ([API_JSON]) + the real DTOs. */
    private fun api(): GifsApi =
        Retrofit
            .Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(API_JSON.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GifsApi::class.java)
}
