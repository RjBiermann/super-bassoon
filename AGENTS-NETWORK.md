# AGENTS-NETWORK.md — `:core:network`

Retrofit/OkHttp layer: DTOs, rate limiting, error handling (endpoint inventory + rate rules
formerly PLAN.md §2, §4).
Base URL: `https://upstream-api-host.example/`. Endpoints are undocumented; **verify live before assuming**.

## Endpoints (adapt paths on drift — nothing else) — 2026 reality, live-verified

- `GET /v2/auth/temporary` → anonymous token. **Dead: `POST /v2/auth` (authkey) and `DELETE /v2/auth` — 404 live; login is PKCE OAuth via `upstream-auth-host.example` (Kinde) — see AGENTS-AUTH.md.**
- Content: `GET /v2/gifs/{id}` · `GET /v2/gifs/search` (tags, order, count, **`type`: `g`=gifs (default) / `i`=images — live-verified 2026-10-02 via the site search page; `type=i` shares the response envelope and GifDto rows but rows are stills: `type=2`, `duration=null`, `hls=false`, `urls.sd/hd` are jpgs — nothing to play)** · `/v2/feeds/trending/popular` (accepts `order`, e.g. `top_week`) · `/v2/feeds/trending/established` · `/v2/feeds/liked` · `/v2/feeds/for-you` (logged-in)
- Niches: `GET /v2/niches` · `/{id}` · `/{id}/gifs` (niche feed source, anonymous OK; **live drift 2026-10: `order=trending` now 400s BadOrder — despite the server's own error text listing it. Omit the param for the default ordering; accepted orders: `hot` `oldest` `latest` `best`**) · `/{id}/top-creators` · `/{id}/related` · `/v2/niches/categories` · `/v2/niches/following`
- Niches taxonomy params (live-verified 2026-10, anonymous OK): `GET /v2/niches/categories` → `{categories:[18 names]}`; `GET /v2/niches?category=<name>` filters (e.g. Animated → 34 niches); `GET /v2/niches?order=` accepts exactly **posts, subscribers, best_match, alphabetical_asc, alphabetical_desc, random** (server BadOrder message is the authoritative list; `subscribers` and `posts` verified, default = subscribers-ish popularity).
- Creators/search: `GET /v2/creators/search` · `/v2/creators/verified` · **`GET /v2/creators/search/previews?order=best_match&page=&count=&query=` → `{gifs:[…]}`, one preview gif per matched creator (dedupe by `gif.userName`) — live-verified 2026-10-02, anonymous OK** · **`GET /v2/niches/search/previews?order=best_match&page=&count=&query=` → `{previews:[{niche, gif, user}]}` (full niche object + preview gif + owner) — live-verified 2026-10-02, anonymous OK** · `GET /v2/users/{username}/search` · `GET /v2/users/{username}/collections` (anonymous OK) · `GET /v2/search/suggest` · `GET /v1/tags/match?query=` → tag-name array (`["Feet"]`, 200-verified 2026-10-02)
- Account reads: `GET /v2/me/following` · `GET /v1/me/follows` · `GET /v1/me/followers/populated` · `GET /v2/likes` · `GET /v2/me/collections` (unverified) · `GET /v2/search/user-history` · `POST /v2/search/user-history` → **202** (site writes history on every search submit — app keeps local Room history; server sync stays a spec'd-not-scheduled fallback)

## Account actions (write) — ✅ verified live 2026, all writes reverted after test

- Like: `PUT /v2/gifs/{id}/like` → **202**, `DELETE /v2/gifs/{id}/like` → **202**, `GET /v2/likes` (array of gif ids), `GET /v2/feeds/liked?page&count&type` (count must be 10–100)
- Follow creator: `PUT /v1/me/follows/{username}` → **204**, `DELETE /v1/me/follows/{username}` → **204**, `GET /v1/me/follows` (username array), `GET /v1/me/followers/populated` (**v1 path — don't "fix" it to v2**)
- Niche join (API word: subscribe; UI word: "Join/Leave Niche"  — see AGENTS-APP lingo): `POST /v2/niches/{id}/subscription` → **202**, `DELETE` same → **202**, `GET /v2/niches/following`
- **All writes require `Content-Type: application/json` with a JSON body** — like: `{"context":"trending","source":"feed","source_id":"home","position":0}`, unlike: `{"context":"trending"}`, follow: `{"source":"profile","source_id":"...","position":0}`, subscription: `{}`. Bare PUT/DELETE → 400.
- All content endpoints (`gifs/search`, `feeds/*`, `likes`) require a Bearer — the anonymous temp token from `GET /v2/auth/temporary` counts. `AnonymousSession` in `:core:network` lazily fetches and attaches it (reentrancy-safe; the temp-token request itself goes out unauthenticated).
- **Token race fix (2026-09, verified):** `AnonymousSession.token()` holds a mutex so racing callers **wait** for the in-flight temp-token fetch instead of going out unauthenticated (that race caused whole-session 401s). On a **401 with a bearer attached**, the auth interceptor invalidates the session via `onUnauthorized`, refetches, and **retries the request exactly once**; a second 401 surfaces as-is. Paging mediators never auto-retry — this retry is the only net.
- On any 401 the interceptor also logs the **response body** (`Auth401 <path> body=…`, logcat tag via println) — e.g. `TokenExpired` — never the token itself.
- **Refresh-on-401 (2026-09):** app modules wire `onUnauthorized = { if (!tokenStore.refreshBlocking()) session?.invalidate() }` — silent refresh-token grant (`POST upstream-auth-host.example/oauth2/token`, form-encoded, client_id `e06c34dac7654821bcb37e0393b54350`) runs first in the background 401 handler; the anon-session fallback only fires when refresh fails or no refresh_token exists. The auth interceptor's retry re-reads `authToken()` so it picks up the refreshed token automatically. No proactive timer — one 401 per hour before refresh is accepted. Refresh grant itself awaits a real refresh_token (WebView login); parse shape unit-tested in `TokenStoreTest`.
- Live-verified quirk (2026): search/trending pagination **overlaps** — page n re-lists gifs from page n-1. Paging keys must be deduped against earlier pages or LazyGrid crashes on duplicate keys (handled in `FeedPagingSource`).
- Never implement: upload, verification, premium paths — viewer-only beyond like/follow/subscribe.
- Authed reads: **`v2/user_profile` 404'd live (2026-10-02) — the real profile read is `GET /v1/users/{username}`** (works for self, presumably others): `{ageVerified, blockedTags, can_boost, creationtime, description, followers, following, gifs, kycVerified, likes, links, name, premium, profileImageUrl, profileUrl, publishedCollections, publishedGifs, status, studio, subscription, url, username, verified, views}`. **Who-am-I solved (2026-10-02, live): the id_token's `preferred_username` claim IS the account username** — claim verified against `GET /v1/users/{claim}` → 200, response username == claim (`TokenStore.usernameFromJwt`, consumed by AuthSection). `GET /v2/me/followers?count=&page=` → `{items, pages, page, total}` (200-verified; **row shape STILL unverifiable — test account has 0 followers, items=[] 2026-10-02 with a fresh real token; Followers page stays blocked on the no-guess rule**). `GET /v2/me/collections` → `{collections, users, page, pages, totalCount}` (**200-verified 2026-10-02**; was "unverified"). `GET /v2/search/user-history?count=` → array of `{id, value, type, account?}` (type=user rows embed a full account object; the site uses this as its search-history source).
- Site-discovery reads (live-verified 2026-10-02, id_token bearer): `GET /v2/niches/suggest?count=` → `{page, pages, total, niches[]}` (context-tagged niche suggestions, 31 for "feet"); `GET /v2/tags/trending?count=` → `{tags: [{name, count}]}`; `GET /v2/feeds/modules` → the site's per-surface module map — every module is already covered or §0-banned (boost/live-cam/only-fans/promotion/paid-links); use it as a completeness checklist. `v2/pins` accepts **PUT|POST only** (GET → 405; no site UI surface).
- **Galleries are dead (2026-10-02 probe):** `/v2/galleries`, `/v2/galleries/mine`, `/v2/galleries/{id}`, `/v2/users/{u}/galleries`, `/v2/me/galleries` all → 404; every probed gif's `gallery` field is `None`. The Phase-2 hard gate resolves to nothing-to-build; do not spec a galleries page.
- **Token-identity (2026-10-02, live):** api.redgifs.com rejects the Kinde OAuth **access_token** (`BadTokenFormat`) — the bearer is the **id_token** (same rule as TokenStore). The site's own SPA session token (`session_data.token`, iss=auth-service, aud=api.redgifs.com) also works and is a different minting path; both hit the same limiter.

## Contracts

- OkHttp **singleton**, `maxRequestsPerHost = 4`.
- **Token bucket:** 2 req/s sustained, burst 10. Hard invariant: never >10 req in any rolling 5s window. Must have a unit test proving it.
- **429:** cooldown per `Retry-After`; minimum 5s, max `Retry-After + 30s`.
- **5xx:** backoff 1s/4s/15s + jitter, max 3 attempts.
- **Circuit breaker:** 3 consecutive failures → open 5 minutes.
- `RateLimitBus` (SharedFlow) publishes limiter state; UI reads it for the "cooling down" indicator.
- DTOs are kotlinx-serialization, `ignoreUnknownKeys = true`. DTO → domain model mapping lives here, not in UI.

## Relationship to ContentFilter

Network is **not** the filter. Filtering happens once downstream (see `AGENTS-CONTENT-FILTER.md`);
network just returns raw pages that the pipeline will consume.
## Not borrowed (creator tools / ads / telemetry — §0 rules)
`v2/adv/*`, `v2/boost/*`, live-cam modules, `v2/only-fans`, `v2/premium/*`, `v2/upload*`/
`v2/gifs/submit*`, `v2/verification/*`, `v2/events/track`, `v2/csat/*`, `v2/experiments/*`,
`v2/analytics`, `v2/metrics/*`, `v2/announcements/*`, `v2/auth/clients/*`, upload pages.
Galleries (`v2/gallery/*`) were hard-gated on Phase-2 verification — never built, no UI.
Spec'd-not-scheduled fallbacks (LAN pairing, server search-history sync, silent rendition
swap) stay unbuilt until a live verification proves the need.

**Server collections CLOSED (2026-10-02 batch 18, stale-doc correction):** collections
were ALREADY fully server-backed — `CollectionsViewModel` is source-of-truth from
`GET /v2/me/collections`, and create/rename/delete/add/remove-gif writes are all wired
(live-verified 2026-10-01). What remains un-verified is BROWSING a collection's gifs
(the collection-content feed): no verified endpoint, same no-guess class.
