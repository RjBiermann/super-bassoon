@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.WatchHistoryEntity
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory

/**
 * 10-foot player: one gif at a time, D-pad up/down (left/right too) walks the
 * list, back exits. Resume + 5s position sampling mirrors the mobile player.
 */
@Composable
fun TvPlayerScreen(
    gifs: List<Gif>,
    startIndex: Int,
    playerFactory: GiffyPlayerFactory,
    settings: SettingsRepository,
    db: GiffyDatabase,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
    // §5 video fit (shared setting): fit | crop | stretch.
    val videoFit by settings.videoFit.collectAsStateWithLifecycle("fit")
    val fitMode =
        when (videoFit) {
            "crop" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    var index by
        remember {
            mutableIntStateOf(startIndex.coerceAtLeast(0).coerceAtMost(gifs.size - 1))
        }
    val player = remember { playerFactory.create(context) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    if (gifs.isEmpty()) {
        // Defensive: a card tap implies an item exists; this guards index churn.
        Text("Nothing here", modifier = Modifier.padding(24.dp))
        return
    }

    val gif = gifs[index]

    // D-pad events only reach onPreviewKeyEvent via a FOCUSED node inside the
    // hierarchy — the PlayerView never takes focus, so grab it on entry.
    val playerFocus =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }
    LaunchedEffect(Unit) { playerFocus.requestFocus() }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable()
                .focusRequester(playerFocus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown, Key.DirectionRight -> {
                            if (index < gifs.size - 1) index++
                            true
                        }
                        Key.DirectionUp, Key.DirectionLeft -> {
                            if (index > 0) index--
                            true
                        }
                        // CENTER = play/pause (audit: "can't pause on TV").
                        Key.DirectionCenter -> {
                            player.playWhenReady = !(player.playWhenReady)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply { useController = false }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = fitMode
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Thin lime progress line (mobile parity).
            var fraction by remember { mutableStateOf(0.3f) } // BISECT: fixed initial
            LaunchedEffect(gif.id) {
                while (true) {
                    kotlinx.coroutines.delay(500)
                    val d = player.durationMs
                    if (d > 0) fraction = player.currentPositionMs.toFloat() / d
                }
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(com.rjbiermann.giffyviewer.core.ui.GiffyColors.Lime),
                )
            }
        }
    }

    // resume + tracking (outside the Column so they survive gif switches)
    LaunchedEffect(gif.id, dataSaver) {
        val resume = db.watchHistoryDao().byGif(gif.id)?.positionMs ?: 0L
        player.playGif(gif, dataSaver, resumeMs = resume)
    }
    LaunchedEffect(gif.id) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            val pos = player.currentPositionMs
            if (pos > 0) {
                db.watchHistoryDao().upsert(
                    WatchHistoryEntity(
                        gifId = gif.id,
                        positionMs = pos,
                        updatedAt = System.currentTimeMillis(),
                        watched = player.durationMs > 0 && pos > player.durationMs * 0.8,
                    ),
                )
            }
        }
    }
}
