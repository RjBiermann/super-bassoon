# DESIGN — TV stream flow (GitHub issues #14, #15, #19)

Read-only design phase. All evidence from this checkout of `super-bassoon`
(jj rev, 2026-10-04). One design doc, no code changes.
Evidence base: app-tv shell (10 files), shared `:feature:feed`/`:feature:search`,
`:core:datastore`, plus the AGENTS-*.md spec sections referenced per issue.

---

## Issue #14 — Remove the zoomed preview on TV (+ feed size pref + focused-row centering)

### Reconciliation decision (read with #15 first)

#15 separately asks to ADD preview to creator/niche feeds, and FocusPreview is a
graduated, device-verified feature (AGENTS.md batch 18 canonical record;
AGENTS-APP.md "TV preview-on-focus + show-more panel"). Previews are wanted —
so "remove the zoomed preview" does NOT mean "remove FocusPreview".
The reconciled intent: **no more zoomed/cropped thumbnail display; true-aspect
content instead** — remove the zoom/crop *framing*, not the preview player.
This doc designs that; the literal "remove previews" reading is recorded here
as considered-and-rejected (it would contradict #15 and the batch-18 graduation).

### 14.1 Current behavior (file:line evidence)

- **Home rows (FeedRow / Continue Watching):** the tile box is
  `fillMaxWidth().height(rowHeight)` with `ContentScale.Crop`
  (TvHomeScreen.kt:610-620). Width comes from `cardWidth(gif, rowHeight)`
  (TvHomeScreen.kt:502-508): aspect-derived but **clamped** — portrait floor
  `coerceAtLeast(120.dp)`, landscape cap `coerceAtMost(280.dp)`. A standard
  9:16 portrait gif at the 170dp default band is 170 × 0.5625 ≈ **95.6dp wide →
  clamped to 120dp** → box ratio 0.71 vs content 0.56 → horizontal crop =
  visible zoom on a large fraction of the pool (the pool is mostly portrait
  per AGENTS-APP.md "Orientation filter": vertical-only is a dead end on TV).
  XL height (306dp) escapes the portrait clamp; the default does not.
- **Preview surface:** `focus_preview_player.xml` uses `resize_mode="zoom"`
  (app-tv/src/main/res/layout/focus_preview_player.xml:25) — fill+center-crop,
  deliberately matched to the poster's `ContentScale.Crop` so the settle swap
  doesn't jump (T4 poster-under-surface, T6 no-jump; batch 22). Correct AT
  THE TIME — but it means the preview displays the same cropped, zoomed
  picture the static tile does. The tile paints an avgColor background
  (`avgColorOr`, TvHomeScreen.kt:611-614), so letterbox bars can be rendered
  nearly invisible.
- **Mobile reference:** mobile home tiles are true aspect —
  `aspectRatio(gif.width/height)` box (GifTile, FeedScreen.kt:801-807); Crop
  inside an exactly-matched box is a no-op (FeedScreen.kt:821).
- **Focused-row centering: none today.** Every scrolling row uses a fixed 16dp
  horizontal contentPadding (TvHomeScreen.kt:239 = Continue-Watching,
  :305 = FeedRow, :393 = CreatorRow) inside the 48/27dp overscan margin
  (TvMainActivity.kt:158). Focus moves scroll rows minimally; the focused
  card stays wherever focus momentum leaves it — left-anchored at row start,
  flush against the right viewport edge at row end. The focused card scales
  1.08 (TvHomeScreen.kt:531, graphicsLayer :562-564), so a card at the last
  visible position draws ~11dp past the LazyRow viewport edge — lazy lists
  clip to bounds, so **right-edge clipping of the focused card is observable
  today**; the left-position part of the ask is cosmetic-centering.
- **Feed size in Settings: ALREADY SHIPPED.** `tv_row_height` DataStore key
  (SettingsRepository.kt:45, flow :176-180, setter :179-181), Settings section
  gated by `showTvRowHeight` with Default/Large/XL chips (SettingsScreen.kt:299-330;
  SettingsViewModel.kt:139-143), pure mapping `rowHeightFor`
  (TvHomeScreen.kt:487-493) → 170 / 238 (×1.4) / 306dp (×1.8), consumed at
  TvHomeScreen.kt:87 and passed through FeedRow/GifCard
  (rowHeight params, TvHomeScreen.kt:352, :527). Reuse, do not duplicate.
  Its one real gap: TvSourceFeedScreen's grid tiles never receive rowHeight —
  GifCard is called bare (TvSourceFeedScreen.kt:154-159) → constant 170dp.
  That gap is folded into #15's redesign below.

Root causes, named: (a) crop-zoom comes from the fixed-dp clamp floor/cap on
an exactly-rowHeight-fixed box rendered with `ContentScale.Crop`, mirrored by
PreviewView's `resize_mode=zoom` — every clamped box materially crops content;
(b) centering: nobody forwards the focus position into the row's
LazyListState.

### 14.2 Proposed minimal change

**A. Kill the zoomed/crop framing on home rows (keep the row-band layout).**

1. `cardWidth` (TvHomeScreen.kt:502-508) — make the clamps relative to the
   band instead of flat dp: portrait floor = `rowHeight * 0.5f`, landscape cap
   = `rowHeight * 1.65f` (same intent as today's 120/280 at the default
   height — cap ratio 280/170 ≈ 1.65). Standard 9:16 portraits (~0.56 aspect)
   then render EXACT (95.6dp at default) at every pref step instead of being
   cropped at default. Pure function + pure mapping → directly unit-testable.
2. GifCard poster `ContentScale.Crop` → `ContentScale.Fit`
   (TvHomeScreen.kt:617). The avgColor background stays, so the (now rare,
   extreme-aspect) clamped tile letterboxes in-color instead of cropping.
   Exact-ratio boxes are visually unchanged (Fit == Crop at matched ratios).
3. `focus_preview_player.xml:25` `resize_mode="zoom"` → `"fit"`. The video now
   letterboxes identically to the poster in clamped tiles (settle-swap stays
   aligned, T4/T6 preserved); in exact-ratio tiles fit == zoom, no delta.

Result: home tiles AND the settled preview show the whole frame; no
zoom/crop anywhere; poster↔video alignment preserved at the swap.

**B. Focused-row horizontal centering, no clipping.**

- New pure helper beside cardWidth in TvHomeScreen.kt (same file — no new
  files for one function; only split if detekt angles for it):

  ```kotlin
  /** Scroll offset (px) that centers an item of [itemWidthPx] in a viewport of
   *  [viewportPx] with [leadingPaddingPx]; null = already centered (deadband,
   *  ~±48dp, so small focus steps don't jitter). */
  internal fun centeredScrollOffset(
      viewportPx: Int,
      itemWidthPx: Int,
      leadingPaddingPx: Int,
  ): Int?
  ```

- Wiring per scrolling row (`FeedRow` + Continue Watching; CreatorRow optional
  second pass — its cards are uniform):
  - Row gains `rememberLazyListState()`, a `focusedIndex: Int?` state, and
    viewport width via `onSizeChanged` on the LazyRow.
  - GifCard gains `onFocusChange: (Boolean) -> Unit = {}` (one-line param, fed
    from the existing `onFocusChanged` at TvHomeScreen.kt:565).
  - `LaunchedEffect(focusedIndex)`: `scrollToItem(index, offset)` where offset
    comes from the pure helper. Item widths are deterministic already —
    `cardWidth` is the single width source — so the math needs no measured
    item states; negative offsets pull an early card right to center.
    Use `scrollToItem` (instant), not `animateScrollToItem`: D-pad walking
    must stay snappy; centering must not add per-step animation lag.
  - Only the row the focus moved in reacts; the vertical LazyColumn and row
    heights are untouched (centering scrolls horizontally only) — up/down
    traversal behavior unchanged.

**C. Feed size in Settings — reuse, no duplication.**

- Home feed: no new pref, no new code — `tv_row_height` already ships it
  (evidence above). Add nothing; verify it survives the framing change.
- Source-feed pages (optional, rides #15's file touch): thread the same pref
  into TvSourceFeedScreen and map default/large/xl → 4/3/2 grid columns
  (`columnsFor(pref)` pure mapping + one collect). Flagged OPTIONAL — it
  smuggles a column-count meaning under a "row height" label; if applied,
  the Settings copy ("TV home row size") should widen to "TV feed size".
  Needs the user's go/no-go before build.

### 14.3 Acceptance criteria

- Any gif: the home tile AND its settled preview show the full frame — no
  center-crop (screencap pair: the preview's first frame framed identically
  to the poster).
- Unit: `cardWidth` — 9:16 portrait at default height → 95.6dp (not 120dp);
  at xl (306dp) → 172dp; extreme 0.35 portrait → the relative floor + Fit
  letterbox; landscape super-wide → the relative cap + Fit.
- Focus RIGHT across the last visible card: card fully inside the row
  viewport (no clipped edge); after walking, the focused card recenters
  horizontally (uiautomator dump: focused node's center within the
  viewport-center band).
- Vertical navigation (UP/DOWN between rows) does NOT trigger row
  recentering; each row's scroll position stays where its own focus left it.
- Settings → TV home row size → Large/XL still applies to home rows
  (regression proof that no pref was duplicated/diverged).
- `FocusPreviewTest` untouched and green; the T5 late-un-settle guard is
  unaffected (this change is framing-only).

### 14.4 Risk / notes

- Fit letterbox changes the tile look for extreme aspects (bars instead of
  crop). avgColor keeps bars near-invisible; device-verify the bar-vs-poster
  contrast, including AMOLED.
- `scrollToItem` with negative offsets can re-anchor during paging prepend
  (load-state churn uses "pending{i}" keys, TvHomeScreen.kt:395-401). Cheap
  guard: skip centering while `loadState.refresh is Loading`, recompute after
  settle. Name the exact trigger at build time by proving it, not pre-coding
  a blind condition.
- Device verify on TV36 (required): centering walk (home Right×10), crop
  removal before/after screencaps, and no regression to hold-Center panel /
  short-tap open / MENU quick actions (the GifCard keymap code path is
  untouched by design — verify it anyway).

---

## Issue #15 — True-aspect multi-column feed on creator/niche pages (+ focus preview)

### Current behavior (file:line evidence)

- Creator/niche full feeds open through the Root `openFeed != null` branch
  (TvMainActivity.kt:185-201) → `TvSourceFeedScreen`
  (app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvSourceFeedScreen.kt).
  Grid: `LazyVerticalGrid(columns = GridCells.Fixed(4))`
  (TvSourceFeedScreen.kt:146-148, contentPadding 16dp :148, title clearance
  `padding(top = 44.dp)` :147), items `GifCard(.., Modifier.fillMaxWidth())`
  (:154-159) — GifCard called WITHOUT rowHeight → the tile box is forced to
  `fillMaxWidth() × 170dp` (CARD_ROW_HEIGHT_DP default, TvHomeScreen.kt:527,
  box :610-613) with `ContentScale.Crop` (:617). On the ~864dp content
  viewport (1920px panel minus the 48dp overscan ×2) that is ~200×170dp
  tiles: portrait content is center-cropped into a near-square shape — the
  user-visible "square tiles" of this issue.
- **Preview EXISTS on these feeds today** (FocusPreview instance
  TvSourceFeedScreen.kt:68-84, passed into GifCard :154-167) — the gap is
  framing (crop-zoom into a square box), not preview machinery. Same root
  cause as #14 §A, amplified; Issue #15's "without preview" should be read as
  "the preview shows an unrecognizable cropped crop of portrait content".
- Mobile reference: `LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(cols))`
  (FeedScreen.kt:697-708) with per-item aspect-correct tiles
  (GifTile, FeedScreen.kt:781-830, `aspectRatio` at :807) — true aspect for
  any pool mix, variable heights, no dead gaps.
- Scroll state: `feedGridState` remembered at Root as `rememberLazyGridState()`
  (TvMainActivity.kt:155) and threaded through the screen (param
  :41-43, used :147) — this is the batch-22 "back-from-player keeps grid
  position" seam and must survive the redesign as a staggered-grid state.

Root cause / gap: TV source feeds borrow the home-row card (fixed height +
crop, width derived from aspect) but render it fillMaxWidth in a Fixed-count
grid — the aspect math that shapes home tiles is discarded; portrait content
is cropped to ~/200×170. A layout gap, not a preview gap.

### Proposed minimal change

1. **Grid → staggered grid.** TvSourceFeedScreen `LazyVerticalGrid` →
   `LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(4))`, same content
   padding (16dp), spacing (10dp), and `top = 44.dp` title clearance; the
   title/stats Row and the shared Filter chip (:125-141) are untouched.
   Type fallout (the only churn): `gridState` param + Root `feedGridState`
   change `LazyGridState` → `LazyStaggeredGridState` — 3 touch points
   (TvSourceFeedScreen.kt:41-43 + 146-148; TvMainActivity.kt:155). Staggered
   is what makes per-item aspect heights line up without dead gaps, exactly
   like mobile.
2. **True-aspect tile via one GifCard parameter** (`trueAspect: Boolean =
   false`, added at TvHomeScreen.kt:518-527): when true, the tile Box uses
   `Modifier.aspectRatio(gif.width.toFloat() / gif.height)` (fallback 0.8f —
   same fallback as mobile GifTile, FeedScreen.kt:807) instead of
   `.height(rowHeight)`, and the caller no longer passes a fixed width
   (grid item keeps `fillMaxWidth()`). Everything else on the card — avgColor
   bg, poster Fit (after #14 §A), AudioBadge, CreatorLabel caption, 1.08
   focus scale, CenterHold keymap, FocusPreview overlay — is untouched:
   FocusPreview binds on `settledId` against the same card regardless of
   geometry. Home rows pass nothing (default false) — home layout identical.
3. **Preview plumbing unchanged.** The existing FocusPreview in
   TvSourceFeedScreen (68-84) starts showing recognizable frames because the
   box now matches content aspect; with `resize_mode=fit` (after #14 §A) the
   poster↔video swap stays aligned.
4. **Sizing (optional, #14 §C):** column count from the same
   `tv_row_height` pref (default/large/xl → 4/3/2). Only with the user's
   go/no-go; without it the grid stays Fixed(4).

Why not reuse mobile's GifTile directly: it is private to FeedScreen and binds
mobile's own surfaces (inlinePlayer shared player, QuickBlockSheet, a11y for
touch). One parameter on the TV card reuses the D-pad card — focus keymap,
preview, badge, caption — rather than reimplementing them; strictly less churn
than any extraction, and TV remains the owner of its own D-pad card.

### Acceptance criteria

- Creator feed and niche feed render true-aspect tiles (aspect from each
  gif) — portrait gifs are no longer cropped: zoomed screencap shows full
  frames; column heights vary (staggered); no large dead gaps in the initial
  viewport.
- Focus preview on source-feed grids still: appears after the 600ms dwell on
  the settled card, follows focus, clears on unfocus and on data-saver
  engagement, and shows UNCROPPED video (pixel framing ≈ poster framing).
- Initial focus lands on the first grid card (FocusRequester stays on the
  first item, TvSourceFeedScreen.kt:130-144).
- D-pad traversal across the staggered grid is clean (UP/DOWN hops between
  ragged rows never trap or skip a column — THE device gate of this issue).
  One historical precedent to keep in mind: the AGENTS.md Phase-7 record
  "TvLazyColumn prefetch crash → moved to compose LazyColumn" involved
  tv-material's LazyColumn, not the compose-foundation lazy staggered grid
  this design uses; still, verify by build, and if an analogous trap shows,
  the documented fallback keeps acceptance: keep `LazyVerticalGrid`
  Fixed(4) with true-aspect tiles (uneven per-row whitespace, still true
  aspect).
- BACK from the player returns to the same grid position (hoisted staggered
  state — same seam as today).
- MENU / hold-Center quick actions still work on grid cards (GifCard keymap
  untouched).
- `TvNicheFeedViewModel` and the paging sources untouched; zero rate-limit
  surface change; no new endpoints.

### Risk / notes

- `LazyStaggeredGridState` at Root must be committed atomically with the
  screen change or BACK-position parity (batch-22 gate) breaks.
- Focus behavior in ragged rows is the highest-risk part — device gate above
  (TV36) before calling this done.
- The optional column ladder needs a user go/no-go (see #14 §C).

---

## Issue #19 — TV tabs on the creator (and niche) page, same tabs as mobile

### Current behavior (file:line evidence)

- TV full feeds (TvSourceFeedScreen) have NO tab strip: title + creator stats
  (TvSourceFeedScreen.kt:100-123), one Filter chip TopEnd (:125-141), grid
  (:145-167). The only paths into another feed are HOME surfaces (home rows,
  the top pill row, the Niches browser, the More ▾ menu —
  TvMainActivity.kt:332-370): from inside a creator page you must BACK to
  home first. Nothing in a full-feed page switches feeds in place.
- Mobile: EVERY source page — including Creator and Niche — renders the chips
  tab row inside FeedScreen: fixed primary chips For You (logged-in only) ·
  Trending · Favorites (FeedScreen.kt:184-205, `viewModel.open(candidate)
  ` — in-place swap via FeedViewModel.open, FeedViewModel.kt:154) + a
  "More ▾" chip whose DropdownMenu covers every secondary surface: Surprise /
  Continue Watching / Top This Week / Explore / Liked (logged-in) /
  collections / pinned niche tabs / pinned creators / custom feeds
  (FeedScreen.kt:204-380).
- The issue phrase "in the TV creator page the feeds are not rows" reads
  against this evidence: on TV a full feed offers no first-level feed
  switcher at all. The actionable spec is the explicit one: "tabs are the
  same as mobile tabs" and "switching a tab should open the feed like a
  creator/niche feed opens in TV" — i.e. the switch must drive the TV
  `openFeed` full-page path (TvMainActivity.kt:185-201), NOT an in-place
  mobile-style pager swap.

Root cause: the mobile tab strip is an inline block inside FeedScreen's body,
not an extracted composable, and the TV source-feed shell predates it — TV
never got the seam, and no other TV screen offers a first-level feed
switcher on itself.

### Proposed minimal change

1. **Extract the tab strip into a shared composable** in `:feature:feed`
   (new file, e.g. `FeedTabStrip.kt`): the chips Row (3 primary chips +
   More ▾ DropdownMenu) moved verbatim out of FeedScreen.kt:168-380 with two
   seams:
   - `onOpen: (FeedSource) -> Unit` replaces every direct `viewModel.open(...)`
     — mobile wire-through `{ viewModel.open(it) }` (zero behavior delta);
     TV wire-through opens full-page (step 3).
   - `onOpenExplore: () -> Unit` preserved (mobile routes Explore to its own
     screen, not an opens).
   State inputs stay plain parameters, collected by the CALLER from
   FeedViewModel — `isLoggedIn` (FeedViewModel.kt:189) and the existing flows
   customFeeds (:284), pinnedNiches (:306), favoritedFeeds (:430),
   pinnedCreators (:434) — so the strip keeps zero VM coupling and the
   labels/logic stay single-sourced ("same as mobile tabs" by construction,
   not by copy).
2. **FeedScreen swap-in.** The extracted block is replaced by one call to the
   shared strip; the selected-state logic (which chip is marked selected,
   including the More-chip marker for the open source) moves with it.
   Mobile behavior must be byte-equivalent after this (regression-gated).
3. **TV hosting.** TvSourceFeedScreen renders the shared strip under the
   title/stats row (full-width, horizontally scrollable like mobile, 16dp
   horizontal padding), with a new param
   `onSwitchFeed: (FeedSource) -> Unit = {}` used as the strip's `onOpen`;
   TvMainActivity's `openFeed != null` branch wires it as
   `onSwitchFeed = { openFeed = it }` — the exact "creator/niche feed opens"
   full-page path the issue names.
4. **Scope conclusion (the parity question):** yes, the same pattern applies
   to niche pages automatically — niche, creator, search, custom and
  Favorites all land in the same TvSourceFeedScreen, so the strip appears on
   ALL TV full feeds, matching mobile where the same FeedScreen hosts all
   sources. No niche-specific variant, no new screen.

### TV-specific D-pad notes

- Chips: the same FilterChip + `giffyFocus` pattern already proven on TV —
  ForYouScopeRow (TvHomeScreen.kt:436-467) and the Settings chip-skip fix
  (focusGroup + giffyFocus per chip; AGENTS-APP.md). The strip's LazyRow chips
  traverse horizontally with standard focus search; keep the dead-end rule
  (a wrapped chip row must never strand focus).
- DropdownMenu on TV: the strip's More menu mirrors Root's "More ▾"
  (TvMainActivity.kt:354-370), already device-verified (AGENTS.md batch-5/14
  notes: M3 popup rows render a visible focus fill — no extra focus work).
- First-focus: TvSourceFeedScreen currently puts the FocusRequester on the
  first GRID card (130-144). KEEP that: entry focus goes to the grid; UP from
  the first card naturally reaches the tab strip. Verify on device that the
  first DOWN/UP walk stays sane with the strip between title and grid.
- BACK semantics: switching a tab replaces `openFeed` — today's BackHandler
  (TvMainActivity.kt:129-146) pops to home. BACK from a tab-switched feed
  will NOT return to the previous feed (Root has no feed stack). If
  "back to previous feed" is wanted, that is a Root stack change — out of
  scope here, flagged as an open question for the user.

### Acceptance criteria

- On the TV creator page: the tab strip is visible with the same labels and
  the same fixed set as mobile (For You when signed in only, Trending,
  Favorites, More ▾ with the same secondary list incl. the signed-in-gated
  Liked entry). Label parity is a compile-time consequence of the extraction.
- Selecting any tab (D-pad + Center) swaps the full feed via the openFeed
  path — the new page's title/stats/grid reflect the new source (uiautomator
  title text is the cheap check).
- Same on niche pages (and search/custom full-feeds) — one pattern.
- Mobile FeedScreen behavior unchanged (chip set, More menu, selected marker,
  in-place opens) — gate: compile + ktlint + detekt + unit on `:feature:feed`
  + a mobile feed open/close smoke run.
- BACK, hold-Center quick actions, initial-focus-on-first-grid-card unchanged.
- No new endpoints; no rate-limit surface change (tab switches reuse the
  same pagers mobile already opens per source; FetchSources page fetches go
  through the same ≤15req/5s limiter infrastructure with nothing new).

### Risk / notes

- The extraction is the ONE deliberate shared-code move in this doc — it is
  the responsible-layer point: a second TV copy of the tab lists would drift
  from the pin-to-tabs language rules (AGENTS-APP.md lingo table). Build gate:
  ktlint + detekt + unit + both apps compile + one mobile smoke.
- If the user wants tabs ONLY on creator pages (not niche/search/full-feeds),
  the strip stays shared but TvMainActivity gates `onSwitchFeed` per source —
  carried as a fallback, not the base plan.
- No device emulator requirement is waived: tab-strip focus walk + switch +
  INITIAL focus + BACK are the TV36 gates.

---

## Implementation order (grouped by file, smallest blast radius first)

Phase A — framing (#14 §A + §C verify):
1. `TvHomeScreen.kt` — cardWidth relative clamps (+ unit tests);
   GifCard poster `ContentScale.Fit`; (add `onFocusChange` param here only if
   Phase C is same-commit, else add in Phase C).
2. `app-tv/src/main/res/layout/focus_preview_player.xml` — `resize_mode="fit"`.
3. `:app-tv` unit tests: cardWidth mapping (and centering helper in Phase C).
4. Device gate TV36: crop-removal screencap pair; AMOLED bar check.

Phase B — source-feed grid (#15; rides Phase A's GifCard param):
5. `TvHomeScreen.kt` — GifCard `trueAspect` param.
6. `TvSourceFeedScreen.kt` — staggered grid + trueAspect call-site + state
   type.
7. `TvMainActivity.kt` — `feedGridState` type change (same commit as 6).
8. Device gate TV36: aspect tiles, preview framing, staggered traversal
   (fallback documented above).

Phase C — centering (#14 §B):
9. `TvHomeScreen.kt` — `centeredScrollOffset` pure fn + test; FeedRow /
   Continue-Watching wiring (focusedIndex + scrollToItem + viewport size);
   consume the GifCard `onFocusChange` param.
10. Device gate TV36: Right×10 walk (no clipping, recentering); UP/DOWN
    row-switch never recenters; paging-prepend sanity.

Phase D — tabs (#19):
11. `:feature:feed` — extract `FeedTabStrip.kt` (verbatim move + two seams).
12. `FeedScreen.kt` — swap-in (mobile no-behavior-delta gate).
13. `TvSourceFeedScreen.kt` — host strip + `onSwitchFeed` param.
14. `TvMainActivity.kt` — wire `onSwitchFeed = { openFeed = it }`.
15. Gates: compile + ktlint + detekt + unit on `:feature:feed` + `:app-tv`;
    device gate TV36: strip walk, tab switch on creator AND niche pages,
    initial focus unchanged, BACK to home.
16. (OPTIONAL, user go/no-go) `TvSourceFeedScreen.kt` — `tv_row_height` →
    column ladder (default/large/xl → 4/3/2); if applied, widen the
    SettingsScreen copy to "TV feed size".

Device passes may be consolidated into one TV36 session; the per-issue
acceptance lists above remain the gates.

Consolidated device-verify list (TV36):
- A: crop removal (before/after screencaps); no clipped edge on last-visible
  card walk; AMOLED letterbox-bar contrast.
- B: aspect grid + preview framing + staggered D-pad traversal.
- C: centering walk + vertical row stability + paging prepend.
- D: tab-strip label parity + switch (creator + niche) + initial focus + BACK.
- Regression spot-checks: hold-Center panels, MENU panels, first-card focus,
  back-position on every surface, watch-history writes unchanged.

Out of scope (parked/decided elsewhere): no new endpoints; no per-feed §8
chips on TV (the Filter chip already hosts the shared dialog); no feed BACK
stack (flagged in #19 for the tab-switch case); RateLimitBus indicator
(decided skip); nothing here touches auth, player internals, or the network
stack.
