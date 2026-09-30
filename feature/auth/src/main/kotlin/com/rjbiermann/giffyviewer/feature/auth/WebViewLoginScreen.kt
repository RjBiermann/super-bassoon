package com.rjbiermann.giffyviewer.feature.auth

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rjbiermann.giffyviewer.core.auth.TokenStore

/**
 * Primary login path (PLAN §2): app-driven OAuth PKCE. The WebView loads the
 * auth2 authorize request; after the user signs in there, auth2 redirects to
 * the redirect_uri with an authorization code, which this screen intercepts
 * (before the site's JS can consume it) and hands to the caller for the
 * in-app token exchange. Verified live 2026-09-30 — see AGENTS-AUTH.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    pkce: TokenStore.Pkce,
    onCodeCaptured: (code: String) -> Unit,
    onClose: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in — upstream.com") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx)
                        .apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        url: String?,
                                    ): Boolean {
                                        val u = url ?: return false
                                        val code = TokenStore.extractCode(u, pkce.state)
                                        if (code != null) {
                                            onCodeCaptured(code)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        println("WebViewLogin page=$url")
                                    }
                                }
                            loadUrl(TokenStore.authorizeUrl(pkce))
                        }.also { webView = it }
                },
            )
            Text(
                text = "Sign in here — the app captures it automatically",
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
