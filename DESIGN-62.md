# DESIGN-62 — Favorites feed: slow first tiles + apparent end of pagination

Investigation + design only (no product code touched). Reporter is on **v0.7.2 = main `32251dec`**,
i.e. *after* #54 (interleave), #59 (stale-ledger revalidation) and #61 (error retry).

## Objective

Explain, from current code, why the Favorites feed (a) takes long before tiles appear and (b) appears
to stop paging after a couple of screenfuls — then fix the mechanism the evidence supports, keeping
the #54 interleaved look, the #36 exhaustion bound and the #59 cache contract.

## Report restated

> - takes time to load
> - there is no pagination. scrolling stops after few videos.
> - expected: videos loads endless unless there is no videos anymore.

## Which surface is "favorite feed" (mapping)

Both shells run one shared path, so one fix serves both:

- **Mobile** — "Favorites" `FilterChip` in the feed tab row (`FeedTabStrip.kt:132-135`) →
  `FeedViewModel.gifs` (`FeedViewModel.kt:128-140`) → `FeedScreen` grid.
- **TV** — "Favorites" home `FeedRow`, shown only when favorites exist
  (`TvHomeScreen.kt:264-284`, `TvHomeViewModel.kt:64-80`).
- Both: `FeedRepository.paging(FeedSource.Favorites)` → `cachedPager` (`FeedRepository.kt:300-340`);
  source `FeedPagingSource` (Room) + `FeedMediator` (network) + `FeedPageFetcher` (shared fill).

Not in scope: **Liked** and **For You** — network-live sources, no `fav:*` cache. "Favorites" =
favorited *creators* stored locally in `creator_prefs` (`Daos.kt:148`); no server restore of that set.

## End-to-end trace (current code)

1. `FeedSource.Favorites` = `keyBase "fav:v1"`, TTL 10 min (`FeedSource.kt:90-95,134`).
2. **REFRESH** (`FeedMediator.kt:62-89`): page-1 row fresh → `Success(end=false)`, no network;
   otherwise `runPage(1)`. It runs *before* the source's first load, so page 1 is fetched once.
3. **fill(page)** (`FeedPageFetcher.kt:32-82`): `rotationSize = favorites().size` = k (`:37`);
   exhaustion pre-check `loopOver && previousRotationExhausted(...)` → `return false` **without
   writing a row** (`:42-50`); `fetch` uses `order = favoritesLoopOrder(creators, seed, (page-1)/k)`
   (`:135`) and `perCreator = ceil(20/k)` (`:136`), then issues **one `userGifs` sub-request per
   favorited creator, sequentially, with `page = page`** (`:137-145`), round-robin merged
   (`:146`, `:248-256`); then `more` / `nextPageKey` (`:66-81`).
4. **Dry-pool rule** — `previousRotationExhausted` reads the k rows before `page` and declares the
   pool dry when all are empty (`:92-101`).
5. **walk** (`FeedPagingSource.kt`): `walkFill` serves a cached row, refills an unfetched one, and
   since #59 refills a **Favorites row older than the TTL** (`:75-108`, TTL gate `:92-100`);
   `walkFeedPages` hops up to `MAX_WALK_HOPS = 8` filtered-empty pages (`:52,119-150`); `load`
   returns `WalkedPage.gifs` + `nextKey` (`:202-221`). Read-time chain drops ids already listed by an
   earlier page (`:232-239`) and non-favorited creators (`:249-254,272`), then the prefs filters.
6. **Paging stops only when `nextKey == null`** — row missing (400, exhaustion pre-check) or row
   written with `nextPageKey = null` (`FeedPageFetcher.kt:73-81`). Short pages and duplicates never
   end a feed.
7. Network floor: `RollingWindowRateLimiter` = **15 requests / rolling 5 s** = 3 req/s, burst 15
   (`rate/RateLimiter.kt:40-45`; `callTimeout(60s)` per call, `NetworkModule.kt:118`).

## Quantified cost of one Favorites page

`perCreator = ceil(20/k)` and the fan-out is **every** creator, every page ⇒ requests per fill = k.

| k (favorited creators) | perCreator | requests / fill | gifs per request | 1st fill (cold) | sustained, per page |
|---|---|---|---|---|---|
| 3 | 7 | 3 | ~7 | ~0.5–1.2 s (burst) | ~1.2 s |
| 5 | 4 | 5 | 4 | ~0.8–2 s (burst) | ~1.7 s |
| 10 | 2 | 10 | 2 | ~1.5–4 s (burst + RTTs) | **3.3 s** |
| 20 | 1 | 20 | 1 | ~3–6 s (15 burst, 5 paced) | **6.7 s** |
| 40 | 1 | 40 | 1 | ~10–14 s | **13.3 s** |

Dead-pool cost is worse: ending costs **one full dead round = k pages × k requests = k²** (k=40 →
1 600 requests ≈ 9 min), and one `load` may burn `8 × k` requests (`MAX_WALK_HOPS`) before returning
— with only a 4 dp mobile line (`FeedScreen.kt:389-400`) and **nothing** on TV rows
(`TvHomeScreen.kt:511-536`) as feedback.

