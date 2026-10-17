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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.VideoQuality
import com.rjbiermann.giffyviewer.core.model.contentAspectRatio
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.player.seedContentAspectRatio
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.PlayerOverlay
import com.rjbiermann.giffyviewer.core.ui.toTileContentScale
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
 * plays; every play starts at 0 (watch-history/resume removed with the feature,
 * 2026-10 stream); SimpleCache writes under the
 * gif ID. Controls: single tap reveals the overlay / pauses, auto-hide after
 * idle, always-on thin progress bar with remaining-time chip, draggable scrub, two-finger pinch zoom (1x–3x) that
 * resets on swipe (PLAN §9 revised 2026-09-30), and the right action rail
 * (like / mute / share / overflow).
 */
private const val IDLE_HIDE_MS = 3_000L

/** Hold-to-2× (UX-PATTERNS candidate): long-press on the player body = 2× while held. */
private const val HOLD_SPEED = 2f

/**
 * Issue #51 (a11y-breaking): the reveal tap is a raw pointerInput gesture
 * TalkBack can't dispatch, and the auto-hidden overlay leaves composition
 * entirely — without this action the controls are unreachable for
 * screen-reader users until the video is re-fetched.
 */
internal fun Modifier.playerControlsSemantics(onReveal: () -> Unit): Modifier =
    semantics {
        customActions =
            listOf(
                CustomAccessibilityAction("Show playback controls") {
                    onReveal()
                    true
                },
            )
    }

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun PlayerScreen(
    startIndex: Int,
    onBack: () -> Unit,
    /** Like while logged out → open Settings (holds the account section). */
    onOpenAccount: () -> Unit,
    /** Issue #13 §3.3: cluster opens (creator/tag/niche from the overlay) are
     *  route pushes now — the host pops this player entry while pushing, so
     *  BACK lands the covered feed at its scroll position. The shared
     *  FeedViewModel keeps paging this route's source meanwhile. */
    onOpenFeed: (FeedSource) -> Unit,
    viewModel: FeedViewModel,
    playerFactory: GiffyPlayerFactory,
    settings: SettingsRepository,
    db: GiffyDatabase,
) {
    val items = viewModel.gifs.collectAsLazyPagingItems()
    // §8 shuffle player: read this feed's shuffle seed (random end-of-pool jumps).
    // remember: Flow operators must not re-run per recomposition (lint).
    val shuffleSeedFlow =
        remember(viewModel.source) {
            viewModel.source
                .flatMapLatest { viewModel.feedPrefs(it.baseKey) }
                .map { it.shuffleSeed }
        }
    val shuffleSeed by shuffleSeedFlow.collectAsStateWithLifecycle(0L)
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
    // Preferred quality (§5, issue #4): auto | highest | sd — the pick is the
    // stream URL at prepare time (probe-closed single-rendition MP4 vocabulary;
    // see VideoQuality.kt).
    val videoQuality by settings.videoQuality.collectAsStateWithLifecycle(VideoQuality.AUTO)
    val muteScope = rememberCoroutineScope()
    // Player adoption (issue #9): the shared inline preview player parks in the
    // FeedViewModel across the FeedScreen→PlayerScreen route swap (a `when`
    // branch change, not navigation). Adopt if offered — codec init + buffer
    // stay warm; a fresh player is built when nothing is parked (2/3-col
    // grids, autoplay off, data saver). The imperative remember holds the
    // player AND whether it was adopted; the dispose below releases or
    // returns accordingly. A GiffyPlayer must never reach saveable state —
    // process death mid-adoption legitimately falls back to a fresh player.
    val playerHold =
        remember {
            viewModel.recallInlinePlayer()?.let { it to true } ?: (playerFactory.create(context) to false)
        }
    val player = playerHold.first

    // playback speed: session-only — resets when the player is released (PLAN §9).
    // holdSpeed (hold-to-2×) overrides it while the long-press is held; the single
    // LaunchedEffect below is the only speed application point.
    var speed by remember { mutableFloatStateOf(1f) }
    var holdSpeed by remember { mutableStateOf(false) }
    LaunchedEffect(speed, holdSpeed) {
        player.setPlaybackSpeed(if (holdSpeed) HOLD_SPEED else speed)
    }
    // playback failure overlay (PLAN §9): Retry re-resolves, Skip advances
    var playError by remember { mutableStateOf(false) }
    // Ended at the last LOADED page: the video sits ended while the next page
    // fetches — an ended video fires no further ended event, so the advance
    // must be re-driven when the page lands (user: "keep swiping until there
    // is no more page").
    val pendingAdvance = remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // pinch zoom: resets on swipe (PLAN §9 revised 2026-09-30) — keyed per page
    val zoom = remember(pagerState.currentPage) { mutableFloatStateOf(1f) }
    val pan = remember(pagerState.currentPage) { mutableStateOf(Offset.Zero) }

    // immersive fullscreen player: hide the system bars once, restore on dispose
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.let {
            val controller = WindowCompat.getInsetsController(it, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val (p, adopted) = playerHold
            // Adopted player → return to the VM (parks it for the feed again,
            // issue #9); a player built here releases as before (no owner).
            if (adopted) viewModel.returnInlinePlayer(p) else p.release()
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
    // mute applies to the single active player instance (PLAN §9); keyed on the
    // player too so an adopted inline preview (issue #9, volume 0) gets the
    // user's pref re-asserted on entry.
    LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }
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
                    // auto-advance silently no-op'd (live-proven).
                    val next = pagerState.currentPage + 1
                    if (next < items.itemCount) {
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
    LaunchedEffect(pagerState.currentPage, dataSaver, videoQuality) {
        val page = pagerState.currentPage
        val neighbors =
            buildList {
                if (page > 0) add(items[page - 1])
                if (page + 1 < items.itemCount) add(items[page + 1])
            }
        player.preloadNeighbors(neighbors.filterNotNull(), dataSaver, videoQuality)
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    videoQuality = videoQuality,
                    playError = playError && page == pagerState.currentPage,
                    onRetry = {
                        playError = false
                        player.playGif(gif, dataSaver, videoQuality = videoQuality)
                    },
                    onSkip = { playError = false },
                    videoFit = videoFit,
                    onOpenCreator = {
                        onOpenFeed(FeedSource.Creator(username = gif.userName))
                    },
                    onOpenTag = { tag ->
                        onOpenFeed(FeedSource.Search(query = tag))
                    },
                    onOpenNiche = { id, name ->
                        onOpenFeed(FeedSource.Niche(id = id, name = name))
                    },
                    onHoldSpeed = { holdSpeed = it },
                )
            }
        }
        // hold-to-2× indicator (UX-PATTERNS candidate): shows while the
        // long-press hold is engaged; gone on release.
        if (holdSpeed) {
            Text(
                text = "2× speed",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(PlayerOverlay.scrim, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
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
            onSpeedChange = { newSpeed -> speed = newSpeed },
            onOpenFeed = onOpenFeed,
        )
    }
}

@Composable
private fun PlayerPage(
    gif: Gif,
    active: Boolean,
    player: GiffyPlayer,
    dataSaver: Boolean,
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
    liked: Boolean,
    onToggleLike: () -> Unit,
    muted: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    /** §5 preferred quality (auto | highest | sd) at prepare time. */
    videoQuality: String,
    autoSwipeOn: Boolean,
    onToggleAutoSwipe: () -> Unit,
    playError: Boolean,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    videoFit: String,
    /** Links audit: cluster @user / tag chips navigate (player closes). */
    onOpenCreator: () -> Unit = {},
    onOpenTag: (String) -> Unit = {},
    /** Links audit #5: cluster niche pills navigate to the niche feed. */
    onOpenNiche: (id: String, name: String) -> Unit = { _, _ -> },
    /** Hold-to-2×: engaged while the long-press is held on the player body. */
    onHoldSpeed: (Boolean) -> Unit = {},
) {
    // React to the shared player's media swaps (attach gating below).
    val playingId by player.currentGifId.collectAsStateWithLifecycle()
    // §5 video fit: user choice (Fit default · Crop). A legacy stored
    // "stretch" value de-maps to Fit (RESIZE_MODE_FILL removed, issue #21 —
    // destructive distortion mode retired; no data migration needed).
    val videoFitMode =
        when (videoFit) {
            "crop" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    var popAt by remember { mutableStateOf<Offset?>(null) }
    if (active) {
        // Every play starts at 0 (watch-history/resume removed with the
        // feature — #1 stream): keyed on gif.id so a swap re-fires it.
        LaunchedEffect(gif.id, dataSaver) {
            if (player.currentGifId.value == gif.id) {
                // Adopted inline preview already on THIS gif (tap-to-open on
                // the settled 1-col tile, issue #9): keep the live position and
                // buffer — no re-prepare. CONTINUE PLAYING: leaving the feed
                // correctly parks the inline player (FeedScreen's onDispose
                // parks = pause + re-armed preview profile), and the full-
                // screen adopt must resume from the live position — otherwise
                // every settled-tile open sits paused (device pass 2026-10-16;
                // unsettled-tile opens built a FRESH player and auto-played).
                player.playWhenReady = true
            } else {
                player.playGif(gif, dataSaver, resumeMs = 0L, videoQuality = videoQuality)
            }
            onPlayingChanged(true)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .playerControlsSemantics(onReveal = onShowControls)
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
                // single tap: TOGGLES UI VISIBILITY ONLY — never stops playback
                // (pause/play lives on the visible control row; user issue #2 —
                // TikTok-style tap-pause rejected). Always-on thin progress line
                // below. Double tap: like/unlike + heart pop (PLAN §9); hold:
                // 2× while held (UX-PATTERNS candidate). Non-consuming (no
                // detectTapGestures) — it would eat the down event and starve
                // the VerticalPager's drag gesture.
                .pointerInput(controlsVisible, active, liked) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        var upChange: PointerInputChange? = null
                        // long-press race: up / swipe-cancel vs the hold timeout.
                        // null = the pointer was still down when the timeout fired.
                        var cancel = false
                        val upInTime =
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id }
                                            ?: continue
                                    if (event.changes.any { it.isConsumed } ||
                                        (change.position - downPos).getDistance() >=
                                        viewConfiguration.touchSlop
                                    ) {
                                        // pager/pinch took the gesture — a swipe, not a hold
                                        cancel = true
                                        break
                                    }
                                    if (change.changedToUp()) {
                                        upChange = change
                                        break
                                    }
                                }
                                upChange != null
                            }
                        if (cancel) return@awaitEachGesture
                        if (upInTime == null) {
                            // still down after the long-press timeout: hold-to-2× —
                            // speed up while held, restore on release (player body
                            // only, never on tiles). A hold is never a tap.
                            if (active) {
                                onHoldSpeed(true)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id }
                                        if (event.changes.any { it.isConsumed } ||
                                            change == null ||
                                            change.changedToUp()
                                        ) {
                                            break
                                        }
                                    }
                                } finally {
                                    onHoldSpeed(false)
                                }
                            }
                            return@awaitEachGesture
                        }
                        val up = upChange ?: return@awaitEachGesture
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
                            if (controlsVisible) onHideControls() else onShowControls()
                        }
                    }
                },
    ) {
        // Poster + surface share the zoom subtree (issue #8): the poster is
        // a placeholder, not pixel-perfect (Fit framing may differ slightly
        // from the seeded/rendered video box — acceptable). It zooms WITH the
        // video so pinch-zoom never contrasts a static poster against a
        // zooming surface. Issue #41: the poster honors the same §5 video-fit
        // pref as the video surface (shared toTileContentScale mapping —
        // crop → Crop, everything else → Fit) so a crop user never watches the
        // un-cropped poster under the cropped video. Poster-null gifs: nothing
        // renders and the black background stays the truthful state (no
        // invented placeholder).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
        ) {
            AsyncImage(
                model = gif.posterUrl,
                contentScale = videoFit.toTileContentScale(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            AndroidView(
                factory = { ctx ->
                    // player_page_view.xml: texture_view + transparent shutter
                    // (issue #8) — transparent until the first video frame, so
                    // this poster shows through the whole prepare window.
                    (
                        android.view.LayoutInflater
                            .from(ctx)
                            .inflate(
                                com.rjbiermann.giffyviewer.feature.feed.R.layout.player_page_view,
                                null,
                                false,
                            ) as PlayerView
                    ).apply {
                        useController = false
                        // §5 video fit: user choice (Fit default · Crop);
                        // re-asserted per update below so the pref wins.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { view ->
                    // Leak H1/H2 (OOM sweep 2026-10-16): a page CAN leave
                    // composition while the AndroidView still holds the shared
                    // player (a generation reset swaps pages under an active
                    // page) — PlayerView detaches its ComponentListener +
                    // TextureView surface ONLY via setPlayer(null)/release(),
                    // so every attached dispose leaks a player-anchored view
                    // subtree. The update{} path's own if/else nulls stay
                    // untouched; adopt/park semantics unchanged.
                    view.player = null
                },
                update = { view ->
                    // Attach ONLY when the shared player is already (about to be)
                    // playing THIS page's gif — otherwise the new page renders the
                    // previous video's last frame for a beat (user report).
                    view.player = if (active && playingId == gif.id) player else null
                    view.resizeMode = videoFitMode
                    // SLICE-16 (stale-aspect guard, TV SLICE-14 parity): re-seed from
                    // the gif's own metadata on every attach/switch — an unknown
                    // gif aspect (0) CLEARS the frame's aspect instead of leaving
                    // the previous video's, so no frame renders with a stale
                    // aspect; the live videoSize update overrides when it arrives.
                    view.seedContentAspectRatio(gif.contentAspectRatio())
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                                0.45f to androidx.compose.ui.graphics.Color.Transparent,
                                // Issue #51 finding 5: the bottom text cluster sits
                                // ~64–214dp above the bottom edge — inside the old
                                // 0.65→1 ramp, so its text landed at ≈2–3.7:1 over
                                // bright artwork. Full scrim from the cluster's top
                                // down restores ≥4.5:1 for the onSurface text.
                                0.75f to PlayerOverlay.scrim,
                                1f to PlayerOverlay.scrim,
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
                        color = PlayerOverlay.secondaryOnVideo,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (gif.tags.isNotEmpty() || gif.niches.isNotEmpty()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Niche chips first (curated, bigger destination); names come
                        // with the payload (NicheRef) — fall back to the id.
                        gif.niches.take(3).forEach { niche ->
                            Text(
                                text = niche.name,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier =
                                    Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable {
                                            onOpenNiche(niche.id, niche.name)
                                        }.background(Color.Transparent, CircleShape)
                                        .border(1.dp, PlayerOverlay.track, CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        // Links audit #2 + style decision 2026-10-02: tags are plain
                        // lime text (secondary topics); niches keep the pill.
                        gif.tags.take(6).forEach { tag ->
                            Text(
                                text = tag,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier =
                                    Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable {
                                            onOpenTag(tag)
                                        },
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
                tint = MaterialTheme.colorScheme.secondary,
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
                        .background(PlayerOverlay.scrim, MaterialTheme.shapes.medium)
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
                                contentColor = MaterialTheme.colorScheme.secondary,
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
            onScrubFinished = { player.seekTo(it) },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

/** Bottom overlay: play/pause row, slider, always-on 3dp progress bar. */
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
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = PlayerOverlay.track
    val fill = MaterialTheme.colorScheme.secondary

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
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics {
                                // Issue #51: bare "NN%" gave no context —
                                // name the control and announce the position.
                                contentDescription = "Playback position"
                                stateDescription = formatRemaining(positionMs, durationMs)
                            },
                )
                Text(
                    text = formatRemaining(positionMs, durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                )
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
                            .background(trackColor, MaterialTheme.shapes.extraSmall),
                ) {
                    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(fill, MaterialTheme.shapes.extraSmall),
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
                tint = if (liked) MaterialTheme.colorScheme.secondary else Color.White,
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
                tint = if (autoSwipeOn) MaterialTheme.colorScheme.secondary else Color.White,
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
