# DESIGN-65-66 — creator feed empty by default (#65) + swipe player stops paging (#66)

Status: **design only, not landed.** Static pass in lane `ws-creator` (jj workspace off
`super-bassoon`). No source edits, no builds, no emulator, no network. Everything below is
`file:line` evidence from this tree plus bytecode facts read out of the resolved dependency
(`androidx.paging:paging-common-android:3.5.1` in the gradle cache) — the two probes that
would turn a hypothesis into a fact are listed in §11 and were **not** run here.

## Objective

- **#65** — a creator feed (`/users/perkyandpink`) renders **nothing** by default; switching
  the sort to *Oldest* renders that creator's content. Expected: the creator feed shows the
  creator's gifs with no sort touched.
- **#66** — while playing a **creator** feed in the swipe player, after some swipes the player
  stops producing new videos even though the feed still has content. Expected: keep loading
  until the pool is genuinely exhausted.

Both reports are labelled `mobile, tv`; §12 explains why the TV half of #66 is a different
(and larger) defect and is a non-goal here.

## 1. How a creator feed is built and paged

Request shape — `core/network/.../GifsApi.kt:51-56`:

```kotlin
@GET("v2/users/{username}/search")
suspend fun userGifs(username, count = 40, page = 1, order: String = "trending")
```

(The code comment records the live BadOrder-accepted set as of 2026-09-30:
`trending, oldest, latest, top, top7, top28`.)

Source identity — `feature/feed/.../FeedSource.kt:107-114`:

- `keyBase = "user:<u>"` and, when a sort is set, `"user:<u>:sort=<sort>"` (`:111`) — the
  cache base.
- `baseKey = "user:<u>"` (`:112`) — the DataStore prefs identity (sort/filter persistence).
- `ttlMs = TTL_SEARCH` = 1 h (`:113`, constant at `:230`).
- `sort` defaults to `""` (`:109`), and `""` means *“the surface's default order”*
  (`activeSort` doc, `FeedSource.kt:180`).

What `""` is turned into on the wire — `feature/feed/.../FeedPageFetcher.kt:139-145`:

```kotlin
is FeedSource.Creator ->
    api.userGifs(username = feed.username, count = pageSize, page = page,
                 order = feed.sort.ifEmpty { "trending" })   // ← :144
```

So **a creator feed with no explicit sort is always fetched with `order=trending`.** The two
other creator-page call sites inherit the interface default `order = "trending"`:
`FeedPageFetcher.kt:161` (a `creator:` ref inside a Custom feed) and `:200` (every
Favorites sub-fetch). The niche feed already has the opposite treatment — `order =
feed.sort.ifEmpty { null }` (`FeedPageFetcher.kt:137`) with the comment “Live drift 2026-10:
`trending` is now a BadOrder — the server's default ordering rides on an omitted param”
(`GifsApi.kt:80-86`, fetcher side `FeedPageFetcher.kt:137`).

Paging run — `feature/feed/.../FeedRepository.kt:295-345` (`cachedPager`):
`PagingConfig(pageSize = PAGE_SIZE = 20, prefetchDistance = 10, enablePlaceholders = false)`
(`:302`, page size `:356`), `initialKey = 1`, `FeedMediator` as `RemoteMediator`, and a
`FeedPagingSource` whose factory also hands over a `FeedPageFetcher` for self-fills (`:315-340`).

Per page: `FeedMediator` routes REFRESH (TTL, `:62-92`) / APPEND (page key from the cache,
`:45-59`) to `FeedPageFetcher.fill(page)` (`FeedPageFetcher.kt:36-93`), which issues the API
call, upserts gifs, writes the page row and decides “more”:

```kotlin
val more = gifs.isNotEmpty() || when { plan == null || windowPages == 0 -> false; ... }
// FeedPageFetcher.kt:78-87 — for a creator feed: more = gifs.isNotEmpty()
```

Read path: `FeedPagingSource.load` walks pages, filtering per page
(`FeedPagingSource.kt:202-221`, walk at `:119-165`, per-page read chain at `:226-300`).
Empty-after-filter pages are hopped, bounded by `MAX_WALK_HOPS = 8` (`:52`). Two dedup layers
run before the filter: cross-page `seen` from cached rows (`:234-239`) and per-generation
`sessionSeen` (`:200`, `:246`).

