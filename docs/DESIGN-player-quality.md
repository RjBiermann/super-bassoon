# DESIGN-player-quality (Stream A) — issues #56, #55

Owner modules: `:core:model`, `:core:player`, `:feature:feed` (mobile player). No shell changes.

## Objective

- **#56** — selecting the "Highest" preferred quality must actually play HD video; today an SD rendition can be replayed from cache.
- **#55** — playback at slow speeds (≤1×, 0.25 steps) must be smooth; user reports non-smooth playback at slow speed.

---

## Issue #56 — "highest quality setting must play HD video"

### Current behavior + evidence

The quality pref is a **stream-URL pick at prepare time** (no track machinery — the live
manifest probe of 2026-10 closed `urls.hd`/`urls.sd` as single-rendition faststart MP4s):

- `core/model/src/main/kotlin/com/rjbiermann/giffyviewer/core/model/VideoQuality.kt:46`
  — `qualityStreamUrl()`: `dataSaver → sd ?: hd`; `quality == SD → sd ?: hd`; **else
  `hd ?: sd`**. So `highest`/`auto` map to `urls.hd` when it exists
  (`GifDtos.kt:82–86`, mapping at `GifDtos.kt:127–128`).
- `core/player/src/main/kotlin/com/rjbiermann/giffyviewer/core/player/GiffyPlayer.kt:42–48`
  — `playGif()` builds a `MediaItem` with `setCustomCacheKey(gif.id)` —
  **the cache key is the gif ID for BOTH renditions**, and the same key shape at the preload
  path (`GiffyPlayer.kt:77–81`).
- `core/player/src/main/kotlin/com/rjbiermann/giffyviewer/core/player/GiffyPlayerFactory.kt:45–49`
  — `CacheDataSource` with `FLAG_IGNORE_CACHE_ON_ERROR` only: a clean cache **hit**
  under `gif.id` is served verbatim; mismatched URL bytes are not revalidated
  (ProgressiveMediaSource trusts the cached byte range).
