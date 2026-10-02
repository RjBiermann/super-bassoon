package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold

/**
 * Groups screen (PLAN §6/§7, mobile): create a tag bundle, cycle its state
 * (Favorited tab → Blocked filter → Neutral), delete. Tap a non-blocked group
 * to open its feed. Tag autocomplete lands with the tag-TTL tags table wiring
 * (§7); the field is a comma-separated text editor for now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (FeedSource.Group) -> Unit,
    viewModel: GroupsViewModel,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<com.rjbiermann.giffyviewer.core.database.NicheGroupEntity?>(null) }

    GiffyScaffold(
        title = "Groups",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "new group")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            items(groups, key = { it.id }) { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    supportingContent = { Text(group.tagList.replace(",", " · ")) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { deleteFor = group }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "delete ${group.name}",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            TextButton(onClick = { viewModel.cycleState(group) }) {
                                Text(
                                    when (group.state) {
                                        "FAVORITED" -> "Tab"
                                        "BLOCKED" -> "Blocked"
                                        else -> "Neutral"
                                    },
                                )
                            }
                        }
                    },
                    modifier =
                        Modifier.clickable {
                            // Blocked groups open no feed (macro-filter only).
                            if (group.state != "BLOCKED") {
                                onOpenGroup(FeedSource.Group(group.id, group.name, group.tagList.split(',')))
                            }
                        },
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No groups yet — tap + to bundle tags into a feed or a filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    deleteFor?.let { group ->
        // Confirm-first: same destructive class as collection delete.
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete group?") },
            text = { Text("Delete “${group.name}” permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(group)
                    deleteFor = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
        )
    }
    if (showCreate) {
        CreateGroupDialog(
            onCreate = { name, tags ->
                viewModel.create(name, tags)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    singleLine = true,
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && tags.split(',').any { it.isNotBlank() },
                onClick = { onCreate(name, tags.split(',')) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
