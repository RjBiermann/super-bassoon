package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.toEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel

/**
 * One page = one fetch + cache write, shared by the RemoteMediator and the
 * paging source (the source fills an unfetched page itself: Paging consults
 * the mediator only when the source's data is exhausted, so a fast scroll
 * used to reach an unfetched page and stall the feed — user report).
 * Rate-limit invariant: every API request is limiter-accounted; a Favorites
 * fill issues one sub-fetch per favorited creator (#54 interleave), paced
 * sequentially by the shared limiter like every other multi-call path.
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
        // Favorites rotation size (#5): one local DB read per fill — read
        // separately from fetch's pool read, so the END decision below uses the
        // same size the cached row's mapping assumed. A mid-fill favorites edit
        // shifts the mapping entirely (accepted — a new generation refetches).
        val rotationSize = if (feed is FeedSource.Favorites) favorites().size else 0
        // (page - 1) / rotationSize > 0 = loop 1 or later: loop 0 has no
        // previous loop to consult (no content probed yet).
        // rotationSize is 0 for every non-Favorites feed (and an empty
        // favorites set) — the division must stay guarded behind the and.
        val loopOver = rotationSize > 0 && (page - 1) / rotationSize > 0
        if (feed is FeedSource.Favorites && rotationSize > 0 && loopOver && previousRotationExhausted(page, rotationSize)) {
            // #36 endless favorites: the PREVIOUS loop probed empty across the
            // board — every creator's pool ran dry at its inner loop page, and
            // inner pages only advance, so no later loop can regain content.
            // Serve the honest end WITHOUT probing the dead loop again: the
            // exhaustion cost is one dead rotation, never two.
            return false
        }
        val dto = fetch(page)
        val gifs = dto.gifs.map { it.toModel().toEntity(now()) }
        if (feed is FeedSource.Favorites && gifs.isEmpty() && rotationSize == 0) {
            // Nothing favorited → don't pin an empty "no favorites" row (§5).
            return false
        }
        val nowMs = now()
        gifDao.upsertAll(gifs)
        // #5/#36 empty-slot semantics: an empty slot mid-loop chains forward
        // (a later loop's slot may still carry inner pages). An EMPTY BOUNDARY
        // slot no longer ends the feed on its own (the old exhaustion-stride
        // rule stopped while other creators still had unfetched inner pages —
        // user bug #36): keep chaining while the loop ONE rotation back was
        // not entirely empty. The pre-check above + this rule together bound
        // the dead-pass cost at exactly ONE fully-empty rotation.
        val more =
            gifs.isNotEmpty() ||
                when {
                    feed !is FeedSource.Favorites || rotationSize == 0 -> false
                    (page - 1) / rotationSize == 0 -> true
                    else -> !previousRotationExhausted(page, rotationSize)
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

    /** #36 pool-exhaustion rule: true when EVERY row of the rotation before
     *  [page] exists and holds NO gif — a full loop that wanted content and
     *  got none. Inner pages only advance, so no later loop can regain
     *  content for a creator that is past its pool end. Missing rows count
     *  as NOT exhausted (a slot never fetched is unknown, not empty), so a
     *  partially-walked chain keeps probing up to the same rule. [rotationSize]
     *  is the size read at fill time — mid-fill favorites-set edits shift the
     *  mapping (accepted, as in [fetch]). */
    private suspend fun previousRotationExhausted(
        page: Int,
        rotationSize: Int,
    ): Boolean {
        val loopStart = page - rotationSize
        if (loopStart < 1) return false
        return (loopStart until page).all { slot ->
            pageDao.page(feedPageKey(feed, slot))?.gifIds?.isEmpty() == true
        }
    }

    private suspend fun fetch(page: Int): GifsPageDto =
        when (feed) {
            is FeedSource.Search ->
                api.search(
                    searchText = feed.query,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Favorites -> {
                // ponytail: a changed favorite set shifts the mapping until the
                // cache refreshes (read-time filter in FeedPagingSource keeps
                // unfavorited rows out). The mediator guards an empty favorite set,
                // but the paging source self-fills unfetched pages too — a fresh
                // install with zero favorited creators walks an unfetched Favorites
                // page → degenerate empty page, not a divide-by-zero (live-proven
                // on TV36).
                // #5 shuffle-on-refresh: the volatile seed drives loop 0's creator
                // order; #36 endless-favorites: each LOOP past loop 0 re-shuffles
                // the order with a new derived seed (favoritesLoopOrder).
                // #54 interleave: ONE fill fetches EVERY creator — the page's gif
                // list is the round-robin merge of all creators' shares
                // (ceil(pageSize/creators) each), so a screenful alternates creators
                // instead of running one creator for 20 gifs. Inner pages advance
                // one per PAGE (inner = page): a (creator, inner page) pair is
                // served by exactly one page — no duplicates, no loss — so the #36
                // exhaustion machinery works unchanged. Each fill's sub-fetches are
                // limiter-paced sequential API calls (still counted requests).
                val creators = favorites()
                if (creators.isEmpty()) {
                    GifsPageDto()
                } else {
                    val order = favoritesLoopOrder(creators, favoritesSeed(), (page - 1) / creators.size)
                    val perCreator = (pageSize + creators.size - 1) / creators.size
                    val chunks =
                        List(creators.size) { i ->
                            api
                                .userGifs(
                                    username = order[(page - 1 + i) % creators.size],
                                    count = perCreator,
                                    page = page,
                                ).gifs
                        }
                    GifsPageDto(gifs = interleaveChunks(chunks))
                }
            }
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
