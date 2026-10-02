package com.rjbiermann.giffyviewer.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.auth.TokenStore

/**
 * Account section — embedded in Settings (mobile + TV share it). WebView PKCE
 * is the only sign-in path (paste-token removed 2026-10, user decision).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSection(
    viewModel: AuthViewModel = hiltViewModel(),
    /** Full-screen WebView render is the host's job (overlay, not inline). */
    onOpenWebView: (TokenStore.Pkce) -> Unit,
    modifier: Modifier = Modifier,
    /** Modifier for the pane's first button (TV initial D-pad focus hook). */
    firstButtonModifier: Modifier = Modifier,
) {
    val token by viewModel.token.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (token == null) {
            Text("Sign in with browser", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "One tap, opens the site in a WebView — the token exchange " +
                    "happens in-app (PKCE). No copy-pasting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onOpenWebView(viewModel.currentPkce()) },
                modifier = firstButtonModifier.fillMaxWidth(),
            ) {
                Text("Sign in with browser")
            }
        } else {
            Text("Signed in", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your favorites, follows and likes sync to your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = viewModel::signOut, modifier = firstButtonModifier.fillMaxWidth()) {
                Text("Sign out")
            }
        }
    }
}