Sort UI: `FeedSource.sortOptions()` for a creator is
`Latest / Oldest / Top This Week / Top This Month` (`FeedSource.kt:218-225`) — **there is no
“Trending” chip**, so the default (`order=trending`) is invisible and unselectable; every chip
leaves it. Chip apply: `FeedScreen.kt:346-359` → `viewModel.setSort` (`:352`) + a route push of
`source.withSort(sort)` (`:357`); TV equivalent `TvSourceFeedScreen.kt:436-446`.

Sort adoption on open — `FeedViewModel.kt:157-172`: a saved sort is adopted only when the
route's source is *unsorted* (`sourceIsUnsorted`, `:174-181`). So an explicit chip choice
sticks; the default stays `""`.

## 2. How the swipe player pages

`PlayerScreen` reads the same shared pager as the grid:
`viewModel.gifs.collectAsLazyPagingItems()` (`PlayerScreen.kt:172`) off a single
Activity-scoped `FeedViewModel` (`MainActivity.kt:177, 212-213`), and `gifs` is
`cachedIn(viewModelScope)` (`FeedViewModel.kt:128-140`) — player and grid see one list.

Pager sizing: `rememberPagerState(initialPage = startIndex) { items.itemCount.coerceAtLeast(1) }`
(`PlayerScreen.kt:176-180`). `items.itemCount` is backed by a `MutableState`
(`LazyPagingItems.itemSnapshotList`, bytecode-verified), so the pager *does* grow with appends.

The auto-advance machine is two places:

1. **Ended listener** (`PlayerScreen.kt:271-305`): on `STATE_ENDED` with auto-swipe on, if
   `pagerState.currentPage + 1 < items.itemCount` it scrolls; otherwise `pendingAdvance = true`
   and **`items.retry()`** (`:302-303`, comment: “ask paging for the next page”).
2. **`LaunchedEffect(pendingAdvance.value, items.itemCount, items.loadState.append)`**
   (`:313-350`): `next < itemCount` → advance; `append is NotLoading && endOfPaginationReached`
   → end-of-pool (shuffle jump or loop last video, `:328-346`); otherwise
   **`else -> items.retry()`** (`:348`).

Adjacent-item *media* preload is separate (`preloadNeighbors`, `:352-359`) and does not touch
paging.

## 3. Paging 3.5.1 facts this design leans on (bytecode-verified, cached artifact)

Read from `paging-common-android/3.5.1` (`PageFetcherSnapshot`, `HintHandler`,
`HintHandlerKt`, `PageFetcher`, `LazyPagingItems`) — these are the load-bearing internals:

1. **A load continues only on a *new* viewport hint.** `HintHandler.processHint` replaces the
   append hint only when `shouldPrioritizeOver(hint, previous, APPEND)`
   (`HintHandlerKt.shouldPrioritizeOver`), which returns **false when the page offsets are
   unchanged and `presentedItemsBeyondAnchor` did not shrink**. Re-reading the same index
   therefore produces no load.
2. **An append that inserts zero items changes neither offsets nor the anchor** → no new hint
   → **the append chain is dead**. Nothing in `PageFetcherSnapshot.doLoad` inspects the new
   page's size (no `isEmpty` on `LoadResult.Page.getData()`), so an empty page is not
   retried or skipped by the library.
3. **`retry()` only re-attempts a *failed* load.** `PageFetcherSnapshot$pageEventFlow$1$4$1`
   resolves the retry target as `refresh/prepend/append is LoadState.Error`, else `null` and
   nothing runs (`remoteMediatorConnection.retryFailed(state)` is called first, which is a
   no-op without a failed mediator load). So `items.retry()` on a `NotLoading` append is a
   **no-op** — the player's “ask paging for the next page” call cannot start a page load.

Consequence — **the grid and the player diverge on the same pager**: the grid produces a new
access hint on every scroll step, so a scroll *after* an empty append re-hints and the walk
creeps forward through empty pages. The player sits still on one page, so the *first* empty
append is terminal for the session. That asymmetry is exactly the #66 report shape (“player
stops, feed still has content”).

## 4. Issue #65 — candidates and verdict

