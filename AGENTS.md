# Giffy Viewer — Agent Guidance (root)

Unofficial RedGifs client. Pure viewer and gif organizer — browsing/viewing and
organizing (labels, favorites, feeds, filters) work whether the person is signed in or
using in-app-only features, and watching gifs on the TV is a first-class use case, not
an afterthought. GitHub-Releases-only distribution.
The **AGENTS-*.md files are the full spec** (PLAN.md is deleted; its content was
merged into these files — historic `former-PLAN spec §N` comments in code refer to it, the
section numbers no longer map, AGENTS docs are current).

## Non-negotiable

- **Viewer only.** No scraping, rehosting, redistribution, or deep-linking beyond share.
- **No "upstream" branding** anywhere in app/README. App name: *Giffy Viewer*. Disclaimer required.
- **No analytics, ads, trackers, Firebase, crash reporting.**
- **No ads anywhere — hard rule.** No ad SDKs, no ad units, no sponsored placements,
  no affiliate links, no premium upsell surfaces. Every rendered item comes from a real
  API response through ContentFilter; `promoted: true` gifs are dropped in filter stage 0
  (paid items inside genuine responses don't render either).
- **Responsive-first, shell-agnostic.** One touch screen set scales with window width;
  all UI/logic lives in shared `:core:*`/`:feature:*` modules, `:app-*` are thin platform
  shells — a future desktop/iOS/web target is a new shell, not new screens. Shared modules
  must not hardcode Android-only idioms a non-Android shell couldn't provide. TV is a
  separate module on purpose (D-pad/10-foot is a different input modality, see AGENTS-APP.md).
- **Fallbacks are spec'd, not scheduled:** design a fallback branch but don't build it
  until live verification proves the primary path needs it — otherwise it's dead code.
- **LeakCanary** is `debugImplementation` only (both app modules) — never ships, no phone-home.
- **No WorkManager** — background refresh is silent network-on-open (stale-while-revalidate),
  no scheduled jobs.
- **No TODOs, no placeholders, no stubs.** Every committed file compiles and works.
- Adapt **API endpoint paths only** when live-site checks reveal drift — never architecture, rules, or features.
- Prefer **verifying against the live site** rather than assuming.

## Stack (fixed)

Kotlin 2.x · Compose/Material 3 · Retrofit + OkHttp · Room + Paging 3 · Hilt · Coil ·
Media3/ExoPlayer + SimpleCache · kotlinx-serialization (`ignoreUnknownKeys=true`) ·
security-crypto (`EncryptedSharedPreferences`, alias `giffy_auth`) ·
version catalog (`gradle/libs.versions.toml`) · ktlint + detekt fail the build.
minSdk 24 (mobile) / 26 (TV) · targetSdk 35 · DB name `giffy.db`.

## Coding rules

- No `GlobalScope`. Structured concurrency only (`viewModelScope`, `lifecycleScope`).
- `UiState` types are immutable data classes; expose `StateFlow`, never `MutableStateFlow` from VMs.
- Room schema changes **always** via explicit `Migration`, never `fallbackToDestructiveMigration`.
- Following someone: flat structure, minimal files, no speculative abstractions.
- **Standard libraries over custom code.** Use popular, well-maintained, trusted
  libraries (see Stack above — all are mainstream: OkHttp/Retrofit/Room/Paging/Hilt/
  Coil/Media3/DataStore/security-crypto) and AndroidX/stdlib APIs for whatever they
  cover. Write custom implementations only for behaviour no maintained library
  provides (e.g. the app-specific ContentFilter, rate limiter, feed mediator).
  Hand-rolled replacements for library/stdlib functionality are a bug, not a
  feature — before writing a utility, check the version catalog + AndroidX first.
- **Standard-library audit (2026-10, doc-only):** codebase scan found exactly two
  shippable replacements: `Hosts.decodeBase64` (core/model) → `kotlin.io.encoding
  .Base64` (stdlib, no API-level floor, the old java.util.Base64/API-26+ excuse
  doesn't apply to the Kotlin API); delete `RateLimitBus` and publish directly on
  its `MutableSharedFlow` (the class adds nothing). Everything else audited is
  justified custom: rate limiter/breaker/retry (plan-specified policy, no Android
  library equivalent), PKCE (AppAuth unusable — redirect_uri is the site root, not
  an app scheme), `formatRemaining` (java.time is API 26+), UI components
  (giffyFocus/PinLock/AgeGate/GiffyScaffold — no standard equivalent).
  **APPLIED 2026-10-02:** both swaps landed (Hosts stdlib decode — live smoke on
  Phone34; RateLimitBus → typealias on MutableSharedFlow + `publish` extension, API
  shape unchanged). Bonus fix in the same sweep: TokenStore's PKCE challenge used
  `java.util.Base64` (API 26+) — a real crash on minSdk 24/25 — swapped to
  `Base64.UrlSafe.withPadding(ABSENT)`.
- Non-trivial logic ships with one minimal self-check (small `test_*.py`-style unit test or `@Test`), not a framework suite.
- rate-limit invariant: ≤10 requests in any rolling 5s window — own change must not break it.

## Module map

```
:core:model      :core:network   :core:auth     :core:database
:core:datastore  :core:player    :core:ui
:feature:feed    :feature:search :feature:favorites
:feature:auth    :feature:settings
:app-mobile      :app-tv
```

## Sub-agent docs (read the one matching your task)

| File | Covers | Plan § |
|---|---|---|
| `AGENTS-NETWORK.md` | OkHttp, rate limiter, 429/5xx, circuit breaker, endpoint inventory, DTOs |
| `AGENTS-DATABASE.md` | Room schema, migrations, cache-first, TTLs, watch history |
| `AGENTS-AUTH.md` | WebView login, token storage/reveal, TV paste-token, logout/revoke |
| `AGENTS-PLAYER.md` | ExoPlayer, SimpleCache-by-ID, data-saver, resume |
| `AGENTS-CONTENT-FILTER.md` | The single ContentFilter choke point, blocks, groups, collections, leak-zero |
| `AGENTS-APP.md` | Mobile + TV UI, age gate, navigation, per-feed prefs display, UI lingo |
| `AGENTS-UX-PATTERNS.md` | Instagram/TikTok interaction practices mapped per device type — doc-only reference, no implementation |

## Verification gates (per phase)

1. Skeleton builds, ktlint+detekt run.
2. Network validated against live site **before** building on it.
3. Airplane-mode cold start: cache renders, zero network, no crash.
4. Rate-limit tests pass; 429/5xx/circuit-breaker verified.
5. Login survives process death; logout revokes.
6. Filter leak-zero on every surface.
7. Export → import round-trip on fresh install.

When unsure: check the matching AGENTS doc first, then ask. Don't invent endpoints or features.

## Device testing (adb / emulator)
- Use the **debroid** skill and the **mobile** MCP server for all adb/emulator testing
  (install, launch, UI interaction, logs, debugging). Raw `adb` one-offs only when the
  MCP/debroid path genuinely doesn't cover it.
- **Prefer UI dumps over screenshots** (`adb shell uiautomator dump` + read the XML) when verifying
  UI state — each screenshot burns against the ~30-image provider cap and a saturated request fails
  with `Too many images in request: 31 > 30`. Screenshots only when visual layout/appearance matters.

## Debugging lessons (2026-09)
- **debroid breakpoints suspend the main thread** (Compose/Paging code runs on main) → ANR
  dialogs and frozen/empty UI that look like app bugs. Never leave a debug session attached
  while driving the UI; detach (`debroid detach <sess>`) before any interaction test.
- Phone34 emulator dies every few minutes on this host (15GB RAM) — stop gradle/kotlin
  daemons (`./gradlew --stop`) before emulator work, keep test windows short, data is wiped
  on every restart (age gate reappears, DB empty).
- **Emulator crash root-caused (2026-09-30): qemu SIGSEGV in NVIDIA NVDEC path**
  (`libnvcuvid` → `MediaCudaVideoHelper` → `cuMemcpy2D_v2`), triggered whenever the guest
  plays video (51 core dumps, all same signature). Not RAM/OOM. Fix:
  `ANDROID_EMU_MEDIA_DECODER_CUDA=0` + `ANDROID_EMU_MEDIA_DECODER_CUDA_HEVC=0` set in
  `~/.bashrc` and `~/.config/environment.d/90-emulator-decode.conf` (emulator falls back to
  software ffmpeg decode). NOTE: the `android` CLI runs qemu inside a systemd user scope
  (`systemd-run --user`), so shell exports do NOT reach it — the vars must be in the systemd
  user environment: `systemctl --user set-environment` (runtime) +
  `~/.config/environment.d/90-emulator-decode.conf` (persists across logins). Env vars must be
  set *before* the emulator process starts — restart emulator to apply. Verified against AOSP
  `emu-main-dev` MediaH264DecoderGeneric.cpp `canUseCudaDecoder()`.
- Emulator cold-start first fetch: wait 45s+ after Enter before judging the grid empty.

## Phase 6 progress (2026-09)
- TV app live: age gate → home (Trending/Explore/Continue Watching rows, tv-material Cards,
  D-pad) → player with D-pad next/prev + resume; paste-token Account screen reuses
  feature:auth AuthScreen. `watch_history` write verified end-to-end on TV36.
- (All "still open" items from this era have since shipped — see later sessions.)
- D-pad/focus docs: the TV D-pad spec (full navigation, search
  focus-up, accent-outline focus states, 10-foot contrast) + M3 a11y baseline (≥48dp
  targets, contentDescription, 4.5:1 contrast) lives in AGENTS-APP.md;
  implemented focus lessons live in
  AGENTS-APP.md TV section (focus scale 1.08, initial-focus FocusRequester fix, MENU
  quick-actions). Any new D-pad/focus lesson → AGENTS-APP.md.

## Phase 7 progress (2026-09)
- Content filter (slice 1), Settings screen (slice 2), UX polish + TV Settings + data
  saver (slice 3), Favorite creator (slice 4), Preferences backup import/export (gate 7
  closed, live round-trip verified on Phone34). See AGENTS-CONTENT-FILTER.md / AGENTS-APP.md.
- TV quick-actions verified (MENU on focused card → favorite, DB row landed); along the
  way fixed: TvLazyColumn prefetch crash (→ compose LazyColumn), stale-empty Favorites
  page TTL bypass in FeedMediator (with regression test), network token-race 401 fix
  (mutex-wait + single 401 retry, see AGENTS-NETWORK.md).
- Phase 5 gate (login survives process death) **verified live on TV36 2026-09-30** with a
  real account id_token: paste → store → authenticated 200s on feeds/search → force-stop
  → relaunch → still "Signed in" → sign-out works. id_token is a 1h token.
- **WebView OAuth login DONE (2026-09-30)** — app-driven PKCE (live-verified via Playwright
  login observation + real emulator login): auth2 authorize → user logs in (email + OTP
  code) → code intercepted on redirect → in-app token exchange → id_token + **refresh_token**
  stored. Signed-in + process-death + authenticated feeds verified. On-401 silent refresh
  wired in both app modules (see AGENTS-AUTH.md / AGENTS-NETWORK.md). Paste-token is the
  TV fallback. The site never writes `localStorage.auth_data` — capturing it was a wrong
  assumption, now documented.
- check-sound probe (2026-09-30): GET 405, POST requires a user token (id_token 403 —
  context unknown) — spec'd-not-scheduled; gif `hasAudio` drives the mute UI.


### Phase 9 progress (2026-09-30)
- Swipe-player §9 slice set complete + live-verified: theme tokens, player controls,
  single-progress fix, description+tags display, auto-hide clears all text but the progress
  line, right action rail (like / mute / share / overflow quick sheet), double-tap like +
  heart pop, playback speed 0.5–2× in overflow, error Retry/Skip overlay, auto-swipe toggle
  (reduced-motion aware, data-saver forced off, prefs-persisted), adjacent-item prefetch via
  Media3 DefaultPreloadManager (prepare-only — no data burn).
- Theme-mode research (2026-09-30, decision): System/Light/Dark selector **considered and rejected** —
  the app stays dark-only with the borrowed upstream palette. SYSTEM mode is a near-free
  ~30-line slice (enum pref + selector + `isSystemInDarkTheme()` pass-through) if ever wanted, but
  LIGHT is new design, not a port: no light tokens exist to borrow (site is dark-only), it needs an
  invented ~20-role scheme + fresh contrast audit + status-bar-icon handling — deferred until there
  is an actual light palette to ship. AMOLED + dynamic-color options unchanged.
- Live like-write drift fixed: PUT/DELETE /v2/gifs/{id}/like need JSON body
  {context:trending, source:watchlist, position} + Json encodeDefaults=true (all-default
  @Body DTOs serialized to {} otherwise); Retrofit DELETE+body needs @HTTP(hasBody=true).
  Pinch zoom resets on swipe now (2026-09-30 revision).

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
  - `navigation-3` / `navigation-event` — only if navigation is ever migrated; not a
    current ask (app uses its own nav).
  - `testing-setup` — if unit-coverage is ever expanded beyond the one-test rule.
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
  playbook. Prereq check: Compose-only ✓; Navigation 3 ✗ (app uses its own
  saveable route-stack + BackHandler — deliberate, spec'd, not a gap to fix
  without a user ask; skill's N3 suggestion noted-and-declined).
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
mobile/TV UI/UX design, keep theming matched to the RedGIFs site. Static audit done,
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
- **Reconciliation rule spec'd:** RedGIFs site = brand (GiffyColors palette + DM Sans);
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
made per-slice ringo-dance uneconomical; all gates still covered:
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
