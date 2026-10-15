# DESIGN — Settings page organized into multiple pages (GitHub issue #17)

Stream: settings · Target repo: `super-bassoon` · Author: read-only design worker
Status: **design only** — no code, no builds. Trace of actual files at the pinned merge (DB v11).

Scope guard: shared `SettingsScreen` stays one shared Composable hosted by both shells
(`:app-mobile` MainActivity, `:app-tv` TvMainActivity); extract no shell-specific screens.
No NavHost dependency added (navigation-compose is not in `gradle/libs.versions.toml` today;
the Navigation 3 / navigation-compose wave is a later stream).

---

## 1. Block-surface inventory (issue text: "currently there is no way to view blocked items")

Traced: `:core:database` ContentFilter pipeline (AGENTS-CONTENT-FILTER.md stages 0–3)
and every piece of UI that writes or reads a "hide this" state.

### 1a. Block-ish state that EXISTS and IS viewable today

| # | What | Storage | View surface today | Gap notes |
|---|---|---|---|---|
| B1 | Blocked creators | `creator_prefs` (BLOCKED) | SettingsScreen "Blocked creators" list, unblock X | interleaved mid-page, easy to miss |
| B2 | Blocked tags | `tag_prefs` (BLOCKED) | SettingsScreen "Blocked tags" list, unblock X | — |
| B3 | Blocked keywords | `keyword_blocks` | SettingsScreen "Blocked keywords" list, unblock X | — |
| B4 | Favorited creators | `creator_prefs` (FAVORITED) | SettingsScreen "Favorited creators" list (prefers/derives `ContentPrefsDao.favoriteCreatorsFlow()`, Daos.kt:177) | — |
| B5 | Blocked custom feeds (merged groups, DB v10) | `custom_feeds.state == "BLOCKED"` | CustomFeedsScreen per-row state cycle Tab→Blocked→Neutral (CustomFeedsScreen.kt:95–105, label :334–345); BLOCKED rows open no feed (:320) | labeled "Blocked" only as a row suffix in the CustomFeeds lists; ContentFilter stage 2 turns its tag/creator refs into GLOBAL blocks (AGENTS-CONTENT-FILTER "Merging groups — DONE") — **a global filter is described on a screen named "My feeds", not Settings** |
| B6 | Favorited custom feeds (Tab) | `custom_feeds.state == "FAVORITED"` | CustomFeedsScreen rows + mobile home Pinned section | not a block; listed for completeness |
| B7 | Hidden-this-week counter | `hide_counts` (rolling 7d) | SettingsScreen "Hidden this week: N" single integer (SettingsScreen.kt:192–198; `hideCount(weekStartMs…)` no per-reason split) | shows the *effect*, not the *thing* |

### 1b. Block-ish state that exists but has NO view surface

| # | What | Storage | Today |
|---|---|---|---|
| M1 | **Blocked/blocked-feeds from Settings** | `custom_feeds` (BLOCKED) | You must know to go More ▾ → "My feeds" and interpret the row state cycle; Settings never mentions it. The issue's core complaint: a user who blocked a tag-bundle group "sees nothing" in Settings except fewer gifs. |
| M2 | **Favorited tags** | `tag_prefs` (FAVORITED) — `favoriteTagsFlow()` exists (Daos.kt:186–187) and FeedViewModel writes unfavoriteTag (FeedViewModel.kt:275) | **Written but never rendered anywhere** (only consumed by tests). A user who taps "Favorite tag" in the quick sheet has no list to see/update. |
| M3 | Age-gate reset option | `SettingsRepository.resetAgeGate()` (SettingsRepository.kt:62) | spec'd in AGENTS-APP.md ("Settings option to re-run/reset gate status") but **exposed nowhere** — dead repo API. Candidate General-panel row BUT it is new behavior, flag below. |

### 1c. Content-hiding prefS (NOT blocks — must stay out of the Blocked lens)

