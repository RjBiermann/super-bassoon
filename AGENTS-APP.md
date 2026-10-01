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
- **Phone + tablet = one adaptive UI** (PLAN §9): same screens, touch-first everywhere;
  width is the only variable. `WindowWidthSizeClass` computed once at the app shell,
  derived layout hint passed down (column counts, margins) — no scattered width checks.
  No separate tablet screens or codepaths.
- Masonry width-derived columns (`Auto`: compact 1 / medium 2 / expanded 3, user-overridable — PLAN §6), Paging 3.
- TikTok-style swipe player.
- Long-press (tile or player) quick sheet: **Like / Unlike** · Block creator · Favorite creator · Block tag `<tag>` · Block all tags on this gif · Block this keyword · Don't block.
- Creator profile: Follow/Unfollow (server-backed, `v1/me/follows`; button states Follow ↔ Following per site); niche cards show Join/Leave state (site wording "Join Niche / Leave Niche", API `v2/niches/{id}/subscription`).
- Tap username → profile-like view (follow/block/manage lists).
- Feeds: Trending / Explore / Top(day…all), group feeds, custom feeds, For You, Search, Favorites, Groups, Settings.
- **UI lingo = site words (verified 2026-10-01; normative table lives in PLAN §9 first bullet):** Explore (not "Discover") · Join/Leave Niche · Following (not "Followed" screen label) · Saved Collections · "Liked GIFs & Images" · Sound On/Off · Related Tags / "you might like" · Hot · Latest · Top / Top This Week · Blocked Tags · App-only surfaces keep app labels (Continue Watching, Surprise me, Groups, Favorite creator, Data saver).
- Surface `RateLimitBus` state as a subtle "cooling down" indicator.

## TV app
- androidx.tv, minSdk 26, full D-pad navigation incl. Groups + settings + PIN pad; 5% overscan margins.
- Kept as a separate module on purpose (PLAN §1): D-pad focus traversal, tv-material
  components and 10-foot contrast are different in kind from touch — a merged module
  would turn every screen into an `isTv` branch. TV shares all :core:* and :feature:*
  code; only the shell is TV-specific.
- Decision (researched 2026-10-01, do not re-litigate): responsive UI handles **width**
  (phone→tablet→foldable→desktop window); TV differs by **input modality** (D-pad/remote,
  10-foot leanback) — Google's own guidance keeps them on separate tracks (adaptive-apps
  hub = phones/tablets/foldables only; `/design/ui/tv/guides/foundations/design-for-tv` =
  10-foot UI, D-pad, communal device; tv-material is a separate component set). Running
  touch UI on TV via sideload compatibility mode is rejected as poor UX. One source of
  truth = shared logic in :core:/:feature:, NOT shared screens.
- `TvLazyRow`s: Trending · Explore · Top This Week · Continue Watching · Favorites · one row per favorited group · custom feeds.
- Focus on username in now-playing → quick actions panel.
- "Why did this get hidden" toast on filtered-item skip.

## Favorite creator (Phase 7 slice 4, verified live on Phone34)
- Mobile: "Favorites" FilterChip in the feed tab row; helpful empty state when nothing
  favorited (no blank screen). Quick sheet: Favorite/Unfavorite follows current state.
- TV: "Favorites" TvLazyRow appears ONLY when favorites exist (`hasFavorites` flow);
  TV favorite/block landed via MENU quick actions on focused cards (2026-09) — row verified live.
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

## UI/UX audit findings (2026-10, research-only — fixes unscheduled)
Static review against mobile-accessibility / M3 / ui-ux-pro-max rules. Not a work order;
a user slice picks from this list. Priority order within each tier.

**Critical**
- Dead control: CollectionsScreen share `IconButton(onClick = {})` — wire it or remove the icon (repo "no stubs" rule).
- Explore error path: `onFailure { loading = false }` with no error UI, and the
  `LaunchedEffect(creators.size)` refetch never re-fires when a failed fetch adds nothing →
  permanently blank screen until recomposition. Same silent-failure pattern in
  NichesScreen.loadMore + TvNichesViewModel.loadMore. Shared `EmptyState` (core:ui) covers
  empty states on FeedScreen only — every other list surface needs an error/empty branch.
- PIN lock (PinLockScreen): `✓` key fixed (submits at <4 digits too); brute-force
  slowdown (3+ wrong → 15s wait) shipped. NO recovery affordance — deliberate
  (user decision 2026-10: forgotten PIN = clear app data, keeps the lock opaque).

