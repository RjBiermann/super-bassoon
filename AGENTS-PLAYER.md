# AGENTS-PLAYER.md — `:core:player`

Media3/ExoPlayer + SimpleCache (cache-first rules formerly PLAN.md §5).

## Cache
- **SimpleCache keyed by gif ID, not URL** — URL rotates, ID doesn't.
- One SimpleCache instance per process (Media3 requirement); evict-by-LRU.
- **Fixed constants (fixed constants, not DataStore-configurable): mobile 512MB · TV 1GB.** Both figures shown in Settings (+ TV) with a Clear cache button (confirm-first).
- `CacheDataSource` flags: cache + stream (BLOCK_ON_CACHE? no) — enable caching for the data source
  while playback streams in parallel (standard `CacheDataSink`/`CacheDataSource` non-blocking setup).

## Expired media URLs (failure-driven, never pre-emptive)
- `urls.*` are tokenized and expire well before the 24h metadata TTL — cached rows
  go stale while metadata stays useful. On player 403/fatal-load-error: re-fetch
  `GET /v2/gifs/{id}` (rate-limited), upsert the fresh row, retry playback ONCE;
  still failing → player error state. Never refresh URLs speculatively (rate-limit invariant).
- **HLS→MP4 fallback:** player tries HLS first; on manifest/load failure fall back
  to the direct MP4 rendition (`urls.hd`/`urls.sd`) before surfacing the error —
  `hls: true` is per-gif, not guaranteed on every item.
- Playback speed (0.5×–2×) is **session-only** — resets to 1× on player exit.

## Playback
- ~~Resume positions read/write `watch_history` (Room) — feed "Continue Watching" and "Surprise me" exclusion.~~ REMOVED with the features (2026-10 stream): `watch_history` dropped (`MIGRATION_10_11`, DB v11) — both players now start every gif at 0.
- Data-saver toggle: prefer SD stream when on.
- **Inline feed autoplay** (1-column mobile feed, BUILT 2026-10-02): see
  the "Inline feed autoplay" section below — one shared player, muted+looped
  (`REPEAT_MODE_ONE`), no watch_history writes, data-saver forces off.
