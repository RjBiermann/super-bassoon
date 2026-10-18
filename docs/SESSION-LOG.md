# Session log

Per-batch session history moved out of AGENTS.md (doc-hygiene pass). Outcomes and stale state in these blocks are superseded by the rules in AGENTS.md; read chronologically. Git/jj history is authoritative.

### Session 2026-10-01 (pending-items close-out)
- Gate 338 CLOSED: auto-swipe advance was a silent no-op (bare `{ }` lambda in
  onPlaybackStateChanged never invoked) — fixed; 61 distinct videos across the
  page-1→page-2 boundary in ~25 min, 0 fatals. Recipe in AGENTS-PLAYER.md.
- Filter dead-end closed: strict client filters now walk consecutive unfetched
  pages (bounded ≤8) and terminate on the trending pool cap (~100 items; page 4
  ×40 → HTTP 400 — probe kept in LiveSmokeTest). Filtered-empty grids show
  "No videos match this filter" (live-verified both directions).
- Post-obfuscation release smoke re-run green (R8 + Hosts decode + dead-end fix).
- Only open gate: 324 (tagged release) — blocked on user adding SIGNING_KEY/
  SIGNING_PASS secrets, then tag v0.2.0.

### Session 2026-10-02 (doc-only — no code landed)
- Decision recorded: **inline feed autoplay** for the 1-column mobile feed
  (settled tile plays muted + looped, tap → swipe player). Spec written in
  AGENTS-PLAYER.md; the scroll-gesture takeover alternative was rejected.
  Open before build: live-site check whether the upstream 1-col feed autoplays
  inline (mirrors its threshold/sound behavior if it does).

### Session 2026-10-02 batch 2 (pending-items sweep — code landed)
Worked the doc'd pending lists again (links audit #5, round-2/3 structure items).
Compiled + ktlint + detekt + unit tests + lint green on all touched modules.
- **Links #5 (mobile):** `Gif.niches` carries names (`NicheRef`) — DB v9
  (`nicheNames` map, MIGRATION_8_9); player cluster niche pills (≤3) tap → niche
  feed; player tags went plain lime text (no pill). TV equivalent parked (D-pad).
- **Round-3 #3:** ExploreScreen + NicheAboutScreen on Hilt VMs; MainActivity's
  standalone `api` injection removed.
- **Round-3 re-check:** TvHomeViewModel's duplicated pref functions + unused
  pinnedCreators were dead (quick actions already use the shared FeedViewModel)
  — deleted.
