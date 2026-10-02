# AGENTS-APP.md — `:app-mobile` + `:app-tv`

Compose UI, navigation, platform shells. Age gate + navigation + feeds + per-feed
prefs + the normative UI-lingo table below (formerly PLAN.md §3, §7–9).

## Age gate (both platforms — blocking)
- Start destination until `age_confirmed_at` set in DataStore.
- "I am 18 or older — Enter" / "Exit (leaves app)". Cannot be bypassed by back gesture.
- **No content loads before confirmation.**
- Optional lock: PIN + biometric — mobile `BiometricPrompt`, TV D-pad PIN pad. No recovery (clear app data resets).
- Settings option to re-run/reset gate status.

## Mobile app
- minSdk 24, targetSdk 35, edge-to-edge, Material 3 dynamic color, AMOLED true-black option.
- **Phone + tablet = one adaptive UI** (AGENTS.md shell-agnostic rule): same screens, touch-first everywhere;
  width is the only variable. `WindowWidthSizeClass` computed once at the app shell,
  derived layout hint passed down (column counts, margins) — no scattered width checks.
  No separate tablet screens or codepaths.
- Masonry width-derived columns (`Auto`: compact 1 / medium 2 / expanded 3, user-overridable ), Paging 3.
- TikTok-style swipe player.
- Long-press (tile or player) quick sheet: **Like / Unlike** · Block creator · Favorite creator · Block tag `<tag>` · Block all tags on this gif · Block this keyword · Don't block. (Structure moving to sub-panes — spec'd 2026-10-02, not yet built: see **Quick actions — submenu restructure** below.)
- Creator profile: Follow/Unfollow (server-backed, `v1/me/follows`; button states Follow ↔ Following per site); niche cards show Join/Leave state (site wording "Join Niche / Leave Niche", API `v2/niches/{id}/subscription`).
- Tap username → profile-like view (follow/block/manage lists).
- Feeds: Trending / Explore / Top(day…all), group feeds, custom feeds, For You, Search, Favorites, Groups, Settings.
- **UI lingo = site words (verified live via logged-in Playwright sweep, 2026-10-01 — normative for every user-visible label):**
  - For You / Trending (home tabs; For You scope dropdown **Creators · Niches · All**).
  - **Explore** (NOT "Discover" — site nav renders Home · Explore · Niches · Profile); Explore = "Top Creators" surface (`/explore/creators`).
  - Follow states **Follow ↔ Following**; following page heading "Following", per-creator **unfollow button**.
  - Niche pages: tabs **Feed · About**, **Join Niche / Leave Niche**, **"N Members / N Posts"**, Niche Rules, Top Creators, "Niches you might also like"; profile menu item **My Niches** — say "joined niches", not "subscriptions" (API paths keep `/subscription`).
  - Collections screen header **Saved Collections** + **Create New Collection** (server's default collection is named "Likes"); liked page heading **"Liked GIFs & Images"** (its URL says favourites but the page never does).
  - Watch-page sections **Related Tags** (See All), "Suggested Niches"/"Suggested Creators", related-gifs strip **"you might like"**; per-card count text "N views".
  - Player rail **Sound On/Off**, **open fullscreen**, **show more** (⋯) menu = Share · Add to a Niche · Add to a Collection · Report; fullscreen overlay **Close video · Previous video · Next video**.
  - Settings content-prefs heading **"Select Your Content Preferences"** with **"Blocked Tags"** + **"add tag"**; result tabs **GIFs / Creators / Niches** (Images is a site-only upload surface).
  - Sorts (niche/creator/profile): **Hot · Latest · Top**; tag/search pages: **Trending · Top This Week · Top This Month · Latest**.
  - **"Pinned" stays "Pinned"** — the API's own word (`v2/pins`); no site UI surface exists.
  - **App-only surfaces keep app labels** (no site equivalent — do not retrofit site wording): Continue Watching · Surprise me · Groups (niche groups) · Favorite creator · Data saver · autoplay toggle · Grid columns · Preferences backup.
- Surface `RateLimitBus` state as a subtle "cooling down" indicator.

## TV app
- androidx.tv, minSdk 26, full D-pad navigation incl. Groups + settings + PIN pad; 5% overscan margins.
- Kept as a separate module on purpose (deliberate split): D-pad focus traversal, tv-material
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
- "Why did this get hidden" toast on filtered-item skip: **obsoleted 2026-10-01** —
  filtering happens upstream (ContentFilter in the paging sources), so the player
  never encounters a hidden item; surfacing one to explain it would punch through
  the leak-zero choke point. "Hidden this week" count in Settings covers the need.

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
  preferences file"). Closes verification gate 7 (round-trip on fresh install).
- Live round-trip: block @cherrymoon__ + favorite @candyai → Export to
  `Download/giffy-prefs.json` → uninstall → fresh install → age gate → Import →
  both prefs restored, blocked creator leak-zero in feed, snackbar "Imported 2 entries".
- BACK from Settings exits the app (no back stack) — relaunch after navigation tests.
- Touch-coordinate scale: Phone34 thumbnails 540×1170 for a 1080×2400 screen = ×2.051,
  NOT ×2 — SAF dialog buttons at (958, 2215), Import button at (423, 810).

## Verified tick + verified-only filter (built 2026-10, live-verified Phone34 + TV36)
- Every gif payload carries top-level `verified` (creator's badge) — no verified-set
  download needed; live-probed 2026-10 (`v2/gifs/search`, `v2/feeds/trending/popular`,
  `v2/users/{u}/search` all include it; `users` array rows carry it too for following).
- **VerifiedTick (core:ui)** renders next to every user-visible @username: mobile tiles,
  player cluster, creator rows (Explore/Following/NicheAbout), feed creator chips,
  quick sheets; TV cards, creator cards, quick-actions header, player cluster. Tint:
  shared Info cyan on mobile dark, white on video overlays, onSurfaceVariant on TV cards.
- **"Verified creators only"** — one global toggle in Settings (shared mobile/TV screen).
  Read-time pref like orientation: `SettingsRepository.verifiedOnly` → pager restart via
  `FeedRepository.paging` combine → `FeedPagingSource`/`LikedNetworkPagingSource`
  filter + `refreshSurprise` + `ContinueWatchingViewModel`. Room: `gifs.verified`
  (v8, MIGRATION_7_8). MUST stay OUTSIDE the hide-count increment (prefs are not blocks).

## UI/UX audit findings (2026-10, research-only — fixes unscheduled)
Static review against mobile-accessibility / M3 / ui-ux-pro-max rules. Not a work order;
a user slice picks from this list. Priority order within each tier.

**Fixed in the 2026-10-01 session (verified live):** dead duplicate data-saver row
removed (Settings); Collections NameDialog cancel/dismiss no longer submits "";
NichesScreen initial fetch moved to LaunchedEffect; empty-Favorites hint now points at
the player's ⋯ sheet (no tiles exist on an empty feed); TV speed label Locale.US;
TvPlayerScreen progress fraction starts at 0 (stale BISECT probe removed).

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

**High (a11y / destructive actions) — all closed 2026-10**
- Player Retry/Skip are ≥48dp TextButtons (audit H6 closed).
- Player bottom text cluster + action rail sit on a bottom gradient scrim
  (audit H5 closed); GiffyPillButton rest state is container-fill + TextHigh
  (contrast closed).
- Delete confirm inconsistency: closed — Groups delete now confirms first
  (same destructive class as Collections).
- TV age gate initial-focus FocusRequester:
  [FIXED same day: TvMainActivity.AgeGate has `gateFocus`; **2026-10 session also
  wired initial focus on TvNichesScreen (first row) and TvSourceFeedScreen (first
  grid card) — those lists sat unfocused on open: no ring, first CENTER did nothing.**
  Focus-ring unification: M3 Buttons/FilterChips on TV draw nothing on D-pad focus —
  every TV Button/chip takes an explicit `MutableInteractionSource` passed BOTH to the
  component and to `Modifier.giffyFocus(is)`, giving the one BrandRed ring everywhere
  (Niches rows, quick-action dialogs, source-feed Filter chip).]

**Medium**
- AuthScreen hierarchy — closed: paste-token removed (2026-10 user decision);
  PKCE "Sign in with browser" is the only path.
- Settings Switch rows: whole row should be `Modifier.toggleable(role = Role.Switch)`
  (target size + TalkBack state announcement), not Switch-only hit area — data-saver
  row closed; remaining switch rows (verified-only, AMOLED, dynamic color) could
  reuse the same pattern.
- FeedScreen empty-Favorites hint — closed (points at the player's ⋯ sheet now).
- Double-tap like — closed: double-tap = LIKE (never unlike), rail heart toggles.
- TvPlayerScreen pause/seek — closed: horizontal = ±10s seek with flash,
  hold-repeat progressive, CENTER = play/pause; TvSourceFeedScreen onMenu opens
  quick actions.
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

## Empty custom feeds + quick-add (IMPLEMENTED 2026-10-01, session 2)
Quick-add is DONE (mobile: tile long-press → ⋯ → "Add to custom feed…" → picker
dialog; TV parity in TvQuickActions; add is deduped + evicts `custom:<id>` pages).
Empty creation now wired (was half-wired — intent documented, code contradicted):
- `CustomFeedsScreen.kt` `save()` no longer guards `refs.isEmpty()` — a name-only
  definition saves with empty `sourcesJson` (matches `canSave = name.isNotBlank()`).
- FeedScreen `EmptyState` now also fires for `FeedSource.Custom` — an empty custom
  feed shows "This feed is empty / long-press a tile or use ⋯ in the player →
  “Add to custom feed…”" instead of a blank grid.
- Stale `FeedPageFetcher.kt` ponytail comment replaced (empty feeds valid; the
  fetcher returns an empty page safely — mediator test already covered it).
- Test: `GifFeedRefsTest` covers the shared `gifFeedRefs` helper (creator first,
  3-tag cap, niche-ref prepend).
- Groups→custom-feeds merge (AGENTS-CONTENT-FILTER.md) stays parked.

## Ponytail audit — merge/simplify/split candidates (EXECUTED 2026-10-01, session 2)
Findings now applied:
- **deleted:** `core/model/UiState.kt` (zero usages) and the unused
  `typealias GifItem = Gif` (FeedSource.kt).
- **merged:** TvNichesViewModel deleted; shared `NichesViewModel` lives in
  `feature:feed` (paging + category/sort/pin); mobile NichesScreen and TV
  TvNichesScreen both consume it — one paging path instead of two.
- **deduped:** `gifFeedRefs` + `customRefSummary` (with the niche branch) in
  `feature:feed`; mobile AddToCustomFeedDialog and TV TvAddToFeedDialog share them.
- **split:** TvSourceFeedScreen + TvNicheFeedViewModel moved out of TvNiches.kt
  into `TvSourceFeedScreen.kt` (zero logic change).
- Judgment call stands: AppModule/TvAppModule stay device-shaped mirrors.

## UI/UX structure round 2 — merge/split/simplify candidates (2026-10-02, scan-only, docs-only)
Bounded scan of feature:*/app-*/core:ui composables only. Adds to the executed
2026-10-01 ponytail audit; overlaps deliberately avoided. Ranked, smallest first:

1. **SPLIT — QuickBlockSheet + AddToCustomFeedDialog out of FeedScreen.kt.**
   The shared quick sheet (both `PlayerScreen.kt` ⋯-overflow and grid long-press
   call it) plus its picker dialog and `listStyle` helper live inside the grid
   screen's file — shared surface, wrong home. Move to `QuickSheet.kt` in
   feature:feed, zero logic change (same move pattern as the TvSourceFeedScreen
   split). ~200 lines, trivial review.
2. **SMALL MERGE — AgeGate lives twice.** `mobile/MainActivity.kt` and
   `TvMainActivity.kt` each define a private `AgeGate` composable — same copy,
   "I am 18 or older — Enter" / "Exit (leaves app)", same confirm flow; only
   TV's initial-focus `gateFocus` FocusRequester differs (TV audit fix). Lift to
   core:ui with the focus modifier as an optional param; both shells keep their
   own nav/host. Text is normative (age-gate section) so one definition can't
   drift from the other.
3. **PONYSAIL-ONLY — list-screen skeleta converge at the 4th instance.**
   NichesScreen / ExploreScreen / FollowingScreen are three hand-rolled
   "paged rows" skeleta (VM + `remember`-ish fetch state machine + error/empty
   branch). Two audits already caught the cost (NichesScreen
   `remember{}`-fetch bug, each surface's missing error branch per the critical
   audit item). `ponytail:` consolidation threshold n=4: until a fourth list
   screen lands, three flat copies beat one abstraction nobody has tuned.

Scanned-and-rejected (documented so the next audit doesn't re-derive them):
- **FeedScreen.kt 42K / PlayerScreen.kt 44K** — large but cohesive (one surface;
   private `PlayerPage`/`PlayerControls`/`ActionRail` are that player's units).
   Flat-structure rule wins; only candidate here is #1's extraction, not a
   general split.
- **FeedFilterDialog vs Settings content prefs** — different jobs (per-feed
   duration/resolution/shuffle/strict/orientation vs global blocks/likes);
   not a merge.
- **Age-gate copy shared, gate screens not** — TV keeps its own shell per the
   input-modality split decision; #2 shares only the widget, never the nav.
- **TvMainActivity 16K** = activity + nav host + AgeGate + ThemeHost in one
   file, only 4 top-level funs — thin-shell shape; no split without eating the
   file apart for zero reuse.
- **WebViewLoginScreen** — the live PKCE path (intercepts the redirect code,
   verified 2026-09-30); NOT dead after the paste-token removal.
- **SettingsScreen (shared, 22K)** — one screen both platforms consume; that is
   the goal state for Settings, not bloat.

The wait-in-line items from audit round 1 (a11y Low tier, OfflineNotice→EmptyState
reuse, per-surface error branches) stay on top of #1–#3 — pick by user exposure,
not file size.

## Site feature-parity audit (2026-10-02, live-verified — doc-only, no code)
Consumption surfaces are a full mirror: home tabs, Explore, Niches (index + Feed/About + join),
creator feed (tag chips, Follow), watch page, Saved Collections, Following, search
(GIFs/Creators/Niches), settings. Deliberately excluded per §0: upload/creator tools,
Data Dashboard, boost/live-cam/only-fans/ads modules, premium (see AGENTS-NETWORK.md —
galleries verified dead, `v2/feeds/modules` checklist fully covered).

Real gaps (site has them, app doesn't) — **spec'd-not-scheduled, a user slice picks**:
- **Report** — site watch-page ⋯ menu = Share · Add to a Niche · Add to a Collection · Report;
  app's overflow sheet (QuickBlockSheet) has Share but NO Report (radio categories:
  Underaged / Racist / Animals / Rape / Violence / Copyright / I Am In This Content / Other
  → Next → follow-up text). Viewer-protective; needs its endpoint probed (shape unknown —
  the site's write was not captured, do not guess).
- **Add to a Collection** — CollectionsScreen can create/rename/delete but cannot ADD a gif
  to one (site popup: picker + "Create a New Collection"); needs the collection-add write
  probed before wiring (v2/me/collections write path unverified).
- **Add to a Niche** — site popup "Add Content to a Niche" lists joined niches matching the
  gif's tags (pre-checked ones disabled); app has add-to-custom-feed only. Niche-add write
  endpoint unverified.
- **Followers page** — site `/followers` ("Accounts That Follow You", tabs
  Following/Followers); API `GET /v2/me/followers` verified 200. No app UI (mobile or TV).
- **Tags browse tab** — site search has a 4th **Tags** tab sourced from
  `GET /v2/tags/trending` (verified live); app search stops at GIFs/Creators/Niches.
- **Niches suggest** — `GET /v2/niches/suggest` (tag-context niche suggestions, verified)
  un-wired; would power a niche-search tab properly.
- **Server search history** — `GET /v2/search/user-history` verified; site syncs history
  server-side, app keeps local Room history. Existing spec'd-not-scheduled fallback now has
  a verified endpoint to hang on.
- **Creator stats header** — `GET /v1/users/{username}` (verified) gives posts/followers/views
  counts the site profile shows; app creator feeds are tiles-only today.

Parked (not gaps): For You server blend is `v2/feeds/for-you` as-is (70% favorite-creator
weighting was a local-blend idea, superseded by the server feed); search-history sync and
server collections remain spec'd-not-scheduled fallbacks per AGENTS-NETWORK.md.

## Quick actions — submenu restructure (SPEC'D 2026-10-02, docs-only — not yet built)
Quick sheets must stop growing flat rows. Two more sheet items already land this
window (Report, Add to a Collection — see the parity audit below); a flat main
pane would be ~10 rows. Spec: generalize the mechanism QuickBlockSheet already
has (`var view by remember` — "main"/"tags" today) into four panes, identical
shape on mobile + TV. No new component, no navigation — in-place content swap.

**Main pane** (everyday toggles + pane entries only):
- Favorite/Unfavorite @creator — state-aware toggle (both platforms).
- Like/Unlike + Mute — **TV card dialog only** (mobile has these on the rail /
double-tap / mute button; TV player keeps its own cluster).
- "Add to…" ›  · "Tags…" › (only when the gif has tags)  · "Block…" ›
- Close / ‹ Cancel.

**"Add to…" pane:** Add to custom feed… (existing picker stays as the second
level — same depth as today). When the spec'd parity writes land, **Add to a
Niche / Add to a Collection** slot here, NOT as new main rows. "Speed
<n>× — tap to change" stays a main pane row (TV; mobile keeps the slider) —
live tuning, not a destination.

**"Tags…" pane:** unchanged — per tag (top 3): Favorite/Unfavorite tag, Block
tag; ‹ Back.

**"Block…" pane:** every hide action in one place — Block creator · Block
keyword "<first tag>" · per-tag Block tag reusing the existing tag state rows.
Instant as today, no confirm dialog; grouping is separation, not a guard.

Per-surface deltas:
- **Mobile QuickBlockSheet** (both entry points: tile long-press + player ⋯
  overflow): moves `Block creator` + `Block keyword` out of main → Block…;
  `Add to custom feed…` → Add to…; `Tags…` becomes one of three equal panes.
  Speed slider (`showSpeed=true`, player entry) stays on main. Header
  (@user + VerifiedTick + shuffle-seed line) unchanged. Main pane drops
  7 rows → 5 incl. Close.
- **TV TvQuickActions** (MENU on focused card; also player username focus):
  same panes as in-place swap (the AddToFeed swap already proves the pattern);
  no Pin row (no pinned rows on TV). ‹ Back is the last focusable row of each
  pane; MutableInteractionSource + giffyFocus ring pattern applies to any new
  focusable consumers.

Hard caps: pane depth 2 (main → pane → existing picker dialog/screen, same as
today); no labels outside the site-lingo table — pane titles reuse existing
wording ("Add to…", "Tags", "Block…", "Block creator", "Block keyword").

## TV parity gaps vs mobile (audited 2026-10-01, deferred — doc-only)
Code-verified inventory. Ranked, biggest first:

**Missing entirely on TV:**
- **Search** — zero `api.search` calls, no search UI anywhere in app-tv. Mobile has
  full search (tags/creators/suggestions). TV browsing = home rows + Niches +
  pinned/custom feeds only.
- **Groups management** — no GroupsScreen equivalent: create/favorite/BLOCK niche
  groups unreachable on TV. Mobile's blockable group bundles are the macro-filter
  path (leak-zero); TV only has per-item tag/creator quick-action blocks.
- **Custom-feed management** — TV can open existing custom feeds (home ⋯ menu) and
  add to them (quick actions), but create/rename/delete is mobile-only
  (CustomFeedsScreen). Stacks with the empty-creation deferral above.
- **Collections** — mobile-only screen (CollectionsScreen); nothing on TV.
- **§8 shuffle** — no shuffle on TV source feeds (mobile: chips + reshuffle +
  infinite-shuffle player). TV has the Filter chip only.

**Broken on TV (own sections):** auto-swipe dead toggle + end-of-media parking +
play-after-end (AGENTS-PLAYER.md), TvNichesViewModel copy-paste paging +
TvSourceFeedScreen misplaced (ponytail audit below).

**Inverse gap:** [CLOSED 2026-10: SettingsScreen now takes showGridColumns=false
on TV — the mobile-only Grid columns row no longer renders.] Export/Import SAF
picker CANNOT be D-pad-verified on any available TV image — AOSP-TV system
images ship **no DocumentsUI** (`pm list packages` has no com.android.documentsui,
verified on Television_AOSP and Television_1080p, API 36); CREATE_DOCUMENT
launches silently no-op and the app stays alive (no crash, graceful no-op).
Gate 7 stands on mobile (DocumentsUI present). When a picker-equipped TV image
or real leanback device appears, re-run: Settings → Export → drive dialog with
D-pad only.

**Found during the empty-homepage incident (2026-10, doc-only next steps):**
- TV SettingsScreen D-pad traversal is messy: no initial focus on entry, and
  directional searches skip chip rows (Orientation/Video-fit chips) or escape
  to the top-bar back button. Pickup: initial-focus FocusRequester on the
  first row + the established TV chip pattern (explicit MutableInteractionSource
  passed to both the chip and giffyFocus), TV-gated.
- A stale global pref can blank home rows with zero explanation: orientation
  filter "horizontal" + an all-portrait cache hid every Trending/Top-This-Week
  tile behind the reserved empty strip (live-reproduced + fixed by restoring
  Any). Pickup: FeedRow shows a small "No videos match your filters — Settings
  → Orientation" hint when itemCount==0 && refresh NotLoading.

**Minor:** no NicheAbout entry from TvNichesScreen (mobile-only); TV "Following"
row is read-only — unverified whether a follow action exists anywhere on TV.

**Parity OK (verified in code):** age gate, PIN lock, Settings/data saver/video
fit/verified-only, per-feed prefs (same feedprefs blob + shared dialog),
favorites/likes, watch history, quick actions, Surprise me, mute/speed.
