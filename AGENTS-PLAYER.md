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
- **Inline feed autoplay** (1-column mobile feed, spec'd 2026-10-02, NOT built): see
  the "Inline feed autoplay" section above — one shared player, muted+looped
  (`REPEAT_MODE_ONE`), no watch_history writes, data-saver forces off.
- **Video fit:** user setting Fit / Crop / Stretch →
  `PlayerView.resizeMode` = `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM` / `RESIZE_MODE_FILL`
  (default Fit). One shared DataStore pref consumed by both `PlayerScreen.kt` (mobile,
  currently sets FIT explicitly) and `TvPlayerScreen.kt` (TV, currently default FIT).
  Crop/Stretch apply before any pinch-zoom (`graphicsLayer` scale multiplies on top).

## Verified end-to-end (emulator, 2026-09)
- Feed tile → PlayerScreen → ExoPlayer playback → 5s position sample → `watch_history` row
  (positionMs 5750 observed). All confirmed via run-as sqlite3.
- Emulator screencap shows a solid green video surface — that's a screencap/codec artifact,
  not a bug; verify playback via position values in `watch_history`, not pixels.
- Data-saver is wired end-to-end: `Gif.streamUrl(dataSaver)` → `GiffyPlayer.playGif`;
  covered by `StreamUrlTest` (:core:model). Toggle UI intentionally deferred to the
  Phase 7 settings screen.
- "Continue Watching" is a TV row (AGENTS.md shell-agnostic rule); mobile has no such row.

## Inline feed autoplay (SPEC'D 2026-10-02 — doc only, not built)
When the feed grid is **1-column** (`LayoutHint.gridColumns == 1`, compact phone),
the tile settled in view plays **inline: muted + looped**, tapping it opens the
existing PlayerScreen. Decision recorded after weighing vs a scroll-gesture
takeover into the swipe player — takeover rejected (hijacks a browsing gesture,
back-stack ambiguity, no mainstream or site precedent).
- **Player:** ONE shared `GiffyPlayer` (existing `GiffyPlayerFactory`/SimpleCache)
  attaches to the first gif ≥50% visible after ~150ms settle (skip hover-bys);
  detaches past 100% out of view. No per-tile instances. Threshold = calibration
  knob, tune once live.
- **Loop:** `REPEAT_MODE_ONE` (simpler than the ended-listener route — inline
  never auto-swipes, so no auto-swipe interaction).
- **Tap → `onOpenPlayer(index)`** as today; inline view is a preview, not a
  feature-complete player (controls/sound/like/speed/all live in PlayerScreen).
- **Watch history: inline plays write NOTHING** — Continue Watching would fill
  with 2-second drive-bys; sampling stays solely in PlayerScreen.
- **Data saver forces inline off** (static poster), same rule the player uses.
- **Rate-limit invariant:** one stream fetch per settled tile, no extra calls;
  `preloadNeighbors` only when scroll stops, never mid-fling.
- **Gating:** 2/3-col grids keep static posters (inline players in masonry =
  scroll-perf + data disaster). New app-only pref "Autoplay in feed"
  (wording per AGENTS-APP.md app-only table), default on in 1-col.
- **Open before build:** live-site check — does the upstream site itself
  autoplay inline in its 1-col feed? (AGENTS.md verify-against-live rule.) If
  yes, mirror its threshold/sound behavior; if no, this stays app-only wording.

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
