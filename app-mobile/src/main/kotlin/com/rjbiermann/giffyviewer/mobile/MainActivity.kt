package com.rjbiermann.giffyviewer.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.feature.auth.AuthScreen
import com.rjbiermann.giffyviewer.feature.feed.FeedScreen
import com.rjbiermann.giffyviewer.feature.feed.PlayerScreen
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThemeHost() }
    }

    /** Reads PLAN §9 theme options and applies them app-wide. */
    @Composable
    private fun ThemeHost() {
        val amoled by settings.amoled.collectAsStateWithLifecycle(initialValue = false)
        val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
        GiffyTheme(amoled = amoled, dynamicColor = dynamicColor) { Host() }
    }

    @Composable
    private fun Host() {
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        var playerStartIndex by remember { mutableStateOf<Int?>(null) }
        var showAccount by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        if (confirmed && playerStartIndex != null) {
            val start = playerStartIndex ?: 0
            PlayerScreen(
                startIndex = start,
                onBack = { playerStartIndex = null },
                onOpenAccount = { showAccount = true },
                viewModel = hiltViewModel(),
                playerFactory = playerFactory,
                settings = settings,
                db = db,
            )
        } else if (confirmed && showAccount) {
            AuthScreen(onBack = { showAccount = false })
        } else if (confirmed && showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else if (confirmed) {
            FeedScreen(
                modifier = Modifier.fillMaxSize(),
                onOpenPlayer = { index -> playerStartIndex = index },
                onOpenAccount = { showAccount = true },
                onOpenSettings = { showSettings = true },
            )
        } else {
            val scope = rememberCoroutineScope()
            AgeGate(
                onConfirmed = { scope.launch { settings.confirmAge() } },
                onExit = ::finishAffinity,
            )
        }
    }
}

/**
 * PLAN §3: one-time full-screen attestation; start destination until confirmed;
 * no content loads before confirmation — FeedScreen isn't even composed here.
 */
@Composable
private fun AgeGate(
    onConfirmed: () -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
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
            androidx.compose.material3.Button(onClick = { scope.launch { onConfirmed() } }) {
                Text("I am 18 or older — Enter")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit) { Text("Exit (leaves app)") }
        }
    }
}
