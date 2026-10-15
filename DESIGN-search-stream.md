# DESIGN: Issue #11 — "Search page should act as the explorer page"

Status: design-only (no code changes). Working copy `ws-search` (super-bassoon), written 2026-10-15.
House rules honored: viewer-only, no invented endpoints (every endpoint below is code-referenced or
live-verified on the date shown), rate-limit ≤15 req/5s, ktlint+detekt gates planned, no TODOs.

---

## 1. Current behavior (file:line evidence)

### 1.1 What the search page shows today (mobile + TV share `:feature:search`)
`feature/search/src/main/kotlin/com/rjbiermann/giffyviewer/search/SearchScreen.kt`
- Query field (OutlinedTextField + back + clear) — :84–:110.
- Empty-query state: "Clear search history" — :119–:127; Room history rows (tap re-runs, per-row
  remove) — :129–:156; debounced suggestion rows (`v2/search/suggest`, debounce 300ms, mapLatest
  cancel) — :157–:180; **trending tags** section (`v2/tags/trending`, one fetch per VM, empty state
  only) — :182–:204.
- Typed-query state: 4-tab TabRow **GIFs · Images · Creators · Niches** (full-scope search, batch 9)
  — :210–:220. GIFs tab = the existing submit path (no inline results; opens `FeedSource.Search`
  feed); Images = 3-up static poster strip, no player path — :228–:250; Creators rows
  (`v2/creators/search/previews`) — :251–:262; Niches rows (`v2/niches/search/previews`) —
  :263–:278. Rows flow the single ContentFilter (`SearchViewModel.loadScope`) with a 12-entry
  per-query VM map cap; niche/preview rows whose preview gif is blocked drop entirely
  (leak-zero).
- Host wiring: mobile `app-mobile/.../MainActivity.kt:203–220` (onSubmit/onOpenCreator/onOpenNiche
  → `feedViewModel.open(...)`, search closes = route-stack swap); TV
  `app-tv/.../TvMainActivity.kt:228–242` (identical shape, `openFeed` var). No `onOpenFeed`
  callback exists yet (only per-scope callbacks).
- State: `SearchViewModel.kt` — query/suggestions/history (:39–:78), trendingTags (:80–:86, 12),
  scope maps + `loadScope` (:124–:183, cached, 1 request per (query,scope), `runCatching` = silent
  failure), `cappedInsert`/`nicheRows` helpers (:188–:208, unit-tested in
  `feature/search/src/test/.../SearchScopeResultsTest.kt`).

### 1.2 What the Explore screen shows today (mobile only, one module away from living in search)
`feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/ExploreScreen.kt`
- Title "Explore" (`GiffyScaffold`) — :24–:32.
- **A paginated list of verified creators ONLY**: `v2/creators/verified` (pages of 20, anonymous OK)
  — `ExploreViewModel.kt:19–:66`. Rows = ListItem avatar + `CreatorLabel(username, verified)` +
  "N followers · M gifs" — :64–:88; tap → `FeedSource.Creator` feed; end-of-list auto-fetch
  (no Load-more button) — :43–:47; error branch (EmptyState + Retry) — :89–:104; end marker —
  :106–:114.
- **No gifs, no photos, no niches, no tags.** Despite the name, the app's "Explore" is a
  verified-creators browser (site Explore = "Top Creators", recorded 2026-10-01).
- Not on TV at all (TV has no explore screen — batch 12 only added Search/Groups/Collections).

