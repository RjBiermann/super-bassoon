# DESIGN — issue #47 probe-gated remainder: full sort menu on the niches listing

Lane: `lane-47sorts` (workspace ws-47sorts, base = main `c559cc49`). DESIGN-47s3 §1
shipped the preview-tile listing with only the two then-verified orders
(`subscribers`, `random`); this lane closes the remainder: probe-verify the three
unprobed orders and restore the full 5-order menu on both shells.

## 1. Probe results (live, 2026-10-17 — all orders VERIFIED)

Method per `giffy-live-probe`: one anonymous temp token (`GET v2/auth/temporary`)
fetched with the exact `BrowserIdentity` header set (Chrome-Android UA — the token
is UA-bound, so token fetch + all calls carry one identity; same identity shape the
47s3 probe used). Budget respected: **4 requests total** (1 token + 3 probes),
spaced 3s apart. Raw JSON + token deleted after recording; never committed.

Endpoint: `GET {apiBase}v2/niches/search/previews?order=<O>&page=1&count=30`
(no `query=`). All three returned **HTTP 200** with the identical top-level shape
`{previews, page, pages, total}` (page=1, pages=62, total=1834, 30 rows each) and
the identical row shape `{niche, gif, user}` (niche keys `cover, description,
gifs, id, name, owner, rules, subscribers, thumbnail`; full GifDtoShell `gif`;
`urls.poster` present — tile source unchanged).

| order | status | semantics verified |
|---|---|---|
| `posts` | 200 | gif-count **descending** (`niche.gifs` sorted desc on the wire) |
| `alphabetical_asc` | 200 | name **ascending** (first rows: `1980s Porn`, `2000s Porn`, `2d Comics`, `2dicks1chick`, `3D Anal`, `3D Porn` — natural sort) |
| `alphabetical_desc` | 200 | name **descending** (first rows: `Zenless Zone Zero Hentai`, `Zelda Cosplays`, `Young MILFs`, …) |

No BadOrder bodies — all three are accepted on the LISTING endpoint exactly as on
the taxonomy endpoint. The full sort set on `v2/niches/search/previews` is:
`subscribers, posts, alphabetical_asc, alphabetical_desc, random` (site-menu order
per REPORT-niches-parity A5: Subscribers · Posts · Name: A-Z · Name: Z-A + Randomize).

## 2. Shipped

- **Shared order-mapping helper** `nicheListingSorts` (feature/feed,
  NichesViewModel.kt — top-level `List<Pair<String,String>>`, menu order + site
  labels). TDD'd: NicheListingSortsTest written first, red (helper missing), then
  green.
- **NichesScreen.kt + TvNiches.kt** menus switched from the inline 2-order list to
  `nicheListingSorts` (all 5). The ad-hoc `sortLabel` string surgery
  (`removeSuffix("_asc")…` → "Alphabetical") replaced by the shared menu's label —
  the header button now renders "Sort: Name: A-Z" like the site.
- `NichesViewModel.resort()` unchanged — values arrive pre-validated by the menus;
  paging/restart machine untouched.

## 3. Gates

`flock /tmp/gradle-lane-lock ./gradlew :feature:feed:check :app-tv:check
:core:network:check --no-daemon --max-workers=2` — see the lane report.
