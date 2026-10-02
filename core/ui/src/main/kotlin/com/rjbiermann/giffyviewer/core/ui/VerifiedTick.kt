package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Creator verified tick (2026-10): the upstream checkmark seal, rendered next
 * to every @username the app shows (tiles, players, creator rows, quick
 * sheets). Uses the shared Info cyan — reads as "verified blue" on the dark
 * borrowed palette. Announced as "verified" (the adjacent username text alone
 * doesn't carry it).
 */
@Composable
fun VerifiedTick(
    modifier: Modifier = Modifier,
    tint: Color = GiffyColors.Info,
) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "verified creator",
        tint = tint,
        modifier = modifier,
    )
}

/** Default badge size (14dp) — inline with username labels. */
val VerifiedTickSize = 14.dp
