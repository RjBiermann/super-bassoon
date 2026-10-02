package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.customRefSummary
import com.rjbiermann.giffyviewer.feature.feed.gifFeedRefs
import androidx.compose.material3.Surface as M3Surface

/** Player-only panel rows (mobile overflow parity): Like · Mute · Speed · Auto-swipe. */
data class TvPlayerActions(
    val liked: Boolean,
    val muted: Boolean,
    val speed: Float,
    /** `hasAudio`-aware: a silent gif shows no Mute row (§9 mute UI). */
    val hasAudio: Boolean,
    val autoSwipeOn: Boolean,
    val onToggleLike: () -> Unit,
    val onToggleMute: () -> Unit,
    val onCycleSpeed: () -> Unit,
    val onToggleAutoSwipe: () -> Unit,
)

/**
 * TV quick actions (mobile QuickBlockSheet parity — D-pad): favorite/pin/block
 * creator, favorite/block tags, add to custom feed. MENU on a focused card or
 * in the player opens this. Uses the shared FeedViewModel actions.
 */
@Composable
fun TvQuickActionsDialog(
    gif: Gif,
    feedViewModel: FeedViewModel,
    onDismiss: () -> Unit,
    /** Links audit #3: the D-pad link equivalent — navigate to the creator feed. */
    onOpenCreator: (String) -> Unit = {},
    /** The open feed itself as an addable ref (e.g. browsing a niche). */
    addableFeedRef: String? = null,
    /** Non-null in the player: adds Like/Mute/Speed/Auto-swipe rows. */
    playerActions: TvPlayerActions? = null,
) {
    var showAddToFeed by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var showAddToNiche by remember { mutableStateOf(false) }
    // Two views (user: "too many options"): main / tags submenu.
    var view by remember { mutableStateOf("main") }
    val customFeeds by feedViewModel.customFeeds.collectAsStateWithLifecycle(emptyList())
    val collections by feedViewModel.collections.collectAsStateWithLifecycle(emptyList())
    val joinedNiches by feedViewModel.joinedNiches.collectAsStateWithLifecycle(emptyList())
    Dialog(onDismissRequest = onDismiss) {
        M3Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row {
                    CreatorLabel(
                        gif.userName,
                        gif.verified,
                        style = MaterialTheme.typography.titleMedium,
                        tickSize = 16.dp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val first = remember { FocusRequester() }
                // Re-request per pane swap so a new pane's first row takes focus.
                LaunchedEffect(view) { first.requestFocus() }
                // Four panes (AGENTS-APP submenu restructure spec) — in-place
                // swap; ‹ Back is the last focusable row of each pane.
                when (view) {
                    "tags" -> {
                        gif.tags.take(3).forEach { tag ->
                            val tagState by feedViewModel
                                .tagState(tag)
                                .collectAsStateWithLifecycle(initialValue = null)
                            QuickAction(
                                text = if (tagState == "FAVORITED") "Unfavorite tag “$tag”" else "Favorite tag “$tag”",
                                onClick = {
                                    feedViewModel.toggleFavoriteTag(tag)
                                    onDismiss()
                                },
                            )
                            QuickAction(text = "Block tag “$tag”") {
                                feedViewModel.blockTag(tag)
                                onDismiss()
                            }
                        }
                        QuickAction(text = "‹ Back") { view = "main" }
                        return@M3Surface
                    }
                    "addto" -> {
                        QuickAction(text = "Add to custom feed…", first = first) { showAddToFeed = true }
                        QuickAction(text = "Add to a Collection…") {
                            feedViewModel.refreshCollections()
                            showAddToCollection = true
                        }
                        QuickAction(text = "Add to a Niche…") {
                            feedViewModel.refreshJoinedNiches()
                            showAddToNiche = true
                        }
                        QuickAction(text = "‹ Back") { view = "main" }
                        return@M3Surface
                    }
                    "block" -> {
                        QuickAction(text = "Block creator", first = first) {
                            feedViewModel.blockCreator(gif.userName)
                            onDismiss()
                        }
                        QuickAction(text = "Block keyword “${gif.tags.firstOrNull() ?: gif.userName}”") {
                            feedViewModel.blockKeyword(gif.tags.firstOrNull() ?: gif.userName)
                            onDismiss()
                        }
                        gif.tags.take(3).forEach { tag ->
                            QuickAction(text = "Block tag “$tag”") {
                                feedViewModel.blockTag(tag)
                                onDismiss()
                            }
                        }
                        QuickAction(text = "‹ Back") { view = "main" }
                        return@M3Surface
                    }
                    else -> {}
                }
                playerActions?.let { pa ->
                    HorizontalDivider()
                    QuickAction(
                        text = if (pa.liked) "Unlike" else "Like",
                        first = first,
                        onClick = {
                            pa.onToggleLike()
                            onDismiss()
                        },
                    )
                    if (pa.hasAudio) {
                        QuickAction(
                            text = if (pa.muted) "Unmute" else "Mute",
                            onClick = {
                                pa.onToggleMute()
                                onDismiss()
                            },
                        )
                    }
                    QuickAction(
                        // Locale.US: comma-decimal locales read "0,50×" — and the
                        // speed steps are 0.25, so 2 decimals is real precision.
                        text = "Speed " + String.format(java.util.Locale.US, "%.2f", pa.speed) + "× — tap to change",
                        onClick = {
                            pa.onCycleSpeed()
                            onDismiss()
                        },
                    )
                    QuickAction(
                        text = "Auto-swipe next: ${if (pa.autoSwipeOn) "ON" else "OFF"}",
                        onClick = {
                            pa.onToggleAutoSwipe()
                            onDismiss()
                        },
                    )
                }
                val favState by feedViewModel
                    .creatorState(gif.userName)
                    .collectAsStateWithLifecycle(initialValue = null)
                QuickAction(
                    first = if (playerActions == null) first else null,
                    text = if (favState == "FAVORITED") "Unfavorite @${gif.userName}" else "Favorite @${gif.userName}",
                    onClick = {
                        feedViewModel.toggleFavoriteCreator(gif.userName)
                        onDismiss()
                    },
                )
                val pinnedCreators by feedViewModel.pinnedCreators.collectAsStateWithLifecycle(emptySet())
                // Links audit #3: the most basic navigation act on a creator.
                // Caller decides navigation (home/source-feed open, player exits first —
                // swapping the feed source under the live pager is the soak-crash class).
                QuickAction(text = "Open @${gif.userName}'s feed") {
                    onDismiss()
                    onOpenCreator(gif.userName)
                }
                QuickAction(
                    text =
                        if (gif.userName.lowercase() in pinnedCreators) {
                            "Unpin @${gif.userName} from home"
                        } else {
                            "Pin @${gif.userName} to home"
                        },
                    onClick = {
                        feedViewModel.togglePinnedCreator(gif.userName)
                        onDismiss()
                    },
                )
                if (customFeeds.isNotEmpty()) {
                    HorizontalDivider()
                    QuickAction(text = "Add to…") { view = "addto" }
                }
                if (gif.tags.isNotEmpty()) {
                    QuickAction(text = "Tags…") { view = "tags" }
                }
                QuickAction(text = "Block…") { view = "block" }
                QuickAction(text = "Close") { onDismiss() }
            }
        }
    }
    if (showAddToCollection) {
        TvAddToCollectionDialog(
            collections = collections,
            onAdd = { folderId ->
                feedViewModel.addToCollection(folderId, gif.id)
                onDismiss()
            },
            onDismiss = { showAddToCollection = false },
        )
    }
    if (showAddToNiche) {
        TvAddToNicheDialog(
            gif = gif,
            niches = joinedNiches,
            onAdd = { nicheId ->
                feedViewModel.addToNiche(gif.id, nicheId)
                onDismiss()
            },
            onDismiss = { showAddToNiche = false },
        )
    }
    if (showAddToFeed) {
        TvAddToFeedDialog(
            gif = gif,
            customFeeds = customFeeds,
            addableFeedRef = addableFeedRef,
            onAdd = { defId, ref ->
                feedViewModel.addToCustomFeed(defId, ref)
                onDismiss()
            },
            onDismiss = { showAddToFeed = false },
        )
    }
}

