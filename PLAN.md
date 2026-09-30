# Giffy Viewer — Unofficial upstream Client: Coding-Agent Build Plan

You are building **Giffy Viewer**, an unofficial Android client for upstream.com, distributed exclusively via GitHub Releases. Follow this spec exactly. Where the API has drifted, adapt **endpoint paths only** — never the architecture, rules, or features.

---

## 0. Ground Rules (non-negotiable)

- **PURE VIEWER ONLY.** No scraping, no rehosting, no deep-linking beyond share features, no media redistribution.
- **No upstream trademark, name, or logo** in branding. App name: **Giffy Viewer**. README + splash state "not affiliated, endorsed, or connected with upstream; adults only; users responsible for local legality."
- **No analytics, ads, trackers, Firebase, or crash reporting.**
- **No ads anywhere — hard rule.** No ad SDKs, no banner/interstitial/rewarded units, no sponsored placements, no affiliate links, no "buy premium" upsell surfaces. Every feed item shown must come from a real API response routed through ContentFilter (§6); nothing rendered comes from an ad network or paid-placement endpoint. **`promoted` gifs dropped in filter stage 0** — paid items inside genuine API responses don't render either
- Legality posture internalized: no API keys exist for third parties — this client uses the same undocumented endpoints the site's own front-end uses, with respectful rate limiting.
- **No TODOs, no placeholder code, no stubs.** Every shipped file compiles and functions.
- Prefer **verifying against the live site** during Phase 2 rather than assuming.

---

## 1. Project Structure

Multi-module Gradle, Kotlin 2.x, version catalog (`libs.versions.toml`), ktlint + detekt wired to fail build on violations. LeakCanary wired `debugImplementation` only in both app modules (debug-build memory-leak watchdog; never ships in release, fully local, no phone-home).

:core:model        — pure domain models + UiState types
:core:network      — Retrofit/OkHttp, rate limiter, DTOs, ContentFilter choke point
:core:auth         — WebView login flow, token storage, revocation
:core:database     — Room ("giffy.db"), migrations
:core:datastore    — per-feed filter/sort prefs, age gate, settings
:core:player       — Media3/ExoPlayer + SimpleCache
:core:ui           — shared Compose components
:feature:feed      — feeds, groups, For You, custom feeds, sort/shuffle
:feature:search    — search + history
:feature:favorites — favorites, likes feed, followed creators/niches, collections
:feature:auth      — login UI, token-reveal screen
:feature:settings  — settings, content controls, import/export
:app-mobile        — Compose, Material 3, minSdk 24
:app-tv            — androidx.tv, minSdk 26
Target SDK 35. Stack: Retrofit/OkHttp, Room + Paging 3, Hilt, Coil, kotlinx-serialization (`ignoreUnknownKeys=true`), security-crypto (EncryptedSharedPreferences, key alias `giffy_auth`). No WorkManager — background refresh is simple silent network-on-open (stale-while-revalidate), no scheduled jobs planned.

Agent coding rules: no GlobalScope, structured concurrency, immutable UiStates, Room always via explicit migrations, original (self-made) app icon, edge-to-edge mobile, 5% overscan margins on TV, stable `key = gifId` on every lazy list/grid item (Compose item-identity guideline). **Backup/security config:** `allowBackup=false` (or `dataExtractionRules` / `fullBackupContent` excluding the `giffy_auth` prefs file + token storage) in both app modules — Android cloud/adb backup must never carry the refresh token or token store off-device

---

## 2. API & Auth

Base URL: `https://upstream-api-host.example/`. No public API exists. Known endpoints (verify live in Phase 2):

- `GET /v2/auth/temporary` → anonymous browsing token (verified live 2026)
- ~~`POST /v2/auth` with `authkey`~~ — **dead (404 live)**. Login is now OAuth via `upstream-auth-host.example` (Kinde-issued JWTs used directly as `Authorization: Bearer` against upstream-api-host.example; **not** sender-bound to IP/UA, unlike the old authkey tokens).
- ~~`DELETE /v2/auth`~~ — dead; revocation TBD via OAuth client flow.
- `GET /v2/gifs/{id}`, `GET /v2/gifs/search` (tags, `order`, count params), `/v2/feeds/trending/...` and top endpoints (accept period params: day/week/month/year/all). **Trending variations (verified 2026):** `/v2/feeds/trending/popular` and `/established` — feed picker should surface both.

**✅ Verified anonymously accessible 2026 (temp token, all 200):**
- `GET /v2/feeds/modules` — home-page slot layout (boost · related-tags · trending-niches · live-cam · only-fans). We render only the content modules: related-tags + trending-niches; skip boost/live-cam/only-fans (§0 no-ads rule).
- `GET /v2/niches/categories` — 18 category names for niche browsing
- `GET /v2/creators/search?query&count` — creator search: name, follower/gifs/publishedCollections counts
- `GET /v2/creators/verified?count` — verified-creator list
- `GET /v2/search/suggest?query` — suggestions as `{type: tag|…, text, gifs}` (tag + count — powers search autocomplete with counts)
- `GET /v1/tags/match?query` — exact tag matching
- `GET /v2/users/{username}/collections` — a creator's public collections, **works anonymously**
- Gif object carries `urls{hd, sd, silent, poster, thumbnail}`, `hls: true`, `hasAudio`, `promoted` — quality picker + audio badge + mute rendition confirmed feasible; HLS per rendition `v2/gifs/{id}/{quality}.m3u8`

