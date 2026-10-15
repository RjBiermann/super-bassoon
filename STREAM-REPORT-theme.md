# STREAM-REPORT-theme.md — THEME-REBRAND stream (issue #7 "Theme Spec")

Implemented per DESIGN-theme-stream.md §1–§7. Host-only edits + cheap greps; a
prior failed run's placeholder junk in this report file was overwritten.
**No gradle/ktlint/detekt/builds, no jj/git, no emulator** (task rules).

## Per-file changes

### Step 1 — scheme (mobile) 
- `core/ui/…/core/ui/Theme.kt`
  - `GiffyColors` rewritten to the issue's token table verbatim (§1.1): Seed
    #E5194D (reserved brand-moment token, never a scheme member per §1.5) +
    full primary/secondary/tertiary/error/neutral/inverse rows. Dead members
    deleted: Page/Chrome/Widget/BrandRed/Lime(+Hover/Pressed)/TextHigh/Mid/Low/
    OutlineSoft/Success/Info/Warning.
  - `giffyColors(amoled)` = darkColorScheme with the §1.1 rows verbatim;
    un-specified rows (surfaceContainer ladder, surfaceTint) stay at M3
    defaults (nothing invented); AMOLED collapses background/surface/
    surfaceVariant + the whole surfaceContainer ladder to black via
    `scheme.copy(...)` — batch-16 collapse shape preserved, accent+on-color
    rows never collapse.
  - **Dynamic color disabled** per §1.5: `GiffyTheme(dynamicColor)` param +
    `dynamicDarkColorScheme` branch + Build/LocalContext imports removed.
- `core/ui/…/core/ui/PlayerOverlay.kt` (§7 reconciliation): token comments now
  record the issue's 40–60% scrim band (55% stays); `secondaryOnVideo`
  white → `GiffyColors.OnSurface.copy(alpha = 0.7f)` (onSurface-text bookkeeping;
  visually identical), desc text on BOTH player screens follows automatically;
  track/hairline strokes stay white (F6a media standard, not text).
- `app-mobile/…/mobile/MainActivity.kt`: dynamicColor collect + wiring removed;
  `GiffyTheme(amoled = amoled)`.
- `core/datastore/…/SettingsRepository.kt`: `DYNAMIC_COLOR` key, `dynamicColor`
  flow + `setDynamicColor` removed (stored pref stays orphaned — harmless, no
  migration/destructive step; mixed-version safe).
- `feature/settings/…/SettingsViewModel.kt`: `dynamicColor` StateFlow +
  `setDynamicColor` removed.
- `feature/settings/…/SettingsScreen.kt`: Dynamic-color switch row + Build
  import + collect removed; AMOLED row is the theme block now.

