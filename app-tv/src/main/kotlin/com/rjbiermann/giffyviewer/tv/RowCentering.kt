package com.rjbiermann.giffyviewer.tv

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * #14 §B — the CENTERED bring-into-view spec for LazyRows hosting focusable
 * cards: when a card gains focus its own focusable emits a bring-into-view
 * request and the row scrolls it to center — through the FOCUS pipeline,
 * never a programmatic scrollToItem.
 *
 * Device pass 2026-10-16 (TV home, F1 repro ×6): ANY programmatic
 * scrollToItem while an item is focused — a valid snap or an
 * impossible-then-clamped one — strips the item's a11y focus state and
 * focus indication (RIGHT/DOWN read focus-lost every step; only LEFT's
 * in-deadband steps re-armed; Compose logical focus itself survives —
 * Center still opened the card). The bring-into-view pipeline is the
 * platform path for focus-driven scrolls and does not disturb focus.
 * [snap] keeps the #14 "no per-step animation lag" rule; the deadband
 * (±[deadbandPx]) keeps small focus steps from jittering.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun centeredBringIntoViewSpec(deadbandPx: Int): BringIntoViewSpec =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float {
            val distance = offset - (containerSize - size) / 2f
            return if (abs(distance) <= deadbandPx) 0f else distance
        }

        override val scrollAnimationSpec: AnimationSpec<Float> = snap()
    }

/** Provides [centeredBringIntoViewSpec] to the LazyRow it wraps. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CenteredRow(
    deadband: Dp,
    content: @Composable () -> Unit,
) {
    val deadbandPx = with(LocalDensity.current) { deadband.roundToPx() }
    val spec = remember(deadbandPx) { centeredBringIntoViewSpec(deadbandPx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}