@Composable
private fun QuickAction(
    text: String,
    first: FocusRequester? = null,
    onClick: () -> Unit,
) {
    // Focus ring: M3 Buttons draw nothing on D-pad focus — the shared
    // giffyFocus BrandRed border is the unified TV focus treatment.
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier =
            (first?.let { Modifier.focusRequester(it) } ?: Modifier)
                .fillMaxWidth()
                .giffyFocus(interaction),
    ) { Text(text) }
}

/** TV feed picker (mobile AddToCustomFeedDialog parity): radio feeds + checkbox refs. */
@Composable
private fun TvAddToFeedDialog(
    gif: Gif,
    customFeeds: List<com.rjbiermann.giffyviewer.core.database.CustomFeedEntity>,
    onAdd: (defId: Long, ref: String) -> Unit,
    onDismiss: () -> Unit,
    addableFeedRef: String? = null,
) {
    var feedId by remember { mutableStateOf(customFeeds.firstOrNull()?.id) }
    val refs = gifFeedRefs(gif, addableFeedRef)
    val selected = remember { mutableStateListOf(refs.first { it.startsWith("creator:") }) }
    Dialog(onDismissRequest = onDismiss) {
        M3Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Add to custom feed", style = MaterialTheme.typography.titleMedium)
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                customFeeds.forEach { def ->
                    val src = remember { MutableInteractionSource() }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .giffyFocus(src)
                                .clickable(interactionSource = src, indication = null) {
                                    feedId = def.id
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = feedId == def.id, onClick = { feedId = def.id })
                        Text(def.name)
                    }
                }
                HorizontalDivider()
                refs.forEach { ref ->
                    val label = customRefSummary(ref)
                    val src = remember { MutableInteractionSource() }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .giffyFocus(src)
                                .clickable(interactionSource = src, indication = null) {
                                    if (ref in selected) selected.remove(ref) else selected.add(ref)
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ref in selected, onCheckedChange = { checked ->
                            if (checked) selected.add(ref) else selected.remove(ref)
                        })
                        Text(label)
                    }
                }
                val addSrc = remember { MutableInteractionSource() }
                Button(
                    enabled = feedId != null && selected.isNotEmpty(),
                    onClick = { feedId?.let { id -> selected.forEach { ref -> onAdd(id, ref) } } },
                    interactionSource = addSrc,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .giffyFocus(addSrc),
                ) { Text("Add") }
                val cancelSrc = remember { MutableInteractionSource() }
                Button(
                    onClick = onDismiss,
                    interactionSource = cancelSrc,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .giffyFocus(cancelSrc),
                ) { Text("Cancel") }
            }
        }
    }
}

