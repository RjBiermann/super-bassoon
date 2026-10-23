# AGENTS-APP.md — `:app-mobile` + `:app-tv`

Compose UI, navigation, platform shells. Age gate + navigation + feeds + per-feed
prefs + the normative UI-lingo table below (formerly PLAN.md §3, §7–9).

## Age gate (both platforms — blocking)
- Start destination until `age_confirmed_at` set in DataStore.
- "I am 18 or older — Enter" / "Exit (leaves app)". Cannot be bypassed by back gesture.
- **No content loads before confirmation.**
- Optional lock: PIN + biometric — mobile `BiometricPrompt`, TV D-pad PIN pad. No recovery (clear app data resets; deliberate user decision — forgotten PIN = clear app data, keeps the lock opaque).
- Settings option to re-run/reset gate status.

## Mobile app
- minSdk 24, targetSdk 35, edge-to-edge, Material 3 with the issue-#7 seed-derived
  dark scheme (dynamic color REMOVED — see "Issue-#7 theme rebrand" below),
  AMOLED true-black option.
- **Phone + tablet = one adaptive UI** (AGENTS.md shell-agnostic rule): same screens, touch-first everywhere;
  width is the only variable. `WindowWidthSizeClass` computed once at the app shell,
  derived layout hint passed down (column counts, margins) — no scattered width checks.
  No separate tablet screens or codepaths.
