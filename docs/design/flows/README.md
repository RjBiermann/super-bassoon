# Giffy Viewer — Flow diagrams

Mermaid sources for every app flow, each derived **from real code** (source files are
listed in the `%%` header of every diagram). Keep them in sync when the owning code
changes — a diagram that drifts is worse than no diagram.

Render with any Mermaid viewer (`mermaid-cli`, IDE plugin, GitHub markdown embed) and
keep the syntax simple: `flowchart TD/LR`, `stateDiagram-v2`, `sequenceDiagram`,
quoted labels only.

| File | Flow | Sources (summary) |
|---|---|---|
| [`mobile-navigation.mmd`](mobile-navigation.mmd) | Mobile nav graph: app gates → routes → BACK semantics, pop-up-inclusive player exits | `app-mobile/.../MainActivity.kt`, `feature/feed/FeedViewModel.kt`, `core/ui/PinLock.kt`, `core/ui/AgeGate.kt` |
| [`tv-navigation.mmd`](tv-navigation.mmd) | TV nav graph: D-pad-first home (pills + More ▾), window-payload player, entry-replacing exits | `app-tv/.../TvMainActivity.kt`, `app-tv/.../TvHomeScreen.kt`, `app-tv/.../TvPlayerScreen.kt` |
| [`auth-login.mmd`](auth-login.mmd) | Sign-in/out: WebView PKCE login, token storage, 401 refresh, debug sign-in, cookie wipe | `feature/auth/AuthSection.kt`, `AuthViewModel.kt`, `WebViewLoginScreen.kt`, `DebugSignIn.kt`, `core/auth/TokenStore.kt`, `core/network/NetworkModule.kt` |
| [`data-flow.mmd`](data-flow.mmd) | Data pipeline: OkHttp → FeedPageFetcher → Room → read-time filter chain → Paging → UI, incl. TTL/stale-while-revalidate and the skipped stage 0 | `FeedViewModel.kt`, `FeedRepository.kt`, `FeedMediator.kt`, `FeedPageFetcher.kt`, `FeedPagingSource.kt`, `ForYouNetworkPagingSource.kt`, `LikedNetworkPagingSource.kt`, `core/database/ContentFilter.kt`, `core/network/NetworkModule.kt` |
| [`rate-limit-breaker.mmd`](rate-limit-breaker.mmd) | Request path: rolling-window limiter → 429 cooldown → retry/backoff → circuit breaker; image-client split | `core/network/rate/RateLimiter.kt`, `RetryInterceptor.kt`, `CircuitBreaker.kt`, `core/network/NetworkModule.kt` |
| [`pinlock-agegate.mmd`](pinlock-agegate.mmd) | App gates: PIN lock (retry slow-down) + age gate, host-wiring differences between shells | `core/ui/PinLock.kt`, `core/ui/AgeGate.kt`, both shell `MainActivity`/`TvMainActivity` hosts |
| [`player-state-machine.mmd`](player-state-machine.mmd) | Player states: open/prepare/play/pause/ended/error, auto-swipe + pool-end, controls visibility, close semantics | `feature/feed/PlayerScreen.kt`, `core/player/GiffyPlayer.kt`, `app-mobile/src/androidTest/.../PlayerScreenMachineTest.kt` |
| [`search-flow.mmd`](search-flow.mmd) | Search: debounced suggest, explore sections, scope tabs (leak-zero), history, submit → results feed | `feature/search/SearchViewModel.kt`, `SearchScreen.kt`, both shell nav hosts |
| [`following-flow.mmd`](following-flow.mmd) | Following page: logged-out hidden, paged `v2/me/following` + `v2/niches/following`, retry, entries → creator/niche feeds | `feature/feed/FollowingScreen.kt`, `FollowingViewModel.kt`, mobile route + TV home row |
| [`collections-favorites-flow.mmd`](collections-favorites-flow.mmd) | Saved Collections management (create/rename/delete via NameDialog) + quick-sheet writes: like/favorite/follow/add-to (JSON-body PUT/DELETE) | `feature/feed/CollectionsScreen.kt`, `CollectionsViewModel.kt`, `QuickSheet.kt`, `FeedViewModel.kt`, `core/network/GifsApi.kt` |
| [`custom-feeds-flow.mmd`](custom-feeds-flow.mmd) | Custom feeds: builder (name + creator/tag/niche refs), state cycle FAVORITED→BLOCKED→NEUTRAL, delete evicts cached pages, `Feature.CUSTOM_FEEDS` gating | `feature/feed/CustomFeedsScreen.kt`, `core/ui/Feature.kt`, `app-tv/TvMainActivity.kt`, `feature/feed/FeedTabStrip.kt` |
| [`niches-flow.mmd`](niches-flow.mmd) | Niches: preview-tile listing (leak-zero rows), category chips, five sorts, auto-retry (#49), join/leave with rollback, About page, pin | `feature/feed/NichesScreen.kt`, `NichesViewModel.kt`, `NicheJoinViewModel.kt`, `NicheAboutScreen.kt` |
| [`settings-backup-flow.mmd`](settings-backup-flow.mmd) | Settings backup: SAF export/import of the giffy-prefs v3 JSON, merge-upsert round-trip (gate 7) | `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `core/database/ContentPrefsBackup.kt` |
| [`offline-flow.mmd`](offline-flow.mmd) | Offline: NetworkMonitor truth → banner mounts per shell; airplane-mode cold start reverts seeded For You to the cached Trending grid (gate 3) | `core/ui/NetworkMonitor.kt`, `OfflineBanner.kt`, `feature/feed/FeedViewModel.kt`, both shell hosts |
| [`tv-player-quick-actions.mmd`](tv-player-quick-actions.mmd) | TV player: window-payload entry, hidden/visible keymap, auto-swipe + end popup, quick-actions panel (like/mute/speed/favorite/block/follow/pin/add-to), entry-replacing exits | `app-tv/TvPlayerScreen.kt`, `TvQuickActions.kt`, `TvMainActivity.kt` |

## Deliberate omissions / verified gaps

- **`watch-history.mmd` — no diagram, BY DESIGN.** Watch history / "Continue
  Watching" / resume was **removed 2026-10** (user decision): `watch_history`
  dropped in `MIGRATION_10_11` (DB v11), both players start every gif at 0, and
  no surface reads played positions anymore (see `AGENTS-DATABASE.md`,
  `AGENTS-PLAYER.md`). A removed feature gets no flow diagram — the removal is
  annotated where it matters (`player-state-machine.mmd`,
  `tv-player-quick-actions.mmd`).

- **`auth-tv-paste-token.mmd` — SKIPPED, no file.** The TV paste-token fallback was
  **removed 2026-10** (user decision, recorded in `feature/auth/AuthSection.kt`:
  "WebView PKCE is the only sign-in path"); no TV paste-token UI exists anywhere, only
  `TokenStore.save` + the shape-tolerant bundle parse it used to share (now exercised
  by `DebugSignIn`, debug builds only). Removed flows don't belong in this visual set.
- **Stage 0 promoted-drop is spec'd-not-scheduled.** `AGENTS.md` says promoted GIFs are
  dropped in filter stage 0, but no code implements the drop (verified 2026-10-23 by
  grep across all modules incl. tests: only `GifDto.promoted` and the
  `LiveSmokeTest` probe exist, and the probe asserts **0 promoted items in public
  feeds** — see `data-flow.mmd`'s annotated node). Re-check if the server ever starts
  mixing promoted items.

## Invariants every diagram respects

- **Leak-zero**: a gif `ContentFilter.allow` rejects never renders, on any surface
  (`data-flow.mmd`, `search-flow.mmd`).
- **Rate-limit invariant**: ≤15 requests in any rolling 5s window, retries included
  (`rate-limit-breaker.mmd`).
- **Cache-first**: Room is the source of truth; cached feeds render offline
  (`data-flow.mmd`, airplane-mode gate 3).
- **Gates are app-level, not page-level**: PIN + age gate sit outside both NavHosts
  (`pinlock-agegate.mmd`).
