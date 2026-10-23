package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.toEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel

/** #62 favorites fan-out bound: creators fetched per fill (one limiter burst). */
internal const val FAVORITES_CREATORS_PER_FILL = 5

/**
 * One page = one fetch + cache write, shared by the RemoteMediator and the
 * paging source (the source fills an unfetched page itself: Paging consults
 * the mediator only when the source's data is exhausted, so a fast scroll
 * used to reach an unfetched page and stall the feed — user report).
 * Rate-limit invariant: every API request is limiter-accounted; a Favorites
 * fill issues one sub-fetch per creator in its round window (#62 fan-out
 * bound), paced sequentially by the shared limiter like every other
 * multi-call path.
 */
class FeedPageFetcher(
    private val feed: FeedSource,
    private val gifDao: GifDao,
    private val pageDao: FeedPageDao,
    private val api: GifsApi,
    private val pageSize: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val favorites: suspend () -> List<String> = { emptyList() },
    /** #5 shuffle-on-refresh rotation seed (Favorites only; 0 = DB order). */
    private val favoritesSeed: () -> Long = { 0L },
) {
    /** Fetches and caches [page]; false = nothing fetched (empty/degenerate). */
    suspend fun fill(page: Int): Boolean {
        // Favorites: ONE local read per fill (#62) drives both the page mapping
        // and the END decision below, so the row's mapping and the exhaustion
        // rule always agree. A mid-fill favorites edit shifts the mapping
        // entirely (accepted — a new generation refetches).
        val plan =
            if (feed is FeedSource.Favorites) {
                favoritesPagePlan(favorites(), favoritesSeed(), page, pageSize)
            } else {
                null
            }
        // (page - 1) / windowPages > 0 = round 1 or later: round 0 has no
        // previous round to consult (no content probed yet). windowPages is 0
        // for a non-Favorites feed and an empty favorites set — the divisions
        // must stay guarded. For k <= FAVORITES_CREATORS_PER_FILL a round is a
        // single page and the window stays k pages (the pre-#62 #36 margin);
        // above it the window is the round's page count.
        val windowPages = plan?.roundPages ?: 0
        val loopOver = windowPages > 0 && (page - 1) / windowPages > 0
        if (plan != null && loopOver && previousRotationExhausted(page, windowPages)) {
            // #36 endless favorites: the PREVIOUS round probed empty across the
            // board — every creator's pool ran dry at its inner page, and inner
            // pages only advance, so no later round can regain content.
            // Serve the honest end WITHOUT probing the dead round again: the
            // exhaustion cost is one dead round, never two.
            return false
        }
        val dto = fetch(page, plan)
        val gifs = dto.gifs.map { it.toModel().toEntity(now()) }
        if (plan != null && gifs.isEmpty() && plan.usernames.isEmpty()) {
            // Nothing favorited → don't pin an empty "no favorites" row (§5).
            return false
        }
        val nowMs = now()
        gifDao.upsertAll(gifs)
        // #5/#36 empty-slot semantics: an empty slot mid-round chains forward
        // (a later round's slot may still carry inner pages). An EMPTY BOUNDARY
        // slot no longer ends the feed on its own (the old exhaustion-stride
        // rule stopped while other creators still had unfetched inner pages —
        // user bug #36): keep chaining while the round ONE rotation back was
        // not entirely empty. The pre-check above + this rule together bound
        // the dead-pass cost at exactly ONE fully-empty round.
        val more =
            gifs.isNotEmpty() ||
                when {
                    plan == null || windowPages == 0 -> false
                    (page - 1) / windowPages == 0 -> true
                    else -> !previousRotationExhausted(page, windowPages)
                }
        pageDao.upsert(
            FeedPageEntity(
                pageKey = feedPageKey(feed, page),
                gifIds = gifs.map { it.id },
                nextPageKey = if (more) feedPageKey(feed, page + 1) else null,
                fetchedAt = nowMs,
            ),
        )
        return more
    }

    /** #36 pool-exhaustion rule: true when EVERY row of the round before
     *  [page] exists and holds NO gif — a full round that wanted content and
     *  got none. Inner pages only advance, so no later round can regain
     *  content for a creator that is past its pool end. Missing rows count
     *  as NOT exhausted (a slot never fetched is unknown, not empty), so a
     *  partially-walked chain keeps probing up to the same rule. [roundPages]
     *  is the plan's round length (= the exhaustion window), read before the
     *  fetch — a mid-fill favorites-set edit shifts the mapping (accepted,
     *  as in [fetch]). */
    private suspend fun previousRotationExhausted(
        page: Int,
        roundPages: Int,
    ): Boolean {
        val loopStart = page - roundPages
        if (loopStart < 1) return false
        return (loopStart until page).all { slot ->
            pageDao.page(feedPageKey(feed, slot))?.gifIds?.isEmpty() == true
        }
    }

    private suspend fun fetch(
        page: Int,
        plan: FavoritesPlan?,
    ): GifsPageDto =
        when (feed) {
            is FeedSource.Search ->
                api.search(
                    searchText = feed.query,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Favorites -> favoritesPage(plan)
            is FeedSource.ForYou -> api.feedForYou(count = pageSize, page = page)
            is FeedSource.Niche ->
                api.nicheGifs(
                    nicheId = feed.id,
                    count = pageSize,
                    page = page,
                    // Live drift 2026-10: "trending" is now a BadOrder — the
                    // server's default ordering rides on an omitted param.
                    order = feed.sort.ifEmpty { null },
                )
            is FeedSource.Creator ->
                api.userGifs(
                    username = feed.username,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Custom -> {
                // Round-robin across the blended refs ("creator:<username>" /
                // "tag:<text>" / "niche:<id>|<name>"; groups are expanded to
                // their tags by the builder). Refs never shrink mid-generation
                // (definitions are edited in the builder), so the mapping is
                // stable. Empty feeds are valid (filled later via quick-add)
                // and land an empty page here.
                val refs = feed.refs
                if (refs.isEmpty()) {
                    GifsPageDto()
                } else {
                    val ref = refs[(page - 1) % refs.size]
                    val inner = (page - 1) / refs.size + 1
                    when {
                        ref.startsWith("creator:") ->
                            api.userGifs(username = ref.removePrefix("creator:"), count = pageSize, page = inner)
                        ref.startsWith("niche:") ->
                            api.nicheGifs(
                                nicheId = parseNicheRef(ref)?.first ?: "",
                                count = pageSize,
                                page = inner,
                            )
                        else ->
                            api.search(
                                searchText = ref.removePrefix("tag:"),
                                count = pageSize,
                                page = inner,
                                order = "trending",
                            )
                    }
                }
            }
            is FeedSource.TopThisWeek -> api.trendingPopular(order = "top_week", count = pageSize, page = page)
            is FeedSource.TopRanged ->
                // Bare ranged search (issue #11, live-verified 2026-10-15):
                // no query — the order IS the pool selection.
                api.search(searchText = "", order = feed.range, count = pageSize, page = page)
            else -> api.trendingPopular(count = pageSize, page = page)
        }

    /** #62: one sub-fetch per creator in the round window, at the round's inner
     *  page, merged round-robin (#54 interleave). Each fill's sub-fetches are
     *  limiter-paced sequential API calls (still counted requests); the window
     *  rotates per page, so consecutive pages surface new creators. No
     *  favorites → empty page (the paging source self-fills unfetched pages
     *  too: a fresh install with zero favorited creators walks an unfetched
     *  Favorites page → degenerate empty page, not a divide-by-zero,
     *  live-proven on TV36). A changed favorite set shifts the mapping until
     *  the cache refreshes (read-time filter in FeedPagingSource keeps
     *  unfavorited rows out). */
    private suspend fun favoritesPage(plan: FavoritesPlan?): GifsPageDto {
        if (plan == null || plan.usernames.isEmpty()) return GifsPageDto()
        val chunks =
            plan.usernames.map { username ->
                api.userGifs(username = username, count = plan.perCreator, page = plan.innerPage).gifs
            }
        return GifsPageDto(gifs = interleaveChunks(chunks))
    }
}

/** #62 favorites page mapping (pure, unit-tested) for [page] of [creators]. */
internal data class FavoritesPlan(
    /** The creators this fill fetches — one round-window of the favorite set. */
    val usernames: List<String>,
    /** The creators' inner page (round number + 1). */
    val innerPage: Int,
    /** Gifs per creator: ceil(pageSize / min(k, fan-out)) — independent of
     *  the window the creator lands in, so its inner-page slice stays a pure
     *  function of [innerPage]. */
    val perCreator: Int,
    /** Page count of one round = the exhaustion window: rounds of this many
     *  pages cover the whole favorite set at a single inner page. For a set
     *  that fits one fan-out it is the set size — the pre-#62 window, so the
     *  #36 "one empty page never ends the feed" margin is unchanged there.
     *  0 = no creators (nothing to fetch, nothing to exhaust). */
    val roundPages: Int,
)

/** #62 fan-out bound (pure, unit-tested): [FAVORITES_CREATORS_PER_FILL]
 *  creators per fill — one limiter burst — instead of the whole favorite set
 *  (#54 cost k limiter-paced requests per page, so a screenful scaled with k
 *  and the dead round at the pool's end cost k²). A ROUND walks the whole set
 *  at ONE inner page across ceil(k / 5) pages; consecutive rounds advance the
 *  inner page, so a (creator, inner page) pair is still served by exactly one
 *  page — no duplicates, no loss — and the #36 exhaustion machinery works on
 *  the round's page count. Leader rotation (#54 novelty) and the loop
 *  reshuffle (#36) are unchanged.
 *
 *  k <= 5 is byte-identical to the #54 mapping: one page per round holds the
 *  whole set at inner page = page, perCreator = ceil(pageSize / k), creator
 *  order = the loop order rotated by (page-1) % k.
 *
 *  perCreator = ceil(pageSize / min(k, 5)) is deliberately NOT window-sized:
 *  a creator's slice is a pure function of its inner page, so it cannot overlap
 *  a slice fetched for it in another round's window. Consequence accepted: a
 *  trailing window (k % 5 != 0) serves a thinner page (k=6 → 20 + 4 items). */
internal fun favoritesPagePlan(
    creators: List<String>,
    seed: Long,
    page: Int,
    pageSize: Int,
): FavoritesPlan {
    val size = creators.size
    if (size == 0) return FavoritesPlan(emptyList(), page, 0, 0)
    val roundPages = (size + FAVORITES_CREATORS_PER_FILL - 1) / FAVORITES_CREATORS_PER_FILL
    val round = (page - 1) / roundPages
    // k <= 5: one page per round, so every page advances the inner page — the
    // pre-#62 mapping. Its reshuffle generation stays (page-1)/k (one
    // generation per k pages) so the served order is byte-identical.
    val generation = if (roundPages == 1) (page - 1) / size else round
    val order = favoritesLoopOrder(creators, seed, generation)
    val windowStart = ((page - 1) % roundPages) * FAVORITES_CREATORS_PER_FILL
    val windowSize = minOf(FAVORITES_CREATORS_PER_FILL, size - windowStart)
    // Slice budget from the FAN-OUT, never the window: a window-sized count
    // made a creator's slice depend on which round it sat in (the 1-slot tail
    // fetched 20 gifs at inner page 1, then the same ids again in a 5-slot
    // window at inner page 2 — cross-page duplicates). k <= 5: min = size =
    // the window, so the #54 mapping (ceil(pageSize / k)) is unchanged; above
    // it every creator gets 4 per round and a thin tail page is accepted.
    val perCreatorSlots = minOf(size, FAVORITES_CREATORS_PER_FILL)
    // Leader rotation: a page doesn't always start with the same creator. With
    // one page per round the window is the whole set, so this is exactly the
    // #54 order[(page-1+i) % k].
    val lead = (page - 1) % windowSize
    return FavoritesPlan(
        usernames = List(windowSize) { i -> order[windowStart + (lead + i) % windowSize] },
        innerPage = round + 1,
        perCreator = (pageSize + perCreatorSlots - 1) / perCreatorSlots,
        roundPages = if (roundPages == 1) size else roundPages,
    )
}

/** #5 shuffle-on-refresh rotation: creators rotated forward by
 *  ((seed % size) + size) % size — modulo-safe for negative seeds. Rotation
 *  preserves the round-robin inner-page math (page n still maps to slots
 *  (n-1)/size + 1); only the creator ORDER of the mapping rotates. Seed 0 =
 *  plain DB order (the pre-rotation first-load behavior). The seed is volatile
 *  repository state (reshuffleFavorites) and never persisted. */
internal fun rotateCreators(
    creators: List<String>,
    seed: Long,
): List<String> {
    if (creators.isEmpty() || seed == 0L) return creators
    val at =
        (((seed % creators.size) + creators.size) % creators.size)
            .toInt()
    return creators.drop(at) + creators.take(at)
}

/** #36 endless-favorites mapping (pure, unit-tested): the creator ORDER for
 *  page n's loop. Loop 0 keeps the #5 refresh rotation — [rotateCreators]
 *  with the session seed (today's first-load/refresh behavior, unchanged).
 *  Every later loop re-shuffles with a NEW derived seed ([loopSeed]) via the
 *  stdlib Fisher–Yates ([Collections.shuffle] with a seeded [java.util.Random]:
 *  spec-stable and deterministic), so the served creator-grouping sequence
 *  never repeats the previous loop's order while staying deterministic within
 *  a session — the volatile seed dies with the process, refresh re-shuffles
 *  explicitly. Single/empty pools have one trivial order. */
internal fun favoritesLoopOrder(
    creators: List<String>,
    seed: Long,
    loop: Int,
): List<String> {
    if (creators.size <= 1 || loop == 0) return rotateCreators(creators, seed)
    val order = creators.toMutableList()
    java.util.Collections.shuffle(order, java.util.Random(loopSeed(seed, loop)))
    return order
}

/** #54 interleave merge (pure, unit-tested): round-robin across the
 *  per-creator chunks in fetch order — every chunk's first gif, then every
 *  chunk's second gif, … Unequal pools interleave honestly: a chunk that ran
 *  dry stops contributing while the others continue. */
internal fun <T> interleaveChunks(chunks: List<List<T>>): List<T> =
    buildList {
        val depth = chunks.maxOfOrNull { it.size } ?: 0
        for (index in 0 until depth) {
            for (chunk in chunks) {
                if (index < chunk.size) add(chunk[index])
            }
        }
    }

/** Volatile session seed → per-loop re-seed. Both coefficients are odd, so
 *  for a fixed session seed the map loop → seed is injective over Z/2⁶⁴:
 *  every loop reshuffles with a NEW seed — unit-pinned — and the mapping is
 *  plain arithmetic, replayable within a session (the volatile base seed
 *  dies with the process; refresh re-shuffles explicitly). */
internal fun loopSeed(
    seed: Long,
    loop: Int,
): Long = seed * -7046029254386353131L + loop * -49064778989728563L
