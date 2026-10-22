# DESIGN-tv-player (Stream B) — issues #53, #52

Owner modules: `:app-tv` (TvPlayerScreen.kt, TvQuickActions.kt). Only
`core:player`-adjacent read APIs (`GiffyPlayer.currentGifId`) — no core edits planned.

## Objective

- **#53** — on TV, when switching gifs the player keeps showing the PREVIOUS video's
  frame until the next video actually plays (expected: the new gif's poster/thumbnail
  like mobile), and the stale frame is **visually warped for a few moments** when the
  previous and next gif have different aspect ratios.
- **#52** — TV mute/unmute does nothing.

---

## Issue #53 — stale frame + aspect warp at switch

### Current behavior + evidence

- TV player surface: ONE persistent `PlayerView` per screen —
  `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt:382` (`view.player = player`
  unconditionally in the AndroidView `update`), hosting a **texture_view** with a
  transparent shutter (`app-tv/src/main/res/layout/player_view.xml:6–8`) so the poster
  shows through **only while no frame has ever rendered**.
- Under the surface sits the new gif's poster `AsyncImage`
  (`TvPlayerScreen.kt:586–596`) — deliberately **beneath** the PlayerView, so it can
  only show while the TextureView is transparent.
- On switch: `TvPlayerScreen.kt:574` (`if (player.currentGifId.value != gif.id)
  player.stop()`) + `TvPlayerScreen.kt:605–609` `playGif(...)`. `stop()` does not blank
  a TextureView — the SurfaceTexture keeps displaying the last decoded frame of the
  OLD gif until the NEW stream's first frame lands on the same surface. The poster
  under it is invisible the whole time → **the stale-frame report**.
- The aspect machinery is already switched on switch:
  `TvPlayerScreen.kt:390–394` seeds `view.seedContentAspectRatio(gif.contentAspectRatio())`
  (`core/player/.../SeedContentAspect.kt:24–26` — clears to 0 when unknown). Seeding
  the NEW aspect while the OLD frame is still on screen **stretches the stale frame**
  under the new aspect for the whole switch window → **the warp report**.

### Root-cause hypothesis (high confidence)

The mobile player fixed this exact report class by **attach-gating the shared player**
(`feature/feed/.../PlayerScreen.kt:753`):
`view.player = if (active && playingId == gif.id) player else null`, with
`playingId = player.currentGifId` collected as state (`PlayerScreen.kt:515`) —
currentGifId flips BEFORE the media swaps (`GiffyPlayer.kt:24–28`), so the surface
detaches at switch and the poster shows through the transparent TextureView until the
first frame of the new gif. TvPlayerScreen never got that gating; its surface stays
attached across switches.

(The inline-feed variant of "attach-on-first-frame" was reverted once for the
chicken-and-egg decode deadlock — but that was for the per-tile surfaces where the
surface had to exist for decoding; the TV screen's surface always exists — gating only
`view.player`, not view existence, does not starve decode; the mobile PlayerScreen
proves it works with a shared fullscreen player.)

### Proposed approach (minimal)

Mirror the mobile fix inside `TvPlayerScreen`'s AndroidView `update` —
**app-tv-only diff, ~3 lines**:

1. Collect `val playingId by player.currentGifId.collectAsStateWithLifecycle()`.
2. `update = { view -> view.player = if (playingId == gif.id) player else null; … }`
   (keep the existing `resizeMode` + seed calls in the same lambda).
3. Keep the poster `AsyncImage` as-is — it now becomes visible exactly during the
   switch/prepare window, which is the "thumbnail like mobile" expectation.

No XML, no `GiffyPlayer` changes, no new listeners. The existing clear-on-switch
`stop()` (`:574`) stays (it also protects the FocusPreview pause/resume path).

### Acceptance conditions

- Device gate (TV AVD `tv`, per the session-log recipe): open player, let frames
  render, press DOWN/UP (or auto-swipe at a page boundary) → screencap shows the NEW
  gif's poster during the switch, not the previous frame; no stretched stale frame
  (compare one vertical gif next to one horizontal gif). uiautomator dump + screencap
  evidence; 0 FATALs.
