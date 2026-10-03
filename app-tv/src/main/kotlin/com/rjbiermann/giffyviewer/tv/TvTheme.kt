package com.rjbiermann.giffyviewer.tv

import com.rjbiermann.giffyviewer.core.ui.DmSans
import com.rjbiermann.giffyviewer.core.ui.GiffyColors

/**
 * FULL borrowed-palette mapping for androidx.tv.material3 (PLAN §9): every
 * color and text style from the same tokens the mobile theme uses — tv-material
 * otherwise leaks its default light colors and Roboto typography (the TV must
 * look like the mobile app, 10-foot sizes aside).
 */
fun giffyTvColors(c: androidx.compose.material3.ColorScheme): androidx.tv.material3.ColorScheme =
    androidx.tv.material3.darkColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        secondary = c.secondary,
        onSecondary = c.onSecondary,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        tertiary = c.tertiary,
        onTertiary = c.onTertiary,
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.onTertiaryContainer,
        background = c.background,
        onBackground = c.onBackground,
        // Surface roles come from the compose scheme's containers — NOT a
        // hardcoded GiffyColors.Widget — so AMOLED collapses to true black on
        // the TV too (audit batch 15, F3).
        surface = c.surfaceContainer,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariant,
        surfaceTint = c.primary,
        inverseSurface = c.inverseSurface,
        inverseOnSurface = c.inverseOnSurface,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
        border = GiffyColors.OutlineSoft,
        borderVariant = GiffyColors.BrandRed,
    )

/**
 * tv-material's own default token scale (verified: identical to the M3 scale —
 * Title 22/16/14, Label 14/12/11 …), with ONLY the family swapped to DM Sans.
 * No more phone-sized copy of the mobile Typography (audit batch 15, F1).
 */
fun tvTypography(): androidx.tv.material3.Typography {
    val d = androidx.tv.material3.Typography()
    return androidx.tv.material3.Typography(
        displayLarge = d.displayLarge.copy(fontFamily = DmSans),
        displayMedium = d.displayMedium.copy(fontFamily = DmSans),
        displaySmall = d.displaySmall.copy(fontFamily = DmSans),
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
