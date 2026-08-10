package com.disciplineos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * DisciplineOS Compose theme. Design-system pass — see Color.kt's kdoc for the full
 * spec-grounded reasoning (Onboarding spec §5's deferred "visual design system" follow-on).
 *
 * Dark-only, deliberately, at least for this pass: this app is used to restrict a phone during
 * focused work — a light theme wasn't requested by any spec section, and Color.kt's contrast
 * math was verified against the dark palette specifically. `isSystemInDarkTheme()`-driven
 * light/dark switching (the usual Compose default, per Google's own theming guide) is a
 * reasonable follow-up once a light palette is deliberately designed and contrast-checked the
 * same way — not silently assumed to be "the same colors, inverted."
 *
 * Dynamic color (Material You wallpaper-derived theming, available API 31+) is deliberately
 * NOT used here even though it's the modern platform default for most apps: Color.kt's palette
 * is a specific, considered choice grounded in the product's own anti-gamification/anti-alarm
 * constraints (§3.5, §2.3) — letting a user's wallpaper inject an arbitrary accent hue would
 * undermine that, and could easily reintroduce exactly the red/yellow/green severity coding the
 * spec explicitly rules out. Revisit only if a future spec pass explicitly asks for it.
 */
private val DisciplineOsDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,

    // No distinct secondary/tertiary key colors defined — Color.kt's kdoc is explicit that
    // this system has exactly ONE deliberate accent (the calibrated-instrument constraint).
    // Falling through to Accent/OnAccent for secondary/tertiary keeps every M3 component that
    // reads those roles visually consistent with Primary rather than introducing an
    // unconsidered second hue by omission.
    secondary = Accent,
    onSecondary = OnAccent,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = OnAccentContainer,
    tertiary = Accent,
    onTertiary = OnAccent,
    tertiaryContainer = AccentContainer,
    onTertiaryContainer = OnAccentContainer,

    background = Background,
    onBackground = OnSurface,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,

    outline = Outline,
    outlineVariant = OutlineVariant,

    error = Error,
    onError = OnError,
    errorContainer = Error,
    onErrorContainer = OnError,
)

/**
 * Shape scale. Slightly restrained corner radii (vs. M3's more rounded defaults) — sharper
 * corners read as more instrument/tool-like, consistent with Color.kt's calibrated-dial intent,
 * without going all the way to fully square (which would fight Material's own touch-target and
 * ripple-shape expectations for no real gain).
 */
private val DisciplineOsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * Wrap any Compose content (typically a single Fragment's ComposeView content, per the
 * incremental interop migration this pass establishes — see app/build.gradle.kts's
 * buildFeatures.compose comment) in this to apply the DisciplineOS design system.
 *
 * Usage in a Fragment:
 * ```
 * composeView.setContent {
 *     DisciplineOsTheme {
 *         FirstMissionSchedulingScreen(...)
 *     }
 * }
 * ```
 */
@Composable
fun DisciplineOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DisciplineOsDarkColorScheme,
        typography = DisciplineOsTypography,
        shapes = DisciplineOsShapes,
        content = content,
    )
}
