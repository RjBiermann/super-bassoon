package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** §8 per-feed filter dialog: duration / resolution / orientation chips. */
@Composable
fun FeedFilterDialog(
    isGroup: Boolean = false,
    prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
    onApply: (com.rjbiermann.giffyviewer.core.datastore.FeedPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf(prefs.duration) }
    var resolution by remember { mutableStateOf(prefs.resolution) }
    var orientation by remember { mutableStateOf(prefs.orientation) }
    var shuffleSeed by remember { mutableStateOf(prefs.shuffleSeed) }
    var untaggedOnly by remember { mutableStateOf(prefs.untaggedOnly) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duration", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Any" to "",
                        "<10s" to "lt10",
                        "10–30s" to "10-30",
                        "30–60s" to "30-60",
                        "1–5m" to "1-5m",
                        ">5m" to "gt5m",
                    ).forEach { (label, value) ->
                        FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
                    }
                }
                Text("Resolution", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Any" to "", "HD only" to "hd").forEach { (label, value) ->
                        FilterChip(
                            selected = resolution == value,
                            onClick = { resolution = value },
                            label = { Text(label) },
                        )
                    }
                }
                Text("Shuffle", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = shuffleSeed == 0L,
                        onClick = { shuffleSeed = 0L },
                        label = { Text("Off") },
                    )
                    FilterChip(
                        selected = shuffleSeed != 0L,
                        onClick = { if (shuffleSeed == 0L) shuffleSeed = System.currentTimeMillis() },
                        label = { Text("On") },
                    )
                    if (shuffleSeed != 0L) {
                        FilterChip(
                            selected = false,
                            onClick = { shuffleSeed = System.currentTimeMillis() },
                            label = { Text("Reshuffle") },
                        )
                    }
                }
                if (isGroup) {
                    // §8: group feeds can restrict to gifs whose tags stay
                    // inside the group bundle (no outside-tag content).
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = untaggedOnly,
                                    role = Role.Switch,
                                    onValueChange = { untaggedOnly = it },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict tags", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Only gifs tagged exclusively with this group's tags",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.Switch(checked = untaggedOnly, onCheckedChange = null)
                    }
                }
                Text("Orientation", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Global" to "",
                        "Any" to "any",
                        "Vertical" to "vertical",
                        "Horizontal" to "horizontal",
                    ).forEach { (label, value) ->
                        FilterChip(selected = orientation == value, onClick = { orientation = value }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onApply(
                    com.rjbiermann.giffyviewer.core.datastore.FeedPrefs(
                        duration = duration,
                        resolution = resolution,
                        orientation = orientation,
                        shuffleSeed = shuffleSeed,
                        untaggedOnly = untaggedOnly,
                    ),
                )
            }) { Text("Apply") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
