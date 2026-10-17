---
name: giffy-live-probe
description: "Safely verify upstream API/site behavior before building on it: identity consistency, rate budget, token capabilities, Playwright UI-contract capture, recording row shapes. Use when an endpoint must be verified, a DTO shape is unknown, a feature is blocked on a live check, or a fresh token bundle arrives."
---

# Giffy Viewer — Live Probe Protocol

The repo rule is **no-guess**: API endpoint paths, params, and row shapes are adapted from
live verification only — never invented. This skill is how you verify safely.

## Identity (get this wrong and everything 401s)

- Every request must carry the SAME browser identity: `BrowserIdentity` in `:core:network`
  (Chrome-Android UA + client hints + Sec-Fetch + Origin/Referer + X-Session-Id).
- **The anonymous temp token is UA-bound**: its JWT embeds `valid_agent`/`valid_addr`;
  a different UA → `401 WrongSender`. When probing manually, use the identical UA the
  token was issued under, for the token fetch AND every subsequent call.
- Probes must respect the rate budget: ≤15 requests in any rolling 5s window. Pace probe
  scripts; a "page broken" probe result may just be 429/breaker pacing.

## Token capabilities (verified live)

- Anonymous temp token: public reads (feeds, search, creators/niches previews).
- User token (id_token): required for `v2/niches/suggest`, likes, follows, collections.
- id_tokens expire ~1h — a mid-test 401 TokenExpired on MANUAL curl is expected; the app
  silently refreshes. Never probe the refresh grant itself (rotation rule).
- Token hygiene: bundles go to `/tmp` (0600), deleted after the session; never in source,
  never committed, never printed.

## Probe methods

- **HTTP shape**: curl with the exact identity headers; record status, params, and the
  full row DTO. A 405/401 result is a finding, not a failure to hide.
- **UI contract**: Playwright at mobile viewport (e.g. 390x844) — read the live DOM
  (tablists, autoplay `<video>` muted/loop attributes, CSS `:root` tokens). This decided
  inline autoplay and the search 4-tab scope, and re-verified the palette with zero drift.
- **Negative probes count**: `GET` 405 / token-403 (check-sound) closed a question without
  building anything.

## Recording results

- New/changed endpoint rows → `AGENTS-NETWORK.md` (endpoint inventory + row shapes).
- UI contract findings → the matching AGENTS doc section.
- Blocked items record WHAT is missing (e.g. "v2/me/followers returns items=[] with 0
  followers — row DTO unverifiable"), so the next session with a populated account can
  unblock instantly.
