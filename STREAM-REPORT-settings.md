# STREAM-REPORT — settings (issue #17: Settings organized into sub-pages + blocked-inventory surface)

Implemented per `DESIGN-settings-stream.md`. No gradle/ktlint/detekt/jj/git/emulator runs (task rules);
proof = content-anchored greps + brace/paren balance + import-order + line-length/static hygiene checks.

## What landed

### 1. `feature/settings/.../SettingsPage.kt` (NEW)
- `enum class SettingsPage(val title: String) { General, Video, Feeds("Feeds & filters"), Blocked, Backup, About }`
  + `internal const val ROOT_PAGE = "root"` (root intentionally not an enum entry — design §5.1).
  Future route table for the navigation wave; no behavior.

### 2. `feature/settings/.../SettingsViewModel.kt` (additions only)
- `val favoritedTags: StateFlow<List<String>>` from `dao.favoriteTagsFlow()` (M2 — written since the
  quick sheet shipped, never rendered) + `fun unfavoriteTag(tag)` (same `ContentPrefsDao.unfavoriteTag`
  write the `FeedViewModel.toggleFavoriteTag` toggle path uses; no favorites-cache eviction — matches
  that path, verified at FeedViewModel.kt:269-281).
- `val blockedFeeds: StateFlow<List<CustomFeedEntity>>` — client-side filter of `customFeedDao().all()`
  (no new DAO query), pure helper `internal fun blockedFeedRows()` at file bottom for the unit test (M1).