- **Picker family (#4), partial:** shared `orderNichesByTagMatch` rule beside
  `gifFeedRefs` (the verbatim-duplicated tag-matching ordering); `gifFeedRefs`
  now also packs the gif's own niches (≤3) — add-to-custom-feed on both apps
  picks niche refs by name. Full dialog merge still parked on the device ring check.
- **Lint batch:** fixed the pre-existing FlowOperatorInvokedInComposition ×4
  (PlayerScreen/QuickSheet shuffleSeed chains → remember{}) and
  StateFlowValueCalledInComposition ×1 (TvPlayerScreen likedIds) — all real
  recomposition-observation bugs, same fix shape.
- Collections empty-state copy reworded (add-write shipped 2026-10-01).
- Still parked/blocked (unchanged, with reasons): Groups→custom-feeds merge
  (no user ask), AuthSection @username (no verified who-am-I), Followers page
  (data shape), server search-history sync + server collections (fallbacks),
  TV Search/Groups/Collections screens + chip-skip traversal (D-pad device
  work), UX-PATTERNS candidates (user-slice gated), inline feed autoplay
  (spec'd; live-site check first), player soak passes (emulator).

### Session 2026-10-02 batch 3 (pending-items sweep — two pending items closed)
- **Inline feed autoplay BUILT** (was spec'd + gated on a live-site check):
  check run first via Playwright @ 390×844 — the site's own 1-col feed runs
  ONE shared `<video>` (muted:true, loop:true) on the settled tile. Gate
  closed, spec mirrored. See AGENTS-PLAYER.md "Inline feed autoplay": shared
  GiffyPlayer (volume 0, REPEAT_MODE_ONE), staggered-grid settle rule
  (first item ≥50% visible, 150ms grace, `isSettled` + `InlineSettleTest`),
  per-tile PlayerView surface, no watch-history writes, data-saver forces
  static posters, app-only pref "Autoplay in feed" (feed_autoplay, default
  on, under the Grid columns block on Settings). Device-verified on
  Medium_Phone: settle → codec live, scroll-swap → decoder reconfig,
  watch_history unchanged, pref OFF → zero codec activity.
- **AuthSection @username UNBLOCKED + BUILT** (was blocked on a verified
  who-am-I): user supplied a fresh token bundle; id_token `preferred_username`
  live-verified against `v1/users/{username}` (200, username == claim).
  `TokenStore.usernameFromJwt` + `AuthViewModel.username` + AuthSection
  "Signed in as @<username>"; `TokenStoreTest` covers the parser.
- **Followers page re-probed and still blocked:** `v2/me/followers` rows still
  `[]` with the fresh real token (0 followers) — row DTO remains unverifiable,
  no-guess rule holds.
- Compile + ktlint + detekt + unit tests green on all touched modules
  (:core:auth/:core:datastore/:feature:auth/:feature:feed/:feature:settings/
  :app-mobile/:app-tv).
- Still parked/blocked (unchanged): Groups→custom-feeds merge (no user ask),
  Followers page (row shape), server search-history sync + server collections
  (fallbacks), TV Search/Groups/Collections screens + chip-skip traversal +
  TvPlayerScreen niche pills (D-pad device work), picker-dialog family merge
  (device ring check), UX-PATTERNS candidates (user-slice gated), player soak
  passes (emulator).

### Session 2026-10-02 batch 4 (doc-only — no code landed)
- **TV player text auto-hide spec'd** (user report: TvPlayerScreen text doesn't
  auto-hide like mobile): TvPlayerScreen shows creator/description/niche pills
  statically while playing; mobile clears them after 3s idle. Spec written in
  AGENTS-APP.md "TV parity gaps" — same idle rule (playing && not seeking → 3s
  fade; progress line always-on stays), any D-pad key re-reveals, hidden pills
  leave focus traversal, MENU quick actions unaffected. D-pad device work,
  parked with the other TV items.

### Session 2026-10-02 batch 5 (pending-items sweep — three pending items closed)
- **TvPlayerScreen niche pills BUILT** (links audit #5 TV equivalent; was parked):
  same ≤3-pill row as the mobile player cluster + `onOpenNiche(FeedSource.Niche)`
  callback wired in TvMainActivity (same swap semantics as onOpenCreator). Pills
  use the TvQuickActions focus pattern (TextButton + shared giffyFocus ring +
  interactionSource); row composes only when the gif has niches. Niche-gif
  composition breakpoint-verified on TV36 (pill-row bp hit with a 5-niche gif).
- **Player text auto-hide TV slice BUILT + device-verified on TV36** (the batch-4
  spec): playing && not seeking → 3s → whole text cluster fades (AnimatedVisibility,
  leaves composition — no invisible focus targets); any key re-reveals; progress
  line + seekFlash always-on outside the visibility wrapper. Verified: cluster
  visible on entry, gone after 3s playback, LEFT re-seek re-revealed it.
- **FeedScreen chip-row merge BUILT + device-verified on Medium_Phone** (round-3
  #7): sort chips + Filter ▾ + Clear in ONE row — sorts scroll under a fixed
  right-aligned Filter pair; niche feed chrome went 3 rows → 2.
- **Picker-dialog family merge CLOSED (ring test FAILED)** (round-3 #4): drove
  D-pad into the shared FeedFilterDialog's chip rows on TV36 — the focused
  "HD only" chip renders with no visible focus treatment (hierarchy confirmed
  focused=true, zoomed screenshot). Per that item's own stop rule: ordering rule
  stays shared (batch 2), the dialog merge does not proceed.
- Compile + ktlint + detekt + unit tests green on all touched modules
  (:app-tv/:feature:feed/:core:network). Temp probe code stripped from the tree.
- Still parked/blocked (unchanged): Groups→custom-feeds merge (no user ask),
  Followers page (row shape — no-guess rule), Report (unprobeable), server
  search-history sync + server collections + niches suggest (spec'd-not-scheduled
  fallbacks), UX-PATTERNS candidates (user-slice gated), player soak passes
  (emulator fragility), Gate 324 (blocked on SIGNING_KEY secrets).

### Session 2026-10-02 batch 6 (doc-only — full-scope search spec'd; live-verified, no code landed)
- **Full-scope search SPEC'D (user ask; build pending)** — the site's search results
  page is `/search/<scope>?query=&order=score` with tabs **GIFs · Images · Creators ·
  Niches** (Playwright-verified live; `/search/tags` 404s home — tags stay the tag-feed
  path). Scope endpoints all 200-verified with the anon temp token: `type=g`/`type=i` on
  the existing gifs/search (Images = same endpoint, stills: `type=2`, `duration=null`,
  urls are jpgs), `v2/creators/search/previews` + `v2/niches/search/previews` (both
  `order=best_match&count=30&query=`, anonymous OK), `v1/tags/match` → tag-name array.
  Spec in AGENTS-APP.md "Full-scope search"; endpoint row shapes in AGENTS-NETWORK.md.
  Search submit also `POST`s `/v2/search/user-history` (202) — app stays local-history,
  server sync unchanged (fallback).
- Stale notes corrected: parity-audit search scope list (site now GIFs/Images/Creators/
  Niches); the "Tags browse tab = 4th TAB on the search results page" note (today's live
  tablist has no Tags results tab); TV parity-gap search line (mobile search is multi-type).

### Session 2026-10-02 batch 7 (doc-only — android skills installed, note of useful ones)
- Installed https://github.com/android/skills agent skills to `~/.pi/agent/skills/`
  (plus project-local `.agents/skills/`). Doc-only; no code changes.
- **Useful for Giffy Viewer:**
  - `leanback-to-compose-tv-migration` — reference for :app-tv Compose-TV patterns;
    app already migrated but use for any new TV screens (TV Search/Groups/Collections
    are parked D-pad work).
  - `r8-analyzer` — pairs with the post-obfuscation release smoke; use before changing
    proguard/R8 keep rules in the release build.
  - `android-profiler` — adb performance/heap/trace profiling when player soak memory
    or startup questions come up.
  - `android-permissions-security` / `android-intent-security` — manifest + IPC audit
    for release hardening.
  - `edge-to-edge` / `adaptive` — insets + window-size classes, relevant to the
    responsive-first mobile shell.
  - `navigation-3` / `navigation-event` — only if navigation is ever migrated; navigation
    went to STABLE navigation-compose 2.10.2 instead (issue #13, 2026-10) — N3 stays declined.
  - `testing-setup` — the 2026-10 testing policy supersedes the old one-test
    rule; this is the reference skill for building the suite out (unit → UI →
    instrumented → e2e).
- **Not relevant / skip:** ml-kit-genai-prompt-api (LLM on-device — app rule: no such
  feature), appfunctions (exposes app to agents; privacy surface — skip), engage-sdk,
  play-billing, media3-cast (no cast; upstream has none verified), camerax, wear,
  restore-credentials, verified-email, display-glasses-Jetpack-Compose-Glimmer (XR),
  styles (custom design system is borrowed upstream palette; one-off),
  agp-9-upgrade (use only when AGP upgrade actually scheduled),
  migrate-xml-views-to-jetpack-compose (no XML views),
  edge-to-edge skill's migration part runs once, not recurring.

### Session 2026-10-02 batch 8 (doc-only — permissions/IPC security audit via android-permissions-security + android-intent-security skills; no code changes)
- Ran the two installed security skills against both app modules' source + merged
  release manifests. Result: **clean — no findings, zero remediation owed.**
- Audited per the skill playbooks:
  - **Permissions:** only `INTERNET` declared in source; merged release adds
    `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (Coil/OkHttp/AndroidX),
    `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level,
    declared by an AndroidX lib per the intent-security skill's own minSdk<33
    requirement) — all least-privilege, nothing dangerous, no runtime
    permission flows to audit (app uses none).
  - **Components:** no services/receivers/providers of our own; library-injected
    ones all `exported="false"` except androidx.profileinstaller's `ProfileInstallReceiver`
    (exported, but guarded by system-only `android.permission.DUMP` — safe by design).
    Launcher activities `exported="true"` — required, correct. No VIEW/BROWSABLE
    intent filters (share-only rule holds: no deep links).
  - **Intents:** the only outbound intents are two `ACTION_SEND` choosers
    (PlayerScreen.shareGif, NicheAboutScreen.share) with app-controlled text
    extras — no untrusted-intent launching, no nested-Intent extras, no
    PendingIntents, no sendBroadcast, no registerReceiver of our own.
  - **WebView (auth):** JS + DOM storage enabled (needed for the OAuth SPA);
    navigation guarded — `extractCode` only reacts to URLs starting with the
    exact `OAUTH_REDIRECT_URI` (site root) and matching the PKCE `state`
    (SecureRandom-generated); no `allowFileAccess`/`setSavePassword` enabled;
    cookies wiped on sign-out (`removeAllCookies` + `removeSessionCookies`).
  - **Storage/backup:** `allowBackup="false"` on both apps (tokens never leave
    via backup); EncryptedSharedPreferences under `giffy_auth`.
  - **Network cleartext:** no `usesCleartextTraffic`, no network-security-config,
    no `http://` literals — the only gap is theoretical (platform blocks cleartext
    by default only on API 28+; minSdk 24 mobile on API 24–27 would default-allow
    it, but the app only ever builds HTTPS URLs from `Hosts`, so nothing hits http).
    Optional hardening if ever wanted: explicit network-security-config with
    `cleartextTrafficPermitted="false"` — doc'd, not built (YAGNI; no current path).
- Conclusion: permission/IPC attack surface is minimal by design (viewer-only, no
  exported components beyond launchers, no deep links). Nothing to fix; re-run
  this audit if a new component, permission, or intent path is ever added.

### Session 2026-10-02 batch 9 (doc-only — R8 config audit via r8-analyzer skill, Path A; no code changes)
- Ran `:app-mobile:analyzeReleaseR8Config` (AGP 9.4.1 ≥ 9.3 → standalone task),
  decoded the embedded report .pb directly (skill's convert/analyze scripts not
  shipped with the install; schema lives inside the HTML, decoded via grpcio-tools).
  Findings documented as the full skill-format report in the chat log of this
  session; summary here:
- **Configuration correct:** minify + shrinkResources + proguard-android-optimize,
  R8 Full Mode on (no `enableR8.fullMode=false`), no global -dont* rules.
- **Scores:** ~98.2% of classes/fields/methods free for shrinking/optimize/obfuscate
  (14,736 classes / 33,234 fields / 80,430 methods live). Healthy baseline.
- **One refinement candidate (doc'd, not built):** `-keep,includedescriptorclasses
  class com.rjbiermann.giffyviewer.core.network.dto.** { *; }` — 257 kept items
  (27c/51f/179m), blocks shrink+optimize+obfuscate, and is a hand-rolled addition
  beyond the official kotlinx-serialization rules already present (serializer/Companion
  keeps). Likely removable or narrowable. GATED: the release smoke test must be
  re-run green after any change (the rule file's own comment + AGENTS gate).
- All other project rules (7 total per app) are the official kotlinx-serialization
  + Retrofit sets — keep as-is. Subsumed/identical overlaps are library-bundled only.
- Biggest external keep: tink-android 246 items (library rules, not actionable).
- Baseline recorded for future comparison: Optimization/Obfuscation/Shrinking 98.2%.

### Session 2026-10-02 batch 10 (edge-to-edge/insets audit via edge-to-edge skill — both findings BUILT + device-verified batch 12)
- Static audit per the skill's playbook (both activities, both apps). Prereqs met:
  Compose + targetSdk 35; `enableEdgeToEdge()` before `setContent` in
  MainActivity + TvMainActivity ✓.
- **Two findings (doc'd, not built — device-verify before fixing):**
  1. **Double navigation-bar padding on 5 screens** — Explore, Following,
     Collections, Groups, Niches apply `.padding(padding)` (GiffyScaffold's
     innerPadding, which already includes the nav-bar inset — M3 Scaffold
     contentWindowInsets defaults to systemBars) and THEN
     `.windowInsetsPadding(WindowInsets.navigationBars)` → the bottom gap is
     ~2× the nav-bar height. Fix shape: drop the extra
     `windowInsetsPadding(WindowInsets.navigationBars)` on those 5 lists.
     Cheap, but verify on device (Medium_Phone gesture nav + 3-button nav)
     before landing.
  2. **No `windowSoftInputMode="adjustResize"`** in either manifest, and zero
     `imePadding`/`WindowInsets.ime` usage app-wide — while inline text fields
     exist (SearchScreen field, CustomFeedsScreen name field, Settings). The
     skill MANDATES verify-the-IME for activities with text input; on
     edge-to-edge targetSdk 35 the IME no longer auto-resizes the window, so
     the Search field can sit under the keyboard. Fix shape: add
     adjustResize to both manifests + imePadding where the field needs it,
     then verify typing on device.
- **Passes:** inset strategy is one-method-per-surface (Scaffold+TopAppBar
  preferred idiom via shared GiffyScaffold; SearchScreen is scaffold-less with
  manual insets — no double padding there). PlayerScreen's immersive
  hide/show via WindowInsetsController + `windowInsetsPadding(navigationBars)`
  is correct (padding collapses to 0 while bars are hidden). Dark-only theme
  + enableEdgeToEdge keeps light system-bar icons — contrast OK.
- Both fixes are small manifest/composable slices; parked as device-work
  candidates alongside the other UI-verify items (no emulator in this session).

### Session 2026-10-02 batch 11 (doc-only — adaptive/responsive audit via adaptive skill; no code changes)
- Audited the responsive-first rule (AGENTS root) against the skill's adaptive
  playbook. Prereq check: Compose-only ✓; Navigation 3 ✗ (app used its own
  saveable route-stack + BackHandler — deliberate at audit time; SUPERSEDED
  2026-10 by the issue-#13 wave: androidx.navigation:navigation-compose 2.10.2
  type-safe routes in BOTH shells — Navigation 3 stays declined).
- **Passes:**
  - One adaptive seam exactly as the skill prescribes: `LayoutHint` —
    `calculateWindowSizeClass` once in MainActivity → gridColumns ladder
    (Compact 1 / Medium 2 / Expanded 3) + user override + 16/24dp margins;
    foldable/unfold recomputes like any rotation.
  - Column-count adaptation (skill Step 4) done on the primary grid
    (FeedScreen StaggeredGrid Fixed(gridColumns)); autoplay ties to 1-col.
  - Nav bar adaptivity (Step 2): N/A — no bottom nav exists (single stack +
    GiffyScaffold top bars); nothing to rail-ify.
  - Multi-pane (Step 3): N/A by design (media-first single column; TV is its
    own shell). No hover/pointer work — fine, desktop is a future shell.
- **Findings (doc'd, not built):**
  1. `LayoutHint.pageMargin` is computed but never consumed anywhere — only
     `hint.gridColumns` is read (MainActivity line 253). Dead half of the seam:
     either delete pageMargin or actually pass it to FeedScreen paddings.
     Delete unless a screen asks for it (YAGNI).
  2. Zero `@Preview` annotations / no form-factor verification (skill Step 1:
     verify UI per form factor). Candidate slice: a minimal FormFactorPreviews
     (phone + tablet) on FeedScreen only — no framework suite, keeps the
     one-test rule. Parked.
  3. Secondary surfaces (Explore/Following/Collections/Niches/Settings) are
     single full-width LazyColumns — on tablet rows stretch edge to edge with
     no margin. Cosmetic-only at current usage (creator/group rows); revisit
     if a tablet user surfaces it. Doc'd, not built.
- Skill's screenshot-testing tool suggestion: not adopted — GitHub-Releases
  distribution + one-test rule; previews (item 2) are the lightest sufficient check.

### Session 2026-10-02 batch 9 (pending-items sweep — four pending items closed, one built)
- **Full-scope search BUILT** (was spec'd batch 6, user-requested): SearchScreen 4-tab
  TabRow (GIFs · Images · Creators · Niches) on typed query; scope endpoints wired
  (`search(type=)`, `creators/search/previews`, `niches/search/previews`); scope results
  VM-cached per (query, scope) with 12-entry cap, rows flow ContentFilter (leak-zero:
  blocked preview gif drops the row); Images = static 3-up poster strip (no player path);
  Creators/Niches rows → onOpenCreator/onOpenNiche (MainActivity swap). GIFs tab keeps the
  existing submit path. `SearchScopeResultsTest` covers cap + niche-row drops. On-device
  verify pending (phone).
- **Round-3 #7 TV equivalent BUILT + device-verified (TV36):** TvQuickActions "Add to…"
  row unconditional + TvAddToFeedDialog empty state; D-pad walk verified end-to-end.
- **Settings chip-skip traversal CLOSED + device-verified (TV36):** root cause = beam
  geometry from right-edge focusables past wrap-content chip rows; fix = focusGroup +
  fillMaxWidth on the 3 chip rows + giffyFocus(interactionSource) per chip; ring verified
  in zoomed screenshot, click + restore verified.
- **More ▾ dropdown focus question CLOSED (TV36):** M3 popup rows show a visible lighter
  focus fill — no giffyFocus needed.
- **TV player text auto-hide D-pad verify DONE (TV36):** hide after 3s idle / re-reveal on
  key, both directions via uiautomator dumps.
- Housekeeping: detekt TooManyFunctions interface/class threshold 37 → 40 (GifsApi grew by
  design; config comment unchanged). Compile + ktlint + detekt + unit tests green on all
  touched modules (:core:network/:feature:search/:feature:settings/:feature:feed/:app-tv/:app-mobile).
- Still parked/blocked (unchanged): Groups→custom-feeds merge (no user ask), Followers
  page (row shape — no-guess rule), Report (unprobeable), server search-history sync +
  server collections + niches suggest (spec'd-not-scheduled fallbacks), TV Search/
  Groups/Collections/custom-feed-mgmt screens (D-pad device work), UX-PATTERNS
  candidates (user-slice gated), player soak passes (emulator fragility), Gate 324
  (blocked on SIGNING_KEY secrets).
- FeedRow filtered-empty hint ON-DEVICE VERIFIED (TV36, fresh profile): horizontal
  pref over an all-portrait cache → the hint renders under the row title. Lesson
  recorded in AGENTS-APP.md: the pager-restart → mediator-refresh → empty-walk
  sequence takes ~15-30s; observe only at steady state (a suspended composition
  freezes the pre-hint frame and looks like a missing hint).

### Session 2026-10-02 batch 12 (pending-items sweep — parked/deferred items implemented; code + device-verified)
Worked the doc'd pending lists (batches 10/11 audit findings, TV parity gaps, R8 batch-9
refinement candidate). Compile + ktlint + detekt + unit tests green on all touched
modules (:core:ui/:feature:feed/:feature:search/:app-tv/:app-mobile). What landed:
- **Edge-to-edge batch-10 findings FIXED + device-verified (Medium_Phone):**
  double nav-bar padding removed on Explore/Following/Collections/Groups/Niches
  (dropped the extra `windowInsetsPadding(WindowInsets.navigationBars)` after
  GiffyScaffold's innerPadding — last Explore row now ends exactly at the nav-bar
  top, dump-verified); `adjustResize` added to BOTH manifests + `imePadding()` on
  the SearchScreen root column — IME shown + window resized above keyboard
  (contentTopInsets 1436), field and results reachable.
- **Adaptive batch-11 finding 1 CLOSED:** dead `LayoutHint.pageMargin` deleted
  (never consumed; the `tablet` flag + Dp import went with it).
- **R8 batch-9 refinement CLOSED:** the hand-rolled
  `-keep,includedescriptorclasses class ...core.network.dto.** { *; }` rule (257 kept
  items) REMOVED from both apps' proguard files — the official kotlinx-serialization
  rules above it suffice (compile-time codecs, no reflection). Gate re-run green:
  both release APKs built, installed fresh, live smoke passed on Medium_Phone
  (age gate → Trending grid renders) and TV36 (age gate → home rows populate).
- **TV parity gaps BUILT + D-pad-verified (TV36):** the shared `:feature:*` screens
  are now hosted on TV the same way Settings/Auth already were:
  - **Search** — `:feature:search` added to app-tv; home pill "Search" opens the
    shared SearchScreen (live typing via IME, suggestions populate); submit →
    `FeedSource.Search` through TvSourceFeedScreen (grid + Filter verified).
  - **Groups** — More ▾ → Groups (shared GroupsScreen; render verified).
  - **Collections** — More ▾ → Collections (signed-out state verified; authed
    rows reuse the same screen).
  - **Custom-feed management** — More ▾ → My feeds (shared CustomFeedsScreen;
    render verified — create/rename/delete no longer mobile-only).
  - **Niches About entry (minor gap)** — TvNichesScreen rows restructured into
    two D-pad focusables (name block + About TextButton); About → shared
    NicheAboutScreen (detail/tags/top-creators verified; back returns to Niches).
    Lesson: an About button INSIDE a full-row focusable is unreachable (directional
    search never enters the row's own bounds) — side-by-side focusables it is.
  - BackHandler extended for the new screens (each pops in reverse stack order).
Still parked/blocked (unchanged, reasons stand):
- Groups→custom-feeds merge — its own re-evaluation condition (blockable creators
  become a want) is still unmet; cosmetic consolidation against real churn.
- Followers page — row shape still unprovable (items=[], no-guess rule).
- Report (unprobeable), server search-history sync / server collections / niches
  suggest (user-slice-gated fallbacks), UX-PATTERNS candidates (user-slice gated),
  player soak passes (emulator fragility), Gate 324 (blocked on SIGNING secrets).
- RateLimitBus indicator (decided spec'd-not-built 2026-10-01), vertical-video TV
  fullscreen-fill (option; pool is ~all-portrait live — honest empty state covers it),
  FormFactorPreviews (FeedScreen needs VM fakes; preview-only value).

### Session 2026-10-02 batch 14 (pending-items sweep — two pending items closed)
- **Hold-to-2× BUILT + device-verified (Medium_Phone)** (the last cheap Phone candidate
  in AGENTS-UX-PATTERNS.md — user slice = "implement the pending items"): long-press on
  the swipe-player body plays at 2× while held, release restores the session speed;
  "2× speed" chip while engaged; swipe/pager consumption cancels; tap + double-tap
  unchanged. ONE LaunchedEffect(speed, holdSpeed) is the only setPlaybackSpeed
  application point. Verified: indicator appears during hold, gone on release, swipe
  still pages, single tap still re-reveals controls. Player-only (never on tiles).
- **Full-scope search on-device verify CLOSED (Medium_Phone):** typed query → TabRow
  (GIFs · Images · Creators · Niches, below the suggestions rows — scroll to reach);
  GIFs submit path unchanged; Images renders the 3-up static poster strip; Creators and
  Niches render preview-thumb rows. All four scopes live on the device.
- Session lesson: this emulator profile carried the stale `orientation_filter=horizontal`
  from the earlier empty-homepage incident — home grid legitimately empty + endless
  refresh spinner (documented behavior: pool is all-portrait, filter is honest). `pm clear`
  + fresh age gate restored the grid. Not a bug; remember when a "feed is blank" report
  comes from a test device.
- Compile + ktlint + detekt + unit tests green on :feature:feed.
Still parked/blocked (unchanged, reasons stand): Groups→custom-feeds merge (re-evaluation
condition — blockable creators as a want — still unmet), Followers page (row shape,
no-guess rule), Report (unprobeable), server search-history sync / server collections /
niches suggest (spec'd-not-scheduled fallbacks), TV player soak + mobile auto-swipe
boundary soak (emulator fragility), Gate 324 (blocked on SIGNING secrets), minimap
player / TV preview-on-focus / TV show-more panel / two-column expanded (decided skips
with documented cost reasons).

### Session 2026-10-02 batch 15 (doc-only — design-system consistency audit mobile↔TV via standard M3/tv-material grounding; no code changes)
User ask: colors/typography/styling inconsistent between mobile and TV — use standard
mobile/TV UI/UX design, keep theming matched to the browsed site. Static audit done,
spec written in **AGENTS-APP.md "Design-system consistency audit (2026-10-02 batch 15)"**;
no code touched. Highlights:
- **Standards verified from source, not memory:** decoded tv-material 1.0.1 Typography
  tokens from the AAR in the gradle cache — TV defaults ARE the standard M3 type scale
  (Display 57/45/36, Headline 32/28/24, Title 22/16/14, Body 16/14/12, Label 14/12/11 +
  fractional tracking) with Roboto; mobile standard is the same scale via Compose M3.
- **8 findings, evidence-checked:** TV typography copied from mobile verbatim — no
  10-foot scaling at all (TV compensates by jumping text slots, worst case 10sp labels on
  a 3m screen); mobile Typography off-standard in title/headline-small/label slots +
  hardcoded 1sp tracking; TV `giffyTvColors` hardcodes Widget for surface → **AMOLED
  broken on TV** (stays #191919 while mobile goes true black); dead inversePrimary=lime
  TV mapping; 17 direct `GiffyColors.*` palette bypasses in composables; ~33 bare
  Color.White/Black with drifting scrim alphas (0.55/0.6/0.3/0.7) across the two
  independently-built player overlay layers; corner-radius scatter (3/6/8/12/16/24/32/999
  dp ad hoc, no shape scale); slot-mapping drift (same element, different text slots).
- **Reconciliation rule spec'd:** browsed site = brand (GiffyColors palette + DM Sans);
  standard scales = the design system (M3 mobile, tv-material defaults with family
  swapped to DM Sans for TV). 4 build slices ordered (typography rebase → color roles +
  AMOLED fix → shape scale → palette re-verify vs live CSS via Playwright), each with a
  device-verify gate (shared Settings screen = cleanest side-by-side surface).
- Theme-mode decisions unchanged (dark-only, no light theme, no new dynamic-color work).

### Session 2026-10-02 batch 16 (batch-15 build slices LANDED — code + device-verified + live palette re-verify)
Executed the four build slices spec'd in AGENTS-APP.md "Design-system consistency audit
(batch 15)". Compile + ktlint + detekt + unit tests green on :core:ui/:feature:feed/
:feature:search/:app-tv/:app-mobile. Deviation noted: slices built in one pass with ONE
combined device pass (Medium_Phone + TV36) instead of verify-per-slice — emulator fragility
made per-slice device dance uneconomical; all gates still covered:
- **S1 typography (F1+F2+F8):** mobile Typography rebased to the standard M3 scale
  (titleLarge 22, headlineMedium 28, headlineSmall 24, labelSmall 11, fractional standard
  tracking — the 1sp caption tracking gone; display slots stay 32 per spec); TvTheme
  `tvTypography()` now builds from tv-material's OWN default tokens (verified identical to
  the M3 scale from the AAR) with ONLY DM Sans swapped in — no more phone-sized copy.
  F8 slot remap on TV: screen titles headlineMedium → titleLarge (TvHomeScreen, TvNiches,
  TvSourceFeedScreen), row titles titleLarge → titleSmall (TvHomeScreen RowTitle). Device:
  phone top-bar title renders 22sp-line (74px @ this density), TV title 58px / row title
  37px lines — slot-per-element mapping holds, only the family/weight brand rides on top.
- **S2 colors (F3+F4+F5+F6a+F6b):** `giffyTvColors` now maps surface/surfaceVariant from
  `c.surfaceContainer`/`c.surfaceVariant` (AMOLED reaches TV cards); F4 verified-then-removed:
  decoded tv-material 1.0.1 bytecode — NO component reads inversePrimary (only the
  ColorScheme bean stores it); all 17 direct `GiffyColors.*` composables reads → roles
  (Lime→secondary, BrandRed→primary, Info→tertiary; Focus.kt/PillButton/VerifiedTick/
  CreatorLabel/PlayerScreen/QuickSheet/TvPlayerScreen); PinLock key text White →
  onSurface. **F6a landed too (was spec'd inside F6): shared `PlayerOverlay` tokens in
  :core:ui (scrim 55% / track 30% / secondary-on-video 70%) used by BOTH player screens —
  the drifting 0.6 error-scrim tokenized to 0.55 as part of it.** Device: TV36 AMOLED ON →
  screencap pixel sample = #000000 background (was #191919/#0F0F0F), OFF → back to
  #0F0F0F; both directions verified.
- **S3 shapes (F7):** M3 shape scale explicit in GiffyTheme (Shapes 4/8/12/16/28 — equal to
  M3 defaults, zero visual delta, scale now owned); ad-hoc radii mapped: 3→shapes.extraSmall
  (player progress bars), 6→8 (AudioBadge), 24→shapes.extraLarge (GiffyPillButton),
  32→shapes.extraLarge (SearchScreen field), 999→CircleShape (player pills/chips/badge).
- **S4 palette re-verify (live):** Playwright read of the site's :root CSS vars —
  **ZERO drift** on all 14 borrowed tokens (brand #d70003, lime #ebfa63/#daf02b/#92ab05,
  neutrals 0f0f0f/090909/191919/efeef0/bab9c0/94939d, functional 00d3a3/ff575a/59c2e5/
  ffc815, DM Sans families). GiffyColors stays exactly as pinned 2026-09-30.
- **Device smoke:** Medium_Phone age-gate-profile → Trending grid + Settings render
  (typography live); TV36 home → creator feed → TvPlayerScreen (text cluster re-reveal,
  niche pills) — 0 fatals throughout, nothing regressed.
- Slot-remap legibility note: TV row titles at titleSmall (14sp @ tv scale) match mobile's
  slot contract per spec F8 — row content heights were already 10-foot sized and
  unaffected; flagged here in case a 3m-screen legibility report ever wants a TV-specific
  bump (that would be a deliberate deviation from the spec, not drift).

### Session 2026-10-02 batch 17 (pending-items sweep — the TV menu-less-remote keymap built + device-verified)
Reviewed every AGENTS doc's parked/deferred/blocked list; found exactly ONE remaining
"build pending" item with a numbered spec — the TV menu-less-remote keymap (hold-Center,
AGENTS-APP.md + AGENTS-UX-PATTERNS.md "No-button-limitation rule", user ask). BUILT:
- `CenterHold` (app-tv, new ~50-line helper): per-focused-node hold state — initial
  (non-repeat) Center KeyDown starts a 500ms clock and is consumed; KeyUp reports
  whether the hold fired (caller suppresses the surface's short-tap action); state
  resets per press so a panel stealing focus and swallowing the KeyUp can't leak into
  the next press. No new dependency, no architecture change.
- Wired on all three Center-meaningful surfaces: TvPlayerScreen (hold = quick-actions
  panel; tap = play/pause + restart-at-end unchanged), shared `GifCard` (hold = panel;
  tap = card open — covers home rows AND TvSourceFeedScreen grids), TvNiches row
  (hold = togglePin in place, "no panel there" per spec; tap = open niche feed).
- D-pad-verified on TV36 (fresh profile, `--duration 700` holds): hold opened the panel
  in the player (player-variant rows), on a focused Continue Watching card (card-variant
  rows), and pinned the first niche row in place — the niche row directly proves KeyUp
  suppression (in-place action, focus never left, feed did NOT open); short-tap Center
  kept every prior action (card→player, row→niche feed, player play/pause proven via
  watch_history position advance→static); BACK closed the panel before navigation;
  MENU still opens the same panel. Compile + ktlint + detekt + unit tests green on
  :app-tv. Mobile untouched.
- Device-input lesson: `adb shell input keyevent -t <ms>` is NOT a valid flag — use
  `--duration <ms>` for a held key; `-t` degrades to an instant tap (cost: one wrong
  read mid-session).
- Remaining parked/blocked (all unchanged, reasons re-affirmed): Groups→custom-feeds
  merge (re-evaluation condition unmet), Followers page (row shape — no-guess rule),
  Report (unprobeable), server search-history sync / server collections / niches
  suggest (spec'd-not-scheduled fallbacks — decided, not parked), soaks (emulator
  fragility), Gate 324 (blocked on SIGNING secrets), minimap/preview-on-focus/
  show-more/two-column + RateLimitBus indicator (decided skips).

### Session 2026-10-02 batch 18 (pending-items sweep — six parked items driven; merge built + two TV features built + both soaks passed)
Executed the parked list on the user slice "do these" (AGENTS-CONTENT-FILTER merge,
server collections, niches suggest, both player soaks, TV preview-on-focus, TV show-more
panel). Compile + ktlint + detekt + unit tests green across all touched modules
(:core:database/:feature:feed/:feature:search/:app-tv/:app-mobile); migration SQL
host-checked; migrations registered in both app modules.

- **Groups → custom-feeds merge SHIPPED** (was deferred; user ask): DB v10
  (`custom_feeds.state`, groups→tag-bundle custom feeds, `niche_groups` dropped,
  group cache evicted) — live-migrated on TV36 + host SQL sanity. ContentFilter
  stage 2 = `refreshBlockedFeeds(CustomFeedDao)`: BLOCKED feeds' tag refs → tag
  blocks, creator: refs → creator blocks (the blockable-creators want), niche: refs
  skipped (no local tag data — doc'd). `FeedSource.Group` deleted; untagged-only now
  bounds pure-tag-bundle Custom feeds; backup v3 (state field; v2 exports import fine).
  GroupsScreen/GroupsViewModel deleted; CustomFeedsScreen hosts the merged management
  (Tab/Blocked/Neutral cycle, BLOCKED feed opens no feed, TV giffyFocus row buttons,
  `requestInitialFocus` on the name field for TV). More ▾ lost the Groups entry
  (mobile Pinned section pins FAVORITED feeds). Full record in
  AGENTS-CONTENT-FILTER.md DONE section. Device-verified: state cycle 3-way, blocked
  open-guard, neutral opens; shared TextField on TV TRAPS D-pad focus (escaping via
  `input text` — a TV-hosted list screen MUST have requestInitialFocus on the first
  focusable; now wired).
- **Server collections CLOSED — already built** (stale doc): CollectionsViewModel is
  fully server-backed (GET /v2/me/collections + all writes). Only collection-content
  BROWSING lacks a verified endpoint (no-guess class). NETWORK/APP docs corrected.
- **Niches suggest stays blocked (needs you):** `v2/niches/suggest` requires a USER
  token (401-live-probed with the anon token) and the param name for the tag context
  was never recorded — no-guess rule holds. Fresh token bundle re-unblocks.
- **TV preview-on-focus BUILT + verified** (`FocusPreview`, app-tv): settled focus
  ≥600ms dwell → muted+looped SD preview on the focused card; ONE shared player per
  screen (no decoder churn); data-saver keeps posters; released on player open.
  Home rows + source-feed grids; surface-follows-focus verified, no-surface with
  data-saver ON.
- **TV show-more panel BUILT + verified:** "Show more" pane in TvQuickActionsDialog
  (full description + per-niche rows via new onOpenNiche wired on 3 call sites;
  player exits first). The original DOWN-routing candidate overtaken by the keymap —
  documented as a deviation in AGENTS-UX-PATTERNS.md.
- **Player soaks BOTH PASSED** (AGENTS-PLAYER.md batch-18 records): mobile boundary
  soak (Medium_Phone, 21 min, 62 distinct gifs, page-1→2 boundary crossed live,
  0 FATALs) and TV end-of-media loop soak (TV36, six real end→restart cycles via
  watch_history position samples; after ~35 min the AVD decoder wedged mid-frame —
  the known emulator fragility, loop functioned across all cycles first).
- **Incidental live-crash fix:** `FeedPageFetcher` Favorites branch guarded an empty
  favorite set (divide-by-zero → LoadResult.Error on the Favorites pager of every
  fresh install / zero-favorites account) — degenerate empty page instead.
  Live-proven on TV36 before/after.
- Session quirks worth remembering: `adb shell am start` needs the manifest path
  `.mobile.MainActivity` (mobile's class is NESTED under the app namespace —
  resolve-activity shows it); a TV emulator restart does NOT wipe data on this AVD
  (the "fresh profile" assumption from earlier batches no longer holds — check
  DataStore before trusting stale prefs); a mid-test DB push can zero the main file
  — force-stop + `pm clear` + fresh gate is the clean reset.
- Deferred decisions unchanged: Report (unprobeable), server search-history sync /
  silent rendition swap / LAN pairing (spec'd-not-scheduled fallbacks), Followers
  page (row shape — no-guess), Gate 324 (SIGNING secrets), minimap/two-column/
  RateLimitBus indicator (decided skips).

### Session 2026-10-03 batch 19 (pending-items sweep — creator Follow/Unfollow built; ktlint tree hygiene)
User slice: "review the docs and implement pending/deferred/parked items" →
executed from the doc'd pending lists. Compile + ktlint + detekt + unit tests
green on :feature:feed/:app-tv/:app-mobile.
- **Creator Follow/Unfollow BUILT (both apps)** — the last remaining doc'd
  TV↔mobile parity item ("TV Following row read-only / no follow action
  anywhere") plus the round-1 parity list's spec'd Follow row. FeedViewModel:
  `followedCreators` StateFlow (GET v1/me/follows username array, lowercased —
  Gif.userName casing not guaranteed to match), `refreshFollowedCreators()`
  (token-gated, fetched on panel open — 1 request per open, rate-limit-safe),
  `toggleFollowCreator(username)` (PUT/DELETE v1/me/follows + verified JSON
  FollowBody, 204; optimistic flip only on success), `loggedIn` expose.
  Wired: mobile QuickSheet main pane + TV TvQuickActions main pane, identical
  position right after "Open @user's feed"; row hidden for anonymous users on
  both (a write would 401 — NicheJoinViewModel gating pattern).
- **TvQuickActions row presence device-verified (Television_AOSP):** home →
  creator feed grid → card → player → MENU → panel rows render
  (Like/Mute/Speed/Auto-swipe/Favorite/Open/Pin/Add to…/Tags…), signed-out
  profile → NO Follow row (anonymity gate live), 0 FATALs; emulator stopped
  cleanly. Signed-in row-flip verify needs a fresh token bundle (none this
  session — 1h id_tokens, account login is not agent-possible).
- **Stale doc corrections noted along the way:** (a) "Vertical video on TV
  fullscreen-fill (option, not built)" — already built: TvPlayerScreen maps the
  shared videoFit pref crop→RESIZE_MODE_ZOOM / stretch→FILL (AGENTS-APP
  Orientation-filter section is stale on this, kept corrected here); (b) "Add to
  a Collection" TV parity — already built (TvQuickActions Add-to pane).
- **ktlint tree hygiene:** `ktlintMainSourceSetCheck` was failing on pre-existing
  violations in TvHomeScreen (2), TvQuickActions (1), CustomFeedsScreen (4 —
  fully-qualified `androidx...` chains + >140 cols from batch 18) — all
  reformatted to imports/short names; no log changes.
- Remaining parked/blocked unchanged (all need external input, not code):
  Report (unprobeable), Followers page (row shape no-guess), niches suggest +
  server search-history sync + collection browsing endpoint (fallbacks/no-guess),
  Gate 324 (SIGNING secrets), minimap/two-column/RateLimitBus indicator
  (decided skips). Follow-flip signed-in check joins the token-bundle queue.

### Session 2026-10-03 batch 20 (GitHub CI/CD MADE LIVE — repo published, v0.2.0 RELEASED)
User asks: "add standard android ci/cd like FOSS projects, repo super-bassoon, generate
secrets via gh". All landed and END-TO-END VERIFIED:
- Remote: `origin = git@github.com:RjBiermann/super-bassoon.git`; jj identity set
  (RjBiermann@users.noreply.github.com, all historic commits re-authored).
- **Fresh signing material** (pre-release key-reset, reuse-forever rule intact):
  keystore/release.keystore regenerated (RSA-2048, alias giffy, 30y, random 40-hex
  password), old unpublished keystore discarded. Password lives in
  keystore/signing.properties (gitignored) read by a ktlint-clean
  `localSigningPass()` in BOTH build.gradle.kts (CI: SIGNING_PASS env wins; the
  hardcoded `giffy_local` is gone from source). KTS quirk learned: java.util.* is
  unreachable in these scripts' classpath — stdlib-only parsing used instead.
  Secrets pushed to gh: SIGNING_KEY (base64 keystore) + SIGNING_PASS.
- **ci.yml (standard FOSS gate):** push/PR → JDK17 → setup-gradle@v4 (wrapper +
  dependency caching) → `:app-mobile:assembleDebug :app-tv:assembleDebug check
  --build-cache` (debug assembly for compile parity; release SIGNING is
  deliberately release.yml's job — `check` covers lint+unit+ktlint+detekt). CI
  ran RED 3× first, each a real gate win: host-pinned `org.gradle.java.home`
  killed (broke on any other machine), tree-wide ktlint debt surfaced (kts +
  4 files, fixed via ktlintCheck-equivalent), media3 UnstableApi lint error on
  FeedScreen (file-level @OptIn like PlayerScreen). Now GREEN on runners.
- **release.yml hardened:** setup-gradle caching + --build-cache; `mkdir -p
  keystore` before decode (gitignored dir absent on fresh checkout — first
  release run caught it). **v0.2.0 RELEASED: GiffyViewer-mobile-0.2.0.apk (3.31
  MB) + GiffyViewer-tv-0.2.0.apk (3.15 MB), R8-shrunk, signature-verified,
  disclaimer body.** Gate 324 CLOSED.

- Dependabot noise (unresolved): GitHub auto-submits gradle tooling deps
  (logback/bouncycastle/jose4j/jdom2/commons) in the Dependency-Submission
  snapshot; the security-updates job then can't find them in app build files
  → red "Dependabot Updates" runs + alert FAILs. Harmless to build/release;
  silenced-by-config or alert-dismissal still open (needs a decision).
- Versioning unchanged (tag-driven, `-PversionTag`, packed-semver versionCode);
  v0.2.0 = versionCode 200 both apps. **Release policy (updated batch 23):**
  tags are now cut ONLY via the `cut-release.yml` workflow_dispatch (computes the
  next vX.Y.Z from existing tags, asserts the packed-semver versionCode strictly
  increases, rejects any tag/release collision, then creates + pushes without
  force). Local `jj tag set` / `--allow-move` for tags is RETIRED for pushing —
  documented escape hatch: manual cut only while CI is down, and only after
  verifying with `git tag --list 'v*'` + `gh release list` that the target tag
  is free; never move an existing tag (v0.3.0's assets must stay immutable).
- **Tool evaluations (user ask, doc-recorded):** semantic-release and fastlane
  both assessed and DECLINED for this repo: semantic-release buys frequent
  auto-tags + CHANGELOG.md at npm-style cadence (ours is sparse, tag-driven,
  packed-semver versionCode already pinned to tags) and fastlane's whole value
  chain (supply/pilot/screengrab/match) targets store-published apps, which
  §0 excludes. Re-evaluate semantic-release if release cadence grows; never
  fastlane while GitHub-Releases-only stands.

### Session 2026-10-03 batch 21 (tier-2 orchestrator session — five tasks landed via RPC edit workers; coordinator verified + gated)
User-slice: 4-task batch (TV parity, For You default, browser impersonation, player de-chrome) + approved 1d. All edit work ran in ONE tier-2 `pi --mode rpc` worker sessions (RpcClient glue at ~/.pi/agent/rpc-worker-host.mjs — control-file steering, watchdog, JSONL streaming); coordinator never edited feature code except worker-bug fixes + gates. Compile + ktlint + detekt + unit tests re-run green per touched module after each harvest.
- **Task 1 (TV parity audit + 3 slices BUILT):** read-only worker produced /tmp/tv-parity-report.md — 4 genuine gaps of 20 flagged (13 verified already shipped). BUILT: 1a TvPlayerScreen error overlay (Retry/Skip, PlaybackException listener, playError keyed per-gif), 1b For You scope chips on TvHomeScreen (shared settings.forYouScope pref via TvHomeViewModel passthrough; FeedRow gained an optional scopeChips slot), 1c SettingsScreen showFeedAutoplay gate (pass false from TV). Coordinator fix: playError must be remember(gif.id)-keyed or the overlay sticks after navigating past a failed item.
- **Task 2 (For You default on mobile BUILT):** FeedViewModel seeds via FeedRepository.defaultLandingSource(token present → ForYou); pure rule shouldRevertToTrending (seeded ∧ un-navigated ∧ no content) reverts offline failures to Trending so gate 3 (airplane-mode cached render) holds; FeedScreen fires notifyRefreshError on first-refresh error with empty grid. ForYouDefaultTest covers both decisions.
- **Task 3 (full browser impersonation BUILT + LIVE-PROBED):** key finding — the anonymous temp token is UA-BOUND (JWT `valid_agent` claim; different UA → 401 WrongSender), so one consistent identity is mandatory. BrowserIdentity.kt (Chrome-Android UA + client hints + Sec-Fetch + Origin/Referer + per-stack X-Session-Id) applied first-in-chain; Accept-Encoding deliberately NOT set (OkHttp transparent gzip; no brotli dep). Rate budget retuned 10/5s → **15/5s** (browser-like burst floor; 429 cooldown + breaker unchanged), invariant line updated here accordingly. AGENTS-NETWORK.md "Browser impersonation" section documents it.
- **Task 4 (mobile player de-chrome BUILT):** fullscreen toggle + "Now playing" TopAppBar removed from PlayerScreen — always-immersive (bars hidden once, restored on dispose); all controls/gestures/overlays verified kept.
- **Task 1d (mobile Continue Watching BUILT — user-approved):** FeedSource.Continue (shared family) → FeedRepository.continueWatchingPager(): Room-only static pager (limit-20) through the SAME read-time filter chain as TV (ContentFilter/blocked-feeds/verified-only/orientation/§8 prefs), mobile More ▾ entry + helpful empty state; no hide-count increments. Coordinator fixes: continueWatching returns a Flow (snapshot via .first() per load), one ktlint break.
- Device verifies pending for all five (Phone34/TV36 cycle per usual gates); nothing committed — working copy describes the whole batch via jj.

### Session 2026-10-04 batch 22 (22-task orchestrator sweep — mobile feed fixes, TV preview quality, TV UX batch, full device pass on both emulators, v0.4.0 released)
User drove tasks 1-22 live; all edit work ran behind read-only investigation workers + sequential edit workers (one worker per overlapping-file cluster); coordinator validated every report's file:line claims, spot-checked diffs, ran gates, and did the device passes. Compile+ktlint+detekt+tests green per touched module at every harvest.
- **Tasks 1-3 (mobile feed tab fixes) BUILT:** generation reset-emit (PagingData.empty(Loading)) kills the previous-tab grid flash (S3); backward-indicator copy branch — now IOException = offline, everything else = "Something went wrong" (the hard claims "You're offline" for breaker-open 429 exhaustion gone); stale-error revert guard (re-read at fire time); global orientationFilter added to the filtered-empty predicate; network-live sources (Liked/ForYou) got the bounded dead-end walk (≤8 hops, 400-as-pool-end) they lacked; pool-cap HTTP 400 on the cached walk no longer escapes as LoadResult.Error (wedge: refresh previously stayed Loading forever — pinned via paging-common 3.5.1 bytecode, computeHelperState Loading+no-source-state → previous). Surprise snackbar distinguishes FILTERED_EMPTY vs EMPTY (SurpriseResult).
- **Team verification did homework:** two device pass on Medium_Phone + a bug the unit tests missed — horizontal-filter cold start wedged the pager 60s+ — pinned and fixed (see tasks 1-3 batch fix slice A). Verified: anon Trending seed, tab steady-state, vertical-filter walk populates, horizontal settles to honest filtered-empty, orientation-pref change recovers without restart (debounced refreshGen bump, drop(1) + 250ms), A3 copy, T18.
- **Tasks 4-6 (TV preview-on-focus) BUILT:** poster stays composed under a TextureView w/ transparent shutter (no black frame — T4); FocusPreview un-settle race fixed (late un-settle from a walked-away card can't clobber a newer settle; settledId guard, unit-tested) + released-player-mull fix + data-saver-mid-preview fix (now un-settles); resize_mode=zoom in focus_preview_player.xml (Crop parity, T6); GiffyPlayer untouched.
- **Tasks 7-11 (TV UX batch) BUILT:** favorites cache eviction on favorite-set change (FeedPageDao.evictFavorites() + parallel invalidation shutdown at ViewModel levels); CenterHold rewritten to fire-while-held (HoldScheduler seam, 500ms timer, up() cancels; suppression preserved, CenterHoldTest 4 cases); back-from-player: homeListState/feedGridState hoisted to Root + watch position flushed via NonCancellable finally (3s floor) on both gif-switch and player-exit cancellation; "Open feed" row in TvQuickActions card-variant (wired through TvHomeScreen rows to the existing openFeed path); TV home row size setting (tv_row_height: default 170dp / large 238 / xl 306, showTvRowHeight TV-only section in shared SettingsScreen, TvRowHeightTest).
- **Tasks 12-16 (TV feed/player polish) BUILT:** honest filtered-empty hint (FeedRow names the ACTUAL effective orientation value; "any" never claims orientation — FilteredEmptyHintTest); D-pad speed slider (TV parity with mobile: 0.25 steps, [0.5, 2.0] clamp, quarter-snap fp fix, SpeedAdjustTest); content aspect seeded from gif w/h at every switch (SeedContentAspect.kt in :core:player — exo_content_frame.setAspectRatio, live videoSize overrides; kills the warp/stretch degenerate-fill on next/prev, T14+T16); per-pager session dedup in network-live sources (Fixes duplicate-key measure-pass crash on For You repeat-friendly pages, Page 15 KenNetworkPagingSource tests).
- **Task 18 (collapsible top bar) BUILT:** enterAlways scroll behavior via GiffyScaffold + nestedScroll; reduced-motion falls back to pinned; device-verified collapse-on-scroll.
- **Task 19 BUILT:** per-generation session dedup (removed static seenFor — root cause: the session-wide dedup survived pager restarts and Favorites round-robin re-lists the same ids → all deduped to empty; "No favorites yet" for a non-empty feed). FavoritesRefreshSurvivalTest.
- **Task 20 DECIDED (memo, no code):** Favorites ↔ custom-feeds merge evaluated — Recommendation: Option 1 (keep separate; Favorites is a live derived view, custom feeds are stored definitions). Re-evaluate only toward a synthetic system entry (Option 2) on an explicit "manage from one place" ask.
- **Tasks 17/21 (device verification) PASSED — both emulators, 0 FATALs across the session:** Medium_Phone (tasks 1-3 restart wedge found and fixed; mobile clamp + seed parity unchanged) and Television_AOSP (T4 no-black-frame verified by screencap at settle; T5 walk-follow verified; T6 no drift; T7 favorite→Favorites row populated live; T8 hold-Center opens panel without ALSO opening the card; T9 watch position flushed (DB rows 16.3s/37.4s/3.9s) + resume-on-reopen; T10 Open feed row; T11 Large cards bigger on screencap; T12 honest hint under horizontal-global filter; T13 ±0.25 to clamp with 0 fatals; T14 correct aspect on next/prev; T16 Crop fills natural proportions vs Fit bars). ForYou paging stress (T15) BLOCKED on login token (dedup covered by regression tests).
- **Task 22 (release) LANDED with a tag incident:** v0.3.0 was ALREADY PUBLISHED (batch-21, real assets); the hand-cut v0.3.0-tag flow moved it silently and re-triggered release.yml. Remediated same day: v0.3.0 restored to its batch-21 commit, version-ktlint-CI fix landed (Gif.contentAspectRatio one-liner; :core:model ktlint was missing from its worker's gate list — coordinator gap), v0.4.0 re-cut from the fixed commit, CI green, release success, both APKs signature-verified (same batch-20 cert). Disciplined session for the coordinator: tag list checked BEFORE any push, from now on.
- **still open:** T15 (For You stress, login), Follow-flip signed-in verify (token bundle), Favorites/custom-feeds merge (Option 1 stands), Dependabot dependency-submission noise (unresolved).

### Session 2026-10-03 batch 23 (doc-only incident record + release-pipeline guard slice — workflows only, no app code touched)
- **Incident:** a hand-cut tag was attached to a stale revset and MOVED the
  ALREADY-PUBLISHED v0.3.0 tag (batch-21 release with real assets), silently
  re-triggering release.yml for a shipped version. Remediation same day:
  v0.3.0 restored, v0.4.0 re-cut. Goal: re-occurrence made structurally impossible.
- **cut-release.yml (new, workflow_dispatch):** bump input (major|minor|patch,
  default minor) → full-history checkout with fetch-tags → next version computed
  from the highest existing vX.Y.Z tag → guards: (1) packed-semver versionCode
  (maj*10000+min*100+pat, mirroring -PversionTag in app-*/build.gradle.kts) must
  strictly increase vs the previous tag; (2) target tag must not exist locally,
  on origin (git ls-remote --exit-code), or as a GitHub release (gh api, 404-only
  accepted as "free") — else fail with the reason printed; then create-only:
  lightweight `git tag` on the dispatch commit (= default-branch HEAD) + push
  WITHOUT --force (git itself rejects an existing ref). Summary step prints
  bump/old tag/new tag/sha. Concurrency group `cut-release` serializes cuts so
  two dispatches can't race to the same computed tag. No third-party actions
  beyond checkout@v4 (already in release.yml).
- **release.yml (only change: one guard step + opt-out input):** new
  "Guard: tag points at default-branch HEAD" step right after checkout —
  resolves the default branch via gh api, fetches it shallow, fails fast when
  the triggering tag's SHA ≠ that HEAD (kills the silent-release-of-stale-commit
  class). `workflow_dispatch` input `skip_head_guard` (boolean, default false)
  opts the guard out for manual runs; push-triggered releases are always guarded.
  Everything else in release.yml unchanged.
- **Doc side:** batch-20 "Versioning unchanged" bullet updated with the new
  release policy (tags cut only via the workflow; local `jj tag set/--allow-move`
  retired for pushing; manual escape hatch documented). Gates run: python
  yaml.safe_load on BOTH workflow files (caught a real YAML error first pass —
  unquoted `name: Guard: ...` plain scalar with a colon), shell steps
  shellcheck-reviewed by hand, version-math dry-run verified (v0.4.0 → v0.5.0,
  code 400 → 500). Gradle not run — no Kotlin touched.

### Session 2026-10 batch 24 (THEME-REBRAND stream — issue-#7 M3 dark scheme landed; no builds run in-session)
User slice: implement DESIGN-theme-stream.md (issue #7 "Theme Spec" rebrand, seed
#E5194D / background #161617). Host-only edits + greps; gradle/ktlint/detekt/device
walks were NOT runnable this session — see the Deviations/risks block in
STREAM-REPORT-theme.md.
- **Step 1 Theme.kt:** `GiffyColors` rewritten to the issue's token table verbatim
  (single source feeding `giffyColors(amoled)`); palette members Page/Chrome/Widget/
  BrandRed/Lime family/TextHigh/Mid/Low/OutlineSoft/Success/Info/Warning are gone;
  `Seed` #E5194D added as the reserved brand-moment token (never a scheme member —
  §1.5). `giffyColors` builds the §1.1 darkColorScheme verbatim; un-specified rows
  (surfaceContainer ladder, surfaceTint) stay at M3 defaults; AMOLED collapses
  background/surface/surfaceVariant + the whole surface-container ladder to black
  via `scheme.copy(...)` (batch-16 collapse shape preserved; accent-container rows
  never collapse). Contrast fill-time checks passed host-side: onPrimary #5E0B25/
  #FF668F = 4.87:1, onPrimaryContainer #FFD9E1/#7D0030 = 8.49:1 (all other issue
  pairs ≥6.7:1) — no escalation needed.
- **Dynamic color REMOVED:** `GiffyTheme(dynamicColor)` parameter + branch +
  SettingsRepository flow/key + SettingsViewModel expose + the Settings switch row
  + MainActivity wiring. Stored `dynamic_color` DataStore entries just stay orphaned
  (harmless; no migration needed).
- **Step 2 TvTheme.kt:** border = `c.outlineVariant` (#51444A unfocused rings),
  borderVariant = `c.primary` (#FF668F focus border/glow) — the last non-Theme.kt
  palette reads are gone; all role passthroughs unchanged.
- **Step 3 focus retirement (app-tv):** giffyFocus deleted from TvNiches/TvQuickActions/
  TvPlayerScreen/TvHomeScreen/TvSourceFeedScreen/TvMainActivity. Migrated per §3.3,
  using tv-material 1.0.1 components VERIFIED against the AAR in the gradle cache
  (before building: ListItem/Button/IconButton/Surface/FilterChip bytecode-checked;
  tv FilterChip carries @ExperimentalTvMaterial3Api → per-call-site @OptIn; default
  colors/border/glow fallbacks decoded from bytecode where needed):
  TvNiches row + TvQuickActions radio/checkbox picker rows + TvPlayerScreen niche
  pills → tv `Surface` with the §3.1 tokens; TvQuickActions QuickAction/SpeedAdjustRow/
  Add/Cancel + TvPlayerScreen Retry/Skip/Start-over/Back-to-feed (the end-of-feed
  popup rows post-date the doc's grep inventory; migrated under the same rule since
  giffyFocus must be gone everywhere) → tv `Button`; TvHomeScreen For You chips →
  tv `FilterChip`; TvSourceFeedScreen Refresh/Sort IconButtons + end-of-feed Refresh
  → tv `IconButton`/`Button`; TvMainActivity header pills → tv `Button` (TvHeaderPill,
  rest-look parity with GiffyPillButton per the §4 audit; the shared widget stays for
  mobile AgeGate). CenterHold/hold-Center keymap/FocusRequester wiring untouched;
  content colors flip natively via tv LocalContentColor where possible (tv Text in
  migrated buttons), conditional scheme reads elsewhere.
- **Documented exception:** shared :feature: screens (SettingsScreen chips/blocklist
  rows, CustomFeedsScreen cycle/delete) keep `giffyFocus` (:core:ui) — recolored to
  the issue trio: focused fill = scheme.primaryContainer (new default; the
  `fillOnFocus` parameter deleted), focused border = scheme.primary. M3 chips keep
  their own outlineVariant rest ring.
- **Scrims (§7):** PlayerOverlay.scrim stays 55% (inside the issue's 40–60% band);
  `secondaryOnVideo` white → onSurface #F7E9EC@70% (desc text bookkeeping; track/
  hairline strokes stay white as the F6a media standard). GiffyColors.OnSurface
  feeds it — still scheme-source-clean.
- Docs: AGENTS-APP.md gained the "Issue-#7 theme rebrand" section under the
  design-system audit (tokens, migration map per surface, exception, owed device
  gates). Typography/shapes untouched (batch-16 owns both; doc §5/§6 checklists pass).
- Owed gates (runnable in a build session): compile+ktlint+detekt+unit tests on
  :core:ui/:core:datastore/:feature:settings/:feature:feed/:app-mobile/:app-tv, then
  the TV36 focus pixel check (fill #7D0030-family, no purple/blue), CenterHold/
  MENU walks, GifCard focus-preview re-check, Medium_Phone AMOLED+player-accent pass,
  release smoke, and the §3.4 onSurfaceVariant-only-large/overscan/48dp walkthroughs.

### Session 2026-10-15 batch 24 (issue-ledger sweep: ALL 27 open issues implemented, gated, merged, pushed)
User slice: "work on all the open github issues; triage, plan, design, implement — jj worktree orchestration." All 27 issues landed across 8 streams (A-H) as parallel/sequential lane work: each stream got a design doc (DESIGN-*.md, file:line evidence), a worker implementation in its own jj workspace (`ws-*` — jj colocate init added this session), a coordinator gate (assembleDebug both apps + check, RAM-serialized), and a jj merge into main with backup-branch pushes at EVERY milestone (`stream-*-wip`/`-design`).
- **A player** (#2 tap hide/unhide not pause, #3 hold-Center panel STAYS open (KeyUp swallow), #8 poster-under-surface, #9 inline-player VM adoption, #16 stop-on-switch, #20 SDR-pinned TV playback (MediaCodecSelector route — AAR check: no TrackSelectionParameters HDR knob in media3 1.11.1), #21 Fit/Crop only, #24 TV error-overlay focus hand-off, #26 FeedRepository distinct-until-changed pager dedupe (mute no longer restarts), #27 @HTTP(hasBody=true) + single-flight follow writes — LIVE-verified with a real token bundle (bare DELETE 204-but-silent-fail reproduced; add/remove verified), #4 video_quality pref Auto/Highest/SD (live manifest probe: hd/sd single-rendition faststart MP4s, no ladder)). **B feeds** (#1 Continue Watching removed + watch_history DROPPED via v11 MIGRATION_10_11, #12 Surprise removed, #10 Feature.CUSTOM_FEEDS=false in :core:ui, #5 Favorites exhaustion-stride + shuffle-on-refresh volatile seed, #6 TV refresh chip + quick-menu row, #18 ONE sort+filter popup with Clear, #22 end-of-feed footer + player end popup, #23 scroll-to-top icon replacing RefreshFeedPill). **C TV** (#14 true-aspect no-crop + focused-row centering via centeredScrollOffset, #15 staggered grid, #19 shared FeedTabStrip extraction). **D search-as-explorer** (#11 empty-state ranged Top gifs Week/Month/All + photos/niches/creators strips + matched tags; Explore deleted). **E settings sub-pages + Blocked lens** (#17, in-screen panels, no NavHost pre-G). **H Feeds group tab + sub-row** (#25, FEED_GROUP pure fns, lastGroupSub). **F M3 theme rebrand** (#7 — full issue token table; dynamic color retired; giffyFocus kept as documented exception on M3-host-on-TV chips with scheme-role reads in Focus.kt; the DV-profile-constant COLLISION with HEVC SDR (DvavPer=1==HEVCProfileMain) caught by unit test → per-family HDR markers). **G navigation-compose** (#13 — stable 2.10.2 via version catalog; RouteNavTypes.kt custom NavTypes for FeedSource/Niche/List-Gif because nav 2.10 auto-derives only NON-polymorphic @Serializable args; MainFeedDefault/MainFeed split avoids RouteEncoder.encodeNull-vs-custom-serializeAsValue; lint Range guard on the re-arm scrollToItems).
- **Device gates (dev = Medium_Phone-equivalent + fresh tv_1080p AVD):** compile/ktlint/detekt/test gates passed everywhere AND the device passes caught 3 crash-class bugs the static gates let through (MainFeed nullable FeedSource → NavType-crash ×2, TvNicheAbout nested arg) — fixed; zero FATALs at rest on both shells after fixes; live-verified UI: home Feeds sub-row + theme (seed #E5194D pink), settings sub-pages incl. Blocked lens on both shells, search explorer rows + ranged strip populating, player tap-reveals controls while playing (#2).
- **Teardown/observability:** gradle --no-daemon --max-workers=2 everywhere; free -h before every build/emulator; emul-stop + gradle --stop at teardown; all milestone pushes via bookmarks (no main force, no tag moves).
- **Open for next batch:** TV D-pad passes for #14 centering walk + #19 strip focus (device host-path verified only via Settings), release cut for v0.5.0 via cut-release.yml when ready, Dependabot dependency-submission noise (unresolved). ~~Follow-flip signed-in device verify~~ CLOSED in-session (2026-10-16): user token bundle → DebugSignIn debug-gate seam (launch extra `giffy_debug_bundle`, BuildConfig.DEBUG-only, token NEVER in source) → For You row rendered signed-in → quick-actions Unfollow row → server `GET /v1/me/follows` proves flip both directions (unfollow: followee gone; re-follow: back) — UI+server agree.
- **Lessons:** (a) jj workspaces snapshot lazily — `jj st` INSIDE a workspace before using it as a bookmark target or the push backs an empty commit; (b) jj abandon on a workspace's @ diverges bookmarks — resolve via `jj bookmark set <name> -r <survivor>`; (c) nav 2.10 auto-NavType handles only non-polymorphic @Serializable — sealed hierarchies need the explicit RouteNavTypes pattern (now canon); (d) RouteEncoder.encodeNull routes through serializeAsValue(null) EVEN for isNullableAllowed — keep route args non-null and use an arg-less start destination; (e) device passes earn their keep — 3 crash-class bugs invisible to every static gate; (f) fresh TV AVD creation path worked headless: avdmanager -d tv_1080p with the google_apis;x86_64 image under the same emul-start recipe.

### Session 2026-10-16 batch 25 (v0.5.0 released + cut-release trigger-chain fix)
- **v0.5.0 RELEASED** (user cut request): `cut-release.yml` minor bump → v0.5.0 @ `cf76b1c7` (versionCode 500, strict-increase + tag/release-collision guards all passed). **Pipeline gap found live**: the tag push made with `GITHUB_TOKEN` did NOT fire `release.yml`'s push trigger (classic GITHUB_TOKEN-doesn't-trigger-workflows rule — v0.3/v0.4 had fired only because they were cut manually via SSH). v0.5.0 was completed via the documented manual path: `gh workflow run release.yml --ref v0.5.0` (HEAD-guard passes by construction since the tag sits on default-branch HEAD). Release green in 7m: assets `GiffyViewer-mobile-0.5.0.apk` (3.4 MB) + `GiffyViewer-tv-0.5.0.apk` (3.3 MB), R8-shrunk, disclaimer body.
- **Signature verify (downloaded release assets):** v0.5.0 mobile + TV APKs carry the SAME release certificate as v0.4.0 (SHA-256 84048555… 9eea5 reuse-forever rule intact).
- **Structural fix landed:** `cut-release.yml` now chains the release itself — final step "Dispatch release.yml on the new tag" verifies the tag exists on the remote (`repos/…/git/ref/tags/$NEXT`) and calls `gh workflow run release.yml --ref $NEXT` with the workflow-level permissions extended to `actions: write` (contents unchanged). LIVE-VALIDATED 2026-10-16: the v0.5.1 cut (patch bump, guards passed, tag on HEAD 7b06fc49) chained release.yml AUTOMATICALLY — release green in 7m, assets GiffyViewer-mobile-0.5.1.apk + GiffyViewer-tv-0.5.1.apk, signature-verified same release cert (SHA-256 84048555…). Single-dispatch end-to-end is now the working norm.
- Also closed out: all 27 issues closed as completed with per-issue evidence comments and the workspaces cleaned up (all stream worktrees removed after a reachability check proved everything landed on main; backup bookmarks retained).

### Session 2026-10-16 batch 26 (e2e sweep completion: BOTH shells — TV F-wave fixes + mobile full sweep, one real bug fixed)
- **TV sweep fix wave (a3724f54, main):** 5-stage TV e2e findings F1–F5 closed. F1 (D-pad focus loss) ROOT-CAUSED: ANY programmatic scrollToItem while a card is focused strips the item's a11y focus + indication (Compose logical focus survives — Center still opened the card); impossible snaps (targeting early items whose content-before is narrower than the centering offset) clamp back one frame later, disposing neighbours. FIXED by moving row centering onto the platform bring-into-view pipeline: custom snapped BringIntoViewSpec, deadband 0 (each focused card exactly centers; deadband caused a ~88px parking artifact), rememberCenteringRow's scrollToItem path deleted. Verified: 12-step RIGHT walk, LEFT walk, cross-row UP/DOWN, player open via Center, single BACK restores the saved card, CENTER/BACK opens-closes the error overlay cleanly (media3 PlayerView consumes ONE back to hide its controller — no app-level trap). F3 icons-beside-title, F4 honest empty states, F5 banner gate — device-verified. CenteredScrollOffsetTest rewritten for the spec math. Regressions invisible to static gates → root cause found with a SCROLL SAMPLER logging state.firstVisibleItem every 150ms (dump-level a11y focus lies; Compose-side event logs + sampler tracing found it).
- **Mobile full sweep (2 subagent runs + coordinator device pass):** PASS everywhere — age gate, tabs, top-bar collapse/re-expand, inline autoplay (pixel-verified playing), tap hide/reveal without pause, hold 2×, quick sheet, BACK-to-feed position, search page + scopes + full explorer, Favorites honest empty, Blocked lens, no custom-feed UI (flag check), PIN set→cold-start lock→unlock→remove (end-to-end), backup export/import SAF round-trip, About, airplane cold start (gate 3), offline banner warm ON/OFF/ON toggles both directions on the final build. 0 FATALs.
- **Real bug fixed (45df332e, main):** full-screen player opened from the SETTLED inline-autoplay tile sat PAUSED — FeedScreen's leave-dispose correctly parks (pause + preview profile) the shared player, PlayerScreen's adopt branch preserved the parked state "for continuity"; adopt now resumes (playWhenReady=true). Unsettled-tile opens were always fine (fresh player + playGif). Device-verified: settled-tile open keeps playing (frames move across 2.5s).
- **Verified NOT bugs (do not re-open):** double-tap on the player while signed out routes to Settings/Account = the designed anonymous-like prompt (PlayerScreen line ~132 docs it). Niches page "Couldn't load niches" during fast-paced sweeps = rate limiting/breaker pacing (live probe: v2/niches 200 + 60 rows with proper identity; paced device opens render every time). Warm offline-banner single-miss after an emulator restart = lost onLost delivery one-off; toggles re-verified. Player-surface a11y masking: full-screen uiautomator dumps come up text-empty while the PlayerView interop is foreground; BACK restores.
- **For You scroll/paging JAM — ROOT-CAUSED + FIXED + device-verified (2026-10-16, same session).** TWO independent bugs, both device-proven and fixed:
  1. **Phantom force-refresh at ~+4s** (FeedViewModel pref-invalidation watcher): a DataStore re-subscription wave re-emits the INITIAL pref state; `drop(1)` swallowed only the first combine emission, so the identical replay passed `distinctUntilChanged` (which never saw the dropped one) and force-restarted the pager under the user. FIX: `.distinctUntilChanged().drop(1)` (dedupe BEFORE drop — dedupe remembers the dropped initial value, identical replays blocked). Verified: 0 forceRefresh calls in the boot window post-fix (FYProbe logs, stripped after).
  2. **Dead bottom strip at full collapse (the actual scroll jam — app-wide, any feed):** GiffyScaffold's collapse shifted content up with `place(0, -offsetPx)` but never grew its measured height; at offsetPx=totalPx (732px here) the content's bottom floated H-732px off the screen and that strip hit NO scrollable — every drag starting there was silently eaten (FeedScreen froze after exactly ONE swipe on BOTH Trending and For You; Niches/player/chrome unaffected — they don't use the collapse). Probe-recorded on the NS connection: consumed deltas then total silence on later drags. FIX: the layout modifier measures the content `minHeight/maxHeight + offsetPx` and places at `-offsetPx` — full-collapsed content still reaches the screen bottom. Device-verified: 10+ deep stops, 4 distinct checkpoint hashes where before was 1-advance-then-frozen. (Lesson: mid-walk a11y labels are unreliable while the settled interop surface is foregrounded — pixel hashes are the evidence; and the earlier "12 distinct stops" reading was decode frames of one settled tile — measurement invalidated, corrected here.)
  3. Same session closes **signed-in double-tap like observed end-to-end**: fresh-bundle sign-in → For You walk (post-fix) → tap-open player → double-tap → app DB gained exactly ONE liked_ids row (`jaggedhighlevelram`, ~43s old) AND the liked re-sync ranks it FIRST in the server's liked page (most-recent-like ordering) — UI+DB+server agree. Note: the manual-curl id_token expired mid-test (TokenExpired 401; id_token-only convention applies to MANUAL curl checks) — the app's silent refresh handled it and stayed signed in. The refresh grant was NOT probed (rotation rule holds).

### Session 2026-10-17 batch 28 (v0.6.0 released + device-gate sweep: #36c Favorites gate, theme-rebrand owed gates, emulator-wedge false alarm discriminated)
- **v0.6.0 RELEASED** (user cut request): `cut-release.yml` minor bump chained
  `release.yml` automatically (batch-25 fix now the norm) → v0.6.0 @ `79f88a18`
  (= main HEAD, the #36c Favorites-row slice), guards passed, assets
  GiffyViewer-mobile-0.6.0.apk (3.6 MB) + GiffyViewer-tv-0.6.0.apk (3.4 MB),
  R8-shrunk, signature-verified SAME release cert (SHA-256 84048555… 9eea5,
  reuse-forever intact).
- **#36c Favorites-row device gate CLOSED (TV36, fresh profile, real token
  bundle via DebugSignIn @phcoxju):** signed out → no Favorites row
  (honest-empty rule hides it — hasFavorites = favorited CREATORS, not liked
  gifs); favorited a creator live (Trending card → hold-Center panel →
  Favorite row, label flips to "Unfavorite") → Favorites row APPEARED on home
  populated with the creator's gifs → **card panel on a Favorites-row card
  shows the "Refresh feed" row** (the #36c change) → pressed it → row
  reshuffled live (first cards changed pose; repository round-robin reshuffle
  + stale-page eviction working). Panel stayed open through the toggle (no
  close on in-place actions).
- **Theme-rebrand owed gates (issue #7 landing) PASSED on both shells:** focus
  fill = #7D0030-family + border = #FF668F (pixel-sampled, no purple/blue
  defaults) on header pills, Settings chips (TV + mobile), quick-action panel
  rows, player error-overlay rows, player niche pills; AMOLED true-black
  sampled (0,0,0) on BOTH shells (restored after); CenterHold panels opened on
  home cards ×2 + MENU player panel with full row set (Like/Mute/Speed/Auto-
  swipe/Favorite/Open feed/Follow/Pin/Add to…/Tags) + D-pad row walk + BACK
  closes panel without popping the player; MENU/tap re-reveals text; mobile
  General sub-page (PIN/Grid columns/Autoplay/AMOLED) renders; error overlay
  Retry recovered a real mid-playback failure live (overlay → Retry focused →
  resume). TV row height Large→cards ~1.4× (508px vs 377px) then restored.
- **Emulator-wedge FALSE ALARM discriminated (do not file as player bug):**
  mid-session ALL video surfaces (inline tile AND fullscreen player) rendered
  black while playback kept advancing (progress bar + countdown moving,
  MediaCodec active). Screencaps showed the home/controls fine → not the app
  renderer. `screenrecord` 4s = 20KB (all-black recording — screencap-variant
  ruled out) + `MediaCodec: rendring output error -32` (EPIPE) in logcat → the
  documented AVD decoder wedge (renderer pipe broke mid-session, unrecoverable
  until process death). force-stop + relaunch → frames render again instantly.
  **Lesson: black-video + advancing progress = wedge until proven otherwise —
  discriminate via screenrecord size (MBs=content, KBs=black) + one process
  restart; never open a player regression from this signature alone.**
- **§8 partial pass:** player pop → home grid restored EXACTLY (same scroll
  position + saved card focus, screencap-verified); sign-in survived
  force-stop/relaunch on both shells. Full 15-step walk + R8-build D-pad walk
  still owed (AGENTS-APP.md nav section updated with the partial-pass state).
- **§3.4 finding flagged (decision owed):** TV quick-action panel rows measure
  ≈37.5dp visual height — under the 48dp M3 target guideline. D-pad-first
  surface (no touch); visually large on 10-foot. Recorded in AGENTS-APP.md
  batch-24 device-gates block; bump to 48dp if a touch-on-TV or strict-a11y
  ask ever lands.
- **Session mechanics lessons:** mobile applicationId is `com.rjbiermann
  .giffyviewer` (pm clear / am start use it; activity path `.mobile
  .MainActivity` — re-affirmed); screencap is 1080x2340 while rendered readers
  scale it — `adb shell input tap` needs ORIGINAL coords (a displayed-coords
  tap missed, hit the Autoplay toggle instead — restored); uiautomator dumps
  lag actual state after navigation (stale dumps showed old screens) —
  screencap is the state evidence (batch-26 lesson re-affirmed).
- **Hygiene:** 0 FATAL exceptions across the whole session (both emulators);
  teardown emul-stop + pkill + `gradlew --stop`; token bundle written to /tmp
  only (0600) and deleted after the session; no app code changes — doc-only
  landing.

### Session 2026-10-17 batch 29 (issue-ledger sweep: #42 #40 #44 fixed + pushed; #43/#46/#49 triaged on-device)
- **#42 FIXED+CLOSED (d47e31bb):** blocks were dead on network-live feeds (For You/Liked) — ContentFilter's sets refreshed only by the cached path + Search; network sources now take a repo-injected `refreshBlocks` closure (list-based `ContentFilter.refreshFrom` overload kept for tests) and both network pagers wear the `PrefObservationLease` (write → invalidate → re-filter; disposed on collection end). Device-verified: block @bromovina in the player → feed re-filters instantly. Regression test in ForYouNetworkPagingSourceTest.
- **#40 FIXED+CLOSED (5df94bc0):** blank band at the feed bottom = GiffyScaffold's collapse layout reported the GROWN child height to M3 Scaffold, which CENTERS content → growth split half off the bottom (collapseOffset/2 dead band, 432px, every scrolled position; gone at offset 0 = "random"). Fix: collapse layout reports the INCOMING maxHeight (CollapseLayoutSpec.reportedHeight, pure + tested). Root-caused by a worker probe (Log GIF40 + layout geometry), hypotheses (bad metadata / composition boundary / feed end) all ruled out on device first.
- **#44 FIXED (aa618a02):** TV row-size ladder gains XXL = 170dp × 2.2 = 374dp (chip + test). Device-verified: cards ~1.2× bigger than XL.
- **#43:** all three For You scope chips verified DISTINCT on the reporter's account (All/Creators/Niches = different first items) on a signed-in phone build; commented asking for specifics (identical order+items? pre-v0.6.0 build?). Niches = read-time subset of the same server pool (no scope param on v2/feeds/for-you).
- **#49:** verified-only ON on TV: niches taxonomy page ✓, niche feed ✓, For You Niches scope ✓ — filter never empties pages here; mobile shares the code paths. Commented asking for screenshot/error-copy detail (couldn't-load pacing vs real wipe).
- **#46:** stress ×3 relaunches: no 429/breaker trips; only recurring error = `trend:v2:pop append p6 HTTP 400` = the documented trending pool cap (expected but logged noisily, NOT For You). Committed follow-up: quiet the pool-cap 400 + visible row recovery (auto-retry on re-entry).
- **Token-bundle lesson (2026-10-17):** fresh user bundles now REJECT manual curl from this host — `BadTokenSignature` for BOTH id_token and access_token (session-bound Kinde signature; earlier id_token-curl convention no longer works). Signed-in device verification goes through DebugSignIn only.
- **TV input lesson:** uiautomator dump coords ≈ screencap coords on TV (1.0-ish scale, small y offset ~27px); tap = focus only on TV header pills — CENTER navigates; BACK stack accumulates on stray taps (force-stop + relaunch is the clean reset; sign-in persists).
- **jj lesson:** commits created while user.email is unset get "no author/committer" and REJECT push ("Won't push commit … no author and/or committer") — jj config user.name/email must be set BEFORE creating commits; re-describe doesn't restamp committer; recover via `jj new <commit> && jj squash --from <commit> --into @`.

### Session 2026-10-17 batch 30 (parallel lanes via jj workspaces + workers: #45 #48 closed, #46 part 1 landed, #47 captured)
- **Orchestration:** THREE parallel lanes with disjoint file fences: ws-45 = #45 (TvSourceFeedScreen+TvMainActivity), ws-48 = #48 (TvPlayerScreen+CenterHold+TvQuickActions), probe lane = #47 live capture (report only). TvHomeScreen.kt fenced OFF everywhere (reserved for #46 part 2). Each worker: module JVM gates only (no assembleDebug/device — coordinator runs those serialized at merge); close-out = jj st inside the workspace + lane bookmark + push. Zero conflicts; both lanes merged fast-forward + assembleDebug + one combined TV device pass.
- **#45 CLOSED (3a7b1f97):** source feed = home-style rows (bands of 4 via FEED_ROW_CARDS + SourceFeedRow reusing GifCard/cardWidth/reservedHeight — no FeedRow fork), row-size setting flows (verified XXL live), niche §8 filter-chip row (Global/Any/Vertical/Horizontal + verified-only, writes existing prefs, no new server params), refresh/sort + honest empties + end footer kept; FeedRowsTest.
- **#48 CLOSED (8e58905b):** player keymap rework — single controlsVisible state init false (no flash on entry/switch), BACK=hide (progress stays) then exit, first CENTER reveals only, hidden-UP/DOWN switch (UP at index 0 = honest no-op), revealed-UP/DOWN = traversal; hold-Center/MENU panel intact. ALL in TvPlayerScreen — no TvMainActivity change. Fully D-pad-verified per-step with screencaps.
- **#46 part 1 landed (1d458dfd):** mediator APPEND treats pool-cap 400 (page>1) as Success(end) — no more "Couldn't load" for the honest wall + launch stack trace; FeedMediatorPoolCapTest (TDD red first). Part 2 (visible recovery auto-retry on row re-entry) still open.
- **#47:** live capture report REPORT-niches-parity.md (on main); gaps: (1) join/leave on niche feed header (small, both shells), (2) TV niches missing category chips + sort (pure wiring), (3) preview-tile listing via v2/niches/search/previews (param probe owed) + unused cover/thumbnail fields.
- **Key lessons:** (a) COORDINATOR BUILD TRAP: assembleDebug compiles the WORKING COPY — after merging a lane bookmark, `jj new main` (or jj edit main) so @ actually contains the lane work, else you device-test a stale tree (cost: one wasted device cycle; the "old staggered grid" scare); (b) rapid multi-key `input keyevent` chains in one shell string drop keys — one key per command + screencap between (the 3s auto-hide makes ~2s gaps look like BACK-exit "bugs" — time your hides); (c) TV uiautomator dump coords ≈ real coords but screencap remains the state evidence; (d) d13-class "BACK exited" false alarm = auto-hide timing, discriminate by screencap timing not one key sequence.

### Session 2026-10-17 batch 31 (lanes #46-part2 + #47-slices1/2 landed; join/leave device findings; OPEN root-cause)
- **#46 CLOSED (1f898b59):** part 2 = TV home rows silent auto-retry (2-retry budget/appearance, 2s debounce, network-return aware via NetworkMonitor; FeedRowAutoRetryTest ×5 TDD). Device verify of the retry itself = deterministic offline-toggle test still owed.
- **#47 slices 1+2 LANDED (6130b0ad, gates green):** join/leave on the niche feed header BOTH shells (FeedHeader joinButton slot + NicheJoinViewModel error/label helpers + NicheJoinStateTest TDD; mobile FeedScreen host approved out-of-fence; TV NicheJoinButton tv-material + honest failure text); TV niches category chips + Sort menu ( NichesViewModel reuse).
- **DEVICE FINDINGS (TV, signed-in):** header button renders + focus ring correct; toggle() PROBE-PROVEN to fire on click; the WRITE path works (Rough Sex went Join→Leave server-side across minutes = my presses wrote; re-entry refresh shows the correct joined label + incremented member count). **OPEN BUG (root cause NOT yet pinned — next session):** the post-write label flip NEVER renders live — ~8 post-press screencaps (1s→30s) across BOTH surfaces showed no flip and no error text; every surface correct only after re-entry refresh. Leading theories: (a) subscribe/unsubscribe calls hang minutes behind the rate limiter under continuous feed traffic (flip only after completion — would ALSO explain the odd-count joined state); (b) a recomposition-propagation break in the header slot. Next step: the result-side probe (onSuccess/onFailure Log, 5 lines) + ONE focused press, or a MockWebServer test of toggle-through-the-limiter timing. NOTE: probes were stripped; account state may still have queued toggles completing later (sluty-milfs subscribe was in flight at session end — verify/undo next session).
- **Device lesson (TV focus):** the new feed-header Join button is UNREACHABLE by plain UP/DOWN traversal from cards (framework jumps to the top-right icons) — reachable only via a side-path (Global chip → UP). Reachability fix owed with the flip fix; taps on TV do not move focus (consistent all session) — D-pad only.
- **Coordinator lesson:** update-stale on a worker workspace can leave the lane bookmark DIVERGENT (lane-47 conflict after the worker's own push + my rebase) — resolve with `jj bookmark set <lane> -r <merged-commit-id>`.

### Session 2026-10-17 batch 32 (#43 closed; #49 REPRODUCED on device — silent-empty taxonomy hang; open root-cause + fix queue)
- **#43 CLOSED** (user confirmed: "similar, not identical — same type of posts" = the read-time-subset behavior, expected).
- **#49 REPRODUCED ON DEVICE (TV, signed-in build):** the niches TAXONOMY page rendered completely EMPTY (title + All chip + Sort, zero rows) with NO error copy, NO spinner, and stayed empty >40s idle. NOT the filter (the taxonomy has no verified-only path — code-checked), NOT the bounded cooldown (max ~35s; still empty after 40s+). It is a SILENT HANG/EMPTY: the VM's load either hangs in the limiter/breaker pipeline or failed with loadFailed never surfaced — the page gives zero indication and never retries. Same silent-hang family likely explains the #47 join-label-flip-never-renders finding (write coroutine pending = no flip, no error; both surfaces correct only after re-entry) and possibly part of #46's reports.
- **Fix queue (next session, in order):** (1) result-side probe (onSuccess/onFailure Log) + one focused press to split "hang vs fail" for both the taxonomy load and the join write; (2) honest pending + failure states + #46-style auto-retry on the niches taxonomy (both shells) and any other silently-empty surface; (3) limiter/cooldown trap audit: cooldownUntil extends on every 429 (maxOf) — a background 401/refresh retry loop can pin it open indefinitely; check who churns requests when a feed's token refresh fails, and whether writes need a priority lane; (4) TV focus traversal keeps SKIPPING the feed-header Join button (reachable only via Global chip → UP) — reachability fix owed.
- Probes stripped; emulator + daemons down; /tmp/bundle.json gone (TV AVD retains sign-in; bundle re-needed for fresh installs only).

### Session 2026-10-17 batch 33 (#49 FIXED + device-verified; join double-tap guard; callTimeout; fresh token bundle #5 in /tmp kept for queued signed-in gates)
- **Root-cause family (static):** RateLimitInterceptor wraps EVERY call in `runBlocking { limiter.acquire { chain.proceed } }` — limiter delays happen ON OkHttp threads; a 429/cooldown burst with ≥5 concurrent in-flight calls to one host starves the OkHttp async dispatcher (maxRequestsPerHost=5) and any further queued call (taxonomy load, join write) waits with NO timeout and NO error — matches the silent-empty taxonomy page, the never-flipping join label (write pending forever), and the #46-family reports. Fix companion: `callTimeout(60s)` on the client converts any such hang into the honest failure states.
- **#49 FIXED (`d01d560d`, all gates green — feature:feed/app-mobile/app-tv/core:network compile+ktlint+detekt+tests):** TvNiches + NichesScreen now show a pending spinner while a load is in flight, an honest "No niches" empty when the pool is genuinely empty, and the shared `AutoRetryEffect` (moved out of TvHomeScreen into `feature/feed/AutoRetryPolicy.kt` — ONE source, both shells; FeedRowAutoRetryTest import updated) with the same 2-retry budget + 2s debounce + network-return re-key, wired through NichesViewModel (now owns loading + NetworkMonitor passthrough). TDD red-first on the join pending contract (pendingAfterStart/pendingResolved in NicheJoinStateTest).
- **Join double-tap guard:** NicheJoinButton (TV) + the mobile header button take `pending` — disabled + only while the write is in flight (from the #47 device pass: repeat presses raced the joined-state non-determinism).
- **Device verify (TV, deterministic offline recipe):** taxonomy loads + renders normally (fresh counts); SVC wifi/data off → honest "Couldn't load niches — Retry" copy (NO silent void) — the exact #49 state is now visibly handled; auto-retry fires capped (2) and does NOT loop while the failure persists; re-entry + fresh network → auto-recovery (page populated 928992/633009 live). NOTE: the failing stretch after the network restore was the EXPIRED tokens (dead identity), not the app — re-fatting with DebugSignIn fixed everything instantly.
- **Fresh token bundle #5 pasted by the user (2026-10-17 ~20:4x) — KEPT at /tmp/bundle.json (0600)** for the queued signed-in gates (For You paging stress T15, Follow-flip UI-side re-flip, join signed-in re-verify). Convention unchanged: delete after those gates.
- **Still owed (device work):** TV focus traversal keeps skipping the feed-header Join button (reachable via Global chip → UP wait only) — reachability fix with the traversal pass; the runBlocking-dispatcher starvation itself may warrant a longer-term coroutine-aware interceptor design (NOT built — callTimeout + honest states are the shippable fix; note as the follow-up candidate).

### Session 2026-10-17 batch 34 (parallel lanes landed + #50 planned; #47 CLOSED; join reachability device-proven; a11y report; follow-ups)
- **Parallel lanes (jj worktrees + workers, disjoint fences)**: lane-47s3 (#47 slice 3 PREVIEW TILES — live-probed `v2/niches/search/previews` WITHOUT query= (verified order=subscribers|random, category=, page, count≤100, {previews,page,pages,total}, niche cover/thumbnail/counts + full GifDtoShell; posts/alphabetical remain UNPROBED — sort menu ships 2 orders) → shipped tiles BOTH shells + leak-zero merge (NicheListingTilesTest ×6, TDD) — MERGED main b4ef4843; lane-a11y (READ-ONLY audit → REPORT-a11y-mobile.md, 264 lines/73 citations) — MERGED. **Worker lesson: the first a11y agent run returned a fake completion (no file, 2.5-min session, acceptance: rejected) — revived with hard requirements (≥15 files quoted, ≥120 lines, file:line evidence or auto-reject) and the redo was excellent.** #50 ("add full testing") = staged plan commented; launches after in-flight lanes (stacked, not parallel).
- **#47 CLOSED** (slices 1+2+3 all landed + device gates).
- **Join reachability FIX fix d06fca94:** the filter-chips row's UP now redirects onto the Join button (focusProperties; the framework beam skipped it to the top-end icons). **Device-proven: UP from cards/chips → dump reads FOC='Join Niche' ✓** (UP again → top-end Refresh = sane default). Live-write label-flip verify STILL OWED (focus dumps went dark in the interop-mask phase before a Center could land).
- **Device result:** niche previews tile-render on TV (taxonomy: images+About side-by-side focusables, walk clean, feed-row cards load) — slice 3 TV gate ✓ (mobile tile gate still owed; same shared VM code path, low risk).
- **FOLLOW-UPS:** (a) a11y fixes per the new issue (6 findings ranked); (b) the committed report lists stage-2 Compose UI tests later; (c) §8 15-step full walk still owed; (d) mobile tiles device gate; (e) join label-flip live write verify; (f) token bundle #6 in /tmp/bundle.json (0600) — kept deliberately for the queued signed-in gates (label-flip write, For You stress, Follow re-flip); delete after those.

### Session 2026-10-17 batch 35 (join label-flip MYSTERY CLOSED — optimistic flip landed + device-proven)
- **ROOT CAUSE (instrumented, live):** the toggle write DID complete in-session (`NJPROBE2` probe: `result=false err=null` at +100s after press, fresh tokens, healthy network) yet the header Join label stayed STALE for 6+ minutes — the write completion raced heavy feed paging and the header slot recomposed stale (non-joined capture predating the flip never re-invalidated; the press#1-era burst of queued writes at process relaunch masked the pattern before it).
- **FIX (e02aa4b0, main):** OPTIMISTIC FLIP welded to the pending guard in `NicheJoinViewModel.toggle` — write-direction computed at press, membership flips immediately, button stays disabled over the write's life (no double-write possible), and a failed write rolls the flip back (exact math TDD'd in `NicheJoinStateTest` both directions: failed JOIN restores, failed LEAVE re-adds) + surfaces `JOIN_UPDATE_FAILED`. No stale-recomposition window can ever show the wrong state: it either already shows the new truth or rolls back with an honest error.
- **DEVICE PROVEN (TV, real token):** CENTER on the focused Join → label flipped at +0.4s ("Leave Niche"); stable at +30s across paging; second press flipped back instantly and the server settled 580,164 → 580,163 members matching one subscribe + one unsubscribe; zero error surfaces. All module gates green.
- Note: the About page's identical-button staleness also dies with this fix (same VM).
- Open items unchanged: mobile a11y fixes (#51), §8 full walk, mobile tiles gate, #50 testing stages launch.

### Session 2026-10-17 batch 36 (parallel lanes: #51 a11y fixes + #50 stage-1 tests landed; BrowserIdentity overflow bug found+fixed)
- **Parallel lanes (disjoint worktrees/fences):** lane-a11yfix (ws-a11yfix) = ALL SIX #51 findings fixed (f318fb69) — TalkBack custom re-reveal action on the player body (breaking fix); minimumInteractiveComponentSize at the 3 sub-48dp sites (visuals unchanged); scrub+speed stateDescription/contentDescription; semantics heading() ×11; PlayerOverlay scrim 0.60 + secondaryOnVideo 0.95 + gradient-from-0.75 (in-band; residual: secondary-over-white ≈3.4:1 = design decision, doc'd in DESIGN-A11YFIX.md); named pin/unpin + labeled search rows. lane-test50 (main checkout, tests-only) = 45 new unit tests ×6 files (breaker/limiter-cooldown/retry-edge/filter-combos/backup-versions/mediator-TTL) (9c55672c) — #50 stage 1.
- **BUG FOUND BY TEST EXPANSION + FIXED SAME SESSION (1749f27f):** BrowserIdentity.newSessionId() — `1e18 + (nextLong() and MAX) % 9e18` topped ABOVE Long.MAX_VALUE → ~8.4% of X-Session-Id headers were NEGATIVE strings. Range now [1e18,9e18); existing 19-digit test proves it; stress ×5 clean. (Session-identity implication: ~1-in-12 launches had a malformed header — the server accepted them, so no user-visible harm, but identity consistency rules now rest on a sane generator.)
- **Merged main @ 1749f27f; all module gates + assembleDebug ×2 green.** #51 CLOSED; #50 stage-1 progress commented (stage 2 = Compose UI tests via testing-setup, stage 3 = instrumented; FeedMediator offline branches untestable at JVM rung — Log-throws, doc'd).
- **Owed device gates (next device session):** TV player overlay visual (the shared token bump reaches TvPlayerScreen), 48dp hit-area layout shifts on the phone (pills/caption), TalkBack re-reveal functional check, §8 full 15-step walk, mobile tiles gate, join label-flip re-verify on a fresh build.
- Worker-lane count this batch: 2 (a11yfix + test50) — fences held: a11yfix touched exactly its 10 files; test50 touched only */src/test (read-only main rule kept it from fixing the bug it found — coordinator fixed it post-merge).

### Session 2026-10-17 batch 37 (parallel lanes: #47 fully closed via 5-order sort menu + #50 stage 2 compose-test skeleton)
- **lane-47sorts (4e9d7595):** live-probed the last 3 orders on /v2/niches/search/previews (posts = gif-count desc, alphabetical_asc/desc = name asc/desc; same {previews,page,pages,total} shape, no BadOrder) → full site-parity 5-order sort menu on BOTH shells via the shared `nicheListingSorts` helper (TDD NicheListingSortsTest; the ad-hoc sortLabel string-surgery in both hosts replaced by the shared label). AGENTS-NETWORK row updated (all 5 verified). **#47 FULLY CLOSED** (slices 1-3 + this tail).
- **lane-test50s2 (6a6e1174):** #50 stage 2 = Robolectric 4.17 + compose ui-test (version catalog; module-level isIncludeAndroidResources in :core:ui/:feature:feed/:feature:settings; NO root gradle.properties) — **Robolectric-compose VIABLE on AGP 9.4.1/BOM 2026.09.00**. 7 compose tests: AgeGate ×3, player custom-action reveal ×1, speed stateDescription ×1, section headings ×2. Seams added (internal, TDD'd): SettingsScreen section() composables, QuickSheet speed helper, PlayerScreen playerControlsSemantics modifier factory. API-drift notes: MainTestClock.advanceUntilIdle() gone (→ waitUntil); custom actions in SemanticsActions (not Properties).
- **Stage-3 (instrumented) list for later:** full PlayerPage machine (Hilt/TokenStore/Media3; AndroidKeyStore unavailable under Robolectric), FeedMediator offline-error branches (Log-throws), live-network pacing families.
- Merged main @ 6a6e1174; gates + assembleDebug ×2 green; workspaces cleaned; #50 stage-2 comment posted.
- **Owed device session (unchanged list):** §8 15-step walk, TV overlay visual (shared token bump), 48dp layout shifts, TalkBack re-reveal functional, mobile tiles gate, join flip re-verify + the new 5-order sort menu live.

### Session 2026-10-17 batch 38 (lanes landed: #51 a11y residual, #47 tail device verify, #50 stage-3 instrumented suites; Sort D-pad reachability)
- **#51 a11y residual FIXED (91f0b23d, lane-scrim, main):** dark backdrop pill behind the player-overlay secondary text on BOTH shells (secondaryOnVideoBackdrop, black @ 0.80) + shared WCAG contrast helper OverlayContrast.kt in :core:ui with OverlayContrastTest — secondary text over video went ~3.5:1 → ~11:1.
- **#47 tail device verify CLOSED:** all 5 niche sort orders live-verified on device — A-Z verified on mobile via the Niches sort menu (header "Sort: Name: A-Z", tiles alphabetized); the shared `nicheListingSorts` helper (batch 37, 4e9d7595) is the single order table used by both shells (NicheListingSortsTest).
- **TvNiches Sort D-pad reachability FIXED (52278b97):** chips-row focusProperties redirect (right-exit) onto the Sort button via FocusRequester; verified via uiautomator dump focus reads. NOTE: this fix is a lane commit parented on 91f0b23d — sibling of lane-stage3, NOT yet merged to main (merge owned by the coordinator).
- **#50 stage 3 LANDED (e499d3c0, lane-stage3, main):** instrumented suites authored — PlayerScreenMachineTest ×4 + FeedMediatorOfflineTest ×2 on a hand-wired TestFeedGraph (real limiter/breaker/retry/Room/TokenStore/Media3 + MockWebServer); 5s-silence WAV asset androidTest-only (user approved keeping it; never ships). Device run then went RED on a three-layer harness failure wave, fixed by workers (lane-stage3fix 6be13730, merged): first the class-exact IOException assertion (ConnectException is the real subclass off connection-refused → assertTrue), then the MockWebServer dispatcher missing v2/auth/temporary (anonymous token parse got "{}" → token null → unauthenticated feed), then the saved test token making FeedRepository seed For You (v2/feeds/for-you) — the graph now serves the fixture page for both paths. FINAL: :app-mobile:connectedDebugAndroidTest tests=6 failures=0 errors=0. Harness fixes: DEX-illegal space test names renamed, debug-only cleartext network-security-config for localhost MockWebServer (app-mobile/src/debug/, never ships), androidTest asset read from the instrumentation context (not targetContext), runOnMainSync for the media3 park call.
- **Session lessons:** (a) media3 throws WrongThread when a player is parked off-main in tests → runOnMainSync; (b) androidTest assets live in the TEST apk — read via the instrumentation context, targetContext.assets misses them; (c) `adb am instrument` arg parsing from the host needs a pushed shell script.
- **Batch-38 device gates CLOSED (all PASS, 0 FATALs, evidence /tmp/gate38/):** mobile (first worker): §8 player-pop → home grid restored, TalkBack re-reveal functional, mobile niche tiles populated, 48dp targets no layout overlap; TV (second worker, after the first timed out mid-TV): A-Z reorder walk 1980s Porn→2000s Porn→2d Comics→2dicks1chick→3D Anal strictly ascending (Posts pre-sort first tile = Amateur Girls, different order), overlay backdrop pill legible over bright frames (description + tag pill), Sort focus redirect verified (chips-row right-exit → Sort button focused). Merge of 52278b97 + 9a3f74b0 + ledger DONE (main = baf56132). Residuals: A-Z verified on the General category only; rate-limit Retry pacing seen once (known, recovered). Still open: §8 full 15-step walk; v0.6.2 release cut (user: cut later).

### Session 2026-10-18 batch 39 (Sort D-pad fix actually lands — batch-38 merge had silently dropped it; repo hygiene)
- **DISCOVERY:** batch-38's "merge DONE (main = baf56132)" did NOT contain the TvNiches Sort D-pad fix — the merge resolution silently dropped the 12-line change (baf56132's TvNiches.kt has 0 focusProperties lines; is-ancestor check on 52278b97 = false). Lesson: a merge's ledger line is not proof of content — verify with `git merge-base --is-ancestor` / content grep before trusting "DONE".
- **LANDED (b89a5ac8, main@origin):** the same fix (chips-row `.focusProperties { right = sortFocus }` redirect + `focusRequester` on Sort, rebased onto main conflict-free). Gates: `:app-tv:check` green (compile+ktlint+detekt 0 smells+unit).
- **DEVICE GATE (TV AVD `tv` — note: AVD name is `tv`, not `tv_1080p`):** D-pad walk on Niches: initial focus was the listing container (one RIGHT fell to About, matching the original finding); UP reached the row; RIGHT-from-last-chip focus bounds = [1456,160][1792,256] = Sort button focused (the fixed behavior; old behavior fell DOWN to About); CENTER opens the 5-order sort menu (Subscribers/Posts/Name A-Z/Z-A/Randomize). 0 FATALs. Teardown clean (emul-stop, gradle daemons stopped).
- **Repo hygiene:** all bookmarked lane/stream bookmarks verified patch-merged into main (git cherry); divergent duplicate half of lane-47 abandoned; stale bookmarks deleted.