- **The SD-under-the-same-key path is exercised deliberately:** TV focus preview
  (`app-tv/.../tv/FocusPreview.kt:46–76`) plays every settled card with
  `dataSaver = true` (SD, the comment literally says "The gif-id cache key means the
  real player later replays from cache"), and TV home cards settle constantly during
  D-pad browsing. Mobile: the inline autoplay player always plays with the default
  HD pick (`FeedScreen.kt:595`), so its collision path needs a data-saver session.

### Root-cause hypothesis (high confidence)

**Rendition-byte aliasing in SimpleCache.** A gif's SD MP4 gets cached under cache key
`gif.id` (TV focus preview, or any data-saver watch). Later, the same gif is opened with
`videoQuality = highest` → player requests the HD URL → `CacheDataSource` resolves
`customCacheKey = gif.id` → returns the **cached SD bytes**. The user sees SD despite
"Highest". This exactly matches "highest quality setting must play HD video" as an
  after-browsing intermittent state and requires no server excuse.

Secondary possibility (needs live probe, see below): the server's `urls.hd` may genuinely
not be HD-rendered for some gifs; then no local fix helps and the setting is cosmetic.

### Proposed approach (minimal)

1. **Namespace the cache key by rendition** — in `GiffyPlayer.playGif`/`preloadNeighbors`
   (`GiffyPlayer.kt:48,81`) derive the custom key from the *picked URL class*:
   `"$id:hd"` vs `"$id:sd"` (mirror the exact same decision `qualityStreamUrl` makes —
   pass the rendition tag alongside the URL, or have `qualityStreamUrl` return
   `(url, tier)`; keep it a one-field choice, no new types). Old un-namespaced entries
   simply become dead bytes evicted by LRU (no migration).
2. Keep `VideoQuality` vocabulary unchanged (Auto/Highest/SD stays the one menu list).
3. **Highest ≠ Auto is the second half:** today `highest` and `auto` are identical
   (`VideoQuality.kt:46` ties both to `hd ?: sd`). If the live probe confirms `hd` is
   real HD, leave the URL logic untouched; if streams additionally expose an HLS master
   (unknown today, see probe), that is a follow-up row in `UrlsDto` — do **not** hand-roll
   a variant-picker now.

### Acceptance conditions

- Unit test (JVM, `:core:player`): rendition-tag function maps (dataSaver, quality,
  sd/hd presence) → expected cache-key tier; no `<id>:hd` key ever reads `<id>:sd` bytes.
- Existing tests updated where cache-key shape is asserted (grep `setCustomCacheKey`
  usages — `GiffyPlayer`, preload, the `PlayerScreenMachineTest` fixtures).
- Device gate (TV emulator): TV — focus-settle a card (SD preview), then open the
  player with quality=Highest → player plays HD (logcat MediaCodec reinit + distinct
  cache entry; second check: `ls cache` shows both `:sd` and `:hd` keys after watching
  both renditions of one gif). Mobile — data-saver ON watch → OFF → reopen same gif
  with Highest → HD bytes fetched (network log), not SD replay.
- `:core:model`, `:core:player`, `:feature:feed` gates green (compile + ktlint + detekt
  + unit).

Explicitly a **live-probe flag**: before claiming #56 closed, one session of live
requests against the site for a known-HD gif must show the hd url's height/bitrate
distinct from sd (the 2026-10 probe closed `hd`/`sd` as separate MP4 URLs but did not
verify the hd rendition's resolution parity with what the site serves in its own player).

### Non-goals

- No TrackSelectionParameters / ABR machinery (single-rendition MP4s — already closed 2026-10).
- No HLS adoption, no new settings UI, no per-gif quality override.
- No cache migration for pre-existing un-namespaced entries (LRU eats them).

---

## Issue #55 — "smooth playback at slow playback speed"

### Current behavior + evidence

- Speed is applied in exactly one mobile point:
  `feature/feed/.../PlayerScreen.kt:207–213` — `player.setPlaybackSpeed(if (holdSpeed) HOLD_SPEED else speed)`
  in a `LaunchedEffect(speed, holdSpeed)`; QuickSheet slider = 0.25 steps, range
  0.5–2× (`QuickSheet.kt:127–128`, label helper `QuickSheet.kt:432`).
- TV equivalents: `app-tv/.../TvPlayerScreen.kt:144–147` (`speed` state) and
  `TvPlayerScreen.kt:618–628` (immediate `player.setPlaybackSpeed(speed)` on adjust).
- No playback-parameter reset, no ABR interplay, no custom frame-source pipeline:
  `GiffyPlayer.kt` is a thin ExoPlayer wrapper; media3 1.11.1 (`gradle/libs.versions.toml:30`).

### Root-cause hypothesis (low confidence — repro first)

No implementation defect is visible in the speed application path: a single
`setPlaybackSpeed` point, session-only, default ExoPlayer audio/video
`PlaybackParameters` handling. ExoPlayer renders video frames at the decoder output
rate regardless of speed; jank at 0.5× is most consistent with

1. **emulator artifact** — the documented AVD decode fragility (SIGSEGV/wedge family):
   software decode at per-second frame pacing can hitch; **device-first repro is
   mandatory** before any code change (per AGENTS-SESSION lessons: black-frame +
   advancing progress = wedge until proven otherwise), or
2. buffering under-runs mid-video at 0.5× (progressive MP4 from cache — unlikely, but
   a logcat-observable `MediaCodec` starvation would confirm), or
3. a stall on the speed *change* itself (re-seek). ExoPlayer applies speed live; a
   perceptible freeze happens only when the audio sink is rebuilt (Sonic re-init). If
   the report targets the *change*, `media3` ≥1.11 handles that; nothing to fix.

### Proposed approach (minimal — after repro)

- If confirmed on device only: nothing to change in `:core:player` (correct usage);
  document known-good behavior. If a concrete defect presents (e.g. speed resets on
  every `playGif`, or a state-restore path resets to 1× while the slider says 0.5),
  fix that exact point — sparingly.
- Candidate code smell to double-check during repro: adopted inline preview players
  (`FeedScreen`/`PlayerScreen` adopt/park handshake, issue #9) re-assert mute but NOT
  speed; if a user on the swipe player hears/see speed jumping after an adopt path, re-applying speed
  is one line in the adoption block.

### Acceptance conditions

- Repro note recorded first: device (not emulator) build, speed 0.5×–0.75× on
  several gifs, with `screenrecord` size evidence per the session-log wedge rule.
- If implementation change lands: the affected module gates green + the 7 compose
  tests of #50 stage 2 (speed stateDescription) still pass.

### Non-goals

- No custom frame renderer, no frame-dropping pipeline, no replay-buffer tuning.
- No persisting speed across player exit (session-only is normative).
- No change to the hold-to-2× feature or step sizes.
- Preserve the "never spec without a proven need" rule: no fallback plumbing until
  device verification proves a primary-path need.