- `fun unblockFeed(feed)` — writes via the shared owner: `customFeedDao().setFeedState(feed, feed.nextCycleState())`
  (from BLOCKED the cycle's next state is NEUTRAL = unblock; one owner of both mapping and write).
- Existing VM state/wiring untouched; constructor FQ parameter types converted to imports (no FQ chains).
- Class functions: 20 (detekt thresholdInClasses 41); file top-level functions: 1 (files threshold 13).

### 3. One owner of the custom-feed state transition
- `core/database/.../Daos.kt` (+7 lines): `suspend fun CustomFeedDao.setFeedState(feed, state)` —
  the single write path for a state change.
- `feature/feed/.../CustomFeedsScreen.kt`: cycle mapping extracted verbatim to
  `fun CustomFeedEntity.nextCycleState()` (public top-level, alongside `customRefSummary` precedent);
  `CustomFeedsViewModel.cycleState` now delegates: `setFeedState(feed, feed.nextCycleState())`.
  Zero behavior change; the Settings panel shares the identical transition (A6).

### 4. `feature/settings/.../SettingsScreen.kt` (main refactor, 641 → ~1035 lines)
- In-screen sub-panel state per design §3: `var page by rememberSaveable { mutableStateOf(ROOT_PAGE) }`
  (panel index survives process death), `BackHandler(enabled = panel != null) { page = ROOT_PAGE }`.
  BACK inside a panel → root (top-most handler, host stacks unchanged); BACK at root falls through to
  the host handler → both hosts' `onBack` semantics and booleans are UNTOUCHED
  (zero host changes; mobile MainActivity.kt + TvMainActivity.kt call sites identical).
- `GiffyScaffold` stays one shared scaffold: title becomes the panel title while a panel is open and the
  same Close top-bar button pops the panel first, then exits (host close only at root).
- WebView sign-in early-return branch, SAF launchers and snackbar stay screen-globals, shape preserved exactly.
- Initial focus (requestInitialFocus): `LaunchedEffect(page)` seeds the current page's first focusable —
  account row's first button at root (existing rootFocus/firstButtonModifier wiring preserved verbatim),
  the panel's first focusable while a panel is open, back to the account row on panel close. `runCatching`
  guards About/static pages (no attached focusable). FocusRequester attaches are per-panel first row
  (General → PIN TextButton; Video → Fit chip; Feeds → Any chip; Blocked → first populated section's
  first row / first feed row).
- Root = account block (unchanged, first) + six plain caption rows (title + current-value caption +
  chevron; no chips at root — design §3). Blocked caption is live: "N creators · N tags · N keywords · N blocked feeds".
- Panels (every original row moved 1:1, same handlers, same giffyFocus/focusGroup chip machinery):
  - **General** — App lock (PIN + SetPinDialog), AMOLED, Dynamic color (S gate), Grid columns chips
    (showGridColumns), Autoplay in feed row (showFeedAutoplay), TV home row size chips (showTvRowHeight).
  - **Video** — Video fit · Video quality (`VideoQuality.VALUES`) · Data saver.
  - **Feeds & filters** — Orientation chips, Verified creators only row.
  - **Blocked** (issue headline) — "Nothing blocked yet" hint (predicate widened to also cover favorited
    tags / blocked feeds, deviation noted below), favorited creators / favorited tags (NEW M2) /
    blocked creators / blocked tags / blocked keywords via the shared `section()` renderer (+optional
    firstFocus param; keys/values unchanged), NEW "Blocked feeds" list (M1) rendered as id-keyed rows
    (names can repeat → can't go through section()) with one-tap `unblockFeed`, a one-line note that
    blocked feeds merge their refs into global blocks, "Hidden this week: N" (moved here per design §4)
    and the honesty line "Niches and individual gifs are not blockable…".
  - **Backup** — the same Export/Import button pair (launchers passed as lambdas, unchanged SAF behavior).
  - **About** (NEW, MIN per §8.7) — "Giffy Viewer", the unofficial-viewer disclaimer, and the hosting
    app's version read through the platform API (`PackageInfo.versionName` — versionName holds the
    `-PversionTag` tag in both shells). No OSS-licence list, no changelog. Static; no interactivity.
- Functions in file: 11 (SettingsScreen, RootPanel, settingsRow, GeneralPanel, VideoPanel, FeedsPanel,
  BlockedPanel, BackupPanel, AboutPanel, section, SetPinDialog) — under thresholdInFiles 13.
- All fully-qualified chains in the file (AuthViewModel, WebViewLoginScreen, TokenStore.Pkce,
  FocusRequester, ExperimentalMaterial3Api @OptIn, AlertDialog/OutlinedTextField/KeyboardOptions/
  KeyboardType, ripple) converted to lexicographic imports; ≤140 cols, no trailing whitespace.

### 5. Test (`feature/settings/src/test/.../SettingsViewModelTest.kt` NEW — first test in the module)
- Two minimal checks per design §5.4: (i) `blockedFeedRows` yields exactly the BLOCKED subset;
  (ii) the shared cycle keeps FAVORITED→BLOCKED (CustomFeedsScreen order intact) and the settings unblock
  writes the `BLOCKED → NEUTRAL` copy through `setFeedState` (inline recording `CustomFeedDao`).
- `feature/settings/build.gradle.kts`: + `testImplementation(libs.kotlinx.coroutines.test)` (runTest).
- No other dependencies/nodes added; no navigation-compose; no new workers (A8).

## Groups, deviations, risks

**General panel** — files: SettingsScreen.kt (GeneralPanel), none else. Difference from design §4:
the Account block stays ONLY on the root (design §4's General table listed Account, §5.3a puts it first
on root — followed §5.3a; rendering AuthSectiona twice would duplicate sign-in state UI and fight the
initial-focus seeding). Deviation accepted, zero row lost (Account renders at root exactly as before).

**Video / Feeds-and-filters panels** — files: SettingsScreen.kt only. 1:1 moves; `orientationFilter`
and `pinHash` collections hoisted to screen level (captions need them); `verifiedOnly/videoFit/
videoQuality/gridColumns/feedAutoplay/tvRowHeight` still collect inside their panel items (same idiom
as before, same lint-clean pattern).

**Blocked panel** — files: SettingsScreen.kt, SettingsViewModel.kt, Daos.kt, CustomFeedsScreen.kt.
Deviations:
1. "Nothing blocked yet" honest-empty predicate widened to include favorited tags + blocked feeds
   (original: creators/tags/keywords only) — the panel's inventory view is wider than the old list.
2. Blocked-feeds rows keep duplicate-name safety by id-keyed rendering instead of the String-keyed
   `section()`; visual shape is section-identical (title + name + X).
3. section() gained an optional trailing `firstFocus` param (default unchanged behavior for callers).
4. Blocking-surface honesty line per design §1c (niches/gifs are not blockable — no fake empty states).
   Nothing speculative added beyond the design's inventory (no age-gate reset — design §8.1 says not committed).

**Backup / About panels** — files: SettingsScreen.kt. About version reads the host app's versionName
via PackageManager instead of a BuildConfig field in the library module (avoids a new buildConfig
wiring + duplicated versionTag default; standard platform API; zero host changes).

**TV (§6)** — nothing in TvMainActivity/MainActivity changed. Sub-page open is a plain row click
(no CenterHold involvement); BACK ordering: SettingsScreen's BackHandler composes above the hosts'
overlay BackHandlers (mobile :87 / TV :125) and is enabled only inside a panel → panel-close first,
second BACK exits as today. Root rows use the exact TvNiches focus pattern
(`giffyFocus(rowInteraction, fillOnFocus) + clickable(interactionSource, ripple)`), panel chip rows kept
their focusGroup/fillMaxWidth/giffyFocus chains verbatim. A2's "settings requestInitialFocus lands on
first panel row" is implemented (see seeding above).

**grep-verify results**
- Every original section (account, nothing-blocked hint, 4 block sections, hidden-this-week, pin,
  grid columns, autoplay, tv row size, orientation, verified-only, video fit, video quality, data saver,
  theme, backup) is present exactly once in its mapped panel — no dangling copy; brace/paren balanced.
- All SettingsRepository keys still reachable (flow reads + setters): gridColumns, feedAutoplay,
  tvRowHeight, orientationFilter, verifiedOnly, videoFit, videoQuality, dataSaver, amoled, dynamicColor,
  pinHash (+ setPin/removePin), exportJson/importJson.
- Only state-transition write site tree-wide (non-test): `Daos.kt setFeedState` → both surfaces delegate.

## Residual risks / open items
1. **No compile run** (forbidden this session): syntax/type verified by static review only; the riskiest
   assumptions are (a) `IconButton(interactionSource=)` overload availability in the pinned M3, (b)
   `PackageInfo.versionName` nullability nuance (guarded by runCatching + isNullOrEmpty fallback), (c)
   `Icons.AutoMirrored.Outlined.KeyboardArrowRight` presence in material-icons-core (same-set precedent:
   `Icons.AutoMirrored.Filled.ArrowBack` in GiffyScaffold).
2. **Device gates (A1/A2/A4) are later-wave work by design** (Phone34/TV36): panel-index restore, BACK
   ordering and D-pad traversal need the emulator pass; unit coverage covers the state/filter logic only.
3. Leftover doc line in CustomFeedsScreen KDoc references `[com.rjbiermann.giffyviewer.core.database.setFeedState]`
   (fully-qualified KDoc link — a comment, not code; no ktlint impact).
4. `favourite tags` unfavorite does not evict the favorites cache — intentionally identical to the
   quick-sheet toggle path (tag favorites don't touch the creator-favorites cache).

Changed files:
- feature/settings/src/main/kotlin/com/rjbiermann/giffyviewer/feature/settings/SettingsPage.kt (new)
- feature/settings/src/main/kotlin/com/rjbiermann/giffyviewer/feature/settings/SettingsScreen.kt
- feature/settings/src/main/kotlin/com/rjbiermann/giffyviewer/feature/settings/SettingsViewModel.kt
- feature/settings/build.gradle.kts (test dep)
- feature/settings/src/test/kotlin/com/rjbiermann/giffyviewer/feature/settings/SettingsViewModelTest.kt (new)
- core/database/src/main/kotlin/com/rjbiermann/giffyviewer/core/database/Daos.kt (setFeedState owner)
- feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/CustomFeedsScreen.kt (delegate)