**High (a11y / destructive actions)**
- GiffyPillButton rest state: BrandRed #D70003 text on near-black ≈ 3.5:1 < 4.5:1 AA
  (14sp text) — this is the TV age-gate + all header pills. Fix: lighter red for
  text-on-black or primaryContainer fill at rest (border can stay, it's decorative).
- Player bottom text cluster (description/tags/@user) + action rail + Retry/Skip overlay
  sit directly on video with no gradient scrim — unreadable on bright content.
- Player Retry/Skip are bare Text+clickable ≈ 36dp tall < 48dp target (error state =
  imprecise tapping); use TextButton or 48dp min-height.
- Delete confirm inconsistency: Collections delete confirms, Groups delete is instant —
  same destructive class needs the same dialog or undo snackbar.
- TV age gate has no initial-focus FocusRequester (home screen got that fix; the
  first screen a TV user sees didn't).

**Medium**
- AuthScreen coaches the DevTools paste-token flow first; PKCE "Sign in with browser"
  (AGENTS-AUTH primary path) is a secondary button. Invert the hierarchy.
- Settings Switch rows: whole row should be `Modifier.toggleable(role = Role.Switch)`
  (target size + TalkBack state announcement), not Switch-only hit area.
- FeedScreen empty-Favorites hint says "long-press a tile and choose Favorite" — no tiles
  exist on an empty feed, and the sheet item is creator-favorite. Wording needs a fix.
- Double-tap like toggles unlike on an already-liked gif (PLAN §9 semantics: double-tap
  = like); only the rail heart should toggle.
- TvPlayerScreen has no pause/seek/position at all (D-pad walks + back only) — documented
  Phase-6 slice limit, but "can't pause on TV" is a real gap. TvSourceFeedScreen
  `onMenu = {}` is another dead control.
- Tile a11y noise: image contentDescription "Gif by @user" + visible "@user" Text = creator
  announced twice per tile; make the image decorative or mergeDescendants. Long-press
  affordance unannounced (add onLongClickLabel semantics).
- Search suggestions render a bare count ("1234", no unit) — "1,234 gifs".

**Low**
- OfflineNotice duplicates core:ui EmptyState — reuse it.
- QuickBlockSheet speed label `String.format("%.2f")` is locale-dependent (comma decimals)
  and 2 decimals is false precision for 0.25 steps → Locale.US + %.1f.
- PinLockScreen: no haptic on keypress / wrong-PIN shake.
- NichesScreen initial fetch inside `remember { scope.launch {} }` — should be
  LaunchedEffect(Unit) (same length, correct idiom).
- contentDescription capitalization varies ("search" vs "Sound On") — copy consistency.

Skipped by design: live D-pad traversal audit (needs emulator session via debroid/mobile
MCP); contrast math under dynamic-color theme (Material guarantees it).

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

## Orientation filter (built 2026-10, live-verified: pref write + pager restart)

## §8 range chips + shuffle (built 2026-10, live-verified on Trending: chips, shuffle on/off, reshuffle)
Client-side per-feed filters: duration (<10s/10–30s/30–60s/1–5m/>5m) · resolution (HD only) ·
orientation (Global default = the §6 global pref). Persisted per feed in DataStore
(`feedprefs:<baseKey>` JSON via `FeedPrefs`); Filter ▾ + Clear chips above the grid
(mobile; TV deferred — its global orientation filter covers the primary use case).
Read-time AFTER ContentFilter, not in hide counts. Date range + untagged-only chips
stay unbuilt (server param unverified / group-feeds-only).
Global DataStore pref `orientation_filter` (`any` default / `horizontal` / `vertical`) in the
shared SettingsScreen — one toggle covers both apps (TV: horizontal-only use case, mobile:
vertical-only). Per-feed §6 orientation chips, when they land, default to this value.
Applied read-time in `FeedPagingSource` AFTER ContentFilter; `LikedNetworkPagingSource` and
the TV Continue Watching row need the same manual check (they bypass the paging choke point).
NOT counted in hide counts, no "why hidden" toast — a pref, not a block. Shuffle /
"Surprise me" must wire it when implemented (leak-zero). Predicate: `height > width`
(`Gif.width/height` already on the model); 0×0 unknown → pass-through (same fallback as
TV card-width rule). Pager restarts on pref change via `flatMapLatest` in `FeedRepository.paging`.

## Feeds (order in cache keys)
Server-backed sorts → distinct cache keys (`...:sort=top_week:p<n>`). Client-side-only ops
(duration sort, shuffle, creator A→Z on group/custom) share the base key. Shuffle is
per-session Fisher-Yates, stable across recomposition, reshuffle action available.

## Pending-items completion (2026-10-01)
- hasAudio 🔊 badge on grid tiles (know-before-tap); keep-screen-on while
  the swipe player is up (window flag, disposed).
- A11y closed: PIN pad keys carry real contentDescriptions ("Delete last
  digit"/"Submit PIN"), dot row announces "N of 4 digits entered", like
  button is a polite live region announcing the optimistic flip,
  data-saver row is a whole-row toggleable switch.
- Strict tags (§8 untagged-only) on group feeds: read-time filter —
  non-empty tag set AND all tags within the group bundle (vacuous-all
  would pass every untagged gif — live-proven). Mobile + TV share
  FeedFilterDialog (now public, own file); TV source feeds get a Filter
  chip writing the same feedprefs blob (pager restarts via the combined
  orientation+prefs flow).
- Date-range chips: the API ignores createdAfter/created_after/date/
  dateFrom (future bound still returns 2019 gifs) — documented impossible.
- **Paging lesson:** cachedPager had been dropping the orientation+prefs
  params → FeedPagingSource ran on constructor defaults → ALL client-side
  filters (duration/resolution/orientation/shuffle/untagged) were silent
  no-ops while the blobs persisted — the earlier verifications only
  proved storage, not filtering. Fixed by combine(orientationFilter,
  feedPrefs) → flatMapLatest → params handed to the source.
- Filter dead-end (live-proven + fixed): a strict client filter can empty
  EVERY cached page; with 0 visible items nothing scrolls → no APPEND →
  the feed starves on an eternal spinner. FeedPagingSource.load now walks
  consecutive unfetched pages through the empties (bounded ≤8 — rate-limit
  safe) and terminates on a missing/invalid next key.
- Trending server pool is ~100 items: page 3×40 → 20 gifs, page 4 → HTTP
  400 (probe kept in LiveSmokeTest `trending page cap probe`). Cache rows
  from older pool rotations coexist, so deep cached pages are historical.
  Strict filters can therefore legitimately match zero tiles → FeedScreen
  shows "No videos match this filter / Clear or loosen the filter chips"
  instead of a blank grid. "Clear" refills (live-verified both ways).
