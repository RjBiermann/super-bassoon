# AGENTS-AUTH.md — `:core:auth` + `:feature:auth`

Login, token storage, revocation (auth flows formerly PLAN.md §2). **REVISED 2026 — live-verified.**

## Login reality (2026, live-verified via Playwright run 2026-09-30)
- The web login runs via `upstream-auth-host.example` (Kinde OAuth). **The old `sess` cookie + `POST /v2/auth` authkey exchange is dead (404 live).**
- Kinde-issued **id_tokens** work directly as `Authorization: Bearer` on `upstream-api-host.example` and are **not** sender-bound (any IP/UA works). The access_token (`aud: []`) is rejected.
- Real login flow observed (email + one-time-code): `POST upstream-api-host.example/v2/auth/login {login, captcha(Turnstile), session_id}` → auth2 "Check your inbox" OTP form → browser 302 to `upstream-site.example/?code=…&scope=openid profile email offline&state=…` → `POST upstream-auth-host.example/oauth2/token` (authorization_code + **PKCE** `code_verifier`) → full bundle (id_token + access_token 24h + **refresh_token**).
- **The site never writes `localStorage.auth_data`** (capturing it was a wrong assumption). Post-login localStorage has only `user_data` (profile) and `session_data` (`token` = an auth-service JWT bound to IP+UA that 401s `UserTokenRequired` on user endpoints — not our bearer). The id_token lives in the site's JS memory only; the refresh_token never reaches the page.
- **The app's login path is therefore app-driven PKCE**: app generates verifier/challenge/state, opens the auth2 authorize URL in a WebView, intercepts the `?code=` redirect (`shouldOverrideUrlLoading`, before site JS runs), exchanges in-app. Verified end-to-end live (see below). Refresh grant: `grant_type=refresh_token&client_id=…` → 200, refresh_token is reusable/non-rotating.

## Token storage

**Bearer contract (re-probed 2026-10-01 with a hard test token):** the content API accepts
the **ID token** as `Authorization: Bearer` on every user endpoint (`/v2/likes`,
`/v2/feeds/liked`, `/v2/feeds/for-you`, `/v1/me/follows`, `/v2/me/following`,
`/v2/me/collections`, and the write actions — all probed live, all writes reverted).
Kinde's **access_token is REJECTED** by the API (`BadTokenFormat: "must be a JWT with
type=bearer"`; its payload also has `aud: []` vs the API's audience.
TokenStore correctly stores the ID token; the hourly expiry + silent refresh stays.
The refresh grant (form-urlencoded, public client) was re-verified live: 200,
mints a fresh ID token + refresh token.

- `EncryptedSharedPreferences`, key alias `giffy_auth` (security-crypto).
- Login flow must survive process death.
- Sign-out / re-login cycle must work end-to-end; verify after changes.

## Logout
1. Revoke what's revocable (no `DELETE /v2/auth` anymore — TBD via OAuth client).
2. Clear WebView cookies.
3. Wipe token storage.

Never log, expose, or transmit the token anywhere except the token-reveal screen.

## Implemented (Phase 5 slice 1, verified on emulator 2026-09)
- `core:auth` `TokenStore`: EncryptedSharedPreferences alias `giffy_auth`, StateFlow token,
  JWT-shape validation (3 base64url parts) rejects fat-finger pastes. Encrypted at rest —
  confirmed via run-as (Tink keyset only, no plaintext). security-crypto is deprecated
  upstream; revisit when a successor ships.
- `feature:auth` `AuthScreen` + `AuthViewModel`: **WebView sign-in only** (paste-token
  + reveal-token REMOVED 2026-10 by user decision — no bearer-token UI remains), sign-out.
  Reached via person icon on the feed app bar; TV reuses the same screen.
- Bearer wiring: `AppModule.network()` sends user token first, anonymous temp token fallback.
- Sign-out clears WebView cookies (`removeAllCookies` + `removeSessionCookies`) AND wipes
  token storage (cookie clearing shipped with WebView login, 2026-09-30).
- No revoke endpoint exists (POST /v2/auth dead) — TBD via OAuth client.

## PKCE WebView login (implemented + verified live 2026-09-30)

