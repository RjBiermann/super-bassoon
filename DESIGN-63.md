# DESIGN-63 — "couldn't load feed" (v0.7.2)

Investigation + design only; no product code changed. Fix owner modules: `:core:network`, `:core:ui`,
`:feature:feed` (+ copy-only reuse in `app-tv`).

## Objective

Issue #63 says a feed still refuses to load on v0.7.2 with copy that names no cause. #61 already gave
that card working retry affordances (tap + pull-down), so the open questions are: which failures still
reach that card, does the card tell the truth about *why*, and can the card outlive the failure it
describes.

## Report restated

> issue - couldn't load feed
> - "something went wrong"
> - "tap to retry, or pull down"

Filed 2026-10-23T20:24Z, ~1 h after `v0.7.2` (= `32251dec`, which already contains #61 `ccf9e6a`).
Labels: bug, mobile, tv. The two quoted strings pin the surface: only the **mobile** feed error card
produces that pair — TV renders `"Something went wrong"` + `"Retry the feed"`
(`app-tv/.../TvSourceFeedScreen.kt:374-380`) or `"Couldn't load <title>"` (`TvHomeScreen.kt:399,537`).
The generic (non-offline) variant is what was reported, i.e. the pager failed with something that is
**not** an `IOException`.

## 1. Where the strings come from and what reaches them

- `feature/feed/.../FeedScreen.kt:447` — `message = if (offlineShaped) "Nothing cached yet" else
  "Something went wrong"`; `:452` — `hint = "Tap to retry, or pull down."` (the offline variant says
  "You're offline — reconnect to load the feed.").
- Gate: `itemCount == 0 && items.loadState.refresh is LoadState.Error` (`FeedScreen.kt:405`, `:432`).
  Every source renders it (Trending/Search/Creator/Niche/Custom/Favorites/Liked/For You): cached ones
  via `FeedMediator`/`FeedPagingSource`, network-live ones via `ForYouNetworkPagingSource`/
  `LikedNetworkPagingSource`.
- Classification is one boolean (`FeedScreen.kt:411`): `error is IOException` → offline copy, else
  "Something went wrong". Batch 41's A3 fix removed the older lie ("You're offline" for every
  failure); what remains is a catch-all of unrelated causes.

## 2. Failure path (current code)

- Sources: `FeedPagingSource.kt:202-221` catches `Throwable` → `LoadResult.Error`;
  `FeedMediator.kt:93-114` catches `IOException`/`HttpException`/`Exception` → `MediatorResult.Error`;
  400 past page 1 = pool end, not an error (`FeedMediator.kt:104-107`, `FeedPagingSource.kt:100-107`).
  No status-mapping layer exists (`:core/network` defines only `CircuitOpenException`), so the UI sees
  raw `IOException`, `retrofit2.HttpException`, or serializer exceptions.
- Stack order (`NetworkModule.kt:110-141`): BrowserIdentity → auth (401) → `RateLimitInterceptor` →
  `RetryInterceptor` → network, so **401 is decided above the retry/breaker layer** and never touches
  breaker bookkeeping. 401 flow (`:92-104`): refresh grant once, else clear token + anon fallback,
  retry once, second 401 surfaces; body logged as `Auth401 <path> body=…` via `println` (tag `System.out`).
