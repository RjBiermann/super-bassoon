# DESIGN — issue #25 "navigation tabs and sub tabs" (stream: tabs)

Read-only design phase. No code changes, no builds. All file:line refs verified against
this jj workspace (`/workspaces/sturdy-potato/ws-tabs`, merged main + stream-C
FeedTabStrip extraction).

## 0. The ask

Issue #25: *"group trending, top of the week, … to one tab and with sub tabs"* — one
group tab whose selection reveals a row of subs underneath; primary chips shrink.
Applies mobile + TV (TV hosts the shared strip on every full feed already).

## 1. CURRENT layout (file:line)

### Mobile — FeedScreen (:feature:feed)

| Piece | Where |
|---|---|
| Strip call, `onOpen = viewModel.open(it)` in-place swap | `FeedScreen.kt:171-182` |
| Primary chips (ForYou/Trending/Favorites, ForYou filtered when logged out) | `FeedTabStrip.kt:73-84` |
| More ▾ chip + selected predicate (TopThisWeek/Liked/Custom/Niche/Creator) | `FeedTabStrip.kt:91-100` |
| More items: Top This Week `:102-107` · Liked `:110-115` · Following (screen seam) `:117-124` · Collections (screen seam) `:126-133` · Niches (screen seam) `:135-141` · custom feeds `:148-181` · Pinned section (niches + FAVORITED feeds + pinned creators) `:183-213` | `FeedTabStrip.kt:102-213` |
| BackHandler pop (`viewModel.back()`, one-level source history) | `FeedScreen.kt:76-79`, `FeedViewModel.kt:94-104` |
| Secondary chip rows already under the strip: search-creator strip, For-You scope row | `FeedScreen.kt:187-208`, `:223-247` (precedent: a second horizontal chip row under the strip is an existing, verified pattern) |

### TV — TvSourceFeedScreen (:app-tv)

| Piece | Where |
|---|---|
| Strip call, `onOpen = onSwitchFeed` (Root `openFeed` full-page swap) | `TvSourceFeedScreen.kt:230-241` |
| `onSwitchFeed = { openFeed = it }` | `TvMainActivity.kt:202` |
| `openFeed: FeedSource?` root state + BackHandler pop | `TvMainActivity.kt:103,120,130` |
| Column order today: title/icon row → **FeedTabStrip** → staggered grid | `TvSourceFeedScreen.kt:186-246` |
| TV home rows open the same sources full-page (Trending row `:175`, Top This Week row `:201`) | `TvHomeScreen.kt:175,:201` |

State model today: **one `FeedSource` is the whole tab state.** Mobile keeps it in
`FeedViewModel.mutableSource` (`FeedViewModel.kt:57-59`, seeded by
`FeedRepository.defaultLandingSource` `:278`, NOT saved across process death). TV keeps
it as `openFeed` at Root (plain `remember`, not saved). Sort/prefs machinery keys on
`FeedSource.baseKey` (`FeedSource.kt:22-24`, `FeedViewModel.kt` §8 flows) — the group
concept must not touch it.

## 2. Grouping decision — table + justification

All `FeedSource` variants (`FeedSource.kt:28-141`), each evaluated as a tab candidate:

| Source | Server feed? | Rate/cache cost | Group or top-level | Why |
|---|---|---|---|---|
| Trending | yes (`gif/search trending`) | cached pool, TTL 10 min (`TTL_TRENDING`) | **SUB** | The issue names it; same pool family as the others. |
| TopThisWeek (`trend:pop?order=top_week`, `FeedPageFetcher.kt:143`) | yes | cached pool, TTL 10 min | **SUB** | The issue names it. |
| TopRanged("top28") / ("top") (`gifs/search` bare, `FeedPageFetcher.kt:144-148`) | yes | cached pool, TTL 10 min | **SUBS** | Built by issue #11 exactly for this "day…all" family; currently only reachable from SearchScreen's "Top creators/ranged" row — moving week/month/all-time into visible tabs is a reachability win, zero new endpoint work. |
| TopRanged("top7") | yes | same | **NOT a sub** | Duplicate wording of TopThisWeek with a different endpoint; two "Top This Week" subs would confuse. TopThisWeek (site-nav order from More today) stays the canonical week sub. |
| ForYou | yes (network-live) | network-live, no Room cache | **TOP-LEVEL chip** (already) | Site home order: For You first. Its scope row (Creators·Niches·All) is a feed-local pref row, not a sub tab. |
| Favorites | no (client round-robin over Room favorited creators) | zero network | **TOP-LEVEL chip** (already) | Personal, not a browsing pool; issue asks it stays primary. |
| Liked | yes (network-live, logged-in) | **network-live, ttlMs=0** (`FeedSource.kt:129-133`) | **More, stays** | Live source: every open burns network; grouping it with cached pools hides its cost. Also logged-in-only — a sub-row that changes membership by auth state is churn. |
| Search | parametric | per-query pool | n/a | Its own screen surface; never a tab. |
| Niche | yes, per-niche pool | per-id pool, TTL 1 h | **More → Pinned, stays** | Open-ended set (like pinned creators); a chip row that grows with joins was already declared unusable and consolidated under More (`FeedTabStrip.kt:69-70` comment). Minimal churn: keep. |
| Creator / Custom | yes (round-robin fetch) | per-ref pools | **More → Pinned / Custom feeds, stays** | Same open-endedness + DB/state roles (FAVORITED feeds, BLOCKED filter). Custom's `state` machine and pinned entries stay untouched (see DESIGN-feeds-stream). |
| Following / Collections / Niches screens | — | — | **More, stays** | Screen seams (`onOpenFollowing/...`), not feed sources. |

