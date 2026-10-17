# REPORT-a11y-mobile.md — Mobile accessibility audit (code-only, read-only)

Scope: **mobile surfaces only** (age gate → PIN → feed grid → search → settings →
player). TV D-pad/focus work is out of scope (separate lane, per task). Baseline:
Compose/Material 3 defaults + the AGENTS-APP.md a11y rules (≥48dp touch targets,
contentDescription on meaningful icons, 4.5:1 text contrast). No emulator, no adb, no
builds — every claim is quoted from source with `file:line`.

## Methodology

1. Read `AGENTS.md` (root) and `AGENTS-APP.md` in this workspace first; the AGENTS-APP
   a11y history (rounds 1–3, “High (a11y / destructive actions) — all closed 2026-10”)
   was used as *claims to re-verify against code*, not as facts.
2. Read every mobile-facing composable file end-to-end (26 files, listed below), plus
   grep sweeps for evidence-line pinning:
   `semantics {`/`heading(`/`stateDescription` (expectation: usage census),
   `combinedClickable`/`pointerInput` (gesture-only controls), `contentDescription`,
   `.sp`, `height(`/`size(` in dp on scrollable containers.
3. Touch-target byteclaims verified against the **BOM-pinned material3 AAR** in the
   local gradle cache (`compose-bom 2026.09.00` → material3-android 1.5.0-alpha22,
   `~/.gradle/caches/.../material3.aar`, decoded with `javap`): `Surface(onClick…)`
   — the base of Button/Chip/IconButton/ListItem rows — unconditionally applies
   `InteractiveComponentSizeKt.minimumInteractiveComponentSize` (Surface.kt sites
   220/354/489 in that AAR). So **M3-provided clickables meet the 48dp touch target**
   and this audit concentrates on *hand-rolled* clickable text/rows.
4. Contrast: ratios computed from `GiffyColors` hexes in
   `core/ui/.../Theme.kt:24-59` and the `PlayerOverlay` alpha tokens in
   `core/ui/.../PlayerOverlay.kt:13-20` (WCAG relative-luminance math, listed in §3).
5. No .kt file was modified. Only this report was written, then committed in jj.

## Files inspected (26)

app-mobile: `MainActivity.kt` · `GiffyApplication.kt`
core/ui: `Theme.kt` `GiffyScaffold.kt` `Focus.kt` `PinLock.kt` `AgeGate.kt`
`EmptyState.kt` `OfflineBanner.kt` `PlayerOverlay.kt` `ScrollToTopFab.kt`
`AudioBadge.kt` `CreatorLabel.kt` `VerifiedTick.kt` `GiffyCollapseState.kt`
`NetworkMonitor.kt` `LayoutHint.kt` `Feature.kt` `ObjectFit.kt`
feature/feed: `FeedScreen.kt` `PlayerScreen.kt` `QuickSheet.kt` `FeedTabStrip.kt`
`FeedHeader.kt` `FeedFilterDialog.kt` `NichesScreen.kt` `NicheAboutScreen.kt`
`FollowingScreen.kt` `CollectionsScreen.kt` `CustomFeedsScreen.kt` `FeedViewModel.kt`
(VM-level files skimmed for state keys only; UI claims all sit in the listed UI files)
feature/search: `SearchScreen.kt`
feature/settings: `SettingsScreen.kt` `SettingsPage.kt`
feature/auth: `AuthSection.kt` `WebViewLoginScreen.kt` `DebugSignIn.kt`
core/player: `GiffyPlayer.kt`/`GiffyPlayerFactory.kt` (no composables; surfaces only)

---

# Ranked findings

Severity legend: **a11y-breaking** = a core flow cannot be completed/observed with
TalkBack (or a target is unusable); **major** = works but violates the stated M3
baseline (48dp / 4.5:1 / labelled controls / heading navigation); **cosmetic** =
edge-case polish.

## 1. Player: tap-to-reveal is unreachable for TalkBack once controls auto-hide — a11y-breaking

- Gesture: the reveal/hide tap is a raw `pointerInput(controlsVisible, active, liked)`
  awaitEachGesture loop — `PlayerScreen.kt:587-678` (tap toggle at ~665-673).
- Auto-hide: `IDLE_HIDE_MS = 3_000L` (`PlayerScreen.kt:122`) fires after 3s of playing
  without scrubbing (`PlayerScreen.kt:234-239`) and sets `controlsVisible=false`.
- The action rail, text cluster and controls row then **leave composition**:
  `AnimatedVisibility(visible = controlsVisible && active, …)` at
  `PlayerScreen.kt:744/768/846/927` — no a11y node remains.
- `grep` census: the ONLY semantics modifiers in the whole mobile tree are
  `PinLock.kt:102` and the like-button liveRegion `PlayerScreen.kt:1067-1068` — there
  is **no custom accessibility action** on the player surface to re-show controls,
  and TalkBack's single-tap (focus) does not dispatch the app's raw tap gesture.
