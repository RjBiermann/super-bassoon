@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.feature.feed

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.rjbiermann.giffyviewer.core.ui.GiffyColors
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * TikTok-style swipe player (PLAN §9): vertical pager, only the active page
 * plays. Resume positions go to `watch_history`; SimpleCache writes under the
 * gif ID. Controls: single tap reveals the overlay / pauses, auto-hide after
 * idle, fullscreen toggle (immersive), always-on thin progress bar with
 * remaining-time chip, draggable scrub, two-finger pinch zoom (1x–3x) that
 * persists across swipes in the session.
 */
private const val IDLE_HIDE_MS = 3_000L

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
    val view = LocalView.current
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
    val player = remember { playerFactory.create(context) }

    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // pinch zoom: session-scoped so it persists across swipes (PLAN §9)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // immersive when fullscreen; restore on exit / dispose
    DisposableEffect(fullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            val window = (context as? Activity)?.window
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // idle auto-hide only while playing and not scrubbing
    LaunchedEffect(controlsVisible, isPlaying, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(IDLE_HIDE_MS)
            controlsVisible = false
        }
    }
    // position + play-state ticker drives progress bar / play button
    LaunchedEffect(scrubbing) {
        while (true) {
            if (!scrubbing) {
                positionMs = player.currentPositionMs
                isPlaying = player.isPlaying
            }
            durationMs = player.durationMs
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !fullscreen) {
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
        }

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val gif = items[page]
            if (gif != null) {
                PlayerPage(
                    gif = gif,
                    active = page == pagerState.currentPage,
                    player = player,
                    dataSaver = dataSaver,
                    watchHistory = db.watchHistoryDao(),
                    onPlayingChanged = { isPlaying = it },
                    controlsVisible = controlsVisible,
                    onHideControls = { controlsVisible = false },
                    onShowControls = { controlsVisible = true },
                    onTogglePlay = { controlsVisible = true },
                    scrubbing = scrubbing,
                    onScrubbing = { scrubbing = it },
                    scrubPositionMs = scrubPositionMs,
                    onScrub = { scrubPositionMs = it },
                    positionMs = positionMs,
                    durationMs = durationMs,
                    zoom = zoom,
                    pan = pan,
                    onZoom = { z, p ->
                        zoom = z.coerceIn(1f, 3f)
                        pan = p
                    },
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
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
    onPlayingChanged: (Boolean) -> Unit,
    controlsVisible: Boolean,
    onHideControls: () -> Unit,
    onShowControls: () -> Unit,
    onTogglePlay: () -> Unit,
    scrubbing: Boolean,
    onScrubbing: (Boolean) -> Unit,
    scrubPositionMs: Long,
    onScrub: (Long) -> Unit,
    positionMs: Long,
    durationMs: Long,
    zoom: Float,
    pan: Offset,
    onZoom: (Float, Offset) -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    if (active) {
        // resume: pull the stored position once per gif before starting playback
        LaunchedEffect(gif.id, dataSaver) {
            val resume = watchHistory.byGif(gif.id)?.positionMs ?: 0L
            player.playGif(gif, dataSaver, resumeMs = resume)
            onPlayingChanged(true)
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // two-finger pinch zoom + pan (PLAN §9); zoom persists across pages.
                // Two-pointer-only: single-finger drags stay unconsumed so the
                // VerticalPager keeps working.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // skip until a second finger joins
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) break
                            if (pressed.isEmpty()) return@awaitEachGesture
                        }
                        var lastCentroid = Offset.Zero
                        var lastSpread = 0f
                        var sawTwo = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pts = event.changes.filter { it.pressed }
                            if (pts.size < 2) {
                                if (pts.isEmpty()) break
                                continue
                            }
                            val a = pts[0].position
                            val b = pts[1].position
                            val centroid = (a + b) / 2f
                            val spread = (a - b).getDistance()
                            if (sawTwo) {
                                val gestureZoom = if (lastSpread > 0f) spread / lastSpread else 1f
                                val gesturePan = centroid - lastCentroid
                                if (gestureZoom != 1f || zoom > 1f) {
                                    val z = (zoom * gestureZoom).coerceIn(1f, 3f)
                                    var p = pan + gesturePan
                                    if (z <= 1.01f) {
                                        p = Offset.Zero
                                    } else {
                                        // clamp pan so edges never show black while zoomed
                                        val maxX = size.width * (z - 1) / 2f
                                        val maxY = size.height * (z - 1) / 2f
                                        p = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                                    }
                                    onZoom(z, p)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                sawTwo = true
                            }
                            lastCentroid = centroid
                            lastSpread = spread
                        }
                    }
                }
                // single tap: reveal UI when hidden, pause/play when shown (PLAN §9).
                // Non-consuming (no detectTapGestures) — it would eat the down
                // event and starve the VerticalPager's drag gesture.
                .pointerInput(controlsVisible) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up =
                            waitForUpOrCancellation()
                                ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() <
                            viewConfiguration.touchSlop
                        ) {
                            if (controlsVisible) {
                                if (player.isPlaying) player.pause() else player.play()
                            } else {
                                onShowControls()
                            }
                        }
                    }
                },
    ) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
        )
        Text(
            text = "@${gif.userName}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, bottom = 56.dp),
        )
        PlayerControls(
            visible = controlsVisible && active,
            isPlaying = player.isPlaying,
            onTogglePlay = {
                if (player.isPlaying) player.pause() else player.play()
            },
            scrubbing = scrubbing,
            onScrubbing = onScrubbing,
            scrubPositionMs = scrubPositionMs,
            onScrub = onScrub,
            positionMs = if (scrubbing) scrubPositionMs else positionMs,
            durationMs = durationMs,
            fullscreen = fullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onScrubFinished = { player.seekTo(it) },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

/** Bottom overlay: play/pause + fullscreen row, slider, always-on 3dp progress bar. */
@Composable
private fun PlayerControls(
    visible: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    scrubbing: Boolean,
    onScrubbing: (Boolean) -> Unit,
    scrubPositionMs: Long,
    onScrub: (Long) -> Unit,
    positionMs: Long,
    durationMs: Long,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color.White.copy(alpha = 0.3f)
    val fill = GiffyColors.Lime

    // bottom-aligned overlay column: controls row above the always-visible
    // thin progress bar (PLAN §9: progress at the player's bottom edge)
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "pause" else "play",
                        tint = Color.White,
                    )
                }
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = {
                        onScrubbing(true)
                        onScrub((it * durationMs).roundToInt().toLong())
                    },
                    onValueChangeFinished = {
                        onScrubFinished(scrubPositionMs.coerceIn(0, durationMs))
                        onScrubbing(false)
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = fill,
                            activeTrackColor = fill,
                            inactiveTrackColor = trackColor,
                        ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatRemaining(positionMs, durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                )
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullscreen) "exit fullscreen" else "fullscreen",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // always-visible thin progress bar (PLAN §9): 24dp hit area, 3dp track
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 72.dp)
                    .height(24.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { }
                    },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(trackColor, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(fill, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** Applies the seek position when the scrub gesture finishes. */
private fun formatRemaining(
    positionMs: Long,
    durationMs: Long,
): String {
    val remaining = max(0, durationMs - positionMs) / 1000
    return "-${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
}
