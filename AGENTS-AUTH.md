# AGENTS-AUTH.md — `:core:auth` + `:feature:auth`

Login, token storage, revocation. Spec: PLAN.md §2. **REVISED 2026 — live-verified.**

## Login reality (2026)
- The web login runs via `upstream-auth-host.example` (Kinde OAuth). **The old `sess` cookie + `POST /v2/auth` authkey exchange is dead (404 live).**
- Kinde-issued JWTs work directly as `Authorization: Bearer` on `upstream-api-host.example` and are **not** sender-bound (any IP/UA works).
- A real OAuth authorize flow exists: `POST /v2/oauth/code {client_id, scope, state, redirect_uri, response_type}` → `{url}` → user approves at `upstream.com/authorize` → redirect with code. Verify the code-exchange endpoint and whether we can register a client before building on it.

## Mobile login
- Primary: WebView through the site login; try to capture the Bearer via the OAuth redirect path.
- Fallback (works today, verified): token-paste — same as TV. Token lives only in the user's browser DevTools (Network tab `Authorization` header) since the web app keeps it in memory only.
- Token-reveal screen: shows the stored token so TV users can copy it.

## TV login
- Paste-token screen only.

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
- NOT yet done: WebView OAuth capture (primary path), real-token end-to-end check (needs the
  user's browser token), TV paste-token screen (Phase 6).
