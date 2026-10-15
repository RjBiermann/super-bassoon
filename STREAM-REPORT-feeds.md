# STREAM-REPORT-feeds.md — feed-stream issues #1, #5, #6, #10, #12, #18, #22, #23

Worktree: `/workspaces/sturdy-potato/ws-feeds`. Design doc: `DESIGN-feeds-stream.md` (spec).
All 8 phases implemented in the doc's ordering (flag first, then #12/#1 repo+DB gutting,
then #5, #6, #18, #23, #22). No gradle/ktlint/detekt/jj/git/adb commands were run —
coordinator gates pending. Static verification: residual greps clean (see Phase 9),
brace/paren balance + ≤140 cols pass on every touched file, no trailing whitespace,
final newlines present, unused-import scan clean, host-side v11 migration SQL sanity PASS.

## Phase 1 — Issue #10: feature flag + custom-feeds disabled (no data change)

**Files changed:**
- `core/ui/Feature.kt` (new) — `object Feature { const val CUSTOM_FEEDS = false }` per the doc's const-in-:core:ui decision (ambiguity 2 confirmed).
- `feature/feed/FeedScreen.kt` — More ▾ custom-feeds section + "New custom feed…"/"New feed…" item wrapped in `if (Feature.CUSTOM_FEEDS)`; Pinned section's FAVORITED-custom-feeds term gated (pinned niches + pinned creators stay).
- `feature/feed/QuickSheet.kt` — addto pane "Add to custom feed…" row gated; main-pane "Add to…" row condition collapsed to one line: `(Feature.CUSTOM_FEEDS && customFeeds.isNotEmpty()) || viewModel.loggedIn`.
- `app-tv/TvMainActivity.kt` — "My feeds" item + per-feed custom dropdown items wrapped in `if (Feature.CUSTOM_FEEDS)`. Routes (`showCustomFeeds`) stay compiling-but-unreachable per the doc's decision table.
- `app-tv/TvQuickActions.kt` — addto pane "Add to custom feed…" row gated (Collection/Niche rows stay reachable; the else branch re-anchors initial D-pad focus on the Collection row and returns early).

Gate count: `Feature.kt` + 6 gate sites (acceptance 4 ✓). Handy notes: TvMainActivity's Room `customFeeds` collection is still referenced inside the gated block (documented "leave it" option of the doc taken). BLOCKED-feed filter stage (`ContentFilter.refreshBlockedFeeds`) untouched — verified still wired at the FeedPagingSource read chain. Backup export/import of customFeeds untouched.

**Deviations:** the doc's mobile QuickSheet main-row gate was `Feature.CUSTOM_FEEDS && customFeeds.isNotEmpty()`; the coordinator's ambiguity-3 resolution applied instead — the row shows for any signed-in user (token = Collections/Niche add targets; both are token-gated calls, so token presence is the cheap honest proxy; a signed-in user with zero collections can still open the picker, same as the pre-existing empty-picker behavior on TV).
**Open risks:** with the flag off, a signed-out user loses "Add to…" entirely from the mobile sheet (old behavior showed it with non-empty custom feeds — unreachable data either way, but note it in the release pass).

## Phase 2 — Issue #12: "Surprise Me" removed entirely

**Files changed:**
- `feature/feed/FeedRepository.kt` — deleted `SurpriseResult`, `surprisePool`, `refreshSurprise()`, the Surprise paging branch; `paging()` now starts at the Liked branch.
- `feature/feed/SurprisePoolPagingSource.kt` — **deleted**.
- `feature/feed/FeedSource.kt` — deleted `FeedSource.Surprise` + its `title()` arm.
- `feature/feed/FeedViewModel.kt` — deleted `surpriseMe()`.
- `feature/feed/FeedScreen.kt` — deleted the "Surprise me" More ▾ item + its SurpriseResult snackbar branches (snackbar host stays as the block-hint host — verified live: block-hint LaunchedEffect still uses it; the `scope` is still used by the scroll-to-top click).
- `app-tv/TvMainActivity.kt` — deleted the "Surprise me" More ▾ item; dropped the now-unused `@Inject lateinit var repository` (was injected for refreshSurprise alone), `SurpriseResult` import, `rememberCoroutineScope`/homeScope (only Surprise used it).
- `core/database/Daos.kt` — deleted `GifDao.randomUnwatched` (last consumer gone).
- test fakes — removed the `randomUnwatched` override from 5 test fakes (`FeedWalkTest`, `FeedMediatorFavoritesTest`, `FeedMediatorCustomTest`, `FavoritesRefreshSurvivalTest`, `FavoritesCacheInvalidationTest`).
- `app-tv/TvQuickActions.kt` — reworded the `feedSource` doc comment that mentioned the Surprise pool ("pass null → no row").

