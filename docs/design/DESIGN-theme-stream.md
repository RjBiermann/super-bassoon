# DESIGN-theme-stream.md — Issue #7 "Theme Spec" rebrand (design phase, no code)

Date: 2026-10 (issue #7 design). Repo state: post batch-16/23 (all direct `GiffyColors.*`
composable reads already converted to scheme roles; mobile Typography already on the
standard M3 scale; `Shapes` owned by GiffyTheme). **Doc-only: no code, no builds.**

## 0. Scope, constraints, and issue-completeness status

**Task:** rebrand both shells to the issue-#7 M3 dark scheme — seed color **#E5194D**,
background seed **#161617** dark-first, per the issue's token table (§1.1, all rows
transcribed verbatim). Retire the custom TV focus ring (`giffyFocus`) in favor of the
standard M3/tv-material focus treatment **with the issue's explicit focus tokens** (§3),
not scheme-derived approximations. Replace custom UI with prebuilt components where a
prebuilt truly fits; style via theming, not component overrides.

**Issue completeness:** the full issue body is now available to the design. Every color
role is specified verbatim. The issue specifies NO `surfaceContainer*` ladder and NO
`surfaceTint` value — those slots therefore stay at the standard M3 `darkColorScheme`
derivations (un-specified = M3 defaults, and grep confirms zero app-specific reads of
`surfaceTint`/`surfaceDim`/`surfaceBright`/fixed families — the TV `surfaceTint = c.primary`
passthrough is the one intentional mapping, kept). Nothing is invented anywhere.

