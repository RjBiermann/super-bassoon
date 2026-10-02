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
- **Inline feed autoplay (BUILT 2026-10-02, live-parity checked):** in 1-column
  mode the settled tile plays inline, muted + looped; tap opens the swipe
  player as today. Full policy lives in AGENTS-PLAYER.md ("Inline feed
  autoplay") — shared-player, no-history-write, data-saver-off rules are
  normative there. Settings surface: app-only pref **"Autoplay in feed"**
  (whole-row switch, under the Grid columns block; TV keeps none — the pref
  only affects the 1-col grid). Live-site check that gated the build
  PASSED: the site's own 1-col feed runs ONE shared `<video>` (muted:true,
  loop:true) on the settled tile — observed via Playwright at 390×844.
- TikTok-style swipe player.
- Long-press (tile or player) quick sheet: four panes per the restructure spec
  (BUILT 2026-10-01, live-verified both apps): main = Favorite · Pin · Add to… ·
  Tags… · Block… · Close; panes are in-place swaps.
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
- ~~Dead control: CollectionsScreen share `IconButton(onClick = {})`~~ — CLOSED: the share
  icon was removed (top-bar actions now = create; per-item edit/delete). No dead control remains
  there.
- Explore error path — UPDATED round 3: a `loadFailed` flag + error EmptyState + Retry button
  were added, but the wiring is still broken: `.onFailure` only does `loading = false` and
  **never sets `loadFailed = true`** → the error branch is unreachable and the
  `LaunchedEffect(creators.size)` refetch still never re-fires on failure. Retry UI exists,
  cannot ever render. Sharpest open item. Same silent-failure pattern confirmed in
  NicheAboutScreen (three `runCatching{}.onSuccess{}` fetches, no failure state) and
  FollowingScreen (no error/empty/loading handling at all) — see round 3 below.
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
  (target size + TalkBack state announcement), not Switch-only hit area — CLOSED for data-saver,
  verified-only and AMOLED (all whole-row toggleable now); the last open instance is the
  dynamic-color row (round 3 Medium below).
