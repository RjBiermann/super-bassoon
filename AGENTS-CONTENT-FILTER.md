# AGENTS-CONTENT-FILTER.md — content controls (mostly `:core:database` + `:core:datastore`)

The single ContentFilter choke point. Spec: PLAN.md §6. **Leak-zero invariant:** blocked
content must never render on ANY surface — feed, search, shuffle, groups, For You, random.

## One interceptor, applied everywhere

Every data source runs through the same filter before UI:
paging remote load, Room cache reads, search results, group feeds, shuffle pool,
"Surprise me", custom/For You blends. If you add a new surface, wire the filter or the feature doesn't ship.

Pipeline order (per item):
0. **Promoted filter:** `promoted: true` → drop unconditionally — paid placements (§0 no-ads). Not
    counted in hide counts (that UI is for the user's own blocks, not ads). First check, runs before
    the user's block sets
1. Creator block (exact username, O(1) map lookup)
2. Blocked niche-group tag sets (built once per session, invalidated on edit)
3. Tag exact match vs `tag_prefs`
4. Keyword substring — case-insensitive over title/description/tags

Track hide counts by reason → Settings shows "Hidden this week: N (x by tags, y by keywords)".

## Effects

| Feature | Effect |
|---|---|
| Block creator | hide everywhere |
| Favorite creator | dedicated feed + 70% weight in For You |
| Block tag | exact match only (not substring) |
| Block keyword | case-insensitive substring over title/description/tags |
| Niche groups | tag bundles → full feeds AND blockable macro-filters |
| Collections | organizational grouping of creators |

## Import/Export
Settings → full JSON export/import of prefs + groups + collections. Must round-trip
on a fresh install (acceptance criterion). No server, no backup API — local file only.

## Implemented (Phase 7 slice 1, verified live on Phone34)
- DB v2: `creator_prefs`, `tag_prefs`, `keyword_blocks` via `GiffyDatabase.MIGRATION_1_2` —
  wired with `addMigrations` in BOTH AppModule and TvAppModule (forgetting it crashes on
  first feed load after an update: "migration from 1 to 2 was required but not found").
- `ContentFilter` (core:database, `@Inject @Singleton`): session-cached blocked
  creator/tag/keyword sets, `refreshFrom(ContentPrefsDao)` before filtering, pipeline
  order creator → tag → keyword. Tags-only text: upstream gif objects carry no
  title/description, so keyword matching is substring over tags.
- **Promoted filter (stage 0, live-verified):** the gif DTO's `promoted` field is nullable/absent on
  organic items — treat missing as `false`; a `true` drops the item before every user-block check
  and does not touch hide counts. Wired as the first branch of `ContentFilter.run` so PROMOTED
  leaks same zero as blocks (one test case added: `promoted=true` with zero prefs set → filtered,
  no hide-count row written)
- Choke point wired in `FeedPagingSource.load` (filters Room reads) + its invalidation
  observer now watches the 3 pref tables, so a block edit re-filters the live grid
  instantly (verified: block @emily.reed → tile gone without a manual refresh).
- Entry UI: long-press a mobile tile → `QuickBlockSheet` (Block creator / 3 tags /
  keyword / don't block) → FeedViewModel writes → invalidationTracker → re-filter.
- Tests: `core/database/src/test/.../ContentFilterTest.kt` (leak-zero invariant:
  case-insensitivity, tag exact-match ≠ substring, precedence creator > tag > keyword).

**Favorite creator (Phase 7 slice 4, live-verified on Phone34):**
- `creator_prefs.state` = `FAVORITED` (no migration needed — single state column).
- `QuickBlockSheet` gained "Favorite @user" / "Unfavorite @user" (label follows
  `ContentPrefsDao.creatorState(username)` flow); Settings has a Favorited creators
  section (unfavorite = delete row).
- Dedicated feed: `FeedSource.Favorites` — mediator round-robins over favorited creators
  via the live-verified `GET /v2/users/{username}/search` (userName-filtered, paginated);
  global page n → creator[(n-1) % n], per-creator page (n-1)/n + 1.
- Read-time filter in FeedPagingSource: rows whose creator is no longer FAVORITED never
  render (instant un-favorite; round-robin cache refreshes on TTL).
- Ceiling (ponytail): feed stops when any creator's round comes back empty; a changed
  favorite set shifts the round-robin mapping until TTL refresh. For You blend (70%) is
  a later slice.
- Mobile round-trip verified: favorite @sweetiefox → Favorites chip shows their gifs →
  `creator_prefs` row `sweetiefox|FAVORITED` → Settings unfavorite → empty state text.

Not yet: favorite tag, niche groups, collections, feed_prefs, hide-count stats,
Import/Export, TV quick actions/favorite UI (TV row shows only when favorites exist).
