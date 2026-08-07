package com.disciplineos.app

import android.app.Application
import android.util.Log
import com.disciplineos.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * First `Application` subclass in this codebase — nothing needed one before this (Phase 2's
 * Accessibility Service and interception Activity both reach [AppContainer] directly via
 * their own `Context`, per `AppContainer`'s own kdoc on why manual DI needs no framework
 * entry point). Added specifically to give [com.disciplineos.app.debug.DebugSeeder]
 * (ROADMAP.md §4(c)) a single, deliberate call site at process start, rather than seeding
 * being triggered ad hoc from wherever happens to run first.
 *
 * **Two independent guards keep seeding out of any non-debug build, not one:**
 * 1. [com.disciplineos.app.debug.DebugSeeder] itself lives under Gradle's `debug` source set
 *    (`app/src/debug/...`) — compiled into debug builds only, so a release build's classpath
 *    structurally does not contain that class at all.
 * 2. The call below is additionally gated on `BuildConfig.DEBUG`, so if this file were ever
 *    copied or its guard removed carelessly, a plain reference to the class from `main`
 *    source would still fail to *compile* in a release context in the normal AGP variant
 *    setup (release sourceSet has no visibility into `debug`-only classes) — the runtime
 *    check here is belt-and-suspenders documentation of intent, not the only thing preventing
 *    a mistake, matching this codebase's general preference (see `AndroidManifest.xml`'s own
 *    comments) for build-system-enforced guarantees over easily-forgotten conventions.
 *
 * Deliberately not a `lifecycleScope`/`ComponentActivity`-style coroutine host — `Application`
 * has no such AndroidX convenience the way `ComponentActivity` does (see
 * `MissionInterceptionActivity`'s kdoc on exactly this gap for `AccessibilityService`) — so
 * this uses the same manually-managed `CoroutineScope(SupervisorJob())` pattern already
 * established in `MissionAccessibilityService` for the same reason.
 */
class DisciplineOsApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                try {
                    val database = AppContainer.database(this@DisciplineOsApplication)
                    val seeded = com.disciplineos.app.debug.DebugSeeder.seedIfNeeded(database)
                    if (seeded != null) {
                        Log.i(TAG, "DebugSeeder: seeded Mission ${seeded.id} for on-device Phase 2 verification (ROADMAP.md §4(c)).")
                    } else {
                        Log.i(TAG, "DebugSeeder: skipped — a Mission already exists (idempotent no-op).")
                    }
                } catch (e: Exception) {
                    // Seeding failure must never take down app startup — this is test
                    // infrastructure for manual verification, not a path any real user flow
                    // depends on. Logged loudly rather than silently swallowed, so a failed
                    // seed attempt is still visible while investigating an on-device pass.
                    Log.e(TAG, "DebugSeeder: seeding failed, continuing app startup regardless.", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DisciplineOsApp"
    }
}