- Masonry width-derived columns (`Auto`: compact 1 / medium 2 / expanded 3, user-overridable ), Paging 3.
- **Inline feed autoplay (BUILT 2026-10-02, live-parity checked):** in 1-column
  mode the settled tile plays inline, muted + looped; tap opens the swipe
  player as today. Full policy lives in AGENTS-PLAYER.md ("Inline feed
  autoplay") — shared-player, no-history-write, data-saver-off rules are
  normative there (the live-site check passed: the site's own 1-col feed
  runs ONE shared muted+looped `<video>` on the settled tile). Settings
  surface: app-only pref **"Autoplay in feed"** (whole-row switch, under
  the Grid columns block; TV keeps none — the pref only affects the 1-col grid).
- TikTok-style swipe player.
- Long-press (tile or player) quick sheet: four panes per the restructure spec
  (BUILT 2026-10-01, live-verified both apps): main = Favorite · Pin · Add to… ·
  Tags… · Block… · Close; panes are in-place swaps.
- Creator profile: Follow/Unfollow (server-backed, `v1/me/follows`; button states Follow ↔ Following per site); niche cards show Join/Leave state (site wording "Join Niche / Leave Niche", API `v2/niches/{id}/subscription`).
- Tap username → profile-like view (follow/block/manage lists).
- Feeds: Trending / Explore / Top(day…all), group feeds, custom feeds, For You, Search, Favorites, Groups, Settings. (**Continue Watching removed 2026-10** — `watch_history` dropped at DB v11, see AGENTS-DATABASE.md; no partial-watches surface or resume positions exist.)
- **UI lingo = site words (verified live via logged-in sweep, 2026-10-01 — normative for every user-visible label):**
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
  - **App-only surfaces keep app labels** (no site equivalent — do not retrofit site wording): Surprise me · Groups (niche groups) · Favorite creator · Data saver · autoplay toggle · Grid columns · Preferences backup.

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
- `TvLazyRow`s: Trending · Explore · Top This Week · Favorites · one row per favorited group · custom feeds. (Continue Watching row removed 2026-10, DB v11.)
- Focus on username in now-playing → quick actions panel.
- "Why did this get hidden" toast on filtered-item skip: **obsoleted 2026-10-01** —
  filtering happens upstream (ContentFilter in the paging sources), so the player
  never encounters a hidden item; surfacing one to explain it would punch through
  the leak-zero choke point. "Hidden this week" count in Settings covers the need.

## Favorite creator (verified live)
- Mobile: "Favorites" FilterChip in the feed tab row; helpful empty state when nothing
  favorited (no blank screen). Quick sheet: Favorite/Unfavorite follows current state.- TV: "Favorites" TvLazyRow appears ONLY when favorites exist (`hasFavorites` flow);
  favorite/block via MENU quick actions on focused cards — row verified live.
- **TV initial focus rule:** no focusable grabs D-pad focus on app open — the first
  focusable gets a FocusRequester + LaunchedEffect request, else header buttons are
  unreachable (AccountButton; same fix on TvNichesScreen first row + first grid card).

## Preferences backup (verified live on Phone34)
- Settings screen: "Preferences backup" section with Export/Import buttons (SAF
  `CreateDocument("application/json")` / `OpenDocument`) + snackbar feedback
  ("Preferences exported" / "Imported N entries" / "Import failed — not a Giffy
  preferences file"). Closes verification gate 7 (round-trip on fresh install,
  including blocked-creator leak-zero after import). BACK from Settings exits the app
  (no back stack) — relaunch after navigation tests.

## Verified tick + verified-only filter (built 2026-10, live-verified)
- Every gif payload carries top-level `verified` (creator's badge) — no verified-set
  download needed (all feed/search/user endpoints include it; `users` array rows
  carry it too for following).
- **VerifiedTick (core:ui)** renders next to every user-visible @username: mobile tiles,
  player cluster, creator rows (Following/NicheAbout), feed creator chips, quick
  sheets; TV cards, creator cards, quick-actions header, player cluster. Tint: shared
  Info cyan on mobile dark, white on video overlays, onSurfaceVariant on TV cards.- **"Verified creators only"** — one global toggle in Settings (shared mobile/TV screen).
  Read-time pref like orientation: `SettingsRepository.verifiedOnly` → pager restart via
  `FeedRepository.paging` combine → `FeedPagingSource`/`LikedNetworkPagingSource`
  filter + `refreshSurprise` + `ContinueWatchingViewModel`. Room: `gifs.verified`
  (v8, MIGRATION_7_8). MUST stay OUTSIDE the hide-count increment (prefs are not blocks).

## A11y & UX requirements
- Player Retry/Skip are ≥48dp TextButtons; SearchScreen rows ≥48dp. Player bottom text
  cluster + action rail sit on a bottom gradient scrim; pill buttons keep container-fill
  rest state with ≥4.5:1 contrast.
- Settings Switch rows are whole-row `Modifier.toggleable(role = Role.Switch)` —
  target size + TalkBack state announcement (data-saver, verified-only, AMOLED; all done).
- PIN pad keys carry real contentDescriptions ("Delete last digit"/"Submit PIN"), dot
  row announces "N of 4 digits entered", like button is a polite live region announcing
  the optimistic flip; keypress haptics (KeyboardTap per key, LongPress on wrong PIN).
- Brute-force slowdown (3+ wrong → 15s wait) shipped. Double-tap like —
  double-tap = LIKE (never unlike), rail heart toggles.
- TvPlayerScreen pause/seek: horizontal = ±10s seek with flash, hold-repeat progressive
  (seek keys act on KeyDown so OS key-repeat yields progressive steps; KeyUp just
  consumes); item-walk Down/Up stays KeyUp-only on purpose (a held Down would churn
  the decoder across gifs). TvSourceFeedScreen onMenu opens quick actions.
- Tile a11y: GifCard image decorative; the visible "@user" Text announces the creator.
- Search suggestion rows show `"%,d gifs"` (grouped + unit), not a bare count.
- Speed labels `Locale.US` with %.2f (steps are 0.25, so 2 decimals is real precision).
  OfflineNotice deleted; core:ui EmptyState is the only empty surface.
- AuthScreen: paste-token removed (user decision); PKCE "Sign in with browser" is the
  only path. Signed-in state shows "Signed in as @<username>" via
  `TokenStore.usernameFromJwt` (id_token `preferred_username`).
- **Every list surface has a retry/error state** (loadFailed + error EmptyState + Retry;
  section fetches degrade to empty sections) — the remember-vars drift class that
  silently lost error branches is closed by the Hilt-VM convergence (below).
- One-time long-press coach mark: SnackbarHost on FeedScreen, DataStore flag
  `block_hint_shown` (written only after showSnackbar returns).
- RateLimitBus "cooling down" indicator — decided spec'd-not-built (no UI consumer;
  build only when a user-visible failure trace demands it).
- Skipped by design: live D-pad traversal audit cadence (needs an emulator session);
  contrast math under the theme (Material guarantees it).

## In-app links audit + navigation-library decision (2026-10-02)
Every user-visible entity reference is a link. Destination plumbing already exists for ALL
of them — `FeedSource` (Creator/Niche/Search/Custom) + `FeedViewModel.open()` are the
in-place link targets, and every screen that needs a hop takes an
`onOpenCreator`/`onOpenNiche` callback. No new screens required; this is wiring.

**Already linked (working today):**
| Entity | Surface | Destination |
|---|---|---|
| Creator | FollowingScreen rows, search-page creator strip, NicheAbout top-creators | creator feed |
| Niche | FollowingScreen rows, NichesScreen rows, NicheAbout related | niche feed |
| Creator (search results) | FeedScreen creator-chips row (above search results) | creator feed |
| Group / Custom feed / Pinned | chips, More ▾, TV pills + dropdown | their feeds |
| Gif (tiles/cards) | mobile tiles + TV cards | player |

- **@username → creator feed — BUILT (mobile):** CreatorLabel `onClick` wired on the
  tile caption (in-place `viewModel.open`), the player bottom cluster (open + close the
  player — the feed behind has swapped) and the quick-sheet row. TV captions
  deliberately stay non-tappable: a clickable inside a focusable Card steals D-pad
  focus; the TV equivalent is the "Open @user's feed" row in TvQuickActions.
- **Player tag chips → tag feed — BUILT (mobile):** chips call
  `FeedSource.Search(query = tag)` + close the player. TV keeps its quick-actions
  tags pane as the equivalent.
- **Quick sheet "Open @user's feed" row — BUILT:** QuickSheet main pane (after
  Favorite) + TvQuickActions main pane; both `viewModel.open(FeedSource.Creator(...))` + dismiss.
- **Niche About + follow entries — CLOSED:** TvNiches rows carry a side-by-side About
  focusable → shared NicheAboutScreen (batch 12, D-pad-verified); follow/unfollow is
  wired BOTH apps via FeedViewModel (`refreshFollowedCreators` + `toggleFollowCreator`
  over the verified PUT/DELETE v1/me/follows, optimistic flip on success): mobile
  quick-sheet row + TV TvQuickActions row, same main-pane position after "Open @user's
  feed". Anonymous (no token) shows no row on either app.
- **Gif niches → niche feed — BUILT (both apps):** the model keeps the payload's name
  (`Gif.niches: List<NicheRef>`; Room `gifs.nicheNames` map via MIGRATION_8_9, ids
  column untouched, round-trips through cache). The player cluster (mobile + TV)
  renders up to 3 niche pills before the tags (labels = payload name, id fallback for
  pre-v9 cache rows), tap → `FeedSource.Niche(id, name)` + player close; tags are
  plain text (no section heading rendered, so no new wording was invented). Pill row
  only composes when `gif.niches` is non-empty. Device-verified on TV36.
- **Open live-check:** the site watch page's "Suggested Niches / Suggested Creators" +
  related "you might like" strip have no in-app counterpart (the swipe player itself
  serves adjacent discovery; TV player has nothing). Verify against the live watch page
  before spec'ing — add to the parity audit only if the strip is real.

Back-stack note: the old hand-rolled source-history stopgap is SUPERSEDED — the nav
migration owns feed-hop history (below).

## NAVIGATION migration (issue #13) — BUILT 2026-10 nav stream
Both shells run `androidx.navigation:navigation-compose` (catalog pin `navigationCompose =
"2.10.2"`, stable 2.10 line per the design doc's live Maven check; `hiltViewModel()` API
unchanged). Type-safe routes are `@Serializable` classes: app modules gained the
kotlinx-serialization plugin; `:feature:feed`'s `FeedSource` and `:core:model`'s `Gif`/`NicheRef`
are annotated (route/saved-state payloads ONLY — comment on the annotated types says so;
`:core:network` DTOs stay out).

**Mobile routes** (`MainActivity.kt`, package `mobile`): `MainFeed(source: FeedSource? = null)`
= start destination (`null` resolves the shared FeedViewModel's seeded default — For-You/
Trending cold-start rule untouched), `MainPlayer(startIndex)` (feed-scoped player, pages the
SHARED pager), `MainSearch`, `MainNiches`, `MainNicheAbout(id, name)`, `MainFollowing`,
`MainCollections`, `MainCustomFeeds`, `MainSettings`. `MainExplore` from the design doc does
NOT exist — the search stream deleted ExploreScreen pre-migration.

**TV routes** (`TvMainActivity.kt`, package `tv`): `TvHome` (start), `TvFeed(source: FeedSource)`,
`TvPlayer(gifs: List<Gif>, startIndex)` (the loaded window in saved state — Gif is
`@Serializable`; ~tens of records, R3-fallback deliberately NOT built), `TvNiches`,
`TvNicheAbout(about: FeedSource.Niche)`, `TvSearch`, `TvCollections`, `TvCustomFeeds`,
`TvSettings`.

**ViewModel scoping (the §3.3 rule — keep this invariant in future edits):** the SHARED
`FeedViewModel` stays ACTIVITY-scoped — acquired with an explicit `hiltViewModel()` at
NavHost scope BEFORE `NavHost { }` and passed by parameter. Inside destination content,
`hiltViewModel()` means NavBackStackEntry scope: only naturally-per-visit models use it
(Collections/CustomFeeds/Following/Search/Niches/NicheAbout models, TV's
`TvNicheFeedViewModel`, TvHome's home model).

**Every MainFeed/TvFeed entry re-asserts its source on (re)composition**
(`LaunchedEffect(src) { if (vm.source != src) vm.open(src) }`) — covered-entry history works
while the VM stays single-sourced. Feed hop seams: FeedScreen's strip/chips/tile-caption/
changed-sort go through `onFeedNav(source) → navigate(MainFeed(source))` (changed sort = its
own back entry; setSort still persists + one-shot-forces). TV feed swaps (= onSwitchFeed) push;
CHANGED sort replaces the current entry (`popUpTo(entry.id) { inclusive = true }`).

**Player cluster opens are route pushes now:** PlayerScreen gained `onOpenFeed: (FeedSource) ->
Unit` (replaces in-place `viewModel.open` + onBack); the host INSERTS the feed while popping
the player entry — BACK lands the covered feed at its scroll position (pre-nav semantics:
open-then-close). TV keeps "exits the player" the same way. QuickBlockSheet's seam renamed to
`onOpenFeed: (FeedSource) -> Unit` likewise.

**Gone with the migration (bookkeeping superseded):** the 8/9 parallel `rememberSaveable` route
flags + the two when-chain BackHandlers; FeedViewModel's `previousSource`/`canGoBack`/`back()`;
FeedScreen's hop BackHandler; TV's Root hoisted `homeListState`/`feedGridState` (per-entry
saveable states now); TV's non-saveable `openFeed`/`player` Root pairs (process death also
covered now, §8.5). AgeGate/PinLock stay OUTSIDE the NavHost; GiffyScaffold, ContentFilter,
CenterHold/FocusPreview/focus rings, E's in-screen SettingsPage panels are NOT migrated.
Mobile FLAG_KEEP_SCREEN_ON keys on `destination.hasRoute<MainPlayer>()` (a covered player
keeps it — onOpenAccount-over-player is finally reachable, §8.6).

**TV focus restore (the "focus position" ask, §4.3):** scroll restores free via nav per-entry
saveable states; focus is explicit — `focusTargetFor(savedFocusItem, itemCount)` (app-tv,
FocusRestore.kt + unit test) picks the re-arm card; the grid effect re-arms once per content
delivery (boolean key — appends never re-grab); home rows save (rowTitle, itemIndex) pairs in
the TvHome destination and each FeedRow/CreatorRow re-arms its saved card with a non-saveable
per-appear latch; header-pill initial focus runs only while no saved row focus exists
(process death keeps the saved pair — PIN demanded fresh, §8.15 unchanged).

**Search-submit semantics under nav:** stay-on-stack (BACK returns to the draft/history, §8.4);
Niches/Following/About/CustomFeeds opens also stay on the stack (no manual clears).

**Device gates:** the §8 15-step walk CLOSED 2026-10-18 (batch 39) — the original step
list died with PLAN.md, so the walk was reconstructed from the surviving §8.x assertions
above and run on TV: grid walk → card open → BACK restore ×3 (immediate re-open/pops,
same card re-armed, no stranded focus), search stay-on-stack (§8.4: BACK returns to the
draft, not home), top-nav Niches open → BACK → home restore, player OK-reveal overlay,
process death mid-player → relaunch to home (0 FATALs). Mobile side: niche preview tiles
render on the phone (batch-39 screencap). STILL OWED: the R8-build D-pad focus walk;
>1MB-serialized-route risk (R3) stays theoretical; §8.15 fresh-PIN demand unexercised
(no PIN set on the test device).

## Feature audit (2026-10-02) — parity writes
GifsApi has `POST v2/me/collections` (create) + `DELETE v2/me/collections/{id}` but NO
add-content write; no report endpoint; no `GET /v2/me/followers`; no tags-trending search
tab; no niches/suggest. Remaining round-1 parity gaps (Report · Add to a Collection ·
Add to a Niche · Followers page · Tags browse tab · Niches suggest · server search history ·
creator stats header) — per-item status in the parity-audit section. Collections
empty-state copy points at the real path ("Create New Collection, then long-press a
tile → ⋯ → Add to a Collection"). Remaining TV parity gap: the Followers page only
(Search/Groups mgmt/custom-feed mgmt/Collections are built — see "TV parity gaps").

## UX polish (verified live)
- Mobile tiles: avgColor placeholder + reserved aspect ratio (no layout jump / black
  flash), 12dp rounded corners, Coil crossfade, contentDescription "Gif by @user".
- TV cards: same placeholder/crossfade + focus scale 1.0→1.08 via `animateFloatAsState`
  + `Modifier.onFocusChanged` (NOT onFocusStateChanged — doesn't exist in compose-ui 1.10).
- Mobile app bar: Settings (gear) icon opens feature:settings SettingsScreen (blocked
  lists + unblock, reactive via ContentPrefsDao Flows); unblock → invalidation →
  hidden tiles reappear instantly (verified round-trip).

## Per-feed prefs (DataStore, via `:core:datastore`)
Sort · date range · duration min/max · resolution · orientation · shuffle seed — persisted
per feed (`feed_prefs` key), survive restart, applied strictly AFTER ContentFilter.

## Orientation filter (built 2026-10, live-verified: pref write + pager restart)
- **TV intent (2026-10-02, user decision): the horizontal filter is a must-work-well
  use case on TV** — the TV grid is a shared 10-foot screen, vertical/portrait tiles
  waste it, so filtering to horizontal is the primary TV orientation behavior and any
  future filter-path change must not regress TV (TV bypass notes below:
  `LikedNetworkPagingSource`; the TV Continue-Watching row is gone with DB v11). Vertical-only on TV is
  accepted as a dead end, not a target: portrait tiles letterbox against the row
  height and waste the screen.
- **Vertical video on TV: BUILT** — TvPlayerScreen maps the shared video-fit pref
  (Crop → `RESIZE_MODE_ZOOM`), so the image can span the 16:9 panel; ~9:16 content
  crops heavily top/bottom, so TV default stays FIT and Fill/Crop remain shared-plug
  user choices, not a TV-forced default.
- The orientation filter is **data-bound, not fetch-bound (probed live)**: the API has no
  orientation parameter, so the filter is client-side over whatever pool the feed
  returns. Live trending pool = 99/99 portrait (0 landscape); with the global filter
  "horizontal" the home rows legitimately show nothing (the empty-homepage incident: a
  stale "horizontal" pref over an all-portrait cache hid every tile — live-reproduced,
  fixed by restoring Any). User decision (2026-10-02): this is accepted as the pool's
  shape, not a defect to pave over — on TV horizontal is the wanted filter, and when a
  pool is all-portrait the empty-state hint is the honest answer, not a silent
  Any-fallback.

## §8 range chips + shuffle (built 2026-10, live-verified on Trending)
Client-side per-feed filters: duration (<10s/10–30s/30–60s/1–5m/>5m) · resolution (HD only) ·
orientation (Global default = the §6 global pref). Persisted per feed in DataStore
(`feedprefs:<baseKey>` JSON via `FeedPrefs`); Filter ▾ + Clear chips above the grid
(mobile; TV renders the shared FeedFilterDialog over the same blob — §8 shuffle on TV
is CLOSED). Read-time AFTER ContentFilter, not in hide counts. Date range + untagged-only
chips: date range is documented impossible (the API ignores createdAfter/created_after/
date/dateFrom — a future bound still returns 2019 gifs); untagged-only exists on group feeds
(strict tags, below).
Global DataStore pref `orientation_filter` (`any` default / `horizontal` / `vertical`) in the
shared SettingsScreen — one toggle covers both apps (TV: horizontal-only use case, mobile:
vertical-only). Per-feed §6 orientation chips, when they land, default to this value.
Applied read-time in `FeedPagingSource` AFTER ContentFilter; `LikedNetworkPagingSource` needs
the same manual check (it bypasses the paging choke point; the TV Continue-Watching
row is gone with DB v11).
NOT counted in hide counts, no "why hidden" toast — a pref, not a block. Shuffle /
"Surprise me" must wire it when implemented (leak-zero). Predicate: `height > width`
(`Gif.width/height` already on the model); 0×0 unknown → pass-through (same fallback as
TV card-width rule). Pager restarts on pref change via `flatMapLatest` in `FeedRepository.paging`.

## Filtering behavior (durable rules)
- hasAudio 🔊 badge on grid tiles (know-before-tap).
- **Strict tags (untagged-only) on group feeds:** read-time filter — non-empty tag set
  AND all tags within the group bundle (vacuous-all would pass every untagged gif —
  live-proven). Mobile + TV share FeedFilterDialog.
- **Paging invariant:** all client-side filters ride
  `combine(orientationFilter, feedPrefs) → flatMapLatest` into the paging source params.
  If a param stops being handed through, the filter becomes a silent no-op while the
  blob persists — never add a filter that bypasses this combine.
- **Filter dead-end:** a strict client filter can empty EVERY cached page; with 0 visible
  items nothing scrolls → no APPEND → eternal spinner. `FeedPagingSource.load` walks
  consecutive unfetched pages through the empties (bounded ≤8 — rate-limit safe) and
  terminates on a missing/invalid next key.
- **Trending server pool cap:** ~100 items — page 3×40 → 20 gifs, page 4 → HTTP 400
  (probe kept in LiveSmokeTest). Cache rows from older pool rotations coexist, so deep
  cached pages are historical; the mediator treats the page>1 pool-cap 400 as a
  Success(end), not an error.
- **Filtered-empty hint:** strict filters can legitimately match zero tiles → FeedScreen /
  FeedRow show "No videos match this filter / Clear or loosen the filter chips" (plus the
  orientation hint: "Settings → Orientation") instead of a blank grid. "Clear" refills.
  Device-verified both ways (filtered-empty and refill).

## Empty custom feeds + quick-add (IMPLEMENTED 2026-10-01)
Quick-add is DONE (mobile: tile long-press → ⋯ → "Add to custom feed…" → picker
dialog; TV parity in TvQuickActions; add is deduped + evicts `custom:<id>` pages).
- `CustomFeedsScreen.kt` `save()` does not guard `refs.isEmpty()` — a name-only
  definition saves with empty `sourcesJson` (matches `canSave = name.isNotBlank()`).
- FeedScreen `EmptyState` also fires for `FeedSource.Custom` — an empty custom
  feed shows "This feed is empty / long-press a tile or use ⋯ in the player →
  "Add to custom feed…"" instead of a blank grid.
- Test: `GifFeedRefsTest` covers the shared `gifFeedRefs` helper (creator first,
  3-tag cap, niche-ref prepend).
- Groups→custom-feeds merge SHIPPED (DB v10): the merged niche-group management
  lives in the shared CustomFeedsScreen (state cycle + TV focus-ring treatment on the
  row buttons) — full record in AGENTS-CONTENT-FILTER.md.

## Structure decisions (settled 2026-10-01/02 — do not re-derive)
Applied consolidations: deleted `core/model/UiState.kt` + `typealias GifItem = Gif`;
shared `NichesViewModel` in feature:feed consumed by both Niches screens;
`QuickSheet.kt` extracted (mobile AddToCustomFeedDialog + TV TvAddToFeedDialog share
`gifFeedRefs`/`customRefSummary`; `packNicheRef`/`parseNicheRef` in CustomFeedsScreen.kt,
`NicheRefTest` covers round-trip); shared `AgeGate` in core:ui (both shells, centered TV
layout); `CreatorLabel(username, verified, tint, style, tickTint, tickSize, onClick)`
replaced all 10 hand-rolled copies (tick size follows the label); `GiffyScaffold(title,
onBack, …)` replaced boilerplate in 9 screens (PlayerScreen keeps a bare TopAppBar in
AnimatedVisibility — not a Scaffold site); list-fetch state machine converged — all four
list screens speak one Hilt-VM dialect (loadFailed/Retry shape; a further shared skeleton
extraction stays parked); `avgColorOr` + `AudioBadge` in core:ui; FeedScreen chip row:
sort chips + Filter ▾ + Clear merged into ONE row (sorts scroll left under a fixed
right-aligned Filter/Clear pair; both stay per-feed controls); AppModule/TvAppModule stay
device-shaped mirrors (judgment call).

**n=4 consolidation threshold:** flat copies beat one abstraction nobody has tuned — converge at the 4th instance.

**D-pad ring-test rule:** M3 Buttons/FilterChips/dialog rows on TV draw NOTHING visible
on D-pad focus by default — every TV focusable carries an explicit
`MutableInteractionSource` wired to its focus treatment (tv-material components carry
their own focused states; in shared M3 dialogs use `giffyFocus`). Ring-test any shared
dialog on device BEFORE merging it: the picker-dialog family merge (Add-to-Custom-Feed /
Add-to-a-Collection / Add-to-a-Niche × mobile/TV) STOPPED per its own stop rule when the
focused "HD only" chip rendered with no visible focus treatment — only the shared
`orderNichesByTagMatch` ordering rule landed. TV TvAddToFeedDialog has an unconditional
"Add to…" row + Collection-style empty state.

Scanned-and-rejected (documented so the next audit doesn't re-derive them):
- **QuickSheet vs TvQuickActions row idioms** — the touch/D-pad split is the
  module-map's stated reason to exist; not a merge.
- **FeedScreen / PlayerScreen file size** — re-affirmed cohesive; private
  PlayerPage/PlayerControls/ActionRail remain that player's own units.
- **PlayerControls vs TvPlayerScreen progress line** — different behavior contracts;
  the TEXT layer follows the mobile idle-hide rule (player text auto-hide, below).
- **FeedFilterDialog vs Settings content prefs** — different jobs; not a merge.
- **Age-gate copy shared, gate screens not** — only the widget is shared, never the nav.
- **TvMainActivity shape** — thin shell, one file; no split for zero reuse.
- **WebViewLoginScreen** — the live PKCE path; NOT dead after the paste-token removal.
- **SettingsScreen (shared)** — one screen both platforms consume; goal state, not bloat.
- **Two time formatters** — different shapes; sharing saves nothing real.

## Site feature-parity audit (2026-10-02, live-verified)
Consumption surfaces are a full mirror: home tabs, Niches (index + Feed/About + join),
creator feed (tag chips, Follow), watch page, Saved Collections, Following, search
(GIFs/Images/Creators/Niches tabs), settings. Deliberately excluded per §0: upload/creator
tools, Data Dashboard, boost/live-cam/only-fans/ads modules, premium (see AGENTS-NETWORK.md —
galleries verified dead, `v2/feeds/modules` checklist fully covered).

Real gaps (site has them, app doesn't) — **spec'd-not-scheduled, a user slice picks**:
- **Report** — UNPROBEABLE: every plausible endpoint 404'd with the test token
  (see AGENTS-NETWORK.md). Do not guess.
- **Add to a Collection / Add to a Niche — BUILT 2026-10-01:** writes probed live
  (POST v2/me/collections/{id}/gifs → 204; PUT v2/gifs/{gifId}/niches → 202, both
  reverted); picker dialogs in the Add-to… pane on both apps (joined niches,
  tag-matching first, 8-row cap, empty hints).
- **Followers page** — BLOCKED on data shape: both follower endpoints return 200 but the
  test account has zero followers — the row DTO is unverifiable, and building against a
  guessed shape risks silent decode breakage (repo no-guess rule). Re-probe with an
  account that has followers before wiring.
- **Tags trending** — BUILT 2026-10-02: `GET /v2/tags/trending` wired as a "Trending
  tags" section in SearchScreen's empty-query state (fails silently to an absent
  section). The site's search results-page tabs are GIFs/Images/Creators/Niches — there
  is no Tags results tab (`/search/tags` 404s home); trending tags live in the header
  search dropdown; the app's empty-state section is the equivalent surface.
- **Niches suggest** — `GET /v2/niches/suggest` (tag-context niche suggestions, verified)
  un-wired; independent of the full-scope search (the Niches search tab uses
  `niches/search/previews`) — stays a fallback.
- **Server search history** — `GET /v2/search/user-history` verified; site syncs history
  server-side, app keeps local Room history. Existing spec'd-not-scheduled fallback now
  has a verified endpoint to hang on.
- ~~Creator stats header~~ — BUILT: FeedViewModel.refreshCreatorStats + creatorStats;
  counts row on Creator sources on both shells.
- Parked (not gaps): For You server blend is `v2/feeds/for-you` as-is (70%
  favorite-creator weighting was a local-blend idea, superseded by the server feed);
  server collections are CLOSED as ALREADY BUILT (CollectionsViewModel is the
  server-backed source of truth; only collection-content BROWSING lacks a verified
  endpoint).

## Full-scope search (BUILT 2026-10-02, mobile; device-verified batch 14)
The site's search results page (live-verified): `/search/<scope>?query=&order=score` with
result tabs **GIFs · Images · Creators · Niches**. `/search/tags` 404s home — tags have no
results page; the header search box's Tags scope routes tag matches to the tag feed
(`/gifs/<slug>`). App: SearchScreen shows the same 4-tab TabRow whenever a query is typed;
the GIFs tab keeps the existing `FeedSource.Search` feed path unchanged.

Scope → endpoint (all 200-verified 2026-10-02; row shapes in AGENTS-NETWORK.md):
- **GIFs** — existing `GET /v2/gifs/search`; the site sends `type=g` explicitly (default `g`).
- **Images** — same endpoint, `type=i`. Same envelope + GifDto rows, but rows are
  stills (`type=2`, `duration=null`, `urls.sd/hd` are jpgs). Rendered as a 3-up
  static-poster strip — **no player path** (nothing to play).
- **Creators** — `GET /v2/creators/search/previews?order=best_match&page=1&count=30&query=`
  → `{gifs:[…]}`, one preview gif per matched creator (dedupe by `gif.userName`); tap →
  `FeedSource.Creator`.
- **Niches** — `GET /v2/niches/search/previews?…&query=` → `{previews:[{niche, gif,
  user}]}`; tap → `FeedSource.Niche`.
- **Tags** — `GET /v1/tags/match?query=` → tag-name array; the existing Trending-tags
  section covers the suggestion state (a typed-query tags row was NOT built — the
  empty/suggestion state already covers it).

SearchViewModel injects ContentFilter and caches per-scope results keyed by trimmed query
(maps capped at 12; `cappedInsert`/`nicheRows` unit-tested in `SearchScopeResultsTest` —
cap eviction, blank-id drop, blocked-preview-gif row drop). Creators/Niches rows →
`onOpenCreator`/`onOpenNiche` (search closes, feed opens — same swap as NichesScreen).

Rules:
- Tabs lazy-load (1 request on first open, cached in the VM, never refetched per
  recomposition) — the rate-limit invariant stays intact.
- ContentFilter: every gif-backed row (GIFs/Images tiles, creator previews, niche
  previews) flows the single ContentFilter — a row whose preview gif is blocked drops
  the whole row (leak-zero at the cheapest check point, no second filter surface).
- History: app keeps local Room history only; the site's `POST /v2/search/user-history`
  (202) stays a spec'd-not-scheduled fallback.
- TV: BUILT batch 12 — the shared SearchScreen is hosted on TV (home "Search" pill;
  submit opens `FeedSource.Search` via TvSourceFeedScreen), D-pad-verified.
- Skipped: no Collections search scope, no tags results page, no search-scope selector
  in the app header (the tab row on the search surface covers it — the site has none of
  these either).

## Hold-to-2× on the swipe player (BUILT 2026-10-02 batch 14)
Inside the swipe player only (never on tiles): a long-press on the player body plays at
**2× while held** and restores the session speed on release — a "2× speed" chip shows
while engaged; a swipe (pager consumption) or movement ≥ touch-slop cancels, so the
VerticalPager drag and the pinch zoom are untouched; tap + double-tap handling is
unchanged (the race runs up / cancel / movement against the platform long-press timeout
before the tap path). Speed state gains `holdSpeed`; ONE `LaunchedEffect(speed,
holdSpeed)` is the only `setPlaybackSpeed` application point (the quick-sheet slider
and the hold both write state, the effect applies — no dual writers).

## Quick actions — four-pane restructure (BUILT 2026-10-01)
Quick sheets must stop growing flat rows. Generalized mechanism (`var view by remember`
panes): main / addto / tags / block panes, identical shape on mobile + TV; no new
component, no navigation — in-place content swap. BUILT: QuickSheet.kt + TvQuickActions;
TV re-requests pane-first-row focus on swap (`LaunchedEffect(view)`). DEVIATION
(deliberate): the original spec omitted the Pin row — Pin/Unpin stays on main on both
(pinned creators feed the home top-row pills on BOTH apps).

**Main pane** (everyday toggles + pane entries only):
- Favorite/Unfavorite @creator — state-aware toggle (both platforms).
- Like/Unlike + Mute — **TV card dialog only** (mobile has these on the rail /
  double-tap / mute button; TV player keeps its own cluster).
- "Add to…" ›  · "Tags…" › (only when the gif has tags)  · "Block…" ›
- Close / ‹ Cancel.

**"Add to…" pane:** Add to custom feed… (existing picker stays as the second level).
Add to a Niche / Add to a Collection slot here, NOT as new main rows. "Speed <n>× — tap
to change" stays a main pane row (TV; mobile keeps the slider) — live tuning, not a
destination.

**"Tags…" pane:** per tag (top 3): Favorite/Unfavorite tag, Block tag; ‹ Back.

**"Block…" pane:** every hide action in one place — Block creator · Block keyword
"<first tag>" · per-tag Block tag reusing the existing tag state rows. Instant, no
confirm dialog; grouping is separation, not a guard.

Per-surface deltas:
- **Mobile QuickBlockSheet** (tile long-press + player ⋯ overflow): Block creator +
  Block keyword live in Block…; `Add to custom feed…` → Add to…; `Tags…` is one of three
  equal panes. Speed slider (`showSpeed=true`, player entry) stays on main. Header
  (@user + VerifiedTick + shuffle-seed line) unchanged.
- **TV TvQuickActions** (hold-Center/MENU on focused card; player username focus): same
  panes as in-place swap; ‹ Back is the last focusable row of each pane; rows carry an
  explicit interactionSource + focus treatment (ring-test rule above).

Hard caps: pane depth 2 (main → pane → existing picker dialog/screen); no labels outside
the site-lingo table — pane titles reuse existing wording ("Add to…", "Tags", "Block…",
"Block creator", "Block keyword").

## TV menu-less-remote keymap (BUILT + D-pad-verified 2026-10-02; reworked 2026-10-04)
No-button-limitation rule: **every feature reachable with only D-pad + Back + Center**;
MENU and media keys are bonus parity, never the only path. Full rationale +
anti-confusion rule amendments in AGENTS-UX-PATTERNS.md "No-button-limitation rule".

- **Long-press Center = quick-actions panel** (the same TvQuickActionsDialog MENU opens)
  on every Center-meaningful surface — TvPlayerScreen, GifCard (home rows AND
  source-feed grids), TvNiches row. **Fire-while-held contract:** the initial
  (non-repeat) Center KeyDown schedules a ~500ms hold timer (`CenterHold.down()` via an
  injectable `HoldScheduler`; production = android.os.Handler main looper); the timer
  FIRES `onHold` mid-press at 500ms — the panel opens while the key is still held, not
  on KeyUp.
- **KeyUp suppression:** `CenterHold.up()` cancels an unfired timer and returns true ONLY
  when a hold already fired — the caller then suppresses the surface's short-tap action
  (play/pause toggle, card open) with no re-fire; state resets per press so a swallowed
  KeyUp cannot leak into the next press. No coroutine/scope needed from callers
  (`CenterHoldTest`: fires-while-held, suppression without re-fire, short tap cancels,
  swallowed-KeyUp reset).
  - **Panel KeyUp swallow:** the hold-open's own release KeyUp is routed into the NEW
  TvQuickActionsDialog window — a focused row would activate on Center KeyUp. The dialog
  swallows exactly ONE Center KeyUp when opened via a hold (`openViaHold` flag threaded
  from every open surface; MENU-opened panels keep normal behavior).
- **TvNiches row:** hold-Center = `viewModel.togglePin(niche)` (the row's MENU action)
  in place, focus never leaves the row; short tap = open the niche feed.
- **Tap semantics unchanged:** short-tap Center = play/pause (+ restart-at-end) in the
  player, card open on GifCard. BACK closes the panel before navigation; MENU still
  opens the same panel. Unchanged/parked: number keys, double-click, hold-for-2×
  (player-only, see Hold-to-2×), media keys stay player-internal parity only.
- **TV player error overlay is D-pad-operable:** when the overlay appears the Retry
  button takes focus (`LaunchedEffect(playError)`), EVERY key falls through to the
  overlay while it is up (`if (playError) return false` at the top of the root key
  handler — Up/Down walk Retry↔Skip, CENTER/ENTER activates, BACK = exit player), and
  overlay close restores root focus. While the overlay is up the gif does NOT advance
  with Up/Down — the honest behavior for a failing video.
- Device-verified on TV36 (hold opens the panel on player/card/niche row, KeyUp
  suppression proven by the in-place niche pin, short-tap actions intact).

## TV preview-on-focus + show-more panel (BUILT 2026-10-02 batch 18)
**Preview-on-focus (`FocusPreview`, app-tv, shared by home rows AND source-feed grids):**
the SETTLED focus (card holds D-pad focus ≥600ms — fast walking never decodes) starts a
muted+looped preview on that card; focus leaving clears it. ONE player instance per
screen moves gif-to-gif (no decoder churn) — `GiffyPlayerFactory` threaded into
TvHomeScreen/TvSourceFeedScreen; card poster swaps to a `PlayerView` while
`settledId == gif.id`; released when the screen leaves composition (player open =
release). Always SD rendition (`playGif(gif, dataSaver = true)`) — a preview must not
burn HD data (gif-id cache key means the real player later replays from cache).
Muted+REPEAT_ONE set at creation; no watch-history writes (none exist — watch_history dropped DB v11).
Data-saver ON = posters stay static (same rule as the mobile inline autoplay).
Device-verified: dwell → preview, follows focus, absent with data-saver ON.

**Show-more panel:** a "Show more" main-pane row in TvQuickActionsDialog (when the gif
has a description or niches) opens a pane with the FULL description (the player cluster
truncates to 2 lines) + one row per gif niche (Open niche: …, no ≤3 cap) — DOWN-routing
was NOT taken (DOWN is the item-walk key per the keymap's spatially-honest rule; this
panel is the sanctioned path). Niche rows open the feed via `onOpenNiche` wired on all
three panel call sites (player: exits first; home/source feed: in-place swap).
Device-verified.

Batch-18 note on this file's topics: the merged niche-group management moved into the
shared CustomFeedsScreen (AGENTS-CONTENT-FILTER.md DONE section).

## TV parity gaps vs mobile (audited 2026-10-01 — CLOSED 2026-10; all items built or resolved)
- **Search / Groups management / Custom-feed management / Collections — ALL BUILT
  2026-10-02 batch 12:** shared :feature:* screens hosted from TvMainActivity (home
  pill + More ▾ menu), D-pad-verified on TV36.
- **Player text auto-hide — BUILT (TV parity with mobile):** playing && not seeking →
  after 3s fade the whole text cluster (creator/description/tags); paused or mid-seek →
  visible. Progress line + seekFlash stay always-on. Any D-pad key press resets the
  timer and re-reveals — the 10-foot standard is "text hides while watching", not "text
  is unreachable". When hidden the cluster (incl. niche pills) leaves composition so
  focus traversal stays clean — invisible focus targets are a D-pad trap. MENU quick
  actions keep working regardless of visibility state. Device-verified both directions.
- **For You row — BUILT 2026-10-03 (mobile↔TV parity):** TV home gains a logged-in
  "For You" FIRST row (site home order, verified sweep — Trending ↔ For You swap fixed;
  site order now exact). FeedRepository serves `FeedSource.ForYou` NETWORK-LIVE
  (`ForYouNetworkPagingSource`, same no-cache rule as Liked — per-user server
  personalization must not ride the shared Room feed cache; intended side-effect: For
  You does not render from cache in airplane mode). Scope (Creators·Niches·All) is a
  read-time filter over the server pages via the repo's 5-min ForYouContext (the
  endpoint has no scope param). Signed-out: row absent, ZERO for-you network activity.
  Device-verified signed-out and signed-in (real in-app login).
- **§8 shuffle on TV — CLOSED:** TvSourceFeedScreen renders the shared FeedFilterDialog
  (Shuffle/Off/Reshuffle) over the same feedprefs blob; shuffle applies via the shared
  FeedRepository path. Mobile parity holds.
- **Inverse gap — Export/Import SAF picker on TV:** the picker CANNOT be D-pad-verified
  on any available TV image — AOSP-TV system images ship **no DocumentsUI**
  (`pm list packages` has no com.android.documentsui); CREATE_DOCUMENT launches silently
  no-op and the app stays alive (graceful no-op). Gate 7 stands on mobile (DocumentsUI
  present). When a picker-equipped TV image or real leanback device appears, re-run:
  Settings → Export → drive dialog with D-pad only. SettingsScreen takes showGridColumns=false
  on TV (mobile-only Grid columns row doesn't render).
- **TV Settings D-pad traversal — FIXED:** initial focus on entry (requestInitialFocus —
  AuthSection's first button); chip-skip traversal root cause is directional-search
  geometry, not lazy composition — from a right-edge focusable the wrap-content chip
  Row's rect has no beam overlap with the source. Fix:
  `Modifier.focusGroup().fillMaxWidth()` on all three chip rows (grid-columns,
  Orientation, Video-fit) so the group participates as one full-width unit; plus
  `giffyFocus(interactionSource)` per chip (M3 FilterChip draws nothing on D-pad focus —
  ring-test rule; the shared MutableInteractionSource avoids the two-focusable race).
- **Orientation filter honest empty:** the pool's shape is accepted (see Orientation
  filter section); FeedRow shows a small "No videos match your filters — Settings →
  Orientation" hint when itemCount==0 && refresh NotLoading. Device-verified.
- **Minor — NicheAbout + Follow entries:** both closed (see In-app links audit).
- **Parity OK (verified in code):** age gate, PIN lock, Settings/data saver/video
  fit/verified-only, per-feed prefs (same feedprefs blob + shared dialog),
  favorites/likes, quick actions, Surprise me, mute/speed. (Watch history removed 2026-10, DB v11 — no parity surface.)

## Design-system consistency (batch-15 audit — landed batch 16; palette SUPERSEDED by issue-#7)
The batch-15 reconciliation rule survives in this form: **sizes/roles/tracking/shape =
the standard platform scales — M3 for mobile, tv-material defaults for TV**. The palette
half ("borrowed site brand" GiffyColors lime/BrandRed mappings, findings F3–F6: AMOLED
surface mapping, inversePrimary, direct `GiffyColors.*` bypasses, bare-white scrim
alphas) was **superseded by the issue-#7 theme rebrand below** — do not apply those
older color fixes; the theme block is the current color truth (overlay tokens live in
the shared `PlayerOverlay` object in :core:ui, used by BOTH player screens). What still
holds from the batch-15 audit:

- **Type scales (landed):** tv-material 1.0.1 default Typography tokens ARE the standard
  M3 type scale: Display 57/45/36 (lh 64/52/44), Headline 32/28/24 (lh 40/36/32), Title
  22/16/14 (lh 28/24/20), Body 16/14/12 (lh 24/20/16), Label 14/12/11 (lh 20/16/16),
  fractional standard tracking. Mobile Typography = the standard M3 scale (titleLarge
  22, headlineMedium 28, headlineSmall 24, labelSmall 11; display slots stay 32 —
  nothing renders display today). TV `tvTypography()` = tv-material's own default token
  sizes/line-heights/tracking with ONLY the family swapped to DM Sans (no phone-sized
  copy).
- **Shape scale (F7, landed):** the M3 shape scale defined once (`Shapes` in GiffyTheme,
  mirrored for tv-material where screens need it): 4/8/12/16/28/full. Ad-hoc radii
  mapped: 3→4, 6→8, 12/16 stay, 24/32→28, 999→full. No new radii without a slot.
- **Slot-per-element rule (F8):** same semantic element = same text slot on both
  platforms (screen title = titleLarge via GiffyScaffold/TV titleLarge-line, row title =
  titleSmall, meta = bodySmall) — only the scale differs per platform. GiffyScaffold is
  the mobile standard; TV has no direct scaffold equivalent — the slot mapping is the
  consistency contract.
- Skipped by design: no light theme (stays dark-only — root AGENTS.md decision stands),
  no font swap (DM Sans stays), no TV overscan changes.

## Issue-#7 theme rebrand (DESIGN-theme-stream.md, landed 2026-10) — palette + focus tokens

The borrowed-site palette was retired wholesale for the issue's M3 dark
scheme: seed **#E5194D** (reserved — brand moments only, never a ColorScheme
member; in-scheme accent is #FF668F) over background seed #161617. `GiffyColors`
(Theme.kt) is now the token table itself; every role derives from it. What
changed where:

- **Values only on the accent surfaces** — the lime retirement was a scheme
  swap, zero call-site color edits: former secondary-lime surfaces (liked
  heart, tags/niche pills, speed chips, progress fill) ride #E3BDC4; verified
  tick is `tertiary` gold #E7C16B (tertiary stays badge-only — exactly these
  sites, no new ones); GiffyPillButton border/fill + giffyFocus ring ride
  `primary` #FF668F.
- **Dynamic color REMOVED** (issue §1.5): parameter, branch, pref plumbing
  (settings switch row + SettingsRepository flow + ViewModel expose) and the
  MainActivity wiring — seed-derived neutrality is not user-selectable anymore.
- **AMOLED:** `giffyColors(amoled)` overwrites background/surface/surfaceVariant
  + the surface-container ladder to pure black; accent-container rows untouched
  (the batch-16 collapse shape preserved verbatim). TV cards ride the collapsed
  compose scheme through `giffyTvColors` — no TV-specific neutral logic.
- **TV focus tokens (§3.1, giffyFocus retired in app-tv)** — focused fill
  `primaryContainer` **#7D0030**, focused content `onPrimaryContainer`
  **#FFD9E1**, focused border/glow `primary` **#FF668F**, unfocused rings
  `outlineVariant` **#51444A** (tv border/borderVariant now map exactly these).
  Migrated to tv-material components (verified from the 1.0.1 AAR): TvNiches
  row + TvQuickActions picker rows → `Surface` (colors + focused border);
  TvQuickActions QuickAction/SpeedAdjustRow/add-cancel + TvPlayerScreen
  Retry·Skip·Start-over·Back-to-feed → `Button`; TvPlayerScreen niche pills →
  `Surface` pill; TvHomeScreen For You chips + TvSourceFeedScreen Refresh/Sort
  IconButtons → tv `FilterChip`/`IconButton` (tv FilterChip carries
  @ExperimentalTvMaterial3Api in 1.0.1 — @OptIn per call site); TvMainActivity
  header pills replaced GiffyPillButton per the §4 audit (shared widget stays
  for mobile AgeGate, rest-look parity via §3.1 tokens).
- **Documented exception (the one giffyFocus survivor):** the SHARED feature
  screens hosted on TV (SettingsScreen/CustomFeedsScreen) cannot carry tv-material
  deps, so their M3 chip/button rows keep `giffyFocus` in :core:ui, recolored to the
  same trio (focused fill primaryContainer + primary border; M3 chips keep their own
  outlineVariant rest ring). GiffyFocus's `fillOnFocus` parameter is gone —
  primaryContainer is the default fill.
- **Scrims (§7):** PlayerOverlay.scrim keeps 55% black (inside the issue's
  40–60% art-scrim band); `secondaryOnVideo` tint moved white → onSurface
  #F7E9EC@70% (desc text on both players, bookkeeping for the issue's
  onSurface wording). Bare-white glyphs (seek flash, action rail) stay white
  (media icons, not themed text) per the audit.
- Device gates: PASSED 2026-10-17 (batch 28, TV36 + Medium_Phone) — see AGENTS.md
  batch-28 record. Focus pixels verified live (fill #7D0030-family, border #FF668F,
  no purple/blue) on header pills, Settings chips, quick-action panel rows,
  error-overlay rows and player niche pills; AMOLED sampled (0,0,0) on BOTH shells;
  CenterHold/MENU/quick-actions re-walked. ONE OPEN §3.4 finding: TV quick-action
  panel rows measure ≈37.5dp visual height — under the 48dp target guideline
  (D-pad-first surface; decision doc'd in AGENTS.md, not a theme defect).
