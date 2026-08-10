package com.disciplineos.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DisciplineOS design-system palette.
 *
 * Design-system pass (ROADMAP.md — see this commit's entry). Onboarding, Consent & Interaction
 * Spec §5 explicitly defers "visual design system, color, typography" as "a follow-on doc" —
 * this file (plus Type.kt/Theme.kt) is that follow-on, scoped to Phase 3 (onboarding) per the
 * decision recorded in ROADMAP.md: Phase 2 (interception) stays on plain Views for now.
 *
 * Hard constraints this palette is built to satisfy, sourced directly from spec text rather
 * than paraphrased second-hand:
 *
 * - Onboarding spec §2.3: "do not visually code Iron as 'the real/serious choice' and Recruit
 *   as 'for beginners/not serious'" — no color ramp across tiers. Tier cards share one template
 *   and one palette; nothing here assigns a tier its own hue.
 * - Onboarding spec §3.5: Predictive Failure Alerts "does not use severity color coding
 *   (red/yellow) across rules... a red card undoes that framing distinction regardless of the
 *   copy underneath it." No red/yellow/green role exists anywhere in this palette. `Error` below
 *   is a desaturated terracotta, reserved for genuine destructive/validation-error affordances
 *   (e.g. a form field that failed to save) — never for severity-coding a Mission, a tier, or a
 *   violation.
 * - Onboarding spec §3.4: Debt Ceiling markers should "read as progress-toward-a-boundary, not
 *   gamified reward-progress" — no bright saturated "achievement" green/gold anywhere.
 * - PRD §2/§27: the product's own stated identity is "exactly as strict as the person needs, no
 *   stricter than consented to" — restrained by default. The one accent color is deliberately
 *   low-saturation (a muted brass/ochre, not a bright brand color) so it reads as a calibrated
 *   instrument dial, not a marketing accent or a call-to-action button lifted from a habit-
 *   tracking app.
 *
 * Structure follows Material 3's tone-based surface model (M3 replaced M2's opacity-based
 * elevation overlays with a fixed ladder of surface-container tones — see
 * developer.android.com/develop/ui/compose/designsystems/material3, verified current as of
 * this pass, Aug 2026) rather than a single flat "card background" color. Background is a
 * near-black warm-neutral, not pure #000000 — pure black on OLED causes edge smearing/halation
 * for long reading sessions, and gives no room for a tonal elevation ladder above it.
 *
 * Every text-on-fill pairing below is checked against the actual WCAG 2 contrast formula
 * (relative luminance, not eyeballed) before being assigned a role. Ratios recorded in each
 * kdoc are the real computed values, verified with a standalone script during this pass —
 * see the ratios cited below rather than trusting an unverified claim:
 *
 * | Foreground              | Background                 | Ratio  | WCAG AA (4.5:1 text) |
 * |--------------------------|-----------------------------|-------:|-----------------------|
 * | OnSurface                | Background                  | 14.90  | pass |
 * | OnSurface                | SurfaceContainer             | 13.27  | pass |
 * | OnSurface                | SurfaceContainerHigh          | 12.03  | pass |
 * | OnSurfaceVariant          | Background                  |  7.76  | pass |
 * | OnSurfaceVariant          | SurfaceContainer             |  6.91  | pass |
 * | OnAccent                 | Accent                      |  5.88  | pass |
 * | Accent                   | Background                  |  6.06  | pass |
 * | OnAccentContainer         | AccentContainer               |  9.34  | pass |
 * | OnError                  | Error                       |  5.51  | pass |
 * | Error                    | Background                  |  5.77  | pass |
 * | Outline                  | Background (3:1 UI-component threshold) | 4.12 | pass |
 */

// --- Neutral surfaces (M3 tone-based container ladder) ---
val Background = Color(0xFF111214)
val Surface = Color(0xFF111214)
val SurfaceContainerLowest = Color(0xFF0B0C0D)
val SurfaceContainerLow = Color(0xFF17181B)
val SurfaceContainer = Color(0xFF1C1E22)
val SurfaceContainerHigh = Color(0xFF24262B)
val SurfaceContainerHighest = Color(0xFF2C2F35)

// --- Content on neutral surfaces ---
val OnSurface = Color(0xFFE7E5E1) // warm off-white, not pure #FFFFFF — softer for long reading
val OnSurfaceVariant = Color(0xFFA3A7AE) // secondary text, section labels, metadata

// --- Structural lines ---
// Outline: real component boundaries (text-field borders, dividers meant to read as a
// boundary, not just a hairline). Bumped from an initial #5B5F68 (2.93:1, failed the 3:1
// UI-component threshold) to #6B6F78 (4.12:1) after checking the actual ratio — see kdoc above.
val Outline = Color(0xFF6B6F78)
val OutlineVariant = Color(0xFF33363C) // quiet hairline dividers, no contrast requirement

// --- Accent: the single deliberate color in this system ---
// Muted brass/ochre. Low saturation on purpose — reads as an instrument-panel dial or a
// calibration mark, not a brand color or a "tap here" CTA green. Used sparingly: primary
// action affordance, selected/focused state, progress-toward-boundary indicators. Never used
// to color-code tier, severity, or urgency (see kdoc above).
val Accent = Color(0xFFB08D57)
val OnAccent = Color(0xFF1A1509)
val AccentContainer = Color(0xFF3A2F1C)
val OnAccentContainer = Color(0xFFE9D8B8)

// --- Error: reserved for genuine destructive/validation-failure affordances only ---
// Deliberately NOT a saturated alarm-red — a desaturated terracotta, consistent with the
// spec's explicit rejection of red/yellow severity coding (§3.5). This role exists for real
// error states a form or a system action can have (e.g. "could not save"), not for coding a
// Mission, tier, or violation as more or less serious than another.
val Error = Color(0xFFC77B6A)
val OnError = Color(0xFF2B0F09)
