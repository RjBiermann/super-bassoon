package com.rjbiermann.giffyviewer.tv

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
        inversePrimary = c.secondary, // lime
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
        surface = com.rjbiermann.giffyviewer.core.ui.GiffyColors.Widget,
        onSurface = c.onSurface,
        surfaceVariant = com.rjbiermann.giffyviewer.core.ui.GiffyColors.Widget,
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

/** Same DM Sans text styles as mobile — only the tv-material locals change. */
fun tvTypography(t: androidx.compose.material3.Typography): androidx.tv.material3.Typography =
    androidx.tv.material3.Typography(
        displayLarge = t.displayLarge,
        displayMedium = t.displayMedium,
        displaySmall = t.displaySmall,
        headlineLarge = t.headlineLarge,
        headlineMedium = t.headlineMedium,
        headlineSmall = t.headlineSmall,
        titleLarge = t.titleLarge,
        titleMedium = t.titleMedium,
        titleSmall = t.titleSmall,
        bodyLarge = t.bodyLarge,
        bodyMedium = t.bodyMedium,
        bodySmall = t.bodySmall,
        labelLarge = t.labelLarge,
        labelMedium = t.labelMedium,
        labelSmall = t.labelSmall,
    )
