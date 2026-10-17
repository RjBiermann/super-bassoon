# DESIGN — issue #47 slice 3: niche preview-tile listing (mobile + TV)

Lane: `lane-47s3` (workspace ws-47s3, base = main `9c55375f`). Slices 1+2 already
landed (`6130b0ad`); this is the third slice of the build plan in the issue
comment: the niches listing becomes a preview-tile grid backed by the
`v2/niches/search/previews` endpoint the site's own `/niches` page uses.

## 1. Verified endpoint truth (live probe 2026-10-17, this session)

Method per `giffy-live-probe`: one anonymous temp token fetched with the exact
`BrowserIdentity` header set (Chrome-Android UA — token is UA-bound, so the
token fetch and every call carry the identical identity), then THREE endpoint
probes (the task's ≤3 probe budget), spaced ≥2s apart. Raw JSON saved while
probing, then deleted with the token.

Base: `GET {apiBase}v2/niches/search/previews` — anonymous OK, 200 with
Bearer temp token.

### P1 — baseline site shape (no query param)

```
GET /v2/niches/search/previews?order=subscribers&page=1&count=30
→ 200, top-level keys: previews | page | pages | total   (30 rows, page=1, pages=?, total present)
```

Row shape (verified on the wire, exact key sets):

- `previews[]` — `{niche, gif, user}`
- `niche` keys: `cover, description, gifs, id, name, owner, rules, subscribers, thumbnail`
  - **`cover` and `thumbnail` DO come back** on preview-row niche objects
    (`https://userpic.redgifs.com/niches/covers/<slug>.jpg` /
    `.../thumbnails/<slug>.jpg`) — confirms the REPORT §2/B1 finding that the
    DTO's unused fields are real payload fields (on the detail call and, as
    probe-verified here, also inside preview rows).
  - `gifs` / `subscribers` counts also present per row (app-superior: we can
    keep counts the site tiles omit).
- `gif` — full GifDtoShell shape: keys match the existing DTO
  (`id, userName, tags, likes, views, duration, hasAudio, width, height,
  createDate, published, avgColor, verified, urls{hd, html, poster, sd, silent,
  thumbnail}, niches[], promoted, ...`). `urls.poster` present on every row —
  the tile image source.
- `user` — owner profile object (like the search spec says); unused by the app.
- Ordering verified: rows arrive **subscribers-descending** under
  `order=subscribers`.
- `niche.id` unique within the page (30 rows / 30 ids); page 1 vs page 2 ids
  disjoint for the same sort (see P2 — cross-page overlap cannot be ruled out
  for `order=random`, so the merge still dedupes by id).

### P2 — category + deep pagination

```
GET /v2/niches/search/previews?order=subscribers&page=2&count=30&category=Animated
→ 200: page=2, pages=2, total=34, rows=4
```
`category=<taxonomy category name>` — accepted (exactly one name from
`v2/niches/categories`; verified with "Animated"). Pagination is
standard `{page, pages, total}`: the existing `page < pages → next` math in
NichesViewModel applies unchanged (4 rows on page 2 = 30 + 4 = 34 total).

### P3 — random + count=100

```
GET /v2/niches/search/previews?order=random&page=1&count=100
→ 200: page=1, pages=19, total=1834, rows=99   (no niche-count ordering)
```
`order=random` accepted; `count=100` accepted (server returned 99 rows —
off-by-one on the server side, harmless); random pages still carry
`page/pages/total`, so paging a randomized pool works.

**What was NOT verified (and is therefore not shipped):** `order=posts`,
`order=alphabetical_asc`, `order=alphabetical_desc` on the previews endpoint.
Those three are taxonomy-endpoint-verified (`/v2/niches`, BadOrder-truth
recorded in AGENTS-NETWORK) but this is a different endpoint — the probe budget
was spent before them, so the slice's sort menu carries only the two
previews-verified orders (`subscribers`, `random`). A follow-up probe should
re-verify the other three before restoring the full menu.

## 2. Planned implementation

### core/network (`GifsApi.kt` — in fence)

- `NicheSearchPreviewsDto` gains `page/pages/total` (defaults; ignoreUnknownKeys
  already tolerates the extra `user` field).
- `NichePreviewNicheDto` (the lenient preview-row niche) gains the verified
  consumable fields: `cover: String?`, `thumbnail: String?`,
  `gifs: Long = 0`, `subscribers: Long = 0`. (`description/rules/owner` also
  come back but nothing consumes them here — left dropped by the serializer.)
- New endpoint fn `nichePreviewListing(order, category, page, count)` WITHOUT
  `query` — the existing `nicheSearchPreviews(query=...)` stays untouched (the
  search scope keeps its own contract). FakeApi test double gains the override.

