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
import kotlinx.coroutines.Job
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

        /** Loads one scope's results for [rawQuery] (no-op while cached). The
         *  fetch runs in a VM-OWNED job: the screen's LaunchedEffect(scope,
         *  query) cancels its caller on every keystroke/tab change
         *  (DESIGN-search hyp 3) — issue #57: a caller killed mid-fetch had
         *  the CancellationException swallowed by runCatching, leaving the
         *  scope map empty forever while the tab sat parked (no re-key, no
         *  retry, no failure state — Images blank on every visit). In
         *  viewModelScope only a newer same-scope load or VM death cancels
         *  it; the per-keystroke request count is unchanged (each new load
         *  still cancels the prior in-flight one, ≤15-req/5s invariant). */
        private val scopeJobs = mutableMapOf<String, Job>()

        suspend fun loadScope(
            scope: String,
            rawQuery: String,
        ) {
            val q = rawQuery.trim()
            if (q.isEmpty()) return
            when (scope) {
                SCOPE_IMAGES -> if (q in _images.value) return
                SCOPE_CREATORS -> if (q in _creatorPreviews.value) return
                SCOPE_NICHES -> if (q in _nichePreviews.value) return
                SCOPE_GIFS -> if (q in _matchedTags.value) return
            }
            scopeJobs.remove(scope)?.cancel()
            scopeJobs[scope] =
                viewModelScope.launch {
                    // Session reload: the choke point's block sets before filtering.
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                    when (scope) {
                        SCOPE_IMAGES ->
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
                        SCOPE_CREATORS ->
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
                        SCOPE_NICHES ->
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
                        SCOPE_GIFS ->
                            // Issue #11 slice C: matched tags for a typed query —
                            // the app stays on the tag-feed submit path (batch 6
                            // rule; no tag-results tab).
                            runCatching { api.tagsMatch(query = q) }
                                .onSuccess { tags ->
                                    _matchedTags.value =
                                        cappedInsert(_matchedTags.value, q, tags.take(MATCHED_TAGS_LIMIT))
                                }
                    }
                }
        }

        // --- Explore empty state (issue #11: the search page IS the explorer) ---

        /** Bare ranged-search pools (verified 2026-10-15: top7/top28/top on a
         *  bare gifs/search, distinct pools per range). Key = range, VM cache
         *  like the scope maps; rows flow the ContentFilter (leak-zero). */
        private val _topRanged = MutableStateFlow<Map<String, List<Gif>>>(emptyMap())
        val topRanged: StateFlow<Map<String, List<Gif>>> = _topRanged.asStateFlow()

        /** Matched tag names per trimmed query (slice C), same cap. */
        private val _matchedTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())
        val matchedTags: StateFlow<Map<String, List<String>>> = _matchedTags.asStateFlow()

        /** Loads one range's bare-search strip (no-op while cached). VM-owned
         *  fetch like loadScope (issue #57: the caller's LaunchedEffect
         *  cancellation used to swallow the request mid-flight). */
        suspend fun loadTopRanged(range: String) {
            if (range in _topRanged.value) return
            scopeJobs.remove("topRanged")?.cancel()
            scopeJobs["topRanged"] =
                viewModelScope.launch {
                    // Session reload: the choke point's block sets before filtering.
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                    runCatching { api.search(searchText = "", order = range, count = EXPLORE_COUNT) }
                        .onSuccess { page ->
                            _topRanged.value =
                                cappedInsert(
                                    _topRanged.value,
                                    range,
                                    page.toModels().filter { filter.allow(it.userName, it.tags, it.description) },
                                )
                        }
                }
        }

        /** Trending photos (site-explore source, stills). One request when
         *  the section first becomes visible (screen's LaunchedEffect);
         *  failure degrades to an absent section (silent-stance rule). */
        private val _trendingPhotos = MutableStateFlow<List<Gif>>(emptyList())
        val trendingPhotos: StateFlow<List<Gif>> = _trendingPhotos.asStateFlow()

        suspend fun loadTrendingPhotos() {
            if (_trendingPhotos.value.isNotEmpty()) return
            scopeJobs.remove("trendingPhotos")?.cancel()
            scopeJobs["trendingPhotos"] =
                viewModelScope.launch {
                    // Session reload: the choke point's block sets before filtering.
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                    runCatching {
                        api.search(searchText = "", order = "trending", type = "i", count = EXPLORE_COUNT)
                    }.onSuccess { page ->
                        _trendingPhotos.value =
                            page.toModels().filter { filter.allow(it.userName, it.tags, it.description) }
                    }
                }
        }

        /** Trending niches (v2/niches/trending/search — parses NichesPageDto).
         *  NOT content rows: a niche strip has no gif to filter — its FEED
         *  filters at read time, so leak-zero holds at the destination. */
        private val _trendingNiches = MutableStateFlow<List<NicheDto>>(emptyList())
        val trendingNiches: StateFlow<List<NicheDto>> = _trendingNiches.asStateFlow()

        suspend fun loadTrendingNiches() {
            if (_trendingNiches.value.isNotEmpty()) return
            runCatching { api.nichesTrendingSearch(count = EXPLORE_COUNT) }
                .onSuccess { _trendingNiches.value = it.niches }
        }

        /** Verified-creators strip: the old ExploreScreen's paging machine
         *  moved here (same distinct-by-username dedup — upstream pagination
         *  overlaps page n re-listing page n-1; no growth = exhausted). */
        private val _exploreCreators = MutableStateFlow<List<CreatorSearchItemDto>>(emptyList())
        val exploreCreators: StateFlow<List<CreatorSearchItemDto>> = _exploreCreators.asStateFlow()

        private val _creatorsEnd = MutableStateFlow(false)
        val creatorsEnd: StateFlow<Boolean> = _creatorsEnd.asStateFlow()

        private val _creatorsFailed = MutableStateFlow(false)
        val creatorsFailed: StateFlow<Boolean> = _creatorsFailed.asStateFlow()

        private var creatorsNextPage = 1
        private var creatorsLoading = false

        /** Fetches the next creator page; the strip's end sentinel re-fires
         *  while the user keeps the row's end visible (blocked creators drop
         *  from the rows — leak-zero on the strip too). Failure = manual
         *  Retry only (no automatic refire loop; same stance as the scopes). */
        fun loadExploreCreators() {
            if (creatorsLoading || creatorsNextPage <= 0 || _creatorsEnd.value) return
            creatorsLoading = true
            viewModelScope.launch {
                _creatorsFailed.value = false
                runCatching {
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                    api.verifiedCreators(page = creatorsNextPage)
                }.onSuccess { pageDto ->
                    val (rows, exhausted) =
                        exploreCreatorRows(_exploreCreators.value, pageDto.creators) {
                            filter.allow(it.username, emptyList())
                        }
                    _exploreCreators.value = rows
                    creatorsNextPage++
                    // Server repeats the tail when exhausted and carries no
                    // page count — no growth means the list is complete.
                    if (exhausted) _creatorsEnd.value = true
                }.onFailure { _creatorsFailed.value = true }
                creatorsLoading = false
            }
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
            const val SCOPE_GIFS = "gifs"

            /** Explore-section strip size (issue #11): one request per
             *  section/strip, all VM-cached — 6 calls worst case on a fresh
             *  open, inside the ≤15-req/5s budget (design §6). */
            const val EXPLORE_COUNT = 12
            const val MATCHED_TAGS_LIMIT = 12
        }
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

/** Explore creator-strip page merge (issue #11): dedup by username first
 *  (upstream pagination overlaps — page n re-lists page n-1; the old
 *  ExploreViewModel machine), then leak-zero drop; "no growth" = the list
 *  is exhausted (server repeats the tail, carries no page count). */
internal fun exploreCreatorRows(
    existing: List<CreatorSearchItemDto>,
    next: List<CreatorSearchItemDto>,
    allow: (CreatorSearchItemDto) -> Boolean,
): Pair<List<CreatorSearchItemDto>, Boolean> {
    val rows = (existing + next).distinctBy { it.username }.filter(allow)
    return rows to (rows.size == existing.size)
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