/** TV picker: saved collections (live-probed POST {gifId} → 204). */
@Composable
private fun TvAddToCollectionDialog(
    collections: List<com.rjbiermann.giffyviewer.core.network.CollectionDto>,
    onAdd: (folderId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        M3Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Add to a Collection", style = MaterialTheme.typography.titleMedium)
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                if (collections.isEmpty()) {
                    Text("No collections yet — create one in Collections.")
                } else {
                    collections.forEach { def ->
                        QuickAction(
                            text = "${def.folderName ?: "Untitled"} (${def.contentCount})",
                            first = if (def === collections.first()) first else null,
                            onClick = { onAdd(def.folderId) },
                        )
                    }
                }
                QuickAction(text = "Cancel", onClick = onDismiss)
            }
        }
    }
}

/** TV picker: joined niches (site popup parity — tag-matching first). */
@Composable
private fun TvAddToNicheDialog(
    gif: Gif,
    niches: List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>,
    onAdd: (nicheId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val gifTags = gif.tags.map { it.lowercase().trim() }.toSet()
    val matching = niches.filter { n -> n.tags.any { it.lowercase().trim() in gifTags } }
    val ordered = (matching + (niches - matching.toSet())).take(8)
    Dialog(onDismissRequest = onDismiss) {
        M3Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Add Content to a Niche", style = MaterialTheme.typography.titleMedium)
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                if (ordered.isEmpty()) {
                    Text("No joined niches — join niches to add content to them.")
                } else {
                    ordered.forEach { n ->
                        QuickAction(
                            text = n.name ?: n.id,
                            first = if (n === ordered.first()) first else null,
                            onClick = { onAdd(n.id) },
                        )
                    }
                }
                QuickAction(text = "Cancel", onClick = onDismiss)
            }
        }
    }
}
