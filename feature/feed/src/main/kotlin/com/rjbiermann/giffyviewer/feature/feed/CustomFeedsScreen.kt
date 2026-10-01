package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.NicheGroupEntity
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

        /** FAVORITED groups offered as one-tap tag expansion. */
        val favoriteGroups: StateFlow<List<NicheGroupEntity>> =
            db.nicheGroupDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Saves a definition; refs are pre-expanded ("creator:<u>" / "tag:<text>"). */
        fun save(
            name: String,
            refs: List<String>,
        ) {
            if (name.isBlank() || refs.isEmpty()) return
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
    }

/** Refs the builder produces: "creator:<username>" or "tag:<text>" (groups expand). */
internal fun customRefSummary(ref: String): String =
    when {
        ref.startsWith("creator:") -> "@${ref.removePrefix("creator:")}"
        else -> "#${ref.removePrefix("tag:")}"
    }

internal fun parseCustomRefs(sourcesJson: String): List<String> = sourcesJson.split(',').filter { it.isNotBlank() }

/** Custom feed builder (PLAN §7): named blend of creators + tags (+ groups expanded). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFeedsScreen(
    onBack: () -> Unit,
    onOpenFeed: (FeedSource.Custom) -> Unit,
    viewModel: CustomFeedsViewModel,
) {
    var name by remember { mutableStateOf("") }
    val refs = remember { mutableStateListOf<String>() }
    var creatorInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    val feeds by viewModel.feeds.collectAsStateWithLifecycle(emptyList())
    val groups by viewModel.favoriteGroups.collectAsStateWithLifecycle(emptyList())
    val canSave = name.isNotBlank() && refs.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom feeds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Blend creators, groups and tags into one feed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feed name") },
                    modifier = Modifier.fillMaxWidth(),
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
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(customRefSummary(ref)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        Modifier.clickable {
                                            refs.remove(ref)
                                        },
                                    )
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
            if (groups.any { it.state == "FAVORITED" }) {
                item {
                    Text("Add a group's tags:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        groups.filter { it.state == "FAVORITED" }.forEach { group ->
                            AssistChip(
                                onClick = {
                                    group.tagList
                                        .split(',')
                                        .filter { it.isNotBlank() }
                                        .forEach { tag -> refs.add("tag:${tag.trim().lowercase()}") }
                                },
                                label = { Text(group.name) },
                            )
                        }
                    }
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
                                onOpenFeed(FeedSource.Custom(feed.id, feed.name, parseCustomRefs(feed.sourcesJson)))
                            }.weight(1f),
                    ) {
                        Text(feed.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            customRefSummaryAll(feed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { viewModel.delete(feed.id) }) { Text("Delete") }
                }
            }
        }
    }
}

private fun customRefSummaryAll(feed: CustomFeedEntity): String =
    parseCustomRefs(feed.sourcesJson).joinToString(" · ") { customRefSummary(it) }
