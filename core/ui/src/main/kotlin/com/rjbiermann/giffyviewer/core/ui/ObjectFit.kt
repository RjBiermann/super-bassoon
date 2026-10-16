package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.layout.ContentScale

/**
 * Tile poster scale (issue #28): the §5 video-fit pref (fit | crop) governs
 * tiles too, so a tile's poster, its inline/preview surface, and the opened
 * player all render the same framing. Mirrors the SettingsRepository contract —
 * the legacy "stretch" value de-maps to fit upstream already, and anything
 * unknown (or absent) reads as the pref's default fit, the same else-branch
 * the player screens use for RESIZE_MODE_FIT.
 */
fun String?.toTileContentScale(): ContentScale = if (this == "crop") ContentScale.Crop else ContentScale.Fit
