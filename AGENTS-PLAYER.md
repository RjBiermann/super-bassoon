# AGENTS-PLAYER.md — `:core:player`

Media3/ExoPlayer + SimpleCache. Spec: PLAN.md §5.

## Cache
- **SimpleCache keyed by gif ID, not URL** — URL rotates, ID doesn't.
- One SimpleCache instance per process (Media3 requirement); evict-by-LRU.
- **Fixed constants (PLAN §5, not DataStore-configurable): mobile 512MB · TV 1GB.** Both figures shown in Settings (+ TV) with a Clear cache button (confirm-first).
- `CacheDataSource` flags: cache + stream (BLOCK_ON_CACHE? no) — enable caching for the data source
  while playback streams in parallel (standard `CacheDataSink`/`CacheDataSource` non-blocking setup).

## Playback
- Resume positions read/write `watch_history` (Room) — feed "Continue Watching" and "Surprise me" exclusion.
- Data-saver toggle: prefer SD stream when on.

## Verified end-to-end (emulator, 2026-09)
- Feed tile → PlayerScreen → ExoPlayer playback → 5s position sample → `watch_history` row
  (positionMs 5750 observed). All confirmed via run-as sqlite3.
- Emulator screencap shows a solid green video surface — that's a screencap/codec artifact,
  not a bug; verify playback via position values in `watch_history`, not pixels.
- Data-saver is wired end-to-end: `Gif.streamUrl(dataSaver)` → `GiffyPlayer.playGif`;
  covered by `StreamUrlTest` (:core:model). Toggle UI intentionally deferred to the
  Phase 7 settings screen.
- "Continue Watching" is a TV row (PLAN §9); mobile has no such row.

## TV player (Phase 6, verified on TV36 emulator 2026-09)
- D-pad: down/right = next gif, up/left = previous, BACK = exit (BackHandler in Root).
- `TvPlayerScreen` mirrors mobile resume + 5s `watch_history` sampling — verified live
  (gif 22.3s played → `watched=1`).
- TV image has no `sqlite3` in run-as — pull `giffy.db` + `-wal` + `-shm` and read on host.
- TV home uses androidx.tv `Card` for tiles: plain `Modifier.clickable` does NOT take
  D-pad focus inside tv-foundation rows.