- Real-flow impact: a TalkBack user opens any video → controls are visible for 3s →
  after auto-hide they lose play/pause, mute, like, share, speed, the Block sheet and
  the like alternative — permanently until the video is re-fetched.
- Fix shape (one line of design): add a semantics `customActions` entry on the player
  body (“Show controls”) that sets `controlsVisible = true`, or key the
  `AnimatedVisibility`s to `controlsVisible || a11yVisible` — no layout change.

## 2. Touch targets <48dp on hand-rolled clickable text (player links-link row) — major

Measured dp straight from the modifiers (M3 buttons/chips are covered by the AAR
verification in Methodology step 3):

- Niche pills in the player cluster: `Text` with
  `.padding(horizontal = 10.dp, vertical = 4.dp)` over `typography.labelMedium`
  (12sp ≈ 16dp line) → **≈24dp tall** — `PlayerScreen.kt:806-812` (clickable at 807).
- Player tag texts (plain, no pill): `.clickable { onOpenTag(tag) }` at
  `PlayerScreen.kt:823-826` with **no padding at all** → **≈16dp tall**.
- Feed tile creator caption: `CreatorLabel(..., onClick = onOpenCreator)` at
  `FeedScreen.kt:795-801`, rendered with `labelSmall` (11sp ≈ 16dp line) +
  `padding(start = 4.dp, top = 2.dp, bottom = 4.dp)` (`FeedScreen.kt:796-799`) →
  **≈20dp tall**, and it is a *nested* clickable inside the tile's
  `combinedClickable` (`FeedScreen.kt:710-714`) — mis-taps fall through to opening
  the player instead of the creator feed.
- Same `CreatorLabel(onClick…)` pattern in creator suggestion chips —
  `FeedScreen.kt:516-524` (inside M3 `SuggestionChip`, which pads fine; only the
  nested caption target is small there — the chip click covers it, so this one is
  cosmetic).
- Real-flow impact: the “links audit” paths (tile → creator feed, player → tag/niche
  feed) are the primary navigation acts; at 16–24dp they fail the 48dp AGENTS-APP
  baseline and, being adjacent, invite wrong-target taps.
- Fix shape: `Modifier.heightIn(min = 48.dp)` (or wrap in
  `minimumInteractiveComponentSize`) on those three call sites; keep visual size.

## 3. Sliders announce only a percentage — no stateDescription/progress in time — major

- Player scrub slider: `Slider(...)` at `PlayerScreen.kt:974-988` — no
  `contentDescription`, no `stateDescription`, no paneTitle; TalkBack hears “NN%”
  with zero indication it is position/duration.
- Quick-sheet speed slider: `Slider` at `QuickSheet.kt:110-115` — the visible label
  is a separate `Text` (“Speed 1.25×”, `QuickSheet.kt:101-107`), not semantically
  attached, so TalkBack announces only “NN%” for a *speed* control.
- The player time readout (`formatRemaining`, `PlayerScreen.kt:994-998`) is a plain
  Text alongside the slider; the slider state never carries it.
- Fix shape: `Modifier.semantics { stateDescription = "-1:23" }` /
  `contentDescription = "Playback position"` on the scrub slider; `contentDescription
  = "Playback speed"` + `stateDescription = "%.2f×"` on the speed slider.

## 4. No `semantics(heading())` anywhere — LazyColumn sections are unjumpable — major

`grep -rn "heading" app-mobile core/ui feature/*/src` → **zero hits** (only imports of
other semantics members). Every list surface has text-only section headers:

- `SearchScreen.kt:459-473` `SectionHeader` (“Top gifs”/“Trending photos”/“Trending
  niches”/“Creators”, used at 307/322/334/349) plus “Trending tags” at
  `SearchScreen.kt:367-374`.
- `SettingsScreen.kt:951-1000` `section()` (“Favorited creators”, “Blocked creators”,
  … — rendered at 961-965) and “Blocked feeds” `SettingsScreen.kt:882-893`.
- `FollowingScreen.kt:84-92` (“Creators”) and `:108-116` (“Niches”).
- `NicheAboutScreen.kt:113/133/152` (“Niche Rules”, “Top Creators”, “Niches you might
  also like”), `CustomFeedsScreen.kt:319` (“Your feeds”).
- Real-flow impact: TalkBack heading navigation (the fastest way through the multi-
  section Search and Settings-Blocked screens) finds no headings; screen-reader users
  must linearly walk every row.
- Fix shape: add `semantics { heading() }` to those ~10 header Texts.

## 5. Player description/tag text over artwork — computed contrast can fall to ≈2–3.7:1 — major

