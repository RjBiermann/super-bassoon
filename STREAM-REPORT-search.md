# STREAM REPORT: SEARCH-EXPLORE (issue #11) — ws-search worktree

Status: implemented (slices A–D per `DESIGN-search-stream.md`). No gradle/ktlint/detekt/build/test
runs (run constraint), no jj/git commands, no emulators — cheap greps + manual invariant checks only.

## Slice A — ranged Top feeds

- `feature/feed/.../FeedSource.kt`
  - New `data class TopRanged(val range)`: `keyBase = "top:<range>"`, `ttlMs = TTL_TRENDING`,
    `init` `require(range in RANGES)`; companion `RANGES = ["top7","top28","top"]` (the search
    endpoint's BadOrder-verified top set; `top_week` family deliberately NOT here).
  - `title()` branch: "Top This Week" / "Top This Month" / "Top of All Time".
  - `sortOptions()` for `Search` gained `"Top of All Time" to "top"` + kdoc updated.
- `FeedPageFetcher.kt`: `is FeedSource.TopRanged -> api.search(searchText = "", order = feed.range,
  count = pageSize, page = page)` — bare ranged search, no new params. Standard cache-first
  FeedRepository/FeedMediator path needs nothing else (verified generic over FeedSource).
- `SearchViewModel.kt`: `_topRanged: MutableStateFlow<Map<String, List<Gif>>>` (+ `withRange`
  exposure), `loadTopRanged(range)` runs the ContentFilter choke-point prelude
  (`refreshFrom` + `refreshBlockedFeeds`, leak-zero `filter.allow` rows), VM-cached via the
  existing `cappedInsert` (12-entry cap, 12 tiles/fetch → ≤1 request per range).
- `SearchScreen.kt`: empty-state "Top gifs" section — `SectionHeader` + "Browse all →" →
  `onOpenFeed(FeedSource.TopRanged(topRange))`; Week/Month/All-time `FilterChip` row (keys = the
  verified orders, labels = the site's wording); 3-up `PosterRow` strip; `topRange`
  remember-state; `LaunchedEffect(topRange, query)` fires the one load (chip switch / first
  visibility; VM cache makes revisits free).
- Host wiring: `MainActivity.kt` `onOpenFeed = { source -> showSearch = false; feedViewModel.open(source) }`;
  `TvMainActivity.kt` same via the existing `openFeed` var.
- Test: `FeedWalkTest` + new case — TopRanged fill hits the bare search (`searchArgs = listOf("" to 1,
  "" to 2)`) and caches under `top:top7:p1` / `top:top7:p2` with chained nextPageKey.

## Slice B — empty-state explorer rows

- `SearchViewModel.kt`: `trendingPhotos` (bare `type="i"` trending Search, ContentFilter rows),
  `trendingNiches` (`v2/niches/trending/search` → existing `NichesPageDto`, zero DTO work),
  `exploreCreators` strip = the old ExploreViewModel paging machine moved here
  (`exploreCreators`/`creatorsEnd`/`creatorsFailed` + `loadExploreCreators()`, distinct-by-username
  dedup) with leak-zero `filter.allow(username)` drop.
- `GifsApi.kt`: `@GET("v2/niches/trending/search") suspend fun nichesTrendingSearch(count) :
  NichesPageDto` (live-pre-verified endpoint, parses with `ignoreUnknownKeys`; FakeApi override added).
- `SearchScreen.kt`: sections above "Trending tags", site-parity top-down order (Top gifs ·
  Trending photos strip · Trending niches chips row · Creators strip · Trending tags). Each
  section loads on first composition via its own LaunchedEffect (body collects never fetch);
  creators strip is a LazyRow with an end-sentinel item (`LaunchedEffect(size)`) so the next page
  fetches only when the row's end enters the viewport, Retry button on failure, "That's all N
  creators" end marker; niche chips → existing `onOpenNiche(id, name)`; creator cards → existing
  `onOpenCreator(username)`.
- Extracted pure helper `exploreCreatorRows(existing, next, allow)` (dedup → filter drop →
  no-growth=exhausted) for the test; test `SearchExploreSectionsTest` covers overlap dedup +
  exhaustion decision and the blocked-row drop path.

## Slice C — matched tags (typed query)

- `SearchViewModel.loadScope`: new `SCOPE_GIFS` branch — one-shot `api.tagsMatch(query=q)` into
  `_matchedTags: Map<String, List<String>>` via `cappedInsert` (12 cap, same as scopes).
- `SearchScreen`: GIFs tab (scope 0) renders a "Tags matching" chip row above the results flow;
  chip tap → existing `submit(tag)` (tags stay the tag-feed path per batch-6 rule); `when (scope)`
  now arms scope 0 through `loadScope`.

## Slice D — Explore screen removal

- DELETED `ExploreScreen.kt` + `ExploreViewModel.kt`.
- `FeedScreen.kt`: `onOpenExplore` param + "Explore" More-▾ entry removed; "Niches…" entry kept
  (menu composes with `Feature.CUSTOM_FEEDS` gating untouched).
- `MainActivity.kt`: `showExplore` state, BackHandler arm + pop, ExploreScreen branch, the
  `onOpenExplore` wire, and the `ExploreScreen` import all removed; BackHandler/branch order of the
  remaining routes unchanged. NichesScreen branch untouched.
- TV untouched (no Explore surface existed; TV home's own Top-Creators row is TvHomeViewModel-owned,
  per its own VM, and is outside this stream's slices).
- NichesScreen kept as-is on both apps (design recommendation §4.4).

## Docs

- `AGENTS-NETWORK.md`: niche endpoint line gains `v2/niches/trending/search` (live date + shape);
  content line gains the search endpoint's BadOrder-verified order set (top/top7/top28/latest/score/
  trending) + bare-search ranged-pool note.
- `AGENTS-APP.md`: creator-table row now points at the search-page creator strip (ExploreScreen
  deleted); PONYSAIL list-skeleta item updated (third skeleton copy removed with the machine's new
  home). Session-history/audit records intentionally left as history.

## Module note (deviation from design §7's claim)

`:feature:search` gains `implementation(project(":feature:feed"))` — the doc names
`onOpenFeed: (FeedSource) -> Unit`, which cannot exist without the FeedSource type; one-way dep,
no cycle (`:feature:feed` does not depend on `:feature:search`). Grep-verified against both modules'
build files.

## Verification done (grep/static; gradle deliberately NOT run per task rules)

- `ExploreScreen|ExploreViewModel|onOpenExplore|showExplore` in *.kt: only provenance comments
  (SearchScreen/SearchViewModel kdoc naming the moved machine) — zero code references.
- `TopRanged` present only in FeedSource/FeedPageFetcher/SearchScreen/SearchViewModel + tests;
  cache keys `top:<range>:p<n>` pinned by the new FeedWalkTest case.
- All `when`-expressions over FeedSource re-checked: `title()` updated; `activeSort`/`withSort`/
  `sortOptions`/FeedPageFetcher/FeedViewModel/FeedScreen use `else` arms — new source compiles-safe.
- Import sort / ≤140 cols / trailing whitespace / brace balance checked on all touched files.
- Rate-limit ledger: worst-case fresh open = tags 1 + top range 1 + photos 1 + niches 1 + creators 1
  (+1 auto-page) = ≤6 VM-cached calls, all through the existing limiter (≤15/5s unchanged; no new
  limiter paths).
- No invented endpoints/params: every URL/query in the diff is design-doc live-verified or
  pre-existing API surface.

## Deviations / judgment calls

1. Empty-state sections render AFTER Room search-history rows (before "Trending tags") — the doc
   pins "above Trending tags" and the fresh-profile ordering (acceptance 1), where history is empty
   anyway; recents stay top for returning users.
2. Trending-niches strip is NOT passed through ContentFilter — a niche row has no gif/filterable
   content; the destination niche feed applies the full read-time filter chain (leak-zero holds at
   the feed), documented on the StateFlow.
3. Photos/niches sections load via explicit VM functions + section LaunchedEffects (scope-pattern)
   instead of `stateIn(Lazily)` flows — Lazily flows would fire on screen OPEN rather than first
   section visibility, violating the design's stagger rule.
4. A `creatorsFailed` StateFlow + manual Retry remained from the moved ExploreViewModel machine
   (design said "silent failure = absent section" for the strips; the strip sentinel still needs a
   manual recovery act or a failed page-1 leaves a dead row — same manual-retry stance ExploreScreen
   shipped).
5. Creator pages whose new rows are ALL blocked/dups end the walk (no-growth rule) — pre-existing
   ExploreViewModel semantics, now folded with the leak-zero filter.

## Open risks / pending (device/D-pad passes not runnable this session)

- TV D-pad walk of the new empty-state sections (chips, LazyRow cards, retry) — criterion 10;
  FilterChip/clickables are focusable so the mechanics should hold, unseen.
- No gradle/ktlint/detekt/test executed (task constraint) — compile + gates must run before merge.
- Live-drift exposure unchanged from design §7: ranges/BadOrder verified 2026-10-15; single fetch
  branch + chips are the only touch points if the set narrows.

Files changed:
- DELETED: feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/ExploreScreen.kt,
  ExploreViewModel.kt
- feature/feed: FeedSource.kt, FeedPageFetcher.kt, FeedScreen.kt,
  src/test/.../FeedWalkTest.kt, src/test/.../FakeApi.kt
- feature/search: SearchViewModel.kt, SearchScreen.kt, build.gradle.kts, src/test/.../
  SearchExploreSectionsTest.kt (new)
- core/network: GifsApi.kt
- app-mobile: MainActivity.kt · app-tv: TvMainActivity.kt
- docs: AGENTS-NETWORK.md, AGENTS-APP.md
- report: STREAM-REPORT-search.md (this file)