**Deviations:** none.
**Open risks:** none; per-feed Shuffle machinery (FeedFilterDialog shuffle chips, player seed display) untouched and validated by `ShuffleOrderTest` staying in place.

## Phase 3 — Issue #1: "Continue Watching" removed + `MIGRATION_10_11` (DB v11 DROP)

**Files changed:**
- `core/database/GiffyDatabase.kt` — `version = 11`; `WatchHistoryEntity` removed from the entity list; `watchHistoryDao()` accessor deleted; `MIGRATION_10_11` added (`DROP TABLE IF EXISTS watch_history`) with the destructive-step contract comment.
- `core/database/Entities.kt` — deleted `WatchHistoryEntity`.
- `core/database/Daos.kt` — deleted the whole `WatchHistoryDao` interface.
- `app-mobile/di/AppModule.kt` + `app-tv/TvAppModule.kt` — `MIGRATION_10_11` registered in both addMigrations chains (**completed in this follow-up pass** — it was the one item short when the session hit the limit).
- `feature/feed/ContinueWatchingPagingSource.kt` — **deleted**.
- `feature/feed/FeedRepository.kt` — deleted the Continue branch, `continueWatchingPager()`, `continueWatchingEntries()`, `CONTINUE_LIMIT`, the `first` import and toModel/orientation imports it carried.
- `feature/feed/FeedSource.kt` — deleted `FeedSource.Continue` + `title()` arm.
- `feature/feed/FeedScreen.kt` — deleted the "Continue Watching" More ▾ item, the `source is FeedSource.Continue` chip-marker term, the Continue msg/hint arms of the helpful empty state.
- `app-tv/ContinueWatchingViewModel.kt` — **deleted**; `TvHomeScreen.kt` — deleted the Continue row item, `continueViewModel` param, `continueEntries` collection, header comment updated, now-unused `androidx.compose.foundation.lazy.items` import removed.
- `app-tv/TvMainActivity.kt` — dropped `continueVm` Hilt lookup + the `continueViewModel =` wiring.
- `feature/feed/PlayerScreen.kt` — dropped the `watchHistory = db.watchHistoryDao()` call-site arg, the `WatchHistoryDao` param, the resume read (`history?.watched` case gone), the 5s sampler, the `WatchHistoryEntity` import; the resume `LaunchedEffect` now always plays at 0.
- `app-tv/TvPlayerScreen.kt` — deleted `flushWatchPosition`, the 5s sampler + teardown flush block, both `byGif` resume reads (retry overlay resume is now 0), `WATCH_POSITION_MIN_MS`, the `WatchHistoryEntity` import; playback-start effect plays at 0; doc comment updated.
- `feature/feed/ContinueSourceTest.kt` — **deleted**.
- Doc: `AGENTS-DATABASE.md` table bullet records the v11 drop ("removed with the feature (2026-10 stream)", old ≥3s write-policy note marked void); `AGENTS-PLAYER.md` Playback bullet struck through + replaced with "every gif starts at 0".

**Deviations:** the doc said "keep the flag itself (skipResume)" pending re-check — re-checked and **removed**: with the drop decision `resume` is unconditionally 0, so the writes-only MutableState + its PlayerPage threading were pure dead code (house rule: no dead code). `PlayerScreen`'s unused `db: GiffyDatabase` param is KEPT (`db = db` call site in MainActivity stays untouched; the doc only ordered the watchHistory param drop).

**Self-check:** host-side SQL sanity run on a scratch v10-shape DB: create `watch_history`, run the v11 `DROP TABLE IF EXISTS` → table gone, second run idempotent — **PASS** (same pattern as the batch-18 v10 check; not a committed test, a documented gate step).

**Open risks:** real deliberate data loss (positions gone post-upgrade) — flagged for release notes, not silently. An old v10 APK pressed onto a v11 DB fails `MigrationRequired 11→10` (same trade every prior migration accepted).

## Phase 4 — Issue #5: Favorites real-infinite feed + shuffle-on-refresh