- **Video fit (updated 2026-10, issue #21 — Stretch REMOVED):** user setting
  Fit / Crop → `PlayerView.resizeMode` = `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM`
  (default Fit). One shared DataStore pref consumed by both `PlayerScreen.kt`
  (mobile) and `TvPlayerScreen.kt` (TV); a legacy stored `"stretch"` de-maps to
  Fit (no migration — the pref is free-form and FILL is retired as a destructive
  distortion mode). Crop applies before any pinch-zoom (`graphicsLayer` scale
  multiplies on top).
  - **TV note (2026-10-02, user decision):** TV default stays **Fit** — for vertical
  gifs the fullscreen-fill alternative (`RESIZE_MODE_ZOOM`, fill width on a 16:9
  panel) shows more screen but crops ~9:16 content heavily; it works as a user
  choice via this pref, not as a TV-forced default. Keep it available to pick on TV
  (the shared SettingsScreen already covers both apps).
- **Preferred video quality (issue #4, built with the plugin+clamp guard; live manifest probe CLOSED 2026-10):** DataStore `video_quality` = `auto | highest | 720 | 480`
  (value list owned by `core/model VideoQuality` — THE one place for the menu
  list; Settings row sits under the video-fit block on the shared screen).
  Data-saver keeps its first rule (forces SD); Auto/Highest equal today's URL
  pick and leave ABR free; 720/480 ride a `TrackSelectionParameters`
  `.maxVideoHeight` cap applied in `GiffyPlayer.applyVideoQuality` (re-applied
  mid-stream on pref change, no re-prepare) with the no-selected-track clamp
  guard: `onTracksChanged` tracks the smallest known rendition height and lifts
  a cap below every rendition to that height (a single-rendition MP4 is an
  honest no-op). Probe result: `urls.hd`/`urls.sd` are single-rendition faststart
  MP4s (no HLS ladder) — the discrete caps are mostly honest no-ops in practice,
  the plumbing is safe-by-construction.

## Verified end-to-end (emulator, 2026-09; pre-watch_history-removal)
- Feed tile → PlayerScreen → ExoPlayer playback verified via run-as sqlite3
  (position samples into `watch_history` — that table has since been DROPPED,
  `MIGRATION_10_11`; both players now start every gif at 0). Emulator screencap
  shows a solid green video surface — a screencap/codec artifact, not a bug;
  verify playback via logcat/codec state, not pixels.
- Data-saver is wired end-to-end: `Gif.streamUrl(dataSaver)` → `GiffyPlayer.playGif`;
  covered by `StreamUrlTest` (:core:model). Toggle UI intentionally deferred to the
  Phase 7 settings screen.
- ~~"Continue Watching" is a TV row~~ — REMOVED entirely (2026-10 stream, issue #1):
  watch_history dropped, no Continue Watching surface on either shell.

## Inline feed autoplay (BUILT 2026-10-02 — live-parity check passed first)
When the feed grid is **1-column** (`gridColumns == 1`, compact phone),
the tile settled in view plays **inline: muted + looped**, tapping it opens the
existing PlayerScreen. Decision recorded after weighing vs a scroll-gesture
takeover into the swipe player — takeover rejected (hijacks a browsing gesture,
back-stack ambiguity, no mainstream or site precedent).

**Live-site gate (CLOSED 2026-10-02, Playwright @ 390×844):** the upstream site's
own 1-col feed DOES autoplay inline — exactly ONE `<video>` element for the whole
feed (shared player, same shape as our spec), `muted:true`, `loop:true`, playing
the settled tile (time advanced across scrolls while the single element
persisted). Spec mirrored its behavior; no threshold deviation to mirror beyond
that (desktop browser allowed unmuted autoplay; mobile policy = muted-first,
which is what we ship).

Implementation (FeedScreen + GifTile):
- **Player:** ONE shared `GiffyPlayer` (`playerFactory.create`) created only when
  `gridColumns == 1 && feedAutoplay && !dataSaver && playerFactory != null`;
  released via DisposableEffect on toggle-off/player-open. `volume = 0f`,
  `repeatMode = REPEAT_MODE_ONE`.
- **Settle:** `snapshotFlow` over the staggered-grid visible items → first item
  ≥50% main-axis visible (`isSettled`, pure + unit-tested `InlineSettleTest`) →
  150ms settle grace (`INLINE_SETTLE_MS` knob), re-check `isScrollInProgress`,
  then `playGif(gif, dataSaver = false)` — one stream fetch per settled tile,
  never mid-fling (rate-limit invariant). Settled null / scroll → pause.
  **Fixed 2026-10-03 (user report, first video played, scroll stalled on its
  frozen frame):** the flow originally emitted ONCE per distinct settled index
  and skipped play when `isScrollInProgress` — at fling-settle that skip was
  final (no re-emission → never retried). The snapshot is now keyed on
  `(settledIndex, isScrollInProgress)`: the scroll-stop transition itself
  re-emits, play is re-checked after the grace, and a same-gif re-settle
  `play()`s (resume) instead of restarting from 0.
- **Surface:** each tile hosts an AndroidView(PlayerView) (controller off,
  non-clickable so tile tap/long-press keep working) only while
  `inlineIndex == index` — inflated from `res/layout/inline_player_view.xml`,
  whose two XML-only knobs close the black-flash report (2026-10-03):
  `surface_type=texture_view` (a SurfaceView is a separate compositor layer
  that is BLACK until the first frame and punches through the poster;
  TextureView renders in-window, transparent until frames land — poster
  visible through the whole prepare window) and
  `shutter_background_color=transparent` (PlayerView's black shutter covers
  the surface until first frame regardless of surface type). Only
  surface_type has no programmatic setter — hence the 15-line XML, not
  hand-rolled UI. On content switch the collect `stop()`s the player BEFORE
  the surface moves (no stale previous-gif frame on the incoming tile) and
  the surface stays on the outgoing tile (frozen frame) while scrolling —
  no mid-fling surface jumps. (attach-on-first-frame was attempted and
  REVERTED: ExoPlayer without an attached surface doesn't decode, so
  onRenderedFirstFrame never fires — chicken-and-egg deadlock).
  TikTok/Reels precedent (2026-10-03 research): per-tile surface +
  TextureView + settle-driven switch — we mirror the surface rules; their
  2–3-player pool stays unbuilt (rate-limit invariant; fallback per the
  fallback rule if a live verify ever proves the single player too heavy).
- **Tap → `onOpenPlayer(index)`** as today; inline view is a preview, not a
  feature-complete player (controls/sound/like/speed/all live in PlayerScreen).
  **Ownership (issue #9, 2026-10): the shared player is VM-owned now** — the
  ONE player parks in `FeedViewModel` (recall/return/drop, released on
  `onCleared`); tap-to-open ADOPTS it in PlayerScreen (the same
  `currentGifId` → no re-prepare, position continues), exiting the player
  parks it back for the grid. Autoplay-off/2-3-col states release via
  `dropInlinePlayer()` (the old DisposableEffect release discipline — a
  parked player must never become an immortal decoder). Player-muting is
  re-keyed on the player instance so an adopted preview gets the user pref.
- **Watch history: inline plays write NOTHING** — Continue Watching would fill
  with 2-second drive-bys; sampling stays solely in PlayerScreen.
- **Data saver forces inline off** (static poster), same rule the player uses.
- **Gating:** 2/3-col grids keep static posters (inline players in masonry =
  scroll-perf + data disaster). App-only pref **"Autoplay in feed"**
  (`feed_autoplay`, default on; SettingsRepository + SettingsScreen row under
  the grid-columns block, TV hidden with it).
- **Device-verified on Medium_Phone (2026-10-02):** 1-col feed settles →
  h264 decoder live on the tile; scroll settle → decoder reconfig (gif swap);
  `watch_history` count unchanged across 20s+ of inline playback (no writes);
  Settings "Autoplay in feed" OFF → zero codec activity (pref path works).
  Note: a stale `grid_columns=3` override silently kept the feature off until
  the setting was reset to Auto — expected behavior, worth remembering when
  a "autoplay not working" report comes in.

## TV player (Phase 6, verified on TV36 emulator 2026-09)
- D-pad: down/right = next gif, up/left = previous, BACK = exit (BackHandler in Root).
- **SDR-pinned playback (issue #20):** the TV `GiffyPlayerFactory` is built
  with `sdrPlayback = true` — a codec-level filter (`MediaCodecSelector` via
  `DefaultPreloadManager.Builder.setRenderersFactory`, verified API on
  media3 1.11.1 — NO parameters-level HDR knob exists there) drops decoders
  that cannot serve SDR (Dolby Vision path / HDR-only profile sets).
  media3 1.11.1 has no TrackSelectionParameters HDR exclusion (AAR-verified);
  a decoder that claims BOTH SDR and HDR profiles still serves SDR (residual
  HDR-variant window awaits the live probe — whether any gif stream even
  presents HDR variants is unprobed).
- ~~`TvPlayerScreen` mirrors mobile resume + 5s `watch_history` sampling~~ —
  watch_history was DROPPED (`MIGRATION_10_11`); TV playback starts every gif at 0.
- TV image has no `sqlite3` in run-as — pull `giffy.db` + `-wal` + `-shm` and read on host.
- TV home uses androidx.tv `Card` for tiles: plain `Modifier.clickable` does NOT take
  D-pad focus inside tv-foundation rows.

## Auto-advance across a page boundary (2026-10-01, logic-verified + SOAK PASSED)
PlayerScreen `pendingAdvance`: pool end → `retry()` + pendingAdvance; the
LaunchedEffect re-drives on (pendingAdvance, itemCount, append) changes —
next < itemCount → advance; append endOfPaginationReached → loop the last
video; else → retry again. Manual swipe cancels via the currentPage
LaunchedEffect. The boundary soak (2026-10-01) caught a real crash — during a
paging refresh the presenter list emptied while the pager still composed page 0 →
`items[page]` IndexOutOfBounds — fixed with a bounds guard before `items[page]`
(the null check only catches placeholders).

**SOAK PASSED (2026-10-02 batch 18, Medium_Phone):** 21-minute continuous
auto-swipe run at 1× over a fresh profile — 62 DISTINCT gifs advanced, the
page-1→page-2 boundary crossed live, zero FATAL exceptions.

**TV end-of-media loop soak (batch 18, TV36):** the STATE_ENDED →
seekTo(0)+play loop was exercised with the media ACTUALLY ending — six real
cycle-end restarts observed. After ~35 min of continuous decoder activity
playback wedged mid-frame — the DOCUMENTED emulator decode fragility, not app
behavior (the loop had functioned over all cycles before the wedge).

## Auto-swipe advance (2026-10-01)
- **Bare-lambda trap:** a `{ ... }` block placed as a statement inside
  `onPlaybackStateChanged` compiles clean but never runs (it's a discarded
  lambda expression) — an entire feature can be a silent no-op while the
  toggle looks functional. Always a direct block; live-proven.

## TV end-of-media (IMPLEMENTED 2026-10-01, session 3 — mirror of mobile's ended-block)
TvPlayerScreen now has one Player.Listener: STATE_ENDED → auto-swipe on (and not
 data-saver) → advance to the next gif, looping the last; off → loop (seekTo(0)+play).
CENTER/PLAY and media keys route through `playOrRestart()` at the ended frame (a
bare playWhenReady toggle re-ends instantly, staying parked — root cause of
"press play does nothing at end"). LIVE-SOAKED: batch-18 (2026-10-02) TV36
end-of-media loop soak passed — six real end→restart cycles via watch_history
position samples; see that record (the decoder wedge after ~35 min was the
documented emulator fragility, not the loop).

Former deferred notes (all closed by the listener above):
- **Dead toggle:** MENU "Auto-swipe next" pref was write-only on TV — now consumed.
- **No loop / no advance:** both behaviors implemented (mobile PlayerScreen:222 reference).
- **Play-after-end:** routes through `GiffyPlayer.playOrRestart()`.
