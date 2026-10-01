# AGENTS-UX-PATTERNS.md — Instagram/TikTok practices, Giffy-mapped (DOC ONLY)

Reference doc, no implementation commitments. Each pattern is either
**already in the plan** (pointer to PLAN §) or a **candidate** (not scheduled —
build only if a user slice actually needs it; if adopted, it goes into PLAN
first, per the AGENTS.md "adapt endpoint paths only" rule with respect to scope).

Borrow only **interaction/UX patterns**. Instagram/TikTok engagement-hacking
mechanics (streaks, notification nudges, infinite-feed psychology, A/B
retention telemetry) are excluded — they violate §0 (no analytics, no
trackers) and the viewer-only posture. TikTok's *gestures and playback
conventions* are fine; its *retention playbook* is not.

## Already in the plan (do not re-spec — pointers only)

| Pattern (origin) | Where |
|---|---|
| TikTok-style vertical snap feed player with right action rail | PLAN §9 swipe-player bullets |
| Double-tap to like + heart pop (TikTok/Reels) | PLAN §9 Gestures |
| Auto-hiding player chrome, tap to reveal / pause | PLAN §9 swipe-view controls |
| Mute default with persistent mute state (silent-first autoplay convention) | PLAN §9 mute-state persistence, §5 audio |
| Auto-advance on video end + loop-off toggle (TikTok autoplay) | PLAN §9 auto-swipe bullet |
| Instagram "New posts" scroll-top + refresh pill, pull-to-refresh | PLAN §9 Refresh feed bullet |
| Bottom bar ≤5 items, everything else as in-Home chips (both apps' nav discipline) | PLAN §9 Navigation |
| Hold hints / one-time coach toasts (first-use affordance onboarding) | PLAN §9 UX-consistency gaps (double-tap hint) |
| Continue Watching resume (TikTok "watch history" inset; IG auto-resume) | §5 watch_history, §9 Tabs |
| Live-preview trends aside — preview-on-hover is TV only (see candidates) | this doc, TV section |
| Reduced-motion honored — animations collapse to instant | PLAN §9 Player lifecycle |
| 48dp targets, tap zones sized to thumb reach (mobile bare-thumb ergonomics) | PLAN §9 Accessibility |

## Candidates (not scheduled — mapped per device)

### Phone / compact window
- **Hold-for-speed (TikTok):** long-press on the player body = 2× while held,
  release restores. Adds a conflicts list: long-press already opens the quick
  sheet on tiles; inside the player only. Reuse of the existing
  long-press-disambiguation clock is the whole cost. Cheap, high value.
  Keep as: hold-to-2× inside the player only, never on tiles.
- **Swipe-down/tap-top to shrink the player (TikTok/Reels minimap):** player
  collapses to a small anchored window over the grid. Cost is high (PiP
  lifecycle, one-active-player invariant in §9 lives here); viewer value is
  low. Skip unless requested.
- **Bottom gradient scrim on player text (TikTok/Reels):** description/tags/@user and
  action-rail icons sit on a bottom gradient instead of raw video — legibility on bright
  content. Found missing in the 2026-10 audit (AGENTS-APP.md); adopt with the next
  player-controls touch, cheap (one `Brush.verticalGradient` behind the bottom cluster).
- **Instagram tap-to-collapse captions/description:** long description lines
  truncate to 2 lines with "more" — already how the description+tags display
  behaves (Phase 9 slice set). Verify parity when touching it; nothing to build.

### Tablet / foldable / expanded window
- **Two-column expanded view (TikTok web / IG desktop pattern):** expanded
  widths show the swipe player with a related-items column beside it instead
  of one full-bleed column. Possible without breaking the responsive-first
  rule — it's the LayoutHint seam expanding an existing surface ("more like
  this" beside the video), not a new screen. Mark: enhanced, not forked.
- **Reels rail retention:** IG keeps the played-position marker on tile
  thumbnails ("more of what you watched"). Already covered by avgColor +
  resume badge on TV rows; extend the resume badge overlay to mobile tiles
  only if Continue Watching proves discoverable enough on its own.

### TV
- **Preview-on-focus (TikTok TV app):** focused row card starts a muted loop.
  Cost:decode-per-focus churn + the rate-limit invariant (manifest fetches
  per focus move at TvLazyRow scroll speed). Mitigation exists in §9
  (adjacent prefetch, decode fallback env vars) but the 2 req/s budget makes
  focus-scoped previewing risky. Mark: only if a low-cost advisory
  (leadership: preview only the settled focus after 600ms dwell) — otherwise
  skip.
- **In-episode "show more" panel (TikTok TV):** D-pad DOWN on the player
  expands description/tags/actions below the video. Matches the existing
  MENU quick-actions + description display already shipped; add DOWN-on-player
  focus routing as polish (AGENTS-APP.md TV section already collects focus
  lessons — route lessons there when touched).
- **Row autoplay trailer (IG TV-style hero row):** molested pattern — skip
  outright (bandage: data burn for zero intent signal, viewer-only).

## Device-type summary used across the doc

| Device | Borrowed posture |
|---|---|
| Phone | TikTok gestural vocabulary; IG nav/refresh discipline |
| Tablet/foldable/window | Same composables, wider — IG desktop two-column as an enhancement, never a fork |
| TV | D-pad equivalents of every borrowed gesture (hold = MENU, actions = quick panel); no hover-only affordances |

## When a candidate graduates
Adoption order: user slice → PLAN §9 bullet (numbered, dated) → AGENTS-APP.md
verification note. Nothing on the Candidates list becomes code without a PLAN
edit first — this file stays commentary, never the spec.