Token math (WCAG luminance, hexes from `Theme.kt:24-59`, alphas from
`PlayerOverlay.kt:13-20`):

| pair | ratio |
|---|---|
| onSurface `#F7E9EC` on background `#161617` | 15.4:1 ✅ |
| onSurfaceVariant `#DCA1AB` on background | 8.4:1 ✅ |
| secondary `#E3BDC4` on background | 10.6:1 ✅ |
| primary `#FF668F` on background (age-gate title `AgeGate.kt:49`, search tags `SearchScreen.kt:348`) | 6.5:1 ✅ |
| offline banner `#FFDAD6` on `#93000A` (`OfflineBanner.kt:43-47`) | 7.2:1 ✅ |
| white on 0.55-black-over-white artwork (badges / “2× speed” chip worst case) | 4.7:1 ✅ (pass, barely) |
| **`PlayerOverlay.secondaryOnVideo` = `#F7E9EC`@70% over bright white artwork, no full scrim** | **1.05:1 ❌** |
| same @70% over artwork with a partial (~0.35–0.5 of the 0.55 max) gradient scrim at the cluster's actual y positions | **≈2.1–3.75:1 ❌ (bodyMedium = 4.5:1 required)** |

- The gradient scrim only ramps 0.65f→1f of screen height to 55% black
  (`PlayerScreen.kt:736-757`); the bottom text cluster sits at `bottom = 64.dp` +
  ~150dp of content (`PlayerScreen.kt:775-783`) — inside the ramp, not under full
  scrim, so description/tags (`PlayerScreen.kt:785-828`, tagged texts colored
  `colorScheme.secondary` at 813/828) land at ≈2.1–3.75:1 over bright artwork —
  below the 4.5:1 body-text rule AGENTS-APP pins.
- Real-flow impact: on bright/white-frame gifs (common pool content), the creator
  cluster and tags become unreadable exactly when controls are shown.
- Fix shape: raise the type-alpha token to ≥0.95s of full `onSurface` inside the ramp
  zone, or extend a localized scrim behind the text cluster (a
  `Brush.horizontalGradient` rail behind the bottom-left column).

## 6. “pin”/“unpin” IconButtons have no object name — major

- `NichesScreen.kt:180-183`: `contentDescription = if (pinned…) "unpin" else "pin"` for
  the per-niche pin button, and its action twin `:178-180` — in a scrolling list the
  announcement gives no niche name (“pin” alone); contrast with the sibling buttons
  that DO name their object (“about ${niche.name}”, `NichesScreen.kt:174-176`;
  “rename ${collection.folderName}” `CollectionsScreen.kt:119-123`; “unblock $value”
  `SettingsScreen.kt:977-982`).
- Fix shape: `"pin ${niche.name}"` / `"unpin ${niche.name}"` (also fixes state:
  selected-state semantics could carry pinned state, but the text flip suffices).

## 7. Search explore-strip creator card + history/search rows — semantics polish — major (grouped)

- `CreatorCard` image carries `contentDescription = "@${creator.username}"` at
  `SearchScreen.kt:500-505` while the row's Text below repeats the same string
  (`SearchScreen.kt:506-511`) → double announcement. Make the image `null`
  (decorative, like everywhere else: `FeedScreen.kt:757-761`,
  `CreatorFeedHeader` `FeedHeader.kt:47-52`).
- History/suggestion rows are bare `Row.clickable` (`SearchScreen.kt:194-215`,
  `:216-239`) with no `onClickLabel` (“Search for …”) and no merged clear semantics:
  the trailing count Text (“%,d gifs”, `:233-238`) rides the row's merged node — that
  part is correct, but the row announces query, then count, with no role context.
  Cosmetic-to-major; a one-line `onClickLabel` fixes it.
- The tile's `combinedClickable` (`FeedScreen.kt:710-714`) sets
  `onLongClickLabel = "Open quick actions"` (good) but **no `onClickLabel`**
  (“Open video”) — cosmetic.

## 8. Large-font behavior — two fixed-height containers — cosmetic

- `OfflineBanner` paints a **fixed `height(40.dp)`** with `maxLines = 1` +
  `bodySmall` (`OfflineBanner.kt:35-47`): at ~1.5× font scale the M3 bodySmall line
  box (16sp) becomes ≈24dp and still fits, but at 2.0× the single line turns 32dp and
  the 40dp box starts clipping/ellipsizing the safety banner.
- `PlayerScreen.kt:995`: `fontSize = 10.sp` for the remaining-time chip — below the
  labelSmall 11sp floor, and a 10sp time readout is the *only* numeric time in the
  player; it does respect fontScale (sp) but the fixed 24dp progress slot below
  (`PlayerScreen.kt:1006-1012`) is decorative so it's fine.