## Ranked root-cause candidates (with verdicts)

1. **O(k) per-fill fan-out from #54 (preserved by #59)** — one page = k sequential limiter-paced
   requests, so first tiles and every append scale with the favorites count, and gif density per
   request falls to 1 for k ≥ 20 (`FeedPageFetcher.kt:136-145`, limiter budget). **Verdict: PROVEN
   in code + arithmetic** — the only mechanism here that makes the feed *slow by construction*; it
   fully explains "takes time to load".
2. **k² dead-round / 8k hop amplification** — the exhaustion rule and the walk multiply the same
   fan-out (`:42-50,92-101`, `FeedPagingSource.kt:52,119-150`): once pools dry (or a page filters
   empty) one append can spend minutes of request budget and still show nothing.
   **Verdict: PROVEN in code; matters for large k or dry pools.**
3. **"Stops" is a perception of 1+2 with no append affordance** — before
   `endOfPaginationReached` the user sees no progress: mobile = 4 dp line only; TV row = D-pad focus
   cannot pass the last loaded card (`items(count = gifs.itemCount)`, `TvHomeScreen.kt:553`), so a
   3–13 s append reads exactly as "scrolling stops". **Verdict: PROVEN UX consequence of 1.**
4. **Honest pool exhaustion (thin pools)** — the end rule fires when the previous round of rows is
   entirely empty (`FeedPageFetcher.kt:42-50,92-101`) and is *correct*: k=3 with creators holding
   30/5/2 gifs legitimately ends after ~3 pages. **Verdict: cannot be confirmed here** — needs k and
   pool sizes (issue comment + the gate probe below).