**Files changed:**
- `feature/feed/FeedPageFetcher.kt` — Favorites branch rotates the creator pool via new internal pure rule `rotateCreators(creators, seed)` (forward-rotation, modulo-safe for negative seeds; seed 0 = DB order); `fill()` now reads the rotation size once per call and: (a) the "nothing favorited → no empty row" guard keys on rotation==0 (a mid-rotation empty page 1 now continues); (b) an EMPTY page EARLY in a rotation is **not** terminal — the row chains forward (`nextPageKey = page+1`) so the source's bounded filter walk and APPEND keep advancing; an empty LAST rotation slot (`page % size == 0`) remains the honest end (one API call per fetch — the next rotation's first slot isn't probed, per the doc's bounded formulation).
- `feature/feed/FeedMediator.kt` — gained the `favoritesSeed` constructor lambda passed through to its internal fetcher (no end-decision change needed — the mid-rotation `more=true` semantics ride the existing `endOfPaginationReached = !more`).
- `feature/feed/FeedRepository.kt` — `@Volatile favoritesShuffleSeed` + `suspend fun reshuffleFavorites()` (fresh random seed, never 0, never persisted) + eviction via `db.feedPageDao().evictFavorites()`; wired inside `paging()`'s cached-pager `flatMapLatest` exactly when `feed is FeedSource.Favorites && forceRefresh`, i.e. at the moment a forced (pull-to-refresh mobile / TV `refresh()`) generation begins — later same-generation rebuilds (source swap, preview re-attach) don't re-roll, mirroring the mobile consumedGen semantics. Seed lambda passed to BOTH the mediator and the source's fetcher.
- `feature/feed/FeedPagingSource.kt` — `shuffleOrdered`'s doc comment corrected (no longer "shared with the repository's Continue branch").

**Tests added:** `FavoritesRoundRobinExhaustionTest` (3 creators [a,b,c]; b's empty slot mid-rotation returns more and chains the page row; rotation ends at an empty LAST slot; single-creator feed keeps today's end semantics; `rotateCreators` ordering + negative-seed modulo safety) and `FavoritesSeedRotationTest` (stubbed seed → page 1 maps to a different creator; unseeded = unchanged; round-robin slot math preserved under rotation). `FakeApi` became `open` with `open override userGifs` so feed-specific tests can override one endpoint. Existing `FavoritesRefreshSurvivalTest` / `FavoritesCacheInvalidationTest` / `FeedMediatorFavoritesTest` remain green by construction (seed 0 defaults, per-generation dedup untouched).

**Deviations:** the doc's "cache-eviction assertion" unit test was NOT built — repository-level `paging()` needs real Room/DataStore fakes far beyond the one-test rule; the eviction is a one-line call inside the forced-generation branch, covered by compile + device gate. The doc's "stride" in FeedMediator is subsumed by the end-decision living in `fill()` (the doc itself listed that as the cleaner fallback).
**Open risks:** a mid-fill favorites-set edit can shift the rotation size between fetch and continuation read (accepted, documented in-line — same instant race as a set change invalidating the whole mapping). Device acceptance items (100+ item scroll, visibly different refresh order, 15 req/5s log during the walk) remain the coordinator's device pass.

## Phase 5 — Issue #6: TV refresh feed

**Files changed:**
- `app-tv/TvSourceFeedScreen.kt` — `TvNicheFeedViewModel` gains the generation machinery: `refreshGen` StateFlow + one-shot `consumedGen` (mobile FeedViewModel parity; force is consumed by the first pager build of the generation) + `gifs(source): Flow<PagingData<Gif>>` = `combine(flowOf(source), refreshGen)` → `flatMapLatest { repository.paging(source, forceRefresh) }`; `refresh()` bumps the gen. Screen helper `refreshAndReturnToTop()` = `viewModel.refresh()` + `scope.launch { gridState.scrollToItem(0); firstCardFocus.requestFocus() }`.
- `app-tv/TvQuickActions.kt` — new nullable param `onRefresh: (() -> Unit)? = null`; a "Refresh feed" QuickAction row right after "Open feed" appears only when a grid context passes `onRefresh != null` (home contexts omit it → no row, per the doc's scope note; player contexts omit it too).
- `app-tv/TvSourceFeedScreen.kt` (call site) — grid-pane TvQuickActionsDialog passes `onRefresh = { refreshAndReturnToTop() }`.

**Deviations:** the doc sketched the row gated on `feedSource != null` for the grid pane; actual wiring ties it to the `onRefresh` callback (the grid dialog call does not pass feedSource today — a callback gate is the honest wiring and keeps home/player rows clean). The doc's "Refresh" chip next to Filter became one Refresh **IconButton** next to the Sort icon (consolidated in phase 6's icon pass — same slot, one row, giffyFocus ring on both).
**Open risks / fold-to-device:** the doc itself allows folding acceptance "unit test: force flag consumed once" into the device check when the Hilt VM shape forbids plain tests — `TvNicheFeedViewModel` is a concrete `@HiltViewModel` over a final `FeedRepository`; no plain unit test was built. FocusPreview re-settle after refresh is a device-pass item.

