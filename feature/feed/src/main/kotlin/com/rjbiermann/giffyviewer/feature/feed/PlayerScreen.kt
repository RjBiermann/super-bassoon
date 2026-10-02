@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.feature.feed

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.paging.compose.collectAsLazyPagingItems
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.WatchHistoryEntity
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.GiffyColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * TikTok-style swipe player (PLAN §9): vertical pager, only the active page
 * plays. Resume positions go to `watch_history`; SimpleCache writes under the
 * gif ID. Controls: single tap reveals the overlay / pauses, auto-hide after
 * idle, fullscreen toggle (immersive), always-on thin progress bar with
 * remaining-time chip, draggable scrub, two-finger pinch zoom (1x–3x) that
 * resets on swipe (PLAN §9 revised 2026-09-30), and the right action rail
 * (like / mute / share / overflow).
 */
private const val IDLE_HIDE_MS = 3_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun PlayerScreen(
    startIndex: Int,
    onBack: () -> Unit,
    /** Like while logged out → open Settings (holds the account section). */
    onOpenAccount: () -> Unit,
    viewModel: FeedViewModel,
    playerFactory: GiffyPlayerFactory,
    settings: SettingsRepository,
    db: GiffyDatabase,
) {
    val items = viewModel.gifs.collectAsLazyPagingItems()
    // §8 shuffle player: read this feed's shuffle seed (random end-of-pool jumps).
    val shuffleSeed by
        viewModel.source
            .flatMapLatest { viewModel.feedPrefs(it.baseKey) }
            .map { it.shuffleSeed }
            .collectAsStateWithLifecycle(0L)
    val pagerState =
        rememberPagerState(initialPage = startIndex.coerceAtLeast(0)) {
            items.itemCount.coerceAtLeast(1)
        }
    val context = LocalContext.current
    val view = LocalView.current
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle(false)
    val muted by settings.muted.collectAsStateWithLifecycle(false)
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle(emptySet())
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
    val autoSwipe by settings.autoSwipe.collectAsStateWithLifecycle(false)
    val videoFit by settings.videoFit.collectAsStateWithLifecycle("fit")
    val muteScope = rememberCoroutineScope()
    val player = remember { playerFactory.create(context) }

    // playback speed: session-only — resets when the player is released (PLAN §9)
    var speed by remember { mutableFloatStateOf(1f) }
    // playback failure overlay (PLAN §9): Retry re-resolves, Skip advances
    var playError by remember { mutableStateOf(false) }
    // auto-advance plays the next item from 0 — a watched neighbor resuming
    // near its end would end instantly and cascade swipes (user report)
    val skipResume = remember { mutableStateOf(false) }
    // Ended at the last LOADED page: the video sits ended while the next page
    // fetches — an ended video fires no further ended event, so the advance
    // must be re-driven when the page lands (user: "keep swiping until there
    // is no more page").
    val pendingAdvance = remember { mutableStateOf(false) }

    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // pinch zoom: resets on swipe (PLAN §9 revised 2026-09-30) — keyed per page
    val zoom = remember(pagerState.currentPage) { mutableFloatStateOf(1f) }
    val pan = remember(pagerState.currentPage) { mutableStateOf(Offset.Zero) }

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
    // mute applies to the single active player instance (PLAN §9)
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    // error surface + end-of-media: one listener on the shared player
    val pagerScope = rememberCoroutineScope()
    DisposableEffect(player, autoSwipe, dataSaver) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playError = true
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state != Player.STATE_ENDED) return
                    if (!(autoSwipe && !dataSaver)) {
                        // no auto-swipe: loop the video (user request 2026-09-30)
                        player.seekTo(0)
                        player.play()
                        return
                    }
                    // NOTE: this block was once wrapped in a bare lambda — a bare
                    // `{ }` is an expression statement and never runs, so the
                    // auto-advance silently no-op'd (live-proven: video parked at
                    // end, watch_history frozen).
                    val next = pagerState.currentPage + 1
                    if (next < items.itemCount) {
                        skipResume.value = true
                        // instant when the system reduced-motion scale is 0
                        val reduced =
                            android.provider.Settings.Global.getFloat(
                                context.contentResolver,
                                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                                1f,
                            ) == 0f
                        pagerScope.launch {
                            if (reduced) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
                        }
                    } else {
                        // end of loaded pool: ask paging for the next page;
                        // the LaunchedEffect below advances when it lands.
                        pendingAdvance.value = true
                        items.retry()
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // likes are refreshed per use (PLAN §5)
    LaunchedEffect(Unit) { viewModel.syncLikes() }
    // Advance past the pool end once the next page lands (or loop when the
    // server says there is no more — see pendingAdvance + the ended listener).
    LaunchedEffect(pendingAdvance.value, items.itemCount, items.loadState.append) {
        if (!pendingAdvance.value) return@LaunchedEffect
        val next = pagerState.currentPage + 1
        when {
            next < items.itemCount -> {
                pendingAdvance.value = false
                skipResume.value = true
                val reduced =
                    android.provider.Settings.Global.getFloat(
                        context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ) == 0f
                if (reduced) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
            }
            (items.loadState.append as? androidx.paging.LoadState.NotLoading)?.endOfPaginationReached == true ->
                {
                    pendingAdvance.value = false
                    if (shuffleSeed != 0L && items.itemCount > 1) {
                        // §8 infinite shuffle player: pool exhausted → jump to a
                        // random item (loop-the-last is the unshuffled behavior;
                        // the pager's APPEND keeps the pool growing while it lasts).
                        val rnd = java.util.Random(System.nanoTime())
                        var target = rnd.nextInt(items.itemCount)
                        if (target == pagerState.currentPage) {
                            target = (target + 1) % items.itemCount
                        }
                        pagerScope.launch { pagerState.scrollToPage(target) }
                    } else {
                        // truly no more pages: loop the last video (black ended
                        // frame otherwise)
                        player.seekTo(0)
                        player.play()
                    }
                }
            else -> items.retry()
        }
    }
    // adjacent-item prefetch (PLAN §9): prepare next/prev on settle
    LaunchedEffect(pagerState.currentPage, dataSaver) {
        val page = pagerState.currentPage
        val neighbors =
            buildList {
                if (page > 0) add(items[page - 1])
                if (page + 1 < items.itemCount) add(items[page + 1])
            }
        player.preloadNeighbors(neighbors.filterNotNull(), dataSaver)
    }
    // errors belong to the current item; a swipe resets the overlay + the
    // pending pool-end advance (a manual swipe supersedes it)
    LaunchedEffect(pagerState.currentPage) {
        playError = false
        pendingAdvance.value = false
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

    var sheetFor by remember { mutableStateOf<Gif?>(null) }

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
            // Soak-found crash (2026-10-01): during a paging refresh the
            // presenter list can momentarily empty while the pager still
            // composes page 0 — items.get(page) throws out-of-bounds. Guard
            // bounds first; the null check below only catches placeholders.
            if (page >= items.itemCount) return@VerticalPager
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
                    scrubbing = scrubbing,
                    onScrubbing = { scrubbing = it },
                    scrubPositionMs = scrubPositionMs,
                    onScrub = { scrubPositionMs = it },
                    positionMs = positionMs,
                    durationMs = durationMs,
                    zoom = zoom.floatValue,
                    pan = pan.value,
                    onZoom = { z, p ->
                        zoom.floatValue = z.coerceIn(1f, 3f)
                        pan.value = p
                    },
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    liked = gif.id in likedIds,
                    onToggleLike = {
                        if (isLoggedIn) viewModel.toggleLike(gif.id) else onOpenAccount()
                    },
                    muted = muted,
                    onToggleMute = { on -> muteScope.launch { settings.setMuted(on) } },
                    onShare = { shareGif(context, gif) },
                    onOverflow = { sheetFor = gif },
                    autoSwipeOn = autoSwipe && !dataSaver,
                    onToggleAutoSwipe = { muteScope.launch { settings.setAutoSwipe(!autoSwipe) } },
                    skipResume = skipResume,
                    playError = playError && page == pagerState.currentPage,
                    onRetry = {
                        playError = false
                        player.playGif(gif, dataSaver)
                    },
                    onSkip = { playError = false },
                    videoFit = videoFit,
                    onOpenCreator = {
                        viewModel.open(FeedSource.Creator(username = gif.userName))
                        onBack()
                    },
                    onOpenTag = { tag ->
                        viewModel.open(FeedSource.Search(query = tag))
                        onBack()
                    },
                )
            }
        }
    }

    val sheetGif = sheetFor
    if (sheetGif != null) {
        QuickBlockSheet(
            gif = sheetGif,
            onDismiss = { sheetFor = null },
            viewModel = viewModel,
            showSpeed = true,
            currentSpeed = speed,
            onSpeedChange = { newSpeed ->
                speed = newSpeed
                player.setPlaybackSpeed(newSpeed)
            },
            onOpenFeed = onBack,
        )
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
    liked: Boolean,
    onToggleLike: () -> Unit,
    muted: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    skipResume: androidx.compose.runtime.MutableState<Boolean>,
    autoSwipeOn: Boolean,
    onToggleAutoSwipe: () -> Unit,
    playError: Boolean,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    videoFit: String,
    /** Links audit: cluster @user / tag chips navigate (player closes). */
    onOpenCreator: () -> Unit = {},
    onOpenTag: (String) -> Unit = {},
) {
    // React to the shared player's media swaps (attach gating below).
    val playingId by player.currentGifId.collectAsStateWithLifecycle()
    val videoFitMode =
        when (videoFit) {
            "crop" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    var popAt by remember { mutableStateOf<Offset?>(null) }
    if (active) {
        // resume: stored position per gif, EXCEPT (a) auto-advance always starts
        // at 0, and (b) fully-watched gifs restart at 0 — a near-end resume would
        // hit STATE_ENDED instantly and cascade auto-swipes mid-video
        // (keyed on gif.id only, so the flag flip doesn't restart playback)
        LaunchedEffect(gif.id, dataSaver) {
            val history = watchHistory.byGif(gif.id)
            val resume =
                when {
                    skipResume.value -> {
                        skipResume.value = false
                        0L
                    }
                    history?.watched == true -> 0L
                    else -> history?.positionMs ?: 0L
                }
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
                // two-finger pinch zoom + pan (PLAN §9); zoom resets per page.
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
                // single tap: reveal UI when hidden, pause/play when shown;
                // double tap: like/unlike + heart pop (PLAN §9). Non-consuming
                // (no detectTapGestures) — it would eat the down event and
                // starve the VerticalPager's drag gesture.
                .pointerInput(controlsVisible) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up =
                            waitForUpOrCancellation()
                                ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() >=
                            viewConfiguration.touchSlop
                        ) {
                            return@awaitEachGesture
                        }
                        val secondDown =
                            withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis.toLong()) {
                                awaitFirstDown(requireUnconsumed = false)
                            }
                        val secondUp = secondDown?.let { waitForUpOrCancellation() }
                        if (secondDown != null &&
                            secondUp != null &&
                            (secondUp.position - secondDown.position).getDistance() <
                            viewConfiguration.touchSlop
                        ) {
                            // PLAN §9: double-tap = LIKE (never unlike) —
                            // un-like stays a rail action only.
                            if (!liked) onToggleLike()
                            popAt = down.position
                        } else {
                            if (controlsVisible) {
                                if (player.isPlaying) player.pause() else player.playOrRestart()
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
                    // §5 video fit: user choice (Fit default · Crop · Stretch).
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                // Attach ONLY when the shared player is already (about to be)
                // playing THIS page's gif — otherwise the new page renders the
                // previous video's last frame for a beat (user report).
                view.player = if (active && playingId == gif.id) player else null
                view.resizeMode = videoFitMode
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
        // Bottom gradient scrim (audit H5 / AGENTS-UX-PATTERNS): text and rail
        // stay legible on bright content; fades with the controls.
        AnimatedVisibility(
            visible = controlsVisible && active,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to androidx.compose.ui.graphics.Color.Transparent,
                                0.65f to androidx.compose.ui.graphics.Color.Transparent,
                                1f to
                                    androidx.compose.ui.graphics.Color.Black
                                        .copy(alpha = 0.55f),
                            ),
                        ),
            )
        }
        // creator chip + description + tags row (PLAN §9 bottom-left cluster).
        // Tags display-only for now — tappable when tag feeds land.
        // Auto-hide: fades out with the controls — only the thin progress
        // line stays visible (user request 2026-09-30).
        AnimatedVisibility(
            visible = controlsVisible && active,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, bottom = 64.dp, end = 96.dp),
            ) {
                gif.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (gif.tags.isNotEmpty()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Links audit #2: tags are FeedSource.Search(query = tag).
                        gif.tags.take(6).forEach { tag ->
                            Text(
                                text = tag,
                                color = GiffyColors.Lime,
                                style = MaterialTheme.typography.labelMedium,
                                modifier =
                                    Modifier
                                        .clickable {
                                            onOpenTag(tag)
                                        }.background(Color.Transparent, RoundedCornerShape(999.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CreatorLabel(
                        gif.userName,
                        gif.verified,
                        tint = Color.White,
                        tickTint = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        tickSize = 16.dp,
                        onClick = onOpenCreator,
                    )
                }
            }
        }
        // right action rail (PLAN §9): like / mute / share / overflow,
        // inset 72dp from the right edge, hides with the overlay
        AnimatedVisibility(
            visible = controlsVisible && active,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            ActionRail(
                hasAudio = gif.hasAudio,
                liked = liked,
                muted = muted,
                autoSwipeOn = autoSwipeOn,
                onToggleLike = onToggleLike,
                onToggleMute = { onToggleMute(!muted) },
                onShare = onShare,
                onOverflow = onOverflow,
                onToggleAutoSwipe = onToggleAutoSwipe,
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 24.dp, bottom = 96.dp),
            )
        }
        popAt?.let { popPos ->
            val pop = remember(popPos) { Animatable(0.6f) }
            LaunchedEffect(popPos) {
                pop.animateTo(1.5f, tween(120))
                pop.animateTo(1f, tween(180))
                popAt = null
            }
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = GiffyColors.Lime,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                (popPos.x - 24.dp.roundToPx()).roundToInt(),
                                (popPos.y - 24.dp.roundToPx()).roundToInt(),
                            )
                        }.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            alpha = 1f - (pop.value - 1f) / 0.5f
                        },
            )
        }
        if (playError && active) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playback failed", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // TextButtons: ≥48dp touch target (audit H6 — bare Text was ~36dp).
                    TextButton(
                        onClick = onRetry,
                        colors =
                            androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = GiffyColors.Lime,
                            ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Retry", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = onSkip,
                        colors =
                            androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                            ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Skip", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        PlayerControls(
            visible = controlsVisible && active,
            isPlaying = player.isPlaying,
            onTogglePlay = {
                if (player.isPlaying) player.pause() else player.playOrRestart()
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
        }

        // fixed 24dp slot under the controls row — the thin bar fades in/out
        // inside it, so nothing shifts when the indicators swap (drift-free,
        // user feedback 2026-09-30). One progress indicator at a time.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // edge to edge when the overlay is hidden (user request)
                    .height(24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            androidx.compose.animation.AnimatedVisibility(visible = !visible, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(trackColor, RoundedCornerShape(3.dp)),
                ) {
                    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
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
}

/** Applies the seek position when the scrub gesture finishes. */
private fun formatRemaining(
    positionMs: Long,
    durationMs: Long,
): String {
    val remaining = max(0, durationMs - positionMs) / 1000
    return "-${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
}

/** Right-edge vertical action rail (PLAN §9 borrowed action set). */
@Composable
private fun ActionRail(
    hasAudio: Boolean,
    liked: Boolean,
    muted: Boolean,
    autoSwipeOn: Boolean,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    onToggleAutoSwipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // like: white heart, lime when liked (site pattern). Live region so
        // TalkBack announces the optimistic outcome (audit finding 7).
        IconButton(
            onClick = onToggleLike,
            modifier =
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Liked — tap to unlike" else "Like",
                tint = if (liked) GiffyColors.Lime else Color.White,
            )
        }
        if (hasAudio) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    // Site lingo (§9, 2026-10-01): Sound On / Sound Off.
                    contentDescription = if (muted) "Sound On" else "Sound Off",
                    tint = Color.White,
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "share", tint = Color.White)
        }
        IconButton(onClick = onOverflow) {
            Icon(Icons.Filled.MoreVert, contentDescription = "more actions", tint = Color.White)
        }
        IconButton(onClick = onToggleAutoSwipe) {
            Icon(
                imageVector = Icons.Filled.FastForward,
                contentDescription = if (autoSwipeOn) "auto-swipe on" else "auto-swipe off",
                tint = if (autoSwipeOn) GiffyColors.Lime else Color.White,
            )
        }
    }
}

/** System share sheet — allowed per PLAN §0 (share only, no deep links in-app). */
private fun shareGif(
    context: android.content.Context,
    gif: Gif,
) {
    val intent =
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, com.rjbiermann.giffyviewer.core.model.Hosts.watch + gif.id)
        }
    context.startActivity(android.content.Intent.createChooser(intent, "Share"))
}
