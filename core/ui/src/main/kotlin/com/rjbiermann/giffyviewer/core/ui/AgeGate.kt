package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Age gate widget (AGENTS-APP §3): one-time full-screen attestation, one
 * definition for both shells so the normative copy can't drift. Shells keep
 * their own nav/host; TV adds initial D-pad focus via [requestInitialFocus].
 */
@Composable
fun AgeGate(
    onConfirmed: suspend () -> Unit,
    onExit: () -> Unit,
    requestInitialFocus: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val gateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (requestInitialFocus) gateFocus.requestFocus()
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Giffy Viewer",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    "This app shows adult content. You must be 18 or older. " +
                        "Not affiliated with or endorsed by upstream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            GiffyPillButton(
                text = "I am 18 or older — Enter",
                onClick = { scope.launch { onConfirmed() } },
                modifier = if (requestInitialFocus) Modifier.focusRequester(gateFocus) else Modifier,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit) { Text("Exit (leaves app)") }
        }
    }
}