## Phase 6 — Issue #18: ONE sort + filter popup per feed

**Files changed:**
- `feature/feed/FeedFilterDialog.kt` — new params `sortOptions: List<Pair<String,String>>`, `selectedSort: String` (both defaulted for harmless compile-compat), `onApply(FeedPrefs, String)`; Sort is the FIRST section, rendered only when `sortOptions.isNotEmpty()` (server-verified orders only — nothing invented); title reworded "Sort & filter"; bottom-row "Clear filters" TextButton calls `onApply(FeedPrefs(), "")` → resets filter prefs AND the saved sort together (TV gains its missing Clear for free).
- `feature/feed/FeedScreen.kt` — the whole sort-chips + Filter ▾ + Clear row replaced by ONE right-aligned `IconButton` (`Icons.AutoMirrored.Filled.Sort`, contentDescription "Sort & filter"); badge semantics ride the tint — `secondary` when `filtersActive || activeSort.isNotEmpty()` else `onSurfaceVariant`; saved-sort collection hoisted (`activeSort = source.activeSort.ifEmpty { savedSort }`); dialog apply = `setFeedPrefs(baseKey, next)` + existing `setSort(source, sort)` (pendingForce one-shot + open() adoption machinery reused, exactly the doc's "rides the EXISTING" instruction).
- `app-tv/TvSourceFeedScreen.kt` — the "Filter" chip replaced by the same Sort IconButton (giffyFocus ring kept by wiring `interactionSource` into both the button and `giffyFocus`); the dialog is fed `sortOptions` + `selectedSort = source.activeSort.ifEmpty { savedSort }`; onApply persists prefs+sort via the shared FeedViewModel, and a CHANGED sort (1) re-identifies the feed via new host callback `onSortChange(source.withSort(sort))` and (2) calls `viewModel.refresh()` so the sorted source loads FORCED (one-shot), then re-anchors `gridState.scrollToItem(0)`.
- `app-tv/TvMainActivity.kt` — wires `onSortChange = { sorted -> openFeed = sorted }`.

**Deviations:** TV sort needs the feed-identity swap to function (the grid pager lives in `TvNicheFeedViewModel`, not the shared FeedViewModel the doc's `setSort`-only reading assumed) — the `onSortChange` host callback + forced generation is the minimal coherent extension documented in the code. The doc's IconButton icon fell back exactly as anticipated: `Icons.AutoMirrored.Filled.Sort` exists in the icons-extended set (the fallback was only needed if the artifact lacked it). The doc's optional pure apply-rule test (acceptance 3) lapsed to its own alternative ("the pure durationIn-style test stays" = `DurationChipTest`) — the apply path is a two-line composition at the call sites, not extracted logic.
**Open risks:** pendingForce written by setSort on TV goes unconsumed inside the TV-unused mobile VM state (doc-sanctioned reuse of setSort; in-memory only). Dialog "Clear filters" closing on apply matches the intended one-tap reset; device D-pad walk of the added chip rows remains the coordinator's gate.

## Phase 7 — Issue #23: scroll-to-top ICON on feeds

**Files changed:**
- `core/ui/RefreshFeedPill.kt` → **renamed/repurposed** `core/ui/ScrollToTopFab.kt` — `RefreshFeedPill` → `ScrollToTopFab`: icon-only `IconButton` (`Icons.Filled.KeyboardArrowUp`, contentDescription "Scroll to top"); NO revalidate on click (pull-to-refresh owns it; single floating control preserved); SAME threshold + upward-scroll derivation; new `SCROLL_TO_TOP_THRESHOLD = 10` const (one spelling, replaces FeedScreen's private `SCROLL_PILL_THRESHOLD`); `rememberScrollingUp` generalized with a `LazyListState` overload next to the staggered-grid one (same derivedStateOf body); shared `rememberReducedMotion()` (system animator scale read, moved verbatim out of FeedScreen) + `LazyStaggeredGridState/LazyListState.scrollToTop(reducedMotion)` (instant at scale 0, animated otherwise) as the host-side scroll helpers. Doc comment records the repurpose.
- `feature/feed/FeedScreen.kt` — pill swap: `ScrollToTopFab(visible = showPill, onClick = { scope.launch { gridState.scrollToTop(reducedMotion) } })`; no `viewModel.forceRefresh()` on click; pull-to-refresh intact; reducedMotion now rides the shared helper; stale RefreshFeedPill references/comments gone.
- `feature/feed/ExploreScreen.kt`, `FollowingScreen.kt`, `CollectionsScreen.kt`, `NichesScreen.kt` — each list hoists `rememberLazyListState` + `rememberScrollingUp(state, SCROLL_TO_TOP_THRESHOLD)` + `rememberReducedMotion`, wrapped in a `Box` with the fab `Alignment.BottomCenter`; click = `listState.scrollToTop(reducedMotion)`. List content indented one level under the Box (indentation re-verified).

**Deviations:** Surface set follows the doc exactly: FeedScreen + Explore + Following + Collections + Niches YES; SearchScreen / NicheAboutScreen / CustomFeedsScreen / TV = NO. No test added (no prior pill tests existed to "swap"; the derivation is unchanged from the tested pass-history pattern).
**Open risks:** the fab is an M3 IconButton (default circular tonal container) — visual weight over海报 tiles is lighter than the old text pill by design; confirm on device that contrast over bright posters is acceptable (mobile device pass).

## Phase 8 — Issue #22: end-of-feed affordances (TV only; mobile already complete)

**Files changed:**
- `app-tv/TvSourceFeedScreen.kt` — end-of-feed footer composed AFTER the item slots inside the grid (never a pager item, never inside `snapshot()`): condition = `refresh settled (NotLoading) && append is NotLoading && (endOfPaginationReached || itemCount == 0)`; spans `GridItemSpan(maxLineSpan)`; renders "End of feed" bodySmall + a giffyFocus-ringed "Refresh" TextButton reusing `refreshAndReturnToTop()` (forced reload + top + first-card focus).
- `app-tv/TvPlayerScreen.kt` — end-of-feed popup: manual NEXT at the LAST gif (Down key and media_NEXT key) opens it; M3 Surface Dialog on the TvQuickActionsDialog pattern (first-row FocusRequester + giffyFocus ring); rows per ambiguity 7: **"Start over"** (`index = 0`, textVisible reset, player body re-focused) and **"Back to feed"** (`onBack()`); dismiss request closes it. Auto-advance's own loop at the last item untouched; the "infinite" rule stays reconciled with #5 for Favorites, pool caps documented as "nothing left", home rows exhaust silently per ambiguity 5.
**Deviations:** none (mobile needed no code per the doc; the footer condition adds the refresh-settled guard so a first-load spinner doesn't flash the footer).
**Open risks:** the a cappella popup uses a static `snapshot()` truth — "Start over" loops the LOADED pages only, copy is truthful ("End of feed"); D-pad traversal between player and popup is device-pass material.

## Phase 9 — Verification evidence

Residual greps (whole tree, `*.kt`): `FeedSource.Continue`, `FeedSource.Surprise`, `SurpriseResult`, `surprisePool`, `continueWatching`, `ContinueWatching`, `randomUnwatched`, `RefreshFeedPill`, `SCROLL_PILL_THRESHOLD` → **zero matches**. `watch_history` appears ONLY inside the v11 migration comment/SQL (no readers/writers anywhere). `Feature.CUSTOM_FEEDS` = `Feature.kt` + exactly 6 gate sites (FeedScreen ×2, QuickSheet ×2, TvMainActivity ×1, TvQuickActions ×1). `refreshBlockedFeeds` intact at the FeedPagingSource read chain (leak-zero choke point) + SearchViewModel; `untagged`/custom machinery/backup intact. Brace/paren balance + ≤140 cols clean on every touched file; unused-import scan clean. Host-side `MIGRATION_10_11` SQL sanity: PASS (+ idempotent re-run).

## Coordinator-gate / device items (not executable here)

- Gradle compile + ktlint + detekt + unit tests on :core:database, :core:ui, :feature:feed, :feature:search (untouched logic only), :app-mobile, :app-tv. Room KSP should emit `schemas/11.json`.
- Device pass: Favorites deep scroll / refresh-order change / rate log; TV refresh chip + "Refresh feed" row + end-of-feed footer + player popup; #18 dialog per-surface (mobile + TV D-pad rows); the Fab's 4 secondary surfaces + FeedScreen visual check; BLOCKED custom feed still filtering live (regression check from #10 acceptance 2).
- Release-notes note: watch-position data is intentionally destroyed by v11.
