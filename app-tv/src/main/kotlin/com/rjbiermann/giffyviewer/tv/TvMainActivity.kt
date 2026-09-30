package com.rjbiermann.giffyviewer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import com.rjbiermann.giffyviewer.feature.auth.AuthScreen
import com.rjbiermann.giffyviewer.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var playerFactory: GiffyPlayerFactory

    @Inject lateinit var db: GiffyDatabase

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GiffyTheme { Root() } }
    }

    @Composable
    private fun Root() {
        val confirmed by settings.ageConfirmed.collectAsStateWithLifecycle(initialValue = false)
        var player: Pair<List<Gif>, Int>? by remember { mutableStateOf(null) }
        var showAccount by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        // D-pad BACK pops the top screen instead of exiting the activity
        androidx.activity.compose.BackHandler(
            enabled = player != null || showAccount || showSettings,
        ) {
            when {
                player != null -> player = null
                showSettings -> showSettings = false
                else -> showAccount = false
            }
        }

        when {
            !confirmed -> AgeGate(onConfirmed = { settings.confirmAge() })
            player != null -> {
                val (gifs, start) = player ?: listOf<Gif>() to 0
                TvPlayerScreen(
                    gifs = gifs,
                    startIndex = start,
                    playerFactory = playerFactory,
                    settings = settings,
                    db = db,
                    onBack = { player = null },
                )
            }
            showAccount -> AuthScreen(onBack = { showAccount = false })
            showSettings -> SettingsScreen(onBack = { showSettings = false })
            else -> {
                val home: TvHomeViewModel = hiltViewModel()
                val continueVm: ContinueWatchingViewModel = hiltViewModel()
                val focusRequester = remember { FocusRequester() }
                // No initial D-pad focus otherwise — header buttons were unreachable
                // until the user tabbed into a row blindly.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Column(modifier = Modifier.fillMaxSize()) {
                    Row {
                        AccountButton(
                            focusRequester = focusRequester,
                            onClick = { showAccount = true },
                        )
                        Button(
                            onClick = { showSettings = true },
                            modifier = Modifier.padding(16.dp),
                        ) { Text("Settings") }
                    }
                    TvHomeScreen(
                        onOpenGif = { gifs, index -> player = gifs to index },
                        homeViewModel = home,
                        continueViewModel = continueVm,
                    )
                }
            }
        }
    }

    /** PLAN §3: one-time blocking attestation; D-pad focusable buttons. */
    @Composable
    private fun AgeGate(onConfirmed: suspend () -> Unit) {
        val scope = rememberCoroutineScope()
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(160.dp))
                Text("Giffy Viewer", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This app shows adult content. You must be 18 or older. " +
                        "Not affiliated with or endorsed by upstream.",
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { scope.launch { onConfirmed() } }) { Text("I am 18 or older — Enter") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = ::finishAffinity) { Text("Exit (leaves app)") }
            }
        }
    }
}

@Composable
/** Header buttons need a requested initial focus — nothing else grabs it on this screen. */
private fun AccountButton(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(16.dp).focusRequester(focusRequester),
    ) { Text("Account") }
}