Flow (all live-verified against upstream-auth-host.example with a real account):
1. `TokenStore.newPkce()` → verifier (64 hex) + S256 challenge + state.
2. `TokenStore.authorizeUrl(pkce)` → `https://upstream-auth-host.example/oauth2/auth?client_id=…&redirect_uri=https://upstream-site.example&response_type=code&scope=openid profile email offline&code_challenge=…&code_challenge_method=S256&state=…`. When already signed in on that browser profile this 302s **instantly** with a code; otherwise the auth2 login form shows (which **renders fine in emulator WebViews** — unlike the www site pages).
3. `WebViewLoginScreen` intercepts the `upstream-site.example/?code=…&state=…` redirect in `shouldOverrideUrlLoading` (state-checked, site JS never runs — no code race) and hands the code up.
4. `AuthViewModel.exchangeCode(code)` → `TokenStore.exchangeBlocking(code, verifier)` on IO → `POST /oauth2/token` grant_type=authorization_code → stores id_token + refresh_token.
5. `BackHandler`: WebView history back, else close. (The old paste-token TV fallback was
   removed 2026-10; TV signs in through the same WebView flow.)

Verified live on the API-33 emulator (Medium_Phone): user logged in with email + OTP code inside the WebView → code captured on redirect → exchange → "Signed in" → **force-stop → relaunch → still Signed in** → feed requests authenticated, zero Auth401s.

**Signed-in @username (2026-10-02):** the id_token's `preferred_username` claim
(standard OIDC) is the account username — live-verified (`v1/users/{claim}` → 200,
username == claim). `TokenStore.usernameFromJwt` decodes it (stdlib Base64Url,
no guessing), `AuthViewModel.username` exposes it, AuthSection shows
"Signed in as @<username>".

`TokenStore` helpers (`newPkce`, `authorizeUrl`, `extractCode`, `parseTokenBundle`) are unit-tested in `TokenStoreTest` (9 tests). `refreshBlocking()` uses the same token endpoint; the refresh grant itself verified live via curl (200, full bundle, non-rotating refresh_token). The in-app on-401 refresh fires when the 1h id_token expires.

**Emulator quirk (informational):** upstream-site.example pages can paint black/white in emulator WebViews (old Chrome WebView + the site's own CSS). auth2 login pages render fine. (The paste-token fallback was removed 2026-10 — if a device WebView ever fails the auth2 page there is no fallback UI; the auth2 page itself is WebView-verified.)

## Tokens are session-bound server-side (probed 2026-10-03, resolved same day)
- **Foreign mints fail:** an id_token minted in another browser session replayed from this
  app/curl → `401 BadTokenSignature` / `UserTokenRequired`, while the owning browser keeps
  getting 200. The 2026-09-30 paste-token flow is dead for this reason (and the paste UI
  was removed 2026-10 anyway).
- **The app's own in-app WebView PKCE login works normally** — own session, own cookie jar;
  full signed-in session verified (For You 200 with zero Auth401s, force-stop → relaunch
  keeps the session). No CookieJar/x-session-id adaptation needed.
- Consequences: debroid token-injection CANNOT fake a signed-in state — sign in for real via
  the in-app WebView (email → OTP; OTP = user slice). If a NEW authenticated endpoint 401s,
  re-probe the SPA's headers before assuming app bugs.
- Session lesson: `orientation_filter` on a TV/profile flips silently if a stray
  ENTER lands on a chip during D-pad walks — an all-portrait pool then empties
  Trending (and every revalidated feed row) while the creators row (not
  orientation-filtered) still fills. Check Settings → Orientation first on any
  "row renders empty" report; DataStore file (`files/datastore/*.preferences_pb`)
  is the quick check (`orientation_filter` value).
- Test-harness gotcha: `adb shell input text` **garbles long strings** (silently drops /
  duplicates chars). Paste in **≤60-char chunks** and verify the tail against the source
  before pressing Sign in.
- Debug aid: on 401, `NetworkModule` prints the response body (`Auth401 …` line in logcat)
  — never logs the token itself.

## Real-token contract (verified live 2026-09-30, re-confirmed since)
- **The id_token (issuer `upstream-auth-host.example`) IS the API bearer** — the Kinde
  **access_token** (`aud: []`) is **rejected** (`BadTokenFormat`).
- id_token lifetime is **1 hour** (`iat`→`exp` = 3600s). On expiry the API answers
  `401 {"error":{"code":"TokenExpired"}}` and the silent refresh grant handles it
  (no user action; the old re-paste convention is gone with the paste-token flow).
- TV D-pad UX: the token field moves focus to **Sign in on key DOWN**
  (`onPreviewKeyEvent` + `FocusRequester` in `AuthScreen`).
