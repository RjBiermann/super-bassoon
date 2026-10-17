# REPORT — Niche pages feature parity (issue #47) — live-site capture, 2026-10

**Method:** read-only Playwright observation of `https://www.redgifs.com/niches` and
`https://www.redgifs.com/niches/just-boobs` (anonymous, age-gate accepted, cookie banner
dismissed; ~5 page loads total, well under the rate budget). No code edited, no commits.
Evidence = rendered DOM text + the site SPA's own API requests, recorded per section.

## 1. Captured site feature inventory

### A. Niches listing page (`/niches`, title "Top Niches")

| # | Site feature | Evidence | URL/endpoint shape |
|---|---|---|---|
| A1 | Visual tile grid: **one preview gif per niche** (poster jpg + autoplaying looped `<video>`), title under tile | `.tileItem` DOM: `a[aria-label="Just Boobs"]` + poster img + `video autoplay loop` | tile links `/niches/<slug>?gif=<id>&source=all_niches` |
| A2 | Data source is `v2/niches/search/previews` (NOT the taxonomy `/v2/niches` the app uses) | SPA fetch on load | `GET /v2/niches/search/previews?order=subscribers&page=1&count=30` |
| A3 | Tiles show **no counts** (members/posts appear only inside niche pages) | tile DOM = image + `.tileTitle` only | — |
| A4 | Category chip row (All · Age · Amateur · Animated · Body Art · Clothing · Communities · Cum Play · Ethnicity · Exhibition …) | buttons on page; chip click fetches | `GET …/v2/niches/search/previews?order=subscribers&page=1&count=30&category=Animated` |
| A5 | Sort menu: **Subscribers · Posts · Name: A-Z · Name: Z-A** + **Randomize Niches** button (count bumps to 100) | menu DOM text; randomize fetch | `GET …/v2/niches/search/previews?order=random&page=1&count=100&category=…` |
| A6 | `create niche` button (creator tool) | button on page | login-gated; viewer-only → out of scope (§0) |
| A7 | Scrolling the captured viewport fired no further niche-page fetches (infinite scroll not observed in-window; "Show more" seen) | scroll probe | — |

### B. Individual niche page (`/niches/just-boobs`, "… Niche GIFs")

| # | Site feature | Evidence | URL/endpoint shape |
|---|---|---|---|
| B1 | Niche identity header: **cover image + square avatar image + name** | `.nicheImage` (`userpic.redgifs.com/niches/covers/boobs.jpg`) + `.nicheAvatar` (`…/niches/thumbnails/boobs.jpg`) | images carried by `GET /v2/niches/{id}` (DTO `cover`/`thumbnail`) |
| B2 | Description (clamped 2 lines) + stats **"633K Members / 929K Posts"** + **"Join Niche"** button | `.nicheInfo` innerText | `GET /v2/niches/{id}` |
| B3 | Tabs **Feed · About** | `[role="tab"]` Feed(selected)/About | same page |
| B4 | Feed tab: gif feed `order=hot` default, count=30 | SPA fetch | `GET /v2/niches/{id}/gifs?count=30&page=1&order=hot` |
| B5 | Feed tab sort menu: **Hot · Latest · Top** | menu DOM text | orders hot/latest/best (app-documented mapping "Top"→`best`) |
| B6 | Niche Rules (numbered) | About content: "1. Boobs 2. Porn featuring female breasts. 3. Are title and description relevant to the gif?" | `GET /v2/niches/{id}` (`rules`) |
| B7 | Top Creators with **follower counts + per-row Follow button** (About/sidebar) | "koreancaro 28K FOLLOWERS Follow" ×10 | `GET /v2/niches/{id}/top-creators` |
| B8 | **Moderators** section ("Moderators — RedGIFs Team") | About content tail | unknown field — NOT in app's `NicheDetail` DTO; flag for a live probe before building |
| B9 | Related niches **with counts**: "Niches you might also like: JAV Moms 9K Members 2K Posts — Visit Niche" (×4); also a feed-tab strip "5 Niches you might like" | sidebar text | `GET /v2/niches/{id}/related` (observed `…/niches/mombods/related`) |
| B10 | Ad modules: "Creators you might enjoy / OnlyFans creators you might enjoy — Visit Page" | sidebar text | `v2/adv/creators-traffic` — §0-banned, never build |
| B11 | Player/card chrome per gif (like counts, creator Follow, Sound Off, fullscreen, Prev/Next video) | buttons on page | already the app's shared card/player surfaces |
| B12 | Deep-link query `?gif=<id>` on the niche URL (tile links) | listing hrefs | minor; not probed for behavior |

Login-gated: join state + create-niche only (join via `POST/DELETE /v2/niches/{id}/subscription` already verified; create-niche out of scope).