**Logged-in-only (✅ verified 2026 with user ID token — read-only probes):**
- `GET /v2/feeds/for-you` — **server-side For You feed exists**, returns full gif objects (same schema as search). When logged in, use it (filtered through ContentFilter) instead of the client-side blend; client-side blend stays the anonymous fallback.
- `GET /v2/me/collections` — `{collections, users, page, pages, totalCount}`; create/update/delete via `/v2/me/collections/{id}`, add gifs via `/v2/me/collections/{id}/gifs` (write shapes from web bundle, verify in Phase 2)
- `GET /v2/niches/my` — `{page, pages, total, niches}` (owned niches; viewer uses `/v2/niches/following`)
- `GET /v2/search/user-history` — array of `{id, value, type: user|free_type, account?}` — server-side search history; individual delete `DELETE /{id}` (bundle)
- `GET /v2/me/following` — `{items: [full creator objects: followers, gifs, description, profileImageUrl…]}` — richer than the v1 username array; use this for the Followed screen
- `GET /v2/niches/following` — `{page, pages, total, niches[{id, name, gifs, subscribers, tags, preferences, thumbnail}]}`
- `v2/pins` — **POST/PUT only** (405 on GET): pin/save gifs; list-read shape still TBD (verify in Phase 2 — possibly per-pin GET or response of the PUT)
- ~~`GET /v2/me/settings`~~ — 404 live; account settings are Kinde-side, not API-side

**⚠️ Token kind matters (verified 2026):** the API accepts the Kinde **ID token** (payload: `email`, `preferred_username`, `at_hash`); the Kinde **access token** (payload: `scp`, `aud: []`) is rejected with `BadTokenFormat: must be a JWT with type=bearer`. WebView capture (§Phase 5) must grab the **ID token**. ID tokens expire ~1h after issue — refresh path critical.

**Full API surface from web bundle (2026) — viewer-relevant, verify in Phase 2:**
- `v2/gallery`, `v2/gallery/{id}`, `v2/gallery/{id}/status` — gifs can belong to galleries
- `v2/search/user-history` (+ `DELETE /{id}`) — server-side search history sync (optional borrow)
- `v2/recommend/gifs/{id}/quick`, `v2/recommend/gifs/{id}/reddit`, `v2/recommend/tags/{gifId}` — "more like this" suggestions for watch page
- `v2/niches/{id}/related`, `/top-creators`, `/trending/{x}`, `/suggest`, `/suggest/adding` — niche detail enrichment
- `v2/users/{username}/search` — search scoped to one creator's content
- `v2/gifs/{id}/check-sound`, `v2/gifs/{id}/niches` — audio check, gifs-per-niche
- `v1/gifs/{gifId}/report-content` — report flow (write; optional, user-safety only)
- `v2/me/content`, `v2/me/followers`, `v2/me/following`, `v2/me/settings` (+ `/age-gate`) — account self-view/settings

**Not borrowed (creator tools / ads / telemetry — §0 rules):** `v2/adv/*`, `v2/boost/*`, `v2/cameron/streamate/*` (live cams), `v2/only-fans` modules, `v2/premium/*`, `v2/upload*`/`v2/gifs/submit*`, `v2/verification/*`, `v2/events/track` (telemetry — no-trackers rule), `v2/csat/*`, `v2/experiments/*`, `v2/analytics`, `v2/metrics/*`, `v2/announcements/*`, `v2/auth/clients/*` (client registration is not ours), upload pages.

**Account actions (✅ verified live end-to-end 2026, writes reverted after test):**
- `PUT /v2/gifs/{id}/like` → 202 · `DELETE /v2/gifs/{id}/like` → 202 · `GET /v2/likes` (array of gif ids) · `GET /v2/feeds/liked?page&count&type` (count 10–100)
- `PUT /v1/me/follows/{username}` → 204 · `DELETE /v1/me/follows/{username}` → 204 · `GET /v1/me/follows` (username array) · `GET /v1/me/followers/populated` (**v1**, not v2)
- `POST /v2/niches/{id}/subscription` → 202 · `DELETE /v2/niches/{id}/subscription` → 202 · `GET /v2/niches/following`
- **All writes require `Content-Type: application/json` + JSON body** (e.g. `{"context":"trending","source":"feed","source_id":"home","position":0}`; `{}` accepted for subscription). Empty bodies → 400.
- Unverified reads: `GET /v2/user_profile` (404 live — params TBD) — also probe `/v1/users/{username}` / `/v2/users/{username}` naming variants in Phase 2; `GET /v2/me/collections`. Fallbacks (decide in Phase 2, not Phase 7): profile endpoint stays dead → creator view = `GET /v2/gifs/search` filtered by username; collections endpoint stays dead → drop server collections, keep only local `collections` tables (§6).
- Non-goals for writes: upload (`v2/upload*`, `v2/gifs/submit`), verification, premium — viewer stays read-only beyond like/follow/subscribe.

Feature additions from this: **Liked feed** (`GET /v2/feeds/liked`, network-backed, logged-in only) and **Followed creators + subscribed niches** (browse lists, also blend into Favorites/For You). Like/follow buttons live in the long-press quick sheet and creator profile. **Collections:** server-backed if `GET /v2/me/collections` verifies live (Phase 2); else local-only via §6 tables — same render surface either way.

**Logged-in surfaces (Phase 7):** Liked feed tab (mobile) / Liked row (TV) — fetched network-live per §5 no-cache rule, supports Unlike (§7); Followed screen — creators I follow (rich creator objects from `GET /v2/me/following`, verified) + niches I subscribe to (from `GET /v2/niches/following`), each entry → creator profile or niche feed (endpoint: see §7 niches, verify Phase 2); Collections screen — list of collections, entries → creator profiles. Followed and Collections are read-only viewers of server state; Liked additionally supports Unlike. Logged-out state hides these surfaces entirely (no dead tabs).