### 1.3 The Niches screen (separate page, mobile `NichesScreen` + TV `TvNichesScreen`)
`feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/NichesScreen.kt`
- Full taxonomy browser: category filter chips (`v2/niches/categories`) — :88–:117; sort menu
  (subscribers/posts/A–Z/Z–A/random — all params live-verified via the server's BadOrder list)
  — :54–:101; niche rows with **Join/Leave (logged-in write)**, **About → NicheAboutScreen**
  (detail/top-creators/related/tags), **pin-to-home-tabs** (pinnedNiches → home tab chips,
  `FeedScreen.kt:168–182`) — :118–:180. TV equivalent `app-tv/.../TvNiches.kt:42+`.
- NichesViewModel (`NichesViewModel.kt:20+`): pages `v2/niches`, category/sort switching, pin
  toggle. Niches search-tab coverage in search = `niches/search/previews` (per-query only).

### 1.4 Who links to Explore / Niches today (all call sites)
Mobile `app-mobile/.../MainActivity.kt`:
- `showExplore` state :82; BackHandler arm :94–:95 + pop :107; **ExploreScreen branch :142–151**
  (onOpenCreator → creator feed); `onOpenExplore = { showExplore = true }` :244.
- `showNiches` state :81; BackHandler :94, :96 + pop :108 (order matters in the stack);
  **NichesScreen branch :222–231** (onOpenNiche → niche feed, onOpenAbout → aboutNiche sheet);
  `onOpenNiches = { showNiches = true }` :242.
- Entry points: `FeedScreen.kt:99–101` (`onOpenNiches`/`onOpenExplore` params);
  More ▾ menu — "Explore" :258–:263, "Niches…" :289–:295.
TV `app-tv/.../TvMainActivity.kt`:
- `showNiches` :100 → `TvNichesScreen` :222–:227; home "Niches" pill :294–:297. `showSearch`
  :103 → SearchScreen :228–:242; home "Search" pill :300–:303. **No Explore on TV.**
Tests: none reference Explore/Niches screens (verified: no test files match). AGENTS-APP.md:262
and :470 mention both (doc-level references to update with the change).

### 1.5 Time-range facts in code today
- `GifsApi.search` (`core/network/.../GifsApi.kt:33–:51`): `search_text`, `order` (default
  "trending"), `count`, `page`, `type` (g/i). Verified orders recorded in code comments:
  search {trending, latest, top7, top28, score} (`FeedSource.kt:161–:164`).
- `GifsApi.trendingPopular(...)` accepts `order` — "top_week = Top This Week feed (live-verified
  2026-09-30)" — `GifsApi.kt:58–:65`; used by `FeedPageFetcher.kt:126` via `FeedSource.TopThisWeek`
  (home More ▾ entry, `FeedScreen.kt:250–:256`).
- **All-time has no order anywhere in the app today** ("top" is documented for creator/userGifs,
  `GifsApi.kt:47–:49`, but not surfaced in `FeedSource.sortOptions()`).

---

## 2. Live-site verification done for this design (2026-10-15, curl + Playwright headless)

API probes (anonymous temp token, consistent UA; ordered responses recorded in the probe log):
1. `GET /v2/gifs/search?search_text=dance&order=top_week|top_month|top_year|top_all` →
   **400 BadOrder**, whose own message enumerates the authoritative accepted set:
   `Bad sorting order ... must be one of: top, top7, top28, latest, score, trending.`
   → **Top of week = `top7`, top of month = `top28`, all-time = `top`** on the search endpoint.
   `top_month`/`top_week` etc. are NOT search orders — do not use them there.
