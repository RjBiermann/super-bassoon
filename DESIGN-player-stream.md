# DESIGN-player-stream.md — Player/stream issue batch (#2 #3 #4 #8 #9 #16 #20 #21 #24 #26 #27)

Design phase output — NO code changed. Every current-behavior claim below is pinned to the working
tree of `/workspaces/sturdy-potato/ws-player` (jj workspace, repo `super-bassoon`). Line numbers are
from the checked-out revset; treat them as anchors, not guarantees.

Reference docs read: root `AGENTS.md` (batches 20–23: CenterHold fire-while-held + per-source panel
swallow lesson, single setPlaybackSpeed application point, SeedContentAspect, de-chromed mobile
player), `AGENTS-PLAYER.md` (cache, HLS-fallback spec, inline autoplay, video fit, TV player),
`AGENTS-NETWORK.md` (endpoint inventory, browser identity, rate budget), `AGENTS-APP.md` (keymap),
`AGENTS-CONTENT-FILTER.md` (not touched by this batch).

House-rule gates that apply to the whole batch: compile + ktlint + detekt + unit tests green per
touched module; no new dependencies; rate-limit invariant ≤15 req/5s untouched (none of the fixes
adds a request path); no WorkManager; StateFlow-only exposure; dark-only; viewer-only.

---

## #2 — Tap toggles player UI WITHOUT pausing (mobile)

**Files:** `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/PlayerScreen.kt`

### Current behavior (evidence)
- Tap detection lives in the player body's `pointerInput` gesture decoder:
  `PlayerScreen.kt:637–663`. The final dispatch is:
  - `PlayerScreen.kt:658–660` — `if (controlsVisible) { if (player.isPlaying) player.pause() else
    player.playOrRestart() }`
  - `PlayerScreen.kt:661` — `else onShowControls()`
  So a single tap toggles *visibility* when the UI is hidden, but toggles *playback* when the UI is
  shown. Pause-on-tap is the TikTok-borrowed behavior the user now rejects.
- Controls visibility state: `controlsVisible` (`PlayerScreen.kt:181`), auto-hided by the idle
  effect (`PlayerScreen.kt:211–216`, `IDLE_HIDE_MS = 3_000L`, `PlayerScreen.kt:119`) while
  `isPlaying && !scrubbing`.
- The "always-on thin progress line" already exists independently of `controlsVisible`:
  `PlayerControls` renders it under `AnimatedVisibility(visible = !visible)` (the reverse gate,
  `PlayerScreen.kt:~947–961`); the whole bottom block is a fixed 24dp slot
  (`PlayerScreen.kt:~941–947`). Nothing to change there.
- Play/pause stays reachable without tap-pause: the bottom controls row has its own
  play/pause `IconButton` (`PlayerScreen.kt:882–898`, `onTogglePlay` →
  `player.pause()/playOrRestart()`, `PlayerScreen.kt:885–887`).
- Double-tap like is a separate branch BEFORE the else (`PlayerScreen.kt:643–657`) — untouched by
  this change. Hold-to-2× (long-press) is an earlier return (`PlayerScreen.kt:616–633`) — untouched.

### Root cause / gap
The else-branch maps "controls shown" single-tap to a play/pause toggle instead of a
visibility toggle. Everything the user asks for (visibility-only tap, always-visible progress
line, double-tap-like) already exists around that one branch.