**Committed group = `FEED_GROUP = [Trending, TopThisWeek, TopRanged("top28"), TopRanged("top")]`** — four cached server browsing pools, one endpoint family, zero rate-limit
behavior change (each sub keeps its own `keyBase` cache; strip render issues no requests;
sub switches are the same cache-first TTL-gated swaps that exist today; the ≤15 req / 5 s
invariant is untouched).

**Group chip label: "Feeds".** NOT "Explore" — AGENTS-APP lingo pins "Explore" = the
Top-Creators surface; reusing it for a browsing pool would violate the site-words rule.
"Feeds" is app-only wording (matches CustomFeedsScreen's "My feeds").

**Primary chips after: For You (logged-in) · Feeds · Favorites · More ▾.**

**More menu delta (the only churn):** remove the "Top This Week" DropdownMenuItem
(`FeedTabStrip.kt:102-107`) and drop `source is FeedSource.TopThisWeek` from the
More-selected predicate (`:93`). Everything else in More stays byte-identical.

## 3. State/robot decision (feeds the mobile + TV sections)

**No new `FeedSource` type. No group source. No wrapper.** The group is a *strip render
concept* over the existing singleton source:

- `FeedSource` identity, `keyBase`/`baseKey`, savedSort, §8 prefs, FeedMediator, back
  history, DeepRestore-equivalents — all untouched.
- Group membership + tap rule as **pure functions colocated in `FeedTabStrip.kt`**
  (testable without Compose), committed definition:

  ```kotlin
  val FEED_GROUP: List<FeedSource> =
      listOf(FeedSource.Trending, FeedSource.TopThisWeek,
             FeedSource.TopRanged("top28"), FeedSource.TopRanged("top"))
  /** The Feeds-chip tap target. */
  fun feedGroupTapTarget(current: FeedSource, lastGroupSub: FeedSource): FeedSource =
      if (current in FEED_GROUP) current else lastGroupSub
  ```

- **Sub-row persistence** = the source itself. Selecting a sub calls the existing
  `onOpen(sub)`; its selected state is exact `FeedSource` equality. "Remembered per
  group" falls out for free: while the user is on any group sub, the strip re-shows that
  sub selected. No new flow, no new DataStore key.
- **Last-sub memory for the group-chip tap** (tap "Feeds" while NOT in the group):
  one in-memory field in `FeedViewModel` —
  `private var lastGroupSub: FeedSource = FeedSource.Trending` + `fun lastGroupSub(): FeedSource`,
  updated in `FeedViewModel.open()` (single line: `if (feed in FEED_GROUP) lastGroupSub = next`)
  so sort adoption (`next = feed.withSort(saved)`) is what's remembered. Click-time read
  only → a plain var is correct (no StateFlow wiring, no composition read).
- **process death / DeepRestore:** mobile's feed source already dies with the process
  (VM re-seeds `defaultLandingSource`, `FeedViewModel.kt:57`); TV's `openFeed` is plain
  `remember`. The last-sub field deliberately has the SAME lifespan — relaunch re-enters
  via the seeded default and the strip renders the non-group primary set; nothing new to
  save, nothing to migrate. If source persistence is ever added (it is currently out of
  scope in both shells), it serializes the whole `FeedSource` and the group needs zero
  extra work. Explicitly rejecting a DataStore `last_group_sub` key today: it would
  outlive the source it must agree with.

**`FeedViewModel.open` gains nothing in signature** — one `open(FeedSource)` seam stays;
`gifs`, `back()`, `setSort` unchanged.

## 4. Mobile design

Changes confined to `FeedTabStrip.kt` (both apps get the behavior by construction) +
one-line VM wiring at each call site.

**`FeedTabStrip` restructure:**

- Outer `Row(horizontalScroll, :62-68)` → `Column { primaryRow; subRow? }`.
  - Primary row keeps the exact current content: ForYou/Trending/Favorites chips are
    replaced by `[ForYou?, Feeds, Favorites]` where the **Feeds chip** is a literal-label
    chip (`Text("Feeds")`), `selected = source in FEED_GROUP`,
    `onClick = { onOpen(feedGroupTapTarget(source, lastGroupSub)) }`. ForYou keeps its
    logged-out filter (site home order unchanged: For You first).
  - **Sub-row** (new): a second, INDEPENDENT horizontally-scrollable `Row`
    (`rememberScrollState()` per row — the single shared state must not scroll two rows)
    with `8.dp` spacing, same 12.dp horizontal padding, `FilterChip`s labeled
    `member.title()` (all four labels exist verbatim in `title()` today, `FeedSource.kt:150-167`),
    `selected = source == member}`, `onClick = { onOpen(member) }`.
  - Row rendered **iff `source in FEED_GROUP`** (AnimatedVisibility-free — plain `if`,
    matching the strip's current render rules; leaving/entering composition is the
    established pattern, e.g. mobile player text auto-hide).
- **New parameter:** `lastGroupSub: FeedSource = FeedSource.Trending` (plain value).
- **More menu:** delete the Top This Week item (`:102-107`); More-selected predicate
  `:91-100` drops TopThisWeek. All other sections byte-identical.
- A local `internal` helper `isInFeedGroup(source)` keeps call sites out of the constant.

**Call-site wiring (one expression each):**

- `FeedScreen.kt:171` → add `lastGroupSub = viewModel.lastGroupSub()`.
- `FeedViewModel.kt` → add the field + getter; `open()` gains
  `if (next in FEED_GROUP) lastGroupSub = next` (next = post-sort-adoption source,
  `FeedViewModel.kt:110-126`).
- No MainActivity changes (sort chips / search-creator rows / For-You scope row already
  compose under the strip inside the same `Column`, `FeedScreen.kt:187-247`; the sub-row
  slots above them — secondary rows appear only for their own sources, no overlap:
  FEED_GROUP members have no scope row and no creator chip row).

**Enter-always top bar, GiffyScaffold:** sub-row sits inside the same Scaffold column the
strip occupies (`FeedScreen.kt:155-186`); insets/nested-scroll behavior unchanged; the
strip is not part of the collapsing bar today, so a second row doesn't change collapse.

## 5. TV design

`TvSourceFeedScreen` hosts the shared strip (`TvSourceFeedScreen.kt:230-241`), so the
sub-row appears on EVERY TV full feed with the same rules. Changes:

- Pass `lastGroupSub = quickVm.lastGroupSub()` (the screen already holds the shared
  `feedViewModel` `FeedViewModel` at `TvSourceFeedScreen.kt:70-72`).
- `onSwitchFeed` semantics unchanged: sub tap → `openFeed = member` full-page swap
  (`TvMainActivity.kt:202`); Back still pops `openFeed` (no new stack entry — subs are
  feed values, not destinations).
- TV home rows (Trending/Top This Week `TvHomeScreen.kt:175,:201`) and TvHomeViewModel's
  TopThisWeek paging (`TvHomeViewModel.kt:31`) unaffected — home is a FeedRow browser,
  not a tab surface.
- TV title (`source.title()` at `TvSourceFeedScreen.kt:200`) keeps rendering the actual
  sub's title — group identity never surfaces in the title bar.

**D-pad traversal (committed order):**

Screen column = title/icons → strip row → **sub-row** → grid. Focus naturally climbs the
reverse spatial order from the grid's first card: **grid → sub-row chip → strip chip →
icons**. Two committed guardrails from the shipped chip-skip lessons
(`SettingsScreen.kt:244-247`, batch 15/16):

1. **Both chip rows get `.focusGroup()`** (strip row and sub-row) — beam geometry from a
   right-edge chip must not jump the row; horizontalScroll rows are the known skip
   surface, and the Settings fix (focusGroup + fillMaxWidth semantics) is the proven
   shape. The strip Row gains `.focusGroup()` without changing mobile rendering.
2. **No `requestInitialFocus` changes** — the strip/sub-row don't steal entry focus; the
   grid's first card keeps the on-open focus (`TvSourceFeedScreen.kt:113-117`). The
   initial focus search skips non-focusable-consumed parents and must not be re-anchored
   onto chips.

Escape-hatch from the failed shared-dialog ring finding (batch 15 #4) does NOT apply:
independent composables outside dialogs already traverse (the shipped strip is
D-pad-verified today); the added sub-row is the same chip family outside a popup. If a
ring check fails on M3 FilterChip on TV, the fallback is the shipped
`giffyFocus(interactionSource)` pattern — but it is not planned (no evidence FilterChip
needs it outside dialogs).

**CenterHold / MENU:** untouched — the sub-row is a plain click row; hold-Center
quick-actions and card behavior unaffected.

## 6. Changed functions / files (exact)

| File | Change |
|---|---|
| `feature/feed/FeedTabStrip.kt` | `FEED_GROUP`, `feedGroupTapTarget`, `isInFeedGroup` (new, internal, pure); outer Row → Column + new sub-row (params unchanged except `lastGroupSub`); primary chip list → ForYou/Feeds/Favorites; More menu: remove Top This Week item, drop it from the More-selected predicate |
| `feature/feed/FeedViewModel.kt` | `lastGroupSub` var + `lastGroupSub()` getter; `open()` gains the one-line "remember group sub" update |
| `feature/feed/FeedScreen.kt` | one added strip argument `lastGroupSub = viewModel.lastGroupSub()` |
| `app-tv/TvSourceFeedScreen.kt` | same one added strip argument via `feedViewModel.lastGroupSub()` |

Unchanged: `FeedSource.kt`, `FeedRepository.kt`, `FeedMediator`/`FeedPageFetcher`/
`FeedPagingSource`, `TvMainActivity.kt`, `MainActivity.kt` (mobile), `TvHomeScreen.kt`,
Settings, backup/export, DataStore, Room.

## 7. Acceptance criteria

1. Mobile strip renders For You (logged-in) / **Feeds** / Favorites / More ▾; no other
   primary chips. TV full-feed pages render the identical set (shared strip).
2. Selecting Feeds (from Trending-context) shows a sub-row: Trending · Top This Week ·
   Top This Month · Top of All Time; the active one is selected; tapping a sub swaps the
   feed in place (mobile) / full-page (TV), same seam as any chip today.
3. Tapping Feeds from a NON-group source opens the last-used group sub (default
   Trending), and lands inside the group with the sub-row exposed.
4. More ▾ no longer lists Top This Week; More no longer shows selected while a group sub
   is active; all other More entries and sections unchanged; pinned niches/creators/custom
   unchanged.
5. savedSort still adopts per base (`Niche`/`Creator`/`Search` unaffected — group subs are
   sortless identity sources); §8 prefs, back() one-level pop, TTL refresh, ContentFilter
   prefixes all behave exactly as before (leak-zero surfaces unchanged).
6. Process death: mobile relaunches to the seeded default (For You/Trending) with the
   strip in the non-group state — same as today's source lifecycle; no crash, no stranded
   sub-row.
7. TV: the sub-row is D-pad-reachable (grid → sub-row → strip) with visible focus
   treatment, chips scroll within the row, and CenterHold/MENU quick actions still work
   from grid cards.
8. Rate budget: strip render = 0 requests; sub switches re-use cached pools (TTL-gated),
   ≤15 req / 5 s invariant provable with existing tests (`RollingWindowRateLimiterTest`).

### Unit tests (pure logic, FeedTabStripTests-style — no Compose)

`feature/feed/src/test/kotlin/.../FeedTabStripGroupTest.kt` (new):

- `feedGroup_def` exact membership (order-sensitive: the four committed subs, and
  `TopRanged("top7")` and Liked/ForYou/Favorites exclusion).
- `tapTarget_insideGroup_returnsCurrent` (with a sorted variant of a group member to
  prove the remembered value is the post-adoption source).
- `tapTarget_outsideGroup_returnsLastSub`; default `Trending` when nothing used yet.
- `subRowVisibility`: visible iff group member; e.g. ForYou→hidden, Trending→visible.
- `selectedSub_exactMatch`: TopRanged("top28") ≠ TopRanged("top"); TopThisWeek exact.
- `groupDefs_loggedOut`: ForYou filtered, Feeds chip present (strip-level list rule kept
  as a pure function if extraction is needed).

### Device gates

- Mobile (Medium_Phone): chip row renders new set; Feeds tap → sub-row; sub switch swaps
  grid without restart; More menu parity; uiautomator dump over screenshot.
- TV (TV36): sub-row under the strip on a Niches-host full feed; UP traversal
  grid→sub-row→strip-order verified via focus dumps; hold-Center panel unaffected;
  refresh/sort icons still reachable above the strip.

## 8. Implementation order (grouped by file, size-sorted)

1. `FeedTabStrip.kt` — pure group helpers + Column restructure + sub-row + Feeds
   chip + More-menu trim (the whole behavior; compile green here).
2. `FeedViewModel.kt` — `lastGroupSub` field/getter + `open()` line.
3. `FeedScreen.kt` + `TvSourceFeedScreen.kt` — pass the argument (two one-line edits).
4. Tests (`FeedTabStripGroupTest.kt`) — can land with step 1.
5. Gates: compile + ktlint + detekt + unit tests on `:feature:feed` and `:app-tv`; then
   the two device passes above.

No migrations, no version bumps, no backup-version change, no new dependencies, no
endpoint work — every sub endpoint is live-verified already (`FeedSource.kt:60-77`,
`FeedPageFetcher.kt:143-148`).
