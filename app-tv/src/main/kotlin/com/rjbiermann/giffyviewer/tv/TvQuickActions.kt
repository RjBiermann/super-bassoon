package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
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
    /** The open feed itself as an addable ref (e.g. browsing a niche). */
    addableFeedRef: String? = null,
    /** Non-null in the player: adds Like/Mute/Speed/Auto-swipe rows. */
    playerActions: TvPlayerActions? = null,
) {
    var showAddToFeed by remember { mutableStateOf(false) }
    // Two views (user: "too many options"): main / tags submenu.
    var view by remember { mutableStateOf("main") }
    val customFeeds by feedViewModel.customFeeds.collectAsStateWithLifecycle(emptyList())
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
                    Text(
                        text = "@${gif.userName}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (gif.verified) {
                        com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp).size(16.dp),
                        )
                    }
                }
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                if (view == "tags") {
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
                QuickAction(text = "Block creator") {
                    feedViewModel.blockCreator(gif.userName)
                    onDismiss()
                }
                if (customFeeds.isNotEmpty()) {
                    HorizontalDivider()
                    QuickAction(text = "Add to custom feed…") { showAddToFeed = true }
                }
                if (gif.tags.isNotEmpty()) {
                    QuickAction(text = "Tags…") { view = "tags" }
                }
                QuickAction(text = "Close") { onDismiss() }
            }
        }
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
    val creatorRef = "creator:${gif.userName.lowercase().trim()}"
    val tagRefs = gif.tags.take(3).map { "tag:${it.lowercase().trim()}" }
    val selected = remember { mutableStateListOf(creatorRef) }
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
                (listOfNotNull(addableFeedRef, creatorRef) + tagRefs).forEach { ref ->
                    val label =
                        when {
                            ref.startsWith("creator:") -> "@${ref.removePrefix("creator:")}"
                            ref.startsWith("niche:") -> "Niche: ${ref.removePrefix("niche:").substringAfter('|')}"
                            else -> "#${ref.removePrefix("tag:")}"
                        }
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