- `rate/RetryInterceptor.kt`: breaker open → synthetic **503** `"circuit open"` + `Retry-After`
  (`:29-37`); 5xx → ×3 at 1 s/4 s/15 s (`:52-55`); 429 → cooldown + retry (`:57-73`); other codes →
  `recordSuccess` (`:75-79`); exhausted → **one** breaker failure per call (#58, `:85-95`) then
  `throw IOException("max retries exhausted")` (`:95`).
- `CircuitBreaker.kt:15-16`: 3 consecutive failures → open 5 min (`recordFailure :31-36`); **one
  instance per process** (`NetworkModule.kt:68`) — the census is client-wide: not per host, endpoint,
  or feed.
- Limiter (`rate/RateLimiter.kt:53-59`): 429 cooldown `maxOf(current, now+Retry-After)`, min 5 s;
  `waitForSlot :72-97` queues callers; `callTimeout(60s)` (`NetworkModule.kt:118`) bounds a call parked
  in cooldown/backoff (expiry is an `IOException`). `FeedViewModel.forceRefresh()` (`:144-146`) bumps
  the generation, whose reset emits `refresh = Loading` and drops the old pager (`:119-140`); Paging
  never auto-retries a mediator error, so #61's tap/pull is the only net.

## 3. Ranked candidates (evidence + verdict)

**(1) Breaker open (client-wide) + a card that never self-clears — STRONGEST.** A breaker-open call
returns the synthetic 503, which Retrofit converts to `HttpException` — *not* an `IOException` — so
`FeedScreen.kt:411` files it under "Something went wrong". While open, every feed fails at once for up
to 5 minutes and mobile has no automatic recovery: every other error surface runs `AutoRetryEffect`
(TV rows `TvHomeScreen.kt:376`, TV niches `TvNiches.kt:106`, mobile niches `NichesScreen.kt:96`), the
mobile feed card is the only one that does not. Batch 42 already ruled the trigger legitimate
("all-feeds-couldn't-load after offline bursts is legit CircuitBreaker … recovery is one tap after
cooldown") — #63 is that state read as a broken feed. *Refuted sub-claim:* "(a) a 401 loop opens the
breaker" does **not** hold — 401 is handled above the retry layer and records nothing.

**(2) Sign-in-required dead end (For You / Liked) — REAL BUT NARROW.** `GET /v2/feeds/for-you` with
the anon token returns `401 {"error":{"code":"UserTokenRequired"}}` (live probe 2026-10-23, repo
BrowserIdentity headers). `AppModule.kt:50-60` clears a dead user token, so `isLoggedIn` goes false and
the For You chip disappears (`FeedTabStrip.kt:120-126`) — but a user already on For You or Liked keeps
that source, whose 401 reads "Something went wrong / Tap to retry" and can **never** succeed.

**(3) 429 / limiter starvation — CONTRIBUTOR, AND A COPY LIE.** All-429 exhaustion ends at
`RetryInterceptor.kt:95` as a plain `IOException("max retries exhausted")` (429s set no
`lastException`), so a **rate-limited** feed renders the **offline** copy; 429 never opens the breaker
(`:71`, `:85-90`). Cooldown is bounded (≥5 s per 429, extended only while new 429s arrive) so it cannot
explain a 5-minute stuck card alone, but call-timeout expiry in that queue chains into (1); amplifiers:
one call per favorited creator per Favorites page (`FeedPageFetcher.kt:137-147`, #54), TV home opening
~10 rows, posters sharing the window (`NetworkModule.kt:47-54`). *Evidence gap:* release builds have no
HTTP logging (`NetworkModule.kt:60`, `TvAppModule.kt:64`) and nothing logs a 429 or a breaker
transition, so a user-reported 429 is invisible in logcat today.

**(4) Upstream 5xx — POSSIBLE, UNPROVABLE AFTER THE FACT.** Same generic copy, same 5-min stuck shape
via (1). Live probe today: `/v2/feeds/trending/popular` → 200 (30 KB of real rows),
`/v2/gifs/search?order=trending|top` → 200 — nothing is broken *now*.

**(5) Endpoint drift — NO EVIDENCE.** Refuted for the default feed by the 2026-10-23 probe; niche
`order=trending` drift is already handled (`FeedPageFetcher.kt:150-158`), and a drifted *field* would
render an empty grid, not this card (`GifsPageDto.gifs` defaults empty; only `GifDtoShell.id` required).

**(6) Stale error state — MECHANISM, not a cause:** once latched, only a new load clears the card (§5).
It cannot produce the state, but guarantees it *persists* after (1)–(4) end.

**Evidence that would settle which one it was** (all missing today, hence §10.1):
`adb logcat -s FeedMediator:W` — a page-1 line with `HttpException: 503 circuit open` proves
breaker-open, `java.net.*`/`InterruptedIOException` proves IO/timeout, the same line repeating on every
tap for minutes proves the latched card; `System.out:W | grep Auth401` with `UserTokenRequired`/
`TokenExpired`/`WrongSender` proves (2); 429 is invisible in release logcat (§3.3).

## 4. What already exists (do not duplicate)

- `AutoRetryEffect` / `shouldAutoRetry` (≤2, 2 s debounce, re-fires on `isOnline` flip) —
  `AutoRetryPolicy.kt:15-52`; used by TV rows, TV niches and mobile niches, **not** the mobile feed.
- `NetworkMonitor` → `LocalIsOnline` (`core/ui/NetworkMonitor.kt`, `OfflineBanner.kt:20`, provided in
  `MainActivity.kt:141-143`, `TvMainActivity.kt:173`) — banner only, no feed reads it.
- Tap/pull retry (#61, `FeedScreen.kt:441-456`, device-gated batch 42) via `forceRefresh()`
  (`FeedViewModel.kt:144-146`); the `onOpenAccount` sign-in seam is wired but unused by the card; 401
  refresh + anon fallback (`NetworkModule.kt:92-101`, `AppModule.kt:50-60`) works silently.
- What is left is small: **honest classification, and automatic recovery from self-healing states.**

## 5. Can the card be shown while a retry silently succeeds? (exact transitions)

Today: **no** — there is no silent retry on mobile, so the card cannot coexist with a successful one;
the defect is the mirror image, the card **outliving** the failure. One pager generation `G`
(`FeedViewModel.kt:119-140`, `FeedScreen.kt:405-432`):

1. `G` starts → reset emit `refresh = Loading`, `itemCount = 0` → card not composed; a failed load →
   `refresh = Error`, `itemCount = 0` → **card**.
2. Only edges out of it: user tap / pull-down (`forceRefresh` → gen+1 → 1); source change or pref-table
   invalidation (pager rebuild → 1); a pref-table write invalidating `FeedPagingSource` (incidental).
   Nothing else.
3. So breaker closing, network returning, upstream recovering, or a later mediator write **do not clear
   the card** — waiting out the cooldown changes nothing until the user touches the screen.
   (`notifyRefreshError` (`FeedViewModel.kt:94-102`) is no exception: it only swaps the *seeded* For You
   source to Trending, before any user navigation.)

## 6. Minimal fix plan

1. **`core/network/.../rate/RetryInterceptor.kt`** — add
   `class RateLimitedException(val retryAfterMs: Long) : IOException(...)`, thrown at `:95` when the last
   attempt was a 429 (`var sawThrottle` in the loop). Subclassing `IOException` keeps every existing
   `catch` intact (`FeedMediator.kt:96`, paging sources, #61); ~6 lines, and without it rate-limited is
   indistinguishable from offline and no test can tell them apart.
2. **`feature/feed/FeedErrorKind.kt` (new, pure)** — `feedErrorKind(Throwable?): FeedErrorKind`
   (`Offline | RateLimited | SignInRequired | Upstream | Generic`) + `feedErrorCard(kind)` →
   `FeedErrorCard(message, hint)`. Precedence: `RateLimitedException` → `HttpException(401)` →
   `IOException` → other `HttpException` → `Generic`. Copy: offline keeps today's wording; rate-limited
   "Rate limited — retry in a moment."; sign-in "Sign in again to load this feed."; upstream "Server
   trouble — tap to retry." One pure function, five strings, unit-testable without Compose.
3. **`feature/feed/FeedScreen.kt:405-460`** — replace `offlineShaped` with the kind; add
   `AutoRetryEffect(error = refresh is Error && itemCount == 0, success = refresh is NotLoading,
   key = refresh, isOnline = LocalIsOnline.current, retry = viewModel::forceRefresh)` (same shape as
   `NichesScreen.kt:96-102`); `SignInRequired` points the card's `onClick` at `onOpenAccount` (retry
   cannot succeed; pull-down stays). `core/ui/EmptyState.kt:28` gains `onClickLabel: String = "Retry"` so
   that card announces "Sign in" to TalkBack.
4. **`app-tv` copy reuse (separable)** — `TvSourceFeedScreen.kt:374`, `TvHomeScreen.kt:399,537` use
   `feedErrorCard(kind)` for message/hint; TV already auto-retries rows, so copy only.

## 7. Red-first acceptance tests

- `:core:network` `RetryInterceptorThrottleExhaustionTest` (MockWebServer, `RetryAndBreakerTest` harness):
  `all429ExhaustionThrowsRateLimitedException_notPlainIOException`,
  `rateLimitedExceptionIsStillAnIOException_soExistingCatchSitesAreUnchanged`,
  `rateLimitedExhaustionDoesNotFeedBreaker` (guards #58).
- `:feature:feed` `FeedErrorKindTest` (pure): `unknownHostIOExceptionIsOffline`,
  `callTimeoutInterruptedIOExceptionIsOffline`, `rateLimitedExceptionIsRateLimited_notOffline`,
  `http401IsSignInRequired`, `http503And500AreUpstream`, `serializerFailureIsGeneric_notOffline`,
  `offlineAndRateLimitedCardsNeverShareCopy`.
- `:core:ui` `EmptyStateClickTest` gains `emptyStateUsesCustomClickLabelForSignInCard`.

## 8. Device-gate plan (existing recipes)

1. **Airplane-mode cold start (batch 42 recipe)** — airplane ON, force-stop, launch → card says "Nothing
   cached yet / You're offline"; airplane OFF → card clears **without a tap** (new `AutoRetryEffect` on
   the `isOnline` flip) and the grid loads. Evidence: screenrecord + `adb logcat -s FeedMediator:W`.
2. **Breaker-open + recovery (#63 core)** — airplane ON, tap the card 3× (one exhausted call = one
   breaker failure) → open; airplane OFF → copy becomes "Server trouble — tap to retry." (honest 503
   copy), auto-retry stops at 2 attempts (no mash storm), one tap after the cooldown loads.
3. **Sign-in-required** — DebugSignIn bundle, open For You (loads), log out from Account while For You is
   the active source → card reads "Sign in again to load this feed"; tapping opens Account.
4. **429 copy** — instrumented, not manual: give `FeedMediatorOfflineTest`/`TestFeedGraph` a 429-only
   MockWebServer dispatcher and assert the copy (a real 429 is not deterministically inducible).
5. **TV parity** — D-pad to a failing row/page → copy variants, row auto-retry unchanged (0 FATAL).

## 9. Non-goals

- No breaker policy change (thresholds, 5-min window, per-endpoint scoping); no cooldown countdown or
  "cooling down" indicator (spec'd-not-built stays that way); no change to the ≤15/5 s invariant, no
  WorkManager, analytics, crash reporting, new endpoints or new fallback feed.
- Mobile append-error UI (a failed *append* renders nothing today) — separate gap. TV's full-page feed
  has no `AutoRetryEffect` (`TvSourceFeedScreen.kt:374` has a manual refresh button); adding it is a
  TV-behaviour decision, not part of this fix.

## 10. Open questions / risks

1. **#63's trigger is still unknown** — the card copy fits (1)/(2)/(3)/(4) equally, so the reporter's
   `adb logcat` trace (§3) plus the open feed's name decides it; without that we fix honesty + recovery,
   not a specific cause. Debug HTTP logging is off in both shells — flipping it for one capture is a
   one-line debug change, not part of the fix.
2. **Breaker reset on reconnect (deferred decision).** The census survives network transitions, so the
   airplane-toggle gate recovers only after the 5 minutes. A `NetworkMonitor` → `breaker.recordSuccess()`
   seam would fix that, but `CircuitBreaker` is unsynchronised plain state (`CircuitBreaker.kt:19-20`)
   written from OkHttp threads — fix that first. Network-policy change: decide separately.
3. **Mobile `AutoRetryEffect` shares the pager restart with the seeded-ForYou revert** — both can fire on
   a cold-start failure. Bounded (≤2 retries; revert wins pre-navigation), but gate §8.1 must confirm no
   double page-1 fetch.
4. **`SignInRequired` is heuristic** — a durable 401 with the anon fallback in place does mean sign-in
   required for For You/Liked; for other feeds the copy is slightly wrong (accepted).
5. Incidental: `FeedPagingSource.kt:48` (`CACHE_WAIT_MS`) is unused and the class comment at `:121-124`
   describes a cache wait that no longer exists — stale doc, unrelated to #63.
