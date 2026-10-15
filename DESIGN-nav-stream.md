# DESIGN-nav-stream — Migrate mobile + TV to `androidx.navigation:navigation-compose`

**Issue #13** (user decision already recorded): `androidx.navigation:navigation-compose` (stable), NOT navigation-3.
Goal: "back should take to the previous page — whether it is player, feed, or anything; and back must restore scroll and focus position."
Scope: design only. No code changes in this pass. Both app shells migrate; shared `:feature:*`/`:core:*` stay screen-and-VM libraries (they don't grow a navigation dependency except two `@Serializable` annotation families, §6).

---

## 1. Current stack — file:line evidence of what is being replaced

### Mobile — `app-mobile/.../mobile/MainActivity.kt` (255 lines)
| Fact | Evidence |
|---|---|
| Route stack = 9 parallel `rememberSaveable` flags (`playerStartIndex`, `showSettings/Niches/Explore/Following/Collections/Search/CustomFeeds`, `aboutNiche: Pair<String,String>?`) | MainActivity.kt:80–89 |
| ONE activity-level `BackHandler` popping the top flag via a `when` chain | MainActivity.kt:89–111 |
| `FLAG_KEEP_SCREEN_ON` keyed on `playerStartIndex` | MainActivity.kt:113–122 |
| Player branch: `PlayerScreen(startIndex, viewModel = hiltViewModel(), …)` — the VM is the SAME activity-scoped `FeedViewModel` the grid uses | MainActivity.kt:126–135 |
| Every "open feed" seam routes through `feedViewModel.open(FeedSource…)` on that one shared VM (Explore/Following/Search/NicheAbout/CustomFeeds all call `open` then clear their flag) | MainActivity.kt:143–151, 160–168, 178–193, 211–220 |
| FeedScreen renders `vm.source` and owns its own 1-level hop-back machinery | MainActivity.kt:235–248 (feed branch); FeedScreen.kt:144–145 `BackHandler(enabled = canGoBack) { viewModel.back() }` |
| In-VM back: `previousSource` remembered by `open()`, `canGoBack` StateFlow, `back()` restores ONE level | FeedViewModel.kt:140–152 (`previousSource`, `_canGoBack`, `back()`), open() at :153–176 |
| FeedScreen internal source switches (chips/tabs/pinned/custom) call `viewModel.open(...)` directly — 8 sites | FeedScreen.kt:199, 247, 254, 269, 310, 341, 363, 721 |
| Player pages the SHARED VM pager (`viewModel.gifs.collectAsLazyPagingItems()`), shares `likedIds`/`isLoggedIn` (Room-backed) and mute/data-saver via `settings` | PlayerScreen.kt:126–134 (signature), :136 (`gifs`), :154–156, likes sync :265 |
| Grid scroll state lives inside FeedScreen composition (`rememberLazyGridState`, L611 area) — survives overlay swaps via `rememberSaveable`, resets to top on VM source swaps | FeedScreen.kt:611, 745 (`scrollToItem(0)` on switch) |

**Note (latent bug found while mapping):** `onOpenAccount` from the player sets `showSettings=true` (MainActivity.kt:134-ish), but the `when` chain checks the player branch FIRST — settings can never render while the player is open. Navigation makes this a real push; behavior will change (improvement) — documented in §8.

### TV — `app-tv/.../tv/TvMainActivity.kt` (408 lines)
| Fact | Evidence |
|---|---|
| Route stack = parallel `remember` vars — **NOT saveable**: `player: Pair<List<Gif>, Int>?` (process death loses the player today), `openFeed: FeedSource?`, `nicheAbout`, `showSearch/Collections/CustomFeeds/Settings/Niches` | TvMainActivity.kt:93–101 |
| ONE Root-level `BackHandler` with a `when` chain | TvMainActivity.kt:129–140 |
| Batch-22 ribbon-state hoisting: `homeListState`/`feedGridState` hoisted at Root so branch swaps don't kill them — `feedGridState` is SHARED across every feed destination | TvMainActivity.kt:150–152; passed to TvSourceFeedScreen at :185 |
| Player payload = full window list (`Pair<List<Gif>, Int>`) passed by param; back = clear the pair | TvMainActivity.kt:164–175 |
| Watch-position flush survives cancellation: `LaunchedEffect` positions sampler with `finally { withContext(NonCancellable) { flushWatchPosition() } }` — `flushWatchPosition()` shared write, ≥3s floor | TvPlayerScreen.kt:114–127, :465–482; DisposableEffect teardown :99 |
| Home row = pills + More ▾ dropdown opening `openFeed`; pinned niches/creators open feeds; home renders `TvHomeScreen(listState = homeListState)` | TvMainActivity.kt:270–399 (pills 289–348, TvHomeScreen 392–399) |
| TV feed VMs: `TvNicheFeedViewModel` (per-feed, takes `source` param) created fresh-ish per openFeed today via activity store | TvMainActivity.kt:177–190; TvSourceFeedScreen.kt:51, 196 |

**Why navigation-compose is the right tool here:** every hand-rolled seam above (flag stack, when-chain BackHandler, Root-hoisted scroll states, in-VM one-level hop stack, `previousSource`) IS a partial re-implementation of the navigation library's back stack + per-destination saved-state. The issue's ask ("back to previous page with scroll & focus") is exactly what `NavHost` provides natively: entries keep `rememberSaveable` state (scroll) while covered, and entries scope ViewModelStores (hop feeds get fresh pagers with their own history).

---

## 2. Version pin (checked live against Google Maven, 2026-10)

`maven-metadata.xml` for `androidx/navigation/navigation-compose` (lastUpdated 2026-09-23): latest stable **2.10.2** (2.10 line rc/stable 2026; 2.9.8 is the newest 2.9 patch). Existing catalog already contains `hilt-navigation-compose 1.4.0` (pulls nav 2.9.1 transitively — our explicit 2.10.2 pin wins, no conflict; `hiltViewModel()` API is unchanged).

Exact catalog edits (gradle/libs.versions.toml):

```toml
[versions]
# after lifecycleVm = "2.11.0"
navigationCompose = "2.10.2"          # androidx.navigation:navigation-compose, stable 2.10 line
```
```toml
[libraries]
# after hilt-navigation-compose
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

**Serializability prerequisite (type-safe routes).** Nav 2.9/2.10 type-safe routes are `@Serializable` route classes; they also let us carry `FeedSource` — and on TV the player window `List<Gif>` — as route arguments directly. Today NONE of these are `@Serializable`:
- `FeedSource` — sealed interface + data object/data classes (:feature:feed, FeedSource.kt:7–85). Needs `@Serializable` on every subtype + the `kotlinx-serialization` plugin on `:feature:feed` (kotlinx.serialization default-compiler is already in the stack as a build-scoped choice, same as :core:network).
- `Gif` + `NicheRef` — plain data classes (:core:model, Gif.kt:4–31). Needs `@Serializable` + the plugin on `:core:model`.
- app modules NULL — route classes live in the app modules, so `app-mobile` and `app-tv` need the `kotlinx-serialization` plugin alias (`libs.plugins.kotlin.serialization`, already in the catalog [plugins] block).

No other module changes. Gif is a flat record (primitives, `List<String>`, `List<NicheRef>`) — serialization-safe; the route JSON lives only in nav saved-state (internal, not a public format).

---

## 3. Proposed graph — MOBILE

### 3.1 Routes (type-safe, NewMainActivity-adjacent in `mobile/` package)

```kotlin
@Serializable data object MainExplore
@Serializable data object MainCollections
@Serializable data object MainCustomFeeds
@Serializable data object MainFollowing
@Serializable data object MainNiches
@Serializable data class MainNicheAbout(val id: String, val name: String)
@Serializable data class MainSearch        // stays on stack after submit (suggestions survive back)
@Serializable data class MainFeed(val source: FeedSource? = null)
//        ^ null source = "the home feed as seeded" (start destination)
//        ^ VM source is restored via FeedViewModel.open (see 3.3)
@Serializable data class MainPlayer(val startIndex: Int)
@Serializable data class MainSettings(val showAuth: Boolean = false)
```

NavHost declares only `MainFeed` as `startDestination`; `MainPlayer` is pushed over it; overlays push/pop always.

### 3.2 MainActivity shape (sketch)

```kotlin
@Composable private fun Host(hint: LayoutHint) {
    val confirmed by settings.ageConfirmed...
    val pinHash by ...
    var unlocked by rememberSaveable { mutableStateOf(false) }
    if (pinHash != null && !unlocked) PinLockScreen(...)
    else if (!confirmed) AgeGate(...)
    else MobileNavHost(hint)              // age gate + pin stay OUTSIDE the NavHost (unchanged semantics)
}

@Composable private fun MobileNavHost(hint: LayoutHint) {
    // ONE FeedViewModel, Activity-scoped — same instance as today (see 3.3 for the
    // LocalViewModelStoreOwner trap: inside NavHost content, default hiltViewModel()
    // means NavBackStackEntry scope, NOT activity).
    val feedViewModel: FeedViewModel = hiltViewModel()
    val controller = rememberNavController()
    val backStack by controller.currentBackStackEntryAsState()

    // FLAG_KEEP_SCREEN_ON moved from `playerStartIndex` to a destination check:
    DisposableEffect(backStack?.destination?.hasRoute<MainPlayer>()) {
        if (backStack?.destination?.hasRoute<MainPlayer>() == true)
            window.addFlags(FLAG_KEEP_SCREEN_ON) else window.clearFlags(FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(FLAG_KEEP_SCREEN_ON) }
    }

    NavHost(controller, startDestination = MainFeed(source = null)) {
        composable<MainFeed> { entry ->
            val src = entry.toRoute<MainFeed>().source ?: feedViewModel.seedSource
            FeedScreen(
                modifier = Modifier.fillMaxSize(),
                gridColumns = hint.gridColumns,
                // player / overlays navigate; scroll state is remembered by NavHost per entry
                onOpenPlayer = { index -> controller.navigate(MainPlayer(index)) },
                onOpenSettings = { controller.navigate(MainSettings()) },
                onOpenSearch = { controller.navigate(MainSearch()) },
                onOpenNiches = { controller.navigate(MainNiches) },
                onOpenExplore = { controller.navigate(MainExplore) },
                onOpenFollowing = { controller.navigate(MainFollowing) },
                onOpenCollections = { controller.navigate(MainCollections) },
                onOpenCustomFeeds = { controller.navigate(MainCustomFeeds) },
                onOpenAccount = { controller.navigate(MainSettings(showAuth = true)) },
                // NEW callback replacing the 8 internal viewModel.open(...) call sites (§3.4)
                onFeedNav = { source -> controller.navigate(MainFeed(source)) },
                playerFactory = playerFactory,
                viewModel = feedViewModel,
            )
        }
        composable<MainPlayer> { entry ->
            val route = entry.toRoute<MainPlayer>()
            PlayerScreen(
                startIndex = route.startIndex,
                onBack = { controller.popBackStack() },
                onOpenAccount = { controller.navigate(MainSettings(showAuth = true)) }, // now reachable (§1 note)
                viewModel = feedViewModel,           // SHARED pager/likes seams preserved
                playerFactory = playerFactory, settings = settings, db = db,
            )
        }
        composable<MainExplore> { ExploreScreen(onBack = { controller.popBackStack() },
            onOpenCreator = { u -> controller.navigate(MainFeed(FeedSource.Creator(u))) },
            viewModel = hiltViewModel()) }          // entry-scoped = fresh per visit (today: stale carry-over)
        // MainNiches (onOpenAbout → push MainNicheAbout(id, name)), MainNicheAbout (onOpenCreator/Niche
        // → push MainFeed; no manual clear — nav back handles it), MainFollowing, MainCustomFeeds
        // (onOpenFeed → push MainFeed), MainSearch (onSubmit → push MainFeed(Search(q)) and stay on
        // the stack), MainCollections, MainSettings — all thin: route push/pop replaces set-flag + clear-flag.
    }
}
```

Shells keep their callback signatures (`onBack` etc.); screens never learn about NavController — MainActivity maps callbacks to `navigate`/`popBackStack`. `onOpenAccount` stays a player seam (no new surface receives it; §1 notes it becomes actually reachable under nav).

### 3.3 ViewModel scoping decision — precise (the "biggest trap")

**Decision: `FeedViewModel` stays at ACTIVITY scope (explicit `hiltViewModel()` at NavHost level, passed by parameter); all overlay VMs become entry-scoped (default `hiltViewModel()` inside destinations).**

Why FeedViewModel stays shared:
- `PlayerScreen` consumes `feedViewModel.gifs` (the LIVE pager), `likedIds` (Room-backed — survives table-level), plus source-scoped feed prefs. A `MainPlayer`-entry-scoped FeedViewModel would page a fresh/empty pager — behavioral break. Passing the activity-scoped VM into the player keeps every seam (existing likes sync at PlayerScreen.kt:265, mute `settings.muted` :153, auto-swipe :156) untouched.
- The feed hop entries (§3.4) still work per-entry because UI state (scroll) is per-entry `rememberSaveable`, and the VM "source" is re-driven by each entry on recomposition (below).

`LocalViewModelStoreOwner` trap (call out precisely): inside a `NavHost` destination's content, `hiltViewModel()` defaults to the NavBackStackEntry's ViewModelStore. Every `hiltViewModel()` call that TODAY lives in MainActivity would silently change meaning if moved into destination lambdas unless passed from the NavHost scope. Rule for the implementation: MainActivity keeps acquiring ALL VMs it passes today at `MobileNavHost` scope (top-level, before `NavHost {`), passing them down; only screens that are naturally per-visit (Explore, Search, Collections, CustomFeeds, Following, NicheAbout/NicheJoin) use in-destination `hiltViewModel()` — intentionally entry-scoped (fixes today's stale carry-over).

**FeedViewModel delta (only mechanical deletions, no architecture change):**
- DELETE `previousSource`, `_canGoBack`, `canGoBack`, `back()` (FeedViewModel.kt:140–152) and their use in `open()` (history bookkeeping lines).
- DELETE `FeedScreen`'s hop BackHandler (FeedScreen.kt:144–145) — the nav stack owns back.
- `open(feed: FeedSource)` KEEPS: per-feed sort adoption (:154–166 adopt block) + `userNavigated = true` + source set. `notifyRefreshError`/`shouldRevertToTrending`/seeded-ForYou fallback semantics (gate 3, airplane-mode) are untouched: nav navigation between non-overlap surfaces does NOT touch the VM (Explore → back keeps `vm.source == seeded ForYou`, `userNavigated == false` — same as today, where overlays don't call `open`).
- Every `MainFeed` entry re-asserts its source on (re)composition (see below) — that is what makes covered-entry history work while the VM stays single-sourced:

```kotlin
// inside MainFeed destination
LaunchedEffect(src) { if (feedViewModel.source.value != src) feedViewModel.open(src) }
// effect: chip hop away → back → restore exactly-open(src) semantics
// (sort adoption + userNavigated sticky true — same as today's behavior)
```

### 3.4 Feed hops become back-stack entries (25 lines of behavior gain)

The 8 internal `viewModel.open(...)` sites in FeedScreen (tabs Continue/Top-Week/Liked, custom feeds, pinned chips, creators from rows/tags) become `onFeedNav(source) → controller.navigate(MainFeed(source))`. Result:
- Back walks the FEED hop history with per-entry scroll restore — today's hop back (`viewModel.back()`) only remembered ONE level and reset scroll to top on swap (FeedScreen.kt:745).
- VM stays coherent because every `MainFeed` entry re-asserts its source (§3.3).
- Generation reset-emit (`S3` wedge fix, `resetGenerationPagingData`) is unchanged — source/compose flow still runs through `FeedViewModel.gifs` flatMapLatest.

- EXPOSE one property: `val seedSource: FeedSource get() = seededForYou` (today private, FeedViewModel.kt:68) so the start route `MainFeed(source = null)` can resolve the seeded default without reaching into `FeedRepository` again.

### 3.5 Player route param strategy — index (gif id rejected with reason)

Route = `MainPlayer(startIndex: Int)` — same parameter the UI produces today (`onOpenPlayer(index)`, grid position) and the same `rememberPagerState(initialPage = startIndex)` seam (PlayerScreen.kt:147).
**gifId rejected because:** the player pages the SHARED VM's live pager, not a static list; resolving an id→index requires a loaded list at cold-restore (not available); it would change the `startIndex` composability for zero restore gain — nav-entry saved state restores the Int across process death exactly as today's `rememberSaveable playerStartIndex` (MainActivity.kt:81) does. Index-vs-regrown-pager drift across process death is pre-existing and unchanged.

---

## 4. Proposed graph — TV

### 4.1 Routes (type-safe, `tv/` package)

```kotlin
@Serializable data object TvHome
@Serializable data object TvNiches
@Serializable data class TvNicheAbout(val about: FeedSource.Niche)
@Serializable data object TvSearch
@Serializable data object TvCollections
@Serializable data object TvCustomFeeds
@Serializable data object TvSettings
@Serializable data class TvFeed(val source: FeedSource)
@Serializable data class TvPlayer(val gifs: List<Gif>, val startIndex: Int)
```

Player payload `List<Gif>` works BECAUSE of §2 (Gif/NicheRef `@Serializable`). Size: a loaded window is 20–60 compact records — order-of-magnitude fine for saved-instance state (well under the ~1 MB binder limit; risk note R3 shrinks with `startIndex`-only fallback if it ever bites, but don't build that now — YAGNI).

### 4.2 TvMainActivity shape (sketch)

```kotlin
@Composable private fun Root() {
    ... theme Surface / CompositionLocalProvider stays (TvMainActivity.kt:60–90 — untouched) ...
    Column(fillMaxSize().padding(48.dp, 27.dp)) {          // 5% overscan margins stay
        val confirmed by ... ; val pinHash by ...
        val unlocked by remember { mutableStateOf(false) }  // NOT saveable — same as today, PIN on cold start
        when {
            pinHash != null && !unlocked -> PinLockScreen(...)
            !confirmed -> AgeGate(...)
            else -> TvNav()                             // NavHost replaces the whole else-chain below :164
        }
    }
}
```

Inside `TvNav()` (parallels §3.2):
- Home destination = exact block currently under `else ->` (TvMainActivity.kt:269–399): pills, More ▾, TvHomeScreen. `onOpenGif = { gifs, i -> nav(TvPlayer(gifs, i)) }`, `onOpen*` → appropriate `nav(TvX)`.
- `TvSourceFeedScreen(source = route.source, gridState = rememberLazyGridState(), feedViewModel = <activity-scoped FeedViewModel for quick actions>, viewModel = hiltViewModel() /* TvNicheFeedViewModel, entry-scoped per feed */ ...)`.
- `TvPlayer(gifs, startIndex, feedViewModel = activityScopeVm, ...)`: TvPlayerScreen signature unchanged; onBack pops; **watch-position flush untouched** (`TvPlayerScreen.kt:465–482` `finally/NonCancellable` runs on the entry's composition disposal — pop triggers it exactly like today's flag clear).
- **Batch-22 hoisted states REPLACED, not kept:** `homeListState` moves INTO the TvHome destination (`rememberLazyListState()` — its saveable state is restored by nav); the Root-hoisted `feedGridState` is deleted (per-feed `rememberLazyGridState()` inside the TvFeed destination restores per feed — today one shared grid state was reused across every feed, which is exactly what per-entry navigation removes).
- **Keep-screen-on flag:** TV never sets one today (no FLAG_KEEP_SCREEN_ON in app-tv) — don't add one; the mobile destination check (§3.2) does not apply.

### 4.3 D-pad focus restoration (the "focus position" piece of the ask)

Nav restores scroll for free, but FOCUS needs an explicit save/restore pattern (TV cards are focus-driven, and `FocusRequester`/focus state is composition-transient). Pattern per destination with a focusable grid/row (new, tiny — matches the house rule "non-trivial logic ships with one minimal self-check"):

```kotlin
// inside TvFeed destination
var lastFocus: Pair<Int, Int> by rememberSaveable { mutableStateOf(-1 to -1) } // (rowIndex or col, itemIndex)
// GifCard onFocus → lastFocus = index-pair
// restore: once per entry-appear (guard flag), not on every count change:
LaunchedEffect(items.itemCount, lastFocus.first, lastFocus.second) {
    if (lastFocus.second >= 0) {
        gridState.scrollToItem(lastFocus.second)   // or row/item math per row type
        // capture the item's FocusRequester (reuse the per-card requesters FocusPreview already owns)
    }
}
```

Home rows save `(listIndex, itemIndex)` analogously inside TvHomeScreen via existing optional params (a `FocusRestore`-shaped helper in `:app-tv` — no `:core:*` dependency creep). Session lesson to honor: a TV-hosted screen must have `requestInitialFocus` on its first focusable (CustomFeedsScreen pattern) — home keeps its existing `focusRequester.requestFocus()` LaunchedEffect (TvMainActivity.kt:276–277).

### 4.4 `CenterHold`, FocusPreview, TvQuickActions
Untouched — they operate INSIDE screens/players; the migration only changes which composable hosts them. GiffyPlayer / Media3 config unchanged.

---

## 5. Migration order (mechanical, per app; one slice = one reviewable commit)

**Stage 0 — additive, compiles without NavHost (both apps):**
1. gradle/libs.versions.toml: `navigationCompose = "2.10.2"`, `navigation-compose` library line (§2).
2. app-mobile/build.gradle.kts & app-tv/build.gradle.kts: add `alias(libs.plugins.kotlin.serialization)` + `implementation(libs.navigation.compose)` (compile-stdlib-only deps at this stage).
3. :feature:feed/build.gradle.kts, :core:model/build.gradle.kts: `alias(libs.plugins.kotlin.serialization)`; annotate `FeedSource.*`, `Gif`, `NicheRef` with `@Serializable`.
4. Gate: `./gradlew :core:model :feature:feed check` → compile + ktlint + detekt.

**Stage 1 — mobile (one commit; MainActivity.kt + FeedScreen.kt + FeedViewModel.kt only):**
5. Extract route classes into MainActivity.kt (ktlint pathways: avoid LongMethod detekt by one `@Composable private fun MobileNavHost` + tiny destination composable funcs per route).
6. Wire NavHost per §3.2; delete flag vars (MainActivity.kt:80–89), the when-chain BackHandler (:89–111), the in-VM back machinery (FeedScreen.kt:144–145, FeedViewModel.kt:140–152), the 8 `viewModel.open(...)` hops → `onFeedNav`.
7. Gate: build + detekt + ktlint + unit tests (:feature:feed tests — `ForYouDefaultTest` must still pass; generation/reset-emit seams untested but untouched).

**Stage 2 — TV (one commit; TvMainActivity.kt only at first, then focus-restore):**
8. Route classes + TvNav() replacing the else-chain (:164–399); MainFeed/TvFeed destination re-assert semantics shared (same LaunchedEffect pattern, referencing `TvNicheFeedViewModel.source`).
9. Delete Root hoisted home states; inside-destination `rememberListState/GridState` + focus-restore slice (§4.3).
10. Gate: build + detekt + ktlint (+ `CenterHoldTest`, `TvRowHeightTest`, `FilteredEmptyHintTest` must pass).

**Stage 3 — DEVICE PASS (both emulators, list in §8). Nothing merges before this.**

**Rollback:** any stage is one `jj restore` away from the workspace parent; no DB/schema/migration coupling (nav replaces COMPOSITIONHOST state only; DataStore/Room keys untouched).

---

## 6. Explicitly NOT migrating (untouched by design)

- **GiffyScaffold** (:core:ui) — layout/inset shell, orthogonal to nav.
- **ContentFilter** and the whole `:core:*` network/DB/datastores/players — never touched.
- **AgeGate / PinLockScreen flow** — stays BEFORE the NavHost (the gate is gating, not navigation).
- **WebViewLoginScreen's screen-internal BackHandler** (:feature:auth/.../WebViewLoginScreen.kt:5,46) — a webview-history guard, unrelated to route back; reuse as-is.
- **`TvPlayerScreen` internals** (watch flush, CenterHold, speed,…) — untouched.
- **GiffyFocus / FocusPreview / focus rings** — unchanged (§4.3 focus-restore is NEW one-time work; the rings themselves stay).
- **"No deep links":** no VIEW/BROWSABLE intent filters are added; routes are internal only. Share intents unchanged.
- **kotlin-serialization should NOT overfly to Gif DTOs** (`:core:network` DTOs stay as they are) — annotations on `:core:model` only.

---

## 7. Risks & hidden costs

| # | Risk | Mitigation / decision |
|---|---|---|
| R1 | **`LocalViewModelStoreOwner` inside destinations is NavBackStackEntry** — silent entry-scoping breaks the shared FeedViewModel/player seams. | Rule in 3.3: acquire the SHARED VMs (FeedViewModel, services, factories, `db`) at NavHost scope; only naturally-fresh VMs use in-destination `hiltViewModel()`. Flag in the PR description. |
| R2 | rememberSaveable player index across process death (grid regrown, index may point at different gif). | Pre-existing (mobile MainActivity.kt:81 had the same fragility). Accepted; document, don't fix (out of the issue's core ask). |
| R3 | TV player route carries the window `List<Gif>` → serialized state size on process-death save. | Window is tens of records; fine. If a TransactionTooLargeException ever surfaces, fallback = route only `startIndex` + re-derive window via a session-scoped VM — NOT built now. |
| R4 | Detekt may flag `LongMethod` on rewritten Activity composables & new route-alignment companions. | Extract destination composables (planned in step 5/8); detekt config otherwise unchanged. |
| R5 | Feeds under a covered entry have their composition DISPOSED — overlay-powered `LaunchedEffect`/collectors pause. | Equivalent to today's branch-swap (composables left the composition there too). `notifyRefreshError`/stale-error revert semantics re-checked in device pass (A2/A3 fixes must behave — batch-22 invariant). |
| R6 | new callback `onOpenAccount` from player now actually navigates (settings overlays the player — reachable UX change, fixes a silent no-op). | Accept as the intent of the seam; verify on device (back-from-settings-to-player, keep-screen-on stays while player covered). |
| R7 | Kotlin-serialization on :core:model suggests "API-owned serialization format" — reviewers may ask. | Add a one-line comment on Gif/NicheRef/FeedSource annotations: "nav route/saved-state payloads only." |
| R8 | Focus restore on TV (§4.3) is new D-pad code — the known failure mode is focus traps/`directional search` skipping restored rows. | Device walk mandatory in §8 (with uiautomator dumps, not screenshots); `CenterHold` suppression unchanged (player tap semantics). |

---

## 8. Device verification list (both emulators, 0 FATALs required throughout)

Mobile (Medium_Phone-class AVD):
1. Home grid → open tile → player → BACK: same tile's scroll offset restored, no grid flash (S3 reset-emit fires only on source changes, not on pop).
2. Home → Explore → BACK: Home grid scroll preserved (already true today via rememberSaveable — regression check that nav doesn't break it); Explore state fresh per visit (intended change).
3. Home chip Continue → chip Liked → chip Trending: back walks 3 feed entries, each restoring grid scroll to its own position.
4. Home → Search → submit (Feed(Search)) → BACK: back returns to search screen (draft/history preserved), second BACK → home.
5. Process death IN the player → relaunch → player entry restores at its saved startIndex; feed grid restored behind on back (pre-existing item-alignment fragility unchanged, R2).
6. Player → onOpenAccount → settings now actually shows (fixes the §1 latent no-op) → back → player STILL at its gif, keep-screen-on honored while the player is covered.
7. Airplane-mode cold start: cached Trending renders / For You reverts to Trending (ForYouDefaultTest semantics preserved live).
8. Fresh install: no db churn in the logs beyond the normal flow; age gate → home as before.

TV (Television_AOSP / TV36):
9. Home rows → open card → player → BACK: home list scroll + focused row restored (D-pad focus on the same item).
10. TvFeed grid: scroll 4 rows → open player → BACK → grid scroll restored AND focused card re-gains D-pad focus (not just visual position).
11. More ▾ → My feeds → open a custom feed → BACK → TvCustomFeeds state (tab/section) preserved.
12. Watch position still flushed on every back-from-player exit (DB check ≥3s floor); open the same gif → resume honored (existing batch-22 behavior).
13. Niche feed → About → creator feed chain (3 entries) walks back faithfully with focus restore each level.
14. Data-saver ON: FocusPreview stays poster-only (existing rule not regressed by NavHost composition changes).
15. PinLock: process death with PIN set → PIN demanded again (unlocked NOT saveable, per §4.2).

Acceptance criteria summary (checked against the issue ask):
- ✅ "back to previous, whether player/feed/anything" — every surface is a nav entry; LIFO pop is the ONLY back path; all `when`-chain BackHandlers deleted.
- ✅ scroll restore — free via nav per-entry `rememberSaveable` (LazyGridState/LazyListState are already saveable types).
- ✅ focus restore — explicit saveable last-focus pattern (§4.3), verified on device.
- ✅ player exit returns to feed at position — grid restore + pager continuity via shared VM.

---

## 9. What this doc does NOT decide (out of scope)

- Navigation 3 (user already decided against it — recorded).
- Keyboard/pointer desktop shells — future shells add a new NavHost; this doc is mobile+TV.
- Any AGENTS doc edit — lands WITH the implementation slice, not in the design pass.