| # | Candidate | Evidence | Verdict |
|---|---|---|---|
| C1 | **`order=trending` is the creator feed's default and returns an empty first page for this creator** (200 + `gifs: []`), so `fill` reports `more=false`, the page-1 row is empty, and the grid renders the empty state | `FeedPageFetcher.kt:144` (the default *is* trending); `:78-87` (empty ⇒ end); `FeedMediator.kt:62-92` REFRESH fetches because a cached empty page is never fresh; `FeedScreen.kt:479-506` renders an empty/filter empty state | **PRIMARY, high confidence in the code path.** The server-side half needs probe P1 (§11) — it distinguishes “200 empty” from “400 BadOrder drift”. Either way the same change fixes it. |
| C2 | `trending` now 400s BadOrder on this endpoint (drift like niches) → page-1 400 stays a mediator `Error` (`FeedMediator.kt:99-112`) and the grid shows the error card, which a reporter may read as “nothing shows” | `FeedMediator.kt:99-112`; niche precedent `GifsApi.kt:80-86` | **Plausible alternative to C1**, not additive — settled by the same probe P1 (status code). Same fix. |
| C3 | Client filter wiped the page (orientation / duration / verified-only / promoted / blocked creator) | `FeedPagingSource.kt:226-300` | **Rejected** — the reporter's *same* feed shows content under `order=oldest` in the same session with unchanged prefs; a client filter would empty both sorts. |
| C4 | The documented `pendingForce` / `keyBase` mismatch (`FeedViewModel.kt:535` records `source.baseKey`, `:134` reads `feed.keyBase`) causes the empty default view | `FeedViewModel.kt:128-140, 529-538` | **Reachable but NOT the cause.** A sort change produces a brand-new `keyBase` (`:sort=` suffix, `FeedSource.kt:111`) whose page row was never written, so the mediator's REFRESH fetches regardless of the lost one-shot bypass. The mismatch only loses the bypass when returning to an already-cached base within the 1 h TTL (stale-while-revalidate then applies). Still worth the one-line fix — see F4. |
| C5 | Sort adoption / route re-assert re-opens the unsorted source and blanks the feed | `FeedViewModel.kt:157-172`, `MainActivity.kt:314-316` | **Rejected** — adoption re-applies the persisted sort, `open()` is a same-value no-op. |

