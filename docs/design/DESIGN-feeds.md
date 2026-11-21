# DESIGN-feeds (Stream C) — issues #58, #54

Owner modules: `:core:network` (#58), `:feature:feed` (#54) + small DI wiring in both
app modules (#58). No core:player/app-tv edits.

## Objective

- **#58** — after using the app for a while, ALL feeds surface "couldn't load [feed]"
  at once (the TV wording interpolates the feed name, so the reporter is likely on TV
  home rows: `app-tv/.../TvHomeScreen.kt:399` and `:537`; mobile equivalent is the
  feed error EmptyState, `feature/feed/.../FeedScreen.kt:435–447`).
- **#54** — the Favorites feed shows one creator's run of videos, then the next
  creator, etc. (expected: videos interleaved across favorited creators; endless feed
  until every creator's pool is exhausted; refresh should serve a different mix).

---

## Issue #58 — every feed "couldn't load" after a while

### Current behavior + evidence — one shared network stack for API *and* images

All HTTP traffic — API pages AND poster thumbnails — runs through ONE OkHttp client
and therefore ONE limiter, ONE retry loop and ONE circuit breaker:

- `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvAppModule.kt:108–113` and
  `app-mobile/src/main/kotlin/com/rjbiermann/giffyviewer/mobile/di/AppModule.kt:108–113`
  — Coil `ImageLoader` built with `OkHttpNetworkFetcherFactory(network.client)` (the
  same client; the comment even says "Images share the API's OkHttp instance → same
  rate limiter covers media").
- That client carries the full chain (`core/network/.../NetworkModule.kt:99–131`):
  auth interceptor → `RateLimitInterceptor` (every call acquires a window slot via
  `runBlocking`, `rate/RetryInterceptor.kt:118–124`) → `RetryInterceptor` (1 initial
  + 3 retries per call, `RetryInterceptor.kt:41`).
- **Breaker accounting per attempt:** every `IOException` attempt calls
  `breaker.recordFailure()` (`RetryInterceptor.kt:53, 83`) — so a single failing
  request contributes up to 4 consecutive failures; the breaker opens for **5 minutes
  at 3** (`rate/CircuitBreaker.kt:10–12`). While open, EVERY call — all feeds, all
  surfaces — gets the synthetic retryable 503 (`RetryInterceptor.kt:43–56`), which
  surfaces as `MediatorResult.Error` → "Couldn't load …" on every row at once
  (`FeedMediator.kt:96–104`).
- **Joint-window saturation:** TV home fires ~10 feed pages at once (batch-29
  evidence) and grids load dozens of posters; both burn the same 15 req/5s rolling
  window (`rate/RateLimiter.kt:40`), and OkHttp's default `maxRequestsPerHost = 5`
  dispatcher (no override anywhere in the repo — grep confirms) starves behind
  `runBlocking` limiter delays; the batch-32/33 session records the starvation family
  plus the `callTimeout(60s)` mitigation (`NetworkModule.kt:104–106`).
- **Cooldown pinning:** `startCooldown` extends on every 429 with `maxOf`
  (`RateLimiter.kt:56–59`); joint API+image bursts can 429 repeatedly, re-extending
  the window; queued calls burn their 60s `callTimeout` → IOException → (4 failures)
  → breaker opens. Session-log batch 32 flagged the "background loop can pin it open
  indefinitely" shape as an audit item — still open.

### Root-cause hypothesis (high confidence, needs device confirmation)

**Shared-pipeline poisoning by media traffic (or any transient burst):** once a
session has scrolled enough to churn many poster loads, one flaky window (network
blip, joint 429 storm, slow CDN) is enough to (a) open the circuit breaker with
exactly one image/page request's retries, and (b) pin a limiter cooldown. While open
or cooled down, every feed page load fails → "Couldn't load [feed]" on ALL feeds
simultaneously; the TV home auto-retry (2 retries, batch 31) refires into the open
breaker and stays failing until the breaker's 5 minutes lapse. "After using a while"
= enough browsing to hit the first stale-thumbnail churn / token-expiry hour.

Secondary contributor: the token-expiry hour (id_token lifetime) doubles every
authed call (401 → refresh attempt → retry) for a while, adding burst traffic that
can trip the same window.

### Proposed approach (minimal)

1. **Take images off the breaker/retry path.** Build the Coil `ImageLoader` with a
   slimmed client derived from the same `BrowserIdentity`+auth headers but WITHOUT
   `RetryInterceptor` (no retry storm, no breaker accounting) — either via
   `client.newBuilder().interceptors(…)` pruning, or a dedicated build helper in
   `:core:network` (`fun buildImageClient(base: OkHttpClient): OkHttpClient`). One
   function, two call sites (AppModule + TvAppModule).
2. **Don't let one request's retries feed the breaker 4×**: in `RetryInterceptor`,
   record ONE failure per exhausted request (the recordFailure sites at lines 53/83),
   not per attempt — restores the intended "3 consecutive failed CALLS" semantics.
3. Keep the limiter shared (rate-limit invariant must hold for ALL requests) but
   consider not counting synthetic-breaker-503 responses as 429-cooldown extenders —
   the breaker already owns that backoff (check `RetryInterceptor.kt:60–77`, the 429
   branch, vs the synthetic 503 path; today the open-circuit 503
   never re-enters the loop, so no double-pinning exists — verify, don't change
   blindly).

### Acceptance conditions

- Unit tests (`:core:network`): (a) a request failing all 4 attempts adds exactly ONE
  breaker failure; (b) three such exhausted requests (not 3 attempt-failures) open
  the breaker; (c) image-client builder strips RetryInterceptor and keeps
  BrowserIdentity + auth headers.
- Device gate (repro-first, TV): reproduce "all rows couldn't load" with airplane/
  degraded network toggle while scrolling (deterministic offline-toggle recipe from
  batch 33), then confirm recovery time shrinks from "5 min breaker" to "next
  success".
- All touched module gates green.

### Non-goals

- No per-feed/parallel breaker instances beyond the image/API split.
- No change to the 15/5s invariant or its test.
- No coroutine-aware interceptor redesign (documented as the longer-term follow-up
  candidate in batch 33; out of scope here).

---

## Issue #54 — Favorites feed: interleave across creators, endless, refresh varies

### Current behavior + evidence

- Page mapping: `feature/feed/.../FeedPageFetcher.kt:110–135` — Favorites page n →
  ONE creator (`order[(page - 1) % creators.size]`) with inner page
  `(page - 1)/creators.size + 1`, and `pageSize = PAGE_SIZE = 20`
  (`FeedRepository.kt:356`), prefetchDistance 10 (`FeedRepository.kt:302`).
- So every visible chunk ≈ one screen of 20 gifs **from a single creator** — the
  reported "one creator continuously then next" is the mapping's literal output.
- The end-of-feed machinery (#36, `FeedPageFetcher.kt:37–101`) already makes the feed
  endless across loops and honest at exhaustion; `favoritesLoopOrder`
  (`FeedPageFetcher.kt:201–236`) re-shuffles creator ORDER per loop.
- Refresh: pull-to-refresh/TV refresh → `FeedViewModel.forceRefresh()` →
  `FeedRepository.paging(forceRefresh = true)` → `reshuffleFavorites()`
  (`FeedRepository.kt:214–221, 229–235`) — evicts cache + new rotation seed. BUT the
  rotation only changes creator ORDER; the inner pages re-walk from page 1 for every
  creator, so refresh serves the SAME first videos of each creator (possibly in a new
  order) — "refresh should show different videos" is only weakly satisfied.

### Root-cause hypothesis (certain for the grouping; partial for refresh)

The per-page monolith (one creator per page of 20) is the design as shipped; the
interleave ask was never implemented (the round-robin exists only at page
granularity). Refresh variance is likewise only an order rotation.

### Proposed approach (minimal)

Keep the existing cache/end-of-feed machinery; change ONLY what a Favorites page
FETCHES:

1. **Interleave within a page:** in `FeedPageFetcher.fetch()` for `FeedSource.Favorites`,
   fetch the loop's creators in rotation order with a small per-slot count
   (e.g. `SLOT_COUNT = 5`, ceil(20/5) = 4 sequential `api.userGifs` calls per fill —
   limiter-paced, still counted requests) and round-robin merge the chunks into the
   page's gif list. FeedPageEntity/mapping unchanged. Note: this multiplies API
   requests per fill by creators.size — acceptable (each fill is a user-visible
   page, and the budget covers it), but the fill loop must acquire its slots
   sequentially (already the shape of every other multi-call path).
2. **End-of-feed rule adaptation:** `previousRotationExhausted` reads cached empty
   slots per rotation slot — with K sub-fetches per slot, an empty slot is one where
   ALL sub-fetches came back empty; store the per-slot emptiness the same way (an
   all-empty slot's page row is empty) — the existing rule works unchanged.
3. **Refresh variety:** rotation (already lands via `reshuffleFavorites`) + per-slot
   interleave makes consecutive refreshes serve a different mix. For deeper variety
   (different videos per refresh, not just reordered) there is no server offset
   primitive verified for `users/{username}/search` — do NOT invent one. Acceptance
   wording: refresh shows a different interleave/order; content novelty is bounded
   by each creator's pool walk (inner pages advance across loops).

### Acceptance conditions

- Pure-function unit tests (`:feature:feed`): interleave merge order for N creators ×
  K chunks (deterministic rotation, blocked-creator drops intact); empty-sub-slot
  semantics feed `previousRotationExhausted` unchanged (existing FeedMediatorPoolCap /
  FeedWalk test family extended).
- Device gate (mobile): 3+ favorited creators → Favorites feed screen shows creators
  alternating within one screenful; feed walks endless across loops; refresh →
  different order (and new content as pools advance).
- Rate-limit invariant test untouched/green (limiter covers the extra calls).

### Non-goals

- No server-side blending (no invented endpoint/param).
- No For You weight changes (70% favorites weight rule untouched).
- No change to the Custom feed mapping (same round-robin shape, separate ask).
- No persistence for the shuffle seed (normative: dies with the process).
