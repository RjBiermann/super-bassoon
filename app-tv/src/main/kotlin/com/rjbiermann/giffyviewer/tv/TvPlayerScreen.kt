@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
    /** Shared quick-action model — MENU opens favorite/block/custom-feed. */
    feedViewModel: com.rjbiermann.giffyviewer.feature.feed.FeedViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<Gif?>(null) }
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
    // Seek feedback: "1:23 / 2:45" flashes while seeking (TV keymap §).
    var seekFlash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            kotlinx.coroutines.delay(1_500)
            seekFlash = null
        }
    }
    // Playback speed (MENU panel cycles 0.5 → 1 → 1.5 → 2).
    var speed by remember { mutableStateOf(1f) }

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
                    // AGENTS-UX-PATTERNS TV keymap: horizontal = time (±10s,
                    // hold-repeat = progressive seek), vertical = items,
                    // CENTER = play/pause, MENU = quick actions, media keys
                    // ride the same actions.
                    when (e.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                        -> {
                            player.playWhenReady = !(player.playWhenReady)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(player, +10_000L) { seekFlash = it }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(player, -10_000L) { seekFlash = it }
                            true
                        }
                    }
                    when (e.key) {
                        Key.DirectionDown -> {
                            if (index < gifs.size - 1) index++
                            true
                        }
                        Key.DirectionUp -> {
                            if (index > 0) index--
                            true
                        }
                        Key.DirectionRight -> {
                            seekBy(player, +10_000L) { seekFlash = it }
                            true
                        }
                        Key.DirectionLeft -> {
                            seekBy(player, -10_000L) { seekFlash = it }
                            true
                        }
                        // CENTER = play/pause (audit: "can't pause on TV").
                        Key.DirectionCenter -> {
                            player.playWhenReady = !(player.playWhenReady)
                            true
                        }
                        // MENU = quick actions for the on-screen gif (parity
                        // with the mobile player overflow sheet).
                        Key.Menu -> {
                            actionsFor = gifs.getOrNull(index)
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
            var fraction by remember { mutableStateOf(0f) }
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
            // Seek feedback: "1:23 / 2:45" while seeking (TV keymap §).
            seekFlash?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            // Mobile-player parity (2026-10): creator + description cluster,
            // bottom-left, above the progress line. Verified tick rides along.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 24.dp, end = 96.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = "@${gif.userName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    if (gif.verified) {
                        com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                            tint = Color.White,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp),
                        )
                    }
                }
                gif.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
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
    val muted by settings.muted.collectAsStateWithLifecycle(false)
    val autoSwipeOn by settings.autoSwipe.collectAsStateWithLifecycle(false)
    actionsFor?.let { sheetGif ->
        TvQuickActionsDialog(
            gif = sheetGif,
            feedViewModel = feedViewModel,
            playerActions =
                TvPlayerActions(
                    liked = sheetGif.id in feedViewModel.likedIds.value,
                    muted = muted,
                    speed = speed,
                    hasAudio = sheetGif.hasAudio,
                    autoSwipeOn = autoSwipeOn,
                    onToggleLike = { feedViewModel.toggleLike(sheetGif.id) },
                    onToggleMute = { scope.launch { settings.setMuted(!muted) } },
                    onCycleSpeed = {
                        speed =
                            when (speed) {
                                0.5f -> 1f
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 0.5f
                            }
                        player.setPlaybackSpeed(speed)
                    },
                    onToggleAutoSwipe = { scope.launch { settings.setAutoSwipe(!autoSwipeOn) } },
                ),
            onDismiss = { actionsFor = null },
        )
    }
}

/** ±10s step with a "1:23 / 2:45" flash; hold-repeat = progressive seek. */
private fun seekBy(
    player: com.rjbiermann.giffyviewer.core.player.GiffyPlayer,
    deltaMs: Long,
    flash: (String?) -> Unit,
) {
    val target = (player.currentPositionMs + deltaMs).coerceIn(0L, player.durationMs.coerceAtLeast(0L))
    player.seekTo(target)

    fun fmt(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
    val dur = player.durationMs
    flash(if (dur > 0) "${fmt(target)} / ${fmt(dur)}" else fmt(target))
    // cleared by the LaunchedEffect below
}