### Proposed minimal change
In `PlayerScreen.kt` (`PlayerPage`'s tap dispatch, lines 658–662) replace:

```kotlin
if (controlsVisible) {
    if (player.isPlaying) player.pause() else player.playOrRestart()
} else {
    onShowControls()
}
```

with a pure visibility toggle (`controlsVisible = !controlsVisible` via the existing callbacks;
`onHideControls`/`onShowControls` are already wired at `PlayerScreen.kt:353–354`, so one more
callback is NOT needed — flip inside the gesture handler against the captured `controlsVisible`
value). Delete nothing else; remove the now-unused `player.isPlaying` read in that branch only.

Keep TV untouched: TV `CENTER` tap = play/pause per the AGENTS-APP keymap; this issue is mobile
`PlayerScreen.kt` only.

### Acceptance criteria
1. Controls visible + single tap → controls fade (description, rail, control row), playback audio/
   position continuous (sample `watch_history` position keeps advancing ≈ as before).
2. Controls hidden + single tap → controls reappear, playback never interrupted.
3. Double-tap like still pops the heart and issues exactly one like write.
4. Long-press 2× chip behavior unchanged (batch 14).
5. Unit/self-check: none warranted (the gate is a device/UI observation; the changed branch is
   gesture plumbing, not logic). `:feature:feed` compile+ktlint+detekt+tests green.

### Risk / notes
- Behavior change is user-visible by definition — it IS the issue.
- The play/pause capability moves solely to the control-row button while controls are shown; after
  auto-hide, reveal is two taps to pause. That matches the site's own player and Instagram-style
  players; the user explicitly asked for it.
- Watch-history sampling / resume logic is keyed to playback state elsewhere
  (`isPlaying` still follows the real player) — unaffected.

---

## #3 — TV hold-Center opens the quick-actions panel AND the panel must STAY open after key release

**Files:** `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt`,
`app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvHomeScreen.kt` (GifCard variant),
`app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvQuickActions.kt`,
`app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/CenterHold.kt` (no change expected)

### Current behavior (evidence)
- The hold machine is correct: `CenterHold` fires `onHold` WHILE the key is held
  (`CenterHold.kt:41–60`: `fireHold` runnable gated on `down`, `HOLD_MS = 500L`); `up()` cancels an
  unfired timer and reports suppression (`CenterHold.kt:66–74`).
- Player surface wiring: `TvPlayerScreen.kt:152–154` — `CenterHold { actionsFor = gifs.getOrNull(index) }`;
  initial Center KeyDown starts the clock and is consumed (`TvPlayerScreen.kt:204–207`);
  Center KeyUp runs `if (!centerHold.up()) { play/pause }` (`TvPlayerScreen.kt:261–272`) — the
  suppression rule keeps tap = play/pause.
- GifCard variant identical: `TvHomeScreen.kt:534` (hold → `onMenu`), `TvHomeScreen.kt:578`
  (KeyDown) / `TvHomeScreen.kt:582` (KeyUp suppression — hold must not ALSO open the card).
- The panel is `TvQuickActionsDialog` (`TvPlayerScreen.kt:~550`, `TvHomeScreen` card-variant call):
  a Compose `Dialog` (`TvQuickActions.kt:119`) whose first row takes focus on open:
  `TvQuickActions.kt:122–125` — `val first = remember { FocusRequester() }`,
  `LaunchedEffect(view) { first.requestFocus() }`.

### Root cause (concrete chain — the batch-22 device lesson is the key)
1. Hold fires at ~500ms → `actionsFor` set → the `TvQuickActionsDialog` window opens, and
   `first.requestFocus()` puts focus on the FIRST row (e.g. `Like`/`Unlike` for the player
   variant, `TvQuickActions.kt:163–177`).
2. The user's key release (Center KeyUp) is routed to the NEW window — the Dialog owns focus now
   (exactly the "a panel stealing focus and swallowing the KeyUp" shape documented at
   root AGENTS.md batch 17/18).
3. On Android, a focused Compose `Button` activates on Enter/DPad-CENTER KeyUp. The still-scheduled
   Center KeyUp lands on the focused first row → its `onClick` runs → the main-pane rows all end
   with `onDismiss()` (`TvQuickActions.kt:170–176, 192–196, 217–222, 231–236, 261–276`). The panel
   closes (and, worse, the first row's action also fires — e.g. an accidental like/unlike every
   hold-open).

So this is a suppressed-KeyUp *arrival* problem in the dialog, not a CenterHold problem. Batch 22's
T8 verification passed because the Supplier-side check observed the panel OPEN and the card NOT
opening; a held-too-fast release (or one that slipped past batch 22's short windows) closes the
panel — the user's report.

### Proposed minimal change
Add a "swallow the NEXT Center KeyUp" gate to the dialog, owned by the opener:

1. `TvQuickActionsDialog` gains one parameter: `openViaHold: Boolean = false` (default keeps every
   MENU-open call site unchanged). Inside, a `var swallowNextCenterUp by remember { mutableStateOf(openViaHold) }`
   and an `onPreviewKeyEvent` on the dialog's `M3Surface` (or root Column) modifier:
   when `swallowNextCenterUp && e.type == KeyEventType.KeyUp && e.key == Key.DirectionCenter` →
   `swallowNextCenterUp = false; true (consume)`, else `false`. Preview phase guarantees it beats
   the focused Button's own click handling.
2. Pass-through surfaces:
   - `TvPlayerScreen` hold-open: `CenterHold { openActions(index, viaHold = true) }` and the
     MENU KeyUp handler (`TvPlayerScreen.kt:273–277`) opens with `viaHold = false`.
   - `TvHomeScreen` GifCard: hold path opens with `viaHold = true`, MENU path unchanged.
3. Re-verify CenterHold suppression stays intact — the KeyUp can now be consumed in the dialog
   window, but `CenterHold.up()` only *clears state*; a swallowed KeyUp cannot leak into the next
   press because `down()` resets state per press (`CenterHold.kt:79–84`) — documented batch-17
   guarantee, unchanged.

### Acceptance criteria
1. Holding Center ≥500ms opens the panel; releasing the key does NOT close it (uiautomator dump
   still shows the panel rows after the release, `--duration 700` hold recipe).
2. No accidental first-row action on release (like count / favorite state unchanged across the
   hold-open; row label stable).
3. Short CENTER tap still does NOT open the panel (player: play/pause happens; card: opens).
4. MENU-opened panels behave exactly as today (immediate focus, no key swallowed — a CENTER tap
   inside the panel activates the focused row).
5. BACK still closes the panel first (dialog `onDismissRequest`), then the player.
6. Device-verify both player-variant and card-variant opens on TV36.

### Risk / notes
One-flag plumbing in two call sites; the dialog body is untouched. Small chance a fast release
KeyUp still arrives before the dialog window exists — in that case it is consumed by the player
root's `centerHold.up()` suppression (correct today); no extra handling needed.

---

## #8 — Black frame between swiped videos — show poster under the surface until first frame

**Files:** `feature/feed/.../PlayerScreen.kt` (+ new surface layout in `:feature:feed` — see below),
`app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt`

### Current behavior (evidence)
- Mobile page surface: `PlayerScreen.kt:667–687` — `AndroidView(factory = { PlayerView(ctx)... })`
  with NO xml knobs. A programmatic `PlayerView` defaults to `surface_view` (separate compositor
  layer, **black until first frame**) and an opaque default shutter → the reported black frame
  during each swipe's prepare window, and — with the shared single player — for the attach gap
  (`view.player = if (active && playingId == gif.id) ...`, `PlayerScreen.kt:681–683`; the active
  page attaches while the previous page's own detach already cleared the surface).
- The `background(Color.Black)` behind it (`PlayerScreen.kt:~529`) gives exactly the black.
- The codebase ALREADY holds the proven recipe for pre-first-frame transparency:
  `feature/feed/src/main/res/layout/inline_player_view.xml`
  (`app:surface_type="texture_view"` + `app:shutter_background_color=transparent`, comments
  documenting the SurfaceView-black rationale) and its TV twin
  `app-tv/src/main/res/layout/focus_preview_player.xml` (same knobs + `resize_mode=zoom`).
- TV player surface: `TvPlayerScreen.kt:282–311` — bare programmatic `PlayerView(ctx)` (same
  default surface_view + shutter) → same black frame at every up/down switch and stage of prepare.
- No poster/AsyncImage today in either player page.

### Root cause / gap
Default `PlayerView` surface type + opaque shutter, and no poster under the view. The inline
autoplay work already proved the fix shape; the swipe players simply never adopted it.

### Proposed minimal change
Mirroring the two existing, device-verified XML surfaces:

1. **Mobile:** new `feature/feed/src/main/res/layout/player_page_view.xml` — copy of
   `inline_player_view.xml` with `app:resize_mode` left at the default (`fit`); the existing
   `update` block (`PlayerScreen.kt:680`) keeps driving `view.resizeMode = videoFitMode` per frame
   so the pref still wins. `PlayerPage`'s Box becomes:
   - `AsyncImage(model = gif.posterUrl, contentScale = ContentScale.Fit, contentDescription = null)`
     UNDER the AndroidView (matchParentSize), poster-always-composed (same T4 rule as
     `TvHomeScreen.kt:~614–630`: no swap cost, TextureView transparent-when-no-frames keeps it
     visible).
   - Inflate `player_page_view.xml` instead of the bare `PlayerView(ctx)` factory;
     `update`/detach gating unchanged.
2. **TV:** `TvPlayerScreen.kt` AndroidView (282–311) inflated from a copy of
   `focus_preview_player.xml` minus `resize_mode=zoom` (the player must honor the
   `videoFit` pref — the `update` lambda sets `view.resizeMode = fitMode` already,
   `TvPlayerScreen.kt:303`); poster `AsyncImage` under it, same Box arrangement.
3. Nothing else: no new player-state logic, keep `seedContentAspectRatio` (`PlayerScreen.kt:686`,
   `TvPlayerScreen.kt:307`) as-is (interacts with #16 below), keep attach gating, keep
   `useController=false`.

Pinch-zoom note (mobile only): the `graphicsLayer` zoom stays on the AndroidView — the poster
should zoom WITH the video (place the poster inside the same `graphicsLayer`-modified subtree, i.e.
wrap poster+AndroidView in one Box with the existing zoom modifier) so zoom no longer contrasts a
static poster against a zooming video.

### Acceptance criteria
1. Swipe to an unwatched video: the incoming page shows the POSTER through the whole prepare
   window; a Platform screencap at settle shows no solid-black page (platform decode artifacts on
   striped colors are known — see AGENTS-PLAYER "verified end-to-end" note; verify via position
   values + layout dumps as before).
2. Switching away and back: no stale-frame flash from the previous video on the incoming page
   (existing attach gating still holds).
3. With `videoFit = crop` the first frames land in the same screen region as the poster (TV:
   `fitMode` drives ZOOM; check the poster's Fit framing does not visibly jump — poster is
   placeholder, not pixel-perfect; acceptable).
4. Watch-history writes, speeds, error overlay unchanged.
5. Poster-null case: for gifs whose `posterUrl` is null the poster layer simply renders nothing
   and the (black) background remains the truthful state — no invented placeholder behavior.
   State this explicitly in the code comment.

### Risk / notes
- TextureView vs SurfaceView on the full-screen player: TextureView is the same choice already
  shipped inside the feed tiles and previews (device-verified in batches 16/18/22); the fullscreen
  player should not regress either (watch: video-size redraw cost at zoom>1 is a known
  TextureView property — the existing zoom path already runs on rounded Layer surfaces; device
  check covers it).
- Keep both XMLs as separate minimal files; no extraction/sharing refactor (each app's AGENTS gate
  requires its own layout dir; the two are 1-line different in default resizeMode).

---

## #9 — Reduce tap-to-open → playback-start delay (mobile)

**Files:** `feature/feed/.../FeedScreen.kt`, `feature/feed/.../PlayerScreen.kt`,
`feature/feed/.../FeedViewModel.kt`, `app-mobile/.../MainActivity.kt`

### Current behavior (evidence)
- Tap path: `GifTile.onClick = { onOpenPlayer(index) }` (`FeedScreen.kt:718`) →
  `MainActivity.kt:238 onOpenPlayer = { index -> playerStartIndex = index }`
  (`MainActivity.kt:79` rememberSaveable) → MainActivity's `when` swaps `else if (confirmed)`
  FeedScreen branch for the `confirmed && playerStartIndex != null` PlayerScreen branch
  (`MainActivity.kt:222–236`) → **FeedScreen leaves composition**.
- FeedScreen owns the inline autoplay player as COMPOSITION-OWNED state and kills it on exit:
  `FeedScreen.kt:624–629` (`remember(playerFactory) { playerFactory?.create(context) }`, only when
  `autoplayOn = gridColumns == 1 && feedAutoplay && !dataSaver`, line 623) and
  `FeedScreen.kt:630–632` (`DisposableEffect(inlinePlayer) { onDispose { inlinePlayer?.release() } }`).
  So at the moment of tap, the video on the tile is ALREADY decoded and buffered by a live player —
  and it is destroyed exactly when the player screen opens.
- PlayerScreen builds a brand-new `ExoPlayer` every open: `PlayerScreen.kt:159`
  (`val player = remember { playerFactory.create(context) }`) → codec/renderer init is the
  dominant fixed cost, plus `setMediaItem+prepare` (`GiffyPlayer.kt:29–44`),
  plus the Room resume lookup (`PlayerScreen.kt:487–498`, fast), plus (separate, small) the
  sheet-open `syncLikes()` GET (`PlayerScreen.kt:~303`, `FeedViewModel.kt:184–197`).
- The 1-column inline autoplay is the app DEFAULT (`feedAutoplay = true`,
  `SettingsRepository.kt:166–168`), so the common case is tap on the SETTLED tile = the exact gif
  already playing behind the player.

### Root cause / gap
Resource disposal at the worst instant: the warm player is released while its successor is being
constructed, and no hand-off exists. GiffyPlayer already publishes the needed seam
(`currentGifId` flip BEFORE the media swap, `GiffyPlayer.kt:23–27`), and `GiffyPlayerFactory`
exists for the rest — the missing piece is ownership across the two screens.

### Proposed minimal change — "adopt, don't reverify"
Move the SHARED inline player's ownership from FeedScreen composition into the shared
`FeedViewModel` (mobile MainActivity instantiates hiltViewModel at Activity scope —
`MainActivity.kt:232` PlayerScreen and the FeedScreen branch get the SAME `FeedViewModel`
instance):

1. **FeedViewModel** gains a small holder:
   - `private var inlinePlayer: GiffyPlayer? = null` + `fun recallInlinePlayer(): GiffyPlayer?`
     (returns and nulls it) + `fun returnInlinePlayer(p: GiffyPlayer?)` (null → release; non-null
     → re-arm inline profile: `volume = 0f`, `repeatMode = REPEAT_MODE_ONE`,
     `setPlaybackSpeed(1f)` — the knobs FeedScreen's settle block re-asserts today at
     `FeedScreen.kt:646–650`).
   - Rationale for VM ownership: ViewModel survives both screens' compositions (the route is a
     `when` switch, not navigation — batch-22 lesson), removes the dispose-on-leave release
     (`FeedScreen.kt:630–632` becomes a pass-through to `returnInlinePlayer`), and keeps release
     discipline in one place (ViewModel `onCleared`).
2. **FeedScreen** stops `remember`ing/creating the player; it calls a VM accessor gated by
   `autoplayOn` (the same condition, `FeedScreen.kt:623`) and otherwise renders tiles as today.
   Settle logic unchanged once `gridColumns`/autoplay conditions are re-checked (the settle block
   at `FeedScreen.kt:636–680` keeps working against the VM-held instance).
3. **PlayerScreen**: `PlayerScreen.kt:159` becomes "adopt if offered":
   `val player = remember { viewModel.recallInlinePlayer() ?: playerFactory.create(context) }`
   and the existing dispose (`PlayerScreen.kt:196–203`) either releases a fresh player or calls
   `viewModel.returnInlinePlayer(adopted)` — pass which case it holds in the same `remember`.
4. **Continuity on adoption**: in the per-page playback effect (`PlayerScreen.kt:487–498`) skip
   `playGif` when `player.currentGifId.value == gif.id` (the player is ALREADY playing this
   exact gif): keep position, surface attach proceeds through the existing
   `playingId == gif.id` gate (`PlayerScreen.kt:681–683`), and watch-history sampling continues
   from live position. Else (opened from another tile/tab while inline was on a different gif)
   normal `playGif(gif, dataSaver, resumeMs = resume)` runs.
5. **Volume on adoption**: make the mute application effect key on the player instance too —
   `LaunchedEffect(muted, player)` (`PlayerScreen.kt:218`) so the adopted muted-preview player
   gets the user's `muted` pref applied on entry (today's keying would skip a fresh-adopted
   player when `muted` didn't change).

Scope guard: 2/3-column grids have NO inline player (no reuse win) — those opens keep today's
cost; `preloadNeighbors` (`GiffyPlayer.kt:46–76`) still prewarms neighbors in the player. TV is
NOT in this slice (a TV FocusPreview/adoption parity pass is listed as a follow-up candidate only —
not scheduled).

### Acceptance criteria
1. 1-col feed, autoplay on: tap the settled tile → the player screen shows the SAME video playing
   with no visible freeze/black hand-off; time-to-first-usable-frame measured by logcat (codec
   activity starts before the screen swap; compare with/without adoption on device).
2. `watch_history` receives the first ≥5s sample for the adopted gif as today (PlayerScreen
   sampling retained).
3. Back out → grid tile preview resumes muted+looped (return-path restores volume 0, repeat ONE —
   observable by opening the same tile again).
4. Behavior on the non-adopting paths unchanged (grid 2/3 columns, autoplay OFF, data-saver ON).
5. Pinch-zoom/like/mute paths on the adopted player unaffected.

### Risk / notes
- The riskiest item of the batch from an ownership standpoint: note in `AGENTS-PLAYER.md` inline
  section that the shared player is VM-owned now.
- `rememberSaveable(playerStartIndex)`: process death mid-adoption falls back to fresh-player
  behavior — a `GiffyPlayer` must NEVER be in saveable state (explicit non-goal; the code must not
  persist it).
- `setMuted`-on-adopt must not double-apply volume with FeedScreen's settle write; the VM
  `returnInlinePlayer` re-arm + PlayerScreen's keyed effect are the only two writers — confirmed
  no third writer exists today.

---

## #16 — TV: aspect-ratio switch stretches the video for a few moments

**Files:** `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt`,
`core/player/src/main/kotlin/com/rjbiermann/giffyviewer/core/player/SeedContentAspect.kt` (likely no
change), `core/model/.../Gif.kt` `contentAspectRatio()` (no change)

### Current behavior (evidence)
- Switch path: `index` changes (D-pad up/down, `TvPlayerScreen.kt:243–250, 476–491`) → the
  shared single player `playGif`s the next item (`TvPlayerScreen.kt:460–462`) WITHOUT clearing the
  outgoing media first (unlike the FeedScreen inline rule at `FeedScreen.kt:654–660` which
  explicit­ly `stop()`s when `currentGifId` differs).
- Aspect handling at switch: `TvPlayerScreen.kt:300–310` — the `update` lambda re-seeds the
  content frame from the gif's OWN metadata (`view.seedContentAspectRatio(gif.contentAspectRatio())`,
  `SeedContentAspect.kt` → `exo_content_frame.setAspectRatio(ratio)`), with SLICE-16's
  "unknown dims (0) clear the seed" guard. The live `videoSize` path (PlayerView's internal
  `onContentAspectRatioChanged`) later overrides.
- no `stop()`/surface clearing occurs on switch — the OLD video's decoded frame stays on the
  TextureView/SurfaceView player surface until the NEW item's first frame + `videoSize` event
  arrive, and that old frame is being laid out under the NEW gif's seeded aspect → the stretch
  the user sees. A second window exists before the real `videoSize` lands if the rendition box
  disagrees with the seed aspect.

### Root cause / gap
Two overlapping windows, both in the switching code, not in the seed helper:
1. **Stale-frame window** (primary): no clear-on-switch on the shared player — old frames
   re-warped by the new seed.
2. **Seed-vs-rendition window** (secondary): gif width/height metadata is only a placeholder; the
   actual rendition box can disagree → the seeded aspect persists over the first rendered frames
   until `videoSize` corrects. Whether this window is real must be probed live (metadata quality
   is unknown) — see verification list.

### Proposed minimal change
1. **Clear-on-switch**: in the playback effect (`TvPlayerScreen.kt:460–462`), before `playGif`:
   `if (player.currentGifId.value != gif.id) player.stop()` (letter-for-letter the FeedScreen
   inline rule, `FeedScreen.kt:654–660`). `stop()` clears the surface content (poster remains
   truthfully visible thanks to issue #8's texture+poster plane arriving in the same file region;
   if #8 is not landed first, the cleared window would be BLACK — land #8 TV first, see order).
2. NO seed change: keep `seedContentAspectRatio` exactly as shipped (SLICE-14/16 semantics are
   correct and unit-relevant for the pre-videoSize filling of ZOOM/FILL mode); if the live probe
   proves rendition boxes systematically differ from gif metadata, revisit the seed SOURCE
   (later report — do not guess a behavior change now).

### Acceptance criteria
1. D-pad next/prev between black/portrait/landscape pairs: no visible stretch of the outgoing
   frame; the brief pre-video window shows the static poster (or cleared surface) at the correct
   new seed aspect.
2. `RESIZE_MODE_ZOOM` (crop pref) shows the new video filling natural proportions promptly —
   batch-22 T6 crop parity behavior unchanged after the first frame.
3. `watch_history` positions (pergif flush at `TvPlayerScreen.kt:468–489`) unchanged.

### Risk / notes
- `stop()`+prepare-per-switch slightly widens the "poster beat" window (instead of last-frame);
  covered visually by #8's poster plane. The TV has no neighbor prefetch (`preloadNeighbors` is
  mobile-only today) — SimpleCache-missed first plays hold the poster longer; acceptable, and the
  seed still constrains the frame to the target aspect immediately on first frame.
- If the probe finds the REAL cause is a seed/reality mismatch (metadata aspect wrong), the fix
  becomes "stop trusting the seed post-first-frame" — listed under live verification, not
  implemented speculatively.

---

## #20 — TV: disable HDR video playback (SDR normal playback)

**Files:** `core/player/src/main/kotlin/com/rjbiermann/giffyviewer/core/player/GiffyPlayerFactory.kt`
(+ its construction sites `app-tv/.../TvAppModule.kt` / `GiffyApplication`), `core/player/.../GiffyPlayer.kt`

### Current behavior (evidence)
- `GiffyPlayerFactory.create` (`GiffyPlayerFactory.kt:18–44`) builds the ExoPlayer via
  `DefaultPreloadManager.Builder(...).setMediaSourceFactory(...).setCache(...).buildExoPlayer()` —
  NO renderers factory, NO track-selection parameters anywhere in `:core:player` (grep across the
  module: the only player-configuration is the preload manager + customCacheKey policy).
- Track selection therefore runs on ExoPlayer defaults → on an HDR-capable TV box the player will
  happily select an HDR/HDR10/HLG/DolbyVision variant if the stream offers one (HBO-typical
  manifest ladder rule; also possible for a direct import).
- Same factory serves BOTH apps (mobile builds its own factory with `GiffyApplication`-held
  SimpleCache; TV likewise) — the toggle must live at the CONSTRUCTION SITE, not hardcode
  "TV-only" inside `:core:player` (shell-agnostic rule).

### Root cause / gap
No track-selection constraint exists; nothing SDR-pins the TV player.

### Proposed minimal change
1. `GiffyPlayerFactory` gains a constructor flag `sdrPlayback: Boolean = false`, applied once per
   player creation (after `buildExoPlayer()`):
   `player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().<hdr exclusion>...build()`.
2. TV's app module (`TvAppModule`/`TvApplication` factory construction) passes `sdrPlayback = true`;
   mobile keeps the default.
3. **HDR exclusion mechanism — must be verified against the pinned media3 1.11.1 before anything
   is written** (`libs.versions.toml:23,45–46`). Two candidates, in preference order, both
   dependency-free:
   - **(a) parameters-level** IF media3 1.11.1's
     `androidx.media3.common.TrackSelectionParameters.Builder` carries an HDR-era knob
     (verify via `javap`/sources of `media3-common` 1.11.1 in the gradle cache; the build box was
     cacheless during design — item listed under verification). If present, one-liner in
     `GiffyPlayerFactory`.
   - **(b) codec-level** (guaranteed available): a media3 `MediaCodecSelector` wrapper filtering
     out HDR-profile codec infos (drop `MediaCodecInfo` entries whose profile belongs to HDR
     codec profiles: DolbyVision / HEVC HDR10 / HEVC HLG) wired via
     `DefaultRenderersFactory(this).setMediaCodecSelector(...)` passed into
     `DefaultPreloadManager.Builder.setRenderersFactory(...)` (builder supports a renderers
     factory injection — verify exact setter name on 1.11.1). Affects only the renderer choice —
     data source / cache / preload untouched.
   Design does NOT call the exact `androidx.media3.*` symbol (not faked); the implementation PR
   verifies the API before landing.
4. GiffyPlayer stays a thin wrapper; `sdrPlayback` is a factory concern (helpers stay
   non-Android-specific — no `:core:*` TV lemma leak beyond the factory flag which is a plain bool).

### Acceptance criteria
1. On a device/report box where a stream carries an HDR variant (or DolbyVision track), the chosen
   video track is an SDR one — observable via `TrackGroupArray` (player dump on device) /
   codec-init log; playback otherwise unchanged.
2. SDR-only libraries: no behavior change whatsoever (mobile/off-TV streams already SDR; the
   exclusion is a no-op).
3. Unit-test: the only custom logic worth a minimal test is the codec-filter predicate (pure
   `MediaCodecInfo`-ish input set → filtered set) IF candidate (b) is picked; none if (a).
4. ktlint/detekt/tests green in `:core:player` + `:app-tv`.

### Risk / notes
- If the live stream ladder carries NO HDR variants (gif loops are typically SDR MP4/HLS), this
  whole slice becomes a defensive no-op — the live probe (verification list) decides whether there
  is anything to disable; a probe that finds no HDR anywhere should park this slice rather than
  build dead code (spec'd-not-scheduled rule).

---

## #21 — Video fit: keep ONLY Fit and Crop; remove Stretch

**Files:** `feature/settings/.../SettingsScreen.kt`,
`core/datastore/.../SettingsRepository.kt`,
`feature/feed/.../PlayerScreen.kt`,
`app-tv/.../TvPlayerScreen.kt`,
docs: `AGENTS-PLAYER.md` (Video fit paragraph)

### Current behavior (evidence)
- Pref: `SettingsRepository.kt:169–172` — `videoFit: Flow<String>` (values `fit | crop | stretch`,
  default `"fit"`), `setVideoFit`. Raw string storage, no enum/validation, NOT part of the
  content-prefs backup (`ContentPrefsBackup.kt` — no reference).
- UI: `SettingsScreen.kt:404–435` — three FilterChips `"Fit"|"Crop"|"Stretch"`
  (`SettingsScreen.kt:421`).
- Player mapping:
  - Mobile: `PlayerScreen.kt:475–479` — `"crop" → RESIZE_MODE_ZOOM`,
    `"stretch" → RESIZE_MODE_FILL`, else FIT; applied at `PlayerScreen.kt:680`.
  - TV: `TvPlayerScreen.kt:88–91` — same mapping; applied at `TvPlayerScreen.kt:303`.
- Doc: `AGENTS-PLAYER.md` "Video fit: user setting Fit / Crop / Stretch" + TV note.

### Root cause / gap
User decision: Stretch (RESIZE_MODE_FILL) is a destructive distortion mode the app no longer wants.
Removal is presentation-only — the pref is free-form, so any stored legacy value must simply stop
mapping to FILL.

### Proposed minimal change
1. `SettingsScreen.kt:421` — drop the `"Stretch" to "stretch"` entry (chips list =
   `listOf("Fit" to "fit", "Crop" to "crop")`); update the comment/copy
   (`SettingsScreen.kt:405–409`).
2. `PlayerScreen.kt:478` and `TvPlayerScreen.kt:90` — delete the `"stretch" -> RESIZE_MODE_FILL`
   branches (the `else -> RESIZE_MODE_FIT` arm already neutralizes stored-on-disk `"stretch"`
   values → a user who previously chose Stretch lands on Fit with no migration and no crash).
   Update the §5 comment lines above each mapping.
3. `SettingsRepository.kt:169–172` — keep the key/storage UNCHANGED (existing values, incl.
   legacy "stretch", de-map gracefully); update the comment to `fit | crop`.
4. `AGENTS-PLAYER.md` — rewrite the Video-fit bullets to "Fit / Crop"
   (`AGENTS-PLAYER.md:47–55`) including the TV note (fill/zoom semantics stay Crop's own).
5. No data migration, no backup change (videoFit is not exported), no DB touch.

### Acceptance criteria
1. Settings shows exactly two chips on both shells; choosing Crop still maps to ZOOM everywhere.
2. A DataStore value of `"stretch"` (set via a debug paste or leftover install) renders as FIT on
   both mobile and TV (no FILL path anywhere — grep-left-zero of `RESIZE_MODE_FILL` outside test
   code).
3. Pinch zoom still 1×–3× on top (mobile), unchanged.
4. No unit test mandated — the mapping is a when-arm deletion; `:feature:settings`, `:feature:feed`,
   `:app-tv` gates green.
5. The shared SettingsScreen device pass doubles as the TV check (row already TV-verified in
   batch 12 chip-skip work — keep the `focusGroup` shape of the row untouched).

### Risk / notes
None beyond user expectation (their old choice silently becomes Fit — deliberate).

---

## #24 — TV: playback-failure overlay Retry/Skip not focusable/clickable

**Files:** `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt`

### Current behavior (evidence — concrete file:line)
The overlay exists with the correct widgets but can never win focus:

1. **Focus is permanently pinned to the player's root**:
   `TvPlayerScreen.kt:159–164` — `FocusRequester` + `LaunchedEffect(Unit) { playerFocus.requestFocus() }`
   on the root `Column` (`.focusable().focusRequester(playerFocus)`, `TvPlayerScreen.kt:170–171`).
   Nothing ever moves focus when `playError` flips (`playError = true` is a bare state write,
   `TvPlayerScreen.kt:496–498`).
2. **The root's keymap pre-consumes every key that could move focus**, in the PREVIEW phase
   (root `onPreviewKeyEvent` traps events before the overlay subtree sees any of them):
   - Up/Down KeyUp: `TvPlayerScreen.kt:243–250` —
     `Key.DirectionDown -> { if (index < gifs.size - 1) index++; true }` and
     `Key.DirectionUp -> { if (index > 0) index--; true }` — always return `true`.
   - Center: KeyDown always consumed (`TvPlayerScreen.kt:204–207`, `centerHold.down()`), KeyUp
     consumed at `TvPlayerScreen.kt:261–272` (tap = play/pause or suppress).
   With the root consuming these, Compose's directional focus search cannot run, and a focused
   overlay `TextButton` would never receive ENTER/CENTER to click.
3. The buttons themselves are fine (NOT the bug): both Retry (`TvPlayerScreen.kt:342–357`) and
   Skip (`TvPlayerScreen.kt:358–376`) are M3 `TextButton`s with the shared `giffyFocus`
   (`core/ui/.../Focus.kt:35` — border+fill interaction modifier, the batch-12-proven pattern) and
   valid `interactionSource`s. They compose inside the `playError` overlay Column
   (`TvPlayerScreen.kt:330–339`).

### Root cause
The overlay is reachable-by-composition but unreachable-by-focus: the single focused node (root
Column) owns all navigation keys, and focus never leaves it when the error state appears.

### Proposed minimal change
1. **Focus hand-off**: when the overlay becomes visible, move focus into it — add
   `val overlayRetryFocus = remember(gif.id) { FocusRequester() }`, a `LaunchedEffect(playError)`
   that `overlayRetryFocus.requestFocus()` when `playError == true`, and
   `Modifier.focusRequester(overlayRetryFocus)` on the Retry `TextButton` (Skip stays focusable by
   traversal — Up/Down between the two buttons is one row apart).
2. **Let the keymap stand down while the overlay is up**: at the TOP of the root
   `onPreviewKeyEvent` (`TvPlayerScreen.kt:172–214`) — after the always-harmless
   `textVisible = true` KeyUp line —
   `if (playError) return@onPreviewKeyEvent false` (i.e., every D-pad key, incl. Up/Down/Center,
   falls through to the focused overlay buttons while the error panel shows; the play/pause Menu
   route is not needed while a video is failing).
   - Keys consumed ONLY by the overlay buttons: Up/Down move focus Retry↔Skip; Center/Enter
     activates a focused button; BACK is unaffected (system back still exits the player via the
     Root `BackHandler`, TvMainActivity.kt:~110–124 — decide: keep BACK = exit-player as today).
3. **Focus restoration on overlay close**: skip/Settle clears `playError` (`TvPlayerScreen.kt:347,
   365 —`via the buttons' own onClick`) — after the overlay leaves composition the focused node
   disappears → re-request the root focus: one `LaunchedEffect(playError)` branch calling
   `playerFocus.requestFocus()` when `playError == false` post-composition posting is safe
   (requestFocus on the root that was already granted once this composition).

### Acceptance criteria
1. Force a failure (airplane mode / bad token / nonexistent stream — recipe used for the mobile
   error overlay in batch 21) → the overlay appears WITH the Retry button focused (a uiautomator
   dump shows `focused="true"` on Retry, `giffyFocus` ring in a zoomed screenshot, batch-12
   precedent).
2. DOWN/UP walks Retry↔Skip (no gif advance while the overlay is up).
3. CENTER on focused Retry re-attempts the gif (playGif wired already at
   `TvPlayerScreen.kt:349–353`); CENTER on Skip advances (`TvPlayerScreen.kt:364–371`) and
   playback resumes on the next gif, root focus restored.
4. Walking with Left/Right while FAILED and overlay DISmissed still seeks (unmask only while
   `playError`).
5. No regression to the mobile overlay (`PlayerScreen.kt:790–820`) — mobile is touch-only.

### Risk / notes
- `requestFocus` from a `LaunchedEffect` on error is the same pattern the batch-18 shared
  TextField found necessary (`requestInitialFocus` on CustomFeedsScreen) — precedent, not a new
  device lesson.
- Slight behavior change while the overlay is up: the gif NO longer advances/backs with Up/Down
  (that was never really happening for a broken video — the error overlay pinned playback; the new
  behavior is more honest). BACK semantics preserved.
- The D-pad walk into the error overlay is disabled BY DESIGN in favor of focus-back on buttons
  (matches the acceptance suite; note it in the keymap section of AGENTS-APP would be the natural
  doc spot — listed below).

---

## #26 — Mobile mute/unmute "from quick actions" restarts/refreshes the feed

**Files:** `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedRepository.kt` (fix),
`feature/feed/.../FeedViewModel.kt` (context), `feature/feed/.../PlayerScreen.kt` (context)

### Data-flow trace (all of it)
1. Mute write: the action rail's Sound button →
   `PlayerScreen.kt:814 onToggleMute = { onToggleMute(!muted) }` →
   `PlayerScreen.kt:372 on = { muteScope.launch { settings.setMuted(on) } }` →(DataStore)
   `SettingsRepository.setMuted` (`SettingsRepository.kt:74–77`, key `muted`).
   (Note: the mobile "quick actions" sheet — `QuickSheet.kt` — currently has NO mute row; mute
   lives only on the player action rail, `PlayerScreen.kt:1034–1041`. The user's "quick actions"
   refers to this rail/overflow area; the SHEET does not trigger it.)
2. Volume application is fine and instant: `PlayerScreen.kt:218`
   `LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }`.
3. **The restart**: DataStore re-emits the WHOLE `Preferences` map on ANY key write. Every derived
   flow in `FeedRepository.paging` re-emits with unchanged values:
   - cached feed branch: `FeedRepository.kt:191–196` —
     `combine(orientationFilter, verifiedOnly, feedPrefs(feed.baseKey)) → flatMapLatest { cachedPager }`
   - network-live branches: Liked `FeedRepository.kt:143–147`, ForYou `FeedRepository.kt:162–166`
     — same shape.
   - Continue `FeedRepository.kt:212–217`.
   `flatMapLatest` switches the pager on EVERY upstream emission — including the muted write's
   identity emission. The live pager (and for network-live sources, in-flight page 1 requests) is
   torn down and rebuilt → the feed visibly "restarts/refreshes" while only the mute flag changed.
4. The SAME class of DataStore write incidentally touches the VM-side invalidation too — but THAT
   path is already protected: `FeedViewModel.kt:471–509` explicitly runs
   `drop(1).distinctUntilChanged().debounce(250)` before `forceRefresh()`. The repository-side
   combine holds NO such guard — that's the gap.

### Root cause
Missing `distinctUntilChanged` on the pager-restart combine in `FeedRepository` — any unrelated
DataStore key write (muted, autoSwipe, feedAutoplay, gridColumns, …) restarts every pager.

### Proposed minimal change
In `FeedRepository.paging` and `continueWatchingPager`, insert
`.distinctUntilChanged()` between each `combine(...) { ... }` and `.flatMapLatest { ... }`
at the four sites (`FeedRepository.kt:126–130, 143–147, 162–166, 191–196, 212–217`). Values are
`Pair`/`Triple` of String/Boolean/data-class — structural equality does the job (note: needs the
`kotlinx.coroutines.flow.distinctUntilChanged` import already... check imports — `combine` there
is `kotlinx.coroutines.flow.combine`; add the one stdlib-flow import).

Deliberately NOT done:
- Making mute player-local/non-persistent (the pref is deliberately global+persistent — PLAN §9,
  "mute applies across sessions", and TV quick actions share it, `TvQuickActions.kt:219–224`).
- Touching the write site (`SettingsRepository.setMuted`) — the write must stay a normal DataStore
  write; the READ side must be resilient (the root fix).

### Acceptance criteria
1. Unit test (the valuable one): a fake DataStore-ish source... the existing tests target pure
   rules; a focused new test in `:feature:feed` (small `_test` style, one-test rule): drive a
   `combine` under test OR assertethe pager-FIN flag? — the cheapest honest check is a VM-level
   test: mute toggle's emission does NOT change the paging restart count (observe via a counting
   wrapper on the paging-factory invocation, FakePagingSource style used in existing tests
   e.g. `KenNetworkPagingSource` tests). If too invasive, assert at minimum the
   `distinctUntilChanged` semantics in isolation (combine-output equality) — one test, the
   no-framework-suite rule.
2. On device: open the player from a CACHED feed, tap the Sound icon — playback continues
   (the grid behind is invisible while the player is up but observable on back-out: grid scroll
   position and content intact, no re-fetch of page 1, rate budget untouched).
3. Toggle mute twice quickly → single volume change, no second restart.
4. An actual orientation/verified-only/sort change and per-feed Filter change STILL restart the
   pager (regression direction: the dedupe must not eat real changes — Triple values differ for
   real filter moves).
5. TV mute row unaffected behaviorally (muting while a TV feed is open also no longer
   needlessly restarts — the same repository code path).

### Risk / notes
- distinctUntilChanged on the cached branch compares `FeedPrefs` (data class) + strings — safe.
- The mute restart ALSO was silently refetching page index/anchors under the live player for
  Liked/ForYou (network-live sources) — network requests burned per mute flip; the fix also
  removes that rate-budget leak (good news for the ≤15/5s invariant, not a violation).
- Do NOT dedupe inside `feedPrefs`' JSON decode (parse-only observations) — dedupe at the combine
  exit, values compared structurally; a same-content re-persist of identical prefs is also
  suppressed (desired).

---

## #27 — Mobile unfollow (quick action) does nothing

**Files:** `core/network/src/main/kotlin/com/rjbiermann/giffyviewer/core/network/GifsApi.kt` (suspect),
`feature/feed/.../FeedViewModel.kt`, `feature/feed/.../QuickSheet.kt`;
(`app-tv/.../TvQuickActions.kt` shares the same VM code — a fix lands both shells)

### Full call chain + trace
1. Row wiring: `QuickSheet.kt:136–145` — visible only when `viewModel.loggedIn`
   (`FeedViewModel.kt:335–338`, token`, label reads `followedCreators` (`QuickSheet.kt:55,
   collected from `FeedViewModel._followedCreators`), onClick:
   `viewModel.toggleFollowCreator(gif.userName); onDismiss()` (silent fire-and-close).
2. Server read on sheet open: `QuickSheet.kt:54` `LaunchedEffect(Unit) { viewModel.refreshFollowedCreators() }`
   (`FeedViewModel.kt:345–350`, runCatching, silent on failure).
3. The VM toggle: `FeedViewModel.kt:356–370` —
   ```kotlin
   val wasFollowing = username.lowercase() in _followedCreators.value
   runCatching { if (wasFollowing) api.unfollowCreator(username) else api.followCreator(username) }
       .onSuccess { _followedCreators.value = ±username }
   ```
4. The network layer: `GifsApi.kt:177–187` —
   - `followCreator`: `@PUT("v1/me/follows/{username}")` + `@Body FollowBody = FollowBody()`
   - `unfollowCreator`: `@DELETE("v1/me/follows/{username}")` + `@Body FollowBody = FollowBody()`
   FollowBody JSON is correct (`GifsApi.kt:393–398`, `encodeDefaults = true` in
   `NetworkModule.kt:28–33` — the like-drift lesson applied to @Body DTOs).

**The break candidates, ranked (this issue must be confirmed live before the fix is trusted):**
1. **The DELETE needs the Retrofit-declared-body shape used everywhere else in this file.** The
   corresponding UNLIKE write was live-fixed to `@HTTP(method = "DELETE", hasBody = true)`
   (`GifsApi.kt:167–170`) — the documented lesson (`AGENTS.md` like-write drift). `unfollowCreator`
   is the only DELETE+body left on the plain `@DELETE` annotation (grep: both `subscribeNiche`'s
   DELETE carries `@Body EmptyBody` too, `GifsApi.kt:190–195` — same class). If Retrofit/serializer
   drops or mangles the body on `@DELETE`, the server 400s ("bare DELETE → 400", AGENTS-NETWORK
   writes rule) → `runCatching` swallows → the UI flips nothing: **"unfollow does nothing"**,
   while FOLLOW (PUT) works. This is the only place the symptom can distinguish UNfollow from
   follow.
2. **The refresh/write race**: `refreshFollowedCreators` (launched on sheet open) can complete
   AFTER a toggle's optimistic flip and overwrite `_followedCreators` with the pre-write server
   list (`FeedViewModel.kt:345–350` vs `356–370` — concurrent coroutines, last-write-wins) →
   re-opened sheet shows the stale "Unfollow" even though the DELETE succeeded. Perceived as
   "does nothing".
3. **Everything is silent**: neither toggle failure (1) nor refresh failure (2) nor a token-expiry
   path surfaces ANY feedback; `onDismiss()` runs regardless (`QuickSheet.kt:145`).
4. NON-issues verified in trace: gating (anonymous hidden via `viewModel.loggedIn` and
   `tokenOrNull()` inside toggle — same source, no eager-null mismatch), casing (toggle uses
   `lowercase()` like the refresh — `Gif.userName` casing can't desync), FollowBody defaults (live
   encodeDefaults lesson already applied).

### Proposed minimal change (order: prove-then-patch; see live verification)
1. **Match the proven shape**: change `unfollowCreator` to
   `@HTTP(method = "DELETE", path = "v1/me/follows/{username}", hasBody = true)`
   (`GifsApi.kt:183–187`) — same normalization that fixed unlike (`GifsApi.kt:167–170`). Harmless
   if redundant against modern Retrofit, decisive if Retrofit behavior differs from like's.
   (`GifsApi.kt:190–195 subscribeNiche/unsubscribeNiche` same-shape note: subscribe is POST — only
   its DELETE twin carries the same suspicion; fix the same line in the same pass, same lesson.)
2. **Serialization of the write into VM state** (race fix, shared FeedViewModel):
   - Suppress overlapping refreshes while a toggle is in flight: guard both `refreshFollowedCreators`
     and `toggleFollowCreator` with a single-flight token (an in-flight `Boolean`/`Mutex` in
     FeedViewModel) — the refresh result must not overwrite a just-issued flip: simplest correct
     variant: `toggleFollowCreator` sets an in-flight flag; `refreshFollowedCreators` skips
     overwriting while in-flight (and the in-flight toggle still applies its own optimistic set).
   - Surface failure minimally: on toggle failure (non-2xx / IO), DON'T flip (already the case)
     and DON'T silently dismiss the sheet on failure — keep it open with the row in its
     pre-toggle state; a user retrying sees the row not flip. No new toast/snackbar subsystem is
     invented for one row (fallback rule — no spec'd UI surface).
   - Await the write before dismiss: `QuickSheet.kt:144` currently `onDismiss()` runs
     synchronously; with (2), the sheet can close immediately (the optimistic set stays
     authoritative when no refresh is in flight).

### Acceptance criteria
1. Live-verified follow/unfollow round-trip both ways (needs a fresh real token bundle — batch-19
   open item): open sheet → row says Unfollow → tap → re-open (or reopen after app restart) → row
   says Follow; and the identical assertable on TV (`TvQuickActions.kt:276–277` calls the same VM).
2. The row does NOT flip when the write fails (a bad token forced via airplane-mode mid-write):
   reopening shows Follow still.
3. Refresh-overwrite race: toggling within the refresh window does not resurrect the stale state
   (guarded by the in-flight flag; unit-testable with a fake api + fake DAO-less flow — mark as
   the one unit test for this issue).
4. Backed by the same_vm: TvQuickActions follow flip verified once tokens are available (batch-19
   queue).

### Risk / notes
- Live python REQUEST probing is required: issue's listed "needing live verification" — the exact
  failure is not provable from the repo alone. The @HTTP normalization and the race guard are both
  safe against all candidate causes; implement together, verify together (batch-23 rule: verify
  with the live environment, not against assumption).
- No retry/UI-word change; the lingo stays Follow/Following row pattern.

---

## #4 — Preferred video quality pref (shared player + settings)

**Files (design):** `core/network/dto/GifDtos.kt` (read-only finding),
`core/datastore/.../SettingsRepository.kt`, `feature/settings/.../SettingsScreen.kt`,
`core/player/.../GiffyPlayer.kt` (+ factory), `feature/feed/.../PlayerScreen.kt`,
`app-tv/.../TvPlayerScreen.kt`

### Rendition metadata finding (the FIRST thing the issue demands — done)
- **The DTO set contains NO rendition list.** `UrlsDto` (`GifDtos.kt:73–78`) exposes exactly two
  stream URLs per gif — `sd`, `hd` (+ `poster`): so the API's per-gif stream vocabulary as
  modeled is TWO qualities.
- No other media/quality endpoint exists in `GifsApi.kt` (grep: no `quality|rendition|variant|
  height` surface; the only height-like fields are gif metadata `width/height`,
  `GifDtos.kt:29–30`).
- The payload carries an UNPARSED `hls` boolean (documented live at
  `AGENTS-NETWORK.md:10` — images are `hls:false`, `urls.sd/hd` are jpgs there
  (`AGENTS-APP.md:651`)) — **`GifDtoShell` does not parse `hls`** (absent,
  `GifDtos.kt:15–38`), so the repo cannot currently tell whether `urls.hd` is a direct MP4 or an
  HLS master with a real ladder. `AGENTS-PLAYER.md:18–20` describes a spec'd HLS→MP4 fallback —
  that fallback is NOT implemented in `GiffyPlayer.playGif` (`GiffyPlayer.kt:29–44` is a single
  `setUri`).
- **Conclusion: with sd/hd only, a discrete "1080p/720p/…" pref menu would be a guess until the
  live manifest probe answers what a ladder the hd URL serves.**

### Proposed design
1. **Pref (add now, meaningful under both probe outcomes):** `video_quality` string
   (`auto | highest | 720 | 480`) in `SettingsRepository` beside `videoFit` (mirror its shape:
   `Keys.VIDEO_QUALITY`, `Flow<String>` default `"auto"`, `setVideoQuality`). No backup entry
   (same policy as videoFit — player shell preference, not content filter).
2. **Settings entry:** a "Video quality" chip row in the shared SettingsScreen under the
   `video-fit` item (`SettingsScreen.kt:404–435`) — mobile+TV in one edit (the shared screen is
   TV-hosted since batch 12). Copy honest: "Only available choices depend on the video; Auto is
   the default."
3. **Player application (shared `:core:player`):**
   - `GiffyPlayer.playGif(gif, dataSaver, resumeMs, quality)` — extend the pick:
     - dataSaver twin-count rule preserved FIRST (`Gif.streamUrl` semantics
       `Gif.kt:35–38` — dataSaver forces SD stream; documented AGENTS-PLAYER rule);
     - non-dataSaver: `highest` → prefer `hdUrl` (≈ today's default `hdUrl ?: sdUrl`);
       `720`/`480` → prefer `hdUrl` + set a Media3 `TrackSelectionParameters.maxVideoHeight` cap
       (720/480) so a multi-rendition manifest downsizes within the cap; `auto` → today.
       DATA-RULE: a cap that can't match any rendition must not kill the track — clamp the cap at
       runtime from the player's first `Tracks` report (tallest available video height) — one
       listener (`onTracksChanged`) in `GiffyPlayer`, remembered per-playback; note: applies only
       when a Manifest ladder is live-verified (implementation gate, see below).
   - Wiring: both `PlayerScreen` and `TvPlayerScreen` collect `settings.videoQuality` and pass it
     into the `playGif` call sites (`PlayerScreen.kt:498,381`; `TvPlayerScreen.kt:462,351`)
     and re-apply on pref change (a `LaunchedEffect` re-plays nothing but sets track parameters
     mid-stream where supported).
4. **SDR-style single-rendition case (probe outcome "hd is a plain MP4")**: the pref reduces to
   Auto / Highest / SD (= prefer `sdUrl`); 720p/480p entries hidden from the Settings row
   conditionally is NOT possible statically → keep the menu at
   `auto | highest | 720 | 480`? NO — with single-rendition streams a height cap DEGRADES into
   "no track" (rejected by the clamp rule above, giving no change at all) — the honest design for
   that outcome: ship `auto | highest | 480` where 480 == the sd stream (rename visible copy
   "SD"); discrete 720/1080 appear ONLY if the live manifest probe finds a ladder. This is a
   build-time VALUE SET decision governed by the probe (verification list item 1) — the design
   intentionally does NOT hardcode the final menu strings.

### Acceptance criteria
1. Settings shows the pref on BOTH shells; choosing a value persists across process death.
2. Against a ladder-carrying stream: choosing 720 caps the selected rendition ≤720 at the
   `(Tracks)`-observable level; Highest forces the top; Auto leaves ABR free.
3. Against single-rendition streams: Auto/Highest unchanged; the SD choice uses `sdUrl` (no
   network difference vs dataSaver's sd, but independent of dataSaver state).
4. DataSaver ON always yields the SD stream (order-of-paid rule; both prefs compose without one
   clobbering the other).
5. Rate-limit budget untouched (no extra requests added).
6. One small self-check for whichever pick logic ends up pure (e.g. `qualityToTrackHeight(
   quality, tallestKnown)` clamp rule — a pure function, ONE test).

### Risk / notes
- The single undeclared variable is the live payload ladder (probe item). Building the full
  discrete menu before it would violate the no-guess rule; the design keeps the surface
  honest-shrinkable in the same slice.
- TrackSelectionParameters on a single-track MP4 with an impossible cap is a no-selected-track
  hazard — the clamp rule covers; alternative "don't cap" was preferred?! (No — the cap is what
  makes the pref real; the clamp is the guard.)

---

## Implementation order (grouped by file to minimize churn)

Wave 1 — small, independent, low-risk (each module green before next):
1. `FeedRepository.kt` — `distinctUntilChanged` at five combine→flatMapLatest sites (#26 fix).
2. `PlayerScreen.kt` — tap-visibility toggle (#2); ALSO in the same module pass Do the
   `videoFit` stretch-arm deletion + comment (#21) and `muted` effect re-key (`#9` sub-piece, if
   wave 3 already planned) — group PlayerScreen edits together once.
3. `SettingsScreen.kt` — remove Stretch chip (#21); add the `video_quality` row only when its
   value-set is decided (#4 wave).
4. `SettingsRepository.kt` — videoFit comment update (#21); `video_quality` pref (+ setter) when
   #4 lands (#4).
5. `TvPlayerScreen.kt` — stretch-arm deletion (#21); error-overlay focusability (#24, needs a
   device pass); poster+texture surface (#8 TV); stop-on-switch (#16). These four all touch the
   same `update`/AndroidView region — sequence them in ONE TvPlayerScreen editing session with one
   device pass.
6. `GifsApi.kt` — `@HTTP(hasBody=true)` normalization for unfollow (+unsubscribeNiche) (#27);
   `FeedViewModel.kt` single-flight + no-flip-on-failure (#27). Module setters same commit.
7. `TvQuickActions.kt` (+ two call sites) — the hold-open keyup swallow (#3).
8. `GiffyPlayerFactory.kt` (+ TV construction site) — `sdrPlayback` flag (#20, gate on live probe).

Wave 2 — cross-cutting, gated on Wave-1 verification:
9. `FeedViewModel.kt` / `FeedScreen.kt` / `PlayerScreen.kt` / `MainActivity.kt` — inline-player
   hoist+adopt (#9) — the largest change; land after #2/#8 mobile pieces are device-verified so
   one PlayerScreen fingerprint moves once.
10. `GiffyPlayer.kt` (+ both screens' playGif call sites) — quality pref application (#4) after
    the live manifest probe decides the menu value set.

Docs per wave: AGENTS-PLAYER.md (#21 stretch removal sentence; #9 shared-player ownership
paragraph; #4 pref) and AGENTS-APP.md (TV error-overlay D-pad rule, #24; hold-panel keyup note, #3).

## Items needing live-site / live-device verification
1. **#4 (critical)**: probe what `urls.hd` actually is (direct MP4 vs HLS master) and, if
   manifest, the REAL rendition ladder heights (Playwright/HEAD probe like batches 6/16; also
   record whether `hls` should be parsed once). Decides the pref value set and whether Media3
   track caps are meaningful.
2. **#20**: probe whether any gif stream presents HDR variants; if none, park the slice
   (spec'd-not-scheduled rather than dead code). Also metric-level: `TrackSelectionParameters`
   field list of media3 1.11.1 (javap of the AAR) — checklist during implementation.
3. **#24**: device pass on TV36 — overlay focus/click cycle via `uiautomator dump`
   (+ zoomed screenshot for the giffyFocus ring).
4. **#3**: device pass on TV36 for BOTH open paths (player-version rows and card rows); also
   re-verify MENU-open unaffected.
5. **#26**: device pass Medium_Phone — mute toggle with the player open, grid intact after BACK;
   unit test on the dedupe semantics.
6. **#9**: device-measured TTFF improvement with/without adoption + full return-path (mute fan
   out); also re-check batch-22's inline-stall regression class on the adopted player.
7. **#27**: fresh REAL token bundle (batch-19 queue) → follow/unfollow round-trip on Device and
   live-server-side state; live HTTP probe of `@DELETE`-vs-`@HTTP` body behavior.
8. **#16**: device pass + optional metadata-vs-rendition-box probe (see root-cause window 2).
9. **#21**: post-change release smoke on both apps is mandatory per project rule (mapping change;
   R8 config untouched — no R8 work expected).

## Ambiguities / decisions wanted
1. **#27's exact server behavior** — will be settled by the probe + token bundle; the fix wave
   intentionally follows verification (not before it).
2. **#4 menu value set** — contingent on the manifest probe: honest-shrinkable design, but the
   FINAL strings (1080p? 720p? SD-only?) are a probe outcome, not a design decision here.
3. **#24 BACK semantics while the error overlay is up** — design keeps BACK = exit-player
   (current behavior); alternative "BACK dismisses overlay first" stays a one-line change if the
   user prefers.
4. **#9 adoption vs. fresh-player on non-matching tap** — when the inline player is playing a
   DIFFERENT gif than the tapped tile (multi-tab path), design re-checks currentGifId and
   reuses the player for the NEW gif (still saves codec init); a purist "fresh player for
   different content" variant is available at small cost — either satisfies the issue; the
   design picked reuse-always (simpler, one player).
5. **#2 parking-speed UX**: the design keeps pause/play only on the visible control row; if the
   user also wants a hidden-state long-tap or keyboard pause, that's a separate slice.
6. **#26 mute scope stays global-persistent** (PLAN §9) — if "mute only the current video"
   is read literally to mean a per-video session flag instead, that is a different feature (and
   would reintroduce the restart problem); the design assumes the literal fix is the restart.