### Step 2 — scheme (TV)
- `app-tv/…/tv/TvTheme.kt`: `border = c.outlineVariant` (#51444A, issue 
  unfocused rings), `borderVariant = c.primary` (#FF668F, focus border/glow);
  GiffyColors import dropped — last non-Theme.kt palette reads gone. All role
  passthrough + AMOLED-via-compose-scheme behavior unchanged.

### Step 3 — giffyFocus retirement (§3.3; tv-material 1.0.1 AAR bytecode verified in-session)
- The §3.2 build-time check was done WITHOUT gradle: the tv-material 1.0.1 AAR
  from the gradle cache was unzipped and public APIs + Kotlin-metadata parameter
  names were read from the classes. `FilterChip`/`ListItem`/`Button`/
  `OutlinedButton`/`IconButton`/`Surface` (clickable + selectable) all exist.
  tv `FilterChip` carries `@ExperimentalTvMaterial3Api` in 1.0.1 (bytecode
  annotations) → per-call-site `@OptIn`.
- `core/ui/…/core/ui/Focus.kt`: `giffyFocus` recolored to the issue trio and
  narrowed to its documented-exception role (shared :feature: screens only):
  focused fill = `colorScheme.primaryContainer` (new default), focused border =
  `colorScheme.primary` (3dp), untouched shape/interaction contract. The
  `fillOnFocus` parameter was DELETED (its two call sites migrate away below).
  `GiffyPillButton` focus/press state = primaryContainer fill +
  onPrimaryContainer text (§3.1; shared widget kept for mobile AgeGate).
- `app-tv/…/tv/TvNiches.kt` (#1): the D-pad name-block became tv `Surface(onClick)`
  with focused fill primaryContainer / content onPrimaryContainer / 3dp primary
  border, 12dp shape (geometry parity). CenterHold keymap, first-row
  FocusRequester, MENU pin toggle untouched; name/pin/supporting texts flip to
  onPrimaryContainer via `collectIsFocusedAsState` (tv Surface content color
  doesn't reach M3 Texts). Old surfaceVariant fill eliminated.
- `app-tv/…/tv/TvQuickActions.kt` (#2/#3): `QuickAction` + `SpeedAdjustRow` + the
  picker dialogs' Add/Cancel → new private `TvButton` (tv Button; rest fill =
  scheme primary + onPrimary text = the old M3 look; focused =
  primaryContainer/onPrimaryContainer + 3dp primary border; tv Text content so
  the focus flip is native); the radio-feed / checkbox-ref rows → tv `Surface`
  with the §3.1 tokens + onPrimaryContainer label flip. SpeedAdjustRow's
  LEFT/RIGHT key handling, add/cancel enabled logic, CenterHold semantics kept.
- `app-tv/…/tv/TvPlayerScreen.kt` (#4/#5): Retry/Skip + Start-over/Back-to-feed →
  `TvFocusButton` (tv Button, transparent rest fill = old text-button look,
  rest content = previous secondary/onSurface choices, focused §3.1 trio); niche
  pills → tv `Surface` pill (rest = secondary text, focused fill
  primaryContainer + onPrimaryContainer + primary border — the §3.3 row-5 trio);
  error-overlay geometry, `overlayRetryFocus`/`endFocus` FocusRequesters,
  AnimatedVisibility auto-hide unchanged.
- `app-tv/…/tv/TvHomeScreen.kt` (#6): For You scope chips → tv `FilterChip`
  (@OptIn(ExperimentalTvMaterial3Api::class)) with focused primaryContainer/
  onPrimaryContainer colors, rest ring `outlineVariant` 1dp, focused border
  primary 3dp; tv Text label so chip content colors apply; scope pref plumbing
  unchanged.
- `app-tv/…/tv/TvSourceFeedScreen.kt` (#7): Refresh + Sort & filter → tv
  `IconButton` + tv `Icon` (focused §3.1 colors + focused primary border; rest =
  tv defaults = surface-on-surface, i.e. today's invisible-at-rest look);
  end-of-feed footer "Refresh" → tv `Button` (transparent rest, §3.1 focus);
  refreshAndReturnToTop + firstCardFocus untouched.
- `app-tv/…/tv/TvMainActivity.kt` (§4 REPLACE-candidate, check PASSED): the five
  home-header `GiffyPillButton` pills → private `TvHeaderPill` (tv Button): rest
  = today's neutral 8% fill + onSurface text + 1dp primary border, extraLarge
  28dp shape + 20/10 padding parity; focused = §3.1 trio. Shared GiffyPillButton
  stays for mobile AgeGate.
- **Documented exception (kept giffyFocus):** `feature/settings/SettingsScreen.kt`
  chip rows + blocklist rows and `feature/feed/CustomFeedsScreen.kt`
  cycle/delete buttons — shared :feature: modules can't host tv-material
  components without widening the mobile/desktop shells' dependency graph, so
  the doc's verbatim exception applies: giffyFocus stays, recolored to the issue
  trio via the new default (fill primaryContainer, border primary; M3 chips keep
  their own outlineVariant rest ring). Mobile is unaffected (no-op on touch).
- Docs: `AGENTS-APP.md` gained the "Issue-#7 theme rebrand" section (token
  values, per-surface migration map, exception, owned device gates);
  `AGENTS.md` gained a session-log entry (batch 24) with the same content in
  session-log form.

## Anatomy checks passed host-side
- Contrast (issue §9 fill-time rows): onPrimary/onPrimary 4.87:1 (≥4.5 AA pass,
  no escalation), onPrimaryContainer/primaryContainer 8.49:1; all other issue
  pairs ≥ 6.7:1 (python WCAG calc).
- `grep` gates: zero direct `GiffyColors.*` reads outside :core:ui (only a
  historical comment in TvTheme); zero references to deleted palette members in
  code; zero remaining `giffyFocus` call sites in app-tv (survivors only in the
  documented-exception shared files); zero `Color(0x…` literals outside
  Theme.kt; no leftover doc-named role literals.
- Edited files: no line >140 cols, no trailing whitespace, no tabs, no duplicate
  imports, no unused-import stragglers found by name-scan (getValue/setValue
  delegate pairs kept per ktlint convention), import blocks re-sorted to the
  project's ASCII style (ktlint sorts aliases in place), brace/paren balance
  verified per file.

## Deviations (all justified against the doc)
1. **Anchors re-located by content**: the doc's §3.3 line numbers predate streams
   A–H (FeedsTabStrip/ScrollToTopFab/refresh+sort icons/end-of-feed rows/picker
   dialogs all exist now). Every §3.3 row was matched by component/behavior, e.g.
   row 7 "Filter ▾ chip" = today's Refresh/Sort&filter M3 IconButtons.
2. **End-of-feed popup + picker-dialog rows migrated** though not in the doc's
   §3.3 inventory (they post-date its grep): the doc's global rule ("giffyFocus
   deleted everywhere else") leaves no legal rest state — under the same §3.1
   tokens, not improvised.
3. **TvNiches used the doc's `Surface` branch** (`ListItem` also exists in 1.0.1,
   but the Surface path preserves the row structure/CenterHold/ring geometry
   byte-for-byte; the doc allows either).
4. **TvQuickActions picker rows (radio/checkbox) are tv `Surface` rows**, not
   tv FilterChips: they are selection rows with M3 RadioButton/Checkbox
   payloads, matching the §3.2 Surface-based row recipe the doc gives for #1/#9.
5. **Content colors via tv Text / collectIsFocusedAsState**: tv-material
   contentColor does not propagate into M3 Texts; where a focus-tied text flip
   was required, the existing GiffyPillButton onFocus pattern (approved by the
   doc's audit) was reused minimally.
6. **TvMainActivity header pills**: kept the GiffyPillButton rest-look via
   explicit colors so the rebrand doesn't silently redesign the home header;
   focus states are the issue's tokens verbatim.
7. Doc §5 optional `@Test` for the on-container contrast pairs: not built
   (no :core:ui test source set exists and no build tooling this session;
   ratios verified host-side and recorded above).

## Risks / owed gates
- **No compile/ktlint/detekt/test run this session** (task rules): tv-material
  named parameters were taken from decoded bytecode + Kotlin metadata (not
  proxy-compiled); a normal build session should verify :core:ui/:core:datastore/
  :feature:settings/:feature:feed/:app-mobile/:app-tv before any release cut.
  Highest-risk API shapes (all double-checked): ButtonDefaults.colors/border,
  IconButtonDefaults.colors/border, ClickableSurfaceDefaults.colors/border/shape,
  FilterChipDefaults.colors/border, Border(BorderStroke).
- **Device gates owed** (doc §8): TV36 D-pad walk — focused fill must be
  #7D0030-family on every migrated row (never a purple/blue default), CenterHold
  + MENU + quick-actions re-walk, GifCard focus-preview settle/unsettle, nav-bar
  overscan + ≥48dp targets, Medium_Phone AMOLED-both-ways + Settings side-by-side
  + player accent walk (like heart #E3BDC4 next to white = recognizability acid
  test, §9), and the §3.4 onSurfaceVariant-only-at-large-sizes walkthrough (TV
  meta rows may need a scoped deviation), release smoke (cheap; R8 doesn't touch
  resources).
- **Like-heart/secondary recognizability + verified tick cyan→gold** are the
  doc's own flagged visual risks — device evidence decides any interaction-
  emphasis follow-up, never hand-tinting.
- TV-hosted Settings/CustomFeeds focused rows now render the primaryContainer
  fill under unchanged label colors (M3 label color can't flip from a modifier);
  contrast on the fill remains ≈4.6:1 (AA-normal pass) — the TV-consumer exact
  onPrimaryContainer flip is only blocked by the module split; if the walkthrough
  objects, the fix is a tv-material host swap, not a token change.
- **Dynamic-color removal** narrows an accessibility escape hatch — decided by
  the issue, recorded per the doc, not relitigated.
