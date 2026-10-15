# STREAM-REPORT-nav (final) — issue #13 navigation-compose migration, both shells

Supersedes/references `STREAM-REPORT-nav.md` (same stream, earlier write); this file is the
authoritative completion report. Per task rules NO gradle/jj/git/adb was run — verification is
content-anchored grep + brace/paren balance + import-order + ≤140-col + trailing-whitespace checks.

## 1. Completeness grep-verify (both shells)

- **Route-flag state: ZERO.** `playerStartIndex / showExplore / showNiches / showSettings /
  showFollowing / showCollections / showCustomFeeds / aboutNiche / openFeed` — no live route-state
  usage in either host or touched feature code. Residual references are doc-comments only (now
  refreshed: TvQuickActions "Slice 10 navigation hook — the TvFeed nav destination navigates",
  TvSourceFeedScreen strip-seam comments, FeedTabStrip header comment); mobile keeps ONE deliberate
  §9-lifecycle comment naming the old `playerStartIndex flag` it replaced (FLAG_KEEP_SCREEN_ON keyed
  on `destination.hasRoute<MainPlayer>()`).
- **NavHost present in both shells:** `NavHost(` found in `mobile/MainActivity.kt` and
  `tv/TvMainActivity.kt`. Mobile start = `MainFeed(source = null)` (resolves
  `feedViewModel.seedSource`); TV start = `TvHome`.
- **ViewModel scoping per design §3.3/§4.2:** shared `FeedViewModel` acquired with explicit
  `hiltViewModel()` at NavHost scope (mobile :145, TV quickVm :179) and passed by parameter to the
  player + feed destinations; naturally-per-visit models use default entry-scoped `hiltViewModel()`
  (Search/Collections/CustomFeeds/Following/Niches/NicheAbout, TV's TvNicheFeedViewModel, TvHome's
  home model).
- **Back behavior preserved:** no host-level BackHandler in either shell (M3 default pop + the
  explicit `popBackStack()`/`popUpTo(entry.id){ inclusive = true }` replaces for player-cluster
  opens and TV sort change); E's SettingsScreen in-screen panel BackHandler is inside
  `:feature:settings` and stays above nav per §6; WebViewLoginScreen's webview-history guard
  untouched.

## 2. Version-catalog house rule (no literals)

Already compliant — corrected nothing:
- `gradle/libs.versions.toml`: `navigationCompose = "2.10.2"` version entry +
  `navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }` library entry (after `hilt-navigation-compose`).
- Both app modules use the alias: `implementation(libs.navigation.compose)`
  (`app-mobile/build.gradle.kts:93`, `app-tv/build.gradle.kts:105`). Zero literal mappings.

## 3. TvSourceFeedScreen / TvMainActivity staggered-grid state per destination

Present: the `TvFeed` nav destination creates its OWN per-entry saveable
`val gridState = rememberLazyStaggeredGridState()` (TvMainActivity:193) and passes it into
TvSourceFeedScreen (whose default param remains for standalone callers). The batch-22 Root-hoisted
`homeListState`/`feedGridState` are gone (grep-zero). Focus restore per §4.3 is present in the same
destination: `var focusItem by rememberSaveable { mutableIntStateOf(-1) }` (:197) feeding
`savedFocusItem`/`onFocusSaved` into TvSourceFeedScreen, whose re-arm consumes
`focusTargetFor(savedFocusItem, itemCount)` once per content delivery (boolean key). Home rows use
the (rowTitle, itemIndex) saveables in `TvHomeDestination` with per-row re-arm latches and a
saveable-gated header-pill initial focus.

