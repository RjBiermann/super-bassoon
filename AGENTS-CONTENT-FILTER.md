# AGENTS-CONTENT-FILTER.md — content controls (mostly `:core:database` + `:core:datastore`)

The single ContentFilter choke point (formerly PLAN.md §6). **Leak-zero invariant:** blocked
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
  order creator → tag → keyword. Keyword text spans tags + the gif's `description`
  (DB v3 stores it — the spec says title/description/tags; gif objects carry no title today).
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

Closed since: favorite tag (quick sheet + tag_prefs FAVORITED), collections
(CollectionsScreen, both apps), hide-count Settings display ("Hidden this week").
Import/Export landed — see ContentPrefsBackup below; niche groups landed in DB v5.

## Live status (2026-09)
- Niche groups: DB v5 (`niche_groups`), GroupsScreen live on both apps, BLOCKED groups feed stage 2.
- Hide counts: single rolling 7-day counter (`hide_counts`), no per-reason split — matches the spec.

## Merging groups into custom feeds (SHIPPED 2026-10-02 — see the DONE section at the bottom)

Conceptually `niche_groups` is a special case of `custom_feeds` (an all-tag-refs feed), and the
merge would generalize blocking to per-ref granularity (blockable `creator:` refs — not possible
today; blocking a custom feed's `tag:` refs would map onto existing paths). NOT implemented:
no bug, no user ask — cosmetic consolidation against real churn. Re-evaluate only if blockable
creators become a want.

If picked up later, the inventory:
- Room migration: `niche_groups` rows → `custom_feeds` rows, `tagList` → refs (`tag:<t>` each); drop the table.
- `custom_feeds` gains a `state` column (BLOCKED | FAVORITED | NEUTRAL, as `niche_groups` has today).
- ContentFilter blocked-set derivation rewritten: a BLOCKED custom feed's refs become global block
  sets (`tag:` → tag path, `creator:` → creator path, `niche:` blocked after fetcher expansion via its
  tag list). Preserve pipeline order and the regression tests (`ContentFilterTest.kt`).
- GroupsScreen folds into CustomFeedsScreen on mobile + TV (the `FeedScreen` "add to custom feed"
  quick path is ref-based already, keeps working).
- `ContentPrefsBackup` export format changes → versioned importer for old exports (round-trip gate 7).
- `nicheGifs` page-mapping quirk stays (live API behavior, not ours to fix).
- AGENTS-APP.md groups references updated.

## Orientation filter (planned, decided 2026-10-01)
NOT part of the ContentFilter pipeline — a global DataStore pref `orientation_filter`
(`any`/`horizontal`/`vertical`, shared SettingsScreen) applied strictly AFTER ContentFilter
at the read-time filter stage. See AGENTS-APP.md for wiring points. No hide counts, no toast:
like promoted (stage 0), it's not user content-blocking.

## Merging groups into custom feeds — DONE (2026-10-02 batch 18, user ask)
The deferred merge SHIPPED. `niche_groups` is gone (DB v10, `MIGRATION_9_10`):
each group became a custom feed whose refs are its tags (bare legacy tag refs —
the fetcher treats a ref without a `creator:`/`niche:` prefix as a tag search);
`custom_feeds.state` (BLOCKED | FAVORITED | NEUTRAL) carries the group role.
"custom_feeds ADD COLUMN state TEXT NOT NULL DEFAULT 'NEUTRAL'" + INSERT…SELECT
from `niche_groups` + DROP + `DELETE FROM feed_pages WHERE pageKey LIKE 'group:%'`
(same ALTER-with-default shape as the live-proven MIGRATION_8_9). Migration SQL
sanity-checked on host (states carried, refs parseable as tags, group cache
evicted) and the real migration ran live on TV36 (old v9 profile → v10).

ContentFilter stage 2 rewrite (`refreshBlockedFeeds(dao: CustomFeedDao)`): a
BLOCKED custom feed's tag refs (bare or "tag:"-prefixed) join the global tag
block set; its `creator:` refs join the global creator blocks (the merge's
blockable-creators want); `niche:` refs are SKIPPED — a niche's tag list isn't
local data, mapping it would be a guess. MUST run after `refreshFrom` (it
merges, not replaces, the creator set). Hide-count reason string "group" →
"feed". FeedPagingSource invalidation observer watches `custom_feeds`.

`FeedSource.Group` deleted — group feeds are `FeedSource.Custom(id, name, refs)`
with the same round-robin fetch. `untagged()` (§8 strict tags) applies to
custom feeds whose refs are ALL tag refs (a blended creator/niche feed has no
single bundle to bound to). GroupsScreen/GroupsViewModel deleted;
CustomFeedsScreen hosts the merged management (per-row state cycle
Tab/Blocked/Neutral with the TV giffyFocus ring treatment, BLOCKED rows open no
feed, builder hint updated). More ▾ lost the Groups entry on both apps (mobile
Pinned section now pins FAVORITED feeds; TV already surfaces all custom feeds).
`ContentPrefsBackup` v3: `CustomFeedDef.state` (absent in v2 exports → NEUTRAL;
versioned-importer rule holds — groups were never exported pre-v3).

Device-verified (TV36): migration live, feed created via typed input (and the
screen's initial focus landed on the name field — requestInitialFocus wired for
TV), state cycled Neutral→Tab→Blocked→Neutral, BLOCKED feed opens no feed,
NEUTRAL feed opens. Verification lesson: `adb shell input -t` isn't a thing and
a shared TextField traps D-pad focus on TV (CAST via `input text` into the
focused field; coordinate taps escape the trap) — TV-hosted shared list screens
MUST take `requestInitialFocus = true` and put it on the FIRST focusable.

Re-evaluation trigger for this merge: satisfied by the user ask ("do these").
