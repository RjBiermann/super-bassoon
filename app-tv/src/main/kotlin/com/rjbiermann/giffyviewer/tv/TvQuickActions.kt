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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.customRefSummary
import com.rjbiermann.giffyviewer.feature.feed.gifFeedRefs
import com.rjbiermann.giffyviewer.feature.feed.orderNichesByTagMatch
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
    /** SLICE-13 (D-pad slider row, mobile QuickSheet 0.25-step parity):
     *  LEFT/RIGHT on the focused row adjusts by the given delta (±0.25);
     *  replacing the old 4-step tap cycle. */
    val onAdjustSpeed: (Float) -> Unit,
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
    /** Show-more pane: niche pills open the niche feed (player exits first). */
    onOpenNiche: (FeedSource.Niche) -> Unit = {},
    /** The open feed itself as an addable ref (e.g. browsing a niche). */
    addableFeedRef: String? = null,
    /** Non-null in the player: adds Like/Mute/Speed/Auto-swipe rows. */
    playerActions: TvPlayerActions? = null,
    /** Slice 10: the HOME row's feed source — the main pane gains an
     *  "Open feed" row that opens it full-page (TvSourceFeedScreen). Rows
     *  without an openable source (Surprise pool) pass null → no row. */
    feedSource: FeedSource? = null,
    /** Slice 10 navigation hook — TvMainActivity swaps `openFeed`. */
    onOpenFeed: (FeedSource) -> Unit = {},
) {
    var showAddToFeed by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var showAddToNiche by remember { mutableStateOf(false) }
    // Follow row (mobile quick-sheet parity): server read on open, toggled in
    // place; anonymous users see no row (a write would 401).
    LaunchedEffect(gif.id) { feedViewModel.refreshFollowedCreators() }
    val followedCreators by feedViewModel.followedCreators.collectAsStateWithLifecycle(emptySet())
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
                        // Always listed — with no custom feeds yet the empty
                        // picker says so (parity with Add to a Collection).
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
                    "more" -> {
                        // TikTok TV "show more" parity (AGENTS-UX-PATTERNS TV):
                        // the full description (the player cluster truncates to
                        // 2 lines) + the gif's niches as openable rows. The
                        // DOWN-routing from the original candidate is NOT taken —
                        // DOWN is the item-walk key (keymap rule); this panel is
                        // the rule-5 sanctioned path.
                        val desc = gif.description
                        if (desc != null && desc.isNotBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        gif.niches.forEach { niche ->
                            QuickAction(text = "Open niche: ${niche.name}") {
                                onDismiss()
                                onOpenNiche(FeedSource.Niche(niche.id, niche.name))
                            }
                        }
                        QuickAction(text = "‹ Back", first = first) { view = "main" }
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
                    SpeedAdjustRow(
                        speed = pa.speed,
                        onAdjust = pa.onAdjustSpeed,
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
                // Slice 10 (user ask): home rows show feeds horizontally but cards
                // jump straight into the player — without this row there was NO path
                // to the row's feed as a full-page grid. Player-variant panels have
                // no row context → omitted there (feedSource is null).
                if (playerActions == null && feedSource != null) {
                    QuickAction(text = "Open feed") {
                        onDismiss()
                        onOpenFeed(feedSource)
                    }
                }
                // Links audit #3: the most basic navigation act on a creator.
                // Caller decides navigation (home/source-feed open, player exits first —
                // swapping the feed source under the live pager is the soak-crash class).
                QuickAction(text = "Open @${gif.userName}'s feed") {
                    onDismiss()
                    onOpenCreator(gif.userName)
                }
                if (feedViewModel.loggedIn) {
                    QuickAction(
                        text =
                            if (gif.userName.lowercase() in followedCreators) {
                                "Unfollow @${gif.userName}"
                            } else {
                                "Follow @${gif.userName}"
                            },
                        onClick = {
                            feedViewModel.toggleFollowCreator(gif.userName)
                            onDismiss()
                        },
                    )
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
                // Round-3 #7 TV equivalent: Add to… always reachable (empty
                // custom-feeds path = the picker's own empty state), main pane
                // no longer depends on prefilled state.
                HorizontalDivider()
                QuickAction(text = "Add to…") { view = "addto" }
                if (gif.tags.isNotEmpty()) {
                    QuickAction(text = "Tags…") { view = "tags" }
                }
                if (!gif.description.isNullOrBlank() || gif.niches.isNotEmpty()) {
                    QuickAction(text = "Show more") { view = "more" }
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

/**
 * SLICE-13: the panel's Speed row as a D-pad slider (mobile QuickSheet
 * parity — same 0.5–2× range, 0.25 steps; was a 4-step tap cycle). While
 * the row is focused, LEFT/RIGHT adjust ±0.25 (held keys repeat via the OS
 * key-repeat) and UP/normal traversal leaves the row; CENTER opens nothing
 * (display-only row — the click is intentionally a no-op, the value lives
 * in the label). The consumed KeyDown prevents directional focus moves out
 * of the row; the shared giffyFocus ring keeps the batch-12 visible-focus
 * treatment.
 */
@Composable
private fun SpeedAdjustRow(
    speed: Float,
    onAdjust: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = { /* display-only: adjust lives on LEFT/RIGHT (SLICE-13) */ },
        interactionSource = interaction,
        modifier =
            Modifier
                .fillMaxWidth()
                .giffyFocus(interaction)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> {
                            onAdjust(-SPEED_STEP)
                            true
                        }
                        Key.DirectionRight -> {
                            onAdjust(SPEED_STEP)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        // Locale.US: comma-decimal locales read "0,50×" — and the speed steps
        // are 0.25, so 2 decimals is real precision.
        Text("Speed " + String.format(java.util.Locale.US, "%.2f", speed) + "× — ‹ › to adjust")
    }
}

/** SLICE-13: quarter-step; [0.5, 2.0] clamp = the mobile slider's range. */
internal const val SPEED_STEP = 0.25f

/** SLICE-13 pure adjust: quarter-grid snap (fp-safe) + clamp [0.5, 2.0]. */
internal fun adjustSpeed(
    current: Float,
    delta: Float,
): Float = (Math.round((current + delta) * 4f)).coerceIn(2, 8) / 4f

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
                if (customFeeds.isEmpty()) {
                    Text("No custom feeds yet — create one on the phone (“Custom feeds”).")
                } else {
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
    val ordered = orderNichesByTagMatch(gif.tags, niches)
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
                    ordered.take(8).forEach { n ->
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
