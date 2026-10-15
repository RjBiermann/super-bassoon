# STREAM REPORT — tabs stream (issue #25, DESIGN-tabs-stream.md)

Implemented the complete tabs stream: FEED_GROUP strip group + sub-row on mobile and TV,
lastGroupSub memory, More-menu delta, unit test.

## Changed files

| File | Change |
|---|---|
| `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedTabStrip.kt` | Added pure group seams `FEED_GROUP` (Trending · TopThisWeek · TopRanged("top28") · TopRanged("top"), order-sensitive), `feedGroupTapTarget(current, lastGroupSub)`, `isInFeedGroup(source)` — all `internal`, plain Kotlin (no Compose), testable. Outer strip `Row` → `Column { primaryRow; subRow? }`; primary chips now For You (logged-in filter kept) · **Feeds** chip (`selected = isInFeedGroup(source)`, `onClick = onOpen(feedGroupTapTarget(source, lastGroupSub))`) · Favorites · More ▾. New sub-row: second INDEPENDENT horizontally-scrollable `Row` (own `rememberScrollState()`), same 12.dp padding/8.dp spacing, `FilterChip`s labeled `member.title()`, exact-equality selection, rendered iff `source in FEED_GROUP` (plain `if`). Both rows carry `.focusGroup()` (TV chip-skip lesson). More ▾: Top This Week DropdownMenuItem deleted; More-selected predicate drops `source is FeedSource.TopThisWeek` (comment records why). New `lastGroupSub: FeedSource = FeedSource.Trending` parameter. |
| `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedViewModel.kt` | `private var lastGroupSub: FeedSource = FeedSource.Trending` + `fun lastGroupSub(): FeedSource` (click-time read, no StateFlow wiring, NOT persisted per design §3); `open()` gains the single assigned line `if (next in FEED_GROUP) lastGroupSub = next` (post-sort-adoption value). |
| `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedScreen.kt` | One added strip argument: `lastGroupSub = viewModel.lastGroupSub()`. No other mobile change (GiffyScaffold/enter-always/nested-scroll untouched — rows already compose under the strip in the same Column). |
| `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvSourceFeedScreen.kt` | One added strip argument: `lastGroupSub = feedViewModel.lastGroupSub()` (shared FeedViewModel already a screen parameter). Sub-row slots between the strip and the staggered grid; first-card `requestInitialFocus` unchanged. No TvMainActivity change (see Deviations). |
| `feature/feed/src/test/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedTabStripGroupTest.kt` | New minimal pure test: exact order-sensitive membership; exclusions (top7/ForYou/Favorites/Liked); tap-target in-group→current, out-of-group→last sub, default Trending; sub-row visibility iff group member; exact-match (top28 ≠ top, TopThisWeek identity). |

Unchanged (per design §6): `FeedSource.kt`, `FeedRepository.kt`, mediator/fetcher/paging
source, `TvMainActivity.kt`, mobile `MainActivity.kt`, `TvHomeScreen.kt`, Settings,
backup/export, DataStore, Room. No new FeedSource type, no new persistence, no endpoint
work; sub-row arrival order (strip → sub-row → grid) holds on both hosts, first-focus on
the first grid card unchanged.

## Grep verifications (requested)

1. **TopRanged on TV:** zero direct `FeedSource.TopRanged` references in `app-tv` — correct
   by design: the shared `FeedTabStrip` (in `:feature:feed`) renders the four subs, so no
   import is needed on the TV side; `TvSourceFeedScreen` only wires `source`/`onOpen`/`lastGroupSub`.
2. **More-menu leftover:** `FeedTabStrip.kt` contains zero `"Top This Week"`
   DropdownMenuItem text and zero `source is FeedSource.TopThisWeek` predicate terms; the
   string survives only in `FEED_GROUP` membership + comments + `FeedSource.title()`/sort
   options (canonical week-sub wording, untouched on purpose).
3. `FEED_GROUP`/helpers referenced only in `FeedTabStrip.kt`, `FeedViewModel.kt` (same
   module), and the new test — visibility is `internal` (same-module test + VM access;
   TV reaches the behavior through the shared composable, never the raw helpers).
4. Hygiene sweep of all 5 touched files: no lines >140 chars, no trailing whitespace,
   brace balance 0, no fully-qualified chains added, imports sorted (`Column`/`focusGroup`
   added lexicographically). No TODOs/placeholders; no GlobalScope; no new flows exposed.

## Deviations / notes

1. **Task text vs design doc on TV `lastGroupSub`:** the task brief says "TvMainActivity
   lastGroupSub passthrough (Root state, same lifespan as openFeed)"; DESIGN-tabs-stream §4/§6
   commits the TV memory inside the shared `FeedViewModel` (already activity-scoped via
   `hiltViewModel()` on TvMainActivity:187 — lifespan ≥ openFeed's plain `remember`) and lists
   `TvMainActivity.kt` as UNCHANGED. Implemented per the design doc (doc-first); no Root state added.
2. **`FeedTabStripTests` doc bullet "sorted variant of a group member":** group members are
   sortless identity sources (`withSort` returns `this`; no sortable member exists), so that
   case is exercised as plain current-source equality instead.
3. Helper visibility is `internal` (design doc left it unstated); nothing outside
   `:feature:feed` needs the raw helpers.
4. Blocked-file residue: pre-existing fully-qualified chains in `TvSourceFeedScreen.kt`
   (e.g. long FQ datastore names) predate this stream and were NOT touched (overlap rule).
   `FeedScreen.kt` retained its pre-existing FQ `FeedPrefs()` default collect — pre-existing,
   not introduced here.

## What was NOT done (out of stream scope, per design §7 gates)

- Device passes (Medium_Phone chip-row dump / TV36 D-pad grid→sub-row→strip traversal and
  ring checks) — coordinator gate, no emulator in this stream (instructions forbid adb).
- Gradle/ktlint/detekt runs — forbidden by stream instructions; the code was sized and
  traced against the existing strip structure instead. `detekt TooManyFunctions` headroom:
  file-level function count well under 41.

## Acceptance mapping (design §7)

1. Primary set For You (logged-in) / Feeds / Favorites / More ▾ — DOMINATED by strip render ✓ (both hosts via shared strip).
2. Feeds tap → sub-row Trending · Top This Week · Top This Month · Top of All Time, active selected, same `onOpen` seam ✓ (exact-equality selection; mobile in-place / TV full-page).
3. Feeds tap from non-group opens lastGroupSub (default Trending), lands in-group with sub-row ✓ (VM memory + tapTarget rule).
4. More: Top This Week removed from item + selected predicate; all other sections byte-identical ✓ (grep #2).
5. savedSort/baseKey/back()/§8 prefs/TTL/filter prefixes untouched ✓ (no source/pager/model edits).
6. Process death: no new persistence; strip non-group state falls out of the existing source lifecycle ✓ (design §3 rejected a DataStore key deliberately).
7. TV D-pad traversal: both rows `.focusGroup()`, sub-row compositional order = strip → sub-row → grid, first-card focus unchanged ✓ (render-order + design §5 guardrails; live focus-dump gate not run here).
8. Rate budget: strip render issues 0 requests; subs re-use existing cached pools (same TTL-path swaps) ✓ (pure render concept; no fetch/model change).
