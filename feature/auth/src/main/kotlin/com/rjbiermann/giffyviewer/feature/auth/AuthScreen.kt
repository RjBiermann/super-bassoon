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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var webLogin by remember { mutableStateOf(false) }

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
                    "Sign in with browser",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "One tap, opens upstream.com in a WebView — the token " +
                        "exchange happens in-app (PKCE). No copy-pasting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
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
                Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
        }
    }
}
