package com.rjbiermann.giffyviewer.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** PLAN §6: blocked creators / tags / keywords with unblock actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // SAF pickers (native, no storage permission): create/open a JSON document.
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(viewModel.exportJson().toByteArray())
                        } ?: throw IllegalStateException()
                    }.fold(
                        onSuccess = { snackbarHostState.showSnackbar("Preferences exported") },
                        onFailure = { snackbarHostState.showSnackbar("Export failed") },
                    )
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val json =
                            context.contentResolver.openInputStream(uri)?.use {
                                it.readBytes().decodeToString()
                            } ?: throw IllegalStateException()
                        viewModel.importJson(json)
                    }.fold(
                        onSuccess = { result ->
                            result.fold(
                                onSuccess = {
                                    snackbarHostState.showSnackbar("Imported $it entries")
                                },
                                onFailure = {
                                    snackbarHostState.showSnackbar("Import failed — not a Giffy preferences file")
                                },
                            )
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar("Import failed — file not readable")
                        },
                    )
                }
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (blocked.creators.isEmpty() && blocked.tags.isEmpty() && blocked.keywords.isEmpty()) {
                item {
                    Text(
                        "Nothing blocked yet. Long-press a tile in the feed to block or favorite creators.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            section("Favorited creators", blocked.favorites) { viewModel.unfavoriteCreator(it) }
            section("Blocked creators", blocked.creators) { viewModel.unblockCreator(it) }
            section("Blocked tags", blocked.tags) { viewModel.unblockTag(it) }
            section("Blocked keywords", blocked.keywords) { viewModel.unblockKeyword(it) }
            item(key = "data-saver") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data saver", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Prefer SD streams — lower bandwidth, faster start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dataSaver, onCheckedChange = { viewModel.setDataSaver(it) })
                }
            }
            item(key = "backup") {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Preferences backup",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("giffy-prefs.json") }) {
                            Text("Export")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Text("Import")
                        }
                    }
                    Text(
                        "Export saves blocked & favorited creators, tags, keywords and data saver. " +
                            "Import merges them in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    values: List<String>,
    onRemove: (String) -> Unit,
) {
    if (values.isEmpty()) return
    item(key = title) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(values, key = { "$title:$it" }) { value ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onRemove(value) }) {
                Icon(Icons.Outlined.Close, contentDescription = "unblock $value")
            }
        }
    }
}
