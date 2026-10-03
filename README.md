# Giffy Viewer

Unofficial client for a public GIF-hosting site, for Android — a pure viewer and gif
organizer. Browse trending/explore feeds, watch on the phone or the TV, and organize with
labels, favorites, feeds, and filters. Works signed-out (in-app-only features) or signed
in to your own account on that site.

> **Disclaimer:** Giffy Viewer shows adult content and is intended for users 18 or older.
> It is not affiliated with, endorsed by, or connected to the sites it browses in any
> way. All content is streamed from those sites' public API hosts; nothing is scraped,
> rehosted, or redistributed.

| | |
|---|---|
| Latest release | [v0.2.0](https://github.com/RjBiermann/super-bassoon/releases) |
| Downloads | APKs from GitHub Releases (mobile + TV, R8-shrunk, signed) |
| Requirements | Android 7.0+ (mobile) / Android 8.0+ (TV) |
| Age gate | 18+ confirmation required on first launch (both apps) |

## Features

**Mobile**
- Trending, Explore, Following, tag/niche/creator feeds with per-feed sorting and filters (HD, duration, orientation)
- Swipe player (TikTok-style vertical feed) with double-tap like, speed control, hold-to-2×, pinch zoom, auto-swipe, resume
- Inline feed autoplay on settled tiles
- Favorites, custom feeds (tags/creators), collections, blocked/neutral feed states
- Search with four scopes: GIFs · Images · Creators · Niches
- Content filter choke point — blocked creators/tags/groups never render
- Settings: data saver, autoplay, grid columns, backup export/import of preferences
- Sign in via in-app WebView (OAuth/PKCE), or paste a token on TV

**TV**
- D-pad-first home with Trending / Explore / Continue Watching rows
- Full player with next/prev, resume, quick-actions panel (hold Center or MENU)
- Preview-on-focus playback on focused cards
- Same feed, filter, favorites, and player stack as mobile (shared modules)

**Shared**
- Offline-first: cache renders on airplane-mode cold start with zero network
- Watch history with resume positions
- Silent token refresh, rate limiting (≤10 requests / 5s) with circuit breaker
- No analytics, no ads, no trackers, no Firebase — nothing phones home

## Build

```bash
./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug
```

Release builds are tag-driven in CI (`-PversionTag=v0.3.0`) and require signing secrets —
see `.github/workflows/release.yml`.

### Modules

```
:app-mobile / :app-tv        thin platform shells (UI + navigation only)
:core:model :core:network    DTOs, Retrofit/OkHttp, rate limiter, circuit breaker
:core:auth :core:database    token storage (EncryptedSharedPreferences), Room cache
:core:datastore :core:player DataStore prefs, ExoPlayer + SimpleCache
:core:ui                     shared theme, player surface, common components
:feature:feed :feature:search :feature:favorites
:feature:auth :feature:settings
```

All UI/logic lives in `:core:*` / `:feature:*`; the app modules are shells only, so a
future desktop/web target is a new shell, not new screens.

## Tech stack

Kotlin 2.x · Jetpack Compose (Material 3) · tv-material for TV · Retrofit + OkHttp ·
Room + Paging 3 · Hilt · Coil · Media3/ExoPlayer + SimpleCache · kotlinx-serialization ·
security-crypto · version catalog · ktlint + detekt gate the build.

## Project docs

Detailed specs and engineering notes live in the `AGENTS*.md` files at repo root
(network policy, database schema, auth, player, content filter, UI patterns). Endpoint
hostnames in those docs and in source are stored encoded, never as plaintext.

## Verification recipe

1. **Airplane-mode cold start** — cache renders, zero network, no crash.
2. **Login survives process death** — sign in, force-stop, relaunch: still signed in.
3. **Logout revokes** — tokens are revoked server-side, cookies cleared.
4. **Filter leak-zero** — blocked items never appear on any surface.
5. **Backup round-trip** — export settings, fresh install, import: identical prefs.

## License

GPLv3 — full text in [`LICENSE`](LICENSE).

- DM Sans font ships under the SIL Open Font License
  (`core/ui/src/main/res/raw/ofl_dm_sans.txt`).
- This project is an independent, unofficial client. It requests only what its features
  need (public feed/search endpoints and your own account's write endpoints when signed
  in — likes, follows, collections).

## Status

Actively developed; releases are cut by tag from GitHub Actions. Issues and PRs welcome —
please read the non-negotiables in `AGENTS.md` first (viewer-only, no ads, no upstream
branding, no trackers).
