package com.rjbiermann.giffyviewer.core.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette — original to Giffy Viewer (no upstream trade dress). Fallback for
// pre-Android-12 devices where dynamic color doesn't exist.
private val Amber = Color(0xFFFFB300)
private val AmberDim = Color(0xFFB28400)
private val NearBlack = Color(0xFF0A0A0C)
private val SurfaceDark = Color(0xFF141419)
private val SurfaceAMOLED = Color(0xFF000000)

/** M3 fallback scheme; [amoled] gives the true-black option (PLAN §9). */
fun giffyColors(amoled: Boolean = false): ColorScheme =
    darkColorScheme(
        primary = Amber,
        onPrimary = NearBlack,
        secondary = AmberDim,
        background = if (amoled) SurfaceAMOLED else NearBlack,
        surface = if (amoled) SurfaceAMOLED else SurfaceDark,
        surfaceVariant = SurfaceDark,
    )

/**
 * Material 3 theming (m3.material.io): dynamic color on Android 12+ (wallpaper-
 * derived scheme), brand fallback below, always dark-first, AMOLED option.
 */
@Composable
fun GiffyTheme(
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            giffyColors(amoled)
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
