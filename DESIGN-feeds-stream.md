# DESIGN-feeds-stream.md — feed-stream issues #1, #12, #10, #5, #6, #22, #23, #18

Design phase only. No code changes. Working copy: repo `super-bassoon`, workspace
`/workspaces/sturdy-potato/ws-feeds`, DB `giffy.db` @ v10, all line numbers from the
working copy at design time (2026-10-04) — re-resolve during implementation.

House-rule constraints honored throughout (AGENTS.md / AGENTS-NETWORK.md /
AGENTS-CONTENT-FILTER.md / AGENTS-APP.md): no invented endpoints (`AGENTS-NETWORK.md`
endpoint inventory is the authority); ContentFilter stays the single leak-zero choke
point; rate budget ≤15 req/5s (batch-21 retune); Room changes only via explicit
`Migration` (never `fallbackToDestructiveMigration`); dark-only, viewer-only, no ads /
analytics; ktlint + detekt + unit tests green per touched module; every non-trivial
change ships one minimal self-check.

Shared context (true for every issue below):

- Feed→UI pipeline: `FeedRepository.paging` (`feature/feed/FeedRepository.kt:156-256`)
  branches per `FeedSource` family: Surprise → `SurprisePoolPagingSource`
  (`SurprisePoolPagingSource.kt:1-30` over `surprisePool`, `FeedRepository.kt:91-124`);
  Liked → `LikedNetworkPagingSource` (network-live, no Room, `LikedNetworkPagingSource.kt:1-99`);
  For You → `ForYouNetworkPagingSource` (same, `ForYouNetworkPagingSource.kt:1-111`);
  Continue → `ContinueWatchingPagingSource` (Room-only, `FeedRepository.kt:181-253`);
  everything else → `cachedPager` = Room `FeedPagingSource` + `FeedMediator` +
  `FeedPageFetcher` (`FeedRepository.kt:255-294`, `FeedPagingSource.kt:163-262`,
  `FeedMediator.kt:32-89`, `FeedPageFetcher.kt:16-132`).
- Filter walk: `walkFeedPages` ≤ `MAX_WALK_HOPS = 8` hops; pool-cap HTTP 400 = graceful
  end-of-pool inside the walk (`FeedPagingSource.kt:62-124`, `walkFill` 68-79).
- Generation semantics: `FeedViewModel.refreshGen` + `forceRefresh()` restart the pager
  with one-shot TTL bypass (`FeedViewModel.kt:128-136`); the reset-emit
  (`resetGenerationPagingData`, `FeedViewModel.kt:137-147`) clears the stale-grid flash;
  per-generation session dedup lives on the source instance (`FeedPagingSource.kt:141-149`).

---

## Issue #1 — REMOVE "Continue Watching" entirely (both apps)

### Current behavior

