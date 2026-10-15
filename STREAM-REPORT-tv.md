# STREAM-REPORT — TV surfaces (#14 crop removal + centering, #15 true-aspect staggered feed, #19 shared tab strip)

Worker: TV-SURFACES stream. Worktree: /workspaces/sturdy-potato/ws-tv (merged main with streams A+B).
Scope honored: only files named by DESIGN-tv-stream.md were touched. No gradle/ktlint/detekt/adb/jj/git run (per instructions — cheap greps/finds only).

## What landed

### Phase A — crop removal (#14 §A)
- `app-tv/.../tv/TvHomeScreen.kt`
  - `cardWidth()` clamps made RELATIVE to the band: portrait floor `rowHeight * 0.5f` (was flat 120dp), landscape cap / unknown-aspect default `rowHeight * 1.65f` (was flat 280dp). The common 9:16 pool now renders EXACT at every `tv_row_height` step (170dp default → 95.625dp, xl 306dp → 172.125dp) instead of being center-cropped at the default.
  - `GifCard` poster `ContentScale.Crop` → `ContentScale.Fit`; poster modifier → `matchParentSize()` (fills the box in both row-band and true-aspect geometry; identical in row mode). avgColor background kept, so clamped/extreme tiles letterbox in-color.
- `app-tv/src/main/res/layout/focus_preview_player.xml`: `resize_mode="zoom"` → `"fit"` (+ header comment updated). Poster↔preview alignment preserved by construction (both letterbox the same way).

### Phase B — true-aspect staggered source feed (#15)
- `TvHomeScreen.kt`: `GifCard` gains `trueAspect: Boolean = false` — when true the tile box is `Modifier.aspectRatio(width/height)` (fallback 0.8f, same as mobile GifTile) instead of `.height(rowHeight)`. Home rows pass nothing → home layout identical. FocusPreview overlay, CenterHold keymap, 1.08 scale, AudioBadge, caption untouched.
- `app-tv/.../tv/TvSourceFeedScreen.kt`: `LazyVerticalGrid(Fixed(4))` → `LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(4))`, same 16dp contentPadding and 10dp spacing (`verticalItemSpacing = 10.dp` replaces verticalArrangement); end-of-feed footer span `GridItemSpan(maxLineSpan)` → `StaggeredGridItemSpan.FullLine`; grid items call `GifCard(trueAspect = true)`.
- `TvMainActivity.kt`: Root `feedGridState` `rememberLazyGridState()` → `rememberLazyStaggeredGridState()` (same commit — the batch-22 BACK-position seam stays hoisted at Root).

### Phase C — focused-row horizontal centering (#14 §B)
- New `app-tv/.../tv/RowCentering.kt` (same module, beside TvHomeScreen content):
  - `centeredScrollOffset(viewportPx, itemWidthPx, leadingPaddingPx, deadbandPx): Int?` — pure; null inside the deadband (±48dp at call sites).
  - `rememberCenteringRow(loading)` — one remembered `LazyListState` + focused-index state; on every focus change (and again when a loading row settles — the loading flag is the effect key) the focused card is instantly recentered via `scrollToItem` (no animation). The loss branch only clears its own index, so focus moves between sibling cards fire in either order without clobbering the newly focused card. Only the row the focus moved in reacts — vertical traversal never recenters.
  - Split into its own file deliberately: detekt `TooManyFunctions` file threshold is 13 and TvHomeScreen would sit at 14 with the split guardrails the design doc itself names ("only split if detekt angles for it") — it did.
- Wiring: `FeedRow` (paging-loading guard: centering skipped while `loadState.refresh is Loading`, recomputed on settle) and `CreatorRow` both consume it; `GifCard` gained the one-line `onFocusChange: (Boolean) -> Unit = {}` fed from the existing `onFocusChanged`.

