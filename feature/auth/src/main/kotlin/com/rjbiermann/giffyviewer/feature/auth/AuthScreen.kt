package com.rjbiermann.giffyviewer.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Paste-token sign-in (fallback — AGENTS-AUTH.md). Raw token is
 * shown ONLY in the reveal dialog. Primary path is the PKCE WebView login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val token by viewModel.token.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var reveal by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    val signInFocus = remember { FocusRequester() }

    if (webLogin) {
        WebViewLoginScreen(
            pkce = viewModel.currentPkce(),
            onCodeCaptured = { code ->
                webLogin = false
                viewModel.exchangeCode(code)
            },
            onClose = { webLogin = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (token == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste your upstream bearer token",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sign in at upstream.com, open browser DevTools → Network, " +
                        "copy the token from any request's Authorization header " +
                        "(without the \"Bearer \" prefix).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        error = false
                    },
                    singleLine = true,
                    label = { Text("Bearer token") },
                    isError = error,
                    supportingText = { if (error) Text("That doesn't look like a JWT token") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // TV: D-pad DOWN moves from the token field to Sign in.
                            .onPreviewKeyEvent { e ->
                                e.type == KeyEventType.KeyUp &&
                                    e.key == Key.DirectionDown &&
                                    signInFocus.requestFocus()
                            },
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!viewModel.save(draft)) error = true else draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().focusRequester(signInFocus),
                ) {
                    Text("Sign in")
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { webLogin = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with browser")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Signed in", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your favorites, follows and likes sync to your upstream account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { reveal = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reveal token (for TV login)")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
        }
    }

    if (reveal) {
        val tokenNow = token.orEmpty()
        AlertDialog(
            onDismissRequest = { reveal = false },
            title = { Text("Bearer token") },
            text = {
                Text(tokenNow, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(tokenNow))
                    reveal = false
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { reveal = false }) { Text("Close") }
            },
        )
    }
}
