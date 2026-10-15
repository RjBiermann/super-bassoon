package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Issue-#7 theme tokens (DESIGN-theme-stream.md §1.1, transcribed verbatim
 * from the issue's token table, dark-first seed #E5194D / background seed
 * #161617). Single color source feeding `giffyColors`; composables read
 * scheme ROLES only (batch-15 F5 rule).
 */
object GiffyColors {
    /** Issue seed — reserved for BRAND MOMENTS ONLY (splash/hero/logo); it
     *  never enters the ColorScheme (in-scheme dark accent is #FF668F). */
    val Seed = Color(0xFFE5194D)
    val Primary = Color(0xFFFF668F)
    val OnPrimary = Color(0xFF5E0B25)
    val PrimaryContainer = Color(0xFF7D0030)
    val OnPrimaryContainer = Color(0xFFFFD9E1)
    val InversePrimary = Color(0xFFB3003C)
    val Secondary = Color(0xFFE3BDC4)
    val OnSecondary = Color(0xFF432930)
    val SecondaryContainer = Color(0xFF5E4349)
    val OnSecondaryContainer = Color(0xFFFBD9E1)
    val Tertiary = Color(0xFFE7C16B)
    val OnTertiary = Color(0xFF3F2D00)
    val TertiaryContainer = Color(0xFF5B4200)
    val OnTertiaryContainer = Color(0xFFFFE08D)
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Background = Color(0xFF161617)
    val OnSurface = Color(0xFFF7E9EC)
    val Surface = Color(0xFF161617)
    val SurfaceVariant = Color(0xFF3E2429)
    val OnSurfaceVariant = Color(0xFFDCA1AB)
    val Outline = Color(0xFF9C7A80)
    val OutlineVariant = Color(0xFF51444A)
    val InverseSurface = Color(0xFFF7E9EC)
    val InverseOnSurface = Color(0xFF37292C)
}

/**
 * Issue-#7 darkColorScheme (DESIGN-theme-stream.md §1.1/§1.2): the issue's
 * token table verbatim. Rows the issue does NOT specify (surfaceContainer*
 * ladder, surfaceTint, onBackground/onSurface defaults) stay at the standard
 * M3 darkColorScheme derivations — nothing invented. AMOLED keeps today's
 * collapse SHAPE: background/surface + surfaceVariant + the whole surface
 * container ladder to pure black; accent + on-color rows never collapse.
 */
private fun giffyColors(amoled: Boolean): ColorScheme {
    val scheme =
        darkColorScheme(
            primary = GiffyColors.Primary,
            onPrimary = GiffyColors.OnPrimary,
            primaryContainer = GiffyColors.PrimaryContainer,
            onPrimaryContainer = GiffyColors.OnPrimaryContainer,
            inversePrimary = GiffyColors.InversePrimary,
            secondary = GiffyColors.Secondary,
            onSecondary = GiffyColors.OnSecondary,
            secondaryContainer = GiffyColors.SecondaryContainer,
            onSecondaryContainer = GiffyColors.OnSecondaryContainer,
            tertiary = GiffyColors.Tertiary,
            onTertiary = GiffyColors.OnTertiary,
            tertiaryContainer = GiffyColors.TertiaryContainer,
            onTertiaryContainer = GiffyColors.OnTertiaryContainer,
            error = GiffyColors.Error,
            onError = GiffyColors.OnError,
            errorContainer = GiffyColors.ErrorContainer,
            onErrorContainer = GiffyColors.OnErrorContainer,
            background = GiffyColors.Background,
            onBackground = GiffyColors.OnSurface,
            surface = GiffyColors.Surface,
            onSurface = GiffyColors.OnSurface,
            surfaceVariant = GiffyColors.SurfaceVariant,
            onSurfaceVariant = GiffyColors.OnSurfaceVariant,
            outline = GiffyColors.Outline,
            outlineVariant = GiffyColors.OutlineVariant,
            inverseSurface = GiffyColors.InverseSurface,
            inverseOnSurface = GiffyColors.InverseOnSurface,
            scrim = Color.Black,
        )
    if (!amoled) return scheme
    val black = Color(0xFF000000)
    return scheme.copy(
        background = black,
        surface = black,
        surfaceVariant = black,
        surfaceContainerLowest = black,
        surfaceContainerLow = black,
        surfaceContainer = black,
        surfaceContainerHigh = black,
        surfaceContainerHighest = black,
    )
}

val DmSans =
    FontFamily(
        Font(R.font.dm_sans_regular, FontWeight.Normal),
        Font(R.font.dm_sans_medium, FontWeight.Medium),
        Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
        Font(R.font.dm_sans_bold, FontWeight.Bold),
    )

/**
 * Standard M3 type scale (m3.material.io), DM Sans family on every slot —
 * the borrowed sizes drift (headline 32/24/20, title 18/…, label 10, 1sp
 * tracking) are gone; slots ride the scale defaults (audit batch 15, F2).
 * Display slots stay 32 (nothing renders display today; correct when used).
 */
private fun dmSansTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = DmSans, fontSize = 32.sp),
        displayMedium = d.displayMedium.copy(fontFamily = DmSans, fontSize = 32.sp),
        displaySmall = d.displaySmall.copy(fontFamily = DmSans, fontSize = 32.sp),
        headlineLarge = d.headlineLarge.copy(fontFamily = DmSans),
        headlineMedium = d.headlineMedium.copy(fontFamily = DmSans),
        headlineSmall = d.headlineSmall.copy(fontFamily = DmSans),
        titleLarge = d.titleLarge.copy(fontFamily = DmSans),
        titleMedium = d.titleMedium.copy(fontFamily = DmSans),
        titleSmall = d.titleSmall.copy(fontFamily = DmSans),
        bodyLarge = d.bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = d.bodyMedium.copy(fontFamily = DmSans),
        bodySmall = d.bodySmall.copy(fontFamily = DmSans),
        labelLarge = d.labelLarge.copy(fontFamily = DmSans),
        labelMedium = d.labelMedium.copy(fontFamily = DmSans),
        labelSmall = d.labelSmall.copy(fontFamily = DmSans),
    )
}

/** M3 shape scale made explicit (audit batch 15, F7): 4/8/12/16/28/full. */
private val GiffyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * Material 3 theming (m3.material.io): dark-first issue-#7 theme, AMOLED
 * true-black option. Dynamic color is DISABLED by the issue rebrand
 * (§1.5, DESIGN-theme-stream.md): seed-derived neutrality is no longer
 * user-selectable; the seed #E5194D stays reserved for brand moments.
 */
@Composable
fun GiffyTheme(
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = giffyColors(amoled),
        typography = dmSansTypography(),
        shapes = GiffyShapes,
        content = content,
    )
}
