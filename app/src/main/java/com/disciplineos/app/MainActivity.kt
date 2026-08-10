package com.disciplineos.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * ROADMAP.md Phase 3 — this app's first launcher Activity. Until now, this project had no
 * `MAIN`/`LAUNCHER` intent-filter anywhere (deliberately — see AndroidManifest.xml's Phase 2
 * comment and ROADMAP.md §4(c)'s device-verification notes on why that was correct at the
 * time: no UI existed yet, and `MissionInterceptionActivity` is explicitly `exported=false`,
 * launched only by [com.disciplineos.app.enforcement.MissionAccessibilityService], never meant
 * to be an app entry point).
 *
 * Hosts a single `NavHostFragment` (declared in `res/layout/activity_main.xml` via
 * `app:navGraph`, not built up in code — the declarative form needs no manual
 * FragmentTransaction here, and Navigation Component's own docs recommend it as the default
 * over the programmatic form used only when a NavHost's graph must be chosen at runtime,
 * which isn't the case here) running `res/navigation/onboarding_nav_graph.xml`.
 *
 * **Corrected, this pass — this kdoc previously described every destination as still
 * [com.disciplineos.app.onboarding.OnboardingPlaceholderFragment] placeholder content; that
 * stopped being true as of PR #16 (see STATUS.md), and this note had gone stale describing
 * it.** The graph's nine onboarding screens (Welcome through First Mission Scheduling) all
 * have real content now — only `ironCalibrationGateFragment` remains a placeholder,
 * deliberately (see that destination's own comment in the graph). As of this pass, the same
 * graph also carries the real post-onboarding hand-off: `firstMissionSchedulingFragment` now
 * navigates to a new `homeFragment` destination on completion, which in turn can reach a new
 * `ironCalibrationFragment` — see [com.disciplineos.app.home.HomeFragment] and
 * [com.disciplineos.app.home.IronCalibrationFragment] for what those do. Still one graph, one
 * NavHost — no second graph or Activity was introduced for this.
 *
 * [FragmentActivity], not `AppCompatActivity` — nothing here needs AppCompat's
 * toolbar/theme-compat machinery, and no dependency on `androidx.appcompat` exists anywhere in
 * this project currently. Revisit if a real screen's design later needs an AppCompat feature
 * `FragmentActivity` doesn't provide (e.g. a Material toolbar) — that's a concrete trigger to
 * add the dependency then, not a reason to add it preemptively now.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
