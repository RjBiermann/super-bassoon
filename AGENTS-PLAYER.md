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
- Resume positions read/write `watch_history` (Room) — feed "Continue Watching" and "Surprise me" exclusion.
- Data-saver toggle: prefer SD stream when on.
- **Inline feed autoplay** (1-column mobile feed, BUILT 2026-10-02): see
  the "Inline feed autoplay" section below — one shared player, muted+looped
  (`REPEAT_MODE_ONE`), no watch_history writes, data-saver forces off.
- **Video fit:** user setting Fit / Crop / Stretch →
  `PlayerView.resizeMode` = `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM` / `RESIZE_MODE_FILL`
  (default Fit). One shared DataStore pref consumed by both `PlayerScreen.kt` (mobile,
  currently sets FIT explicitly) and `TvPlayerScreen.kt` (TV, currently default FIT).
  Crop/Stretch apply before any pinch-zoom (`graphicsLayer` scale multiplies on top).
  - **TV note (2026-10-02, user decision):** TV default stays **Fit** — for vertical
  gifs the fullscreen-fill alternative (`RESIZE_MODE_ZOOM`, fill width on a 16:9
  panel) shows more screen but crops ~9:16 content heavily; it works as a user
  choice via this pref, not as a TV-forced default. Keep it available to pick on TV
  (the shared SettingsScreen already covers both apps).

## Verified end-to-end (emulator, 2026-09)
- Feed tile → PlayerScreen → ExoPlayer playback → 5s position sample → `watch_history` row
  (positionMs 5750 observed). All confirmed via run-as sqlite3.
- Emulator screencap shows a solid green video surface — that's a screencap/codec artifact,
  not a bug; verify playback via position values in `watch_history`, not pixels.
- Data-saver is wired end-to-end: `Gif.streamUrl(dataSaver)` → `GiffyPlayer.playGif`;
  covered by `StreamUrlTest` (:core:model). Toggle UI intentionally deferred to the
  Phase 7 settings screen.
- "Continue Watching" is a TV row (AGENTS.md shell-agnostic rule); mobile has no such row.

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
  pause, set `inlineIndex`, 150ms settle grace (`INLINE_SETTLE_MS` knob),
  re-check `isScrollInProgress`, then `playGif(gif, dataSaver = false)` —
  one stream fetch per settled tile, never mid-fling (rate-limit invariant).
  Settled null / scroll → pause. Adjacent prefetch NOT run inline (preview only;
  PlayerScreen keeps its own preload).
- **Surface:** each tile hosts an `AndroidView(PlayerView)` (controller off,
  non-clickable so tile tap/long-press keep working) only while
  `inlineIndex == index`; the poster AsyncImage stays underneath.
- **Tap → `onOpenPlayer(index)`** as today; inline view is a preview, not a
  feature-complete player (controls/sound/like/speed/all live in PlayerScreen).
  FeedScreen leaves composition under PlayerScreen (MainActivity else-chain) →
  inline player released.
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
- `TvPlayerScreen` mirrors mobile resume + 5s `watch_history` sampling — verified live
  (gif 22.3s played → `watched=1`).
- TV image has no `sqlite3` in run-as — pull `giffy.db` + `-wal` + `-shm` and read on host.
- TV home uses androidx.tv `Card` for tiles: plain `Modifier.clickable` does NOT take
  D-pad focus inside tv-foundation rows.

## Auto-advance across a page boundary (2026-10-01, logic-verified)
PlayerScreen `pendingAdvance`: pool end → `retry()` + pendingAdvance; the
LaunchedEffect re-drives on (pendingAdvance, itemCount, append) changes —
next < itemCount → advance; append endOfPaginationReached → loop the last
video; else → retry again. Manual swipe cancels via the currentPage
LaunchedEffect. Compile-verified + code-reviewed; live soak through an
actual boundary still pending (needs minutes of playback — cheap unit
coverage isn't possible against Compose pager state).

**Update (2026-10-01, soak):** the boundary soak DID exercise this path and
caught a real crash — during a paging refresh the presenter list emptied
while the pager still composed page 0 → `items[page]` IndexOutOfBounds.
Fixed with a bounds guard before `items[page]` (the null check only catches
placeholders). Full clean pass still pending — the Phone34 emulator dies
every few minutes on this host, so a 10-min soak keeps getting interrupted.

**SOAK PASSED (2026-10-02 batch 18, Medium_Phone):** 21-minute continuous
auto-swipe run at 1× over a fresh profile — 62 DISTINCT gifs advanced
(`COUNT(DISTINCT gifId)` on watch_history as the observable), the
page-1→page-2 boundary crossed live (count jumped 40→42 with
appended-page ids), zero FATAL exceptions. The "keeps getting interrupted"
streak is closed.

**TV end-of-media loop soak (batch 18, TV36):** the STATE_ENDED →
seekTo(0)+play loop was exercised with the media ACTUALLY ending —
watch_history position samples cycled 10396 → 6263 → 16283 → 13155 →
6332 → 16342 across six real cycle-end restarts (19.2s gif, 5s sampler).
After ~35 min of continuous decoder activity playback wedged mid-frame
(position frozen, Codec2 "Invalid WorkBundle" chatter) — the DOCUMENTED
emulator decode fragility, not app behavior (the loop had functioned over
all cycles before the wedge).

## Auto-swipe advance (2026-10-01)
- **Bare-lambda trap:** a `{ ... }` block placed as a statement inside
  `onPlaybackStateChanged` compiles clean but never runs (it's a discarded
  lambda expression) — the whole auto-advance was a silent no-op while the
  toggle looked functional. Symptom: video parked at STATE_ENDED, watch_history
  frozen, no crash. Now a direct block; live-proven (61 advances, boundary
  crossed, 0 fatals).
- Boundary pass recipe: duration chip 10–30s + auto-swipe ON + speed 2× in the
  overflow sheet → distinct `watch_history.gifId` growth is the advance
  observable (grid tile dumps are unreliable this session).

## TV end-of-media (IMPLEMENTED 2026-10-01, session 3 — mirror of mobile's ended-block)
TvPlayerScreen now has one Player.Listener: STATE_ENDED → auto-swipe on (and not
 data-saver) → advance to the next gif, looping the last; off → loop (seekTo(0)+play).
CENTER/PLAY and media keys route through `playOrRestart()` at the ended frame (a
bare playWhenReady toggle re-ends instantly, staying parked — root cause of
"press play does nothing at end"). Not yet live-soaked through end-of-media on
TV36 (needs a video actually ending; emulator session pending).

Former deferred notes (all closed by the listener above):
- **Dead toggle:** MENU "Auto-swipe next" pref was write-only on TV — now consumed.
- **No loop / no advance:** both behaviors implemented (mobile PlayerScreen:222 reference).
- **Play-after-end:** routes through `GiffyPlayer.playOrRestart()`.
