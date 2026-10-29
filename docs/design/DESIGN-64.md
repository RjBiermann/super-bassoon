# DESIGN-64 — TV offline cold start deadlocks in `authToken`

Issue: [#64](https://github.com/RjBiermann/giffy-viewer/issues/64) · status: design (no code yet) ·
scope: `:core:network` + the two shell DI modules · size: ~40 lines of production code + 4 JVM tests.

## 1. Objective

A cold start must never park a feed request inside the auth interceptor indefinitely. Every row has
to reach `LoadState.Error` within a bounded time (so the #63 error copy / Retry affordance composes),
and a reconnect must be able to recover it. Today: permanent `LoadState.Loading`, zero error copy,
no recovery without a process restart.

## 2. Evidence (traced, not taken from the issue)

| # | Fact | Evidence |
|---|---|---|
| E1 | The auth interceptor calls `authToken()` through `runBlocking` on the OkHttp worker thread, i.e. while the call **owns a per-host dispatcher slot**. | `core/network/.../NetworkModule.kt:88` (and the 401 retry at `:95`) |
| E2 | The anonymous token is fetched **through the same retrofit/client** whose slots are held, i.e. the nested call needs a second slot on the same client. | `AnonymousSession.kt:30` (`api.temporaryToken()`), session is built from `nc.api` at `app-mobile/.../di/AppModule.kt:64` and `app-tv/.../TvAppModule.kt:67` |
| E3 | Retrofit suspend methods are dispatched with `Call.enqueue`, so interceptors run on the client's Dispatcher executor — not on the caller's coroutine thread. | `retrofit2/KotlinExtensions.await` → `enqueue` (Retrofit 3.0.0 bytecode) |
| E4 | The Dispatcher defaults (nothing in the repo configures a Dispatcher) are `maxRequests = 64`, **`maxRequestsPerHost = 5`**. The "5 parked threads" in the issue's dumps *are* the per-host limit. | `javap` of `okhttp3.Dispatcher.<init>` (OkHttp 5.5.0, `gradle/libs.versions.toml:6`); no `Dispatcher`/`maxRequests*` reference anywhere in `app-*`/`core/*` |
| E5 | The mutex makes it permanent: the 4 waiters hold their slots while they wait (`AnonymousSession.kt:25`) and one owner holds the 5th while it waits for its nested call to be dispatched → no slot ever frees. | `AnonymousSession.kt:23-39` |
| E6 | `runBlocking` is not interruptible by OkHttp's 60s `callTimeout`: `RealCall.cancel()` never interrupts the worker thread, and `Dispatcher.finished()` only runs in `AsyncCall.run`'s `finally` — which never executes while the interceptor is parked. So the slot is never released; the queue never drains. | `NetworkModule.kt:118` (`callTimeout(60s)`); OkHttp 5.5.0 `RealCall.cancel` has no `Thread.interrupt`; `Dispatcher.finished$okhttp` called only from `AsyncCall.run` |
| E7 | Nothing seeds the anonymous token off the request path — the first request of the process always pays the nested fetch inside the interceptor. | only call sites of `token()` are `AppModule.kt:50` and `TvAppModule.kt:54` |
| E8 | The TV home composes several independent network surfaces, so its cold start is the one burst that can reach the per-host limit: Trending row, Explore/top-creators, Top This Week (+ Favorites when favorited, + For You/Liked/Following when signed in). | `app-tv/.../TvHomeScreen.kt:203-330`, `TvHomeViewModel.kt:40-41,162-177` |
| E9 | Mobile home is a **single pager** (`FeedViewModel` → `repository.paging(feed)`), so its cold start stays at 1–2 concurrent API calls — below the per-host limit. That is the structural reason mobile is not hit, not luck. | `feature/feed/.../FeedViewModel.kt:138` |
| E10 | Consequence in the UI is exactly as reported: rows only render the error branch when `loadState.refresh is LoadState.Error`. | `app-tv/.../TvHomeScreen.kt:537-546`, `AutoRetryEffect` at `:513-521` |

## 3. Root cause (verified; one correction to the issue)

**Correct as stated:** nested `v2/auth/temporary` call cannot be dispatched because the pool that must
run it is saturated by calls parked on the mutex, permanently (E1–E6).

**Corrections:**

1. It is **not** "the 5 OkHttp dispatcher threads" as a total pool — the pool is unbounded/elastic and
   `maxRequests = 64`; the binding limit is **`maxRequestsPerHost = 5`** (E4). That matters for the fix:
   raising the total limit, or adding threads, would not help.
2. `AGENTS-NETWORK.md:35` claims the contract is `maxRequestsPerHost = 4`; the code never sets it, so the
   real value is OkHttp's default 5. The doc line is wrong and should be corrected as part of the fix.
3. "Mobile is unaffected because the phone's first request path does not saturate the pool the same way"
   is only half right: mobile is a one-pager surface (E9), so saturation is *unreachable* there today —
   it is not a device property and not a guarantee.
4. The issue says the deadlock needs "the owner's nested call needs a dispatcher thread"; precisely, it
   needs a **per-host slot**, and the owner's own call already holds one of them (so 5 calls in the auth
   phase is the locking state, not 6).
5. The deadlock is **not offline-specific**. Any process state where a token must be fetched while ≥5
   same-host calls are in flight locks identically (e.g. the hourly proactive refresh, or a fresh install
   on a fast network where the four row calls all enter auth before the temp call returns). Offline just
   makes it reproducible because the failing calls hold their slots for the interceptor backoff.

## 4. Chosen approach

**Issue candidate 2: give the temp-token call its own client (own Dispatcher) and bound the acquisition
so a stuck fetch becomes an `IOException` for that request instead of a hang for the whole process.**

Two small, independent pieces, both required:

### 4.1 `tokenClient` + `tokenApi` in `buildNetwork`

A third client, built **not** from `clientBuilder()`:

* own `OkHttpClient` instance ⇒ own `Dispatcher` and own executor (OkHttp creates one per client) ⇒ the
  temp call can always be dispatched no matter how many API-client slots are parked. The mutex already
  single-flights it, so at most one token request is ever in flight.
* **`BrowserIdentityInterceptor` — the same instance the other two clients use.** The anon token is
  UA-bound (`valid_agent`; a different UA ⇒ `401 WrongSender`, `AGENTS-NETWORK.md:38`), and today each
  client builds its own `BrowserIdentityInterceptor` with its own `X-Session-Id`
  (`BrowserIdentity.kt:91-95`). One shared instance keeps identity byte-identical between the token
  fetch and the requests that use that token.
* `RateLimitInterceptor(limiter)` with the **same** `RollingWindowRateLimiter` — the temp request is real
  API traffic and must keep counting against ≤15 req / 5 s (it does today, via the shared client).
* **no** auth interceptor (that is the re-entrancy guard, now structural rather than a path check) and
  **no** `RetryInterceptor` (a failing token fetch must not sit in 1 s/4 s/15 s backoff while callers
  wait on it; failing fast is what makes the row error honest).
* short, explicit bounds: `connectTimeout`/`readTimeout`/`callTimeout` = `tokenTimeoutMs` (default
  10 s) instead of the API client's 15 s/30 s/60 s.

`NetworkComponents` gains `tokenClient: OkHttpClient` and `tokenApi: GifsApi` (a `Retrofit` over
`tokenClient`), mirroring the existing `imageClient` shape so tests can assert on it.

### 4.2 Bound the acquisition inside `AnonymousSession`

`token()` wraps its whole body in `withTimeoutOrNull(acquireTimeoutMs)` (default 10 s), replacing
`mutex.lock()` with `mutex.withLock` (cancellation-safe release, same semantics). On timeout → `null`,
i.e. **fail open**: the request goes out without a Bearer, the upstream answers 401/IOException, the
paging source surfaces `LoadState.Error`, and the section 3/honesty machinery already in the app takes
over. Nothing hangs.

The bound is **per caller**, so N parked callers cost `acquireTimeoutMs` in total, not N × timeout. That
is also why the bound lives here and not in the interceptor: `AnonymousSession` owns "how long may a
caller wait for a token".

### 4.3 Why this and not candidate 1 (resolve the token before the chain)

Candidate 1 (pre-resolve + refresh off the call path) is architecturally cleaner but a much wider and
lossier change: the token would have to be threaded through every call site (all paging sources,
`FeedPageFetcher`, the two network-live sources, the write actions, both image paths), the
refresh-on-401-once net (`NetworkModule.kt:88-105`, `AGENTS-NETWORK.md:23`) would have to be
re-implemented at each call site, and the image client would need its own copy of the same machinery.
It also does not cover the 401 path, which *must* fetch a token from inside the interceptor. Candidate 2
removes the deadlock at its cause (slot starvation) and leaves every existing auth behaviour intact.

## 5. Files / functions to change

| File | Change |
|---|---|
| `core/network/src/main/kotlin/com/rjbiermann/giffyviewer/core/network/NetworkModule.kt` | hoist one `BrowserIdentityInterceptor` instance and pass it to both `clientBuilder()` clients (they currently get one each, `:119`); add `tokenClient` + `tokenApi`; `buildNetwork(..., tokenTimeoutMs: Long = 10_000)`; extend `NetworkComponents` (`:33-42`) with `tokenClient`, `tokenApi` |
| `core/network/src/main/kotlin/com/rjbiermann/giffyviewer/core/network/AnonymousSession.kt` | `token()` bounded by `withTimeoutOrNull(acquireTimeoutMs)` + `mutex.withLock`; constructor gains `acquireTimeoutMs: Long = 10_000`; doc comment updated (re-entrancy note now says *why* the token client must never be the shared one) |
| `app-mobile/src/main/kotlin/com/rjbiermann/giffyviewer/mobile/di/AppModule.kt` | `:64` → `AnonymousSession(nc.tokenApi)` |
| `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvAppModule.kt` | `:67` → `AnonymousSession(nc.tokenApi)` |
| `core/network/src/test/kotlin/.../AnonymousTokenDispatchTest.kt` | new — see §7 |
| `docs/AGENTS-NETWORK.md` | correct `maxRequestsPerHost = 4` (line 35) to the truth (default 5, not configured) and add one contract line: *no application interceptor may issue a network call — a nested call competes for the same per-host slots; token acquisition goes through `tokenApi`*. `AGENTS-AUTH.md` needs no change (bearer contract unchanged). |

Nothing else. `RetryInterceptor`, `RateLimitInterceptor`, `CircuitBreaker`, the 401 handling, the UI,
the TV error copy and `FeedErrorKind` are untouched.

## 6. Must not regress

1. **Signed-in Bearer injection** — `authToken` still reads `TokenStore` first (`AppModule.kt:50`,
   `TvAppModule.kt:54`); signed-in requests never touch `tokenApi`.
2. **401 → refresh → single retry** (`NetworkModule.kt:88-105`): unchanged, still exactly one retry,
   still falls back to the anon session via `onUnauthorized`.
3. **Anonymous single-flight** — one temp fetch per expiry window, racing callers reuse it
   (`AnonymousSession.kt:24,27`).
4. **Rate-limit accounting** — the temp request still consumes a slot from the shared limiter; the
   ≤15/5 s invariant is unchanged (and the new client must not get its own limiter).
5. **UA / session identity** — the token fetch and all subsequent requests must carry identical
   `User-Agent` and `X-Session-Id` (E: `AGENTS-NETWORK.md:38`); hence the shared interceptor instance.
6. **Offline error honesty (#63)** — a failure must arrive as `IOException` (`feedErrorKind` →
   `Offline` when the radio is down), never as a hang; `AutoRetryEffect` + `BreakerReconnectReset`
   must be able to recover it.
7. **TV and mobile both** — the two DI modules change identically; TV is the surface that was broken,
   mobile is the surface that must stay green.
8. **No new dependency, no `GlobalScope`, no WorkManager, no proactive refresh timer** (structured
   concurrency only; the bound is `withTimeoutOrNull`).

## 7. TDD plan (JVM rung, `:core:network`; MockWebServer, no device)

Write in this order — each test must be seen failing first.

1. **`AnonymousTokenDispatchTest.5 concurrent calls never deadlock on the anonymous token`** (the
   regression test for this issue)
   Setup: `MockWebServer` + a dispatcher that answers `/v2/auth/temporary` with `{"token":"temp"}`
   after a ~300 ms body delay (so the four other callers are already parked) and every `/v2/*` path with
   a minimal page DTO; `buildNetwork(baseUrl = server.url("/"))`; `session = AnonymousSession(nc.tokenApi)`;
   `authToken = { session.token() }`.
   Act: 5 concurrent `nc.api.trendingPopular()` on a 5-thread pool, then `assertTrue(latch.await(3, SECONDS))`.
   Assert: all 5 complete; the socket log shows exactly **one** `/v2/auth/temporary` request; all 5 feed
   requests carry `Authorization: Bearer temp`.
   Red first: with `AnonymousSession(nc.api)` (today's wiring) the latch times out — that is the
   mechanism proof; the durable test keeps `nc.tokenApi`.
2. **`… a token endpoint that never answers fails the request instead of hanging`**
   `MockWebServer` dispatcher returns `SocketPolicy.NO_RESPONSE` for `/v2/auth/temporary`;
   `buildNetwork(tokenTimeoutMs = 300)`, `AnonymousSession(nc.tokenApi, acquireTimeoutMs = 300)`.
   Assert: the feed call throws `IOException` (not `TimeoutCancellationException`, not a hang) within
   ~2 s, and `feedErrorKind(thatThrowable)` classifies it (offline honesty pin, `feature:feed`).
3. **`… the token request keeps the shared limiter and the browser identity`**
   Assert `nc.tokenClient.dispatcher !== nc.client.dispatcher` and `!== nc.imageClient.dispatcher`
   (the isolation contract — someone "simplifying" by sharing a Dispatcher reintroduces #64);
   `nc.tokenClient.interceptors` contains `RateLimitInterceptor` and `BrowserIdentityInterceptor` and
   **not** an auth interceptor; the recorded token request's `User-Agent` and `X-Session-Id` equal the
   feed request's.
4. **`… signed-in bearer and the 401 refresh path are unchanged`**
   `authToken` returns `"user.jwt"` then `"new.jwt"`; server answers 401 to the first authenticated call
   and 200 to the retry. Assert: `onUnauthorized` invoked once, exactly one retry, second request carries
   `Bearer new.jwt`, and `/v2/auth/temporary` is never requested.

Also green (existing): `RollingWindowRateLimiterTest`, `RetryAndBreakerTest`, `ImageClientTest`
(its `imageClient.interceptors.size == client.interceptors.size - 1` invariant must still hold),
plus `./gradlew check` on the touched modules (compile + ktlint + detekt + unit tests).

### Device gate (after green, not in this design pass)

TV: `pm clear` + airplane-mode cold start → each row shows "Couldn't load <title>" + Retry within one
retry cycle, `logcat` free of parked-thread dumps; reconnect → `AutoRetryEffect` recovers the rows.
Mobile: same cold start as a regression check (it was never broken; it must not become broken).

## 8. Non-goals

* No removal of `runBlocking` from the interceptors (the chain is synchronous by contract); no
  `RateLimitInterceptor`/`RetryInterceptor` redesign.
* No change to the token TTL policy (1 h proactive / 24 h validity), no proactive refresh timer
  (`AGENTS-NETWORK.md:25`), no negative caching of failed token fetches.
* No `maxRequestsPerHost` tuning, no extra threads, no second rate limiter.
* No UI work: the TV error copy, `AutoRetryEffect` and `BreakerReconnectReset` already exist.
* `TokenStore.refreshBlocking()` (`HttpURLConnection`, up to 25 s on the interceptor thread) stays as is
  — it does not need a dispatcher slot, so it is not part of this deadlock; noted as adjacent debt only.
* No change to any endpoint path or DTO.

## 9. Risks

1. **Second connection pool** — the token call no longer reuses the API client's pooled connection: one
   extra TLS handshake per token fetch (≈ hourly). Accepted; sharing a `ConnectionPool` is possible later
   if it ever shows up.
2. **Fail-open trade-off** — a timed-out acquisition sends one unauthenticated request, which the API
   answers 401 (`AnonymousSession`'s doc comment records that this used to cause session-wide 401s when it
   was the *normal* path). Here it is the exceptional path, bounded, and self-healing on the next request;
   the risky window is "flaky network that accepts connections but never answers", not offline.
3. **Bounded ≠ instant** — worst case a row takes `acquireTimeoutMs` + the request's own timeout before it
   errors. That is the intended trade (bounded honesty over indefinite spinners), but the device gate must
   confirm the perceived delay is acceptable on TV.
4. **Timing-based test** — test 1 relies on 5 threads + a 300 ms body delay; keep the assertion window
   generous (3 s) so it cannot flake on a loaded CI box.