### Phase D — shared tab strip (#19)
- New `feature/feed/.../FeedTabStrip.kt`: the mobile FeedScreen tab block (For You-when-logged-in · Trending · Favorites + More ▾ dropdown) moved VERBATIM behind seams — `onOpen: (FeedSource) -> Unit` replaces every `viewModel.open(...)`; the screen seams (`Explore/Following/Niches/Collections/CustomFeeds`) are nullable and their rows are HIDDEN when null. State inputs are plain parameters (caller collects from its own FeedViewModel); pinned-niche "id|name" parsing stays inside the strip (single-sourced). NO Surprise seam — that feature was removed from main (#12) and is absent everywhere.
- `FeedScreen.kt` swap-in: one `FeedTabStrip(...)` call; mobile passes EVERY seam non-null and `onOpen = { viewModel.open(it) }` → zero behavior delta; the flow collections (`pinnedNiches/favoritedFeeds/pinnedCreators/customFeeds`) moved from the inline block into the caller; now-unused imports dropped (`DropdownMenu/DropdownMenuItem/HorizontalDivider/Feature`).
- `TvSourceFeedScreen.kt` hosts the strip under the title/stats header with new params `onSwitchFeed` (used as `onOpen`) + `onOpenNiches/onOpenCollections/onOpenCustomFeeds` (when called, the browser opens). It collects the same five states from the shared `FeedViewModel` it already owns — zero VM coupling added.
- `TvMainActivity.kt` wiring in the `openFeed` branch: `onSwitchFeed = { openFeed = it }` (the exact full-page path the issue names); Niches/Collections/My feeds clear `openFeed` first so the Root browser branch composes and BACK pops correctly. Explore/Following screens have no TV host today → not even threaded (hidden). Per the decided rule, `Feature.CUSTOM_FEEDS = false` keeps those rows dead code without any new gating.
- TV layout host note: the strip needs vertical room, so the title/stats + TopEnd Refresh/Sort icons are one header Box and the grid flows below the strip (the old fixed `top = 44.dp` title clearance is gone). Everything else (filter dialog, quick actions, footer, `refreshAndReturnToTop`) is untouched.

### Skipped per instruction
- Phase 14C optional `tv_row_height` → column ladder: SKIPPED (recorded; Settings copy unchanged).

## Unit tests added (plain JUnit4, same suite as TvRowHeightTest et al.)
- `app-tv/src/test/.../CardWidthTest.kt` — 9:16 exact at default/large/xl, extreme 0.35 portrait → relative floor, super-wide + normal landscape → relative cap, unknown dims → band-relative landscape default.
- `app-tv/src/test/.../CenteredScrollOffsetTest.kt` — centering math, exact-boundary deadband null, negative offset (pull right), degenerate inputs null.

## Verification performed (no gradle, per instructions)
- Brace-balance script over all 5 touched Kotlin files → depth 0 each.
- `.editorconfig`-style sweeps: no lines >140 cols, no trailing whitespace in any touched file.
Tree greps: no `FeedSource.Continue*`/`Surprise*`/`RefreshFeedPill` references anywhere; no `lazy.grid`/`LazyVerticalGrid` leftovers in app-tv; `Feature.CUSTOM_FEEDS` pattern consistent.
- Import-usage greps after the FeedScreen swap and the RowCentering split (no unused/missing imports per eyeball + grep; NOTE: full compile will be the true gate).

## Parity deviations (recorded per instructions)
1. **Explore/Following hidden on TV** — strip rows hidden via null seams; TV never threads those callbacks at all (decided rule).
2. **Niches/Collections/CustomFeeds wired to Root browser states**, clearing `openFeed` so the browser branch composes (decided rule — recorded here as the parity deviation: on mobile these open dedicated screens; on TV they open the same shared screens hosted full-page by Root).
3. **No Surprise seam anywhere** (feature removed from main).
4. Strip chips are plain M3 `FilterChip`s (no `giffyFocus` added) to keep the mobile swap byte-equivalent; TV D-pad focus relies on standard M3 focus handling (the Root "More ▾" menu precedent). If the first TV36 focus walk shows the chip row needs the shared ring, that is a follow-up device-gate slice, not silence.
5. `centeredScrollOffset` carries a 4th `deadbandPx` parameter (the doc's 3-param sketch could not express the ~±48dp pixel deadband); wiring reads measured item widths from `listState.layoutInfo` rather than assuming `cardWidth` (uniform for CreatorRow, whose card width is intrinsic); no `onSizeChanged` needed.
6. Landscape cap/unknown-aspect width at the default height is now 280.5dp (170 × 1.65) vs the old flat 280dp — cosmetic-only, documented in `cardWidth`.
7. TvSourceFeedScreen top region restructured (header Box + grid below the strip without the fixed 44dp clearance) — the one non-trivial host-side change Phase D required.

## Open risks / device gates (not run here — emulator/ad.numpy forbidden in this task)
- TV36 device gates remain: crop-removal screencap pair, centering Right×10 walk (no clipped edge, no vertical-recenter), staggered D-pad traversal on ragged rows (THE #15 gate; documented fallback: `LazyVerticalGrid(Fixed(4))` + true-aspect tiles), strip walk + tab switch (creator AND niche) + initial-first-card focus + BACK, hold-Center/MENU regression spot-checks.
- BACK from a tab-switched feed returns to HOME, not the previous feed (Root has no feed stack) — flagged as an open user question in the design doc, unchanged here.
- `TvSourceFeedScreen` uses member `items(count=...)` of the staggered scope — no import needed; confirmed against the foundation API surface used by the mobile FeedScreen (which imports `staggeredgrid.items` for the list overload).

## Changed files
- app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvHomeScreen.kt
- app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/RowCentering.kt (new)
- app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvSourceFeedScreen.kt
- app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvMainActivity.kt
- app-tv/src/main/res/layout/focus_preview_player.xml
- feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedTabStrip.kt (new)
- feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedScreen.kt
- app-tv/src/test/kotlin/com/rjbiermann/giffyviewer/tv/CardWidthTest.kt (new)
- app-tv/src/test/kotlin/com/rjbiermann/giffyviewer/tv/CenteredScrollOffsetTest.kt (new)