| Piece | Where |
|---|---|
| `FeedSource.Continue` | `feature/feed/FeedSource.kt:100-109` (`keyBase "continue:v1"`, title `FeedSource.kt:137`) |
| Mobile feed branch | `FeedRepository.kt:181-183` → `continueWatchingPager()` 210-227 → `continueWatchingEntries()` 230-253 (`watchHistoryDao().continueWatching(limit=20)` at :238, `CONTINUE_LIMIT` :326) |
| Mobile pager | `ContinueWatchingPagingSource.kt:1-54` (Room-only, invalidation-tracker observer over `watch_history`/`gifs`/pref tables) |
| TV row | `ContinueWatchingViewModel.kt:1-63` (Room flow): row in `TvHomeScreen.kt:233-257` (always-composed item, opens `player = listOf(entry.gif) to 0`) |
| TV quick-menu source marker | `TvHomeScreen.kt:249` passes `FeedSource.Continue` to `openActions` |
| Mobile More ▾ entry | `FeedScreen.kt:244-248` (`viewModel.open(FeedSource.Continue)`) + selected-marker `FeedScreen.kt:216` + empty states `FeedScreen.kt:580-604` |
| watch_history writers | mobile `PlayerScreen.kt:482-520` (`PlayerPage` resume read :488-497 + 5s sampler upsert :500-520); TV `TvPlayerScreen.kt:112-126` (`flushWatchPosition`, 3s floor `WATCH_POSITION_MIN_MS` :550), teardown flush `TvPlayerScreen.kt:464-480`, resume reads :350, :461 |
| watch_history readers | `WatchHistoryDao` (`core/database/Daos.kt:139-151`): `byGif` (resume), `recent`, `continueWatching`; `GifDao.randomUnwatched` `Daos.kt:13-19` (`WHERE id NOT IN (SELECT gifId FROM watch_history)` — the Surprise pool's exclusion set) |
| Schema | `WatchHistoryEntity` `core/database/Entities.kt:60-68`; entity + DAO in `GiffyDatabase.kt:20,42` (v10); **not** in `ContentPrefsBackup` (backup = prefs + custom feeds only, `ContentPrefsBackup.kt:50-107`) |

Other watch_history consumers: none (grep over non-test sources returns only the files
above). Tests touching it: `ContinueSourceTest.kt` (source identity only — no Room test
depends on the table).

### Root cause / gap

User decision: the feature is unwanted. It is also the only surface with a
feed-prefs footprint nobody can reach once removed. The real design question is what
happens to the `watch_history` table, because TWO other features ride on it:

1. **Resume positions** in both players (`TV/mobile` reads above) — the issue removes
   writes AND resume, so (1) goes away with the feature.
2. **Surprise's unwatched exclusion** (`Daos.kt:13-19`) — Issue #12 removes Surprise
   entirely, so the exclusion's consumer disappears too.

### Watch_history keep-or-drop — DECISION, recommendation

**Recommendation: DROP the table via `MIGRATION_10_11` (DB v11).**

Justification: after #1 and #12 land, `watch_history` has **zero readers and zero
writers** — not even the backup reads it (`ContentPrefsBackup` excludes it), and the
"Surprise unwatched" consumer is removed the same stream. A starved table is a dead
schema surface that still appears in `exportSchema`, the invalidation-tracker
registers (`ContinueWatchingPagingSource` observer is deleted anyway), and every later
migration reasoning; dropping it is the honest contract step. Rollback path: if resume
is ever wanted back, that is a NEW v12schema feature (entity + writers re-added) —
nothing about the v11 drop blocks it; the "preserve existing data" concern applies to
prefs (kept in backup tables), and watch positions are precisely the data the user is
deleting. This is a destructive step and is explicitly authorized by the issue itself
("watch-history position writes/resume" in the removal list).

If the parent overrules and prefers *starve* (keep table, delete all readers/writers):
cost = `WatchHistoryEntity` + `WatchHistoryDao` stay, no migration, Surprise's
`randomUnwatched` still needs its consumer removed (#12) — the table then never
changes again (writes are gone). Simpler diff, but leaves dead schema forever. The
rest of this doc assumes DROP (v11); the starve variant's deltas are called out under
"changed files".

### Proposed minimal change

New/changed (DB version 10 → 11):

1. `core/database/GiffyDatabase.kt`: remove `WatchHistoryEntity` from the entity list,
   bump `version = 11`; delete `watchHistoryDao()` accessor; add

   ```kotlin
   val MIGRATION_10_11 = object : Migration(10, 11) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("DROP TABLE IF EXISTS `watch_history`")
       }
   }
   ```

2. `core/database/Entities.kt`: delete `WatchHistoryEntity`.
3. `core/database/Daos.kt`: delete `WatchHistoryDao` (whole interface);
   `GifDao.randomUnwatched` loses its consumer with #12 — delete the query (see #12).
4. Register `MIGRATION_10_11` in BOTH app modules: `app-mobile/di/AppModule.kt:80-88`
   and `app-tv/TvAppModule.kt:83-91`.
5. `feature/feed`: delete `ContinueWatchingPagingSource.kt`; delete the Continue
   branch in `FeedRepository.paging` (:181-183), `continueWatchingPager()` (:201-227),
   `continueWatchingEntries()` (:230-253), `CONTINUE_LIMIT` (:326); delete
   `FeedSource.Continue` + its `title()` arm + `keyBase`/`baseKey` machinery;
   `shuffleOrdered`'s doc comment ("Shared with the repository's Continue branch",
   `FeedPagingSource.kt:36`) is corrected.
6. `FeedScreen.kt`: delete the More ▾ "Continue Watching" item (:244-248), the
   `source is FeedSource.Continue` chip-selection term (:216), and the Continue arms
   in the helpful empty state (:582, :590, :600).
7. `app-tv`: delete `ContinueWatchingViewModel.kt`; `TvHomeScreen.kt` — delete the
   Continue row item (:233-257) + the `continueViewModel` param and its wiring at
   `TvMainActivity.kt:275-302` (the `continueVm` Hilt lookup) + `ContinueSourceTest.kt`.
8. Position writes/resume (both players), per the issue:
   - `PlayerScreen.kt`: drop `watchHistory` param (:350, :438), the resume read in
     `LaunchedEffect(gif.id, active/dataSaver)` (:488-497 → `resumeMs = 0`), the 5s
     sampler (:499-520).
   - `TvPlayerScreen.kt`: delete `flushWatchPosition` (:112-126), the teardown-flush
     sampler (:464-480), resume reads (:350, :461); `skipResume` (auto-start-at-0 for
     auto-advance) loses its companion and simplifies to "always start at 0"
     (`skipResume` stays only because `history?.watched == true -> 0` disappears —
     re-check during implementation; keep the flag itself, it also serves a distinct
     purpose in the mobile swipe player for auto-advance).
9. Tests: delete `ContinueSourceTest.kt`; add one focused self-check — a host-side SQL
   sanity run (same pattern as the batch-18 v10 migration check) asserting
   `PRAGMA table_info(watch_history)` fails post-migration and v10 rows are simply gone
   (documented data loss), plus the existing gates re-run.

Behavior retained explicitly: **auto-advance/skip/swipe unchanged**; the watch-history
3s-floor write policy note in AGENTS-DATABASE.md is marked "removed with the feature
(2026-10 stream)".

### DB migration specifics (migration-skill ordered: expand → migrate → verify → contract)

- Single atomic destructive step (`DROP TABLE`), no data copied → no expand phase needed;
  rollback path = none for positions (accepted, see decision), full runtime rollback =
  previous APK keeps writing to the same DB (Room in-place migration; an old APK after
  upgrading to v11 will fail `MigrationRequired 11→10` — SAME trade every prior
  v-migration accepted; downgrade makes no promises today, unchanged by this).
- Mixed-version safety: an upgrading install executes v11 immediately at open; no
  background code touches the table between versions (no WorkManager in this app).
- Retry/partial-failure: Room transactions make the single-statement migration
  idempotent (`DROP TABLE IF EXISTS`).

### Acceptance criteria

1. Both apps compile with ktlint + detekt + unit tests green (:feature:feed, :app-tv,
   :app-mobile, :core:database).
2. Mobile: More ▾ has no "Continue Watching"; TV: no row between Liked/Following rows
   and the list end; `grep -R "Continue Watching\|FeedSource.Continue"` feature-wide is
   clean.
3. Player: starting a gif always plays from 0; no logcat writes to `watch_history`; the
   v11 migration installs and opens on an upgraded v10 install (MigrationTest-style
   host/side check or device `pm clear`-free upgrade run).
4. Filter/prefs behavior of every other feed is unchanged (all filter walks and
   ContentFilter paths untouched).

### Risk / notes

- Data loss is real and deliberate (positions gone after upgrade). Flagged in the
  release notes, not silently.
- The starve-variant costs nothing in migration risk but keeps a dead table; if the
  parent picks starve, skip items 1-4 and the table stays.

---

## Issue #12 — REMOVE "Surprise Me" entirely

### Current behavior

- Pool + result enum: `FeedRepository.kt:23` (`SurpriseResult`), pool `MutableStateFlow`
  :91-96, `refreshSurprise()` :99-124 (reads `GifDao.randomUnwatched(PAGE_SIZE*2)`,
  applies ContentFilter + verified-only + orientation).
- Pager branch: `FeedRepository.paging` :135-140 → `SurprisePoolPagingSource`
  (`SurprisePoolPagingSource.kt:1-30`).
- `FeedSource.Surprise` (`FeedSource.kt:86-92`, title :136, OPEN entry nothing else).
- VM: `FeedViewModel.surpriseMe()` :442-450 (refresh + `open(FeedSource.Surprise)`).
- Mobile entry: `FeedScreen.kt:224-243` ("Surprise me" item + `SurpriseResult`
  snackbar copy-branching EMPTY/FILTERED_EMPTY).
- TV entry: `TvMainActivity.kt:329-338` ("Surprise me" item → `repository.refreshSurprise()`
  → `openFeed = FeedSource.Surprise`).
- `FeedSource.Surprise` is also referenced as a comment-only case in
  `TvQuickActions.kt:88` doc ("Surprise pool pass null").
- `GifDao.randomUnwatched` (`Daos.kt:13-19`) — its ONLY consumer is `refreshSurprise`.

### Root cause / gap

User decision: unwanted. The shuffle machinery it popularized (per-feed
`shuffleSeed`, `shuffleOrdered`, the QuickSheet seed display) is a SEPARATE user-facing
feature (FeedFilterDialog "Shuffle On/On/Reshuffle", `FeedFilterDialog.kt:66-85` +
mobile player shuffle seed display `PlayerScreen.kt:139-145`) — it stays.

### Proposed minimal change

1. `FeedRepository.kt`: delete `SurpriseResult` (:23), `surprisePool` (:91-96),
   `refreshSurprise()` (:99-124), the Surprise paging branch (:135-140);
   `paging()`'s first branch collapses to the Liked branch.
2. Delete `SurprisePoolPagingSource.kt`.
3. `FeedSource.kt`: delete `FeedSource.Surprise` + its `title()` arm.
4. `FeedViewModel.kt`: delete `surpriseMe()` (:442-450).
5. `FeedScreen.kt`: delete the Surprise item + snackbar branch (:224-243); the
   `scope`/`snackbarHostState` pair it was the only async user of inside the dropdown
   stays (block-hint snackbar at :122-135 keeps both).
6. `TvMainActivity.kt`: delete the Surprise item (:329-338) — `repository` in TvMainActivity
   was injected for this alone → drop the injection if unused after the edit.
7. `core/database/Daos.kt`: delete `randomUnwatched` (last consumer gone; note: with
   issue #1's drop decision the subquery's table goes too — if the parent picked the
   starve variant, `randomUnwatched` still dies here because Surprise died).
8. `TvQuickActions.kt:88` doc comment: reword (mentions the removed pool).
9. Tests: delete nothing (no Surprise tests exist — the removed paths are wired through
   `surpriseMe`, device only). Add/adjust none; the removal is covered by compile +
   existing feed tests.

### Acceptance criteria

1. compile + ktlint + detekt green on :feature:feed, :app-mobile, :app-tv (+:core:network
   untouched, :core:database has the #1 v11 change co-located).
2. No "Surprise me" in mobile More ▾ or TV More ▾; opening a feed never lands a
   `surprise:v1` key (grep clean feature-wide).
3. Per-feed Shuffle pref (`FeedFilterDialog`) + QuickSheet seed display still work
   (ShuffleOrderTest still passes).

### Risk / notes

- Small: `FeedScreen`'s snackbar host construction must not become dead code — it is
  the block-hint host; verify before deleting imports.
- The "everything watched" behavior class disappears with the exclusion; fine — caller
  of the feature is gone.

---

## Issue #10 — Feature flag + disable custom feeds for now

### Current behavior (every entry point enumerated)

| # | Entry point | Where | Type |
|---|---|---|---|
| 1 | Mobile More ▾ "Custom feeds" section header + per-feed open items | `FeedScreen.kt:295-313` | UI listing |
| 2 | Mobile More ▾ "New custom feed…" / "New feed…" item | `FeedScreen.kt:313-320` | route trigger |
| 3 | Mobile More ▾ Pinned section's FAVORITED custom feeds | `FeedScreen.kt:321-327` (`favoritedFeeds.filter { state == "FAVORITED" }` → `FeedSource.Custom`) | UI listing |
| 4 | Mobile More ▾ selected-marker (`source is FeedSource.Custom`) | `FeedScreen.kt:218` | defensive |
| 5 | Mobile route `showCustomFeeds` (+ BackHandler arm + `onOpenCustomFeeds` wiring) | `MainActivity.kt:87,99,105,192-205,243` | route |
| 6 | TV route `showCustomFeeds` | `TvMainActivity.kt:105,127,137,249-262` | route |
| 7 | TV More ▾ "My feeds" | `TvMainActivity.kt:340-345` | route trigger |
| 8 | TV More ▾ per-feed custom dropdown items | `TvMainActivity.kt:353-362` | UI listing |
| 9 | TV quick-actions: Add-to pane "Add to custom feed…" row | `TvQuickActions.kt:150` ; picker `TvAddToFeedDialog` :419-467 | write path |
| 10 | TV quick-actions main pane "Add to…" row (pane entry, also hosts collection/niche rows) | `TvQuickActions.kt:297` | pane entry |
| 11 | Mobile player overflow (QuickSheet): "Add to…" main row (currently gated `customFeeds.isNotEmpty()`) | `QuickSheet.kt:159-161` | pane entry |
| 12 | Mobile QuickSheet Add-to pane "Add to custom feed…" + `AddToCustomFeedDialog` | `QuickSheet.kt:169-175, 186-193, 263-300` | write path |
| 13 | `FeedSource.Custom` runtime: fetcher round-robin branch | `FeedPageFetcher.kt:100-129` | dead-but-loadable |
| 14 | `FeedPagingSource.untagged()` strict-tags read filter | `FeedPagingSource.kt:28-44` | read filter |
| 15 | ContentFilter stage 2: BLOCKED custom feeds → global blocks | `ContentFilter.refreshBlockedFeeds(db.customFeedDao())` (consumers `FeedPagingSource.kt:205`, `FeedRepository.kt:241-242`, TV `ContinueWatchingViewModel.kt:44`) | **filter mechanism** |
| 16 | Backup export/import of `customFeeds` | `ContentPrefsBackup.kt:53,64,81-88,99-110` | data preservation |
| 17 | Tile/empty-state "Add to custom feed…" copy | `FeedScreen.kt:599` | hint copy |
| 18 | TvSourceFeedScreen addableFeedRef + strict-tags args | `TvSourceFeedScreen.kt:170-183,188` | dialog wiring |

Note: the FeedsViewModel's `customFeeds`/`favoritedFeeds`/`addToCustomFeed`
(`FeedViewModel.kt:274-277, 295-310, 491-497`) are all shared-state heaps — they stay.

### Root cause / gap

No flag exists; the feature is the builder + the merged feed-group management (DB v10).
Disabling means hiding EVERY surface where a user can create/open/edit a custom feed,
while keeping the below-the-UI machinery intact so `custom_feeds` data survives
(especially the BLOCKED-feed filter stage) and a re-enable is a one-line change.

### Flag placement — DECISION, recommendation

**Recommendation: one `const val` in `:core:ui`** — new file
`core/ui/src/main/kotlin/com/rjbiermann/giffyviewer/core/ui/Feature.kt`:

```kotlin
/** Product feature gates: one place each, no config plumbing (rule: no config
 *  for fixed values). */
object Feature {
    /** #10: custom-feed feature (builder + management UI) is disabled for now;
     *  DB rows and BLOCKED-feed filtering keep working underneath. */
    const val CUSTOM_FEEDS = false
}
```

Justification: all gate sites are already `:core:ui` consumers
(`feature/feed`, `app-mobile`, `app-tv` import `core.ui.*` throughout) — zero new module
edges. `:core:model` avoids a UI word in the data module but adds no benefit; a
BuildConfig flag would be config-for-fixed-value and is the anti-pattern the house rules
name. ktlint/detekt: dead-code warnings are not produced for `const val false ||`
branches in the Kotlin compiler at the expressions used here (build-time checked branch
elimination keeps code compiling).

### Decisions per entry point: HIDE vs REMAIN-COMPILING

| Point | Decision | Mechanism |
|---|---|---|
| 1, 3, 8 | **HIDE** | `if (Feature.CUSTOM_FEEDS)` around the dropdown section + each custom item |
| 2, 7 | **HIDE** | same conditional on the menu items |
| 9 | **HIDE the "Add to custom feed…" row** (Collection+niche rows stay, i.e. the pane stays reachable) | `if (Feature.CUSTOM_FEEDS)` on the row |
| 11, 12 | **HIDE** the custom row + custom defs from the picker; the "Add to…" main row's pre-existing `customFeeds.isNotEmpty()` gate becomes `Feature.CUSTOM_FEEDS && customFeeds.isNotEmpty()` (Collections/Niche adds live only under this row today — on mobile, HIDEing it for a collection-user with zero custom feeds would REMOVE a reachable path; document this as an intentional no-op for the flag=off era because with the flag off there are no NEW custom feeds and existing ones become unreachable from UI, so the pane entry's purpose is void; the parent should confirm, see ambiguities) | condition change |
| 5, 6 | **REMAIN COMPILING, unreachable** | routes stay; only entry points 2/7 disappear. No dead-code removal — re-enabling is flag=true. |
| 4, 17 | REMAIN (defensive/copy) — empty-state copy mentions custom feeds only on a Custom feed the user can no longer reach | no gate |
| 10 | REMAIN — the pane entry also hosts "Add to a Collection…" and "Add to a Niche…" (batch-22 TV parity), so removing the row would break reachability | no gate |
| 13, 14 | REMAIN — the source stays in the sealed interface so cached pages never crash |
| 15 | **REMAIN — MUST keep working.** BLOCKED custom feeds are the ContentFilter macro-filter; a block created pre-flag must never silently stop filtering. Verified by existing ContentFilter paths; no gate |
| 16 | REMAIN working (data preservation across the flag; backup format untouched) |
| 18 | REMAIN (typeof tests only fire on openable sources) |

`FeedSource.title()`/`sortOptions()`/mediator: untouched. UntaggedOnlyTest,
FeedMediatorCustomTest, GifFeedRefsTest keep passing (the machinery is untouched, only
the UI is disabled).

### Acceptance criteria

1. Flag-off build (the default): mobile More ▾ shows no custom-feed group, no
   "New custom feed…"/"New feed…" line, no FAVORITED custom feeds in Pinned; TV More ▾
   has no "My feeds" and no per-feed custom items; TV Add-to pane lacks the custom row;
   mobile QuickSheet picker never lists custom feeds.
2. BLOCKED custom feeds still filter tiles live (device: a pre-existing BLOCKED feed's
   tags still exclude rows) — regression-safety check in the device pass.
3. Backup export/import still carries customFeeds bytes (round-trip test exists).
4. One-const grep: `grep -R "CUSTOM_FEEDS"` returns only `Feature.kt` + the 6 gate sites.

### Risk / notes

- The `state` column's FAVORITED role is unreachable from UI while the flag is off —
  acceptable for a temporary disable; re-enable decides whether FAVORITED pinning
  returns with it.
- TV's `TvMainActivity` customFeeds Room collection (`:114-116`) becomes an observed
  but unused state with the flag off — leave it, or short-circuit to `emptyList()`
  under the flag (cheap; propose the short-circuit to keep recomposition work down).

---

## Issue #5 — Favorites: real infinite feed + shuffle on refresh

### Current behavior

- `FeedSource.Favorites` = `fav:v1`, TTL 10min (`FeedSource.kt:55-61`).
- Round-robin fetch: `FeedPageFetcher.kt:64-82` — page n →
  `creators[(page-1) % n]` at inner page `(page-1)/n + 1`, `creators` from
  `db.contentPrefsDao().favoriteCreators()` **at fetch time** (DB order, unshuffled);
  empty set → `GifsPageDto()` (degenerate page, guarded by the mediator’s
  `favorites().isEmpty()` check `FeedMediator.kt:72-77` and the paging source’s
  batch-18 empty-page guard).
- Pagination end semantics: `fill()` writes `nextPageKey = null` when the fetched page
  is empty (`FeedPageFetcher.kt:39-46`); mediator APPEND runs `runPage(page)` →
  `endOfPaginationReached = !more` (`FeedMediator.kt:84-88`). A creator whose pool ends
  returns “empty inner page” MID-SEQUENCE — but the round-robin means an empty inner
  page for ANY creator at position (n-1) mod n ends the whole feed even if another
  creator still had inner pages (the linear page sequence stops advancing; per-creator
  exhaustion is only visible at that slot). Result: the feed ends the first time any
  slot is empty; “infinite until nothing left to paginate” is not guaranteed today.
- Content order: server order per creator (no `order` param passed for Favorites —
  `FeedPageFetcher.kt:73`), so no shuffle; the per-feed `shuffleSeed` pref can shuffle
  the DISPLAY order client-side (`FeedPagingSource.kt:252-253` `shuffleOrdered`), but
  the USER didn't ask for it on every refresh. Refresh currently lands exactly the same
  pages (deterministic round-robin) — the batch-19 regression test
  `FavoritesRefreshSurvivalTest` pins this (“Favorites re-lists the same ids → all
  deduped to empty” — the old static-dedup bug the per-generation dedup fixed).

### Root cause / gap

1. **Not actually infinite**: a single empty inner page anywhere in the sequence ends
   pagination, even when other creators still have pages.
2. **No refresh variation**: refresh simply re-lands the same deterministic
   round-robin (same creators, same order) — the shuffle-on-refresh ask is unimplemented.

### Proposed minimal change

**A. Infinite until every creator is exhausted.** Change the end condition, in
`FeedPageFetcher` + `FeedMediator`:

- `FeedPageFetcher.fetch(Favorites)` additionally carries the creator pool size and
  detects slot exhaustion: if the mapped inner page came back empty AND the same empty
  condition holds for the NEXT slot too… simplest honest formulation with one API call
  per fetcher call: treat the empty page as terminal ONLY when
  `(page % creators.size == 0)` — i.e. the last slot of a full rotation came up empty;
  otherwise return `more = false` for that SLOT but let the mediator stride forward to
  the next rotation's page instead of ending.
- Concretely: `FeedMediator.APPEND` for `FeedSource.Favorites` doesn't trust one
  `!more`: when `!more && !isEndOfRotation(page)` it calls `fill(page + 1)` bounded by
  the existing rate budget (each hop is one request; the walk budget/rate-limit already
  bounds the caller; document that a Favorites advance may consume up to N fetches per
  page slot — reuse `MAX_WALK_HOPS` as a per-APPEND dividend to stay inside the 15/5s
  budget).
- Alternative (cleaner but bigger): fix `fill()` to NOT WRITE a terminal page row for
  Favorites until true end — kept as the fallback if the stride approach trips on the
  cache's `nextPageKey = null` pinning.
- Cache semantics: page rows keep their existing `pageKey/nextPageKey` format — the
  stride only changes WHICH page is fetched next, not the row shape; no migration.

**B. Shuffle on refresh (one volatile seed + cache eviction).** Per the
favorites-cache-eviction lesson (`FeedRepository.evictFavoritesCache`,
`FeedRepository.kt:65-79` + `FeedViewModel.toggleFavoriteCreator`), the mapping is only
valid for the rotation it was built against. Minimal coherent design:

- `FeedRepository` gains `@Volatile private var favoritesShuffleSeed: Long` (0 =
  unseeded) + `fun reshuffleFavorites()` — assigns a fresh seed (best: a random long;
  alternative: `+1` per call for testability) AND evicts the favorites page cache (the
  same call the toggler uses; `FeedPageDao.evictFavorites()`).
- `FeedPageFetcher` (Favorites branch only) rotates `creators` by the seed before the
  modulo mapping: `val rotated = creators.drop((seed % size).toInt()) +
  creators.take((seed % size).toInt())` — rotation preserves the inner-page math for
  pagination wrapper, so the changed ORDER accounts for new content while the
  row-key/base shapes stay the same.
- Wire `reshuffleFavorites()` from BOTH pull-to-refresh and the TV/“Refresh feed” pill
  equivalents for Favorites only, i.e. `FeedViewModel.forceRefresh()` (mobile
  `FeedViewModel.kt:135-137` + `FeedRepository` refreshed path): simplest spot is
  INSIDE `FeedRepository`'s REFRESH branch when `feed is FeedSource.Favorites && force` —
  the seeding is active at the exact moment the REFRESH generation begins (mediator
  load REFRESH with `force`).
- Display-order shuffle stays user-controlled (`shuffleSeed` pref): when the user has
  a seed set, the rotated feed order is per-seed re-hashed by `shuffleOrdered`
  (`FeedPagingSource.kt`), which composes fine (rotation changes page contents; the
  seed orders each page).

Per-feed default sorting for Favorites: none (no server order; the round-robin is the
order). Documented explicitly under #18's validity table.

### Acceptance criteria

1. Unit test (new, feature:feed): with creators [A, B] where B's inner page 3 is empty
   but A has it, a Favorites APPEND continues to page 4 (A slot) instead of ending;
   with BOTH slots empty at the same rotation boundary the feed ends
   (`FavoritesRoundRobinExhaustionTest`).
2. Unit test: two consecutive forced REFRESH generations land DIFFERENT page-1 content
   order (rotation test with a stubbed seed + a cache-eviction assertion).
3. Device: Favorites feed scrolls past 100+ items (a favorited creator with many gifs);
   pull-to-refresh produces visibly different ordering; RateLimit invariant log stays
   within 15/5s during the gap walk.
4. Existing `FavoritesRefreshSurvivalTest` + `FavoritesCacheInvalidationTest` stay
   green (dedup semantics untouched, per-generation behavior preserved).

### Risk / notes

- Extra API calls per APPEND when a creator exhausts mid-rotation: bound them with the
  existing walk budget share (`MAX_WALK_HOPS` re-used); if a rotation truly exhausts
  all creators the walk ends in bounded calls.
- Upstream pagination overlap is already deduped per generation (`sessionSeen`) —
  rotation re-landing earlier gifs is invisible to the grid.
- Do NOT introduce a persisted seed: per-day persistence would make refreshes
  deterministic again. Volatile = state dies with the app process, which matches "on
  refresh, shuffle" semantics.

### Sort options valid per feed type (input for #5 + #18) — from the API, NOT invented

| Feed / endpoint | `order` values verified live | Comment source |
|---|---|---|
| `FeedSource.Search` → `GET /v2/gifs/search` | `trending` (default), `latest`, `top7`, `top28`, `score` | server-verified taxonomy (2026-10-01, FeedSource.kt:159-196 comment; `GifsApi.search` GifsApi.kt:43-58) |
| `FeedSource.Creator` → `GET /v2/users/{u}/search` | `trending` (default), `oldest`, `latest`, `top`, `top7`, `top28` | `GifsApi.kt:59-67` comment — `sortOptions()` deliberately surfaces only `latest/oldest/top7/top28` (site's own wording) |
| `FeedSource.Niche` → `GET /v2/niches/{id}/gifs` | **omit** (default), `hot`, `oldest`, `latest`, `best` | `GifsApi.nicheGifs` `GifsApi.kt:104-111` + `AGENTS-NETWORK.md:11` — `order=trending` 2026-10 drift → BadOrder; the fetcher's default arm already omits (`FeedPageFetcher.kt:90-98`) |
| `FeedSource.Trending` → `GET /v2/feeds/trending/popular` | no user-facing sort; only `top_week` verified for the OTHER call | `GifsApi.trendingPopular` :71-78; `FeedSource.sortOptions()` returns empty |
| `FeedSource.TopThisWeek` | `top_week` pinned | `FeedPageFetcher.kt:130-132` |
| `FeedSource.ForYou` → `GET /v2/feeds/for-you` | NO `order` param on the endpoint | `GifsApi.kt:213-217` |
| `FeedSource.Liked` → `GET /v2/feeds/liked` | NO `order` param | `GifsApi.kt:219-226` |
| `FeedSource.Favorites` | currently none (round-robin uses no order) — **live-verification candidate** | see items needing live verification |
| `FeedSource.Custom` | per-ref: creator w/ `trending` default; niche omitted; tag w/ `order=trending` | `FeedPageFetcher.kt:100-129` |
| `v2/niches` (niches TAXONOMY list, not the gifs feed) | `posts`, `subscribers`, `best_match`, `alphabetical_asc`, `alphabetical_desc`, `random` | `GifsApi.niches` :79-90 + `AGENTS-NETWORK.md:12` — not applicable to #18's feed dialog (NichesScreen already owns it) |

---

## Issue #6 — TV quick-menu 'Refresh feed' action

### Current behavior

- TV refresh paths today: none on a full-page feed — `TvNicheFeedViewModel.gifs(source)`
  is a one-shot cached pager with NO generation bump (`TvSourceFeedScreen.kt:196-201`;
  `repository.paging(source)` without `forceRefresh`); `PullToRefresh` is mobile-only.
- Quick actions: `TvQuickActionsDialog` (panes main/addto/tags/more/block, `TvQuickActions.kt:71-349`)
  has no refresh row; "Open feed" exists for the row context (`feedSource != null`,
  `TvQuickActions.kt:286-291`).
- Grid state is hoisted at Root (`gridState` to `TvMainActivity.kt:171-172`) for BACK
  preservation; focus plumbing (`firstCardFocus` on first card) lives in
  `TvSourceFeedScreen.kt:89-97` (`LaunchedEffect(gifs.itemCount > 0)` → `firstCardFocus.requestFocus()`).

### Root cause / gap

No user-visible refresh path on源的 TV full-page feeds; `repository.paging(source)`
can't be re-bumped (no refreshGen mechanism on TV) — the mobile `FeedViewModel`
generation machinery isn't reachable from `TvNicheFeedViewModel`.

### Proposed minimal change

1. `TvNicheFeedViewModel` (`TvSourceFeedScreen.kt:196-201`): add a generation counter:

   ```kotlin
   private val refreshGen = MutableStateFlow(0)
   fun refresh() { refreshGen.value += 1 }
   fun gifs(source: FeedSource) = refreshGen
       .combine { gen -> gen }
       .flatMapLatest { (source, gen) -> repository.paging(source, forceRefresh = gen > 0).cachedIn(viewModelScope) }
   ```

   (exact wiring to follow flatMapLatest shape; the one-shot `forceRefresh=true`
   mirrors mobile `FeedViewModel.kt:129-136`).
2. `TvSourceFeedScreen`: capture the VM, and add to the Filter chip (top-end
   `Alignment.TopEnd`, `TvSourceFeedScreen.kt:88-100`) a second chip **"Refresh"** using
   the SAME `giffyFocus` ring pattern:
   onClick → `viewModel.refresh()` + `scope.launch { gridState.scrollToItem(0); firstCardFocus.requestFocus() }`.
   (Chips row over the grid = the Filter chip's proven focused-treatment; a second chip
   left of Filter keeps 10-foot geometry consistent — one row, two chips.)
3. `TvQuickActions` (the grid pane, `feedSource != null`): add a **"Refresh feed" row**
   after "Open feed" (`TvQuickActions.kt` after :291) with
   `onRefresh: (FeedSource?) -> Unit` wired from both call sites:
   - `TvSourceFeedScreen` (`TvQuickActions.kt:185-195`): dismiss + `viewModel.refresh()` +
     scroll-top/first-focus (same helper as 2).
   - `TvHomeScreen` (`TvQuickActions.kt:260-289`, via home rows): no home refresh exists
     and home rows are separate pagers — the row is NOT added for home contexts
     (feedSource-on-home opens the full-page feed where refresh exists). Scope note
     recorded for the parent review.
4. Reduced motion: none (no animation).

### Acceptance criteria

1. Device (TV36): open a pinned niche/creator feed → MENU on a card → panel has
   "Refresh feed"; activating it reloads the grid (new pill/first load visible), grid
   scrolls to top, first card regains focus (dump-verified, chip-match on
   `firstCardFocus` behavior).
2. Filter chip + Refresh chip both take and keep D-pad focus (shared giffyFocus ring).
3. Back from refresh state: the stale refresh generation retains no lingering pager
   (`forceRefresh` is one-shot, per the mobile consumedGen pattern).
4. Unit test: `TvNicheFeedViewModel` force flag consumed once (a tiny test in :app-tv
   or feature:feed if the VM lands out of there) — or fold into a device check if the
   Hilt VM shape forbids plain tests.

### Risk / notes

- Refresh during in-player preview: `FocusPreview` is Dispose-disposed on refresh
  (screen state churn) — verify nothing re-sets a preview gif (the settle rule pins
  ensure it re-fires off the new pager generation only after focus settles again).
- Rate limit: a single refresh is ONE API call (page 1 revalidate via mediator TTL
  bypass).

---

## Issue #22 — Infinite scroll until nothing left + TV end-of-feed + TV player end-of-pool popup

### Current behavior — per FeedSource paging sink

| Source | Pagination end behavior | Gap |
|---|---|---|
| Trending / Search / Creator feeds / Custom | `walkFeedPages` ≤8 hops; pool cap: beyond-pool fetch → HTTP 400 seized in `walkFill` (`FeedPagingSource.kt:62-124`) → `null entity` → graceful EMPTY tail. **Honest end = trending pool cap ~100 items (live-verified); server pages otherwise paginated.** | none functionally; TV needs an end-of-feed banner (below), mobile already lands a quiet stop |
| TopThisWeek | same cached-pager chain; pool cap ends the feed | same |
| Favorites | dies on FIRST empty slot, not at true rotation end (see #5) | fixed by #5 |
| Custom | round-robin; tag/creator/niche per-ref inner pages | same shape as Favorites post-#5 mechanics (round-robin; keep as-is — out of scope to add stride unless the parent raises it) |
| For You (`ForYouNetworkPagingSource.kt:30-107`) | network-live, dedup-aware B1 walk ≤8 hops, 400→graceful end | OK |
| Liked (`LikedNetworkPagingSource.kt:24-99`) | same pattern | OK |
| Continue / Surprise | feature surfaces | deleted by #1/#12 |

- TV end-of-feed UI: none. `TvSourceFeedScreen` grid just stops; home rows stop silently.
- TV player end-of-period: `TvPlayerScreen` at the last item with auto-swipe OFF /
  dataSaver loops (`TvPlayerScreen.kt:482-499` `onPlaybackStateChanged`, `index >= gifs.size - 1 → seekTo(0)+play()`); with it ON, auto-swipe advances while unshuffled (last item loops). The gif list is a static snapshot taken at open (`snapshot(gifs)` in `TvHomeScreen.kt:421-428`) — no auto-retry of more pages, so "end of all videos" really just means the snapshot end.
- Mobile player ALREADY handles pool end: the pendingAdvance logic (`PlayerScreen.kt:200-299`)
  asks paging for the next page and, once `endOfPaginationReached`, jumps to a shuffle
  target (when seeded) or loops the last video — mobile parity already exists.

### Root cause / gap

TV lacks (a) an explicit feed-exhaustion affordance on full-page feeds, (b) a
player-end popup. Mobile is complete except #5's Favorites exhaustion.

### Proposed minimal change

1. **TV full-page feeds (`TvSourceFeedScreen`)**: after the grid items, when
   `gifs.loadState.append is LoadState.NotLoading && endOfPaginationReached ||
   gifs.itemCount == 0 (NotLoading)` → compose an end-of-feed footer row at the bottom
   of the grid:

   ```kotlin
   item { EndOfFeedButton(onClick = refreshAndReturnToTop) } // "End of feed — Refresh"
   ```

   `refreshAndReturnToTop` reuses issue #6's refresh plumbing: reload the source,
   scroll to top, focus the first item. Pure state addition; no cache change.
2. **TV player popup**: when the user navigates NEXT from the LAST gif (the
   seek-by/next handler knows `index == gifs.size - 1`) → show a 1-dialog popup
   (`@TvSourceFeedScreen`-style `M3Surface` Dialog, D-pad focusable per
   `TvQuickActionsDialog` pattern):

   "End of feed" · rows:
   - **"Start over"** → `index = 0`, `skipResume`, autoFocus the player body.
   - **"Back to feed"** → `onBack()` (returns to the grid, which by #22's own rule now
     shows "End of feed — Refresh" ready to march top).
   Row labels per the issue wording; no auto-refresh (popup, not rewind).
3. **Mobile**: no code change to pagination; EVERY source already walks to a real end
   or the pool cap. The "infinite until nothing left" rule relies on #5 for favorites;
   for Trending the pool cap (~100 items) IS "nothing left to paginate" (documented in
   the batch-22 session log; no artificial cap of OUR making) — no fake infinite loop
   for the trending pool.
4. `FeedRow` home rows: silently exhausted rows are the DECISION of record (no extra
   chrome per row on TV; the parent may overrule — see ambiguities).

### Acceptance criteria

1. Compiles green on :app-tv (+ feature:feed if shared items touch it).
2. Device TV36: scroll a pinned feed to its true end → "End of feed — Refresh"
   appears, is D-pad focusable, activation scrolls to top and focuses the first card.
3. Device TV36: in a 2-item creator feed, press next at the last item → popup appears
   (screencap), "Start over" returns to item 1 focused; "Back to feed" exits to the
   grid.
4. Mobile: no regression on the pool-end walk (`FeedWalkTest`, pool-cap tests stay green).

### Risk / notes

- The "footer item" proposal must not interact with `snapshot()` — it is composed under
  the grid's `item {}` slots; keep the footer OUT of the pager item list (compose it
  after the `items` block in the grid), keyed off `loadState` not item indices.
- ForYou snapshot-player pagination: out of scope — the issue's ask reads "player end
  of all videos"; the snapshot contains the loaded pages only, so "start over" must not
  claim MORE were loaded than the grid gave. Truthful copy: "End of feed" fits both.

---

## Issue #23 — Mobile scroll-to-top icon on feeds

### Current behavior

- A scroll-up affordance already exists on the feed grid: `RefreshFeedPill`
  (`core/ui/RefreshFeedPill.kt:1-44`; filled `Button` + text) = **scroll-to-top +
  force revalidate in one action** (IG new-posts semantics). Shown while the user has
  scrolled ≥ `SCROLL_PILL_THRESHOLD` (10 items) AND is scrolling upward
  (`rememberScrollingUp`, `RefreshFeedPill.kt:55-83`); reduced-motion aware —
  `FeedScreen.kt:755-771` uses `scrollToItem(0)` (instant) when reduced. Active on
  `FeedScreen` only (`FeedScreen.kt:613-631`, click at :739-750).
- OTHER mobile full-screen feeds do NOT get any pill:
  `ExploreScreen`, `FollowingScreen`, `CollectionsScreen`, `NichesScreen`,
  `CustomFeedsScreen`, `NicheAboutScreen`, `SearchScreen` results — plain LazyColumns
  (per batch-11 audit note 3) with no scroll-state affordance.

### Root cause / gap

The user asked for a scroll-to-top ICON (navigation affordance, no revalidate). Today
the ONLY floating control doubles with a network revalidate; the moment a second
floating control appeared, the "never two stacked floating buttons over media" rule
(the pill's own doc comment, `RefreshFeedPill.kt:8-10`) is violated.

### Proposed minimal change — DECISION within this doc, parent confirms

**Recommendation: repurpose, don't stack.** Convert the existing pill into a
scroll-to-top ICON (IconButton, `Icons.AutoMirrored.Filled.KeyboardArrowUp`) — same
threshold/only-while-scrolling-up/reduced-motion logic — and REMOVE the force-revalidate
half (pull-to-refresh already owns revalidate on every feed at `FeedScreen.kt:690-701`,
and #6 owns refresh on TV). This yields an unambiguous single floating affordance per
the surface; adding a SECOND control next to the pill is rejected as a UX violation.

Surfaces that get the icon (DISCOVERY of which ones want it, at the point of
implementation each with the same helper):

- **`FeedScreen`** (main feed): YES — pill swap, top priority (this is the scrolled
  one).
- **`ExploreScreen`**: YES (creator rows reach thousands of entries).
- **`NichesScreen`**, **`CollectionsScreen`**, **`FollowingScreen`**: YES (same
  LazyColumn shape; content can page deep — Collections page-limit 50 and Following
  count-100 per request, both appended by the server through paging VMs).
- **`CustomFeedsScreen`**: NO while #10 flag is off (screen unreachable).
- **`SearchScreen`**: NO — different layout (field + suggestion rows + scope tabs
  batch-14; a floating control competes with the scope tabs' TabRow geometry).
- **`NicheAboutScreen`**: NO — short content (detail + top creators + related niches;
  not page-deep).
- TV: the issue scopes it to "mobile"; `#6` covers the TV top-return via refresh.

Cross-surface shape today (no shared state):
| Surface | scroll container |
|---|---|
| FeedScreen | LazyStaggeredGridState already feeds `rememberScrollingUp` |
| Explore/Following/Collections/Niches/CustomFeeds | LazyListState |

Implementation: generalize `rememberScrollingUp` minimally by adding a LazyListState
overload in `:core:ui` (same derivedStateOf body, parameterized on
`firstVisibleItemIndex/ScrollOffset` getters), and a small `ScrollToTopFAB`
(or rename `RefreshFeedPill` → `ScrollToTopFab`) composable — one button + gating
predicate + reduced-motion instant/instant-scroll helper that both container types call.
Keep it in `:core:ui` (GiffyApp-wide, used from feature screens).

Reduced motion: reuse the same pattern as `FeedScreen.kt:766-770` (scale==0 → instant
`scrollToItem`); the shared helper takes the host's `reducedMotion` flag.

### Acceptance criteria

1. Mobile FeedScreen: the floating icon appears after ≥10 items scrolled + upward
   motion, is a single pill, and jumps to top (with reduced-motion instant) —
   no force-revalidate on click (network only via pull-to-refresh).
2. Explore/Following/Collections/Niches show the icon on deep scroll, each reduced-
   motion aware.
3. `RefreshFeedPill`'s tests/documentation swap; any test depending on the
   force-revalidate click path updates accordingly (FeedScreen combinator tests now
   rely on `PullToRefreshBox(onRefresh)` only).
4. ktlint/detekt + unit tests green (:core:ui + touched feature/app modules).

### Risk / notes

- Semantics change is a user-facing behavior change (refresh-behalf of the pill);
  called out explicitly in the summary for parent confirmation (see Ambiguities).
- If the parent prefers keeping the refresh+scroll combo, the alternative is to NOT
  touch the pill and instead ADD the icon only to surfaces WITHOUT a pill
  (Explore/Following/Collections/Niches) — those have no revalidate affordance, so a
  scroll-to-top icon per surface is pure-additive there; FeedScreen keeps the pill.
  This variant touches less behavior; flag this as the conservative path.

---

## Issue #18 — ONE sort + filter popup per feed (extends, does not rebuild)

### Current behavior

- Per-feed SORT: `FeedViewModel.setSort(source, sort)` persists per-base via
  `settings.setFeedSort(baseKey, sort)` (`SettingsRepository.kt:188`) + a ONE-shot TTL
  bypass (`pendingForce`, `FeedViewModel.kt:479-492`); `open()` ADOPTS a saved sort on
  the three sortable source types (`FeedViewModel.kt:153-163, 165-171`).
- Per-feed FILTERS: `FeedFilterDialog` (`FeedFilterDialog.kt:27-140`) with
  duration / resolution / Shuffle / strict-tags (tag bundles) / Orientation rows,
  persisted via `settings.setFeedPrefs(baseKey, prefs)` (FeedPrefs default
  `SettingsRepository.kt:240-248`).
- The Filter ▾ chip grid + sort chips row are SEPARATE on the mobile `FeedScreen`
  (`FeedScreen.kt:436-488`): left = horizontal scroll-sided FilterChip per sort from
  `source.sortOptions()` + `Filter ▾` chip + `Clear` chip on the right; TV prop
  ALSO opens the same dialog (`TvSourceFeedScreen.kt:170-183`, "Filter" chip at :88-100).
- GLOBAL filters (already in Settings, unchanged):
  orientation (`§6`, `:core:datastore` `orientationFilter`), verified-only
  (`:core:datastore` `verifiedOnly`), plus block sets per ContentFilter. The dialog
  merges per-feed over global on read-time (`FeedPagingSource.kt:186-190`).
- Clear-filters: the inline mobile `Clear` chip wipes `FeedPrefs` for the feed
  (`FeedScreen.kt:471-484`); TV has no Clear row.

### Root cause / gap

Sorting lives OUTSIDE the dialog (chips); TV has no sort UI at all (no chips row);
there is no unified one-icon entrypoint. The piece is EXTEND-able: `FeedFilterDialog`
is the choke point, per-feed sort persistence is fully built — the hole is only the
dialog wiring + chip removal.

### Proposed minimal change (EXTEND `FeedFilterDialog`)

1. **Sort section in the dialog.** `FeedFilterDialog` gains params:
   `sortOptions: List<Pair<String, String>>`, `selectedSort: String`,
   `onApply: (FeedPrefs, selectedSort) -> Unit`. Rendered as the FIRST section titled
   "Sort" when `sortOptions.isNotEmpty()`, chipped exactly like the duration row (same
   chip component + spacing); per-feed sort persistence rides the EXISTING
   `onApply` → VM calls BOTH `setFeedPrefs(baseKey, prefs)` and
   `setSort(source, selectedSort)` (the sort half re-uses `pendingForce` + adoption
   logic, no new persistence).
2. **Clear-filters inside the dialog.** Add a `TextButton("Clear filters")` bottom row
   (dismissButton alternative): calls `onApply(defaultFeedPrefs, sort = "")` (so both
   filters AND the per-feed sort reset in one place). TV gains its missing Clear for
   free.
3. **One icon entrypoint per surface.** Replace the `FeedScreen.kt:436-470` chip row
   and the `TvSourceFeedScreen.kt:88-100` Filter chip with ONE `IconButton` using
   `Icons.AutoMirrored.Filled.Sort` (descending horizontal lines; standard M3 icon,
   import from material-icons-base — already on the classpath by way of other icon
   imports; fallback: `Icons.Filled.Sort` if AutoMirrored lands only in newer icons-
   artifacts, verify at implementation). Selected/hint styles: active-state shows a
   badge (e.g. an enabled chip-like background tint) when either filters or a
   non-default sort is active (the badge state composition already exists as
   `filtersActive` :452-455 — extend with `savedSort.isNotEmpty()`).
4. **No endpoint invention.** Sort options come ONLY from
   `FeedSource.sortOptions()` (`FeedSource.kt:165-196`), which returns empty lists for
   ForYou/Liked/Trending/TopThisWeek/Favorites/Custom — those feeds simply get a
   dialog with NO sort row (Sort chip section suppressed when empty; Shuffle row does
   appear where it makes sense — all feeds, as today). Validity table under #5 stands
   as the source of truth; anything not in the table is NOT OFFERED.

### Acceptance criteria

1. Mobile: one icon opens ONE popup offering sort (when the feed has server sorts),
   duration/resolution/orientation/shuffle/strict-tags, and "Clear filters" that
   resets everything (dialog closes, sort chips row above the grid is GONE from
   FeedScreen — the tiles get more vertical space, the round-3 #7 win is preserved
   and improved).
2. TV: the icon replaces the Filter chip; sort worked nowhere before, works now —
   saved per feed persists across opens (`FeedViewModel.open` adoption 153-163), D-pad
   ring per the Settings chip-row pattern (`focusGroup` + `giffyFocus`).
3. Unit test: the dialog's clear path resets FeedPrefs AND savedSort
   (`FeedFilterDialogTest`-style pure piece if the apply rule is extracted; else the
   pure `durationIn`-style test stays).
4. No endpoint/query param change anywhere (`GifsApi.kt` untouched — this is a UI
   assembly over `order=` values already verified).

### Risk / notes

- The dialog already uses M3 `AlertDialog` (D-pad-walked OK at batch 12); a Sort row
  is more chips in a scrollable row — same pattern.
- `activeSort` display code (`FeedScreen.kt:442-470`) disappears with the chips;
  `FeedViewModel.setSort` gains story: it's now called from the dialog, one caller.
- Reduced motion: dialog has no animation concern.

---

## Implementation order (grouped by file, to minimize churn)

Phase ordering assumption: #10 (flag) first — it shrinks some later diffs' UI paths;
#1 and #12 are two DB-backed deletions that pair cleanly (both gut the repository's
first branches and the v11 migration in one go).

| Phase | Step | Files |
|---|---|---|
| 1 | #10 flag + gate sites (no data change) | `core/ui/Feature.kt` (new), `FeedScreen.kt` (3 gates), `MainActivity.kt` (0 code, routes already flag-safe), `TvMainActivity.kt` (2 gates), `TvQuickActions.kt` (1 row gate), `QuickSheet.kt` (picker + main row condition) |
| 2 | #12 Surprise removal | `FeedRepository.kt`, `SurprisePoolPagingSource.kt` (delete), `FeedSource.kt`, `FeedViewModel.kt`, `FeedScreen.kt`, `TvMainActivity.kt`, `Daos.kt` (randomUnwatched), `TvQuickActions.kt` (comment) |
| 3 | #1 Continue removal + v11 migration (co-located with phase 2's Daos.kt edit) | `GiffyDatabase.kt` (v11 + MIGRATION_10_11), `Entities.kt`, `Daos.kt` (WatchHistoryDao), both `AppModule.kt`s, `ContinueWatchingPagingSource.kt` (delete), `ContinueWatchingViewModel.kt` (delete), `FeedRepository.kt`, `FeedSource.kt`, `FeedScreen.kt`, `TvHomeScreen.kt`, `TvMainActivity.kt`, both `PlayerScreen.kt`s, `TvPlayerScreen.kt`, `ContinueSourceTest.kt` (delete) |
| 4 | #5 Favorites (infinite + shuffle refresh) | `FeedPageFetcher.kt`, `FeedMediator.kt`, `FeedRepository.kt`, + tests in `feature/feed/src/test` |
| 5 | #6 TV refresh feed (+ #22 reuses it) | `TvSourceFeedScreen.kt`, `TvQuickActions.kt`, `TvMainActivity.kt` wiring |
| 6 | #18 one sort+filter popup (extends FeedFilterDialog) | `FeedFilterDialog.kt`, `FeedScreen.kt` (chip row removal + icon), `TvSourceFeedScreen.kt` (chip → icon), `FeedViewModel.kt` (apply hook co-locates with setSort) |
| 7 | #23 scroll-to-top icon | `core/ui/RefreshFeedPill.kt` (repurpose) or new `ScrollToTopFab.kt` + `rememberScrollingUp` LazyListState overload, `FeedScreen.kt`, `ExploreScreen.kt`, `FollowingScreen.kt`, `CollectionsScreen.kt`, `NichesScreen.kt` |
| 8 | #22 end-of-feed + player popup | `TvSourceFeedScreen.kt` (footer row), `TvPlayerScreen.kt` (popup) |

Rationale: phases 2-3 touch the same repository/daos/screenslines back-to-back without
regenerating; phase 5's TV refresh lands before #22 (which reuses it); phase 6's dialog
row extension is independent of 7 (both touch FeedScreen minimally but different
regions). Every phase keeps the touched modules' compile+ktlint+detekt+unit-test gate
green individually.

---

## Items needing live-site verification before/while building

1. **Search feed's `score` sort stability** (#18's only non-obvious search chip):
   `FeedSource.sortOptions()` says verified 2026-10-01; one anonymous probe rerun of
   `GET /v2/gifs/search?search_text=…&order=score` suffices (row ordering differs from
   `trending`, HTTP 200) — no code until confirmed.
2. **Favorites: does `GET /v2/users/{u}/search` accept an `order` we can pass to gain a
   server-true ordering?** (#5's fetch currently uses the default `trending` only —
   the table in #5 already assumes NO user-visible Favorites sort; if the parent wants
   one later, the probe result feeds `sortOptions()`.)
3. **Pool-cap semantics on other sources** (#22 honesty check): replay the
   batch-14-style probe once — `userGifs`/`search`/`nicheGifs` at a page beyond the
   still-served window confirm 400-vs-empty-page behavior (the walk treats 400 as
   end; an EMPTY page would need the walk's `nextKey null` path — already handled,
   just verify which shape each surface returns).
4. **For You page pacing (rate budget)**: after any #5 stride change re-reads
   fetch-bursts, re-log one device pass to confirm the 15/5s window holds during
   favorites walk (no probe site-side).

## Ambiguities (decisions requested from the parent)

1. **watch_history: KEEP (starved) vs DROP (v11)** — recommendation: DROP (#1 above).
   Confirm before implementation; it changes the diff materially.
2. **Feature flag placement** — recommendation: `const val` in `:core:ui/Feature.kt`
   (#10). Alternative accepted shape: `:core:model` object; no functional difference,
   one module edge changes. Confirm.
3. **#10 QuickSheet main-pane "Add to…" row visibility** — with the flag off and
   zero custom feeds, the row never appears today (Collections/Niche adds unreachable
   from QuickSheet); preserving the CURRENT behavior means collection adds stay
   unreachable for users who only joined collections, which is a pre-existing
   behavior bug (out of this stream's scope) — OR fix it opportunistically by
   showing the row whenever ANY add target exists. Recommend the opportunistic fix
   (1-line condition), flagged here because it widens scope slightly.
4. **#23 semantics** — replace the refresh-pill with a scroll-to-top icon (recommend)
   vs keep the pill on FeedScreen and add the icon only to the other surfaces. The
   second variant is the conservative no-behavior-change path.
5. **#6/#22 TV home-row refresh** — recommended: refresh lives on the full-page feed
   only; home rows exhaust silently (no per-row chrome). Parent may want a row-level
   end-of-feed hint instead.
6. **#18 sort for non-server-sorted feeds** — Shuffle stays the only user-facing order
   control on ForYou/Liked/Trending/TopThisWeek/Favorites/Custom; the dialog shows no
   sort row for them. Confirm this is the intended reading of "per-feed default
   sorting user-changeable" (i.e., sorting applies only where the server accepts an
   `order`).
7. **#22 player popup copy** — "Start over" (loops to index 0) vs "Refresh and start
   over" (needs re-collecting the feed from the underlying screen; bigger plumbing
   than the popup design assumed). Recommend "Start over"; the popup can be upgraded
   later if a refresh path lands (it reuses #6's refresh only when the player is
   entered from `openFeed`, not from home cards, since home-card snapshots don't
   own the source).
