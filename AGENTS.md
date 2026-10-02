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
