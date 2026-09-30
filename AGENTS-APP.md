# AGENTS-APP.md — `:app-mobile` + `:app-tv`

Compose UI, navigation, platform shells. Spec: PLAN.md §3, §7–9.

## Age gate (both platforms — blocking)
- Start destination until `age_confirmed_at` set in DataStore.
- "I am 18 or older — Enter" / "Exit (leaves app)". Cannot be bypassed by back gesture.
- **No content loads before confirmation.**
- Optional lock: PIN + biometric — mobile `BiometricPrompt`, TV D-pad PIN pad. No recovery (clear app data resets).
- Settings option to re-run/reset gate status.

## Mobile app
- minSdk 24, targetSdk 35, edge-to-edge, Material 3 dynamic color, AMOLED true-black option.
- Masonry 2-col portrait / 3-col landscape, Paging 3.
- TikTok-style swipe player.
- Long-press (tile or player) quick sheet: **Like / Unlike** · Block creator · Favorite creator · Block tag `<tag>` · Block all tags on this gif · Block this keyword · Don't block.
- Creator profile: Follow/Unfollow (server-backed, `v1/me/follows`); niche cards show Subscribe state (`v2/niches/{id}/subscription`).
- Tap username → profile-like view (follow/block/manage lists).
- Feeds: Trending / Discover / Top(day…all), group feeds, custom feeds, For You, Search, Favorites, Groups, Settings.
- Surface `RateLimitBus` state as a subtle "cooling down" indicator.

## TV app
- androidx.tv, minSdk 26, full D-pad navigation incl. Groups + settings + PIN pad; 5% overscan margins.
- `TvLazyRow`s: Trending · Discover · Top This Week · Continue Watching · Favorites · one row per favorited group · custom feeds.
- Focus on username in now-playing → quick actions panel.
- "Why did this get hidden" toast on filtered-item skip.

## Favorite creator (Phase 7 slice 4, verified live on Phone34)
- Mobile: "Favorites" FilterChip in the feed tab row; helpful empty state when nothing
  favorited (no blank screen). Quick sheet: Favorite/Unfavorite follows current state.
- TV: "Favorites" TvLazyRow appears ONLY when favorites exist (`hasFavorites` flow);
  TV cannot favorite yet (quick actions panel pending) — row verified hidden.
- TV initial focus fix: no focusable grabs D-pad focus on app open — AccountButton gets
  a FocusRequester + LaunchedEffect request, else header buttons are unreachable.

## Preferences backup (Phase 7, verified live on Phone34)
- Settings screen: "Preferences backup" section with Export/Import buttons (SAF
  `CreateDocument("application/json")` / `OpenDocument`) + snackbar feedback
  ("Preferences exported" / "Imported N entries" / "Import failed — not a Giffy
  preferences file"). Closes PLAN gate 7 (round-trip on fresh install).
- Live round-trip: block @cherrymoon__ + favorite @candyai → Export to
  `Download/giffy-prefs.json` → uninstall → fresh install → age gate → Import →
  both prefs restored, blocked creator leak-zero in feed, snackbar "Imported 2 entries".
- BACK from Settings exits the app (no back stack) — relaunch after navigation tests.
- Touch-coordinate scale: Phone34 thumbnails 540×1170 for a 1080×2400 screen = ×2.051,
  NOT ×2 — SAF dialog buttons at (958, 2215), Import button at (423, 810).

## UX polish (Phase 7 slice 3, verified live on Phone34 + TV36)
- Mobile tiles: avgColor placeholder + reserved aspect ratio (no layout jump / black
  flash), 12dp rounded corners, Coil crossfade, contentDescription "Gif by @user".
- TV cards: same placeholder/crossfade + focus scale 1.0→1.08 via `animateFloatAsState`
  + `Modifier.onFocusChanged` (NOT onFocusStateChanged — doesn't exist in compose-ui 1.10).
- One-time long-press coach mark: SnackbarHost on FeedScreen, DataStore flag
  `block_hint_shown` in SettingsRepository (written only after showSnackbar returns).
- Mobile app bar: Settings (gear) icon opens feature:settings SettingsScreen (blocked
  lists + unblock, reactive via ContentPrefsDao Flows); unblock → invalidation →
  hidden tiles reappear instantly (verified round-trip on emily.reed).

## Per-feed prefs (DataStore, via `:core:datastore`)
Sort · date range · duration min/max · resolution · orientation · shuffle seed — persisted
per feed (`feed_prefs` key), survive restart, applied strictly AFTER ContentFilter.

## Feeds (order in cache keys)
Server-backed sorts → distinct cache keys (`...:sort=top_week:p<n>`). Client-side-only ops
(duration sort, shuffle, creator A→Z on group/custom) share the base key. Shuffle is
per-session Fisher-Yates, stable across recomposition, reshuffle action available.
