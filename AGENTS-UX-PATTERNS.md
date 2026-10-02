# AGENTS-UX-PATTERNS.md — Instagram/TikTok practices, Giffy-mapped (DOC ONLY)

Reference doc, no implementation commitments. Each pattern is either
**already in the app** or a **candidate** (not scheduled —
build only if a user slice actually needs it; if adopted, document it in the AGENTS-*.md files
first, per the AGENTS.md "adapt endpoint paths only" rule with respect to scope).

Borrow only **interaction/UX patterns**. Instagram/TikTok engagement-hacking
mechanics (streaks, notification nudges, infinite-feed psychology, A/B
retention telemetry) are excluded — they violate §0 (no analytics, no
trackers) and the viewer-only posture. TikTok's *gestures and playback
conventions* are fine; its *retention playbook* is not.

## Already in the plan (do not re-spec — pointers only)

| Pattern (origin) | Where |
|---|---|
| TikTok-style vertical snap feed player with right action rail | implemented in AGENTS-APP.md |
| Double-tap to like + heart pop (TikTok/Reels) | implemented in AGENTS-APP.md |
| Auto-hiding player chrome, tap to reveal / pause | implemented in AGENTS-APP.md |
| Mute default with persistent mute state (silent-first autoplay convention) | implemented in AGENTS-APP.md |
| Auto-advance on video end + loop-off toggle (TikTok autoplay) | implemented in AGENTS-APP.md |
| Instagram "New posts" scroll-top + refresh pill, pull-to-refresh | implemented in AGENTS-APP.md |
| Bottom bar ≤5 items, everything else as in-Home chips (both apps' nav discipline) | implemented in AGENTS-APP.md |
| Hold hints / one-time coach toasts (first-use affordance onboarding) | implemented in AGENTS-APP.md |
| Continue Watching resume (TikTok "watch history" inset; IG auto-resume) | §5 watch_history, §9 Tabs |
| Live-preview trends aside — preview-on-hover is TV only (see candidates) | this doc, TV section |
| Reduced-motion honored — animations collapse to instant | implemented in AGENTS-APP.md |
| 48dp targets, tap zones sized to thumb reach (mobile bare-thumb ergonomics) | implemented in AGENTS-APP.md |

| Quick-sheet sub-panes — flatten-to-group (YouTube long-press "save to playlist" submenu pattern): destructive blocks separated, main pane capped at everyday toggles + entries | spec'd (not built) in AGENTS-APP.md "Quick actions — submenu restructure" |

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

## TV D-pad research (2026-10, research-only — porting mobile features to TV)
Doc-only reference, same graduation rule as this file (user slice → AGENTS-APP.md note → code).
Trigger: "port more mobile features to TV / D-pad". Scope: what leading TV apps actually
bind to keys, what Giffy TV already has, and the gap — with anti-confusion rules so the
keymap stays legible.

### Market scan (what mainstream TV apps bind)

| App | Center | Left/Right | Up/Down | Hold | Back | Speed control |
|---|---|---|---|---|---|---|
| Netflix | play/pause | ±10s step; show progress bar | Up = back to browse row / exit | hold R/L = progressive FF/RW | dismiss controls, then exit | menu only ("Playback speed" in settings overlay) |
| YouTube | play/pause | ±10s with scrub timeline | Up = Up-next/related carousel | hold R = 2×→ progressive FF | dismiss overlay, then exit | menu only |
| Prime / Disney+ | play/pause | ±10s when controls visible | Up/Down = controls rows / episodes | hold = repeat same action | dismiss controls first | menu only |

Denominators: directions map **spatially** (horizontal = time, vertical = items/rows); one
press = one small action; **hold always repeats its own key's action** (key repeat — never a
different action); everything beyond the base keys lives in a panel reached from the player;
speed is **never a bare shortcut**; BACK dismisses overlays before navigating; remote
transport keys (`KEYCODE_MEDIA_*`) are handled alongside D-pad. Number keys 0–9 = percent
jump on YouTube only — nicety, not a convention worth copying.

### Giffy TV today vs mobile player

| Mobile feature | TV D-pad port | Status |
|---|---|---|
| Tap pause | Center = play/pause | shipped (audit gap closed) |
| Swipe next/prev | Down/Up = next/prev | shipped |
| Overflow quick sheet | MENU = quick-actions panel | shipped (partial parity) |
| Playback speed 0.5–2× | MENU panel item (submenu or cycling row) | gap |
| Like / double-tap like | MENU panel Like (no double-tap binding — see rules) | gap |
| Mute rail (Sound On/Off) | MENU panel Mute, `hasAudio`-aware per §9 | gap |
| Auto-swipe toggle | MENU panel toggle; reduced-motion + data-saver rules already in prefs | gap |
| Progress line | Left/Right = ±10s (press = one step, hold = repeat); show position while seeking | gap |
| Hold-for-2× (player only) | skip — hold Right (progressive FF) is the TV idiom for the same need | candidate, low priority |
| Share sheet | skip — no established TV share convention | skip |
| Pinch zoom | skip — no D-pad analog, mobile-only gesture | skip |

### Recommended keymap (candidate — AGENTS-APP.md edit first if adopted)

Player: Center play/pause · Down/Up prev/next · Left/Right ±10s (+hold repeat) ·
MENU quick-actions panel (Like · Mute · Speed · Auto-swipe · Block/Favorite set it already
has) · BACK dismiss panel then exit · handle `KEYCODE_MEDIA_PLAY_PAUSE/FF/REW` to the same
actions. Home: unchanged (MENU on card, focus-up into search already implemented).
Fix-with-touch: `TvSourceFeedScreen` `onMenu = {}` dead control (audit finding).

### Anti-confusion rules (the whole point)
1. **Spatial honesty:** horizontal = time, vertical = items — never swap meanings per screen.
2. **One key, one meaning app-wide:** MENU is quick actions on content everywhere (tiles +
   player); never two different behaviors for the same key on the same surface.
3. **No double-tap bindings** — phone remotes debounce taps; TV remotes send accidental
   repeats. Mobile double-tap-like ports to the panel's Like item instead.
4. **Hold repeats its own key's action** (progressive seek), never triggers a new one.
5. **Everything else goes in the MENU panel** — if it needs a new bare key, it first needs a
   strong reason the panel isn't enough.
6. **BACK dismisses overlays before navigation**, and never bypasses the age gate / PIN.

## Device-type summary used across the doc

| Device | Borrowed posture |
|---|---|
| Phone | TikTok gestural vocabulary; IG nav/refresh discipline |
| Tablet/foldable/window | Same composables, wider — IG desktop two-column as an enhancement, never a fork |
| TV | D-pad equivalents of every borrowed gesture (hold = MENU, actions = quick panel); no hover-only affordances |

## When a candidate graduates
Adoption order: user slice → AGENTS-APP.md note (numbered, dated) → code.
Nothing on the Candidates list becomes code without
edit first — this file stays commentary, never the spec.
