package com.disciplineos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DisciplineOS type scale. Design-system pass — see Color.kt's kdoc for the full spec-grounded
 * reasoning this file shares.
 *
 * Two families, both platform-default (zero bundled font files, zero licensing/bundling
 * decisions to make in this pass — a genuine simplification, not a corner cut; noted as a
 * deliberate v1 choice, not silently skipped):
 *
 * - [FontFamily.Monospace] for anything numeric/instrument-like: tier labels, countdowns,
 *   Debt/Reputation figures, step-progress indicators ("Step 3 of 8"). Reinforces the
 *   calibrated-instrument identity from Color.kt's kdoc — a monospace figure reads as a
 *   measurement, not a marketing headline.
 * - [FontFamily.SansSerif] for everything else: body copy, screen titles, button labels. Most
 *   of this app's actual content is careful, plain-spoken prose (see e.g.
 *   strings.xml's welcome_tone_body) — the type scale should stay legible and unshowy, not
 *   compete with that copy for attention.
 *
 * Material 3's own type scale (display/headline/title/body/label × large/medium/small) is used
 * as-is for the roles that map cleanly; this project's screens don't need the full 15-style
 * scale (most use title + body + label only, per the existing screens' own two-or-three-size
 * XML convention), so only the styles actually in use are overridden — the rest fall through to
 * Typography()'s own M3 defaults rather than being redundantly restated here.
 */
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val DisciplineOsTypography = Typography(
    // Screen titles ("Before you start", "Set up your first Mission")
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    // Card/section headers (tier labels, section labels like "Enforcement" / "Voice" / "Exit")
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    // Step progress ("Step 3 of 8") — monospace, reads as a counter/instrument readout
    titleSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    // Primary body copy
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    // Secondary/dense copy (card sub-sections, helper text)
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    // Button labels
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Small metadata labels (section headers like "ENFORCEMENT" in tier cards)
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
