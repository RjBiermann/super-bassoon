# DESIGN-A11YFIX.md — issue #51 implementation log (lane-a11yfix)

Base: main `fa77b5cf`. Six findings from `gh issue view 51`, evidence in the (since
removed) `REPORT-a11y-mobile.md` audit — see jj/git history. Fence: PlayerScreen/QuickSheet/FeedScreen (host of the
tile caption fix)/SearchScreen/SettingsScreen/FollowingScreen/NicheAboutScreen/
NichesScreen + `:core:ui` PlayerOverlay.kt. TV files, ContentFilter,
FeedMediator, FeedRepository, NicheJoinViewModel untouched. No emulator/adb.

## Finding 1 (a11y-breaking) — TalkBack re-reveal of auto-hidden player controls
`PlayerScreen.kt` (PlayerPage body `Box`, after `.background(Color.Black)`):
`semantics { customActions = listOf(CustomAccessibilityAction("Show playback
controls") { onShowControls(); true }) }`. The raw `pointerInput` tap is not
dispatchable by TalkBack and the hidden overlay (`AnimatedVisibility`) leaves
composition, so this is the only reachable path to play/pause/mute/like/etc.
Controls still auto-re-hide after 3s (`IDLE_HIDE_MS`) while playing — same
lifetime as sighted users; the action is re-runnable.

## Finding 2 — 48dp touch targets (M3 `minimumInteractiveComponentSize`, placed
inner-visual-untouched: it centers the content in a ≥48dp box and expands the
hit area outward; no padding/size change to the drawn visuals)
- `PlayerScreen.kt` niche pills (~was 24dp): `minimumInteractiveComponentSize()`
  before `.clickable`, border/padding untouched.
- `PlayerScreen.kt` tag texts (~was 16dp): same shape.
- `FeedScreen.kt` tile creator caption (~was 20dp, nested clickable inside the
  tile's `combinedClickable`): passed
  `Modifier.padding(start = 4.dp).minimumInteractiveComponentSize()` into
  `CreatorLabel` (the shared `:core:ui` widget applies caller modifier before
  its own `clickable`, so the min-size box wraps the clickable — CreatorLabel.kt
  itself untouched, it is outside this lane's fence).
M3-provided targets (Buttons/Chips/IconButtons) were already verified compliant
against the material3 AAR (report Methodology step 3) — not touched.

## Finding 3 — slider semantics
- Scrub slider (`PlayerScreen.kt` `PlayerControls`): `contentDescription =
  "Playback position"`, `stateDescription = formatRemaining(positionMs, durationMs)`
  (the existing helper; TalkBack hears the control's name + time instead of "NN%").
- Speed slider (`QuickSheet.kt`): `contentDescription = "Playback speed"`,
  `stateDescription = "%.2f×"` (Locale.US, matching the visible label's format).

## Finding 4 — `semantics { heading() }` on section headers
- `SearchScreen.kt`: `SectionHeader` title (covers "Top gifs"/"Trending photos"/
  "Trending niches"/"Creators") + the "Tags matching" inline header.
- `SettingsScreen.kt`: `LazyListScope.section()` title (covers all blocked lists)
  + the "Blocked feeds" title.
- `FollowingScreen.kt`: "Creators" (:88) and "Niches" (:111).
- `NicheAboutScreen.kt`: "Niche Rules" (:113), "Top Creators" (:133),
  "Niches you might also like" (:152).
Not added: `CustomFeedsScreen` "Your feeds" (file outside this lane's fence —
flagged in the report; the issue's list pins :459/951/88/:111/113/133/152 which
are all covered).

## Finding 5 — overlay text contrast (token-level, no restyle)
- `PlayerOverlay.scrim` 0.55 → **0.60** (stays inside the issue-#7 art-scrim
  band 40–60%). WCAG math over white artwork under FULL 0.60 scrim: bg
  luminance ≈0.13 (sRGB-linear), onSurface #F7E9EC ≈0.85 → ≈4.9:1 ✅ (0.55
  gave ≈4.05 max — below the 4.5 body-text rule even at full coverage).
- `PlayerOverlay.secondaryOnVideo` (desc text) 0.70 → **0.95** (the report's
  "≥0.95 of full onSurface" floor for text in the ramp zone).
- `PlayerScreen.kt` bottom gradient: full scrim reached from fraction 0.75f of
  screen height (was 1f, ramp 0.65→1) — the text cluster sits ~64–214dp above
  the bottom edge (~0.75–0.92 of a phone viewport), so it now sits under full
  scrim instead of inside the ramp. Gradient lengthens; no element restyled.
- **Residual (not fixed, recorded):** tag + niche-pill texts are painted
  `colorScheme.secondary` (#E3BDC4, brand token — issue-#7 palette). Over a
  pure-white frame even under the 0.60 scrim that pair computes ≈3.4:1; reaching
  4.5:1 there requires either a text-color change (a visual/design change this
  lane is forbidden to make) or a >60% scrim (outside the issue-#7 band).
  Everything else in the cluster (description = secondaryOnVideo, creator =
  Color.White) computes ≥4.9:1 under the new tokens.
- Note: `PlayerOverlay` is shared — TvPlayerScreen picks up the new scrim/
  description-alpha values too (token file is inside this lane's fence; TV
  composables were not edited).

## Finding 6 — nameless semantics
- `NichesScreen.kt` (`NicheTileCard` pin IconButton): `"pin ${tile.name}"` /
  `"unpin ${tile.name}"` (sibling buttons already name their object).
- `SearchScreen.kt` `CreatorCard` avatar: `contentDescription = null` — the row
  Text directly below repeats `@username`; the duplication made TalkBack
  announce the name twice (report #7 fix shape: decorative image).

## Testing note (AGENTS policy)
All six fixes are Compose semantics modifiers, M3 standard modifiers, resource
token constants, and string-literal descriptions — no new branches/loops/parsers
or extracted pure logic, so there is no JVM-testable unit and no Compose-UI test
infrastructure exists in this project yet (all current tests are JVM;
`createComposeRule` suite is a queued #50 stage). README-quoted repo policy
allows skipping tests where no trivial hook exists; noted per finding above.

## Not fixed (deferred with reasons)
- `CustomFeedsScreen.kt:319` "Your feeds" heading — outside the named fence.
- History/suggestion-row `onClickLabel` + tile `onClickLabel` (report #7
  cosmetic group) — the issue's finding-6 line pins only NichesScreen pin/unpin
  and SearchScreen.kt:500-511; the onClickLabel polish is cosmetic, not ranked.
- Report #8/#9 cosmetic batch (OfflineBanner heightIn, 10sp time chip, no-op
  focusable rows) — cosmetic class, not in issue #51's six findings.
- Secondary-colored tag text over bright artwork (residual above) — needs a
  design decision (token or color change), not an alpha fix.
- RESOLVED: finding-5 residual closed via design decision — subtle dark pill
  backdrop `PlayerOverlay.secondaryOnVideoBackdrop` (80% black, theme
  shapes.extraSmall) behind the overlay secondary text rows (description,
  tags, niche chips/pills) on PlayerScreen + TvPlayerScreen; text colors
  unchanged, contrast carried by the backdrop and WCAG-verified by
  OverlayContrastTest (:core:ui).