2. `GET /v2/gifs/search?search_text=&order=top7|top28|top|trending&count=10` (BARE search) → all
   200; pools **differ per range** (top7 ≠ top28 from row 2 on; `top ≠ top7/top28 ≠ trending`;
   first `top` row had 66.8M views vs trending's first row) → a bare ranged search is a usable
   site-wide ranged pool today, no query needed.
3. `GET /v2/gifs/search?search_text=&order=top7&type=i` → 200 (ranged bare image search also
   exists; not required below, noted as capability).
4. `GET /v2/feeds/trending/popular?order=top_week|top_month|top_year|top_all` → all 200 (only
   `top_week` was previously verified; home feed source stays as-is).
5. `GET /v2/niches/trending/search?count=4` → **200**, shape
   `{page, pages, total, niches:[{id, name, gifs, subscribers, tags, preferences, thumbnail}]}` —
   parses into the **existing `NichesPageDto`** (`GifsApi.kt:434–439`; ignoreUnknownKeys drops the
   extra fields). Endpoint is new to the inventory (site's own Explore/Search source).
6. `GET /v2/creators/search?order=trending&verified=y&page=1` → 200 (items =
   existing `CreatorSearchItemDto`). The site's Explore uses this variant; our existing
   `v2/creators/verified` (used by ExploreScreen) returns the same data family and remains
   live-verified — keep the app's current call.

Site UI (Playwright, mobile viewport):
- `https://www.redgifs.com/explore` and `https://www.redgifs.com/search?query=` (EMPTY query)
  fire the **same API calls and render the same sections**: a trending gif feed
  (`trending/popular?count=100`), "Trending Niches" row (`niches/trending/search`),
  "Trending Creators" row (`creators/search?order=trending&verified=y`), plus OnlyFans promo
  modules — **§0-banned, app must never render them**. → The site itself already treats the
  empty search page as the explorer page; issue #11 mirrors exactly that.
- Site search WITH a query = the 4-tab results page (already verified batch 6 / app built batch 9).

### 2.1 Remaining pre-build verification checklist
1. `top7`/`top28`/`top` pool sanity: cross-check the first N ids against the site's own
   weekly/monthly top pages (ordering direction — views-desc within range) — one Playwright hit.
2. Confirm the site still renders niches/creators rows for a signed-out profile (this probe was
   logged-out = anonymous, so yes; re-confirm after any token change).
3. Confirm `niches/trending/search` count bounds (probe used count=4/8; test count=12).
4. After build: on-device checks per §5 acceptance.

---

## 3. Gap (issue #11 vs today)

1. The search page's EMPTY state shows only history/suggestions/tags — no trending gifs, photos,
   ranges, niches, or creators; discovery lives on two separate screens (Explore + Niches).
2. Explore (mobile-only) is a verified-creators-only browser duplicated as a standalone page
   while the search page already hosts creator discovery (Creators tab) — the page is
   redundant once the search page hosts the same content.
3. Time ranges (week / month / all-time) exist only as the single "Top This Week" home feed;
   all-time exists nowhere; the search page sorts cannot reach all-time (`top` missing from
   `FeedSource.sortOptions()`).
4. Tags covered only by the trending-tags list (empty state); matched tags for a typed query
   (`v1/tags/match`, endpoint verified + wired in `GifsApi.kt:142–146`) are fetched nowhere.

---

## 4. Proposed minimal change

Design principle: **the search page becomes the explorer; the explorer screens die.** The 4-tab
full-scope search (already built + tested) is reused as-is; new work concentrates on the
EMPTY-query state (Explore sections + ranged Top row) and one small feed-source addition so
section rows can open browsable feeds. Niches keep their page (see §4.4).

### 4.1 Slice A — ranged Top feeds (the time ranges), by file
`feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedSource.kt`
- Add one source: `data class TopRanged(val range: String) : FeedSource` with
  `keyBase = "top:<range>"`, `ttlMs = TTL_TRENDING`; allowed ranges `top7` / `top28` / `top`
  (verified set, §2.1). `title()` = "Top This Week" / "Top This Month" / "Top of All Time";
  `sortOptions()` unchanged (ranges are sources, not sorts on other feeds).
- ALSO extend `sortOptions()` for `FeedSource.Search` with `"Top of All Time" to "top"` (the
  verified missing order — typed-query result feeds gain all-time the same way they have
  top7/top28 today).
`feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/FeedPageFetcher.kt`
- One branch: `is FeedSource.TopRanged -> api.search(searchText = "", order = feed.range,
  count = pageSize, page = page)` — bare ranged search verified 200 (§2.1 probe 2). Room pages
  land in the standard cache (`feed_pages`/`gifs`, keys `top7:p1`-style) → **cache-first revisit
  for free**; no new fetch/cache machinery.
`feature/search/src/main/kotlin/com/rjbiermann/giffyviewer/search/SearchViewModel.kt`
- New empty-state states (VM-held, PageFetcher-free, mirroring the scope-pattern):
  `_topRanged: StateFlow<Map<String, List<Gif>>>` (key = range, cap 12 entries reuse
  `cappedInsert`; **12 tiles per fetch** — one `api.search(searchText="", order=range, count=12,
  page=1)` per range, first view of the strip only); `loadTopRanged(range)` runs the same
  `filter.refreshFrom/refreshBlockedFeeds` choke-point prelude as `loadScope` and maps rows
  through `filter.allow(...)` (leak-zero identical to scope rows).
`feature/search/src/main/kotlin/com/rjbiermann/giffyviewer/search/SearchScreen.kt`
- Empty-query section (shown instead of nothing, positioned ABOVE "Trending tags", parroting the
  site's top-down order): "Top gifs" — one row with 3 FilterChips (Week · Month · All time) +
  a 3-up poster strip (reuse `PreviewThumb` chunking, same component as the Images tab).
  Chip switches which strip renders; first chip touched fires its one request.
- Strip tap → nothing (stills/preview tiles have no player path today — same viewer rule as the
  Images tab); a section-header **"Browse all →"** action opens the corresponding full feed:
  new callback `onOpenFeed: (FeedSource) -> Unit`.
- Host wiring (2 lines per host): mobile `MainActivity.kt:203–220` — hoist the branch's
  existing `feedViewModel` (:205) and pass `onOpenFeed = { source -> showSearch = false;
  feedViewModel.open(source) }`; TV `TvMainActivity.kt:228–242` — same shape via the existing
  `openFeed` var. Both hosts already have the identical pattern for onSubmit/Creator/Niche.

### 4.2 Slice B — empty-state explorer rows (same content the site's Explore shows)
`SearchViewModel.kt` (all new state VM-held; every list filtered through ContentFilter; silent
`runCatching` failure = absent section, matching the scope/suggestions failure stance — no retry
storms):
- **Trending photos row**: bare `api.search(searchText="", order="trending", type="i", count=12)`
  → `_trendingPhotos: StateFlow<List<Gif>>` → static 3-up poster strip (no player path).
- **Trending niches row**: new `GifsApi.nichesTrendingSearch(count=12)` (endpoint live-verified
  §2.1 probe 5; response parses into existing `NichesPageDto` — zero DTO work) →
  `_trendingNiches: StateFlow<List<NicheDto>>` → horizontal chip/card row; tap → existing
  `onOpenNiche(id, name)` callback (wired on both hosts today).
- **Creators row**: reuse the existing `api.verifiedCreators(page=…)` call; the ExploreScreen
  paging machine MOVES here (fields `_exploreCreators/_endReached/_loadFailed`, same
  distinct-by-username dedup) rendered as a horizontally auto-paging strip (avatar + name +
  counts cards); reaching the strip's end fetches the next page — full list stays reachable
  without a page; tap → existing `onOpenCreator(username)`.
`SearchScreen.kt`
- Rows between the "Top gifs" section and "Trending tags": Trending photos strip · Trending
  niches row · Creators strip. Each section loads **on first visibility** (one
  `LaunchedEffect(sectionVisible)` per section, cached thereafter) — not all at once.
`SearchSearch scope rows` (typed query) are NOT changed in this issue (already built batch 9).

### 4.3 Slice C — typed-query tags (tiny, closes the "tags" bullet)
`SearchViewModel.kt`: in `loadScope` GIFs handling add a one-shot `api.tagsMatch(query=q)`
(verified endpoint, already in the API surface, `GifsApi.kt:142–146`) →
`_matchedTags: StateFlow<Map<String, List<String>>>` (same cap). `SearchScreen.kt` GIFs tab:
one chip row above the submit hint "Tags matching: #x"; tap → `onSubmit(tag)` (the existing
tag-feed path — batch 6 rule "tags stay the tag-feed path" preserved).

### 4.4 Slice D — Explore screen removal (delete, do not merge into Niches)
- DELETE `feature/feed/.../ExploreScreen.kt` and `ExploreViewModel.kt` (paging machine moved in
  §4.2). No tests reference them.
- `FeedScreen.kt`: remove `onOpenExplore` param (:101) and the More ▾ "Explore" entry
  (:258–263). Discovery for creators lives 1 tap away in the top-bar search icon search page
  (:160–163) — the menu entry is the only deletion; nothing else moves. "Niches…" entry STAYS.
- `MainActivity.kt`: remove `showExplore` state (:82), its BackHandler arm (:94–95) and pop
  (:107), the ExploreScreen branch (:142–151), the `onOpenExplore = …` wire (:244), and the
  `ExploreScreen` import (:30). BackHandler ordering unaffected (branch order preserved
  otherwise).
- TV: untouched (no explore surface).
- **NichesScreen KEPT as its own page on both apps** (recommendation): it is not just a browse
  list — it owns Join/Leave writes, pin-to-home-tabs (home-tab generation depends on pinnedNiches,
  `FeedScreen.kt:168–182`), category filters, and sort switching; merging that into the search
  page would strand four features or bloat the search dialog. The search page gains a trending-
  niches preview row; the full browser remains one More ▾/pill tap away. If the issue author
  insists on niche-page removal too, the fallback (bigger churn, not recommended) is hosting
  NichesScreen content as a search-page tab — flagged as a separate decision, not part of this
  slice set.
- Docs: refresh AGENTS-APP.md ("Explore" references :262/:470 → search-page sections) and
  AGENTS-NETWORK.md (add `v2/niches/trending/search` to the niche endpoint line + the ranged
  order set `top7/top28/top` on bare `gifs/search`).

### 4.5 Implementation order (grouped by file)
1. `:core:network GifsApi` — `nichesTrendingSearch` (5 lines; reuses `NichesPageDto`).
2. `:feature:feed FeedSource` + `FeedPageFetcher` — `TopRanged` source + fetch branch
   (+ `sortOptions` "top" for Search) + regression line in existing feed tests
   (`FeedWalkTest`-style FakeApi case: TopRanged page fill + cache-key shape).
3. `:feature:search SearchViewModel` — `loadTopRanged`, photos / niche rows / creators states
   (+ matched tags slice C); unit test `SearchExploreSectionsTest` (cap, filter drop, range
   map fill, tagsMatch row) mirroring `SearchScopeResultsTest` shape.
4. `:feature:search SearchScreen` — empty-state sections + chips + `onOpenFeed` param.
5. Host wiring: `app-mobile MainActivity.kt` + `app-tv TvMainActivity.kt` — `onOpenFeed` pass-through.
6. `:feature:feed FeedScreen.kt` — drop the Explore menu entry (+ param).
7. Delete `ExploreScreen.kt`/`ExploreViewModel.kt`; prune `MainActivity.kt` explore wiring.
8. Doc updates (AGENTS-APP.md, AGENTS-NETWORK.md) + module gates: compile + ktlint + detekt +
   unit tests on `:core:network`, `:feature:search`, `:feature:feed`, both app modules.

---

## 5. Acceptance criteria (observable / testable)

1. Search page, empty query, fresh anonymous profile renders in order: Top gifs (chips Week/
   Month/All time + poster strip) · Trending photos strip · Trending niches row · creators strip
   · Trending tags — **zero promo/onlyfans content ever appears** (ContentFilter + module-ban
   rules).
2. Tapping each Top-range chip populates the strip with DIFFERENT gifs (probes show distinct
   pools); a second visit to the same chip within the VM lifetime makes NO new network call
   (VM cache; logcat-observable via limiter output / FakeApi test counter).
3. "Browse all" per range opens the paged feed (Week/Month/All-time), whose pages land in Room
   (`feed_pages` key `top:<range>:p1`) and survive airplane-mode cold start (gate-3 shape:
   cache renders, zero network).
4. Creators strip: initial 20 cards, scrolling the row horizontally auto-fetches page 2 …
   exactly one request per approach (dedup by username, no LazyColumn key crash); tap opens the
   creator feed on both apps.
5. Niches row: tap opens the niche feed; the full Niches page still opens from More ▾ (mobile) /
   home pill (TV) with Join/About/Pin intact.
6. GIFs tab of a typed query shows matched-tag chips; submit path + 4-tab behavior unchanged
   (no regression to batch 9 tests, `SearchScopeResultsTest` stays green).
7. `Show all time` chip opens a feed with paged continuations (server pages via bare ranged
   search verified 200; FeedPageFetcher fills ≥2 pages without duplicate-key crash).
8. Unit tests: new `SearchExploreSectionsTest` (+ one TopRanged case in the feed walk tests)
   cover — range map fill/dedup/cap, blocked-gif row drop (leak-zero), creators paging dedup,
   photos-on-trending strip pool (fake api), matched-tags cap. `SearchScopeResultsTest`,
   `FeedWalkTest`, limiter invariant test all stay green; ktlint + detekt pass on touched
   modules.
9. Both app modules compile + assemble; ExploreScreen/ExploreViewModel gone (no dangling
   references, BackHandler stack sane after probing back through every route).
10. TV D-pad pass: new empty-state sections reachable and focusable (strip cards are
    focusables — giffyFocus treatment), submit/creator/niche paths unregressed.

---

## 6. Rate-limit implications (≤15 req/5s invariant)

- Budget per search page open (worst case, all sections fresh): tags 1 + top-range first chip 1
  + photos 1 + niches row 1 + creators 1 + creator auto-page 1 = **6 live calls**, all cached
  per VM. Typed-continuation: suggestions (debounced 300ms + mapLatest cancel) + 1 request on
  first tab open/scope. Total stays under the 15/5s burst budget even with rapid tab switching —
  same ledger the full-scope search already ships.
- Structural safety: every request funnels the OkHttp RateLimiter (`core/network/rate/
  RateLimiter.kt`, 15/5s + 429 cooldown + breaker) — overload can delay, never violate.
- Stagger rule (design, not limiter-dependent): load-on-first-visibility per section; within a
  section one request at a time (creators paging machine is sequential by construction, same as
  ExploreViewModel today).
- Cache reuse from `:core:database`: ranged-range FEED pages flow the standard FeedPageFetcher →
  Room (`GifDao`/`FeedPageDao`, TTL 10 min) → revisits are cache-first; Section strips
  intentionally do NOT write Room rows (preview lists are ephemeral, session-VM-cached, exactly
  like today's scope results) — keeps `gifs` table semantics for pager-owned pages. The niches
  trending row could later join NichesScreen's Room-side caching IF the taxonomy ever caches —
  out of scope today (niches taxonomy is not Room-backed today either).
- Rate-limit regression guard: no new limiter paths; invariant test unchanged.

---

## 7. Risks / notes

1. **Strip tiles are not player-paths** (poster taps are no-op by design; the feed opens via
   section header). A single-gif→player path would need a new one-item feed source — explicitly
   NOT in this issue (scope creep; flag if the user wants it later).
2. Tapping a Top-range gif opens the RANGE feed (not that specific gif) — the "See the gif from
   the strip" upgrade is the same future slice as risk 1.
3. The creators strip is horizontal; on phone the old vertical ExploreScreen scrolled the full
   list vertically. Horizontal auto-paging preserves the full list but is a UX change — if it
   reviews poorly on device, fallback (small, self-contained): a "Creators…" search-page section
   EXPANDS in place (vertical list inside the section, all state identical). Decide at the
   device pass, both variants share the same VM.
4. TV: new focusable rows inside SearchScreen = D-pad surfaces to verify (see criterion 10);
   the site parity is mobile-first, TV inherits the shared screen per shell-agnostic rule.
5. Live-drift exposure: ranges verified today; if the server's BadOrder set narrows again, the
   single fetch branch + chips are the only touch points (endpoint-path-only drift per house
   rule).
6. Probe-only sessions must stay consistent-UA (anonymous tokens are UA-bound — WrongSender on
   UA change); device verification uses the app's own BrowserIdentity identically (no change).
7. Module graph: `:feature:search` gains nothing new dependency-wise (reuse `CreatorSearchItemDto`,
   `NichesPageDto`, existing callbacks); `:feature:feed` shrinks (Explore deletion), keeping
   round-2's flat-VM rule (no shared-abstraction extraction, n=4 threshold stays unmet).

---

## 8. Merge recommendation (one-liner for the issue)

**Remove ExploreScreen + its wiring (More ▾ entry included) and host its content — plus the
site's trending gifs/photos/niches and the week/month/all-time ranges — on the search page's
empty state (2 hosts, already shared); KEEP NichesScreen as its own page (unique Join/About/Pin/
category machinery); reuse the built 4-tab full-scope search untouched.**
