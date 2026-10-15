package com.rjbiermann.giffyviewer.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.SearchHistoryEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.NicheRef
import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheDto
import com.rjbiermann.giffyviewer.core.network.SuggestDto
import com.rjbiermann.giffyviewer.core.network.TrendingTagDto
import com.rjbiermann.giffyviewer.core.network.TrendingTagsDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import com.rjbiermann.giffyviewer.core.network.dto.toModels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search (PLAN §7): debounced typed autocomplete (every keystroke request
 * counts toward the ≤10/5s window, so the debounce + mapLatest cancel is
 * mandatory), Room history (cap 50, per-row remove + clear-all), and the
 * results feed = a standard filterable feed (FeedSource.Search, cache-first).
 * Creator results row refreshes per query, anonymous OK.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        private val db: GiffyDatabase,
        private val filter: ContentFilter,
        savedState: androidx.lifecycle.SavedStateHandle,
    ) : ViewModel() {
        /**
         * The live text field, restored across process death (gate: UI state
         * survives process death alongside login).
         */
        val query =
            MutableStateFlow(savedState.get<String>("query") ?: "").also { flow ->
                viewModelScope.launch {
                    flow.collect { savedState["query"] = it }
                }
            }

        /** Debounced autocomplete rows; empty query = no network (history only). */
        val suggestions: StateFlow<List<SuggestDto>> =
            query
                .asStateFlow()
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(SUGGEST_DEBOUNCE)
                .mapLatest { q ->
                    if (q.isEmpty()) {
                        emptyList()
                    } else {
                        api
                            .suggest(q)
                            .take(SUGGEST_LIMIT)
                    }
                }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Recent searches, newest first (Room cap 50). */
        val history: StateFlow<List<SearchHistoryEntity>> =
            db
                .searchHistoryDao()
                .recent(HISTORY_CAP)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Trending tags (site search's "Tags" tab source, live-verified endpoint).
         *  One fetch per VM; failure degrades to an absent section (no UI break). */
        val trendingTags: StateFlow<List<TrendingTagDto>> =
            flow {
                emit(api.trendingTags(count = TRENDING_TAGS_LIMIT))
            }.catch { emit(TrendingTagsDto()) }
                .map { it.tags }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun setQuery(text: String) {
            query.value = text
        }

        /** Record (async) + hand the query to the caller (opens the results feed). */
        fun submit(raw: String): String? {
            val q = raw.trim()
            if (q.isEmpty()) return null
            viewModelScope.launch {
                db.searchHistoryDao().add(SearchHistoryEntity(query = q, searchedAt = System.currentTimeMillis()))
                db.searchHistoryDao().trim(HISTORY_CAP)
            }
            query.value = ""
            return q
        }

        fun removeHistory(entry: SearchHistoryEntity) {
            viewModelScope.launch { db.searchHistoryDao().remove(entry.query) }
        }

        fun clearHistory() {
            viewModelScope.launch { db.searchHistoryDao().clear() }
        }

        // --- Full-scope search (AGENTS-APP spec'd 2026-10-02) ---

        /** Results per scope, keyed by trimmed query — 1 request on first open
         *  per (query, scope), cached in the VM, never refetched per
         *  recomposition (≤10-req/5s invariant). Maps cap at 12 entries. */
        private val _images = MutableStateFlow<Map<String, List<Gif>>>(emptyMap())
        val images: StateFlow<Map<String, List<Gif>>> = _images.asStateFlow()

        private val _creatorPreviews = MutableStateFlow<Map<String, List<Gif>>>(emptyMap())
        val creatorPreviews: StateFlow<Map<String, List<Gif>>> = _creatorPreviews.asStateFlow()

        private val _nichePreviews = MutableStateFlow<Map<String, List<Pair<NicheRef, Gif?>>>>(emptyMap())
        val nichePreviews: StateFlow<Map<String, List<Pair<NicheRef, Gif?>>>> = _nichePreviews.asStateFlow()

        /** Loads one scope's results for [rawQuery] (no-op while cached). Runs
         *  in the caller's coroutine — the screen's LaunchedEffect cancels it on
         *  every keystroke/tab change, so only the settled query's request
         *  completes (≤10-req/5s invariant). */
        suspend fun loadScope(
            scope: String,
            rawQuery: String,
        ) {
            val q = rawQuery.trim()
            if (q.isEmpty()) return
            run {
                // Session reload: the choke point's block sets before filtering.
                filter.refreshFrom(db.contentPrefsDao())
                filter.refreshBlockedFeeds(db.customFeedDao())
                when (scope) {
                    SCOPE_IMAGES -> {
                        if (q in _images.value) return
                        runCatching { api.search(searchText = q, type = "i", count = SCOPE_COUNT) }
                            .onSuccess { page ->
                                // Leak-zero: a blocked preview gif drops the row.
                                _images.value =
                                    cappedInsert(
                                        _images.value,
                                        q,
                                        page.toModels().filter { filter.allow(it.userName, it.tags, it.description) },
                                    )
                            }
                    }
                    SCOPE_CREATORS -> {
                        if (q in _creatorPreviews.value) return
                        runCatching { api.creatorSearchPreviews(query = q) }
                            .onSuccess { dto ->
                                _creatorPreviews.value =
                                    cappedInsert(
                                        _creatorPreviews.value,
                                        q,
                                        dto.gifs
                                            .map { it.toModel() }
                                            .distinctBy { it.userName }
                                            .filter { filter.allow(it.userName, it.tags, it.description) },
                                    )
                            }
                    }
                    SCOPE_NICHES -> {
                        if (q in _nichePreviews.value) return
                        runCatching { api.nicheSearchPreviews(query = q) }
                            .onSuccess { dto ->
                                _nichePreviews.value =
                                    cappedInsert(
                                        _nichePreviews.value,
                                        q,
                                        nicheRows(dto.previews) {
                                            filter.allow(it.userName, it.tags, it.description)
                                        },
                                    )
                            }
                    }
                }
            }
        }

        // --- Empty-state explorer sections (issue #11: search page = explorer; site parity
        // 2026-10-15: the site's empty search page fires these same pools). Load-on-first-
        // visibility per section; VM-cached, no Room writes for strip previews.

        /** Ranged Top strips (bare search pools top7/top28/top), keyed by range —
         *  12 tiles per fetch, one request on the chip's first visit, capped map
         *  like the scope maps; rows flow the ContentFilter (leak-zero). */
        private val _topRanged = MutableStateFlow<Map<String, List<Gif>>>(emptyMap())
        val topRanged: StateFlow<Map<String, List<Gif>>> = _topRanged.asStateFlow()

        /** Trending photos (bare image search, static poster tiles). One fetch per
         *  VM on first collection; failure degrades to an absent section. */
        val trendingPhotos: StateFlow<List<Gif>> =
            flow {
                filter.refreshFrom(db.contentPrefsDao())
                filter.refreshBlockedFeeds(db.customFeedDao())
                emit(
                    api.search(searchText = "", order = "trending", type = "i", count = TOP_STRIP_COUNT)
                        .toModels()
                        .filter { filter.allow(it.userName, it.tags, it.description) },
                )
            }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /** Trending niches row (v2/niches/trending/search). One fetch per VM. */
        val trendingNiches: StateFlow<List<NicheDto>> =
            flow {
                emit(api.nichesTrendingSearch(count = TOP_STRIP_COUNT).niches)
            }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /** Creators strip (moved from ExploreScreen, same paging machine: dedup by
         *  username, no-growth end detection, one request per approach). Rows of
         *  blocked creators drop (leak-zero, same stand-in filter as niche rows). */
        private val _exploreCreators = MutableStateFlow<List<CreatorSearchItemDto>>(emptyList())
        val exploreCreators: StateFlow<List<CreatorSearchItemDto>> = _exploreCreators.asStateFlow()

        private val _exploreEndReached = MutableStateFlow(false)
        val exploreEndReached: StateFlow<Boolean> = _exploreEndReached.asStateFlow()

        private val _exploreLoadFailed = MutableStateFlow(false)
        val exploreLoadFailed: StateFlow<Boolean> = _exploreLoadFailed.asStateFlow()

        private var exploreNextPage = 1
        private var exploreLoading = false

        /** Fetches the next creators page (guards make repeated calls no-ops); the
         *  strip's row-end observation re-fires as the user scrolls. */
        fun loadMoreCreators() {
            if (exploreLoading || exploreNextPage <= 0 || _exploreEndReached.value) return
            exploreLoading = true
            viewModelScope.launch {
                _exploreLoadFailed.value = false
                if (exploreNextPage == 1) {
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                }
                runCatching { api.verifiedCreators(page = exploreNextPage) }
                    .onSuccess { pageDto ->
                        val (rows, grew) =
                            mergeCreatorPage(_exploreCreators.value, pageDto.creators) {
                                filter.allow(it.username, emptyList(), null)
                            }
                        // Upstream pagination overlaps: page n re-lists page n-1 rows —
                        // dedup by username or the row keys collide (crashed live).
                        _exploreCreators.value = rows
                        exploreNextPage++
                        // Server repeats the tail when exhausted and carries no page
                        // count — no growth means the list is complete.
                        if (!grew) _exploreEndReached.value = true
                    }.onFailure { _exploreLoadFailed.value = true }
                exploreLoading = false
            }
        }

        /** Matched tag names per query (v1/tags/match, GIFs-tab chips); same
         *  12-entry cap as the scope maps. */
        private val _matchedTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())
        val matchedTags: StateFlow<Map<String, List<String>>> = _matchedTags.asStateFlow()

        /** One ranged-Top strip fill per range, on the chip's first visit
         *  (VM-cached, 12 tiles, 1 request). Runs in the caller's coroutine —
         *  the screen's LaunchedEffect cancels it on query changes, so only the
         *  settled empty state's request completes. */
        suspend fun loadTopRanged(range: String) {
            // Session reload: the choke point's block sets before filtering (same
            // prelude as [loadScope]).
            filter.refreshFrom(db.contentPrefsDao())
            filter.refreshBlockedFeeds(db.customFeedDao())
            if (range in _topRanged.value) return
            runCatching { api.search(searchText = "", order = range, count = TOP_STRIP_COUNT) }
                .onSuccess { page ->
                    _topRanged.value =
                        cappedInsert(
                            _topRanged.value,
                            range,
                            page.toModels().filter { filter.allow(it.userName, it.tags, it.description) },
                        )
                }
        }

        /** Matches tag names for the typed query while it is typed (GIFs tab);
         *  silent failure = no chips (same stance as the scope loads). */
        suspend fun loadMatchedTags(rawQuery: String) {
            val q = rawQuery.trim()
            if (q.isEmpty() || q in _matchedTags.value) return
            runCatching { api.tagsMatch(query = q) }
                .onSuccess { tags -> _matchedTags.value = cappedInsert(_matchedTags.value, q, tags) }
        }

        companion object {
            const val SUGGEST_DEBOUNCE = 300L
            const val SUGGEST_LIMIT = 8
            const val HISTORY_CAP = 50
            const val TRENDING_TAGS_LIMIT = 12
            const val SCOPE_COUNT = 30
            const val SCOPE_IMAGES = "images"
            const val SCOPE_CREATORS = "creators"
            const val SCOPE_NICHES = "niches"
            const val TOP_STRIP_COUNT = 12
        }
    }