- Same-gif re-entry (BACK→re-open, auto-swipe loop of the last gif) must NOT flicker:
  `playingId == gif.id` keeps the surface attached, unchanged from today.
- `:app-tv` gates green (compile + ktlint + detekt + unit).

### Non-goals

- No first-frame callback listener in `GiffyPlayer` (attach-gating makes it
  unnecessary; avoid touching core:player — Stream A edits that file, see overlap).
- No per-tile surface pool, no SurfaceView/TextureView re-selection, no shutter
  color changes.
- No change to poster-null behavior (black background stays the truthful state).

---

## Issue #52 — TV mute/unmute doesn't work

### Current behavior + evidence

- The pref toggles fine and the UI even reflects it:
  `TvPlayerScreen.kt:577` (`val muted by settings.muted.collectAsStateWithLifecycle(false)`),
  `TvPlayerScreen.kt:622` (`onToggleMute = { scope.launch { settings.setMuted(!muted) } }`),
  label flip in `TvQuickActions.kt:275` ("Mute"/"Unmute").
- **The pref is write-only on TV:** grep for `volume`/`muted` application in
  `app-tv` shows the only player-volume writes are the FocusPreview's hardcoded
  `volume = 0f` (`FocusPreview.kt:48`). There is NO
  `player.volume = if (muted) 0f else 1f` anywhere in TvPlayerScreen.
- Mobile has the missing line (`feature/feed/.../PlayerScreen.kt:266`):
  `LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }`.

### Root-cause hypothesis (certain — missing consumer)

`settings.muted` was never consumed by the TV player's volume. Classic
"dead-toggle" class (same shape as the retired TV auto-swipe dead toggle, batch-3
lesson in AGENTS-PLAYER.md).

### Proposed approach (minimal)

One line in `TvPlayerScreen` (mobile parity, keyed on the player instance):
`LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }` — placed next to the
other settings-side effects (`:577` area). PlayerScreen mute is app-global
(returns with the pref on next open); TV mirroring that keeps the shells consistent.

### Acceptance conditions

- Device gate (TV): quick-actions → Mute with audible/gif-with-audio content →
  silence (verified via logcat audio track or by un/mute pairing with an audio gif on
  the emulator), Unmute → audio back. Screencap of the label + a volume assertion via
  a debug-only log is acceptable; visual-only is NOT (mute is audible by nature).
- `:app-tv` gates green.

### Non-goals

- No per-player mute state (pref is global by design), no FocusPreview change
  (preview is always muted, normative).
- No audio-focus handling.

---

## Stream overlap verdict (for the orchestrator)

- **A × B — near-disjoint, run in parallel with a file fence.** Stream A's #56 core
  change is `core/player/GiffyPlayer.kt` (cache-key) + `core:model`; Stream B's fixes
  live entirely in `app-tv/TvPlayerScreen.kt`. The only shared risk: if Stream B
  wanted a `GiffyPlayer` change (e.g. an `onRenderedFirstFrame` flow), it would collide
  with A's cache-key edit in the same file — **this design deliberately avoids that**
  (B needs no core:player edit). Verdict: parallel lanes OK with fence
  A = `core:model/VideoQuality.kt` + `core/player/GiffyPlayer.kt` (+ tests);
  B = `app-tv/TvPlayerScreen.kt` (+ tests). B also touches nothing Stream C/D needs.
- **B × C:** disjoint (`:app-tv` vs `:core:network`/`:feature:feed`) — parallel OK.
- **B × D:** disjoint unless D's search repro turns out to be TV-only
  (`TvSourceFeedScreen`) — see DESIGN-search.md; keep D fenced off `app-tv` until
  repro'd.

## Probe flags (not performed — investigate lane)

- **Device-only verification for both fixes** (TV AVD): stale-frame/warp is a
  compositing behavior; no JVM test can cover it. Mute is audible.
- **Emulator-artifact warning for #53:** the documented AVD decode wedge (black video
  + advancing progress) must not be confused with the stale-frame report —
  discriminate via screenrecord size per the session-log rule before filing any
  "still broken".
