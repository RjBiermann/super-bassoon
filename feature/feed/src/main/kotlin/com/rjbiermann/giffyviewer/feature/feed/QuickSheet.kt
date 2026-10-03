package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.feature.feed.orderNichesByTagMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** PLAN §9 quick sheet: block creator / tags / keyword / don't block. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
internal fun QuickBlockSheet(
    gif: Gif,
    onDismiss: () -> Unit,
    viewModel: FeedViewModel,
    showSpeed: Boolean = false,
    currentSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    /** Surfaced when the OPEN feed itself is addable (e.g. a niche). */
    addableFeedRef: String? = null,
    /** Links audit #3: "Open @user's feed" — player entry must exit the player first
     *  (swapping the source under the live pager is the soak-crash class); grid entry
     *  navigates in place. */
    onOpenFeed: () -> Unit = {},
) {
    // Hoisted for the AddToCustomFeedDialog scope below.
    val customFeeds by viewModel.customFeeds.collectAsState(initial = emptyList())
    val collections by viewModel.collections.collectAsState(initial = emptyList())
    val joinedNiches by viewModel.joinedNiches.collectAsState(initial = emptyList())
    var showAddToFeed by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var showAddToNiche by remember { mutableStateOf(false) }
    // §8 infinite shuffle player: show the active seed (restore = the same seed
    // re-derives the same global order from the pool).
    // remember: Flow operators must not re-run per recomposition (lint).
    val shuffleSeedFlow =
        remember(viewModel.source) {
            viewModel.source
                .flatMapLatest { viewModel.feedPrefs(it.baseKey) }
                .map { it.shuffleSeed }
        }
    val shuffleSeed by shuffleSeedFlow.collectAsState(initial = 0L)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            CreatorLabel(
                gif.userName,
                gif.verified,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (shuffleSeed != 0L) {
                Text(
                    text = "Shuffle seed $shuffleSeed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (showSpeed) {
                // Continuous slider (0.25×–2×): the user asked for fine control
                // beyond preset chips; applies on release to avoid player thrash.
                var dragSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Speed " + String.format(java.util.Locale.US, "%.2f", dragSpeed) + "×",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (dragSpeed != currentSpeed) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("0.25×", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = dragSpeed,
                            onValueChange = { dragSpeed = it },
                            valueRange = 0.25f..2f,
                            onValueChangeFinished = { onSpeedChange(dragSpeed) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("2×", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Four panes (AGENTS-APP submenu restructure spec): main = everyday
            // toggles + pane entries; Add to… / Tags… / Block… are in-place
            // content swaps — no navigation, depth cap 2.
            var view by remember { mutableStateOf("main") }
            when (view) {
                "main" -> {
                    val favState by viewModel
                        .creatorState(gif.userName)
                        .collectAsState(initial = null)
                    listStyle(if (favState == "FAVORITED") "Unfavorite @${gif.userName}" else "Favorite @${gif.userName}") {
                        viewModel.toggleFavoriteCreator(gif.userName)
                        onDismiss()
                    }
                    // Links audit #3: the most basic navigation act on a creator.
                    listStyle("Open @${gif.userName}'s feed") {
                        viewModel.open(FeedSource.Creator(username = gif.userName))
                        onDismiss()
                        onOpenFeed()
                    }
                    val pinnedCreators by viewModel.pinnedCreators.collectAsState(initial = emptySet())
                    listStyle(
                        if (gif.userName.lowercase() in pinnedCreators) {
                            "Unpin @${gif.userName} from home"
                        } else {
                            "Pin @${gif.userName} to home"
                        },
                    ) {
                        viewModel.togglePinnedCreator(gif.userName)
                        onDismiss()
                    }
                    if (customFeeds.isNotEmpty()) {
                        listStyle("Add to…") { view = "addto" }
                    }
                    if (gif.tags.isNotEmpty()) {
                        listStyle("Tags…") { view = "tags" }
                    }
                    listStyle("Block…") { view = "block" }
                    listStyle("Close", onDismiss)
                }
                "addto" -> {
                    // Picker dialogs stay the second level (same depth as today).
                    listStyle("Add to custom feed…") { showAddToFeed = true }
                    listStyle("Add to a Collection…") {
                        viewModel.refreshCollections()
                        showAddToCollection = true
                    }
                    listStyle("Add to a Niche…") {
                        viewModel.refreshJoinedNiches()
                        showAddToNiche = true
                    }
                    listStyle("‹ Back", { view = "main" })
                }
                "tags" -> {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    gif.tags.take(3).forEach { tag ->
                        val tagState by viewModel
                            .tagState(tag)
                            .collectAsState(initial = null)
                        listStyle(
                            if (tagState == "FAVORITED") "Unfavorite tag “$tag”" else "Favorite tag “$tag”",
                        ) {
                            viewModel.toggleFavoriteTag(tag)
                            onDismiss()
                        }
                        listStyle("Block tag “$tag”") {
                            viewModel.blockTag(tag)
                            onDismiss()
                        }
                    }
                    listStyle("‹ Back", { view = "main" })
                }
                else -> {
                    // Every hide action in one pane — grouping is separation,
                    // not a guard (instant as today, no confirm).
                    listStyle("Block creator") {
                        viewModel.blockCreator(gif.userName)
                        onDismiss()
                    }
                    listStyle("Block keyword “${gif.tags.firstOrNull() ?: gif.userName}”") {
                        viewModel.blockKeyword(gif.tags.firstOrNull() ?: gif.userName)
                        onDismiss()
                    }
                    gif.tags.take(3).forEach { tag ->
                        listStyle("Block tag “$tag”") {
                            viewModel.blockTag(tag)
                            onDismiss()
                        }
                    }
                    listStyle("‹ Back", { view = "main" })
                }
            }
        }
        if (showAddToCollection) {
            AddToCollectionDialog(
                collections = collections,
                onAdd = { folderId ->
                    viewModel.addToCollection(folderId, gif.id)
                    onDismiss()
                },
                onDismiss = { showAddToCollection = false },
            )
        }
        if (showAddToNiche) {
            AddToNicheDialog(
                gif = gif,
                niches = joinedNiches,
                onAdd = { nicheId ->
                    viewModel.addToNiche(gif.id, nicheId)
                    onDismiss()
                },
                onDismiss = { showAddToNiche = false },
            )
        }
        if (showAddToFeed) {
            AddToCustomFeedDialog(
                gif = gif,
                customFeeds = customFeeds,
                addableFeedRef = addableFeedRef,
                onAdd = { defId, ref ->
                    viewModel.addToCustomFeed(defId, ref)
                    onDismiss()
                },
                onDismiss = { showAddToFeed = false },
            )
        }
    }
}

/** Pick which custom feed + which refs (creator / tags) to add. */
@Composable
private fun AddToCustomFeedDialog(
    gif: Gif,
    customFeeds: List<com.rjbiermann.giffyviewer.core.database.CustomFeedEntity>,
    onAdd: (defId: Long, ref: String) -> Unit,
    onDismiss: () -> Unit,
    addableFeedRef: String? = null,
) {
    var feedId by remember { mutableStateOf(customFeeds.firstOrNull()?.id) }
    val refs = gifFeedRefs(gif, addableFeedRef)
    val selected = remember { mutableStateListOf(refs.first { it.startsWith("creator:") }) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to custom feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                customFeeds.forEach { def ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { feedId = def.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = feedId == def.id,
                            onClick = { feedId = def.id },
                        )
                        Text(def.name)
                    }
                }
                HorizontalDivider()
                refs.forEach { ref ->
                    val label = customRefSummary(ref)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                if (ref in selected) selected.remove(ref) else selected.add(ref)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = ref in selected, onCheckedChange = {
                            if (it) selected.add(ref) else selected.remove(ref)
                        })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = feedId != null && selected.isNotEmpty(),
                onClick = {
                    feedId?.let { id -> selected.forEach { ref -> onAdd(id, ref) } }
                },
            ) { Text("Add") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColumnScope.listStyle(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

/** Pick a saved collection for the gif (live-probed write: POST {gifId} → 204). */
@Composable
internal fun AddToCollectionDialog(
    collections: List<com.rjbiermann.giffyviewer.core.network.CollectionDto>,
    onAdd: (folderId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to a Collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (collections.isEmpty()) {
                    Text("No collections yet — create one in Collections.")
                } else {
                    collections.forEach { def ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(def.folderId) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = false,
                                onClick = { onAdd(def.folderId) },
                            )
                            Text("${def.folderName ?: "Untitled"} (${def.contentCount})")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick a joined niche (site popup parity: joined niches matching the gif's tags first). */
@Composable
internal fun AddToNicheDialog(
    gif: Gif,
    niches: List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>,
    onAdd: (nicheId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = orderNichesByTagMatch(gif.tags, niches)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Content to a Niche") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ordered.isEmpty()) {
                    Text("No joined niches — join niches to add content to them.")
                } else {
                    ordered.take(8).forEach { n ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(n.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = false,
                                onClick = { onAdd(n.id) },
                            )
                            Text(n.name ?: n.id)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
