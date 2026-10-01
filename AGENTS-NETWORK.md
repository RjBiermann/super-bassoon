# AGENTS-NETWORK.md — `:core:network`

Retrofit/OkHttp layer: DTOs, rate limiting, error handling. Spec: PLAN.md §2, §4.
Base URL: `https://upstream-api-host.example/`. Endpoints are undocumented; **verify live before assuming**.

## Endpoints (adapt paths on drift — nothing else)

- `GET /v2/auth/temporary` → anonymous token (`addr, agent, session, rtoken, token, expiry_date`)
- `POST /v2/auth` body `{"authkey": "<sess cookie>"}` → long-lived token
- `DELETE /v2/auth` → revoke
- `GET /v2/gifs/{id}` · `GET /v2/gifs/search` (tags, order, count)
- `/v2/feeds/trending/...`, top endpoints (period: day/week/month/year/all)

## Account actions (write) — ✅ verified live 2026, all writes reverted after test

- Like: `PUT /v2/gifs/{id}/like` → **202**, `DELETE /v2/gifs/{id}/like` → **202**, `GET /v2/likes` (array of gif ids), `GET /v2/feeds/liked?page&count&type` (count must be 10–100)
- Follow creator: `PUT /v1/me/follows/{username}` → **204**, `DELETE /v1/me/follows/{username}` → **204**, `GET /v1/me/follows` (username array), `GET /v1/me/followers/populated` (**v1 path — don't "fix" it to v2**)
- Niche join (API word: subscribe; UI word: "Join/Leave Niche" — PLAN §9 lingo table): `POST /v2/niches/{id}/subscription` → **202**, `DELETE` same → **202**, `GET /v2/niches/following`
- **All writes require `Content-Type: application/json` with a JSON body** — like: `{"context":"trending","source":"feed","source_id":"home","position":0}`, unlike: `{"context":"trending"}`, follow: `{"source":"profile","source_id":"...","position":0}`, subscription: `{}`. Bare PUT/DELETE → 400.
- All content endpoints (`gifs/search`, `feeds/*`, `likes`) require a Bearer — the anonymous temp token from `GET /v2/auth/temporary` counts. `AnonymousSession` in `:core:network` lazily fetches and attaches it (reentrancy-safe; the temp-token request itself goes out unauthenticated).
- **Token race fix (2026-09, verified):** `AnonymousSession.token()` holds a mutex so racing callers **wait** for the in-flight temp-token fetch instead of going out unauthenticated (that race caused whole-session 401s). On a **401 with a bearer attached**, the auth interceptor invalidates the session via `onUnauthorized`, refetches, and **retries the request exactly once**; a second 401 surfaces as-is. Paging mediators never auto-retry — this retry is the only net.
- On any 401 the interceptor also logs the **response body** (`Auth401 <path> body=…`, logcat tag via println) — e.g. `TokenExpired` — never the token itself.
- **Refresh-on-401 (2026-09):** app modules wire `onUnauthorized = { if (!tokenStore.refreshBlocking()) session?.invalidate() }` — silent refresh-token grant (`POST upstream-auth-host.example/oauth2/token`, form-encoded, client_id `e06c34dac7654821bcb37e0393b54350`) runs first in the background 401 handler; the anon-session fallback only fires when refresh fails or no refresh_token exists. The auth interceptor's retry re-reads `authToken()` so it picks up the refreshed token automatically. No proactive timer — one 401 per hour before refresh is accepted. Refresh grant itself awaits a real refresh_token (WebView login); parse shape unit-tested in `TokenStoreTest`.
- Live-verified quirk (2026): search/trending pagination **overlaps** — page n re-lists gifs from page n-1. Paging keys must be deduped against earlier pages or LazyGrid crashes on duplicate keys (handled in `FeedPagingSource`).
- Never implement: upload, verification, premium paths — viewer-only beyond like/follow/subscribe.
- Authed reads: `v2/user_profile` 404'd live (params TBD); `v2/me/collections` unverified.

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
