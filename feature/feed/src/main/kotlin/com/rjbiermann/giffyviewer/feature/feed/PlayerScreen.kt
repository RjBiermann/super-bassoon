@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.paging.compose.collectAsLazyPagingItems
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.WatchHistoryEntity
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory

/**
 * TikTok-style swipe player (PLAN §9): vertical pager, only the active page plays.
 * Resume positions go to `watch_history`; SimpleCache writes under the gif ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: FeedViewModel,
    playerFactory: GiffyPlayerFactory,
    settings: SettingsRepository,
    db: GiffyDatabase,
) {
    val items = viewModel.gifs.collectAsLazyPagingItems()
    val pagerState =
        rememberPagerState(initialPage = startIndex.coerceAtLeast(0)) {
            items.itemCount.coerceAtLeast(1)
        }
    val context = LocalContext.current
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
    val player = remember { playerFactory.create(context) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Now playing") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val gif = items[page]
            if (gif != null) {
                PlayerPage(
                    gif = gif,
                    active = page == pagerState.currentPage,
                    player = player,
                    dataSaver = dataSaver,
                    watchHistory = db.watchHistoryDao(),
                )
            }
        }
    }
}

@Composable
private fun PlayerPage(
    gif: Gif,
    active: Boolean,
    player: GiffyPlayer,
    dataSaver: Boolean,
    watchHistory: com.rjbiermann.giffyviewer.core.database.WatchHistoryDao,
) {
    if (active) {
        // resume: pull the stored position once per gif before starting playback
        LaunchedEffect(gif.id, dataSaver) {
            val resume = watchHistory.byGif(gif.id)?.positionMs ?: 0L
            player.playGif(gif, dataSaver, resumeMs = resume)
        }
        // resume tracking: sample position every 5s
        LaunchedEffect(gif.id) {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val pos = player.currentPositionMs
                if (pos > 0) {
                    val watched = player.durationMs > 0 && pos > player.durationMs * 0.8
                    watchHistory.upsert(
                        WatchHistoryEntity(
                            gifId = gif.id,
                            positionMs = pos,
                            updatedAt = System.currentTimeMillis(),
                            watched = watched,
                        ),
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.player = if (active) player else null
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "@${gif.userName}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}