**#65 root cause (confidence: high for the client path, high for the server fact once P1 runs):**
the creator surface's default order is `order=trending`, and `trending` on
`v2/users/{username}/search` is a *subset* order (only the creator's trending-window items) —
for a low-engagement creator it can be empty (and drifting to BadOrder is a live possibility,
per the niche precedent). Every chip in the creator sort menu leaves trending, which is why
*Oldest* works and the reporter never saw the default succeed. The UI hides it: `""` selects no
chip (`FeedScreen.kt:313, 346-347`).

## 5. Issue #66 — candidates and verdict

| # | Candidate | Evidence | Verdict |
|---|---|---|---|
| P1 | **An append that inserts zero items kills the append chain permanently** (§3.1-3.2), and the player's only recovery call `items.retry()` is a no-op for a non-error load (§3.3) | `PlayerScreen.kt:302-303, 348`; paging internals §3 | **PROVEN mechanism.** This is why the player, unlike the grid, never recovers. |
| P2 | **What produces the zero-item append on a creator feed: `order=trending`.** Its page windows are not exhaustive and the documented upstream overlap (“search/trending pagination overlaps — page n re-lists gifs from page n-1”, AGENTS-NETWORK.md) means page n can be largely/entirely deduped (`FeedPagingSource.kt:234-239`), the walk hops up to 8 pages (`:52`), and when all hops read empty the walk returns **empty data with an open `nextKey`** (`:119-165`) — the exact stall signature | `FeedPageFetcher.kt:144`; `FeedPagingSource.kt:52, 119-165, 202-221`; AGENTS-NETWORK overlap note | **PRIMARY trigger, medium-high confidence** for a creator feed (same root as #65). Needs probe P5/P6 (per-page id overlap under `trending` vs `latest`) plus one device log (§10) to confirm. |
| P3 | The creator's trending pool is simply shallow: pages run out after a few while the creator's catalogue is large (`fill` → `more=false` ⇒ honest end) | `FeedPageFetcher.kt:78-87`; DESIGN-59.md:49 (past-pool = **200 empty**, no 400 wall) | **Plausible alternative to P2**, same fix family (F1). Sits behind the same probe (P1 vs P2 pages 2-3). |
| P4 | Pool cap / 400-wall / rate-limit / breaker stops appends | `FeedMediator.kt:84-112`; DESIGN-59 live probe found no 400 wall on this endpoint | **Rejected** — would show as an error card or a breaker cooldown indicator, and the user reports neither. |
| P5 | A page-count cap / window limit / in-flight suppression in the app | grep: no cap exists; `PagingConfig` has `maxSize` unbounded, `prefetchDistance = 10` | **Rejected** — the only app-side cap is `MAX_WALK_HOPS` (P2). |
| P6 | `items.itemCount` not observable in the pager's `pageCount` lambda, so the pager never grows | `LazyPagingItems.getItemCount()` reads `itemSnapshotList` `MutableState` (bytecode-verified) | **Rejected.** |

**#66 root cause (mechanism: proven; creator-specific trigger: medium-high):** the player stops
because an append reads back **zero visible items while the pool continues** (P2/P3, the
creator's `order=trending` pages) and, once that happens, paging has no hint left to ask again
and `items.retry()` cannot ask either. Nothing in the player re-drives a load; the video just
loops the last item or sits ended.

## 6. Shared-cause verdict

**One root cause for the creator-feed half of both issues, one independent robustness gap:**

- Shared: **the creator page fetch defaults to `order=trending`** (`FeedPageFetcher.kt:144`
  and the `GifsApi.userGifs` default at `GifsApi.kt:57`). Empty/partial trending windows empty
  page 1 (#65) and empty deep pages (#66). Fixing the order fixes both reports' creator cases.
- Not shared: #66 additionally exposes that **an empty append is unrecoverable in this pager**
  (§3). That is generic (any feed with a strict client filter hits it), so it gets its own small
  fix (F2/F3) — but the *reported* creator case is closed by the order fix alone.

## 7. Chosen approach

Five small, separable changes. F1 alone closes both reports; F2/F3 remove the silent-stall
class; F4/F5 are the two UI/hygiene consequences of F1 that would otherwise be inconsistent.

**F1 — creator pages stop sending `order=trending` (the fix for #65 and the creator case of #66).**

- `GifsApi.userGifs(order: String? = null)` — mirror `nicheGifs(order: String? = null)`
  (`GifsApi.kt:87-97`) and its “server default rides on an omitted param” comment. Omitted
  param ⇒ all three creator call sites stop sending trending in one move.
- `FeedPageFetcher.kt:144` → `order = feed.sort.ifEmpty { null }` (Creator branch).
- If probe P2 shows the omitted-order default is *also* empty for the reported creator, send
  `"latest"` instead: one constant `internal const val CREATOR_DEFAULT_ORDER = "latest"` next
  to the fetcher, used by `:144`, `:161` and `:200`. **The probe decides which of these two
  spellings ships; nothing else in the design changes.**
- Cache-base bump (the #59/#62 lesson, “mapping changed, keys didn't”): the page→content
  mapping for `user:` and `fav:` pages changes, so `FeedSource.Creator.keyBase` becomes
  `"user:v2:<u>"` (`FeedSource.kt:111`) and `FeedSource.Favorites.keyBase` becomes `"fav:v4"`
  (`:97`) while both `baseKey`s stay put (`:112`, `:100`) so no user pref/sort/filter resets.
  Old rows age out by TTL/`evictBase`; no migration, no schema change.
- `FeedSource.defaultSort` (new computed property next to `activeSort`, `FeedSource.kt:180-188`):
  `""` everywhere except `Creator`, which reports `CREATOR_DEFAULT_ORDER`/`"latest"`. The two
  shells use it as the chip fallback:
  `FeedScreen.kt:313` and `TvSourceFeedScreen.kt:437` become
  `source.activeSort.ifEmpty { savedSort.ifEmpty { source.defaultSort } }` — otherwise the
  default view highlights *no* chip while serving the default order (an existing hole that F1
  makes visible on the creator surface).

**F2 — the read path never returns “empty page with an open chain” (the silent-stall signature).**

In `FeedPagingSource.kt`:

- `walkFill` (`:75-108`) reports *how* it resolved the page: a small
  `internal data class WalkFill(val entity: FeedPageEntity?, val fromNetwork: Boolean)`
  (`pageDao.page(...)` hit ⇒ `false`, a `fetcher.fill(...)` ⇒ `true`). Both callers of
  `walkFill` (`:211` here, and the two `pageFor` lambdas in `FeedWalkTest` /
  `FavoritesStaleLedgerTest`) move to the new return type.
- `walkFeedPages` (`:119-165`) spends two budgets instead of one: cache-served empty hops
  (`MAX_WALK_CACHED_HOPS = 64` — Room reads only, no requests) and network fills
  (`MAX_WALK_FILLS = 8` — today's `MAX_WALK_HOPS` value, so the worst-case request depth of one
  load is unchanged and the ≤15 req/5 s invariant is untouched).
- **Terminal rule:** when either budget is exhausted with the chain still open, return
  `WalkedPage(gifs = emptyList(), nextKey = null)` — an honest end — instead of today's
  `emptyList() + nextKey` (which the pager can never continue). Consequence: an unreachable-
  by-paging state becomes the feed's existing end-of-feed state (§22 footer), and because
  cached hops are now free, a refresh (or any new generation) walks past the already-cached
  empty pages and spends its fills deeper — progress is recoverable instead of permanent.

**F3 — the player's pool-end decision becomes explicit and testable.**

`PlayerScreen.kt`:

- Extract the `LaunchedEffect` body's decision into a pure function next to the composition
  helpers (same file, precedent: `playerControlsSemantics`, `PlayerScreen.kt:138-149`):
  `internal enum class PoolEndAction { ADVANCE, WAIT, RETRY, END }` +
  `internal fun poolEndAction(next: Int, itemCount: Int, append: LoadState): PoolEndAction` —
  `next < itemCount ⇒ ADVANCE`; `append is Loading ⇒ WAIT`; `append is Error ⇒ RETRY`;
  `append NotLoading end=true ⇒ END`; `NotLoading end=false ⇒ RETRY` (documented as the
  defensive arm that F2 makes unreachable; it keeps `items.retry()` — which *does* retry a
  failed mediator load — as the one call).
- The ended listener (`:296-305`) keeps setting `pendingAdvance` and drops its own
  `items.retry()`; the effect owns the retry (one call site, one comment).
- Fix the misleading comment at `:301-302`: `retry()` re-asks the *failed* load only; it does
  not fetch a page.

**F4 — the documented `pendingForce` / `keyBase` mismatch (one line + one test).**

`FeedViewModel.setSort` (`:534-535`) records `source.baseKey`; the read (`:134-135`) tests
`feed.keyBase`. Record the post-sort key instead: `pendingForce.value + source.withSort(sort).keyBase`.
Not a #65 cause (see C4) but it is in the file this batch touches, it is listed in AGENTS.md
“Open items”, and with F1 the creator surface is exactly where sorts change.

**F5 — no other change.** `FeedMediator`’s APPEND/400/TTL rules, the Favorites round-robin,
`ContentFilter`, `PagingConfig`, `MAX_WALK_HOPS`’ request ceiling and the rate limiter stay as
they are.

### Rejected alternatives

- **Fallback on empty page 1 (fetch the default sort; if empty, retry once with `latest`).**
  Rejected: AGENTS.md “fallbacks are spec'd, not scheduled”, and it doubles the page-1 request
  cost for every genuinely empty creator while leaving the default order unproven.
- **Keep `trending` and let the user pick a sort.** Rejected: it is the reported bug.
- **Player-side “force an append” hack** (re-reading an early index and then the last index to
  defeat `shouldPrioritizeOver`, §3.1). Rejected: depends on undocumented hint bookkeeping,
  costs a `get()` per attempt, and cannot be covered by a meaningful test.
- **Raise `MAX_WALK_HOPS` / make the walk unbounded.** Rejected: multiplies requests per load
  and can burst past the 15 req/5 s budget; F2 keeps the ceiling identical.
- **Return `LoadResult.Error` when the walk budget is exhausted** so the UI retry works.
  Rejected: it is a lie on a healthy network and would loop the error card.
- **Give the player its own pager / replay appends by restarting the generation.** Rejected:
  a generation restart emits `resetGenerationPagingData()` (`FeedViewModel.kt:117-126`) → the
  pager clamps to page 0 → the user is thrown back to the top of the feed.
- **Do nothing about #66 and rely on F1.** Rejected: leaves the proven silent-stall class in
  place for any filtered feed (a strict orientation/duration chip triggers exactly the same
  freeze).

## 8. Exact files / functions to change

| File | Function | Change |
|---|---|---|
| `core/network/.../GifsApi.kt` | `userGifs` (`:51-56`, order default `:55`) | `order: String? = null` (or the F1 `latest` spelling) |
| `feature/feed/.../FeedPageFetcher.kt` | `fetch` Creator branch (`:139-145`, order `:144`), Custom creator ref (`:161`), `favoritesPage` (`:200`) | pass the creator default order explicitly; add `CREATOR_DEFAULT_ORDER` if the probe picks `latest` |
| `feature/feed/.../FeedSource.kt` | `Creator` (`:107-114`), `Favorites` (`:95-102`) | `keyBase` → `user:v2:…` / `fav:v4`; new `defaultSort` next to `activeSort` (`:180-188`) |
| `feature/feed/.../FeedPagingSource.kt` | `walkFill` (`:75-108`), `walkFeedPages` (`:119-165`), `load` (`:202-221`), constants (`:52`) | `WalkFill(fromNetwork)`, two budgets (`MAX_WALK_FILLS = 8`, `MAX_WALK_CACHED_HOPS = 64`), honest terminal `nextKey = null` |
| `feature/feed/.../PlayerScreen.kt` | ended listener (`:271-305`), pool-end effect (`:313-350`) | `PoolEndAction` + `poolEndAction()` pure fn; single `items.retry()` call site; comment fix |
| `feature/feed/.../FeedViewModel.kt` | `setSort` (`:529-538`) | F4: record `withSort(sort).keyBase` |
| `feature/feed/.../FeedScreen.kt`, `app-tv/.../TvSourceFeedScreen.kt` | sort chip fallback (`:313` / `:437`) | `…ifEmpty { savedSort.ifEmpty { source.defaultSort } }` |
| tests/fixtures (must follow the signature changes) | `feature/feed/src/test/.../FakeApi.kt:62-70`, `FavoritesFanOutTest.kt:84,103`, `FeedWalkTest.kt:75` (its `pageFor` helper), `FavoritesStaleLedgerTest.kt:141`, `FavoritesMappingKeyTest.kt` | nullable `order`, new `pageFor` return type, `fav:v4` / `user:v2` expectations |

## 9. Acceptance conditions

1. A creator feed opened with no sort never issues `order=trending` (asserted on the fake API’s
   captured arguments), and renders the creator’s gifs (device gate, §10).
2. Creator/favorites cache bases carry the mapping generation; prefs identities do not.
3. A feed page load can end in exactly two states: items, or `nextKey == null`. No load returns
   `empty + open nextKey` (unit test on `walkFeedPages`).
4. `poolEndAction` maps every `LoadState` to one of the four actions; the retry path is reached
   only for `Error`/`NotLoading(end=false)`.
5. `check` green on `:core:network`, `:feature:feed`, `:app-mobile`, `:app-tv`
   (compile + ktlint + detekt + JVM tests). No Room schema change, no migration.
6. Rate-limit invariant test untouched and green; per-load request ceiling unchanged (8 fills).
7. Device gates in §10 pass; 0 FATAL in logcat.

## 10. TDD test plan

Red first, JVM only (`:feature:feed`, `:core:network` where noted). Each item names the test
that fails before the change.

1. **`CreatorOrderTest`** (`:feature:feed`, fake API capturing `order`): “a creator feed with no
   sort fetches with the creator default order, never trending” — fails today
   (`FakeApi.userGifs` must grow an `orderArgs` list). Also pins: explicit sort still wins;
   a `creator:` Custom ref and each Favorites sub-fetch use the same default.
2. **`CreatorMappingKeyTest`** (mirrors `FavoritesMappingKeyTest`’s shape): `keyBase` carries
   `user:v2`, `baseKey` stays `user:<u>`, `sort` suffix still appends after the generation
   token; same for `fav:v4`.
3. **`FeedWalkTest` / new `WalkTerminationTest`**: (a) a walk whose empty pages were all served
   *from cache* keeps walking past 8 of them and finds the first non-empty page; (b) a walk
   that exhausts its *fills* while the chain is open returns `nextKey == null` (today: returns
   the advanced key — the red); (c) a walk over a chain ending at a row with `nextKey == null`
   is unchanged (regression guard on the existing cases).
4. **`PoolEndActionTest`** (`:feature:feed`, `androidx.paging.LoadState` is JVM-safe):
   `next < itemCount ⇒ ADVANCE` for every append state; `Loading ⇒ WAIT`; `Error ⇒ RETRY`;
   `NotLoading(end=true) ⇒ END`; `NotLoading(end=false) ⇒ RETRY`.
5. **`SortForceKeyTest`** (F4): the key `setSort` records is the one the gifs flow consumes —
   for a sortable source the recorded value equals `source.withSort(sort).keyBase` (red today:
   it records `baseKey`).
6. **Existing suites that must stay green:** `FavoritesFanOutTest`, `FavoritesMappingKeyTest`,
   `FavoritesStaleLedgerTest`, `FeedMediatorPoolCapTest`, `FeedWalkTest`, `PlayerClusterReplaceNavTest`.

Device gates (after JVM green, one heavy op at a time, debroid/MCP path, UI dumps not
screenshots):

- **G1 (#65)** mobile: open `/users/perkyandpink` (or the probe’s confirmed empty-trending
  creator) → the grid renders items on first open, zero sort interaction; the sort dialog shows
  the default chip selected; *Oldest* still works. Repeat on TV (`TvSourceFeedScreen`).
- **G2 (#66)** mobile: same creator feed → tap a gif → auto-swipe through ≥ 3 pages beyond the
  first screenful; logcat shows continuous page fills and no repeated empty-page loads;
  `adb shell run-as … sqlite3 gifs.db "select pageKey,length(gifIds) from feed_pages where pageKey like 'user:v2:%'"` grows monotonically.
- **G3 (#66 robustness)** mobile: creator feed with a strict filter (orientation that the
  creator mostly violates) → the grid shows the end-of-feed state instead of freezing, and a
  pull-to-refresh advances further.
- Evidence for P2/P3 (§5) is taken during G2: screenshot/log of the last loaded page index and
  the `feed_pages` row count at the moment the player stops, before and after F1.

## 11. Probe needed (must be run centrally — not run in this lane)

Identity: one anonymous token (`GET {apiBase}v2/auth/temporary`) fetched **with the exact
`BrowserIdentity` header set** from `core/network/.../BrowserIdentity.kt:73-84`
(`User-Agent`, `Sec-Ch-Ua*`, `Accept`, `Accept-Language`, `Origin`/`Referer` = site root,
`X-Session-Id`), and every later call carrying the *same* UA (the token is UA-bound).
`apiBase` = `Hosts.apiBase` (`https://api.redgifs.com/`). Pace ≤15 requests per rolling 5 s
(≥1 s apart here); delete nothing, record status + `gifs.length` + `pages`/`total` if present.
Token to `/tmp` 0600, never printed or committed.

```bash
# 0) does this creator actually have a catalogue? (1 request, optional but strong evidence)
curl -sS -D- -o /tmp/p0.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v1/users/perkyandpink"     # -> .gifs (catalogue size), .verified

# P1) the app's current default for #65 — status tells us 200-empty vs 400 BadOrder
curl -sS -D- -o /tmp/p1.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=1&order=trending"

# P2) the server's own default (the F1 spelling if non-empty)
curl -sS -D- -o /tmp/p2.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=1"

# P3) the fallback spelling
curl -sS -D- -o /tmp/p3.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=1&order=latest"

# P4) the reporter's working sort (control)
curl -sS -D- -o /tmp/p4.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=1&order=oldest"

# P5) #66 trigger check: page-2 overlap under the app's current default
curl -sS -o /tmp/p5.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=2&order=trending"
#    then: python3 -c "import json;a=json.load(open('/tmp/p1.json'));b=json.load(open('/tmp/p5.json'));\
#    ia={g['id'] for g in a.get('gifs',[])};ib={g['id'] for g in b.get('gifs',[])};print(len(ia&ib),len(ib))"

# P6) control: same overlap under the candidate default order
curl -sS -o /tmp/p6.json -H "Authorization: Bearer $TOKEN" <IDENTITY_HEADERS> \
  "https://api.redgifs.com/v2/users/perkyandpink/search?count=20&page=2&order=latest"
#    same overlap computation against /tmp/p3.json
```

Decision rules:

- P1 = 200 with `gifs: []` and P2 or P3 non-empty ⇒ F1 with the omitted param (P2) or `latest`
  (P3) — #65 confirmed, C1.
- P1 = 400 ⇒ C2 (BadOrder drift); F1 with the same spelling choice; also add the drift note to
  AGENTS-NETWORK.md.
- P1 non-empty ⇒ #65 is **not** an order problem on this account; stop and re-open the
  investigation with P0 as the anchor (catalogue may be empty/private).
- P5 overlap ≈ P6 overlap ≈ 0 ⇒ P2 (§5) is wrong, P3 (shallow trending pool) is the #66
  trigger; F1 still fixes it, F2/F3 stay as the robustness fix.

## 12. Non-goals

- **TV half of #66.** `TvPlayerScreen` takes a **fixed `List<Gif>` snapshot** captured at tap
  time (`onOpenGif(snapshot(gifs), index)`, `TvSourceFeedScreen.kt:525`,
  `TvHomeScreen.kt:568`) and walks that list (`TvPlayerScreen.kt:84-86, 114-140`); it cannot
  page by construction, so it honestly shows its end-of-pool dialog (#22). Making TV page in the
  player needs the player to own the pager (`Flow<PagingData>` + depth request) — a separate
  design, not a bug fix. Nothing in this batch regresses TV.
- No new endpoint, param, or DTO field; no changes to `FeedMediator`’s APPEND/400/TTL rules, the
  Favorites round-robin mapping, `ContentFilter`, `PagingConfig`, the limiter, or the breaker.
- No Room schema change, no migration, no `evictStale` sweep (no WorkManager).
- No “default order” chip added to the menus beyond marking the existing default (no new
  *Trending* chip on the creator surface).
- No change to search/niche/trending feed orders (`search` keeps `trending`, verified).
- No persistence of any new state; no speculative fallback branches.

## 13. Risks / open questions

1. **Omitting `order` may not equal a non-empty default.** Mitigated by the P2/P3 branch in
   §11; the code change is a constant either way.
2. **Creator/favorites page mapping changes ⇒ one refetch pass** of `user:`/`fav:` rows after
   the base bump (cheap: TTL already 1 h / 10 min; the Favorites walk revalidates stale rows).
   Reviewer decision: `fav:v4` is technically required by the #59 lesson (content per page
   changes); dropping it means stale Favorites content until TTL/refresh.
3. **F2 trades a silent freeze for a visible end** in the residual case (≥8 consecutive
   *freshly fetched* pages that read empty — reachable only with a strict client filter or a
   badly re-listing upstream page). The feed then shows its end-of-feed state, and a refresh
   walks further for free because cached hops no longer spend the budget. If the reviewer
   prefers “never end early”, the alternative is a much larger fill budget at the cost of the
   15 req/5 s starve — rejected here.
4. **Default-chip highlight** (F1 + `defaultSort`) makes the creator surface show “Latest”
   selected on first open; choosing *Latest* from that state re-identifies the feed
   (`sort=latest`, its own cache base) with identical content. Accepted noise; the alternative
   (normalising `latest` → `""`) would make `sort` a lossy identity.
5. **#66’s trigger (P2 vs P3) is not fully proven without the probe + one device log.** Both
   roads are fixed by F1; F2/F3 are the robustness net. If G2 still stalls after F1, capture the
   `feed_pages` dump at the stall point before changing anything else.
6. **F2 changes `walkFill`’s return type**, so it touches two existing test files
   (`FeedWalkTest`, `FavoritesStaleLedgerTest`). Mechanical, but it must land together.
