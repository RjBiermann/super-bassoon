# Giffy Viewer — Agent Guidance (root)

Unofficial client for a public GIF-hosting site. Pure viewer and gif organizer — browsing/viewing and
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
- **Testing policy (2026-10, supersedes the old one-minimal-test rule — every kind of
  test, small to big, is in scope; no suite implemented yet, policy first):**
  1. **Unit (JVM)** — pure logic, ViewModels/state (StateFlow/UiState with fakes),
     rate limiter/breaker/filter invariants. JUnit 4; MockWebServer for API/DTO paths;
     Robolectric only where Android deps are unavoidable.
  2. **Compose UI component tests** — `createComposeRule()` for shared widgets with
     interaction/visual behavior worth pinning (GiffyScaffold, focus/fill states, chips).
  3. **Instrumented tests** (`src/androidTest`) — Espresso + Compose test per shell
     (mobile + TV): launch, navigation smoke, player open/play/pause.
  4. **Integration/e2e** — full flows end to end on emulators; the manual device-gate
     recipes (giffy-device-gate skill) become automated where economical.
  5. **Manual device gates stay** for what automation can't judge: focus rings,
     10-foot feel, visual regressions.
  New non-trivial logic ships with a test at the lowest rung that covers it — no
  logic lands untested. Instrumented/e2e run locally on the emulator (RAM rule:
  one heavy operation at a time); CI runs the JVM rungs via `check` until a
  CI emulator lane exists.
- **TDD (test-driven development) is the default development loop:** for any new
  logic or bug fix, write the failing test first, watch it fail, implement the
  minimum to make it pass, then refactor. UI/device work follows the same shape:
  capture the expected state (XML dump / probe) first where possible. `check`
  green is the merge gate; device gates come after green.
- rate-limit invariant: ≤15 requests in any rolling 5s window (retuned 2026-10-03 to the browser-like budget after the live-probe session; browser identity policy in AGENTS-NETWORK.md) — own change must not break it.

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
8. Testing-policy suite green (JVM rungs via `check`; instrumented/e2e on device).

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


## Release policy (tags & releases)
- Tags are cut ONLY via the `cut-release.yml` workflow_dispatch: it computes the next vX.Y.Z
  from existing tags, asserts the packed-semver versionCode (maj*10000+min*100+pat, mirroring
  `-PversionTag`) strictly increases, and rejects any tag/release collision (local, origin,
  GitHub release), then creates + pushes the tag WITHOUT force.
- `cut-release.yml` chains the release itself: it verifies the tag exists on the remote and
  dispatches `release.yml --ref <tag>` (extended to `actions: write`). This is the working
  norm since v0.5.1 — a bare `GITHUB_TOKEN` tag push does NOT fire `release.yml`'s push trigger.
- `release.yml` guards: the triggering tag must point at default-branch HEAD (kills the
  silent-release-of-stale-commit class; `skip_head_guard` dispatch input exists for manual runs only).
- Never move an existing tag — published release assets are immutable (v0.3.0 incident).
  Local `jj tag set` / `--allow-move` for tags is retired for pushing.
- Manual escape hatch: only while CI is down, and only after verifying with
  `git tag --list 'v*'` + `gh release list` that the target tag is free.
- Release signing key/cert is reuse-forever; keystore material lives in gh secrets
  (SIGNING_KEY/SIGNING_PASS), never in source.

## Session conventions
- Signed-in device verification via the DebugSignIn debug-gate seam ONLY (launch extra
  `giffy_debug_bundle`, BuildConfig.DEBUG-only, token never in source). Fresh user token
  bundles REJECT manual curl from this host (BadTokenSignature — session-bound signature);
  the old id_token-curl convention is dead.
- User-pasted token bundles live at /tmp/bundle.json (0600) and are deleted after the queued
  signed-in gates.
- Per-batch gate recital: compile + ktlint + detekt + unit tests green on all touched modules
  (assembleDebug both apps for cross-module sweeps); 0-FATAL expectation on device passes.
- jj: set `jj config user.name/email` BEFORE creating commits (no-author commits reject push;
  recover via `jj new <commit> && jj squash --from <commit> --into @`); jj workspaces snapshot
  lazily — run `jj st` inside a workspace before using it as a bookmark target; after merging
  a lane bookmark, `jj new main` so @ actually contains the work before device-testing
  (assembleDebug builds the working copy).