/** Merges a fetched strip page into the creators list: dedup by username
 *  (upstream pagination overlaps rows), leak-zero ([allow] drops blocked
 *  creators); `false` = the page brought nothing new (end of the list). */
internal fun mergeCreatorPage(
    current: List<CreatorSearchItemDto>,
    page: List<CreatorSearchItemDto>,
    allow: (CreatorSearchItemDto) -> Boolean,
): Pair<List<CreatorSearchItemDto>, Boolean> {
    val merged = (current + page).distinctBy { it.username }
    return merged.filter(allow) to (merged.size != current.size)
}

/** VM map cap: results for the 12 most recent queries survive a tab revisit. */
internal fun <T> cappedInsert(
    map: Map<String, T>,
    key: String,
    value: T,
): Map<String, T> =
    (map + (key to value)).let {
        if (it.size > 12) it.entries.drop(it.size - 12).associate { e -> e.toPair() } else it
    }

/** Niche preview rows → (NicheRef, preview gif?) pairs. Leak-zero: rows whose
 *  preview gif is blocked drop entirely (AGENTS-APP full-scope search spec). */
internal fun nicheRows(
    previews: List<com.rjbiermann.giffyviewer.core.network.NichePreviewRowDto>,
    allow: (Gif) -> Boolean,
): List<Pair<NicheRef, Gif?>> =
    previews.mapNotNull { row ->
        val niche = row.niche?.takeIf { it.id.isNotBlank() } ?: return@mapNotNull null
        val gif = row.gif?.toModel()
        if (gif != null && !allow(gif)) return@mapNotNull null
        NicheRef(niche.id, niche.name ?: niche.id) to gif
    }