5. **A real early end misclassified** — a per-creator HTTP 400 inside `List(k)` aborts the whole
   fill; the mediator maps `400 && page > 1` to `Success(end)` (`FeedMediator.kt:105-106`) and
   `walkFill` returns `null` on any 400 (`FeedPagingSource.kt:103-104`) ⇒ silent END, not an error.
   A renamed/deleted/banned favorited creator is the plausible trigger (404 surfaces as "Couldn't
   load"). **Verdict: UNPROVEN, plausible** — settled by a live probe of `userGifs` for a bogus
   username (record the status code).
6. **Ruled out**: `perCreator` rounding → a short fill keeps `nextPageKey`
   (`FeedPageFetcher.kt:66-72`) and Paging never ends on a short page; the API's `pages`/`total`
   (`dto/GifDtos.kt:95-100`) are read nowhere, so the server cannot end the walk either; 429 /
   `CircuitOpenException` surface as `LoadResult.Error`, never `end` (they can be *triggered* by 2).
7. **#59 revalidation yielding a short/duplicate-heavy page** — duplicates are dropped by the
   cross-page `seen` set, which can shrink a page but never nulls the key
   (`FeedPagingSource.kt:232-239`). Residual hole: when `fill` short-circuits on the exhaustion
   pre-check it writes nothing, so `walkFill` re-serves the *stale* row and its old `nextPageKey`
   (`:101-107`). **Verdict: real residual, self-limiting, not this stop.**

## Fix plan (smallest complete change)

**Step 1 — bound the per-fill fan-out (root cause 1+2).** `feature/feed/.../FeedPageFetcher.kt`

- New `internal const val FAVORITES_CREATORS_PER_FILL = 5` (one limiter burst) and a pure
  `internal fun favoritesPagePlan(creators, seed, page, pageSize): FavoritesPlan`
  (`usernames`, `innerPage`, `perCreator`, `roundPages`): `R = ceil(k/5)`; `round = (page-1)/R`;
  window offset `((page-1) % R) * 5` into the round's `favoritesLoopOrder` (`:233-242`, unchanged);
  `innerPage = round + 1`; `perCreator = ceil(pageSize / window.size)`.
- `fill` computes the plan from **one** `favorites()` read (today it reads twice, `:37` and `:131`)
  and uses `R` (not k) in the `loopOver` / `previousRotationExhausted` / `more` rules
  (`:37-50,66-72`); `fetch(Favorites)` uses the plan and keeps `interleaveChunks`.
- Properties kept: ≤ 5 requests per fill (≈ one burst, ≤1.7 s sustained); ~20 gifs per page;
  **k ≤ 5 byte-identical to today** (#54's 5-creator gate and every existing test use k ≤ 5); each
  `(creator, round)` pair still maps to exactly one page ⇒ no duplicates, no loss; dead-round cost
  drops from k² to ≈ k requests.
- Accepted trade: for k > 5 a screenful interleaves 5 of k creators (4 gifs each) instead of all k
  (1 each); the window rotates per page, so consecutive pages surface new creators.

**Step 2 — mapping generation in the cache identity.** `FeedSource.kt:93` `keyBase = "fav:v2"`;
`Entities.kt:94` `FAVORITES_PAGE_PREFIX = "fav:v:p"` (the LIKE sweeps every generation, so
refresh/favorites edits still clear old rows) + the DAO comment (`Daos.kt:63-68`). #59 was exactly
"mapping changed, keys didn't": without this, rows written by v0.7.2 inside the last 10 min still
end the walk at the old tail. Cost: key literals in 5 test files (8 assertions) —
`FavoritesInterleaveTest`, `FavoritesRefreshSurvivalTest`, `FeedMediatorFavoritesTest`,
`FavoritesRoundRobinExhaustionTest`, `FavoritesCacheInvalidationTest`. No other production change:
no schema change, no migration, no new endpoint, no WorkManager.

## Red-first acceptance tests (`:feature:feed`, JVM)

`FavoritesFanOutTest` (all fail before step 1):
1. `a favorites fill issues at most one sub-fetch per creator in its round window` — k=12 → 5 calls
   (today 12).
2. `every favorited creator is covered exactly once per round` — k=12 → windows 5/5/2 across pages
   1..3, then round 2 restarts at page 4 with `innerPage = 2`.
3. `no creator and inner-page pair is served by two pages` — 3 rounds over 60-gif pools: page-row
   id sets pairwise disjoint, every pool gif on exactly one row.
4. `k at or below the fan-out keeps the #54 mapping` — k=5 → one page contains all 5 creators,
   `innerPage = page`, `perCreator = 4` (pins that `FavoritesInterleaveTest` stays valid unmodified).
5. `the exhaustion window is the round length, not the creator count` — k=12, all pools dry at inner
   page 1: the walk ends after a 3-page dead round costing ≤ 12 requests (today 12 × 12).
6. `perCreator is ceil(pageSize / window size)` — k=12 page 3 (window 2) → 10.

`FavoritesMappingKeyTest` (fails before step 2):
7. `the favorites cache base carries the mapping generation`.
8. `favorites eviction sweeps every mapping generation`.

Existing favorites tests (`FavoritesInterleaveTest`, `FavoritesLoopReshuffleTest`,
`FavoritesRoundRobinExhaustionTest`, `FavoritesStaleLedgerTest` — all k ≤ 5) stay green apart from
the key literal.

## Device gate (after JVM green; one heavy op at a time)

Build with a temporary `FeedPageFetcher` probe (one `Log.i` per fill: `page, k, window, requests,
gifs, more`), stripped before merge as in every previous batch.

- Mobile, fresh profile (favorites are local ⇒ signed-out is enough): favorite **12** creators via
  quick sheets → open Favorites. Expect: page-1 probe ≤ 5 requests, first tiles < 2 s; six
  successive scroll loads each ≤ 5 requests with no gap > 2 s (today the same scroll logs 10–12
  requests per page); request cadence still ≤ 15/5 s (limiter invariant untouched).
  - scroll to the honest end: the end footer appears once, the dead round logs ≤ 3 pages ≤ 5
    requests each, no duplicate tile ids in the grid.
- TV (AVD `tv`): Favorites row → D-pad right past card 18 → focus reaches card 25+ within ~2 s and
  the row keeps growing to the real pool end.
- Regression eyeball: Trending / For You / Search paging unchanged (non-Favorites path untouched).
- If candidate 5 is still suspected: bogus-username live probe (`users/<bogus>/search`) — 400 ⇒
  that mapping needs a per-creator skip, 404 ⇒ nothing to do here.

## Non-goals

- No append/footer affordance in the UI (separate UX decision; fast appends make the 4 dp line
  enough).
- No use of the per-creator `pages`/`total` DTO fields (semantics never live-verified — no-guess).
- No pageSize / prefetchDistance / limiter retuning; the request *count* is the problem.
- No `evictStale` sweep or background prefetch (#59 decision stands); no change to the #36 contract
  beyond its window length, nor to the #5 rotation/refresh contract.

## Open questions and risks

1. **The reporter's k is unknown.** If it is ≤ 5, candidate 1 cannot be their cause and the answer
   is 4 or 5. Settled by: one issue comment asking how many creators are favorited, plus the gate
   probe at k=12 and k=40.
2. **Reduced exhaustion margin.** The window shrinks from k pages to `R = ceil(k/5)` (2 for k=10), so
   two transiently-empty pages can end the feed sooner than today. If the gate shows it, extend the
   rule to two consecutive empty rounds (≤ k extra requests — cheap after step 1); not built now.
3. **Pagination stability at the new `count`.** `userGifs(order="trending", count=perCreator)` is
   assumed stable across pages from the 2026-10-23 probe (36 gifs, `count=1..7`, zero overlap).
   Pre-merge check: one live probe of a 60+-gif creator at `count=4`, pages 1..3, expect disjoint
   slices.
4. **Per-creator 4xx still fails the whole fill** (candidate 5) — fixing it needs a product call
   (skip silently vs. surface an error), so it stays out of this change.
5. **`fav:v1` rows become unreachable garbage** after step 2 (`evictStale` still has no caller);
   swept by the broader eviction prefix on the next favorites edit/refresh.
