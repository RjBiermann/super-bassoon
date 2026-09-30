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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Borrowed design tokens — PLAN §9 (exact values verified from the source
 * site's CSS via Playwright, 2026-09-30). Palette only, no trade dress.
 * AMOLED option collapses page/chrome/widget to true black.
 */
object GiffyColors {
    val Page = Color(0xFF0F0F0F)
    val Chrome = Color(0xFF090909)
    val Widget = Color(0xFF191919)
    val BrandRed = Color(0xFFD70003)
    val Lime = Color(0xFFEBFA63)
    val LimeHover = Color(0xFFDAF02B)
    val LimePressed = Color(0xFF92AB05)
    val TextHigh = Color(0xFFEFEEF0)
    val TextMid = Color(0xFFBAB9C0)
    val TextLow = Color(0xFF94939D)
    val OutlineSoft = Color(0x66FFFFFF)
    val Success = Color(0xFF00D3A3)
    val Error = Color(0xFFFF575A)
    val Info = Color(0xFF59C2E5)
    val Warning = Color(0xFFFFC815)
}

private fun giffyColors(amoled: Boolean): ColorScheme {
    val black = Color(0xFF000000)
    val page = if (amoled) black else GiffyColors.Page
    val chrome = if (amoled) black else GiffyColors.Chrome
    val widget = if (amoled) black else GiffyColors.Widget
    return darkColorScheme(
        primary = GiffyColors.BrandRed,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4A0001),
        onPrimaryContainer = GiffyColors.TextHigh,
        secondary = GiffyColors.Lime,
        onSecondary = Color(0xFF0F0F0F),
        secondaryContainer = Color(0xFF31331A),
        onSecondaryContainer = GiffyColors.Lime,
        tertiary = GiffyColors.Info,
        onTertiary = Color(0xFF0F0F0F),
        tertiaryContainer = Color(0xFF0E3A4C),
        onTertiaryContainer = GiffyColors.Info,
        background = page,
        onBackground = GiffyColors.TextHigh,
        surface = page,
        onSurface = GiffyColors.TextHigh,
        surfaceVariant = widget,
        onSurfaceVariant = GiffyColors.TextMid,
        surfaceContainerLowest = chrome,
        surfaceContainerLow = chrome,
        surfaceContainer = widget,
        surfaceContainerHigh = widget,
        surfaceContainerHighest = widget,
        outline = GiffyColors.OutlineSoft,
        outlineVariant = Color(0x1AFFFFFF),
        error = GiffyColors.Error,
        onError = Color(0xFF0F0F0F),
        inverseSurface = GiffyColors.TextHigh,
        inverseOnSurface = page,
        inversePrimary = GiffyColors.BrandRed,
    )
}

private val DmSans =
    FontFamily(
        Font(R.font.dm_sans_regular, FontWeight.Normal),
        Font(R.font.dm_sans_medium, FontWeight.Medium),
        Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
        Font(R.font.dm_sans_bold, FontWeight.Bold),
    )

/** PLAN §9 scale: headings 32/24/20/18/16/14, body 16/14/12/10, caption ls 1sp. */
private fun dmSansTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = DmSans, fontSize = 32.sp),
        displayMedium = d.displayMedium.copy(fontFamily = DmSans, fontSize = 32.sp),
        displaySmall = d.displaySmall.copy(fontFamily = DmSans, fontSize = 32.sp),
        headlineLarge = d.headlineLarge.copy(fontFamily = DmSans, fontSize = 32.sp),
        headlineMedium = d.headlineMedium.copy(fontFamily = DmSans, fontSize = 24.sp),
        headlineSmall = d.headlineSmall.copy(fontFamily = DmSans, fontSize = 20.sp),
        titleLarge = d.titleLarge.copy(fontFamily = DmSans, fontSize = 18.sp),
        titleMedium = d.titleMedium.copy(fontFamily = DmSans, fontSize = 16.sp),
        titleSmall = d.titleSmall.copy(fontFamily = DmSans, fontSize = 14.sp),
        bodyLarge = d.bodyLarge.copy(fontFamily = DmSans, fontSize = 16.sp),
        bodyMedium = d.bodyMedium.copy(fontFamily = DmSans, fontSize = 14.sp),
        bodySmall = d.bodySmall.copy(fontFamily = DmSans, fontSize = 12.sp),
        labelLarge = d.labelLarge.copy(fontFamily = DmSans, fontSize = 14.sp),
        labelMedium = d.labelMedium.copy(fontFamily = DmSans, fontSize = 12.sp, letterSpacing = 1.sp),
        labelSmall = d.labelSmall.copy(fontFamily = DmSans, fontSize = 10.sp, letterSpacing = 1.sp),
    )
}

/**
 * Material 3 theming (m3.material.io): dark-first borrowed theme (PLAN §9),
 * AMOLED true-black option, dynamic color as opt-in override on Android 12+.
 */
@Composable
fun GiffyTheme(
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            giffyColors(amoled)
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = dmSansTypography(),
        content = content,
    )
}