House rules honored (unchanged): dark-only (no light theme; the issue's light-mode tone
note #B3003C is recorded but not built), no analytics/tracking, no upstream branding,
AMOLED option preserved, `CenterHold` (hold-Center keymap) untouched — input modality,
not visuals; the focus-ring ask does not reach it.

## 1. The new darkColorScheme (mobile `Theme.kt` → `giffyColors(amoled)`)

### 1.1 Token table — the complete issue rows, exact `Color(0xFF…)` lines

```kotlin
return darkColorScheme(
    primary = Color(0xFFFF668F),               // [issue]
    onPrimary = Color(0xFF5E0B25),             // [issue]
    primaryContainer = Color(0xFF7D0030),      // [issue]
    onPrimaryContainer = Color(0xFFFFD9E1),    // [issue]
    inversePrimary = Color(0xFFB3003C),        // [issue]

    secondary = Color(0xFFE3BDC4),             // [issue]
    onSecondary = Color(0xFF432930),           // [issue]
    secondaryContainer = Color(0xFF5E4349),    // [issue]
    onSecondaryContainer = Color(0xFFFBD9E1),  // [issue]

    tertiary = Color(0xFFE7C16B),              // [issue]
    onTertiary = Color(0xFF3F2D00),            // [issue]
    tertiaryContainer = Color(0xFF5B4200),     // [issue]
    onTertiaryContainer = Color(0xFFFFE08D),   // [issue]

    error = Color(0xFFFFB4AB),                 // [issue]
    onError = Color(0xFF690005),               // [issue]
    errorContainer = Color(0xFF93000A),        // [issue]
    onErrorContainer = Color(0xFFFFDAD6),      // [issue]

    background = Color(0xFF161617),            // [issue]
    onBackground = Color(0xFFF7E9EC),          // [issue]
    surface = Color(0xFF161617),               // [issue]
    onSurface = Color(0xFFF7E9EC),             // [issue]
    surfaceVariant = Color(0xFF3E2429),        // [issue]
    onSurfaceVariant = Color(0xFFDCA1AB),      // [issue]
    outline = Color(0xFF9C7A80),               // [issue]
    outlineVariant = Color(0xFF51444A),        // [issue]

    inverseSurface = Color(0xFFF7E9EC),        // [issue]
    inverseOnSurface = Color(0xFF37292C),      // [issue]
    scrim = Color(0xFF000000),                 // [issue]

    // NOT specified by the issue → standard M3 dark derivations:
    // surfaceContainerLowest/Low/Container/High/Highest stay at the M3
    // darkColorScheme defaults; surfaceTint stays at its M3 default (the app
    // reads no surface*Container role explicitly — the tier structure rides
    // M3's own container derivation; AMOLED still collapses every surface +
    // container slot to pure black, §1.2).
)
```

Brand-moment rule (§1.5): the seed #E5194D is RESERVED — it never enters the ColorScheme;
in-scheme UI accent is #FF668F (dark). No `#CSV`-style near-seed values appear in code.

### 1.2 AMOLED + the container behavior

`giffyColors(amoled)` keeps today's collapse SHAPE: background/surface + every container
tier + surfaceVariant + chrome → pure `#000000` (the current code already collapses
surfaceVariant too — preserve). Accent + on-color rows are never collapsed. Since the
issue specifies no container ladder, the M3 defaults carry the tier structure and the
AMOLED branch simply overwrites them to black — no invented hexes either way.

### 1.3 What dies with the old palette

`object GiffyColors` is rewritten to the issue's token table ONLY — single token source
feeding the scheme (`Theme.kt`), never read by composables (batch-15 F5 rule, already
enforced). Dies outright: `Lime` (+ dead `LimeHover/LimePressed`), `Info` cyan,
`BrandRed #D70003`, `TextHigh/TextMid/OutlineSoft`, `Page/Chrome/Widget`, dead members
`Success/Warning/TextLow` (grep-verified unused).

**The lime case explicitly:** the issue's scheme has NO lime. Stateful-affordance accent
becomes the pink `secondary` (#E3BDC4); focus/action accent becomes `primary` (#FF668F).
Because batch 16 converted every former direct-lime read to `colorScheme.secondary`, the
whole lime retirement is a values-only edit — zero call-site color changes anywhere.

### 1.4 Per-surface assignment — every formerly-Lime/BrandRed/Info surface

Inventory (grep, post-batch-16 state: all reads are role reads):

| Surface (file:line) | Today | New scheme role |
|---|---|---|
| Player liked heart, auto-swipe tile tint, speed chip fill, unfilled-progress fill (PlayerScreen 759/775/834/867/916/1030/1053) | secondary (lime) | `secondary` #E3BDC4 — pale pink; see risks §9 |
| Player niche-pill + tag text (759/775), QuickSheet accent rows (QuickSheet 85/99) | secondary (lime) | `secondary` |
| TvPlayerScreen speed chip (323), D-pad speed rows (357), niche pills (446) | secondary | `secondary` |
| VerifiedTick default tint; CreatorLabel tickTint fallback | tertiary (Info cyan) | `tertiary` #E7C16B — verified tick turns gold; **tertiary used SPARINGLY per the issue (badges/highlights) — exactly these current call sites, no new tertiary surfaces** |
| `giffyFocus` focused border; `GiffyPillButton` border/fill; TvPlayerScreen error-pill fill (441) | primary (#D70003) | `primary` #FF668F |
| Settings section headers, blocklist rows (SettingsScreen 518/550); SearchScreen field icon (219); AgeGate title (49); PinLock dots + wrong-PIN (91/111) | primary / error | unchanged roles, recolored by the scheme |
| TvQuickActions panel rows / chips / add-cancel borders | primary ring | per §3 (ring retirement, issue focus tokens) |
| TvTheme border/borderVariant (TvTheme 42–43 — the only non-Theme.kt palette reads left) | static `OutlineSoft`/`BrandRed` | `border = c.outlineVariant` (#51444A, the issue's unfocused-ring color), `borderVariant = c.primary` (§2) |
| PlayerOverlay tokens (:core:ui/PlayerOverlay.kt) | independent | reconciled with the issue's art-scrim rule (§7) |
| AudioBadge black/white; over-video `Color.White` text/icons | static | reconciled per §7 (onSurface text over scrims where feasible) |

### 1.5 Mobile directives from the full issue body (folded verbatim)

- **Dynamic color DISABLED.** The existing opt-in (`GiffyTheme(dynamicColor = …)` +
  `dynamicDarkColorScheme` + the settings flag) is REMOVED in Step 1: the parameter, the
  branch, the pref plumbing, and the caller wiring in `MainActivity.kt:70`. Seed-derived
  neutrality is no longer user-selectable.
- **Seed #E5194D reserved for brand moments only** — splash/hero/logo surfaces. Not a
  scheme member; any future brand moment renders it directly from the token table, never
  through `colorScheme.primary`.
- **In-scheme red usage is tone-shifted per the issue:** #FF668F carries the in-scheme
  red accent on dark (this app only ships dark); the light-mode companion #B3003C equals
  the given `inversePrimary` and is recorded here for completeness, unused.
- **Red-tinted neutrals (#3E2429/#DCA1AB/#9C7A80/#51444A family) carry BOTH form factors** —
  the TV shares the same neutral rows via `giffyTvColors` passthrough; no separate TV
  neutral set is created.

## 2. TV: `giffyTvColors(c)` flow + AMOLED composition

`GiffyTheme(amoled = amoled)` stays the single entry point on BOTH apps; `TvMainActivity`
already wraps the tv-material `MaterialTheme(colorScheme = giffyTvColors(composeColors))`
inside the compose `GiffyTheme`. The rebrand changes values + two border reads only:

```kotlin
fun giffyTvColors(c: androidx.compose.material3.ColorScheme) =
    androidx.tv.material3.darkColorScheme(
        // unchanged passthrough: every slot reads from the compose scheme `c.*`,
        // which now carries the issue's complete role set.
        background = c.background, onBackground = c.onBackground,
        // AMOLED-aware: surface roles come from the ALREADY-collapsed compose scheme,
        // so TV cards join the true-black collapse with zero TV-specific logic.
        surface = c.surfaceContainer,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariant,
        surfaceTint = c.primary,     // only intentional surfaceTint mapping; kept
        border = c.outlineVariant,   // [issue] unfocused rings = outlineVariant #51444A
                                     //   (was a static GiffyColors.OutlineSoft read)
        borderVariant = c.primary,   // [issue] focus border/glow color #FF668F
                                     //   (was a static GiffyColors.BrandRed read)
    )
```

**AMOLED:** the pref lives in `SettingsRepository` (`amoled` Flow; both MainActivities
read it and pass it into `GiffyTheme`). The collapse decision happens once in
`giffyColors(amoled)` before any compose ColorScheme exists; `giffyTvColors` only reads
*that*, so the batch-16 fix (F3: "TV never hardcodes a neutral") keeps working.
New base surface #161617: AMOLED OFF → TV card surfaces ride the M3-default container
tier (unspecified by the issue); AMOLED ON → cards pure #000000 (identical to today,
screencap-verifiable per the batch-16 recipe).

## 3. TV focus: retire `giffyFocus`, migrate to the issue's SPECIFIED focus tokens

### 3.1 The issue's TV focus spec (authoritative — replaces any "derive from scheme" wording)

| Focus state | Issue token | Value |
|---|---|---|
| Focused card/row background | `primaryContainer` | **#7D0030** |
| Focused card/row text + icons | `onPrimaryContainer` | **#FFD9E1** |
| Focus border / glow | `primary` | **#FF668F** |
| Unfocused rings/borders | `outlineVariant` | **#51444A** |

**NEVER purple/blue defaults:** every migrated TV focusable must carry these colors
explicitly (component `colors(focusedContainerColor = primaryContainer,
focusedContentColor = onPrimaryContainer)` or equivalent), because tv-material's fallback
focus states resolve through the theme — and any component that silently falls back to a
system/default purple-blue draw must be caught by the Step-3 walkthrough. The scheme now
presents these tokens as the standard roles, so composition stays THEMED while the values
are issue-pinned: components read `colorScheme.primaryContainer/onPrimaryContainer/primary/
outlineVariant` — no hex literals in composables, honoring "use theming, don't override".

### 3.2 What tv-material 1.0.1 provides — verified-API list + one build-time check

- **tv-material Cards** (already used in TvHome `GifCard`/`Card`) carry native focus
  treatment: focused container-color change + shape tokens + scale-up (our `GifCard`
  remaps it to 1.08). With §3.1's tokens the focused container color is set via
  `CardDefaults.colors(focusedContainerColor = primaryContainer, …)` — themed, not derived.
- **tv-material Surfaces/Buttons** (`Surface` variants, `Button`, `WideButton`) provide
  built-in focus states (focused scale + border + fill) — set to the §3.1 tokens.
- **M3 (`androidx.compose.material3`) components have NO focus visuals** — the root cause
  of every `giffyFocus`: M3 FilterChip/Button react only to press, so shared M3 screens
  hosted on TV need something extra on D-pad. The standard replacement therefore exists
  ONLY as tv-material components.
- **Build-time check (blocks the focus slice, not the design; AAR not decodable in this
  read-only container — limitation stated, moving on):** trial-import
  `androidx.tv.material3.FilterChip` / `androidx.tv.material3.ListItem` to confirm they
  exist in 1.0.1. Where they exist, chips/rows migrate; where not, the *documented
  exception* below applies verbatim.

### 3.3 giffyFocus call-site inventory (grep 2026-10) → replacement per site

Every replacement renders focus with exactly the §3.1 tokens via scheme roles.

| # | Call site | Component | Replacement (focus visuals = §3.1 tokens) |
|---|---|---|---|
| 1 | TvNiches.kt:109 (fillOnFocus=surfaceVariant) | custom clickable row | **tv-material `ListItem`** (if present) or `Surface(focusedContainerColor = primaryContainer, focusedContentColor = onPrimaryContainer)` + `border(focusedBorderColor = primary)` — note: the old `fillOnFocus = surfaceVariant` gives way to the issue's primaryContainer fill |
| 2 | TvQuickActions.kt:359/385 (`QuickAction`, `SpeedAdjustRow`) | M3 `Button` + ring | **tv-material `Button`/`WideButton`** with §3.1 colors; key-handled D-pad rows keep their onKeyEvent |
| 3 | TvQuickActions.kt:450/468, 489/498 (chips, add/cancel) | M3 FilterChip/TextButton | tv-material equivalents per §3.2 |
| 4 | TvPlayerScreen.kt:359/374 (Retry/Skip) | custom Box + ring | tv-material Button (§3.1 colors); error-overlay geometry unchanged |
| 5 | TvPlayerScreen.kt:439 (fillOnFocus=primary) | niche pills | tv-material Surface-based pill — focused fill = primaryContainer, content = onPrimaryContainer, border/glow = primary (the issue's focus trio) |
| 6 | TvHomeScreen.kt:456 (For You scope chips) | M3 FilterChip + ring | tv-material FilterChip if present, else documented exception |
| 7 | TvSourceFeedScreen.kt:140 (Filter ▾ chip, immersive overlay) | M3 FilterChip + 8dp ring | same as #6; ring radius folds to `shapes.small` |
| 8 | SettingsScreen.kt:261/331/371/431 (chip rows TV-hosted; mobile unaffected) | M3 FilterChip + ring | host-decided swap: tv-material FilterChip where available, else documented exception |
| 9 | SettingsScreen.kt:556 (blocklist rows) | Row + ring (no click) | tv-material ListItem / Surface row focus visuals (§3.1) |
| 10 | CustomFeedsScreen.kt:339/353 (cycle/delete buttons) | TextButton + ring | tv-material Button |

**Documented exception (one line in AGENTS-APP if it survives):** M3 chip rows hosted on
TV use components with *no* focus visuals; where tv-material has no matching built-in,
`giffyFocus` stays ONLY on those chips (recolored to the issue trio) — deleted everywhere
else. `GifCard`, `CenterHold`, MENU/quick-actions and the focus-preview logic stay
untouched (input modality + key flow guarantees); they only *read* focus via
`onFocusChanged`.

**Ripple/InteractionSource check:** grep found **zero** custom ripple/ripple-color
overrides app-wide (`RippleColor`, ripple-alpha overrides, custom `LocalIndication` —
none). The single `androidx.compose.material3.ripple()` call (TvNiches row) rides the
standard ripple. Chips already thread their `MutableInteractionSource` into `giffyFocus`,
so the swaps are mechanical.

### 3.4 Further TV directives from the full issue body (folded verbatim)

- **Body text colors:** TV body text uses `onSurface` #F7E9EC; `onSurfaceVariant`
  #DCA1AB only at LARGE text sizes (no small muted labels). Devices gate: audit every
  `color = MaterialTheme.colorScheme.onSurfaceVariant` + `bodySmall/labelSmall` slot on
  TV-hosted screens — where the slot renders small, switch to onSurface or remove the
  muted role (list produced during Step 3 walkthrough dump; shared mobile screens keep
  their small slots — this is a TV-hosting delta, expressed per host).
- **Overscan:** ~48dp safe area on all TV surfaces (10-foot normative). Current TVs
  layouts already dodge edges; verify perimeter padding on TvHomeScreen rows and the
  source-feed grid during the Step-3 walkthrough; add nothing unless a dump shows content
  within the unsafe band.
- **Minimum focusable size:** ~48dp on every D-pad target (the §3.3 migrated rows are the
  affected surface; check the `SpeedAdjustRow`/chip row min-heights at the same time).
- **Typography:** "keep M3 ramp PROPORTIONS, scaled up for TV" is the issue's stance;
  batch 16 owns family/scale and already uses tv-material's own default tokens (the
  standard 10-foot ramp) — the directive is honored. A FURTHER deliberate deviation
  (bigger than the standard TV ramp) stays an OPTION only with a specific device
  legibility report in hand — not built speculatively.

## 4. Custom vs prebuilt component audit (REPLACE / KEEP-CUSTOM)

| Component | Verdict | Reason / call sites |
|---|---|---|
| GiffyPillButton | KEEP-CUSTOM on mobile, **REPLACE-candidate on TV** — colors already re-tint via roles; only TV cases gain a native D-pad focus fill. If the §3.2 check passes, switch the TvMainActivity call sites to a tv-material Button with the §3.1 tokens; else the widget stays (it already IS the standard "focused fill + border" treatment, expressed in M3 primitives). Never hand-tint; style via the scheme only. | TvMainActivity 286/293/300/307/319 + AgeGate (both shells) |
| PillButton (task list) | n/a — grep-verified: the repo has only `GiffyPillButton` under that name | — |
| AudioBadge | KEEP-CUSTOM — no prebuilt badge matches a rounded pill + volume icon over media; black/white over video is the F6a class | unchanged by the rebrand |
| VerifiedTick | KEEP-CUSTOM — already the prebuilt `Icons.Filled.Verified` + scheme tint | tick recolors to gold via `tertiary`, zero edits |
| CreatorLabel | KEEP-CUSTOM — aggregation idiom (10 hand-rolled copies collapsed); no M3 compound owns "@user + tick" | recolors free |
| PlayerOverlay tokens | KEEP — shared scrim/track/secondary-on-video tokens (F6a) are the "theming, not overrides" answer for the media layer | reconciled with the issue's art-scrim range (§7) |
| RefreshFeedPill + rememberScrollingUp | KEEP — already 100% prebuilt (`Button` + AnimatedVisibility); custom part is scroll-position derivation only | recolors free |
| PinLock | KEEP-CUSTOM — no M3 PIN/pattern component; keygrid + haptics + brute-force slowdown is app behavior | colors ride the scheme; no code change |
| AgeGate | KEEP-CUSTOM — legal attestation, normative copy | recolors free |
| GiffyScaffold | already prebuilt (Scaffold + TopAppBar wrapper) | untouched |
| EmptyState | KEEP — 2-line text idiom; no prebuilt shrinks it | recolors free |
| TvQuickActionsDialog | KEEP-CUSTOM — tv-material 1.0.1 has no sheets/side panels | verify focus visuals after Step 3 (its rows migrate per §3) |
| GifTile / GifCard / FocusPreview | out of scope — media surfaces, not theme tokens | focus-preview settle/scale semantics preserved |

## 5. Typography (delta: none) — checklist

- [x] Batch 16 landed the standard M3 ramp: mobile `dmSansTypography()` copies the M3
  defaults; `tvTypography()` uses tv-material's OWN default tokens (the standard 10-foot
  scale) with only the DM Sans family swapped. Slot-per-element mapping reclaimed on TV
  (F8). The issue's "M3 ramp proportions scaled up" directive is satisfied by this exact
  construction; no further scale change unless a specific legibility report demands the
  deliberate-deviation option (§3.4).
- [x] The rebrand changes NO type slots; DM Sans stays (brand family).
- [x] `onSurfaceVariant` only-at-large-sizes rule folded into the Step-3 walkthrough
  (§3.4); typography slots themselves unchanged.

## 6. Shapes — checklist only (no changes)

- [x] `GiffyShapes` (4/8/12/16/28) owned by GiffyTheme (batch 16); ad-hoc radii mapped on
  landing; TV Cards keep tv-material `CardDefaults.shape` tokens.
- [x] Zero shape code touched by the rebrand.
- [ ] Gate: post-Step-3 ktlint+detekt confirm no new literal radii crept into the tree.

## 7. Scrims over artwork — issue rule vs the shared PlayerOverlay tokens

**Issue rule (authoritative):** scrims over artwork/gifs are `scrim` #000000 at **40–60%
alpha**, with text/icons in `onSurface` #F7E9EC (not bare white).

**Reconciliation with the existing token set (:core:ui/PlayerOverlay.kt, used by BOTH
player screens):**
- `PlayerOverlay.scrim = #000000 @ 0.55` — sits INSIDE the issue's 40–60% band. No
  numeric change needed; it simply gains the issue's range as its spec (document 40–60%
  as the working band; 55% remains the chosen value).
- `PlayerOverlay.track = #FFFFFF @ 0.3` (progress track/hairline) and
  `secondaryOnVideo = #FFFFFF @ 0.7` — hairline/track strokes are not text; they stay as
  the media-overlay standard (the F6a class), with one Step-1 note: if any caption/body
  TEXT is drawn via `secondaryOnVideo` on a scrim, migrate its color to
  `colorScheme.onSurface` and keep the alpha in the token (white@70% vs onSurface
  #F7E9EC@70% is visually identical here; the change is bookkeeping for the issue's
  "onSurface text" wording, not a visual delta).
- Scrims ON TOP of AMOLED-black or over artwork keep collapsing correctly because the
  scrim is palette-independent black — no AMOLED interaction.
- The bare `Color.White` over-video text/icon sites (PlayerScreen 791/927/1030/1039/1044/1047,
  TvPlayerScreen 385/411) stay for now; a Step-5 audit maps any TEXT-role ones to
  `onSurface` for the issue's wording; glyphs like the heart/share row can stay white
  (media icons, not themed text) — recorded, not forced.

## 8. Migration order, gates, and device verification

Ordered slices; each ends compile + ktlint + detekt + unit tests green on touched modules,
plus its device gate (CI `check` covers the runner gates).

- **Step 1 — `core/ui/Theme.kt`:** rewrite `GiffyColors` + `giffyColors(amoled)` with the
  §1.1 values verbatim (complete issue table; no fill work remains). REMOVES the dynamic
  color opt-in (parameter + branch + `MainActivity.kt` wiring + settings flag). Optional:
  one minimal `@Test` asserting the issue's on-container pairs meet the M3 minimums
  (house one-test rule); mar the seed rule (§1.5) in the token-table docs.
  Gate: compile `:core:ui` + `:app-mobile`; device Medium_Phone: AMOLED both ways,
  Settings side-by-side, player accents (like/heart/tags/pills/progress), age gate +
  pin lock unchanged visually-except tint.
- **Step 2 — `app-tv/TvTheme.kt`:** `border = c.outlineVariant`, `borderVariant =
  c.primary`; comment update (no more palette reads; last non-Theme.kt read removed).
  Gate: compile `:app-tv`; TV36 AMOLED screencap pixel check (ON = #000000, OFF = the M3
  container tier) + home rows render.
- **Step 3 — focus retirement** (after the §3.2 trial import): §3.3 rows 1→10 in order;
  `app-tv/*` first, then the TV-hosted feature files (SettingsScreen/CustomFeedsScreen).
  Every migrated focusable carries the issue's §3.1 tokens (focused fill primaryContainer,
  focused content onPrimaryContainer, border/glow primary, unfocused ring outlineVariant).
  Gate: compile `:app-tv`; D-pad walk on TV36 — visible standard treatment everywhere
  (uiautomator `focused=true` dump + pixel check that focused fill = #7D0030-family, NOT
  a purple/blue default); CenterHold still fires; quick-actions still open; focus-preview
  settle/unsettle unchanged; PLUS the §3.4 sweeps: onSurfaceVariant-only-late audit
  (large-sizes rule), 48dp overscan safe-area check, ≥48dp targets on migrated rows.
- **Step 4 — component audit sweep (§4 REPLACE cases only)** + doc updates: AGENTS-APP
  "Design-system consistency audit" gains the rebrand note; AGENTS.md session-log entry
  (anchored by this doc).
- **Step 5 — live-visual pass:** mobile player (like/auto-swipe/speed/tags/pills/progress
  over video), TV player (same + Retry/Skip), QuickSheet, RefreshFeedPill, PinLock,
  AgeGate; §7 audit (scrim/onSurface bookkeeping); release smoke after Step 1 (cheap; R8
  doesn't touch resources but the gate is cheap).

## 9. Risks and the contrast audit

**Issue-supplied pairs with design-time baselines (recorded so Step 1's test has
expectations):**
1. primary #FF668F on surface #161617 = 6.5:1
2. onSurface #F7E9EC on surface = 15.35:1
3. onSurfaceVariant #DCA1AB on surfaceVariant #3E2429 = 6.53:1
4. secondary #E3BDC4 on surface = 10.63:1 (tags/pill text)
5. tertiary #E7C16B on surface = 10.54:1 (verified tick)
6. inversePrimary #B3003C on inverseSurface #F7E9EC = 5.98:1
7. error #FFB4AB on surface = 10.65:1
8. onErrorContainer #FFDAD6 on errorContainer #93000A = 7.24:1
9. onSecondaryContainer #FBD9E1 on secondaryContainer #5E4349 = 6.78:1
10. onTertiaryContainer #FFE08D on tertiaryContainer #5B4200 = 7.33:1
11. outline #9C7A80 on surface = 4.74:1 (border affordance, non-text)

**Fill-time checks for the now-specified primary-family rows (Step 1 test, no derived
numbers pre-recorded):** `onPrimary` #5E0B25 on primary #FF668F,
`onPrimaryContainer` #FFD9E1 on `primaryContainer` #7D0030 — both expected ≥4.5:1 text;
if the issue's generator picked a pair that misses, escalate rather than hand-tune.
Also check #FFD9E1/#7D0030 as the TV focus paint in glare (100-nit floor).

**Risk items (owned follow-ups, device-visual evidence decides):**
- **Like-heart recognizability:** liked heart moves to `secondary` #E3BDC4 next to the
  unliked white heart — subtle-tint range rather than the lime pop. Fix path via
  interaction emphasis only (never hand-tinting outside the scheme).
- **Lime deletion is a brand-recognition change:** tags, niche pills, speed chips, liked
  heart and progress fills stop glowing. Acid test: device walk over the §1.4 accent
  surfaces before any release cut.
- **Focus treatment change on TV** re-tests the D-pad usability bar on all ten §3.3 rows;
  the §3.1 tokens eliminate the purple/blue-default failure mode by construction only if
  every component carries them explicitly — hence the Step-3 pixel check.
- **Verified tick goes cyan → gold:** check next to onSurface text and over-video white
  (`tickTint = Color.White` callers unaffected by the scheme).
- **Dynamic color removal** narrows an existing accessibility escape hatch (users who
  prefer system-derived contrast lose it); the issue owns that decision — noted here for
  the record, not relitigated.
- **onSurfaceVariant-only-large** on TV might shrink the muted-text inventory noticeably
  on TV-hosted meta rows (views/dates) — if the walkthrough shows the rule is too strict
  for meta glyphs (icons/counts, not sentences), record a scoped deviation per element
  with device evidence; don't blanket-widen.

## 10. Explicitly not replaced (house rules)

PinLock/AgeGate/GiffyScaffold/GiffyPillButton/PlayerOverlay/EmptyState/TvQuickActionsDialog
were challenged honestly per §4: each either IS built on a prebuilt component already or
has no library equivalent. Nothing speculative cut: no M3 Banner/SegmentedButton/DataRow
slots invented, no new abstractions; the whole rebrand lands as values plus a handful of
focus-component swaps — Theme.kt tokens (+ dynamic-color removal), TvTheme 2 border
lines, focus swaps in ~7 files, docs.