- Theme is dark-only; dynamic color retired (light theme = new design, rejected — no light
  palette exists to borrow; see AGENTS-APP.md design-system audit).
- API write-shape rules (promoted from batch logs): like/follow writes PUT/DELETE need a JSON
  body ({context, source, position}) + `Json encodeDefaults=true` (all-default @Body DTOs
  serialize to `{}` otherwise); Retrofit DELETE+body needs `@HTTP(hasBody=true)`.

## Device-input lessons
- `adb shell input keyevent -t <ms>` is NOT a valid flag — use `--duration <ms>` for held
  keys; on some SDK-34 AVDs neither works — use `input keycombination -t <ms> <key> <key>` for holds.
- Rapid multi-key `input keyevent` chains in one shell string drop keys — one key per command.
- uiautomator dumps lag actual state after navigation and go text-empty while the PlayerView
  interop is foreground — screencap is the state evidence.
- Screencap coords are native (e.g. 1080x2340) while rendered readers scale them — `input tap`
  needs ORIGINAL coords.
- Black video + advancing progress (progress bar/countdown move, MediaCodec active) = AVD
  decoder wedge until proven otherwise — discriminate via screenrecord size (MBs = content,
  KBs = black) + one process restart; never open a player regression from this signature alone.

## Open items (current truth)
- §8 walk CLOSED 2026-10-18 (derived from surviving §8.x assertions; original 15-step list
  lost with PLAN.md — R8-build variant still owed, fresh-PIN demand unexercised). Mobile
  niche-preview-tiles device gate CLOSED same batch.
- TV quick-action panel rows measure ≈37.5dp visual height vs the 48dp M3 guideline — decision
  owed (D-pad-first surface; bump only if touch-on-TV or a strict-a11y ask lands).
- Dependabot dependency-submission noise unresolved (auto-submitted gradle tooling deps make
  "Dependabot Updates" runs red; harmless to build/release; silenced-by-config or
  alert-dismissal needs a decision).
- Followers page blocked (row shape unprovable — `v2/me/followers` items=[], no-guess rule).
- Report unprobeable.
- Server search-history sync + niches suggest: spec'd-not-scheduled fallbacks (niches suggest
  additionally user-token-gated — 401 with the anon token; context param name never recorded).
- Mobile niche-preview-tiles device gate CLOSED 2026-10-18 (doc line was stale).
- Issue stream 2026-10-22 (batch 40): #52 #53 #56 #57 #58 CLOSED with evidence comments (main @ 4c106655);
  #54 CLOSED 2026-10-23 (mobile signed-in gate PASS: item-wise interleave, refresh re-seed, endless paging);
  #55 NO-REPRO on emulator, awaiting reporter device details.
- Search scopes have no visible failure state while unavailable (silent `runCatching` UX hole,
  surfaced by the #57 fix; building one is a design decision, not owed by any issue).
- TV quick-actions MENU-open focus anomaly: one session saw D-pad unable to enter the panel
  (touch taps also didn't toggle; prior gates passed the same path) — one focused re-check owed.
- Minimap player / two-column expanded / RateLimitBus indicator: decided skips (documented
  cost reasons).
- #62 (favorites fan-out) + #63 (feed error honesty) CLOSED 2026-10-23 with device evidence (main @ 2e3e7311).
  #62: bounded 5-creator-per-fill fan-out with a rotating window, `perCreator` window-independent,
  favorites cache generation `fav:v3` (prefs identity pinned at `fav:v1`). #63: honest error kinds,
  mobile feed `AutoRetryEffect`, breaker reset on reconnect. Honest ends not exercised on device:
  #62's true end-of-pool (still chaining at p9) and #63's TV copy (blocked by the TV issue below).
- TV offline cold start deadlocks in the auth interceptor (#64, pre-existing; found by the #63 TV gate):
  all 5 OkHttp threads park in `runBlocking { authToken() }` (`NetworkModule.kt:88`) re-entering the
  AnonymousSession mutex, so rows never leave `LoadState.Loading` and no error copy can render. Needs a
  design pass (token resolved off the interceptor chain, or its own executor + timeout).
- ~~`FeedViewModel` `pendingForce` mismatch~~ FIXED 2026-10-23 with #65/#66: `setSort` records
  `source.sortForceKey(sort)` = `withSort(sort).keyBase`, the key the gifs flow reads (~:134);
  pinned by `SortForceKeyTest`.

Per-batch session history: docs/SESSION-LOG.md.