### feature/feed (`NichesViewModel.kt`, `NichesScreen.kt` — in fence)

- Pure mapping (TDD target, mirrors feature/search's `nicheRows` precedent):

```kotlin
internal data class NicheTile(id, name, gifs, subscribers, imageUrl)
internal fun nicheListingRows(
    existing: List<NicheTile>,
    previews: List<NichePreviewRowDto>,
    allow: (Gif) -> Boolean,
): List<NicheTile>
```
  - drop rows with blank `niche.id` (no identity → no tile);
  - **leak-zero**: rows whose preview gif is blocked drop entirely (same rule
    the search niche scope already enforces — `nicheRows`, leak-zero comment);
    a null gif row keeps its tile (image null → name-only tile);
  - image = `gif.urls.poster ?: niche.thumbnail ?: niche.cover`
    (site tile parity: preview-gif poster; niche avatar/banner fallback);
  - merge with `existing`, `distinctBy { it.id }` (upstream pagination overlap
    guard — the same dedup verifiedCreators learned the hard way);
  - `allow` runs only on a present gif, `gif != null && !allow(gif)` drops.

- `NichesViewModel`: data source switches from `api.niches(...)` to
  `api.nichePreviewListing(order=_sort, category=_category, page=nextPage)`;
  the pending/error/endReached/auto-retry machine (issue #49) is reused
  as-is; `nextPage` math via the verified `{page, pages}`.
- Leak-zero needs the filter's sets reloaded per session load: VM injects
  `GiffyDatabase` + `ContentFilter` (both Hilt singletons, same shape
  FeedViewModel/SearchViewModel already use) and runs
  `filter.refreshFrom(db.contentPrefsDao())` +
  `filter.refreshBlockedFeeds(db.customFeedDao())` before the mapping.
- `isPinned`/`togglePin` switch from `NicheDto` to the tile model (callers:
  only NichesScreen + TvNiches — checked).
- Sort state: `_sort` default stays `"subscribers"`; menu choices in BOTH
  screens become the two previews-verified orders
  (`subscribers` → "Subscribers", `random` → "Randomize"). The removed
  taxonomy-only entries are recorded in §1 (probe-gated restore).

### mobile `NichesScreen`

- Keep GiffyScaffold, category chips (`v2/niches/categories`, verified), sort
  menu (2 entries), pending/error/empty/end states, scroll-to-top FAB — all
  unchanged mechanics.
- The_taxoa list rows become **tile rows in the existing LazyColumn**:
  `items(niches.chunked(2))` → a Row of up to 2 tiles
  (no LazyVerticalGrid — the shared scroll helpers/fab are LazyListState-typed;
  chunked rows keep the whole state machine, zero new helpers).
- Tile: `Column(clickable → onOpenNiche)` = AsyncImage poster (16:9 box,
  rounded, niche-name contentDescription) + name + "929K gifs · 633K members"
  + compact action Row (Join/Leave TextButton when signed in, About
  IconButton, Pin IconButton) — every pre-existing action survives.

### TV `TvNiches` (in fence)

- Rows keep the WORKING D-pad structure (batch-12 lesson: name block Surface +
  About button side-by-side focusables; hold-Center pin keymap untouched) —
  the slice adds the tile image INSIDE the Surface's Column (poster above the
  name/counts, tile width capped) and the same 2-order sort menu parity.
- Issue-#7 tokens unchanged: Surface focused fill = primaryContainer, border=
  primary (existing pattern this file already carries; no new giffyFocus).

## 3. Acceptance conditions

1. JVM test `NicheListingTilesTest` covers: blocked-preview-gif drop,
   blank-id drop, cross-page id dedupe, image fallback chain, null-gif tile
   survival (TDD: failing first, then green).
2. `:feature:feed:check` + `:core:network:check` + `:app-tv:check` +
   `:app-tv:compileDebugKotlin` green (RAM-serialized, no assembleDebug).
3. No emulator work, no device steps (coordinator owns the device pass);
   listing renders tiles from live data on a later device gate.

## 4. Non-goals

- Inline autoplay on niche tiles (site tile autoplays `<video>`; the app ships
  static posters — same stance the repo took on TV preview tiles vs data-saver).
- `moderators` field (B8) + full `v2/niches/{id}` payload probe — separate item.
- Restoring `posts`/`alphabetical_*` sorts on this endpoint (unverified; §1).
- AGENTS-NETWORK.md endpoint row (this lane's fence excludes the doc; row truth
  recorded here — the coordinator should carry the §1 shape into the inventory).
- Any FeedHeader/FeedScreen/ContentFilter/FeedRepository changes (hard fence).
