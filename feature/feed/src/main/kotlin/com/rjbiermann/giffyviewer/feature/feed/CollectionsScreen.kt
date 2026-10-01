package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Saved Collections (PLAN §7, site wording verified 2026-10-01): header
 * "Saved Collections" + "Create New Collection"; rows rename/delete; share
 * mirrors the site's collection share. Logged-out → hidden by the shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    viewModel: CollectionsViewModel,
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<com.rjbiermann.giffyviewer.core.network.CollectionDto?>(null) }
    var deleteFor by remember { mutableStateOf<com.rjbiermann.giffyviewer.core.network.CollectionDto?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Collections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "create new collection")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            items(collections, key = { it.folderId }) { collection ->
                ListItem(
                    headlineContent = { Text(collection.folderName ?: "Untitled") },
                    supportingContent = { Text("${collection.contentCount} items") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { renameFor = collection }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "rename ${collection.folderName}")
                            }
                            IconButton(onClick = { deleteFor = collection }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "delete ${collection.folderName}")
                            }
                        }
                    },
                )
            }
            if (collections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No collections yet — Create New Collection bundles gifs from your watch page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(title = "Create New Collection", initial = "") { name ->
            viewModel.create(name)
            showCreate = false
        }
    }
    renameFor?.let { c ->
        NameDialog(title = "Rename", initial = c.folderName ?: "") { name ->
            viewModel.rename(c.folderId, name)
            renameFor = null
        }
    }
    deleteFor?.let { c ->
        // Confirm-first: deleting a collection is unrecoverable.
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete collection?") },
            text = { Text("Delete “${c.folderName ?: "Untitled"}” permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(c.folderId)
                    deleteFor = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onSubmit: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onSubmit("") },
        title = { Text(title) },
        text = {
            Box {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSubmit(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { onSubmit("") }) { Text("Cancel") } },
    )
}
