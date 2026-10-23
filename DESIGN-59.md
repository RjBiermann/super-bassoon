# DESIGN-59 — favorites feed endless-paging regression after #54

## Objective

Restore the reported behavior: the Favorites feed loads endlessly (every
unfetched pool page is reachable by the walk) instead of stopping at the
scroll depth reached under a previous app version / a previous favorites-set
generation.

## Title

Issue #59: "feed doesn't load endlessly anymore — after #54". REGRESSION
against the #54 interleave change (`cdaac67`, first shipped in release
**v0.7.1**; v0.7.0 does not contain it).

## Root cause (file:line evidence)

The #54 change altered the Favorites page→content mapping
(`feature/feed/.../FeedPageFetcher.kt:129–151`): page *n* now fetches EVERY
favorited creator at **inner page = n** (perCreator = ⌈pageSize/creators⌉
each, round-robin merged), replacing the pre-#54 mapping of ONE creator at
inner `(n-1)/creators.size + 1`.

The page→content mapping is NOT part of the cache identity: page rows keep
the key `"fav:v1:p<n>"` under both mappings (`FeedSource.kt:93,139–142`).
Nothing ages favorites rows out:

- `evictStale(olderThan)` (`core/database/.../Daos.kt:52–53`) has **no caller**
  in the whole codebase — the documented TTLs are never swept from
  `feed_pages`.
- Favorites rows are only evicted on a forced REFRESH
  (`reshuffleFavorites()` → `evictFavorites`, `FeedRepository.kt:229–236`) and
  on the two UI-set-change paths (`evictFavoritesCache` callers,
  `FeedViewModel.kt:265`, `SettingsViewModel.kt:63,209`).
- The walk (the read path) served any cached row regardless of age
  (`walkFill`, former `FeedPagingSource.kt:80`: `if (cached != null) return cached`).

Consequence (the reported dead end): a ledger built under the OLD mapping
(the v0.7.0→v0.7.1 app update keeps the DB, or any favorites-set shift not
covered by the UI evictions) is served verbatim by the walk. When the walk
finally reaches an unfetched page beyond the stale ledger, the current fill
probes **inner page = that page number**, which is past every creator's
pool (pools dried at a far smaller inner page); `previousRotationExhausted`
(`FeedPageFetcher.kt:30–39, 92–102`) ends the feed honestly-but-wrongly ONE
window later, while unfetched pool content remains. The feed is capped at the
previous generation's scroll depth in both directions.

Live probes (2026-10-23, anon token, 5 gate creators) confirm the server side
is fine and unchanged by #54: `users/{username}/search` past the pool returns
200 EMPTY (no 400 wall, zero overlap between consecutive inner pages, no
count floor — metart 36 gifs walked 1:1 at count=1..7). Request cadence is
also not the driver (limiter paces, gate saw 0 429). The regression is purely
stale-ledger serving.

## Approach (fix)

`feature/feed/.../FeedPagingSource.kt` — `walkFill` revalidates a Favorites
row past the feed's TTL instead of serving it: the stale hit goes through the
shared fetcher (`fill(page)`), exactly what `FeedMediator.REFRESH` already
does for page 1. The refetched row is re-stamped, so within-TTL reads keep
the cache fast path; nothing changes for other feeds; airplane-mode (rows
from the last session are simply OLD → refilled on demand once tiles are
fetched — the walk self-fills as before) and the rate-limit budget are
untouched (no new requests when nothing is stale; stale refills are the same
fills a cold cache performs).

The refill converts old-mapping rows (including the old dead tail) into
fresh current-mapping rows BEFORE the exhaustion-window math reads them, so
`previousRotationExhausted` regains its honest meaning over a consistent
ledger.

## Acceptance conditions

- JVM regression test `FavoritesStaleLedgerTest`:
  - `a stale favorites ledger is revalidated and the walk still reaches every pool page` —
    every live pool gif reaches a row, no stale id survives, one sub-fetch
    per creator per inner page, bounded dead-window tail, walk terminates
    with a null key. FAILED before the fix (served only stale `old_*` ledger
    ids), passes after.
  - `a fresh favorites row still serves from cache` — within-TTL hit serves
    with zero sub-fetches (stale-while-revalidate unchanged).
- Full `:feature:feed` unit suite green.
- No Room schema change (no migration needed).
- Device gate (own recommendation, serialized gate phase): a device that updated
  v0.7.0 → v0.7.1 with preset favorites should show the Favorites feed
  advancing past the pre-update depth after an app restart + >10 min since
  the last favorites open (TTL lapse), without pull-to-refresh.

## Non-goals

- No per-base `evictStale` sweep, no background eviction job (No-WorkManager
  rule); the walk revalidates on demand.
- No mapping-generation tag in the cache key / schema (rows self-heal via TTL).
- No change to the interleave shape, the #36 exhaustion bounds, the
  refresh re-seed contract, or any request cadence.
