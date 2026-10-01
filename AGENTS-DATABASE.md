# AGENTS-DATABASE.md — `:core:database`

Room as **single source of truth** with stale-while-revalidate. DB name: `giffy.db`.
Spec: PLAN.md §5, §6.

## Tables (current spec)

- `gifs` — metadata incl. **tags + username stored locally** (offline filtering depends on it)
- `feed_pages` — key format `"trend:v2:pop:p3"` incl. extended variants; sort/range changes → **distinct keys**
- `search_history` · `tags` · `liked_ids` (tiny mirror of server likes — write-through like/unlike, refreshed from `/v2/likes`; renders liked badges + Liked feed skeleton offline) · `watch_history` (resume positions; write policy: record only after ≥3s playback OR ≥30% played, one row per gif, cap 1000 oldest-evicted) · `hide_counts` (single rolling 7-day counter) · `custom_feeds` (§7, pending)
- `creator_prefs(username PK, state, favorited_at)` · `tag_prefs(tag PK, state, blocked_at, favorited_at)`
- `keyword_blocks(pattern PK, blocked_at)` · `niche_groups(id auto, name, tag_list, state)` (state ∈ BLOCKED | FAVORITED)
- `pinned_ids` (server-pin mirror, `v2/pins` — Phase 7)
- Local `collections` + `collection_items` tables: only if the §2 server-collections verification fails (creator-groupings shape per §6/§7 — decide in Phase 2, not Phase 7)
- `feed_prefs` handled in `:core:datastore`, **not** Room.

## Rules

- Schema change ⇒ explicit `Migration` class. `fallbackToDestructiveMigration` is forbidden.
- UI reads Room; a background refresh hits the API and upserts. Never fetch-to-render first.
- **Favorites are never cached** — network-backed only.
- TTLs: tags 7d · trending/top 10 min · search 1h · gif metadata 24h.
- Group feed keys `group:<id>:p<n>`; revalidate when the group's tag list changes.
- Airplane-mode cold start must work off Room alone — design queries so the first frame needs no network.

## Preferences backup (`ContentPrefsBackup.kt`)

Export/import of content prefs as JSON via SAF document pickers (Settings screen).
- Format: `{"creatorPrefs":[{"username","state","changedAt"}]}` — kotlinx-serialization; the encoder omits default values, so `format`/`version`/`dataSaver:false` may be absent — decoding tolerates that (defaults apply).
- Import is **merge-upsert** (no wipe): rows inserted/updated, unknown JSON fields ignored; returns restored count; throws SerializationException on malformed input without touching the DB.
- Age-gate confirmation deliberately NOT exported (per-device).
- **`:core:database` needs `alias(libs.plugins.kotlin.serialization)`** in its build.gradle.kts — `@Serializable` in this module silently lacks a serializer at runtime otherwise (unit tests caught it).
- Live-verified round-trip (Phone34, 2026-09): block + favorite creators → Export via `CreateDocument` → uninstall → fresh install → age gate → Import via `OpenDocument` → both prefs restored, blocked creator leak-zero in feed.

## Verified pitfalls (live debugging 2026-09)
- **Custom PagingSource gets NO automatic Room invalidation.** Room auto-wires invalidation
  only for its own generated paging sources. Ours must subscribe manually:
  `db.invalidationTracker.addObserver(Observer("feed_pages","gifs"))` → `invalidate()`.
  Without it, a cold start fetches + upserts but the grid stays empty FOREVER (UI never reloads).
  Symptom that misleads: restarting the app "fixes" it (initial load now finds data).
- Room `IN` queries don't preserve ordering — `FeedPagingSource` re-orders to the page's gifIds.
