package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable

/**
 * The one adaptive-layout seam (PLAN §9 phone+tablet bullet): the app shell
 * computes [WindowWidthSizeClass] once and derives this hint; screens consume
 * the hint instead of scatter-gunned width checks. Foldables/unfold recompute
 * on resize like any rotation.
 */
data class LayoutHint(
    /** Feed grid column count (§6 Grid columns: 2 phone / 3 tablet+, overrideable). */
    val gridColumns: Int,
)

@Composable
fun layoutHint(
    widthSizeClass: WindowWidthSizeClass,
    userOverride: Int,
): LayoutHint {
    // §6 Grid columns responsive-first: Auto default = width-derived ladder
    // (compact 1 — the phone preference, media-first · medium 2 · expanded 3).
    val auto =
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> 1
            WindowWidthSizeClass.Medium -> 2
            else -> 3
        }
    return LayoutHint(
        gridColumns = if (userOverride > 0) userOverride else auto,
    )
}
