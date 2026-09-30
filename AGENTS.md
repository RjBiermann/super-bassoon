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
- Emulator cold-start first fetch: wait 45s+ after Enter before judging the grid empty.

## Phase 6 progress (2026-09)
- TV app live: age gate → home (Trending/Discover/Continue Watching rows, tv-material Cards,
  D-pad) → player with D-pad next/prev + resume; paste-token Account screen reuses
  feature:auth AuthScreen. `watch_history` write verified end-to-end on TV36.
- Still open (later slices): PIN pad optional lock, settings screen (cache size/data-saver),
  Top This Week + Favorites + group rows (Phase 7 features), focus polish/scaling.

## Phase 7 progress (2026-09)
- Content filter (slice 1), Settings screen (slice 2), UX polish + TV Settings + data
  saver (slice 3), Favorite creator (slice 4), Preferences backup import/export (gate 7
  closed, live round-trip verified on Phone34). See AGENTS-CONTENT-FILTER.md / AGENTS-APP.md.
- Still open: TV quick-actions (favorite/block from TV), favorite tag, For You blend,
  niche groups, collections, hide-count stats, Top This Week row, WebView OAuth.
