package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NichePreviewRowDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import com.rjbiermann.giffyviewer.core.ui.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full sort menu for the niches LISTING (`v2/niches/search/previews`): all
 *  five orders live-verified 2026-10-17 (DESIGN-47SORTS §1) — menu order +
 *  labels mirror the site's own /niches sort UI. Mobile + TV share it. */
val nicheListingSorts: List<Pair<String, String>> =
    listOf(
        "subscribers" to "Subscribers",
        "posts" to "Posts",
        "alphabetical_asc" to "Name: A-Z",
        "alphabetical_desc" to "Name: Z-A",
        "random" to "Randomize",
    )

/**
 * One preview-tile row (issue #47 slice 3, DESIGN-47s3): niche identity +
 * the counts the site tiles omit (app-superior) + the tile image (preview-gif
 * poster, niche thumbnail/cover fallbacks).
 */
data class NicheTile(
    val id: String,
    val name: String,
    val gifs: Long,
    val subscribers: Long,
    val imageUrl: String?,
)

/**
 * Niches browser state (mobile + TV — one paging path instead of the
 * TvNichesViewModel copy; ponytail audit 2026-10). Issue #47 slice 3: the
 * listing renders preview tiles from `v2/niches/search/previews` — the site's
 * own /niches source (live-verified 2026-10-17, DESIGN-47s3 §1) — with the
 * site-parity category filter + the previews-verified sorts. Paginated via the
 * verified top-level {page, pages}; the pending/error/endReached/auto-retry
 * machine (issue #49) is unchanged.
 * Leak-zero: rows whose preview gif is blocked drop (the search niche-scope
 * rule); the filter's block sets are reloaded per page like the network feed
 * sources (issue #42 closure shape).
 */
@HiltViewModel
class NichesViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        private val db: GiffyDatabase,
        private val filter: ContentFilter,
        val settings: SettingsRepository,
        val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _niches = MutableStateFlow<List<NicheTile>>(emptyList())
        val niches: StateFlow<List<NicheTile>> = _niches

        private val _endReached = MutableStateFlow(false)
        val endReached: StateFlow<Boolean> = _endReached

        private val _loadFailed = MutableStateFlow(false)
        val loadFailed: StateFlow<Boolean> = _loadFailed

        private val _categories = MutableStateFlow<List<String>>(emptyList())
        val categories: StateFlow<List<String>> = _categories

        private val _category = MutableStateFlow<String?>(null)
        val category: StateFlow<String?> = _category

        // Values restricted to [nicheListingSorts] (all probe-verified).
        private val _sort = MutableStateFlow("subscribers")
        val sort: StateFlow<String> = _sort

        private var nextPage = 1

        /** A page is in flight; hosts show a pending indicator while the list
         *  is empty instead of the silent-blind state. */
        private val _loading = MutableStateFlow(false)
        val loading: StateFlow<Boolean> = _loading

        /** Live connectivity (the auto-retry re-fires when it returns). */
        val isOnline: StateFlow<Boolean>
            get() = networkMonitor.isOnline

        init {
            loadMore()
            viewModelScope.launch {
                runCatching { api.nicheCategories() }.onSuccess { _categories.value = it.categories }
            }
        }

        /** Fetches the next preview page; safe to call repeatedly (tap/D-pad walk). */
        fun loadMore() {
            if (_loading.value || nextPage <= 0) return
            _loading.value = true
            viewModelScope.launch {
                runCatching {
                    // Session reload of the choke point's block sets before the
                    // rows map (the FeedMediator's one-load refresh order).
                    filter.refreshFrom(db.contentPrefsDao())
                    filter.refreshBlockedFeeds(db.customFeedDao())
                    api.nichePreviewListing(order = _sort.value, category = _category.value, page = nextPage)
                }.onSuccess { dto ->
                    _loadFailed.value = false
                    _niches.value =
                        nicheListingRows(_niches.value, dto.previews) { gif ->
                            filter.allow(gif.userName, gif.tags, gif.description)
                        }
                    nextPage = if (dto.page < dto.pages) dto.page + 1 else 0
                    _endReached.value = nextPage == 0
                }.onFailure { _loadFailed.value = true }
                _loading.value = false
            }
        }

        fun selectCategory(category: String?) {
            if (_category.value == category) return
            _category.value = category
            restart()
        }

        fun resort(sort: String) {
            if (_sort.value == sort) return
            _sort.value = sort
            restart()
        }

        private fun restart() {
            nextPage = 1
            _niches.value = emptyList()
            _endReached.value = false
            _loadFailed.value = false
            loadMore()
        }

        fun isPinned(
            niche: NicheTile,
            pinned: Set<String>,
        ) = pinned.any { it.startsWith("${niche.id}|") }

        fun togglePin(niche: NicheTile) {
            viewModelScope.launch { settings.togglePinnedNiche(niche.id, niche.name) }
        }
    }

/**
 * Preview rows → merged [NicheTile] pages (pure; TDD in [NicheListingTilesTest]).
 * Leak-zero: a row whose preview gif is blocked drops entirely (the search
 * niche-scope rule — [allow] receives the preview gif as a model Gif).
 * Blank niche ids drop. Cross-page id overlap dedupes keeping the FIRST
 * occurrence (upstream pagination re-lists — the verifiedCreators lesson).
 */
internal fun nicheListingRows(
    existing: List<NicheTile>,
    previews: List<NichePreviewRowDto>,
    allow: (Gif) -> Boolean,
): List<NicheTile> {
    val fromRows =
        previews.mapNotNull { row ->
            val niche = row.niche?.takeIf { it.id.isNotBlank() } ?: return@mapNotNull null
            val gif = row.gif?.toModel()
            if (gif != null && !allow(gif)) return@mapNotNull null
            NicheTile(
                id = niche.id,
                name = niche.name ?: niche.id,
                gifs = niche.gifs,
                subscribers = niche.subscribers,
                imageUrl = row.gif?.urls?.poster ?: niche.thumbnail ?: niche.cover,
            )
        }
    return (existing + fromRows).distinctBy { it.id }
}
