# DESIGN-search (Stream D) — issue #57

Owner module: `:feature:search` (+ possibly `:feature:feed`/`:app-*` depending on repro).
**This issue is under-specified — the doc pins hypotheses and the repro plan; the fix
shape depends on which of them reproduces.**

## Objective

**#57** — "search page feeds don't work" (reporter's words; not yet pinned to a
surface, platform, or symptom).

## The search-page surface map (what "feeds" can mean)

The search page (`feature/search/src/main/kotlin/com/rjbiermann/giffyviewer/search/SearchScreen.kt`)
has several distinct feed-like surfaces; the bug is in one or more of these:

1. **Submit path → results feed** — `SearchViewModel.submit`
   (`SearchViewModel.kt:119–127`) → nav `MainFeed/TvFeed(FeedSource.Search(query))`
   (`app-mobile/.../mobile/MainActivity.kt:237`, `app-tv/.../tv/TvMainActivity.kt:316`)
   → the standard cache-first feed pager (`FeedSource.Search` branch of
   `FeedPageFetcher.fetch`, `FeedPageFetcher.kt:101–110` — `api.search(searchText, order
   = feed.sort.ifEmpty { "trending" })`). If THIS is broken, the repro is any search
   submit on either shell.
2. **GIFs scope tab (typed, not submitted)** — shows only matched-TAG chips
   (`SearchScreen.kt:415–440`, `SearchViewModel.kt:180–188` `SCOPE_GIFS → api.tagsMatch`);
   by design there is NO inline gif-result list in that tab ("no tag-results tab",
   issue-#11 slice C note). **A user expecting to see gifs per query in the GIFs tab
   reads this as "search feeds don't work"** — a plausible non-bug/product-expectation
   reading of #57.
3. **Images / Creators / Niches scope tabs** — inline previews loaded by
   `SearchScreen.kt:175–182` → `SearchViewModel.loadScope`
   (`SearchViewModel.kt:149–206`): each scope is VM-cached per trimmed query
   (`cappedInsert`, 12-entry cap, `SearchViewModel.kt:371–378`). Known sharp edges:
   - The scope loader runs in the CALLER's coroutine — `LaunchedEffect(scope, query)`
     cancels it on EVERY keystroke/tab change (`SearchScreen.kt:175–182` comment:
     "the screen's LaunchedEffect cancels it on every keystroke/tab change"); if a
     slow request loses the race against a recomposition re-key, the result may never
     land even though the request completed (the cache check `if (q in map) return`
     happens BEFORE the fetch, so a cancelled-after-write still lands; but a cancelled
     DURING fetch leaves the map empty → the next identical keystroke refetches —
     churn, not breakage).
   - **Images scope rows are stills:** `type=i` returns rows whose `urls.sd/hd` are
     JPGs (AGENTS-NETWORK.md, live-verified 2026-10-02). If image tiles are
     tap-navigable into the player, playback fails ("feeds don't work" on tap).
     Check: the images strip renders via `PosterRow`-style rows — verify their click
     target (see SearchScreen.kt:440+ area) before changing anything.
4. **Explore empty-state strips** (`query.isBlank()`): Top ranged / photos / niches /
   creators / trending tags, all VM-cached loaders wired in
   `SearchScreen.kt:246–355`; failure modes degrade to silent-empty sections by
   design ("failure degrades to an absent section"), which users can also report as
   "not working".

## Root-cause hypotheses (ranked, repro-first)

1. **GIFs-tab expectation mismatch (non-bug):** no inline gif results exist by design;
   only tag chips + the submit path. Highest-probability reading.
2. **Results feed (submit path) fails for certain queries** — e.g. characters that
   leak into the cache key (`FeedSource.Search.keyBase = "search:${query.lowercase().trim()}"`
   + `:sort=` suffix, `FeedSource.kt:33–37`) or the `order = "trending"` default vs the `order = "trending"` default vs the
   live `BadOrder` drift history on `/v2/gifs/search` (the endpoint's accepted orders
   were re-verified 2026-10-15 — `trending` IS the verified default here; a drift
   would 400 every search feed page 1 → `MediatorResult.Error` → "couldn't load
   [query]"). If reproduced, this is the #58-family failure surface.
3. **Scope-tab cancelled-fetch race** (hypothesis 3 above) — tab shows empty
   indefinitely until retype; fix = move the in-flight fetch to a VM-owned scope
   keyed by (query, scope) instead of caller-coroutine cancellation.
4. **Image-tile → player crash/failure** (stills with jpg urls).

## Proposed approach (repro-gated)

1. **Repro matrix on device (mobile + TV), first session:** (a) type a query, observe
   each scope tab without submitting; (b) submit and watch the results feed; (c)
   tap an Images row; (d) retry with a multi-word query and a punctuation-only query.
   Logcat `FeedMediator`/`Auth401` lines decide hypothesis 2; tab behavior decides 1/3.
2. Fix ONLY the reproduced hypothesis:
   - 2 → likely nothing to change client-side (server drift) — adapt the endpoint
     param order per AGENTS rule ("adapt API endpoint paths only on drift") and
     re-verify live.
   - 3 → minimal: `loadScope` no longer relies on the caller's coroutine (VM-owned
     job keyed by (query, scope), cancellation by newer query only).
   - 4 → make image rows open a still viewer or make them non-navigable (site-parity:
     Images is an upload-surface-only scope; tap → nothing is site behavior).
   - 1 → no code; close the issue with an explanation comment (product behavior,
     documented in AGENTS-APP full-scope search spec) or ask the reporter to confirm.

## Acceptance conditions

- Repro record posted in the issue thread BEFORE any change (which shell, which
  surface, logcat excerpt).
- Any code change ships with: JVM unit tests for the changed scope/VM logic
  (`SearchViewModelTest` family — MockWebServer per the testing policy), gates green.
- Device gate: search submit → results feed loads; all four scope tabs render or
  show honest empty/error states.

## Non-goals

- No server search-history sync (spec'd-not-scheduled fallback, normative).
- No niches-suggest wiring (user-token-gated 401 — normative open item).
- No new results tab/surface beyond what exists.

## Probe flags

- **Live-site probe needed only if hypothesis 2 reproduces** (verify
  `/v2/gifs/search` order param acceptance for the exact query the reporter used).
- **Device-only:** everything above is UI/state behavior; no JVM-rung substitute.
- **Reporter contact needed:** the issue text ("search page feeds don't work") is not
  actionable as-is — which surface + shell + what "doesn't work" means (empty? error?
  nothing happens?) must be pinned; the orchestrator may want a comment on #57.