- FeedScreen empty-Favorites hint — closed (points at the player's ⋯ sheet now).
- Double-tap like — closed: double-tap = LIKE (never unlike), rail heart toggles.
- TvPlayerScreen pause/seek — closed: horizontal = ±10s seek with flash,
  hold-repeat progressive, CENTER = play/pause; TvSourceFeedScreen onMenu opens
  quick actions.
- ~~Tile a11y noise~~ — CLOSED on mobile AND TV (GifCard image decorative;
  the visible "@user" Text announces the creator).
- ~~Search suggestions render a bare count~~ — CLOSED: rows now show `"%,d gifs"`
  (grouped + unit).

**Low**
- ~~OfflineNotice duplicates core:ui EmptyState — reuse it~~ — CLOSED: OfflineNotice deleted;
  EmptyState is the only empty surface.
- Speed label precision — CLOSED with correction: both labels are `Locale.US` now, and the
  original "%.1f" claim was wrong — speed steps are 0.25, so 2 decimals is real precision
  (0.25×, 1.25×). Kept %.2f on both platforms.
- PinLockScreen: keypress haptic DONE (KeyboardTap per key, LongPress on wrong PIN —
  2026-10-02); wrong-PIN shake skipped (haptic covers the feedback; brief screen).
- NichesScreen initial fetch inside `remember { scope.launch {} }` — should be
  LaunchedEffect(Unit) (same length, correct idiom).
- contentDescription capitalization varies ("search" vs "Sound On") — copy consistency.

Skipped by design: live D-pad traversal audit (needs emulator session via debroid/mobile
MCP); contrast math under dynamic-color theme (Material guarantees it).

## UI/UX + a11y + feature audit round 3 (2026-10-02, docs-only)
Full sweep of every composable in feature:*/app-*/core:ui (grep-driven a11y patterns +
file-by-file reads of the smaller screens; FeedScreen/PlayerScreen/Settings covered region-by-
region). Stale round-1 items above are already marked. New findings, code-verified, fixes
unscheduled — a user slice picks.

**Critical (broken UI, must fix first)**
- ~~ExploreScreen `loadFailed` is a dead flag~~ — CLOSED 2026-10-01: onFailure writes it,
  Retry clears it, and the size/loadFailed LaunchedEffect re-fires on failure.

**High (silent failures — same class as the Explore item)**
- ~~FollowingScreen: zero error/empty/loading handling~~ — CLOSED 2026-10-01:
  FollowingViewModel gained loadFailed (creators+niches result), the screen shows
  error+Retry and a "Nothing followed yet" empty state.
- ~~NicheAboutScreen swallow failures~~ — CLOSED 2026-10-01: detail failure shows
  error EmptyState + Retry (loadAbout() local fun shared by initial + retry); section
  fetches degrade to empty sections.
- System back from an opened feed exits the app: MainActivity's BackHandler covers only the
  overlay flags (settings/search/collections/…), but feeds opened inside FeedScreen via
  `viewModel.open(...)` (creator chip, More ▾ item, search submit, Explore/Following/Niche
  hops) set no flag — back gesture finishes the activity. The doc's "no back stack" posture
  covers Settings, not this: from a creator feed the only escape is the Trending chip.
  Minimal fix spec'd in the links audit below (FeedViewModel source history + BackHandler).
  [BUILT 2026-10-01: open() remembers the prior source; canGoBack StateFlow +
  FeedScreen BackHandler pops one level; verified compile + code-review.]

**Medium**
- ~~CustomFeedsScreen delete has NO confirm dialog~~ — CLOSED 2026-10-01: confirm-first
  AlertDialog (mirrors Groups), same destructive-class wording.
- ~~CustomFeedsScreen ref chips dead tap + <48dp Remove~~ — CLOSED 2026-10-01:
  AssistChip (click = remove, the X states it; refs re-add trivially), 48dp IconButton
  with "remove @user"-style descriptions.
- ~~TV home login drift~~ — CLOSED 2026-10-01: isLoggedIn is now a token-flow StateFlow;
  Following row refresh keyed on it (Liked row rides its own token flow).
- ~~FeedRow LoadState.Error~~ — CLOSED 2026-10-01: error rows show "Couldn't load <title>"
  + Retry (gifs.retry()); the filter-empty hint landed earlier. Explore/Following creator-row
  guards still open (failed fetch = blank strip).
- ~~Settings dynamic-color Switch-only row~~ — CLOSED 2026-10-01: whole-row toggleable
  (last switch row; data-saver/verified-only/AMOLED already done).

**Low**
- ~~"upstream" wording ×2~~ — CLOSED 2026-10-01: AgeGate reworded ("the sites it browses"),
  AuthSection "sync to your account".
- ~~WebViewLoginScreen println~~ — CLOSED 2026-10-01: removed.
- ~~Inverted close/back labels~~ — CLOSED 2026-10-01: Settings X = "close",
  WebViewLogin ArrowBack = "back".
- ~~PlayerScreen dead onTogglePlay param~~ — CLOSED 2026-10-01: param + wiring removed
  (PlayerControls builds its own player lambda).
- ~~TvNiches 📌 emoji prefix~~ — CLOSED 2026-10-01: PushPin vector icon with
  contentDescription "pinned".
- ~~TV media NEXT/PREV keys~~ — CLOSED 2026-10-01: handled (walk the list, up/down parity).
- ~~SearchScreen rows <48dp~~ — CLOSED 2026-10-01: vertical padding 10→14dp.
- contentDescription capitalization — CLOSED 2026-10-02: the "Back"×1 drift went with
  GiffyScaffold; "remove"-prefixed descriptions verified lowercase everywhere.
- AuthSection signed-in state shows only "Signed in" — no @username. ~~BLOCKED~~ —
  **CLOSED 2026-10-02 batch 3:** the user supplied a fresh token bundle; the id_token's
  `preferred_username` (standard OIDC claim) was live-verified as the account username
  (`v1/users/{claim}` → 200, response username == claim). `TokenStore.usernameFromJwt`
  (stdlib Base64Url payload decode, no guessing) + `AuthViewModel.username` → AuthSection
  now shows "Signed in as @<username>" ("Signed in" fallback if the claim is absent).
  Unit-tested in `TokenStoreTest`.
- RateLimitBus "cooling down" indicator — decided 2026-10-01: **spec'd-not-built**
  (marker only; no UI consumer — build when a user-visible failure trace demands it).
- TV home "More ▾" DropdownMenuItems have no giffyFocus ring (M3 popup rows) — whether
  they show visible D-pad focus at all needs the skipped device check.
  ~~Still open~~ — **CLOSED 2026-10-02 (later session, TV36):** M3 popup rows DO show
  visible D-pad focus (a lighter fill on the focused row — zoomed screenshot on the
  fresh-profile single-row menu). No giffyFocus needed for dropdown items.

## In-app links audit + navigation-library decision (2026-10-02, docs-only)
Trigger: "creator/niche/tag etc. should be links so users can navigate". Audit of every
user-visible entity reference × the surface that renders it. Destination plumbing already
exists for ALL of them — `FeedSource` (Creator/Niche/Search/Group/Custom) + `FeedViewModel.open()`
are the in-place link targets, and every screen that needs a hop already takes an
`onOpenCreator`/`onOpenNiche` callback. No new screens required; this is wiring, not
architecture.

**Already linked (working today):**
| Entity | Surface | Destination |
|---|---|---|
| Creator | FollowingScreen / ExploreScreen rows, NicheAbout top-creators | creator feed |
| Niche | FollowingScreen rows, NichesScreen rows, NicheAbout related | niche feed |
| Creator (search results) | FeedScreen creator-chips row (above search results) | creator feed |
| Group / Custom feed / Pinned | chips, More ▾, TV pills + dropdown | their feeds |
| Gif (tiles/cards) | mobile tiles + TV cards | player |

**Missing links — status 2026-10-02:**
1. **@username → creator feed — BUILT (mobile):** CreatorLabel gained an optional
   `onClick`; wired on the mobile tile caption (in-place `viewModel.open`), the player
   bottom cluster (open + close the player — the feed behind has swapped, like TikTok
   leaving the player on a hashtag) and the quick-sheet row (#3). TV captions
   deliberately stay non-tappable: a clickable inside a focusable Card steals D-pad
   focus; the TV link equivalent is the "Open @user's feed" row in TvQuickActions
   (built same day).
2. **Player tag chips → tag feed — BUILT (mobile):** chips call
   `FeedSource.Search(query = tag)` + close the player. TV keeps its quick-actions
   tags pane as the equivalent.
3. **Quick sheet "Open @user's feed" row — BUILT 2026-10-02:** QuickSheet main pane
   (after Favorite) + TvQuickActions main pane; both `viewModel.open(FeedSource.Creator(...))`
   + dismiss.
4. **FollowingScreen niche rows** carry no About entry and TV has no NicheAbout at all
   (existing "Minor" gap) — link exists, depth missing; unchanged here.
5. **Gif niches → niche feed — MOBILE BUILT 2026-10-02 (slice a–c; the live-check
   caveat did not block it):** the model keeps the payload's name (`Gif.niches:
   List<NicheRef>` — NicheRef(id, name); Room `gifs` gained a `nicheNames` map via
   MIGRATION_8_9, ids column untouched, round-trips through cache). The mobile player
   cluster renders up to 3 niche pills BEFORE the tags (labels = payload name, id
   fallback for pre-v9 cache rows), tap → `FeedSource.Niche(id, name)` + player close;
   tags went plain lime text (style decision (b)) — no section heading is rendered, so
   no new wording was invented (the (d) watch-page label check still applies IF a
   labelled section is ever added). TV equivalent — **BUILT 2026-10-02:** TvPlayerScreen
   gained the same ≤3-pill row (labels = payload name) + `onOpenNiche(FeedSource.Niche)`
   callback wired in TvMainActivity (`openFeed = niche`, same swap semantics as
   onOpenCreator). Pills use the TvQuickActions row pattern (M3 TextButton + shared
   giffyFocus ring + interactionSource); pill row only composes when `gif.niches`
   is non-empty. Compose-cluster pill render + the niche-gif composition path were
   breakpoint-verified on TV36 (pill-row line hit with a 5-niche gif); the visible
   D-pad focus ring cannot be verified on this image — the shared-dialog ring check
   ran the same day and FAILED (see round-3 #4), so the pills inherit the same
   limitation as every other in-dialog focusable on TV (MENU quick actions remain
   the reliable link path). Parked residue: none for the wiring itself.
6. **Possible gap to verify live:** the site watch page's "Suggested Niches / Suggested
   Creators" + related "you might like" strip have no in-app counterpart (the swipe player
   itself serves adjacent discovery; TV player has nothing). Verify against the live watch
   page before spec'ing anything — add to the parity audit only if the strip is real.

**Back-stack prerequisite (ties into round 3 High):** wiring @user links on mobile exposes the
back problem immediately — open a creator feed from a tile and system back exits the app.
Minimal standard fix BEFORE/with the link wiring: a small source history in FeedViewModel
(`List<FeedSource>`; `open()` appends, a `BackHandler` inside FeedScreen pops it, disabled on
home) — platform-native Compose mechanism, ~20 lines, no dependency.

**Navigation library decision: NOT now (revisit on trigger).**
Reasoning, ladder-style:
- The links do not need it — `FeedSource` + `open()` already navigate; a nav library would
  route the same hops through a controller with identical in-place semantics.
- Deep links — the library's biggest freebie — are excluded by §0 (viewer-only, share-only).
- The screen-shell flags (MainActivity/TvMainActivity) are confined and TV's BackHandler
  already covers every state; the ONE back bug lives in FeedScreen's in-place source, which
  a nav library wouldn't naturally own either (feeds are state, not destinations — the
  chip-tab model).
- New dependency cost is real: no navigation-compose today; adopting typed routes means
  re-plumbing both shells + splitting FeedScreen into home vs source-feed destinations
  (the TV's TvSourceFeedScreen shape) — a week-shaped restructure to fix one ~20-line bug.
**Revisit triggers:** (a) a second multi-hop navigation pain appears (e.g. nested About →
related-niche chains losing position), (b) screen arguments need serialization guarantees
beyond `rememberSaveable` booleans, (c) the shells' flag lists keep growing. If adopted:
Navigation Compose typed routes (kotlinx-serialization) in `:app-mobile` first; TV follows
only if its shell grows (its leanback back behavior is already correct).

**Feature audit (2026-10-02) — parity writes unchanged; corrections**
- GifsApi has `POST v2/me/collections` (create) + `DELETE v2/me/collections/{id}` but NO
  add-content write; no report endpoint; no `GET /v2/me/followers`; no tags-trending search
  tab; no niches/suggest. All eight round-1 parity gaps stand (Report · Add to a Collection ·
  Add to a Niche · Followers page · Tags browse tab · Niches suggest · server search history ·
  creator stats header).
- Collections empty-state copy — FIXED 2026-10-02 (the add-write shipped
  2026-10-01): now points at the real path ("Create New Collection, then
  long-press a tile → ⋯ → Add to a Collection").
- TV parity list correction: **§8 shuffle on TV is CLOSED** — TvSourceFeedScreen renders the
  shared FeedFilterDialog (Shuffle/Off/Reshuffle chips) over the same feedprefs blob +
  shared FeedRepository.paging path. The "TV has the Filter chip only" line above was stale;
  remaining TV gaps (Search, Groups mgmt, custom-feed mgmt, Collections, followers) stand.

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
- **TV intent (2026-10-02, user decision): the horizontal filter is a must-work-well
  use case on TV** — the TV grid is a shared 10-foot screen, vertical/portrait tiles
  waste it, so filtering to horizontal is the primary TV orientation behavior and any
  future filter-path change must not regress TV (TV bypass notes above apply:
  `LikedNetworkPagingSource` + the TV Continue Watching row). Vertical-only on TV is
  accepted as a dead end, not a target: portrait tiles letterbox against the row
  height and waste the screen.
- **Vertical video on TV (option, not built): fullscreen-fill the width**
  (`RESIZE_MODE_ZOOM`) so the image spans the 16:9 panel — acceptable live picture,
  but ~9:16 content crops heavily top/bottom; only viable when the user explicitly
  wants filled-screen over whole-frame. Keep TV default FIT; Fill is a shared-plug
  user choice via the existing Fit/Crop/Stretch pref, not a TV-forced default.

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

1. **SPLIT — DONE (2026-10-01):** QuickBlockSheet + AddToCustomFeedDialog +
   listStyle moved to `QuickSheet.kt` (feature:feed), zero logic change.
2. **SMALL MERGE — DONE (2026-10-01):** shared `AgeGate` in core:ui
   (onConfirmed suspend, onExit, requestInitialFocus flag replaces TV's
   gateFocus); both shells consume it; TV layout now shares the centered one
   (visual change on TV: centered instead of top-160, pill button instead of
   plain M3 Button).
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

## UI/UX structure round 3 — simplification audit (2026-10-02, docs-only)
Post-fix-batch rescan (after the round-3 batch + the Add-to-Collection/Niche commit).
Simplification only — every feature stays; findings collapse implementation, not scope.
Ranked by lines removed per risk, smallest diffs first. Threshold rule from round 2
(consolidate at the 4th instance) applied where it now fires.

1. **CreatorLabel convergence — DONE (2026-10-02):** core:ui `CreatorLabel(username,
   verified, tint, style, tickTint, tickSize, onClick)` replaced all 10 hand-rolled
   copies (FeedScreen tile caption + creator chip, Explore/Following/NicheAbout rows,
   PlayerScreen cluster, QuickSheet header, TvHomeScreen ×2, TvPlayerScreen cluster,
   TvQuickActions header). Tick size follows the label (14dp default, 12dp TV cards,
   16–18dp player clusters); tint colors text, tickTint the tick (Info cyan fallback).
   Optional `onClick` powers the links-audit @user wiring.

2. **GiffyScaffold — DONE (2026-10-02):** core:ui `GiffyScaffold(title, onBack, modifier,
   backIcon, backDescription, actions, snackbarHost, content)` replaces the boilerplate in
   Explore, Following, Collections, Groups, CustomFeeds, Niches, NicheAbout, Settings
   (Close icon variant) and FeedScreen (onBack = null, home surface). PlayerScreen's top
   bar is a bare TopAppBar inside AnimatedVisibility — not a Scaffold site, left alone.
   "Back"→"back" copy drift (CustomFeeds) fixed as the side effect.

3. **List-fetch state machine: the round-2 n=4 threshold is now CROSSED — CONVERGED 2026-10-02.**
   ExploreScreen + NicheAboutScreen converted to Hilt VMs (`ExploreViewModel`,
   `NicheAboutViewModel`) with the same StateFlow machine as Niches/Following; MainActivity's
   standalone `api` injection died with the `api=` composable params. All four list screens
   now speak one dialect — the loadFailed/Retry shape is consistent, the drift class (a
   remember-vars machine silently losing its error branch) is closed. A further shared
   skeleton extraction stays parked (four flat VMs beat one abstraction until real reuse
   shows).

4. **Picker-dialog family: six implementations of one dialog shape — CLOSED (2026-10-02, device check ran, ring test FAILED).**
   Add-to-Custom-Feed, Add-to-a-Collection, Add-to-a-Niche — each × mobile + TV = 6 near-
   identical "list of options → tap → Cancel" dialogs (~820 lines across the two files).
   The niche tag-matching ordering rule (`gifTags → matching → ordered`) is copy-pasted
   VERBATIM in both niche dialogs. Consolidation: one shared M3 picker dialog in
   feature:feed (platform-neutral — the shared FeedFilterDialog already runs on TV) + one
   shared `orderNichesByTagMatch(gifTags, niches)` rule beside gifFeedRefs. TV caveat:
   TvQuickActions rows carry the giffyFocus ring — a shared dialog either accepts default
   M3 focus visuals (what the shared FeedFilterDialog's contents already do on TV) or
   takes a per-row decoration parameter. Decide on device, don't guess; if the ring test
   fails, keep only the ordering rule + custom-feed dialog shared and stop.
   **RAN THE DEVICE CHECK (TV36, 2026-10-02):** drove D-pad into the shared FeedFilterDialog's
   chip rows on the niche source feed — the focused "HD only" chip (`focused="true"` in the
   uiautomator dump) renders with NO visible focus treatment (default M3 outline only,
   zoomed screenshot). Ring test FAILED ⇒ per this item's own stop rule the merge stops:
   the shared ordering rule landed (batch 2), the dialog merge does NOT proceed. A TV
   picker screen either needs giffyFocus per row (per-row decoration parameter) or
   tv-material components — same conclusion the TvQuickActions rows already prove.
   **TV equivalent DONE (2026-10-02, later session — no mobile-side changes):**
   TvQuickActions' "Add to…" row is now unconditional (divider + row always render —
   the no-custom-feeds state reaches the picker instead of the row disappearing), and
   TvAddToFeedDialog gained the Collection-style empty state ("No custom feeds yet —
   create one on the phone (“Custom feeds)."). Device-verified on TV36: main pane
   shows "Add to…" with zero custom feeds; pane swap → picker renders title + empty
   hint + disabled Add + Cancel.

5. **avgColorOr + AudioBadge — DONE (2026-10-02):** both moved to core:ui
   (`AudioBadge.kt`); the two call sites consume them (tile/card badge padding drift
   collapsed to 4dp inner). Full shared GifThumb stays skipped on purpose.

6. **packNicheRef/parseNicheRef — DONE (2026-10-02):** both helpers live in
   CustomFeedsScreen.kt beside `customRefSummary`; all four parse sites (TV pills, TV
   dropdown, FeedScreen pinnedNiches, FeedPageFetcher, CustomFeedsScreen summary) and the
   two prefixed pack sites (FeedScreen, TvSourceFeedScreen) call them.
   `NicheRefTest` covers both variants + round-trip. Pin packing in core:datastore
   (`togglePinnedNiche`) stays inline — core can't reach feature:feed.

7. **UX simplification (visual, not code): the FeedScreen chip pile-up — BUILT (2026-10-02).**
   On a For-You niche/creator feed with options, the grid sat under up to five stacked
   chrome rows — feed tabs · matching creators · For-You scope · sorts · Filter/Clear —
   before the first tile (bad on a 360dp-wide phone in portrait). Landed: sort chips +
   Filter ▾ + Clear merged into ONE row — sorts scroll left under a fixed right-aligned
   Filter/Clear pair (weight(1f) scrollable inner row); both remain per-feed controls,
   scope selector untouched. Compile + ktlint + detekt + unit tests green on :feature:feed.

Scanned-and-rejected (documented so the next audit doesn't re-derive them):
- **QuickSheet vs TvQuickActions row idioms** (TextButton+ripple vs Button+giffyFocus) —
  the touch/D-pad split is the module-map's stated reason to exist; not a merge.
- **FeedScreen 32K / PlayerScreen 44K** — re-affirmed cohesive post-batch; private
  PlayerPage/PlayerControls/ActionRail remain that player's own units.
- **PlayerControls 24dp slot vs TvPlayerScreen always-on 3dp line** — different behavior
  contracts (auto-hide vs 10-foot always-visible), not duplication. Scope narrowed
  2026-10: "always-visible" is the PROGRESS LINE only — the text layer (creator /
  description / niche pills) follows the mobile idle-hide rule (player text auto-hide,
  TV parity gaps below).
- **FeedViewModel vs TvHomeViewModel block/favorite creator functions** — RESOLVED
  2026-10-02 (re-check ran; the Add-to writes had settled in FeedViewModel): the TV
  quick actions already route through a fresh shared FeedViewModel, so TvHomeViewModel's
  toggleFavoriteCreator/blockCreator/creatorState/togglePinnedCreator/pinnedCreators
  were dead — deleted (~35 lines), not merged.
- **Two time formatters** (formatRemaining vs seekBy's fmt) — 6 lines each, different
  shapes (remaining vs position); sharing saves nothing real.

## Site feature-parity audit (2026-10-02, live-verified — doc-only, no code)
Consumption surfaces are a full mirror: home tabs, Explore, Niches (index + Feed/About + join),
creator feed (tag chips, Follow), watch page, Saved Collections, Following, search
(GIFs/Images/Creators/Niches tabs + Tags scope — see "Full-scope search" below), settings. Deliberately excluded per §0: upload/creator tools,
Data Dashboard, boost/live-cam/only-fans/ads modules, premium (see AGENTS-NETWORK.md —
galleries verified dead, `v2/feeds/modules` checklist fully covered).

Real gaps (site has them, app doesn't) — **spec'd-not-scheduled, a user slice picks**:
- **Report** — UNPROBEABLE (2026-10): every plausible endpoint 404'd with the test
  token (see AGENTS-NETWORK.md). Stays spec'd-not-scheduled; do not guess.
- ~~Add to a Collection~~ — BUILT 2026-10-01: write probed live (POST
  v2/me/collections/{id}/gifs {gifId} → 204, reverted), picker dialog in the Add-to…
  pane on both apps (mobile AddToCollectionDialog, TV TvAddToCollectionDialog; empty
  state points at Collections). In-app e2e not possible (emulator Keystore token
  blocker); contract verified at API level.
- ~~Add to a Niche~~ — BUILT 2026-10-01: write probed live (PUT v2/gifs/{gifId}/niches
  {nicheId} → 202, reverted); AddToNicheDialog / TvAddToNicheDialog in the Add-to… pane —
  joined niches, tag-matching first (site popup parity), 8-row cap, empty hint.
- **Followers page** — BLOCKED on data shape (2026-10): both `GET /v2/me/followers`
  and `GET /v1/me/followers/populated` return 200 but the test account has zero
  followers — the row DTO is unverifiable, and building against a guessed shape
  risks silent decode breakage (repo no-guess rule). Re-probe with an account
  that has followers before wiring.
- ~~Tags browse tab~~ — BUILT 2026-10-02 (section form): `GET /v2/tags/trending` wired
  (GifsApi.trendingTags + TrendingTagsDto/TrendingTagDto); SearchScreen's empty-query
  state gains a "Trending tags" section (#name + "N gifs", tap = the tag search feed via
  the existing submit path — records history like any search). Fails silently to an
  absent section. Note correction (2026-10-02, live): the site's search results-page tabs
  are GIFs/Images/Creators/Niches — there is no Tags results tab (`/search/tags` 404s home);
  trending tags live in the header search dropdown ("Trending Searches", links → tag feeds).
  The app's empty-state section remains the equivalent surface.
- **Niches suggest** — `GET /v2/niches/suggest` (tag-context niche suggestions, verified)
  un-wired; independent of the full-scope search spec (the Niches search tab uses
  `niches/search/previews`, not suggest) — stays a fallback.
- **Server search history** — `GET /v2/search/user-history` verified; site syncs history
  server-side, app keeps local Room history. Existing spec'd-not-scheduled fallback now has
  a verified endpoint to hang on.
- ~~Creator stats header~~ — BUILT 2026-10-01: FeedViewModel.refreshCreatorStats +
  creatorStats; mobile FeedScreen shows the counts row on Creator sources; TV
  TvSourceFeedScreen shows it under the title (same shared VM state). Live-verified
  on mobile (@lilymatrix → "154 posts · 121 followers · 200,599 views").

Parked (not gaps): For You server blend is `v2/feeds/for-you` as-is (70% favorite-creator
weighting was a local-blend idea, superseded by the server feed); search-history sync and
server collections remain spec'd-not-scheduled fallbacks per AGENTS-NETWORK.md.

## Full-scope search (spec'd 2026-10-02 — BUILT same day, mobile; on-device verify pending)
The site's search results page (Playwright-verified live, desktop, `query=feet`):
`/search/<scope>?query=&order=score` with result tabs **GIFs · Images · Creators · Niches**.
`/search/tags` 404s home — tags have no results page; the header search box's Tags scope
routes tag matches to the tag feed (`/gifs/<slug>`). App spec: SearchScreen gains the same
tab row; the GIFs tab keeps the existing `FeedSource.Search` feed path unchanged.

Scope → endpoint (all 200-verified 2026-10-02 with the anonymous temp token; row shapes in
AGENTS-NETWORK.md):
- **GIFs** — existing `GET /v2/gifs/search`; the site sends `type=g` explicitly (default `g`).
- **Images** — same endpoint, `type=i`. Same response envelope + GifDto rows, but rows are
  stills (`type=2`, `duration=null`, `hls=false`, `urls.sd/hd` are jpgs). Render as a
  static-poster grid — **no player path** (nothing to play, no watch-history write).
- **Creators** — `GET /v2/creators/search/previews?order=best_match&page=1&count=30&query=`
  → `{gifs:[…]}`, one preview gif per matched creator (dedupe by `gif.userName`);
  tap → `FeedSource.Creator` (existing destination plumbing).
- **Niches** — `GET /v2/niches/search/previews?order=best_match&page=1&count=30&query=`
  → `{previews:[{niche, gif, user}]}` (niche object + preview gif + owner);
  tap → `FeedSource.Niche` (existing destination plumbing).
- **Tags** — `GET /v1/tags/match?query=` → tag-name array (`["Feet"]`); renders as
  suggestion rows in the search empty/suggestion state, tap = the tag feed via the existing
  submit path (alongside the existing Trending-tags section — one list, no new screen).

**BUILT (2026-10-02, later session):** SearchScreen shows the 4-tab TabRow whenever a query
is typed (GIFs default — keeps the existing submit path untouched; switching tabs fires the
scope's single request). SearchViewModel injects ContentFilter and caches per-scope results
keyed by trimmed query (maps capped at 12; `cappedInsert`/`nicheRows` extracted + unit-tested
in `SearchScopeResultsTest` — cap eviction, blank-id drop, blocked-preview-gif row drop).
New API surface: `search(type=)`, `creatorSearchPreviews`, `nicheSearchPreviews`, `tagsMatch`
(tagsMatch wired endpoint-side only — the Tags section is the existing Trending-tags one; a
typed-query tags row was NOT built: the empty/suggestion state already covers it). Images
render a 3-up static poster strip (avgColor placeholder, no player path, no tap target);
Creators/Niches render preview-thumb rows → `onOpenCreator`/`onOpenNiche` callbacks wired in
MainActivity (search closes, feed opens — same swap as NichesScreen).niches suggest remains
a fallback (explicitly independent of this spec).

Rules:
- Tabs lazy-load (1 request on first open, cached in the VM, never refetched per
  recomposition) — the ≤10-req/5s invariant stays intact.
- ContentFilter: every gif-backed row (GIFs/Images tiles, creator previews, niche previews)
  flows the single ContentFilter — a row whose preview gif is blocked drops the whole row
  (leak-zero at the cheapest check point, no second filter surface).
- History: app keeps local Room history only; the site's `POST /v2/search/user-history`
  (202) stays a spec'd-not-scheduled fallback.
- TV: unchanged — search stays in the TV parity-gap list (D-pad device work).
- Skipped: no Collections search scope (site has none), no tags results page (site has
  none), no search-scope selector in the app header (the tab row on the search surface
  covers it — the site's header scope strip is a redundant desktop affordance).

## Quick actions — submenu restructure (BUILT 2026-10-01; was spec'd 2026-10-02)
Quick sheets must stop growing flat rows. Two more sheet items already land this
window (Report, Add to a Collection — see the parity audit below); a flat main
pane would be ~10 rows. Spec: generalize the mechanism QuickBlockSheet already
has (`var view by remember` — "main"/"tags" today) into four panes, identical
shape on mobile + TV. No new component, no navigation — in-place content swap.
BUILT: QuickSheet.kt (extracted from FeedScreen.kt) + TvQuickActions — main /
addto / tags / block panes; TV re-requests pane-first-row focus on swap
(LaunchedEffect(view)). DEVIATION (deliberate): the spec omitted the Pin row
and claimed TV has no pinned rows — both are wrong (pinned creators feed the
home top-row pills on BOTH apps), so Pin/Unpin stays on main on both.

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
- **Player text auto-hide** (reported 2026-10, doc-only spec): the mobile player
  clears creator/description/tags after 3s idle (playing && not scrubbing only,
  `IDLE_HIDE_MS`) and keeps just the thin progress line; `TvPlayerScreen` shows
  creator + description + niche pills statically while playing. Spec for the TV
  slice (D-pad device work, parked with the other TV items):
  - Same idle rule as mobile: playing && not seeking → after 3s fade the whole
    text cluster; paused or mid-seek → visible. Progress line stays always-on
    (that contract above is unchanged).
  - Any D-pad key press (seek / center / play-pause) resets the timer and
    re-reveals — the 10-foot standard is "text hides while watching", not
    "text is unreachable".
  - When hidden the niche pills leave focus traversal entirely — invisible
    focus targets are a D-pad trap; first DOWN re-reveals the cluster instead
    of landing on an unseen pill.
  - MENU quick actions keep working regardless of visibility state.
  [BUILT 2026-10-02 batch 5 — one LaunchedEffect per gif (playbackState!=IDLE &&
  playing && !seekFlash → 3s → visible=false); any onPreviewKeyEvent sets visible=true;
  hidden cluster (incl. pills) leaves composition so focus traversal stays clean;
  progress line + seekFlash outside the AnimatedVisibility. D-pad visual verify
  still pending (emulator).
  [D-pad verify DONE 2026-10-02 (later session, TV36): player entry → 3s+ idle →
  uiautomator dump has NO text nodes (cluster left composition); any key (LEFT)
  re-revealed it (@creator visible); idle again → gone. Both directions verified.]]
- **Search** — zero `api.search` calls, no search UI anywhere in app-tv. Mobile search is
  multi-type (GIFs/Images/Creators/Niches tabs + tag suggestions — see "Full-scope
  search"). TV browsing = home rows + Niches + pinned/custom feeds only.
- **Groups management** — no GroupsScreen equivalent: create/favorite/BLOCK niche
  groups unreachable on TV. Mobile's blockable group bundles are the macro-filter
  path (leak-zero); TV only has per-item tag/creator quick-action blocks.
- **Custom-feed management** — TV can open existing custom feeds (home ⋯ menu) and
  add to them (quick actions), but create/rename/delete is mobile-only
  (CustomFeedsScreen). Stacks with the empty-creation deferral above.
- **Collections** — mobile-only screen (CollectionsScreen); nothing on TV.
- **§8 shuffle** — ~~no shuffle on TV source feeds~~ CLOSED round 3: TvSourceFeedScreen
  renders the shared FeedFilterDialog (Shuffle/Off/Reshuffle) over the same feedprefs blob;
  shuffle applies via the shared FeedRepository path. Mobile parity holds.

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
- TV SettingsScreen D-pad traversal is messy: directional searches skip chip
  rows (Orientation/Video-fit chips) or escape to the top-bar back button.
  [Initial focus on entry FIXED 2026-10-01: SettingsScreen takes
  requestInitialFocus — AuthSection's first button carries the FocusRequester
  (new firstButtonModifier param); TV passes true.] The chip-skip traversal
  itself ~~is still open~~ — **CLOSED 2026-10-02 (later session, device-verified on
  TV36):** root cause is directional-search geometry, not lazy composition — from a
  right-edge focusable (Set PIN) the wrap-content chip Row's rect has no beam overlap
  with the source, so the full-width row below wins the DOWN. Fix:
  `Modifier.focusGroup().fillMaxWidth()` on all three chip rows (grid-columns,
  Orientation, Video-fit) so the group participates as one full-width unit; plus
  `giffyFocus(interactionSource)` per chip (M3 FilterChip draws nothing on D-pad focus
  — same ring-test lesson; the shared MutableInteractionSource avoids the
  two-focusable race). Verified: DOWN from Set PIN now lands on the Orientation chip
  group (enters at the rightmost chip — beam-logical), zoomed screenshot shows the
  BrandRed ring, chip click moves selection, DOWN leaves the group cleanly.
- **Orientation filter is data-bound, not fetch-bound (probed 2026-10):** the
  API has no orientation parameter, so the filter is client-side over whatever
  pool the feed returns. Live trending pool = 99/99 portrait (0 landscape);
  with the global filter "horizontal" the home rows legitimately show nothing
  (the empty-homepage incident: a stale "horizontal" pref over an
  all-portrait cache hid every Trending/Top-This-Week tile behind the
  reserved empty strip — live-reproduced, fixed by restoring Any).
  Horizontal-on-TV works only when a pool actually contains landscape gifs.
  User decision (2026-10-02): this is accepted as the pool's shape, not a defect
  to pave over — on TV horizontal is the wanted filter (see the Orientation
  filter section above), and when a pool is all-portrait the empty-state hint is
  the honest answer, not a silent Any-fallback.
  Pickup: FeedRow shows a small "No videos match your filters — Settings →
  Orientation" hint when itemCount==0 && refresh NotLoading. [DONE 2026-10-01:
  FeedRow shows the hint; live-verified conditions in code, on-device check
  pending a filtered-empty state.] [ON-DEVICE VERIFY DONE 2026-10-02 (later
  session, TV36, fresh profile): orientation Horizontal over an all-portrait
  cache → Trending row renders the hint under the title (zoomed screenshot,
  also in uiautomator once the recomposition settles). Verification lesson:
  the flatMapLatest pager restart + mediator refresh + empty-walk takes ~15-30s,
  and suspending an in-flight composition (debug breakpoint) freezes the
  pre-hint frame — take the observation only at steady state, or the hint
  looks missing when it isn't.]

**Minor:** no NicheAbout entry from TvNichesScreen (mobile-only); TV "Following"
row is read-only — unverified whether a follow action exists anywhere on TV.

**Parity OK (verified in code):** age gate, PIN lock, Settings/data saver/video
fit/verified-only, per-feed prefs (same feedprefs blob + shared dialog),
favorites/likes, watch history, quick actions, Surprise me, mute/speed.

## Pending backlog batch (2026-10-02 — executed from the parked/deferred items)
Worked off the doc'd pending lists (round-3 simplification winners, links audit #1–#3,
residual Low a11y, Tags parity gap). Compiled + ktlint + detekt + unit tests green on
all touched modules (:core:ui/:core:network/:feature:feed/:feature:search/:feature:settings/
:feature:auth/:app-tv/:app-mobile). What landed:
- core:ui `CreatorLabel` (10 sites), `GiffyScaffold` (9 sites), `AudioBadge`+`avgColorOr`
  (2 sites each), feature:feed `packNicheRef`/`parseNicheRef` (+ `NicheRefTest`).
- Mobile links: tile-caption @user → creator feed (in-place open), player cluster
  @user + tag chips → creator/tag feed (player closes — feed behind swaps),
  quick-sheet "Open @user's feed" row; TV parity via the TvQuickActions row.
- Tags parity: `GET /v2/tags/trending` wired as a "Trending tags" section in the
  search empty state (endpoint was live-verified 2026-10-02; new call = 1 request on
  search open, rate-limit-safe; silent degradation to an absent section).
- PIN pad: keypress KeyboardTap haptic + LongPress haptic on wrong PIN.
Deliberately NOT built this batch (still parked, with reasons):
- Picker-dialog family merge (#4) — needs the on-device giffyFocus ring check the doc
  itself demands before deciding how much to share.
- List-fetch state-machine VM convergence (#3) — Explore's failure path now works;
  idiom-consistency conversion is optional churn until a shared skeleton is wanted.
- AuthSection @username — blocked on a verified "who am I" source (no stored username,
  v2/user_profile 404'd; not guessing a JWT claim).
- TV parity gaps (Search/Groups/custom-feed mgmt/Collections screens) and the TV
  chip-skip traversal — D-pad device work, must be built with emulator verification.
- UX-PATTERNS candidates (hold-for-speed, minimised player, TV preview-on-focus) —
  still user-slice gated per that doc's graduation rule.