## 2. App-side mapping

Shared screens host on TV too (NichesScreen/NicheAboutScreen/NicheFeedHeader via app-tv).

| Site feature | Mobile | TV | Notes |
|---|---|---|---|
| A1 preview tiles | MISSING (text list `NichesScreen.kt:139`) | MISSING (`TvNiches.kt:107`) | endpoint already verified; needs category/order/pagination params probe (A4/A5 shapes observed live) |
| A2/A3 — | n/a (app shows counts the site tiles lack — app-superior) | same | keep counts even if tiles land |
| A4 category chips | SHIPPED `NichesScreen.kt:118-137` | **MISSING** — TV screen renders neither chips nor sort | VM (`NichesViewModel.kt:70`) already supports both |
| A5 sort menu | SHIPPED `NichesScreen.kt:58-90` (Subscribers/Posts/A–Z/Z–A/Random) | **MISSING** | same shared VM |
| B1 cover/avatar images | MISSING — `NicheDetail.cover/thumbnail` (`GifsApi.kt:323-324`) unused by `NicheAboutScreen.kt` / `FeedHeader.kt` | MISSING | DTO already carries both URLs |
| B2 desc + Members/Posts + Join | SHIPPED on niche feed `NicheFeedHeader` (`FeedHeader.kt:71-109`, used `FeedScreen.kt:230`) + About (`NicheAboutScreen.kt:99-124`); Join on feed header MISSING (info-only header) | SHIPPED feed header (`TvSourceFeedScreen.kt:260`); Join on feed header MISSING | TV About = shared `NicheAboutScreen` (`TvMainActivity.kt:297`) |
| B3 Feed/About tabs | PARTIAL — split across two screens (feed tab + separate About screen via Info icon); site inlines About as a second tab | PARTIAL (same shape) | two-screen split is arguably better mobile UX; parity gap is Join-on-feed-header only |
| B4 niche feed from listing | SHIPPED (`onOpenNiche` → `FeedSource.Niche` pager) | SHIPPED | |
| B5 niche sorts Hot/Latest/Top | SHIPPED `FeedSource.kt:214-219` | SHIPPED (`FeedFilterDialog`, `TvSourceFeedScreen.kt:378`) | |
| B6 Niche Rules | SHIPPED `NicheAboutScreen.kt:120-133` | SHIPPED (shared) | |
| B7 top creators + followers | SHIPPED rows `NicheAboutScreen.kt:136-158`; per-row Follow button MISSING (Follow exists in player QuickSheet) | same | |
| B8 Moderators | MISSING (DTO lacks field) | MISSING | live-probe `v2/niches/{id}` full payload first |
| B9 related niches + counts | SHIPPED `NicheAboutScreen.kt:160-176` | SHIPPED (shared) | feed-tab "you might like" strip MISSING (sidebar-only on site too — optional) |
| B10 OnlyFans/ad modules | — | — | banned, ignore |
| B12 `?gif=` deep-link | MISSING | MISSING | trivial value; skip unless asked |

## 3. Recommended build order (smallest first)

1. **Join/Leave Niche button on the niche feed header** (B2/B3 residual): `NicheFeedHeader` gains an optional join row (shared `NicheJoinViewModel` already does everything) — mobile + TV free via the shared header. Smallest, closes the only functional gap.
2. **TV category chips + sort menu on TvNichesScreen** (A4/A5): pure UI on the already-shared `NichesViewModel` state; no new endpoints.
3. **Niche cover/avatar images** (B1): render `NicheDetail.cover`/`thumbnail` in `NicheFeedHeader` + `NicheAboutScreen`; zero network change.
4. **Preview tiles on the niches listing** (A1, biggest visual parity): switch listing to `GET /v2/niches/search/previews` (verified endpoint, already DTO'd for search). Needs a **live probe first** for the observed `category=`/`order=`/`count=100` shapes (AGENTS-NETWORK documents only `order=best_match&query=`); keep counts via taxonomy endpoint or drop them to match site tiles.
5. **Per-row Follow on About's Top Creators** (B7): reuse `FeedViewModel.toggleFollowCreator` pattern; defer to QuickSheet parity unless asked.
6. **Moderators (B8)**: probe `GET /v2/niches/{id}` full JSON for a moderators field; build only if present.
7. **B12 deep-link `?gif=`**: skip (no user ask).

## 4. Endpoints needing live verification before build (no-guess rule)

- `GET /v2/niches/search/previews` with `order=subscribers|random`, `category=<name>`, `page=`, `count=100` — all observed in the site SPA's traffic but not yet BadOrder/shape-verified the way AGENTS-NETWORK requires.
- `GET /v2/niches/{id}` full payload for a `moderators` field (app DTO consumes only description/cover/thumbnail/counts/rules/tags).
