package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Scaffold + TopAppBar + back icon, one shared idiom (2026-10 round-3 audit:
 * the boilerplate rendered 10×; copy-drift like "back"/"Back" now lives here).
 * `onBack = null` hides the navigation icon (home surface). `backIcon`/
 * `backDescription` cover the Close variant (Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiffyScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = "back",
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(backIcon, contentDescription = backDescription)
                        }
                    }
                },
                actions = actions,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        content = content,
    )
}