Auth flows (REVISED 2026 — sess cookie flow is dead, API drift):
- **Reality check:** the web login runs through `upstream-auth-host.example` (Kinde). The web app sends the Kinde **ID token** as Bearer (verified 2026; the access token is rejected — see token-kind note above). Tokens live in `localStorage.auth_data` / in-memory. There is now a real OAuth authorize flow: `POST /v2/oauth/code {client_id, scope, state, redirect_uri, response_type}` → `{url}` → user approves at `upstream.com/authorize` → redirect to client's `redirect_uri` with code. Verify the code-exchange endpoint (likely `POST /v2/oauth/token` or Kinde's `/oauth2/token`) and client registration before Phase 5.
- **Mobile (WebView-native, primary):** WebView loads `https://upstream-site.example/login`; after login the app captures the session from the web's own token store: **read `localStorage.auth_data`** — the full Kinde token response JSON (`id_token` + `access_token` + `refresh_token` + `expires_in`, shape verified live 2026 from the user's own session). Fallback if `auth_data` is absent: CookieManager + JS-injection shim for in-memory tokens, then OAuth-redirect intercept (legacy path). **No manual token typing on mobile.** Token-paste screen stays only as a last-resort fallback if capture fails live verification.
- **Token-reveal screen:** mobile shows the captured token so TV users can copy it. Reveal is confirm-first (button press; biometric/PIN when lock enabled), token masked until revealed.
- **TV:** Android TV WebView also supports the same login flow — `WebView` on TV renders the login page, D-pad navigable (focus/zoom on fields, on-screen keyboard via remote). Primary TV path = same WebView capture flow as mobile. Fallbacks if TV WebView login fails live verification: (a) LAN pairing — same Wi-Fi, mDNS discovery + local socket, TV displays pairing code, mobile pushes token; (b) import/export JSON (§6) includes the token for device migration. Manual paste stays as last-resort only. (No QR: TV has no camera and the payload is the token itself — a QR on either screen isn't scannable end-to-end.)
- **Logout:** clear WebView cookies + wipe token storage (no server revocation exists — `DELETE /v2/auth` dead); server session dies with token expiry.
- **Anonymous token expiry:** the temporary token also expires. On 401 while logged out → silently re-fetch `GET /v2/auth/temporary` (via rate limiter) and retry once; if that fails, surface session error and keep cached content readable offline. No user prompt.
- **User-token refresh (✅ refresh grant verified live 2026):** capture the `refresh_token` alongside the ID token at login (scope includes `offline` — confirmed issued). On expiry → `POST https://upstream-auth-host.example/oauth2/token` with form body `grant_type=refresh_token&client_id=e06c34dac7654821bcb37e0393b54350` — **verified 200, no client secret needed** (public SPA client from the bundle's `azp` claim), non-destructive (no rotation while the current token is unexpired; Kinde returns the same tokens). New ID token → session continues transparently. Trigger: timer every ~45 min while running + on-401 fallback, both via the rate limiter. Phase 5 must re-verify the post-expiry case (fresh ID token minted, rotation behavior) — that's the only untested leg. If refresh fails (revoked), clear state and route to re-login.
- **LAN pairing security:** pairing only over the app's own socket discovered via its unique mDNS service name (not a generic port scan); TV shows a 6-digit code, mobile must echo it back before the socket accepts the token push; token transmitted over the pair-only socket, wiped from memory on both ends after transfer; pairing session is one-shot — second push requires a fresh code.
- Login must survive process death; sign-out/re-login cycle must work end-to-end. **UI state also survives process death:** navigation backstack, active Home tab/chip, current swipe-player item + playhead, open search query restore via `rememberSaveable`/`SavedStateHandle`
- **Likes/follows/niche-subscriptions are server state** (not local): the app reads `GET /v2/likes`, `GET /v1/me/follows`, `GET /v2/niches/following` as the source of truth; local favorites remain a separate local concept.

---

- **Data-saver policy (one rule, no contradictions):** data-saver ON = grid/tile feeds render
**static thumbnails with tap-to-play** (no video in the grid, silent rendition preferred, HD→SD) —
the grid stays passive; the **swipe player and watch page are exempt** from autoplay-off — entering
them is an explicit user action, so they play (SD rendition per §5 quality override). Autoplay
toggle in Settings governs grid inline playback; data-saver forces it off but never blocks the swipe
player — playing a video you deliberately opened is not background data burn

## 3. Age Gate (blocking, first run)

- One-time full-screen attestation: "I am 18 or older — Enter" / "Exit (leaves app)".
- Gate is the navigation **start destination** until `age_confirmed_at` is set (stored in DataStore). Cannot be bypassed by back gesture; no content loads before confirmation.
- Optional lock: PIN + biometric (mobile: BiometricPrompt; TV: D-pad PIN pad). No recovery — clearing app data resets. **Re-arm policy:** the lock re-prompts whenever the app leaves the foreground and returns (not cold start only); the token-reveal screen always requires it fresh regardless
- Settings option to re-run/reset the gate status.

---

## 4. Network Layer & Rate Limiting

- OkHttp singleton: `maxRequestsPerHost=4`.
- **Token bucket:** 2 req/s sustained, burst 10 — hard invariant: never more than 10 requests in any rolling 5-second window. Test this.
- **429:** cooldown per `Retry-After`, minimum 5s, max `Retry-After + 30s`.
- **5xx:** backoff 1s/4s/15s with jitter, max 3 attempts.
- **Circuit breaker:** 3 consecutive failures → open 5 minutes.
- **Invariant scope:** every app-issued API call counts toward the ≤10/5s window — feed pages, debounced suggest keystrokes, HLS manifest + segment requests, auth refreshes alike; the acceptance test drives paging + player together in its real shape, not a synthetic single-endpoint loop
- **WebView traffic is exempt** from the limiter (user-driven, login-only, one session at a time) — do not route it through the token bucket; only app-issued API calls count toward the invariant.
- `RateLimitBus` (SharedFlow) publishes limiter state; UI surfaces a subtle "cooling down" indicator.

---

## 5. Cache-First Architecture

Room is the **single source of truth** with stale-while-revalidate; UI reads Room, a background refresh hits the API and upserts. DB name `giffy.db`.

Tables:
- `gifs` — metadata incl. **tags + username stored locally** (required for offline filtering)
- `feed_pages` — keyed like `"trend:v2:pop:p3"`, incl. extended variants
- `search_history` (cap 50 entries, oldest evicted; "clear search history" action in Settings), `watch_history` (resume positions, cap 1000 entries — oldest evicted on insert; **write policy:** record a view only after ≥3s of playback OR ≥30% played, whichever first — one row per gif, revisit updates position, never duplicates; skipped grid snaps never record), `pinned_ids` (local mirror of server pins — write-through, renders offline; see §9), `liked_ids` (local mirror of server likes — write-through on like/unlike, refreshed from `/v2/likes` when online; tiny ID list, so liked badges + Liked feed skeleton render offline), `tags` (autocomplete source, TTL 7d). Favorites are network-backed — no Room cache; Room holds only gifs metadata so already-liked state renders offline.

TTL policy: tags 7d · trending/top 10 min · search 1h · gif metadata 24h · favorites never cached.

- **Video quality control:** user-chosen preferred quality — Auto · 1080p · 720p · SD (data-saver) · Lowest — applied at player track-selection: pick the rendition variant matching or just under the chosen height (Auto = adaptive, default). Persisted in DataStore (global), Settings screen picker. **Confirmed by 2026 scan:** gif object carries `urls.hd` / `urls.sd` + `urls.silent` (mute) + `hls` flag; per-rendition HLS at `v2/gifs/{id}/{quality}.m3u8`. Cache-fill follows the selected rendition (same SimpleCache key by gif ID; a quality switch after cache fill re-streams the chosen variant — one cache entry per gif+quality boundary). Data-saver toggle (§5) remains as the one-tap override that forces SD regardless of the quality picker.

- **Expiring media URLs vs 24h metadata cache:** `urls.*` are tokenized and expire well before the 24h TTL — cached rows go stale while metadata stays useful. On player 403/fatal-load-error: re-fetch `GET /v2/gifs/{id}` (rate-limited), upsert fresh row, retry playback once; still failing → player error state (§9). Never pre-emptively refresh URLs (failure-driven only, respects the rate-limit invariant)
- **HLS→MP4 fallback:** player tries HLS first; on manifest/load failure fall back to the direct MP4 rendition (`urls.hd`/`urls.sd`) before surfacing the error state — `hls: true` is per-gif, not guaranteed on every item
- **Video cache bounds:** SimpleCache with LRU eviction — max 512MB mobile / 1GB TV (constants, not DataStore-configurable); Coil image disk cache separately capped (256MB); both figures shown in Settings (+ TV) — "Cache: 412 MB of 512 MB" + a **Clear cache** button that empties both (confirm-first). Keep the acceptance gate: airplane-mode cold start renders from cache with no crash and zero network calls

Offline: airplane-mode cold start renders from cache with no crash and zero network calls.

---

## 6. Content Controls (local-only, fully offline-capable)

### Preferences
| Feature | Effect |
|---|---|
| Block creator | Hide all their gifs everywhere. Two ways to add: quick sheet / profile actions, or typed/searched manually in Settings (creator editor: username input + current block list with remove). Case-insensitive exact username match |
| Favorite creator | Dedicated feed + 70% weight in For You |
| Block tag | Hide gifs containing that tag (exact match). Two ways to add: quick sheet ("Block tag") / "Block all tags on this gif", or typed manually in Settings, sharing the same tag editor as niche groups (text input + current block list with remove) |
| Block keyword | Case-insensitive substring match over title/description/tags. Added two ways: long-press quick sheet on a gif, or typed manually in Settings (keyword editor: text input + current block list with remove) |
| Niche groups | User-defined tag bundles — full feeds + blockable macro-filters. Created from the Groups screen (new-group sheet), not the quick sheet |
| Collections | Organizational groupings of creators |
| Screenshots toggle | `FLAG_SECURE` on/off — blocks screenshots, screen recording, and the recents-task preview. Default off; persisted in DataStore (global); lives in Settings under Content controls |
| Grid columns | User-chosen column count for grids — 1 · 2 · 3 — for **mobile** and **tablet** each (two independent pickers). Default: 2 on phone-width, 3 on tablet+; the setting overrides the width-based default. Persisted in DataStore (global), Settings screen pickers, applied immediately to all feed grids (Paging handles re-layout, backstack unaffected) |

### Room tables
- `creator_prefs(username PK, state, favorited_at)`
- `tag_prefs(tag PK, state, blocked_at, favorited_at)`
- `keyword_blocks(pattern PK, blocked_at)`
- `niche_groups(id PK auto, name, tag_list ["a,b,c"], state)` — `state` ∈ BLOCKED | FAVORITED
- `collections(id, name, created_at)`, `collection_items(collection_id, username)` (local fallback storage; server-backed if §2 verification passes)
- `hide_counts(reason PK, week_start, count)` — rolling 7-day hidden-item counters (§6 pipeline)
- **ContentFilter scope includes watch-history reads:** Continue Watching row/player and any watch-history-derived surface run through the pipeline — a creator blocked after watching must not resurface from history (leak-zero has no exception for historic items)
- `custom_feeds(id PK auto, name, sources_json "[{type: creator|group|tag, ref: <id|username|tag>}...]"`, created_at)` — custom feed definitions (§7)
- `feed_prefs(feed_key, sort, date_range, duration_min/max, resolution, orientation, shuffle_seed)` — per-feed persistence

### ContentFilter pipeline (single choke point)
Every data source — Paging remote load, Room cache read, search, group feeds, shuffle pool, "Surprise me", custom/For You blends — runs items through **one** interceptor before UI:

0. **Promoted filter:** any GIF with `promoted: true` is dropped unconditionally (paid placements can arrive inside genuine feed responses; the §0 no-ads rule bans SDKs, this stage bans the items themselves). Does NOT increment hide counts — hiding ads isn't content blocking. Logged in or out, feed or search, no surface exempts
1. Creator block (exact username, O(1) map)
2. Blocked niche-group tag set (built once per session, invalidated on edit)
3. Tag exact match against `tag_prefs`
4. Keyword substring match

**Filter–Paging contract:** filters shrink pages (a 40-item page may render 12). Keep loading next pages while below the min visible count (grid page size 30 → over-fetch until satisfied or end-of-feed), so grids don't dead-end after one heavily-filtered page. Hide-count increment happens inside the filter, not the UI.

Track hide counts by reason → Settings shows "Hidden this week: 1,240 (912 by tags, 328 by keywords)". Counters: rolling 7-day, in `hide_counts(reason PK, week_start, count)` Room table overwritten per week. **Leak-zero invariant:** blocked items must never render on ANY surface.

### Import/Export
Settings → export/import all preferences + groups + collections as JSON (device migration). **Token transfer caveat:** the file carries the ID token + refresh token ONLY in explicit "transfer to TV" mode (user-initiated, confirm-first); routine preference export never includes tokens — the refresh token is long-lived and must not leak into a shareable file. Default device migration for TV = LAN pairing instead.

---

## 7. Feeds

Feed identity extends to cache keys: sort/range changes produce distinct page streams, client-side-only operations do not.

- **Trending / Discover / Top (day/week/month/year/all)** — default tabs; Trending picker: `popular` (default) · `established` (verified 2026 variations)
- **Niches:** browse via `/v2/niches/categories` → niche detail with `related` niches + `top-creators` (verified shape Phase 2); trending-niches slot from `/v2/feeds/modules` on home — rendered as the site's `trendingNiches` widget (verified live 2026-09-30: `#191919` panel, 12dp radius, horizontal niche cards). **Niche feed source (TBD Phase 2):** probe `GET /v2/niches/{id}/gifs` / search-by-niche-tags (`v2/tags/feed/{tag}` on the niche's tag list) — pick whichever verifies; niche feed is a standard filterable feed with its own cache key `niche:<id>:sort=<x>:p<n>`
- **Creator search:** `/v2/creators/search` + verified-creator list (`/v2/creators/verified`)
- **Search suggestions:** `/v2/search/suggest` typed autocomplete with tag + gif-count; tag matching `/v1/tags/match`
- **Liked feed** (logged-in): gifs the user liked on server, `GET /v2/feeds/liked`; network-live, never cached (§5). Unlike supported here (verified `DELETE /v2/gifs/{id}/like`); after unlike the item drops on next refresh (no local tombstone).
- **Favorites feed** — merged recency-ordered searches over favorited creators
- **Group feeds** (per niche group):
  - FAVORITED → pinned mobile tab / TV row; feeds For You
  - NEUTRAL → preview feed reachable from Groups screen, not pinned
  - BLOCKED → no feed; tags feed the global block set
  - Cached `group:<id>:sort=<x>:p<n>` (sort variants per §8); group feed revalidates when its tag list changes
- **For You** (mobile): when logged in — server feed `/v2/feeds/for-you` (verified exists, 401 anonymous) run through ContentFilter; **cached `foryou:p<n>` at trending TTL (10 min)** — personalized but not credential-sensitive content, short TTL keeps it fresh; **failover:** if the server feed 401s/errors (token race, drift), fall back to the client-side blend below until it recovers; when anonymous — client-side blend: 70% followed-creator posts (same merged recency-ordered search mechanism as the Favorites feed, over FOLLOWED creators instead) / 30% group tag-searches — the tag-feed pool round-robins across ALL FAVORITED groups (offsets independent per group), dedup by gif ID, minus everything blocked, through ContentFilter; degrades gracefully when one side exhausts.
- **Followed** (logged-in): creators I follow + niches I subscribe to → browsable list; entries jump to creator profile / niche feed. Backs 70% of For You.
- **Custom feed builder:** combine creators + groups + single tags into a named feed; stored in `custom_feeds` (§6); appears as mobile tab + TV row
- Groups screen (both platforms): pin-to-tabs toggle, tag autocomplete editor, cards for FAVORITED/NEUTRAL/BLOCKED states
- **Collections screen** (both platforms, logged-in): server-backed if `/v2/me/collections` verifies, else local §6 tables; collection detail = list of followed-creator profile links. **Browsing others:** creator profile shows their public collections (`/v2/users/{username}/collections`, verified anonymous-accessible)
- **Related gifs (watch page):** "more like this" from `/v2/recommend/gifs/{id}/quick` (fallback: `/reddit` variant, else same-tags search); related-tags chips from feeds-modules shape
- **Galleries:** if a gif belongs to a gallery (`gallery` field), show "part of gallery" chip → gallery feed `v2/gallery/{id}` (verify Phase 2; drop silently if dead)
- **Cache-key / sort consistency:** all server-backed feeds — default tabs AND group feeds — follow §8's distinct-key rule: `group:<id>:sort=<x>:p<n>`, creator feeds `creator:<username>:sort=<x>:p<n>`. Liked feed sorts and range chips are **client-side only** (its pages are network-live, never cached, so no cache keys exist).

---

## 8. Sorting, Shuffle & Ranges (per feed)

**Sorts:** Newest · Oldest · Most viewed · Top (day/week/month/year/all) · Longest/Shortest duration (client-side) · Creator A→Z (group/custom feeds). Server-backed sorts get distinct cache keys (`...:sort=top_week:p<n>`).

**Shuffle/random:**
- Per-session Fisher-Yates shuffle of the loaded pool, stable across recomposition, reshuffle action
- **Infinite shuffle player:** random unseen item from pool, loads more pages on exhaustion, seeded (seed shown, restorable)
- **"Surprise me":** random gif from any cache-eligible source, excluding watched (`watch_history`)

**Range chips per feed:** date range (today/week/month/year/custom pickers) · duration slider + chips (<10s, 10–30s, 30–60s, 1–5m, >5m) · resolution (SD/HD/any) · orientation (horizontal/vertical/any) · untagged-only toggle (group feeds). All persisted per feed in `feed_prefs` (DataStore), applied strictly **after** ContentFilter, shuffle respects resume markers.

---

## 9. UI

### Mobile
- **Theme (borrowed from upstream — exact design tokens verified live via Playwright mobile-viewport scan, 2026-09-30):** dark-first. Page/content background `#0f0f0f` (neutral-990), chrome bars `#090909` (top nav + bottom nav), widgets/cards/sheets `#191919` (bg-tertiary), neutral scale: 0 `#fff` · 50/100 `#efeef0` · 300 `#bab9c0` · 400 `#94939d` · 600 `#63616c` · 910 `#373333` · 930 `#302e2e` · 950 `#28272a` · 970 `#191919` · 980 `#090909` · 990 `#0f0f0f`. Brand red `#d70003` (brand-primary — a color, not a trademark); yellow-lime `#ebfa63` (brand-secondary: links/active states; hover `#daf02b`, pressed `#92ab05`, 40% variant `#ebfa6366`); functional: success `#00d3a3` · error `#ff575a` · info `#59c2e5` · warning `#ffc815`; white-opacity steps on dark: 5% `#ffffff0d` · 10% `#ffffff1a` · 20% `#fff3` · 40% `#fff6`. Typography: DM Sans (OFL Google font) everywhere — headings 32/24/20/18/16/14, body 16/14/12/10, weights 400/500/600/700, caption letter-spacing 1px; logo font "Greed Extended" (licensed — skip, do NOT borrow). Radius scale: 8 · 10 · 12 · 14 · 16 · 18 · 20 · 22 · 24 · 26 · 28 · 32 · 999(pill). Full dark theme + AMOLED true-black option retained; light theme optional but not default. No upstream logo/name/wordmark anywhere (§0) — palette only. Material 3 components stay; color roles mapped onto M3 scheme (primary=red accent, secondary/tertiary=yellow-lime) so dynamic color can still be offered as an option
- **Borrowed card & grid design (verified live 2026-09-30):** gif tiles = full-bleed media with no chrome, zero radius on tiles (confirmed — thumbs have no rounding); placeholder state uses near-black `#191919` fill with 1:1 aspect while loading (no spinners — matches the site); thumb metadata text 500-weight 12px/20px. Masonry 2-col mobile / 3-col tablet+ by default (user-overrideable via Grid columns, §6); site grid breakpoints — mobile 390px: 4 col, 16dp margin+gutter; tablet 834px: 8 col, 24dp gutter; desktop 1440px: 12 col, 32dp margin. Shapes: cards/widgets 12dp radius (bg `#191919`), follow/secondary buttons 12dp, hint toasts 14dp, filter pills 8dp, chips/buttons pill (999px) or 16dp-rounded, search field: 16dp-radius wrapper, 37–40dp tall, ~0.75dp outline rgba(255,255,255,0.4) on `#090909`, 32dp-radius input, placeholder "Search…". Tag chips: thin 1dp outline style, compact padding. Accent-filled buttons: 1dp outline in accent color, fill-on-press with inverse text; secondary buttons = outline style. Related-gifs strip: 3–4-col mini-tiles with 2–4dp gaps (reuse for the watch page "more like this" row). Niche/creator suggestion widgets: `#191919` panels, 12dp radius, 16dp page margins
- **Borrowed player design (snap feed anatomy verified live 2026-09-30):** TikTok-style snap player matches the site's feed — full-bleed vertical snap; on mobile web each item is a `TapTracker › GifPreview › Player(video) + OverLayer` stack occupying viewport height (~772dp inside 844 viewport with 68dp top bar + 56dp tab row + 72dp bottom nav); full-screen mode on rotate. Progress bar (verified): bottom edge, 24dp slider hit-area inset 16dp left / 72dp right, thin 3dp track (3dp radius), white ~30% opacity unfilled, yellow-lime `#ebfa63` fill + round thumb, remaining-time countdown chip (~30×20dp) right of the slider. Keep system/immersive controls minimal: overlay actions bottom-aligned (padding ≈12dp); fullscreen-hint toast (242×55dp, `#0f0f0f`, 14dp radius) on first load
- **Borrowed swipe-player action set (upstream snap-feed pattern — geometry verified live 2026-09-30):** swipe view gets a right-aligned vertical action rail next to the video column, inset 72dp from the right edge (matches seek-bar right padding): like button (≈46×52dp, white icon, filled-heart swap via `.liked`/`.unliked` classes; `PUT/DELETE /v2/gifs/{id}/like`, optimistic flip via `liked_ids` write-through, revert on failure; logged-out tap → age-gated login prompt), below it the sound/mute button (≈32×38dp) — verified site pattern: like above sound on the right rail, both over the video; then pin/save (logged-in, `v2/pins` path per §7 Saved/Pinned fallback), share (system share sheet — allowed per §0), overflow (⋯) → the standard long-press quick sheet (Block/Favorite/Block-tag actions live there). Bottom-left: creator chip (avatar + name, ≈120×40dp, tap → profile) with adjacent follow button (small secondary, ≈76×32dp, 12dp radius, logged-in) + tags row, inline-tappable. Bottom: dot pagination for gallery posts. The watch-page overlay reuses this same action set inline (its GalleryGifNav-style row)
- **Swipe-view player controls:** full-screen mode toggle inside the swipe player (true full-bleed: system bars hidden, immersive). In full-screen the player UI (overlay actions, progress chrome) auto-hides after a short idle timeout; single tap reveals the UI, next tap pauses/plays (standard tap-on-content semantics). Draggable seek slider on the revealed UI to scrub playback position (coexists with the always-visible thin 3dp progress bar; scrubbing pauses playout and resumes on release). Two-finger pinch zooms the video (1x–3x, pan while zoomed); zoom level + pan persist across swipe to the next video in the session, reset on player exit. **Mute-state persistence:** the mute toggle carries across swipes in the session and persists across sessions (per §6 prefs); tiles still show the independent `hasAudio` badge. **Auto-swipe button:** toggle icon button in the swipe-player overlay (verified rail pattern — sits in the same right-aligned action set, below share/overflow); when ON, once the current video plays to its natural end (`onPlaybackEnded`), the player snap-scrolls to the next item automatically (instant when reduced-motion is on); when OFF (default), the video ends and stays on frame-zero of the same item like today. **End-of-feed behavior:** if ON and the last loaded item ends, trigger the next-page load and advance when it arrives; stop at true end-of-feed (no bounce). Toggle state is remembered across sessions (per §6 prefs persistence) but does NOT override the data-saver autoplay-off guard — data-saver forces auto-swipe off too, same reasoning. No behavior change for gallery posts (dot pagination unaffected — looping items never fire ended)
- Edge-to-edge, Material 3 dynamic color (opt-in override of the borrowed theme), AMOLED true-black option
- **Navigation (≤5 rule — M3 bottom-nav limit):** bottom bar = 5 fixed destinations only: Home · Search · Favorites · Groups · Settings. Everything else (feed variants, group feeds, custom feeds, For You, Liked, Followed, Collections, Pinned) is switchable content WITHIN Home — chip row above the grid (horizontal-scroll, no bottom-bar tab flood). Logged-in tabs append as chips in the Home chip row, not as new bottom-bar items. Home surface borrows the site's mobile home anatomy (verified 2026-09-30): fixed 68dp top bar (`#090909`) with centered 37–40dp search, 56dp home tab row — two half-width text tabs (active = thicker bottom border, white label), trailing 96×40dp filter pill (8dp radius) — then the feed, then a 72dp bottom bar (5 destinations, `#090909`); content bg `#0f0f0f`, 16dp page margins
- **Accessibility (M3 baseline):** every interactive element ≥48dp touch target; icons carry `contentDescription` (decorative icons explicitly `null`); TalkBack focus order matches visual order; visible focus/selection states everywhere (mobile too, not just TV D-pad); the yellow-lime accent on near-black passes 4.5:1 — verify all text/accent pairs at implementation with the contrast rule
- **Player lifecycle & motion:** exactly ONE active player at a time — released/paused when its composable leaves composition or scrolls off-screen (leak + data-saver guard); autoplay toggle in Settings (default on, data-saver forces off); honor system reduced-motion — snap animations and reshuffle transitions become instant when motion is disabled. **Audio focus:** player sets ExoPlayer `audioAttributes` with `handleAudioFocus=true` — pause on transient loss (call, navigation); without it audio items overlap whatever the user was listening to. **Keep screen on:** `FLAG_KEEP_SCREEN_ON` (Compose `KeepOnScreenCondition`) only while the swipe-player/watch page is resumed and playback is active. **Adjacent-item prefetch:** the swipe player pre-initializes (player + manifest) for next/prev neighbors during idle/settling (Media3 preloading) — kills the freeze-then-play stutter on swipe; prefetches count toward the rate-limit invariant like every other app call. **Playback speed:** 0.5×–2× options in the overflow (⋯) menu of the revealed player UI (swipe view + watch page), Media3 `PlaybackParameters`; session-only — resets to 1× when the player exits
- **Gestures:** vertical snap, pinch-zoom (§9 Swipe-view player controls), and single-tap (reveal UI / play-pause, tap zones per that bullet) inside the player; **double-tap to like:** double-tap = optimistic like/unlike + heart-pop animation (single-tap delays ~250ms to disambiguate from double-tap, then reveals/pauses per the tap semantics above); no horizontal swipe conflicts with the grid, system back/gesture zones stay intact, pull-to-refresh absent (stale-while-revalidate refreshes silently)
- **Error/empty states:** reusable `ErrorState`/`EmptyState` composables in `:core:ui` matching the placeholder visual language (dark fill, icon + one line + retry button); wired to feed failures, empty search results, circuit-breaker-open, and logged-out-only surfaces. **Player error state:** swipe-view/watch-page playback failure (dead/expired URL, network drop, fallbacks exhausted) renders the same visual language as an inline overlay on the video — Retry (re-resolve URLs per §5, replay) · Skip (next item in swipe view) — never a frozen unexplained frame
- Masonry grid: 2-col portrait / 3-col landscape by default, overridden by the per-form-factor Grid columns setting (§6), Paging 3
- Tabs (in order): Videos (feeds, group feeds, custom feeds, For You — Liked · Followed · Collections · Pinned append as chips when logged in, hidden when logged out) · Continue Watching (watch-history-derived ≥3s-watched items, resumable — same source as the TV row) · Search · Favorites · Groups · Settings. TikTok-style swipe player; **long-press** on tile or player opens quick sheet:
  Like/Unlike · Pin/Unpin (logged-in) · Block creator · Favorite creator · Block tag `<tag>` · Block all tags on this gif · Block this keyword · Don't block. Follow lives on the creator profile, not the quick sheet (per-gif follow would be ambiguous)
- Tabs (in order): feeds, group feeds, custom feeds, For You, Search, Favorites, Groups, Settings — with Liked · Followed · Collections · Pinned appended when logged in (hidden entirely when logged out — no dead tabs)
- **Creator profile & feed:** username tap → creator screen: header (avatar, display name, follower count via `GET /v1/me/followers/populated`, follow/block buttons) + their gif feed. Feed source: `GET /v2/user_profile` if it verifies live in Phase 2; else `GET /v2/gifs/search` filtered by username (fallback already noted above). Creator feed is a standard filterable feed — gets sort/range chips (§8), runs through ContentFilter, cached under `creator:<username>:sort=<x>:p<n>` with search-TTL (1h). Server sorts only if `/v2/gifs/search` accepts `order` for username-filtered queries (verify Phase 2); else client-side sort over fetched pages. Offline: last-fetched creator page list renders from Room. Also reachable from search results and block-list screens.

### TV
- androidx.tv, full D-pad navigation (includes Groups screen, settings, PIN pad); same borrowed theme as mobile (§9 Mobile theme bullet), 10-foot contrast-tuned (accent colors lightened for viewing distance per TV guidelines)
- **Borrowed design, TV-adapted:** cards keep the 12–16dp-rounded, no-chrome, full-bleed-media look but with tv-material Cards; focus states use the accent outline (borrowed hover-fill pattern → focus-fill: accent border on focus, filled on selection); related-gifs mini-tile strip works as a "More like this" TV row; player keeps the thin accent progress bar, scaled up (4–5dp) for viewing distance; search field mirrors the rounded-outline style
- `TvLazyRow`s: Trending · Discover · Top This Week · Continue Watching · Favorites · one row per favorited group · custom feeds; logged-in adds: Liked row · Followed row · Collections row · Pinned row (hidden when logged out)
- **Creator profile & feed (TV):** tap username in now-playing / from search results → same creator screen as mobile (header + feed, same endpoint source, same `creator:<username>:sort=<x>:p<n>` cache). D-pad: header actions focusable, feed items same tile behavior as rows. Creator profile also shows their public collections (`/v2/users/{username}/collections`) and a scope-limited search (`v2/users/{username}/search`, cached `creator:<username>:search=<q>:p<n>`)
- **Saved/Pinned (logged-in, borrowed):** web `v2/pins` feature — pin/save gifs to a personal list (server state like likes); Pin/Unpin action in the long-press quick sheet **on both mobile and TV**, Pinned tab (mobile) / row (TV) logged-in only. **Read-path fallback (decide Phase 2):** if no list-read endpoint verifies (GET is 405 today), keep a local `pinned_ids` mirror — server PUT/DELETE confirms the write, local table renders the list; tiles resolve from `gifs` metadata
- **Audio:** `hasAudio` badge on tiles; watch page checks `/v2/gifs/{id}/check-sound` before unmuting; silent rendition (`urls.silent`) used when muted to save bandwidth
- Focus on username in now-playing → quick actions panel — same action set as the mobile quick sheet (Like/Unlike · Pin/Unpin · Block creator · Favorite creator · Block tag · Block all tags · Block this keyword · Don't block), D-pad focusable; "why did this get hidden" toast on filtered-item skip

---

## 10. Build Phases (order fixed — do not reorder)

1. **Skeleton:** modules, version catalog, ktlint+detekt wired, LeakCanary debug-only
2. **Network + anonymous browsing — VALIDATE LIVE FIRST.** All other phases depend on endpoint reality. If endpoints drifted, adapt paths only.
3. **Room + feeds offline** (incl. `feed_pages`, tags/username in gif rows)
4. **Player + media cache** (SimpleCache by ID, data-saver, quality picker, resume; snap controls: fullscreen + auto-hide UI, tap reveal/pause, seek scrub, pinch-zoom persistence, swipe-player action rail per §9, mute-state persistence, auto-swipe-on-ended toggle, player error overlay, adjacent prefetch, playback speed, double-tap-to-like, cache bounds + Clear cache)
5. **Auth:** WebView login with automatic token/cookie capture on BOTH mobile and TV (TV WebView verified live in this phase — it exists here because the TV app shell lands next), paste-token fallback, logout wipe
6. **TV app:** all rows, D-pad navigation, 10-foot UI, PIN pad, TV transfer-path screens (LAN pairing / JSON import) as WebView fallback
7. **Content controls + advanced feeds:** all Section 6–8 features
8. **Release:** signing + GitHub Actions

### Acceptance criteria
- [ ] Signed APKs produced by tag-triggered GitHub Actions (base64 keystore in secrets: `SIGNING_KEY`, `SIGNING_PASS`; keystore reused forever — key change blocks updates)
- [ ] Airplane-mode cold start renders from cache, zero network, no crash
- [ ] ≤10 requests in any rolling 5-second window (tested)
- [ ] 429/5xx backoff, cooldown minimums, and circuit breaker verified
- [ ] Login survives process death; sign-out performs full local wipe (token, cookies, cache) + server revoke if a revocation endpoint verifies in Phase 5; re-login works
- [ ] ID-token expiry during active use auto-refreshes via the verified refresh grant (timer + on-401 both trigger) — no user-visible re-login
- [ ] Age gate blocks all content until confirmed; optional lock works (mobile biometric/PIN, TV D-pad pad)
- [ ] TV: full D-pad navigation including Groups + settings + PIN pad
- [ ] Filter leak-zero: blocked content absent from every surface (incl. shuffle, groups, random, watch-history); promoted (`promoted: true`) items dropped on every surface too
- [ ] Per-feed sort/filter/shuffle state survives restart; global prefs too (grid columns, quality, data-saver, autoplay)
- [ ] All preferences + groups + collections survive export → import on a fresh install; custom feed definitions likewise
- [ ] Logged-out state shows zero Liked/Followed/Collections surfaces (no dead tabs, no ghost rows)
- [ ] Swipe player: fullscreen auto-hide + tap reveal/pause + seek scrub + pinch-zoom (persists across swipes, resets on exit); double-tap likes (single-tap still reveals/pauses); adjacent-item prefetch (no stutter on swipe); playback speed 0.5×–2× in overflow; like button optimistic-flips and reverts on failure; auto-swipe toggle advances to next video on natural end when ON (off = current behavior; next-page load then stop at true end), forced off under data-saver; mute state persists across swipes and sessions; player failure surfaces Retry/Skip overlay, never a frozen frame
- [ ] UI state (backstack, tab, swipe item + playhead, search query) survives process death alongside login
- [ ] 30-min idle: no crash, no leak, behavior identical to fresh
