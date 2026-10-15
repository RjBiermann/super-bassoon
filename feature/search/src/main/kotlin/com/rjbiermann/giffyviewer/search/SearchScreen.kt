package com.rjbiermann.giffyviewer.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import com.rjbiermann.giffyviewer.core.ui.avgColorOr
import com.rjbiermann.giffyviewer.feature.feed.FeedSource

/** Full-scope tab indices (GIFs · Images · Creators · Niches). */
private const val SCOPE_GIFS = 0

/**
 * Search screen (PLAN §7): query field (borrowed 16dp-radius style) → recent
 * searches (tap re-runs, per-row remove + clear-all) → typed autocomplete rows
 * (suggestion text + gif count). Submit opens the results feed.
 *
 * Full-scope search (AGENTS-APP spec'd 2026-10-02): when a query is typed, a
 * GIFs · Images · Creators · Niches tab row appears — GIFs keeps the existing
 * submit path, the other scopes render inline (1 request on first open,
 * VM-cached; rows flow the ContentFilter).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    viewModel: SearchViewModel,
    onOpenCreator: (String) -> Unit = {},
    onOpenNiche: (id: String, name: String) -> Unit = { _, _ -> },
    /** Empty-state strips open their full feed (issue #11 "Browse all"). */
    onOpenFeed: (FeedSource) -> Unit = {},
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

    fun submit(raw: String) {
        keyboard?.hide()
        viewModel.setQuery("")
        viewModel.submit(raw)?.let(onSubmit)
    }

    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "clear query")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(onSearch = {
                        submit(query)
                    }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            )
        }

        // Hoisted reads (composable context): history-clear section + trending tags.
        val trendingTags = viewModel.trendingTags.collectAsStateWithLifecycle().value
        val images by viewModel.images.collectAsStateWithLifecycle()
        val creatorPreviews by viewModel.creatorPreviews.collectAsStateWithLifecycle()
        val nichePreviews by viewModel.nichePreviews.collectAsStateWithLifecycle()
        // Explore empty state (issue #11): scope-map VM caches, seeded by the
        // sections' own load-on-compose effects — body collects never fetch.
        val topRanged by viewModel.topRanged.collectAsStateWithLifecycle()
        val trendingPhotos by viewModel.trendingPhotos.collectAsStateWithLifecycle()
        val trendingNiches by viewModel.trendingNiches.collectAsStateWithLifecycle()
        val exploreCreators by viewModel.exploreCreators.collectAsStateWithLifecycle()
        val creatorsEnd by viewModel.creatorsEnd.collectAsStateWithLifecycle()
        val creatorsFailed by viewModel.creatorsFailed.collectAsStateWithLifecycle()
        val matchedTags by viewModel.matchedTags.collectAsStateWithLifecycle()

        // Selected top range (issue #11 slice A): Week first — verified pool
        // order mirrors the site (top7 · top28 · top).
        var topRange by remember { mutableStateOf(FeedSource.TopRanged.RANGES.first()) }

        // Full-scope tabs (spec'd 2026-10-02): visible while a query is typed.
        // GIFs = the existing submit path; other scopes render inline.
        var scope by remember { mutableIntStateOf(SCOPE_GIFS) }
        LaunchedEffect(scope, query) {
            if (query.isNotBlank()) {
                when (scope) {
                    0 -> viewModel.loadScope(SearchViewModel.SCOPE_GIFS, query)
                    1 -> viewModel.loadScope(SearchViewModel.SCOPE_IMAGES, query)
                    2 -> viewModel.loadScope(SearchViewModel.SCOPE_CREATORS, query)
                    3 -> viewModel.loadScope(SearchViewModel.SCOPE_NICHES, query)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Empty field → history only (no suggestions, no extra network — §7).
            if (query.isBlank()) {
                item {
                    TextButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Clear search history") }
                }
            }
            items(history, key = { it.id }) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                submit(entry.query)
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { viewModel.removeHistory(entry) }) {
                        Icon(Icons.Filled.Close, contentDescription = "remove ${entry.query}")
                    }
                }
            }
            items(suggestions, key = { "s:" + it.text }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                submit(suggestion.text)
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        text = "%,d gifs".format(suggestion.gifs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Explore empty state (issue #11, site-parity top-down order:
            // Top · photos · niches · creators · trending tags). Each section
            // is VM-cached; loads fire when the section first composes — not
            // all at once.
            if (query.isBlank()) {
                item(key = "top-header") {
                    LaunchedEffect(topRange, query) { viewModel.loadTopRanged(topRange) }
                    SectionHeader("Top gifs") {
                        onOpenFeed(FeedSource.TopRanged(topRange))
                    }
                }
                item(key = "top-chips") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        // Chip labels = the site's own range wording; keys are
                        // the search endpoint's BadOrder-verified orders.
                        listOf("top7" to "Week", "top28" to "Month", "top" to "All time").forEach { (range, label) ->
                            FilterChip(
                                selected = topRange == range,
                                onClick = { topRange = range },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                items(topRanged[topRange].orEmpty().chunked(3), key = { "top:$topRange:" + it.first().id }) { rowGifs ->
                    PosterRow(rowGifs)
                }
                item(key = "photos-header") {
                    LaunchedEffect(query) { viewModel.loadTrendingPhotos() }
                    SectionHeader("Trending photos")
                }
                items(trendingPhotos.chunked(3), key = { "ph:" + it.first().id }) { rowGifs ->
                    PosterRow(rowGifs)
                }
                item(key = "niches-header") {
                    LaunchedEffect(query) { viewModel.loadTrendingNiches() }
                    SectionHeader("Trending niches")
                }
                item(key = "niches-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(trendingNiches, key = { it.id }) { niche ->
                            FilterChip(
                                selected = false,
                                onClick = { onOpenNiche(niche.id, niche.name) },
                                label = { Text(niche.name) },
                            )
                        }
                    }
                }
                item(key = "creators-header") {
                    SectionHeader("Creators")
                }
                item(key = "creators-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(exploreCreators, key = { it.username }) { creator ->
                            CreatorCard(creator) { onOpenCreator(creator.username) }
                        }
                        // End sentinel: composes only as the row's end enters
                        // the viewport — one next page per walk to the end
                        // (the old ExploreScreen auto-fetch, now lazy).
                        item(key = "creators-next") {
                            if (creatorsFailed) {
                                TextButton(onClick = viewModel::loadExploreCreators) { Text("Retry") }
                            } else {
                                LaunchedEffect(exploreCreators.size) { viewModel.loadExploreCreators() }
                            }
                        }
                        if (creatorsEnd && exploreCreators.isNotEmpty()) {
                            item(key = "creators-end") {
                                Text(
                                    "That's all ${exploreCreators.size} creators",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            // Trending tags rows (hoisted read above; empty query only).
            if (query.isBlank() && trendingTags.isNotEmpty()) {
                item(key = "tags-header") {
                    Text(
                        "Trending tags",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(trendingTags, key = { "t:" + it.name }) { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    submit(tag.name)
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(
                            text = "%,d gifs".format(tag.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Full-scope tab row (site results-page parity: GIFs · Images ·
            // Creators · Niches) + the active scope's inline section.
            if (query.isNotBlank()) {
                item(key = "scope-tabs") {
                    TabRow(selectedTabIndex = scope) {
                        listOf("GIFs", "Images", "Creators", "Niches").forEachIndexed { i, label ->
                            Tab(selected = scope == i, onClick = { scope = i }, text = { Text(label) })
                        }
                    }
                }
                when (scope) {
                    0 -> {
                        // Issue #11 slice C: matched tag chips feed the existing
                        // tags → tag-feed submit path (no tag-results tab).
                        val tags = matchedTags[query.trim()].orEmpty()
                        if (tags.isNotEmpty()) {
                            item(key = "matched-tags") {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "Tags matching",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(tags, key = { "#" + it }) { tag ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { submit(tag) },
                                                label = { Text("#$tag") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val imgs = images[query.trim()].orEmpty()
                        if (imgs.isEmpty()) {
                            item(key = "images-hint") {
                                SectionHint(if (images.containsKey(query.trim())) "No images found" else "")
                            }
                        } else {
                            items(imgs.chunked(3), key = { "img:" + it.first().id }) { PosterRow(it) }
                        }
                    }
                    2 -> {
                        val rows = creatorPreviews[query.trim()].orEmpty()
                        if (rows.isEmpty()) {
                            item(key = "creators-hint") {
                                SectionHint(if (creatorPreviews.containsKey(query.trim())) "No creators found" else "")
                            }
                        } else {
                            items(rows, key = { "cr:" + it.id }) { gif ->
                                PreviewRow(gif) {
                                    onOpenCreator(gif.userName)
                                }
                            }
                        }
                    }
                    3 -> {
                        val rows = nichePreviews[query.trim()].orEmpty()
                        if (rows.isEmpty()) {
                            item(key = "niches-hint") {
                                SectionHint(if (nichePreviews.containsKey(query.trim())) "No niches found" else "")
                            }
                        } else {
                            items(rows, key = { "ni:" + it.first.id }) { (niche, gif) ->
                                PreviewRow(gif, label = niche.name) {
                                    onOpenNiche(niche.id, niche.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tab placeholders before their scope's request returns. */
@Composable
private fun SectionHint(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** Explore-section header (issue #11): title + optional "Browse all →"
 *  action opening the section's full feed. */
@Composable
private fun SectionHeader(
    title: String,
    onOpenAll: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (onOpenAll != null) {
            TextButton(onClick = onOpenAll) { Text("Browse all →") }
        }
    }
}

/** Static 3-up poster strip row (stills — no player path, no tap target;
 *  the same component the Images tab renders rows from). */
@Composable
private fun PosterRow(rowGifs: List<Gif>) {
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        rowGifs.forEach { gif ->
            PreviewThumb(gif = gif, modifier = Modifier.weight(1f).padding(2.dp))
        }
        repeat(3 - rowGifs.size) { Spacer(Modifier.weight(1f).padding(2.dp)) }
    }
}

/** Verified-creator card (explore strip): avatar + @name + counts,
 *  tap → creator feed. */
@Composable
private fun CreatorCard(
    creator: CreatorSearchItemDto,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(120.dp)
                .clickable(onClick = onClick)
                .padding(4.dp),
    ) {
        AsyncImage(
            model = creator.profileImageUrl,
            contentDescription = "@${creator.username}",
            modifier = Modifier.size(56.dp).clip(CircleShape),
        )
        Text(
            "@${creator.username}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "${creator.followers} followers · ${creator.gifs} gifs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Static poster cell (Images scope: stills — no player path, no tap target). */
@Composable
private fun PreviewThumb(
    gif: Gif?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(
                    if (gif != null && gif.width > 0 && gif.height > 0) {
                        gif.width.toFloat() / gif.height
                    } else {
                        0.8f
                    },
                ).clip(RoundedCornerShape(8.dp))
                .background(
                    gif?.let { avgColorOr(it.avgColor, MaterialTheme.colorScheme.surfaceVariant) }
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                ),
    ) {
        if (gif != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(gif.posterUrl ?: gif.sdUrl)
                        .crossfade(200)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Preview row (Creators/Niches scopes): thumbnail + label, whole row tappable. */
@Composable
private fun PreviewRow(
    gif: Gif?,
    label: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreviewThumb(
            gif = gif,
            modifier = Modifier.size(width = 64.dp, height = 44.dp),
        )
        Text(
            text = label ?: "@${gif?.userName.orEmpty()}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}
