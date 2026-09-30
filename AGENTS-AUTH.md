# AGENTS-AUTH.md — `:core:auth` + `:feature:auth`

Login, token storage, revocation. Spec: PLAN.md §2. **REVISED 2026 — live-verified.**

## Login reality (2026, live-verified via Playwright run 2026-09-30)
- The web login runs via `upstream-auth-host.example` (Kinde OAuth). **The old `sess` cookie + `POST /v2/auth` authkey exchange is dead (404 live).**
- Kinde-issued **id_tokens** work directly as `Authorization: Bearer` on `upstream-api-host.example` and are **not** sender-bound (any IP/UA works). The access_token (`aud: []`) is rejected.
- Real login flow observed (email + one-time-code): `POST upstream-api-host.example/v2/auth/login {login, captcha(Turnstile), session_id}` → auth2 "Check your inbox" OTP form → browser 302 to `upstream-site.example/?code=…&scope=openid profile email offline&state=…` → `POST upstream-auth-host.example/oauth2/token` (authorization_code + **PKCE** `code_verifier`) → full bundle (id_token + access_token 24h + **refresh_token**).
- **The site never writes `localStorage.auth_data`** (capturing it was a wrong assumption). Post-login localStorage has only `user_data` (profile) and `session_data` (`token` = an auth-service JWT bound to IP+UA that 401s `UserTokenRequired` on user endpoints — not our bearer). The id_token lives in the site's JS memory only; the refresh_token never reaches the page.
- **The app's login path is therefore app-driven PKCE**: app generates verifier/challenge/state, opens the auth2 authorize URL in a WebView, intercepts the `?code=` redirect (`shouldOverrideUrlLoading`, before site JS runs), exchanges in-app. Verified end-to-end live (see below). Refresh grant: `grant_type=refresh_token&client_id=…` → 200, refresh_token is reusable/non-rotating.

## Token storage
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
- `feature:auth` `AuthScreen` + `AuthViewModel`: paste-token sign-in, reveal+copy dialog
  (TV use), sign-out. Reached via person icon on the feed app bar.
- Bearer wiring: `AppModule.network()` sends user token first, anonymous temp token fallback.
- Sign-out currently wipes the token only; WebView cookie clearing lands with WebView login.
  No revoke endpoint exists (POST /v2/auth dead) — TBD via OAuth client.

## PKCE WebView login (implemented + verified live 2026-09-30)

Flow (all live-verified against upstream-auth-host.example with a real account):
1. `TokenStore.newPkce()` → verifier (64 hex) + S256 challenge + state.
2. `TokenStore.authorizeUrl(pkce)` → `https://upstream-auth-host.example/oauth2/auth?client_id=…&redirect_uri=https://upstream-site.example&response_type=code&scope=openid profile email offline&code_challenge=…&code_challenge_method=S256&state=…`. When already signed in on that browser profile this 302s **instantly** with a code; otherwise the auth2 login form shows (which **renders fine in emulator WebViews** — unlike the www site pages).
3. `WebViewLoginScreen` intercepts the `upstream-site.example/?code=…&state=…` redirect in `shouldOverrideUrlLoading` (state-checked, site JS never runs — no code race) and hands the code up.
4. `AuthViewModel.exchangeCode(code)` → `TokenStore.exchangeBlocking(code, verifier)` on IO → `POST /oauth2/token` grant_type=authorization_code → stores id_token + refresh_token.
5. `BackHandler`: WebView history back, else close. Paste-token stays as the fallback (TV).

Verified live on the API-33 emulator (Medium_Phone): user logged in with email + OTP code inside the WebView → code captured on redirect → exchange → "Signed in" → **force-stop → relaunch → still Signed in** → feed requests authenticated, zero Auth401s.

`TokenStore` helpers (`newPkce`, `authorizeUrl`, `extractCode`, `parseTokenBundle`) are unit-tested in `TokenStoreTest` (9 tests). `refreshBlocking()` uses the same token endpoint; the refresh grant itself verified live via curl (200, full bundle, non-rotating refresh_token). The in-app on-401 refresh fires when the 1h id_token expires.

Gone: the earlier `localStorage.auth_data` polling capture — the real site never writes it (wrong assumption, killed by the Playwright observation above).

**Emulator quirk (informational):** upstream-site.example pages can paint black/white in emulator WebViews (old Chrome WebView + the site's own CSS). auth2 login pages render fine. If a device WebView ever fails the auth2 page, paste-token is the fallback.

## Real-token end-to-end (verified on TV36 2026-09-30)
- **The browser id_token (issuer `upstream-auth-host.example`) IS the API bearer** — works on
  `v2/feeds/*`, `v2/users/{name}/search`, `v1/me`, `v2/likes`. The Kinde **access_token**
  (same issuer but `aud: []`) is **rejected** (`BadTokenFormat`).
- id_token lifetime is **1 hour** (`iat`→`exp` = 3600s). On expiry the API answers
  `401 {"error":{"code":"TokenExpired"}}` and the user must re-paste until WebView OAuth
  lands (no refresh_token available in the paste-only flow).
- Sign-in, process-death survival (force-stop → relaunch → "Signed in"), and sign-out all
  verified on TV36 with the real token; authenticated feed/search requests returned 200.
- TV D-pad UX: the token field moves focus to **Sign in on key DOWN**
  (`onPreviewKeyEvent` + `FocusRequester` in `AuthScreen`).
- Test-harness gotcha: `adb shell input text` **garbles long strings** (silently drops /
  duplicates chars — a "signed-in" paste once stored a 1153-char token that looked like a
  JWT but got 401 on everything, anonymous-working endpoints included). Paste in **≤60-char
  chunks** and verify the tail against the source before pressing Sign in.
- Debug aid: on 401, `NetworkModule` prints the response body (`Auth401 …` line in logcat)
  — never logs the token itself.
