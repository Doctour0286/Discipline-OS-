package com.disciplineos.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

/**
 * Single shared call site for the Compose-hosted-in-a-Fragment pattern every onboarding screen
 * uses (ROADMAP.md §5.26 introduced it with First Mission Scheduling as the proof-of-concept;
 * §5.27 repeated it identically across the other 8 screens; this file is the follow-up §5.26
 * itself flagged — "worth revisiting... to theme once at the Activity level instead of
 * per-screen" — see STATUS.md's "known standing gaps" note this closes).
 *
 * **Why this, and not an actual Activity-level `setContent { }`:** `MainActivity` hosts a
 * `NavHostFragment` declared in `activity_main.xml` (`app:navGraph`, XML-declarative, no
 * `ComponentActivity`/`setContent` anywhere in that class) — there is no single Activity-level
 * Compose tree to hoist [DisciplineOsTheme] into without replacing Jetpack Navigation's
 * Fragment-based graph with Compose Navigation, a materially bigger and riskier change than
 * "reduce nine copies of the same four lines to one." That rewrite isn't ruled out forever, but
 * it's a deliberate future decision of its own — not something to fold into a
 * theme-deduplication pass, per this project's own "small, reviewable, one-concern-per-PR"
 * convention (see ROADMAP.md §5's prior entries). What this function *does* give: exactly one
 * place in the codebase that applies [DisciplineOsTheme] to a Fragment's Compose content, so a
 * future change to that wrapper (e.g. adding `isSystemInDarkTheme()`-driven theme switching,
 * flagged as a real follow-up in [DisciplineOsTheme]'s own kdoc) touches one file, not nine.
 *
 * **Behavior is identical to what it replaces** in every migrated Fragment:
 * `ComposeView(requireContext()).apply { setViewCompositionStrategy(...); setContent { ... } }`
 * with [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] — the officially
 * recommended strategy for `ComposeView` hosted inside a Fragment (ties composition disposal to
 * the Fragment's view lifecycle, not its own, so it neither leaks nor double-disposes; verified
 * against Android Developers' ComposeView-in-Fragment guidance when §5.26 first established
 * this pattern). Nothing about *when* or *how* the ComposeView is created changes — this is a
 * pure extraction, not a behavior change, so it needs no re-verification of business logic, only
 * a re-check that each call site still renders and navigates identically (see this pass's own
 * ROADMAP.md entry for what was and wasn't re-verified).
 *
 * Usage in a Fragment's `onCreateView`:
 * ```
 * override fun onCreateView(...): View = themedComposeView {
 *     FirstMissionSchedulingScreen(...)
 * }
 * ```
 */
fun Fragment.themedComposeView(content: @Composable () -> Unit) =
    ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DisciplineOsTheme {
                content()
            }
        }
    }