- PinLock keys are safe: 72dp squares (`PinLock.kt:143` — `Modifier.size(72.dp)`)
  vs titleLarge 22sp → 44sp line at 2.0× still fits.
- Fix shapes: `OfflineBanner` → `Modifier.heightIn(min = 40.dp)` + wrap to 2 lines;
  time chip → `labelSmall`/11sp.

## 9. Transcript/no-op rows worth one grep each — cosmetic

- Settings `section()` rows carry `giffyFocus()` (adds `.focusable()`) on rows whose
  only control is the trailing IconButton (`SettingsScreen.kt:181-185` + `:955-982`):
  on touch this is a no-op focusable that puts an extra silent stop in TalkBack
  order. Harmless on TV (it's the D-pad ring), but on mobile the wrapper focusable
  could be dropped or gated.
- `AudioBadge` (`AudioBadge.kt:27-33`) announces “has sound” merged into each tile
  with audio — meaningful and fine — but the 12dp icon is decorative visual; leaving
  the description is correct (sound state is not otherwise announced).

---

# Passes (verified, not assumed)

- **Switch rows**: every mobile switch row is whole-row
  `toggleable(role = Role.Switch, onValueChange)` with `Switch(onCheckedChange =
  null)` — `SettingsScreen.kt:470-480` (feed autoplay), `:536-548` (AMOLED),
  `:655-664` (data saver), `:737-747` (verified-only), `FeedFilterDialog.kt:114-124`
  (strict tags) → correct state announcement per the AGENTS-APP rule (the old
  dynamic-color row no longer exists — dynamic color was removed with the issue-#7
  rebrand, `Theme.kt:160-171` has no dynamicColor param).
- **PIN pad**: 72dp keys with real descriptions via `clearAndSetSemantics`
  (“Delete last digit” / “Submit PIN”, `PinLock.kt:64-69, 151-158`), haptic per key +
  long-press on wrong PIN (`PinLock.kt:53-68`), dot-progress announced as “N of 4
  digits entered” (`PinLock.kt:100-104`). Matches the AGENTS-APP “closed” note.
- **M3 touch targets**: IconButtons/Buttons/Chips/TextButtons across feed, player
  rail, tabs, settings, dialogs — covered by the material3 AAR
  `minimumInteractiveComponentSize` verification (Methodology step 3); player
  Retry/Skip carry explicit `heightIn(min = 48.dp)` (`PlayerScreen.kt:910-921`).
- **Gesture only + alternatives**: double-tap like (`PlayerScreen.kt:644-666`) has the
  labelled rail Like button with a polite liveRegion announcing the outcome
  (`PlayerScreen.kt:1058-1073`); hold-to-2× (`:618-641`, `HOLD_SPEED = 2f` `:125`)
  has the labelled speed slider in the quick sheet (`QuickSheet.kt:96-115`); pinch
  zoom is non-exclusive (Fit/Crop pref exists, `SettingsScreen.kt:553-597`).
- **Icon buttons labelled**: back/close (`GiffyScaffold.kt:110-114`,
  backDescription="back"/"close" `:41, SettingsScreen.kt:199-201`), search/settings
  (`FeedScreen.kt:129-134`), sort+filter tinted state (`FeedScreen.kt:222-236`),
  share/more/sound/auto-swipe in the rail (`PlayerScreen.kt:1080-1098`), create/
  rename/delete collections (`CollectionsScreen.kt:74-77, 117-126`), about niche
  (`NichesScreen.kt:174-176`), with per-entry removal descriptions in history
  (`SearchScreen.kt:207-212`), blocked lists (`SettingsScreen.kt:969-977`).
- **ScrollToTopFab**: labelled “Scroll to top” (`ScrollToTopFab.kt:48-52`), honors
  reduced-motion (`ScrollToTopFab.kt:118-131`); pull-to-refresh is an M3 built-in.
- **Dark-on-load immutability**: AMOLED collapse keeps accent/on-colors
  (`Theme.kt:63-88`), so the verified 8.49:1 focus container pair stays intact under
  AMOLED.
- **Fonts**: typography is the standard M3 scale on DM Sans (`Theme.kt:112-134`) —
  no raw `px`; the only raw `.sp` is the 10sp time chip (finding 8).

# Summary counts

a11y-breaking: 1 (player reveal/unreachable controls after auto-hide).
major: 6 (small link targets ×3 sites, sliders unlabelled ×2, missing headings ×~10
sites, overlay-text contrast, pin/unpin ambiguity, search row polish group).
cosmetic: 4 (banner height, 10sp chip + fixed 24dp slot, onClickLabels, extra
focusable rows).

All findings are code-static; no device pass was run (out of scope). Suggested
verification order for a device gate: finding 1 (TalkBack + 3s wait), then 2 (hit
squares), then 3/4 (TalkBack walk), then 5 (bright-frame sample).