## 4. Final file table (stream diff)

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | `navigationCompose = "2.10.2"` version + `navigation-compose` library entry |
| `app-mobile/build.gradle.kts` | + kotlinx-serialization plugin alias, + `implementation(libs.navigation.compose)` |
| `app-tv/build.gradle.kts` | same two lines |
| `core/model/build.gradle.kts` | + serialization plugin alias + `kotlinx.serialization.json` (annotations only; DTOs untouched) |
| `core/model/.../Gif.kt` | `@Serializable` on Gif + NicheRef (route/saved-state payloads ONLY — comment records why) |
| `feature/feed/build.gradle.kts` | + serialization plugin alias + `kotlinx.serialization.json` |
| `feature/feed/.../FeedSource.kt` | `@Serializable` sealed interface + all 11 subtypes; `keyBase`/`baseKey`/`ttlMs` → computed `get()`s (route arg = ctor params alone) |
| `feature/feed/.../FeedViewModel.kt` | deleted `previousSource`/`_canGoBack`/`canGoBack`/`back()` (open() keeps adoption + userNavigated + lastGroupSub); added `val seedSource` |
| `feature/feed/.../FeedScreen.kt` | + `onFeedNav` seam (strip/creator chips/tile caption/changed sort/quick sheet); hop BackHandler + canGoBack deleted; grid state → per-entry saveable `rememberLazyStaggeredGridState()` |
| `feature/feed/.../QuickSheet.kt` | seam → `onOpenFeed: (FeedSource) -> Unit` (open+dismiss → dismiss+navigate) |
| `feature/feed/.../PlayerScreen.kt` | + `onOpenFeed` param; overlay cluster rows + sheet row route through it (host pops the player while pushing) |
| `app-mobile/.../MainActivity.kt` | FULL rewrite: routes + `MobileNavHost`; age gate/PIN outside; FLAG_KEEP_SCREEN_ON → `hasRoute<MainPlayer>()`; search-submit stays on stack |
| `app-tv/.../TvMainActivity.kt` | FULL rewrite: routes + `TvNav`; shared quickVm activity-scoped; per-entry grid state + focusItem saveable; sort change = push+replace; player openings push+replace; home destination owns pills/More ▾/focus saveables |
| `app-tv/.../TvHomeScreen.kt` | focusRow/focusItem/onFocusSaved params; per-FeedRow/CreatorRow rowKey/restoredItem/onFocusSaved, FocusRequester attach on the restored card, per-appear re-arm latch, save-hook on focus |
| `app-tv/.../TvSourceFeedScreen.kt` | gridState→per-entry param; + savedFocusItem/onFocusSaved; first-card focus effect re-arms via `focusTargetFor`; stale openFeed comments refreshed |
| `app-tv/.../TvQuickActions.kt` | stale `openFeed` doc comment refreshed (no code change) |
| `feature/feed/.../FeedTabStrip.kt` | header doc comment refreshed (mobile = onFeedNav push, TV = TvFeed push) |
| `app-tv/.../FocusRestore.kt` (NEW) | pure `focusTargetFor(savedFocusItem, itemCount)` — the re-arm decision |
| `app-tv/src/test/.../FocusRestoreTest.kt` (NEW) | minimal self-check: feasible saved card re-arms, boundary card valid, -1/regrown pool → 0 |
| `AGENTS-APP.md` | "NAVIGATION migration (issue #13) — BUILT" record; round-3 High + NOT-now blocks marked superseded-by-trigger |
| `AGENTS.md` | 2 stale "app uses its own saveable route-stack" lines corrected (nav-compose 2.10.2 adopted; N3 stays declined) |

## 5. Deviations (design-vs-tree, re-anchored by content)

1. **No `MainExplore` route** — ExploreScreen was deleted by the search stream pre-migration; the
   design doc's roster predates it, the task's destination list never names it either.
2. **Player cluster opens = a real seam change: `onOpenFeed: (FeedSource) -> Unit` (push +
   pop-replace)** —
   keeping the doc's implicit `viewModel.open` + onBack would interact with the required per-entry
   source re-assert and revert the swap on back; the seam preserves "open closes the player" and
   BACK lands the covered feed at its scroll position.
3. **TV changed sort = entry replace** (`popUpTo(entry.id){ inclusive = true }`) — no stacking per
   tap; the dying TvNicheFeedViewModel's `refresh()` call is gone with the entry (comment in the
   dialog).
4. **TV TvNicheFeedViewModel entry-scoped** (doc confirms "entry-scoped per feed"); mobile
   FeedScreen/PlayerScreen stay activity-scoped (§3.3).
5. **`data object` MainSearch/MainSettings** instead of the doc's fieldless `data class` shells.
6. **`Feature.CUSTOM_FEEDS` gate** preserved on TV (More ▾ My feeds); mobile CustomFeedsScreen
   destination unchanged shape.

## 6. Risks / owed gates

- **Device pass (design §8, 15 steps, both emulators) OWED** — not runnable under these task rules
  (no adb/emulator). Highest-sensitivity bits: nav-entry source re-assert must not flash/reset the
  grid on pop (believed clean: same-value StateFlow write → no pager restart); TV D-pad focus
  restore walk (focus-trap class, R8); S3 reset-emit + For-You revert semantics under nav
  (ForYouDefaultTest decision path untouched).
- Player index process-death restore fragility (R2: index vs regrown pager) pre-existing, unchanged.
- TV player route carries the gif window in saved state (R3, theoretical >1MB risk; fallback
  deliberately not built).
- Fresh install / process-death on the start route re-seeds For-You via `seedSource` — gate-3
  offline revert semantics unchanged (decision logic untouched).
