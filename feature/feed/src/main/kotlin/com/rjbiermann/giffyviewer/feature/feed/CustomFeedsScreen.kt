package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomFeedsViewModel
    @Inject
    constructor(
        private val db: GiffyDatabase,
    ) : ViewModel() {
        val feeds: StateFlow<List<CustomFeedEntity>> =
            db.customFeedDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Saves a definition; refs are pre-expanded ("creator:<u>" / "tag:<text>" / "niche:<id>|<name>"). */
        fun save(
            name: String,
            refs: List<String>,
        ) {
            // Empty feed is valid: name-only definitions are created to be
            // filled later via tile long-press / player ⋯ "Add to custom feed…".
            if (name.isBlank()) return
            viewModelScope.launch {
                db
                    .customFeedDao()
                    .upsert(
                        CustomFeedEntity(
                            name = name.trim(),
                            sourcesJson = refs.joinToString(","),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
            }
        }

        fun delete(id: Long) {
            // Evict the feed's cached pages too — a stale "custom:<id>:p*" row
            // otherwise lingers until TTL eviction (and would resurrect a
            // deleted feed's tiles if a new definition ever reuses the id).
            viewModelScope.launch {
                db.feedPageDao().evictBase("custom:$id")
                db.customFeedDao().delete(id)
            }
        }

        /** Merged niche-group state cycle (PLAN §6/§7): FAVORITED → pinned home
         *  tab · BLOCKED → ContentFilter macro-filter · NEUTRAL → openable only. */
        fun cycleState(feed: CustomFeedEntity) {
            val next =
                when (feed.state) {
                    "FAVORITED" -> "BLOCKED"
                    "BLOCKED" -> "NEUTRAL"
                    else -> "FAVORITED"
                }
            viewModelScope.launch {
                db.customFeedDao().upsert(feed.copy(state = next))
            }
        }
    }

/** Short label for a custom-feed ref (picker chips + builder pills). */
public fun customRefSummary(ref: String): String =
    when {
        ref.startsWith("creator:") -> "@${ref.removePrefix("creator:")}"
        ref.startsWith("niche:") -> "Niche: ${parseNicheRef(ref)?.second ?: ""}"
        else -> "#${ref.removePrefix("tag:")}"
    }

/** Niche "id|name" packing format — pins store bare "<id>|<name>", custom-feed
 *  refs carry the "niche:" prefix. Both parse here; one named home for the
 *  stringly-typed format (audit round-3 #6).
 *
 *  ponytail: pinned packing itself lives in core:datastore (togglePinnedNiche)
 *  — core can't reach feature:feed, so the pack half stays inline there. */
public fun packNicheRef(
    id: String,
    name: String,
    prefixed: Boolean = false,
): String = if (prefixed) "niche:$id|$name" else "$id|$name"

/** (id, name) from either variant, or null when the ref isn't a niche pair. */
public fun parseNicheRef(ref: String): Pair<String, String>? =
    ref
        .removePrefix("niche:")
        .split('|', limit = 2)
        .takeIf { it.size == 2 }
        ?.let { (id, name) -> id to name }

/** Refs a gif contributes to a custom feed (its niches + creator + first 3 tags);
 *  builder refs are "creator:<username>" / "tag:<text>" / "niche:<id>|<name>".
 *  Niche caps: the gif's own niches ≤3 (quick-sheet tag cap), optionally one
 *  extra for the open feed itself. */
public fun gifFeedRefs(
    gif: Gif,
    nicheRef: String? = null,
): List<String> =
    listOfNotNull(nicheRef, "creator:${gif.userName.lowercase().trim()}") +
        gif.niches.take(3).map { packNicheRef(it.id, it.name, prefixed = true) } +
        gif.tags.take(3).map { "tag:${it.lowercase().trim()}" }

internal fun parseCustomRefs(sourcesJson: String): List<String> = sourcesJson.split(',').filter { it.isNotBlank() }

/** Pick a joined niche (site popup parity: joined niches matching the gif's
 *  tags first) — shared rule so both dialogs (mobile M3, TV giffyFocus) agree.
 *  Extracting the dialogs themselves needs the TV ring check the audit demands;
 *  the ordering rule is the part that was verbatim-duplicated. */
public fun orderNichesByTagMatch(
    gifTags: List<String>,
    niches: List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>,
): List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto> {
    val gifTags = gifTags.map { it.lowercase().trim() }.toSet()
    val matching = niches.filter { n -> n.tags.any { it.lowercase().trim() in gifTags } }
    return matching + (niches - matching.toSet())
}

/** Custom feed builder (PLAN §7): named blend of creators + tags (+ niches).
 *  Also hosts the merged niche-group management (DB v10): FAVORITED feeds pin
 *  as home tabs, BLOCKED feeds are the ContentFilter macro-filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFeedsScreen(
    onBack: () -> Unit,
    onOpenFeed: (FeedSource.Custom) -> Unit,
    viewModel: CustomFeedsViewModel,
    /** TV keeps the no-focus-on-open trap away: first focus on the name field. */
    requestInitialFocus: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (requestInitialFocus) firstFocus.requestFocus()
    }
    val refs = remember { mutableStateListOf<String>() }
    var creatorInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    // Confirm-first: same destructive class as Collections/Groups delete (also
    // evicts the feed's cached pages).
    var deleteFor by remember { mutableStateOf<CustomFeedEntity?>(null) }
    val feeds by viewModel.feeds.collectAsStateWithLifecycle(emptyList())
    // Empty feed is valid: create it now, fill it later from any tile's
    // long-press "Add to custom feed…" quick action.
    val canSave = name.isNotBlank()

    GiffyScaffold(
        title = "Custom feeds",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Blend creators, niches and tags into one feed — or bundle tags to favorite as a tab or block as a filter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feed name") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (requestInitialFocus) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            ),
                    singleLine = true,
                )
            }
            if (refs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        refs.forEach { ref ->
                            AssistChip(
                                // Click = remove (the whole chip is one target;
                                // the X icon states it — refs re-add trivially).
                                onClick = { refs.remove(ref) },
                                label = { Text(customRefSummary(ref)) },
                                trailingIcon = {
                                    androidx.compose.material3.IconButton(
                                        onClick = { refs.remove(ref) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "remove ${customRefSummary(ref)}",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = creatorInput,
                        onValueChange = { creatorInput = it },
                        label = { Text("Creator") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        enabled = creatorInput.isNotBlank(),
                        onClick = {
                            refs.add("creator:${creatorInput.trim().lowercase()}")
                            creatorInput = ""
                        },
                    ) { Text("Add") }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Tag") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        enabled = tagInput.isNotBlank(),
                        onClick = {
                            refs.add("tag:${tagInput.trim().lowercase()}")
                            tagInput = ""
                        },
                    ) { Text("Add") }
                }
            }
            item {
                Button(
                    enabled = canSave,
                    onClick = {
                        viewModel.save(name, refs.toList())
                        name = ""
                        refs.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save feed") }
            }
            item { Text("Your feeds", style = MaterialTheme.typography.titleSmall) }
            items(feeds, key = { it.id }) { feed ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        Modifier
                            .clickable {
                                // Blocked feeds open no feed — macro-filter only
                                // (merged niche-group rule).
                                if (feed.state != "BLOCKED") {
                                    onOpenFeed(FeedSource.Custom(feed.id, feed.name, parseCustomRefs(feed.sourcesJson)))
                                }
                            }.weight(1f),
                    ) {
                        Text(feed.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            customRefSummaryAll(feed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Merged group state cycle: Tab (favorited) → Blocked → Neutral.
                    // giffyFocus: M3 buttons draw nothing on D-pad focus (TV).
                    val cycleInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { viewModel.cycleState(feed) },
                        modifier = Modifier.giffyFocus(cycleInteraction),
                        interactionSource = cycleInteraction,
                    ) {
                        Text(
                            when (feed.state) {
                                "FAVORITED" -> "Tab"
                                "BLOCKED" -> "Blocked"
                                else -> "Neutral"
                            },
                        )
                    }
                    val deleteInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { deleteFor = feed },
                        modifier = Modifier.giffyFocus(deleteInteraction),
                        interactionSource = deleteInteraction,
                    ) { Text("Delete") }
                }
            }
        }
    }
    deleteFor?.let { feed ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete “${feed.name}”?") },
            text = { Text("Removes the feed and its cached pages. Creators and tags stay untouched.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.delete(feed.id)
                    deleteFor = null
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteFor = null }) { Text("Cancel") }
            },
        )
    }
}

private fun customRefSummaryAll(feed: CustomFeedEntity): String =
    parseCustomRefs(feed.sourcesJson).joinToString(" · ") { customRefSummary(it) }
