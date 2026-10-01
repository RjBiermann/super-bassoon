# Giffy Viewer — Agent Guidance (root)

Unofficial upstream Android client. Pure viewer, GitHub-Releases-only distribution.
Full spec: `PLAN.md` — read it before working in this repo. Phase order there is fixed.

## Non-negotiable

- **Viewer only.** No scraping, rehosting, redistribution, or deep-linking beyond share.
- **No "upstream" branding** anywhere in app/README. App name: *Giffy Viewer*. Disclaimer required.
- **No analytics, ads, trackers, Firebase, crash reporting.**
- **No TODOs, no placeholders, no stubs.** Every committed file compiles and works.
- Adapt **API endpoint paths only** when live-site checks reveal drift — never architecture, rules, or features.

## Stack (fixed)

Kotlin 2.x · Compose/Material 3 · Retrofit + OkHttp · Room + Paging 3 · Hilt · Coil ·
Media3/ExoPlayer + SimpleCache · kotlinx-serialization (`ignoreUnknownKeys=true`) ·
WorkManager · security-crypto (`EncryptedSharedPreferences`, alias `giffy_auth`) ·
version catalog (`gradle/libs.versions.toml`) · ktlint + detekt fail the build.
minSdk 24 (mobile) / 26 (TV) · targetSdk 35 · DB name `giffy.db`.

## Coding rules

- No `GlobalScope`. Structured concurrency only (`viewModelScope`, `lifecycleScope`).
- `UiState` types are immutable data classes; expose `StateFlow`, never `MutableStateFlow` from VMs.
- Room schema changes **always** via explicit `Migration`, never `fallbackToDestructiveMigration`.
- Following someone: flat structure, minimal files, no speculative abstractions.
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
| `AGENTS-NETWORK.md` | OkHttp, rate limiter, 429/5xx, circuit breaker, DTOs, ContentFilter entry point | 2, 4 |
| `AGENTS-DATABASE.md` | Room schema, migrations, cache-first, TTLs, watch history | 5 |
| `AGENTS-AUTH.md` | WebView login, token storage/reveal, TV paste-token, logout/revoke | 2 |
| `AGENTS-PLAYER.md` | ExoPlayer, SimpleCache-by-ID, data-saver, resume | 5 |
| `AGENTS-CONTENT-FILTER.md` | The single ContentFilter choke point, blocks, groups, collections, leak-zero | 6 |
| `AGENTS-APP.md` | Mobile + TV UI, age gate, navigation, per-feed prefs display | 3, 7–9 |
| `AGENTS-UX-PATTERNS.md` | Instagram/TikTok interaction practices mapped per device type — doc-only reference, no implementation | 7–9 |

## Verification gates (per phase, from PLAN §10)

1. Skeleton builds, ktlint+detekt run.
2. Network validated against live site **before** building on it.
3. Airplane-mode cold start: cache renders, zero network, no crash.
4. Rate-limit tests pass; 429/5xx/circuit-breaker verified.
5. Login survives process death; logout revokes.
6. Filter leak-zero on every surface.
7. Export → import round-trip on fresh install.

When unsure: check PLAN.md first, then ask. Don't invent endpoints or features.

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
- Still open (later slices): PIN pad optional lock, settings screen (cache size/data-saver),
  Top This Week + Favorites + group rows (Phase 7 features), focus polish/scaling.
- D-pad/focus docs: PLAN §7/§9 covers the TV D-pad spec (full navigation, search
  focus-up, accent-outline focus states, 10-foot contrast) + M3 a11y baseline (≥48dp
  targets, contentDescription, 4.5:1 contrast); implemented focus lessons live in
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
- Still open: favorite tag, For You anonymous blend (server feed landed, live-verified via
  the app 2026-09-30), niche groups, collections, hide-count stats, Top This Week row,
  PIN pad lock, logout WebView-cookie clearing. check-sound probe (2026-09-30): GET 405,
  POST requires a user token (id_token 403 — context unknown) — spec'd-not-scheduled,
  gif `hasAudio` drives the mute UI.

### Phase 9 progress (2026-09-30)
- Swipe-player §9 slice set complete + live-verified: theme tokens, player controls,
  single-progress fix, description+tags display, auto-hide clears all text but the progress
  line, right action rail (like / mute / share / overflow quick sheet), double-tap like +
  heart pop, playback speed 0.5–2× in overflow, error Retry/Skip overlay, auto-swipe toggle
  (reduced-motion aware, data-saver forced off, prefs-persisted), adjacent-item prefetch via
  Media3 DefaultPreloadManager (prepare-only — no data burn).
- Live like-write drift fixed: PUT/DELETE /v2/gifs/{id}/like need JSON body
  {context:trending, source:watchlist, position} + Json encodeDefaults=true (all-default
  @Body DTOs serialized to {} otherwise); Retrofit DELETE+body needs @HTTP(hasBody=true).
  Pinch zoom resets on swipe now (PLAN §9 revision 2026-09-30).