| # | What | Storage | View |
|---|---|---|---|
| P1 | Orientation filter (any/vertical/horizontal) | DataStore `orientation_filter` (`SettingsRepository`) | SettingsScreen.kt:340–378 chip row |
| P2 | Verified creators only | `verified_only` | SettingsScreen.kt:379–404 toggling row |
| P3 | Data saver (SD streams) | `data_saver` | SettingsScreen.kt:480–504; forces static posters in focus preview / autoplay |
| — | Promoted-gif filter | DTO field, filter stage 0 | automatic, uncounted (no-ads) — intentional |
| — | Niche "blocked" sets | N/A | **No niche-block concept exists**: niche-group merge skips `niche:` refs in stage 2 (niche → tag expansion is not local data — doc'd, not guessed). Niche rows are Join/Leave (server subscription), not blocks. |
| — | Blocked gifs | N/A | no gif-level block state exists anywhere in the schema |

So "view blocked niches" and "view blocked gifs" are **non-features** — there is no state to view.
The Blocked panel gets an honest one-line note rather than empty-world scaffolding.
What actually answers the issue = **B1–B5 + M1 + M2 reachable from one "Blocked" sub-page.**

---

## 2. Today's SettingsScreen anatomy (flat page — file:line enumerate)

`feature/settings/src/main/kotlin/com/rjbiermann/giffyviewer/feature/settings/SettingsScreen.kt` (641 lines): one `LazyColumn`, 14+ sections, GiffyScaffold top bar (Close-as-back at :159–164), SAF launchers :102–133, inline WebView login branch :138–155, TV gates as booleans.

| Section (render order) | file:line | State source (SettingsViewModel.kt unless noted) | TV-visible |
|---|---|---|---|
| Account + sign-in (AuthSection inline, WebView branch) | :162–186 | `feature:auth` AuthViewModel | yes |
| "Nothing blocked yet…" hint | :187 (empty-predicate item above the four block sections) | `blocked` empty predicate | yes |
| Favorited creators list | :188 | `blocked.favorites` | yes |
| Blocked creators list | :189 | `blocked.creators` | yes |
| Blocked tags list | :190 | `blocked.tags` | yes |
| Blocked keywords list | :191 | `blocked.keywords` | yes |
| Hidden this week | :192–198 | `hiddenThisWeek` | yes |
| App lock (PIN) + SetPinDialog | :200–240 | `pinHash`, `setPin/removePin`; SetPinDialog :613–641 | yes |
| Grid columns chips (1/2/3/Auto) | :239–269 (`showGridColumns` gate) | `gridColumns` | **no** |
| Autoplay in feed row | :270–298 (`showFeedAutoplay`) | `feedAutoplay` | **no** |
| TV home row size chips | :300–339 (`showTvRowHeight`) | `tvRowHeight` | **yes (TV-only)** |
| Orientation chips | :340–378 | `orientationFilter` | yes |
| Verified creators only | :379–404 | `verifiedOnly` | yes |
| Video fit chips (fit/crop) | :405–441 | `videoFit` | yes |
| Video quality chips | :442–479 | `videoQuality` | yes |
| Data saver row | :480–504 | `dataSaver` | yes |
| AMOLED + Dynamic color rows | :505–554 | `amoled`, `dynamicColor` | yes (dynamic = Android 12+) |
| Preferences backup (Export/Import) | :555–580 | SAF + `exportJson()/importJson()` | yes |

TV chip-row pattern in use here (must survive regrouping): `focusGroup()` + `fillMaxWidth()` on every chip row + `giffyFocus(interactionSource)` per chip (:245–259, :315–338, :353–375, :420–437, :456–474) — this is the Settings chip-skip fix (batch 6); do not touch while moving.
Host parameters: `showGridColumns`/`showFeedAutoplay` (default true; TV passes false, TvMainActivity.kt:284–291), `showTvRowHeight` (default false; TV true), `requestInitialFocus` (TV true, `firstFocus` = `FocusRequester`, :166–170 wired onto AuthSection's first button via `firstButtonModifier`).

Mobile host: MainActivity.kt:223 `SettingsScreen(onBack = { showSettings = false })` — one boolean overlay; TV host: TvMainActivity.kt:284–293 + BACK via host BackHandler stack (TvMainActivity.kt:125). Both shells navigate to Settings exactly the same way → any new sub-page state can live **entirely inside `:feature:settings`** with zero host changes.

---

## 3. Chosen pattern: in-screen sub-panel state (Delicate explicitly NO NavHost now)

**Rejected: true NavHost destinations.** navigation-compose is not on the version catalog yet and a later stream (G wave) migrates apps to new navigation. Burning detail-route work now = rework at migration. **Rejected: rememberSaveable boolean-per-panel** — doesn't scale to 6 panels, no ordering, no future-route mapping.

**Chosen: one `rememberSaveable` String in `SettingsScreen`.**

- A sealed `enum class SettingsPage(val label: String)` in a NEW tiny file `SettingsGroups.kt`
  (Root, General, Video, Feeds, Blocked, Backup, About) — each entry maps 1:1 to a future
  navigation route (`"settings/{page}"`); the enum is the route table when stream G lands.
- Root = today's grouped list rendered as **rows that open panels** (each row: title + current-state caption + chevron; still chips for 2-choice values? **NO — root is 100% plain rows**; all chip rows move inside their panel, keeping the GiffyScaffold Close top bar as-is).
- `var page by rememberSaveable { mutableStateOf("root") }` + `BackHandler(enabled = page != Root) { page = "root" }` — BACK inside a panel returns to root; BACK at root falls through to the existing host `onBack = { showSettings = false }`. Same pattern precedent already lives in the shared `feature:auth` WebViewLoginScreen (:5, :46) and FeedScreen (:137).
- Why `rememberSaveable` (survives process death, acceptance criterion): panel index restored by the `saveable` bundle directly inside the Composable — **not** placement-dependent VM state; no `SavedStateHandle` change in SettingsViewModel is needed. Both shells host Settings as the single top-level overlay, so death+relaunch re-enters the same panel (mobile: `showSettings` is stored via `rememberSaveable` already at MainActivity.kt:76/87 — confirm at implementation; if it is not, the host loses its overlay itself, which is EXISTING behavior, not a new regression).
- Panel content = private composables **in the same file** (`SettingsScreen.kt`) — no new module, no new shapes. Each panel wraps its rows in the same `LazyColumn`-style layout inside one shared `GiffyScaffold`. `requestInitialFocus` semantics preserved per panel: the panel's FIRST focusable takes it (TV-hosted shared list screen MUST have the first row focused on open — batch 18 lesson; the current `firstFocus` FocusRequester + AuthSection `firstButtonModifier` wiring stays on the General panel's account row, and the panel-open path requests focus on the first row of the newly open panel when `requestInitialFocus` is true).
- Entry animation: none new — instant panel swap, matching M3 "cut" transition, no new tokens.

### Why this is minimal-churn
- Hosts: **zero changes** (they only set booleans and take `onBack`).
- `SettingsViewModel`: **zero changes** to existing state; only ADDITIONS (see §5).
- All TV chip machinery (focusGroup/fillMaxWidth/giffyFocus) is lifted verbatim into panels — no re-work of the batch-6/12 fixes; the `requestInitialFocus` FocusRequester seed just moves to the panel's first row.
- Navigation migration later: the `SettingsPage` enum becomes 6 static routes; panel composables become destination lambdas with `page = "root"` replaced by the router back-stack pop. No panel implementation changes.

---

## 4. Groups (proposed) and the state-key map

Tracked against actual `SettingsRepository` keys + `ContentPrefsDao` flows. Every existing section is mapped; every row keeps its existing handler.

### General
| Row | file:line today | State key |
|---|---|---|
| Account (AuthSection + WebView sign-in branch) | :162–186, :138–155 | feature:auth AuthViewModel |
| App lock (PIN) + SetPinDialog | :200–240, :613–641 | `pinHash` (DataStore `pin_hash`) |
| AMOLED true-black + Dynamic color | :505–554 | `amoled`, `dynamicColor` |
| Grid columns chips (mobile) | :239–269 | `gridColumns` |
| Autoplay in feed row (mobile) | :270–298 | `feedAutoplay` |
| TV home row size chips (TV) | :300–339 | `tvRowHeight` |
| *(optional, OUT of initial slice)* Age-gate reset | — | `resetAgeGate()` (M3 above — dead API today) — new destructive-ish behavior; needs user sign-off before it ships (see §8 open questions). |

### Video
| Row | file:line today | State key |
|---|---|---|
| Video fit (Fit/Crop) | :405–441 | `videoFit` |
| Video quality (Auto/Highest/720p/480p via `VideoQuality.VALUES`) | :442–479 | `videoQuality` |
| Data saver (SD streams/posters) | :480–504 | `dataSaver` |

No "playback speed" row exists in Settings (speed is player-only: session-long via quick sheet + hold-to-2×); no pref key exists. NOT designed in (no-guess rule).

### Feeds & filters
| Row | file:line today | State key |
|---|---|---|
| Orientation chips (Any/Vertical/Horizontal) | :340–378 | `orientationFilter` |
| Verified creators only toggle | :379–404 | `verifiedOnly` |
| Per-feed defaults + shuffle | — | **out of slice, no damage**: per-feed sort/pref live on the feed surfaces (`FeedFilterDialog`, `feedPrefs(feedPrefs` in SettingsRepository); "Surprise me" shuffle is a session state (`shuffleSeed`), no repo key. A settings row for them would duplicate existing UI. |

### Blocked ← solves the issue headline
| Row | file:line today | State key |
|---|---|---|
| Blocked creators list + unblock X | :189 (`section`) | `dao.blockedCreatorsFlow()` |
| Blocked tags list + unblock X | :190 | `dao.blockedTagsFlow()` |
| Blocked keywords list + unblock X | :191 | `dao.blockedKeywordsFlow()` |
| Favorited creators list + unfavorite X | :188 | `dao.favoriteCreatorsFlow()` |
| **NEW** Favorited tags list + unfavorite X | — (M2: written, never viewed) | `dao.favoriteTagsFlow()` (exists, Daos.kt:186) + `dao.unfavoriteTag` (exists, Daos.kt:168) |
| **NEW** Blocked feeds list + one-tap state cycle → Neutral | — (M1: only visible in CustomFeedsScreen) | `db.customFeedDao().all()` → client-side filter `state == "BLOCKED"`; cycle rewrite = same line pair CustomFeedsScreen.kt:99–105 |
| Hidden this week counter | :192–198 | `hiddenThisWeek` |
| Niche / gif "blocks" honesty line | — | plain body text: "Niches and individual gifs are not blockable; blocked tag-bundle feeds above are the niche-based filter." |

Blocked-panel unblock/unfavorite rows REUSE the existing `section()` renderer (:582–611) verbatim; the new blocked-feeds rows reuse the state-cycle logic already proven in CustomFeedsScreen (no new DAO query needed; the "Blocked feeds" list is a client-side filter of the `custom_feed_dao().all()` StateFlow, ≤ dozens of rows).

### Backup
| Row | file:line today | State key |
|---|---|---|
| Export / Import + snackbar text | :555–580, launchers :102–133 | `exportJson()/importJson()` (v3 backup incl. custom_feed state) |

### About
| Row | file:line today | State key |
|---|---|---|
| App name + "Unofficial viewer" disclaimer + version tag | NEW, no surface exists today | version string from `-PversionTag` BuildConfig (both builds already compute it, app-*/build.gradle.kts:31–34) — complete result is one static non-editable panel; noop interactivity. |

ROOT list = one row per Non-Root panel with a `caption` (current-value summary, e.g. "Data saver · On", "Orientation · Any") + `[ChevronRight]` icon; captions derive from the same StateFlows, no new writes.

---

## 5. Implementation order (grouped by file)

1. **`feature/settings/.../SettingsPage.kt` (NEW, ~25 lines)** — `enum class SettingsPage(val title: String) { General, Video, Feeds, Blocked, Backup, About }`. No behavior, no logic. (`Root` is not an enum entry — it's `page = null` semantics via the same String key.)
2. **`feature/settings/.../SettingsViewModel.kt` (+~30 lines)** — only ADDITIONS:
   - `val favoritedTags: StateFlow<List<String>>` from `dao.favoriteTagsFlow()`.
   - `val blockedFeeds: StateFlow<List<CustomFeedRow>>` from `db.customFeedDao().all()` filtered client-side to `state == "BLOCKED"`.
   - `fun feedStateBlockedFeed(feed: CustomFeed): Unit` = upsert same `state = "NEUTRAL"` (the CustomFeedsScreen cycle, written once here so both surfaces share it — the CustomFeedsScreen cycle may switch to call it at zero risk, otherwise the two diverge by copy — **prefer refactoring CustomFeedsViewModel's cycle to delegate**, keeping ONE owner of the "BLOCKED→NEUTRAL" transition).
   - All existing StateFlows, wiring, and export/import code untouched.
3. **`feature/settings/.../SettingsScreen.kt` (main refactor)** —
   a. hoist 5 private panel composables out of the single `LazyColumn` body into `LazyColumn { panel("General", …) … }` style **per section** but plumb a `page: String` argument that shows exactly one page at a time:
      - root = 6 caption rows + chevron + Account row FIRST (replaced only by panel header on entry),
      - each panel = its section block as a LazyColumn content lambda, **cursor unchanged** inside it (chip rows, toggleable rows, unblock rows all stay the same code-shape, only the enclosing item boundaries are relabeled).
   b. add `rememberSaveable page`, `BackHandler(enabled = page != ROOT)`, initial-focus wiring per panel (`LaunchedEffect(page)` when `requestInitialFocus` — replaces the current single-surface `LaunchedEffect(Unit)` layer with the panel variant per the TV-hosted-list lesson; first focusable inside the new panel, not the previous list).
   c. SAF launchers + WebView login branch + snackbar stay at the top level (panel-independent) — they are SettingsScreen-global and their `return`-style early-exit shape (:138–155) must be preserved EXACTLY (the WebView branch `return`s before GiffyScaffold).
4. **`feature/settings/src/test/...SettingsViewModelTest.kt` (add — no settings test exists today; reuse the data-layer fakes already present in `:core:database`/`:feature:feed` test sources)** — smallest new unit checks per the one-test rule: (i) `blockedFeeds` filter yields only `state == "BLOCKED"`, (ii) the shared BLOCKED→NEUTRAL state-cycle handler upserts `NEUTRAL` (and FAVORITED→BLOCKED keeps the CustomFeedsScreen order).
5. **Gates** — compile + ktlint + detekt + unit tests green on every touched module: `:feature:settings` (the change), plus `:app-mobile` and `:app-tv` compile parity since both shells host the screen. Device/UI verify of the two shells is later-wave work per A1/A2.

---

## 6. TV navigation (leading edge acceptance)

- SUB-page OPEN: root rows are plain focusable rows; DOWN/UP as usual; CENTER opens the panel, only ONE row swap per press (tap opens, no double-trigger — CenterHold scope is the giffyOnBlock hold geometry for LONG-press banners only; sub-page OPEN is a plain click, no conflicts).
- SUB-page CLOSE: the shared Settings `BackHandler(enabled = page != Root)` consumes BACK first (top-most BackHandler in the win); BACK at root then exits to the host stack — **no new router, no host changes** (TvMainActivity BackHandler at :125 pops the `showSettings` boolean as today, behaves as one BACK = panel-close, second BACK = exit).
- D-pad interplay: each panel's first focusable (chip / toggleable / footer row) is wired to `requestInitialFocus` per the batch-6 fix; the existing `focusGroup` + `fillMaxWidth` + `giffyFocus` ring pattern in chip rows keeps D-pad down motion intact everywhere a chip row moves as a block — no traversal re-audit is needed per move because the row's **modifier chain is untouched**, only its parent is re-named.
- leaving-edge D-pad risk = the root list collapse: `showTvRowHeight` gates the TV-only chips INSIDE the General panel identically (gate booleans unchanged), and no `if (showX)` branch inside a LazyColumn flow has to move code. **Nothing in TvMainActivity changes** — the host doesn't know sub-pages exist.

---

## 7. Acceptance criteria

| # | Criterion | Proof expected |
|---|---|---|
| A1 | Blocked items reachable on mobile: root → "Blocked" panel shows B1/B2/B3 lists, favorited B4, NEW favorited tags M2, NEW blocked-feeds M1 | on-device run of Phone34 later wave; unit-level coverage of the state flows |
| A2 | Blocked items reachable on TV: same panel, D-pad (rows → CENTER → BACK closes to root, second BACK exits) | on-device TV36 later wave; settings requestInitialFocus lands on first panel row |
| A3 | BACK inside a panel = root panel; BACK at root = exit/screen close (host `onBack` unchanged) | unit: `BackHandler` asserted by tooling; device manual |
| A4 | Panel index survives process death (open "Blocked", force-stop, relaunch → same panel) | rememberSaveable bundle record; manual device check is the norm here (both shells already host the screen) |
| A5 | No regrouped regressions: every existing row mapping (section table §2) still binds to the SAME VM setter it had before | per-file diff review; the VM untouched except additions |
| A6 | CustomFeedsScreen BLOCKED↔Neutral cycle keeps working and both surfaces share the same transition handler | existing unit fakes + one new VM test |
| A7 | ktlint + detekt + unit tests green on `:feature:settings` and dependents (`:app-mobile`, `:app-tv` — the screens host it), per house rules | `./gradlew :feature:settings:ktlintCheck :app-mobile:detekt …` (exact task set run at implementation) |
| A8 | No new workers/dependencies added (no navigation-compose, no new activity, no new route table) | dependency diff clean; nav migration slot preserved for stream G |

---

## 8. Open questions / explicit out-of-design items

1. **Age-gate reset (M3)** — `resetAgeGate()` is a live repo API with no UI and would be the natural General-panel row; it's also the only destructive-ish pref (logs the user out of the content gate, re-triggers the gate on next start). **Not committed in this design.** If the user wants it, it's a 10-line row in General + the existing `DataStore` field, gated to ONE confirmation dialog (same class as Groups delete confirm).
2. **Per-feed defaults / shuffle in "Feeds & filters"** — deliberately NOT added: the repo already surfaces per-feed sort/prefs through `FeedFilterDialog` & quick sheet and per-feed shuffle is session state; a settings copy would be a second controller of the same pref without a user ask.
3. **Playback speed / mute / auto-swipe as settings rows** — deliberately NOT added: none is currently a settings key and all three already have first-class player surfaces; duplicating them in a panel is a new product call, not a settings-reorg task.
4. **Blocked niches / blocked gifs** — do not exist (no schema state, no filter pipeline stage). The Blocked panel states this in one line instead of faking empty-state rows. If the platform later gains a niche-block state (the merged-groups "blockable niche: refs" case that is currently a documented skip), the panel list is ready to accept it by adding one more `section`.
5. **"Hidden this week" per-reason split** — schema today stores one rolling count; the panel keeps the single integer. A per-reason split is a DB-v12 schema change and out of this slice's scope (regrouping ≠ new schema).
6. **Panel scroll-position restoration** — not targeted; only the panel INDEX is saved. If a panel is long (General on TV) and the user wants to reenter scrolled-to-foot later, the LazyColumn position restarts at 0 — acceptable, matches the whole-surface behavior today.
7. **About panel content** — kept minimum (name / disclaimer / version tag from `-PversionTag`); no OSS-license list, no changelog — those are distribution-surface features and issue #17 is a page-organization fix.
