plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Originally deliberately minimal (this module existed only so :data and :domain had a real
// application module depending on them, giving CI a full AGP variant graph to build). Stale
// as of Phase 2: this module now contains the real Accessibility Service, interception
// Activity, layout/strings/manifest resources, and their test coverage. Phase 3 (onboarding
// UI): the navigation skeleton (MainActivity + NavHostFragment + placeholder onboarding
// destinations) now exists too — see ROADMAP.md Phase 3 for what's implemented vs. still
// placeholder content.
android {
    namespace = "com.disciplineos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.disciplineos.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1-skeleton"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true // AGP 8+ generates BuildConfig only when opted in. Needed by
        // DisciplineOsApplication.onCreate()'s BuildConfig.DEBUG gate around DebugSeeder
        // (ROADMAP.md §4(c)) — nothing in this module referenced BuildConfig before this,
        // so it was never previously turned on.
        compose = true // Design-system pass (ROADMAP.md, see this commit): Google announced
        // at I/O 2026 that the Views-based UI toolkit (android.widget, including MDC-Android)
        // is now in maintenance mode and the platform is Compose-first going forward — a
        // materially different situation than when activity_mission_interception.xml's
        // comment ("no Compose dependency exists yet... no framework not already justified")
        // and this module's fragment-ktx/navigation-fragment-ktx dependencies below were
        // chosen. That comment is now stale, not wrong for its time — recorded here rather
        // than silently contradicted. This migrates incrementally (Google's own recommended
        // strategy): Fragments + Jetpack Navigation stay exactly as they are, each Fragment's
        // XML content is replaced by a single ComposeView hosting real composables, one
        // screen at a time. Phase 2 (MissionInterceptionActivity, accessibility-service
        // interception loop) is deliberately NOT touched by this pass — it stays on plain
        // Views for now, migrated later as its own deliberate decision, not swept in here.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        // Pinned to the Compose Compiler release that Google's own compatibility guidance
        // maps to Kotlin 1.9.24 exactly (this project's pinned Kotlin version, see root
        // build.gradle.kts) — verified against developer.android.com/jetpack/androidx/
        // releases/compose-compiler's own release notes ("this compiler release is targeting
        // Kotlin 1.9.24") rather than assumed. Do not bump this independently of a Kotlin
        // version bump; the two are coupled pre-Kotlin-2.0.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // required for Robolectric, matching :domain's
            // build.gradle.kts — InterceptionControllerTest (ROADMAP.md Phase 2) needs the
            // same in-memory-Room-under-Robolectric setup RecordViolationUseCaseTest already
            // established, since InterceptionController drives real RecordViolationUseCase /
            // TierTransitionUseCase instances, not fakes of them (neither is an interface).
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4") // lifecycleScope, used by
    // MissionAccessibilityService/MissionInterceptionActivity (ROADMAP.md Phase 2) — not
    // previously a direct dependency since this module had no real app code yet.
    implementation("androidx.activity:activity-ktx:1.9.1") // ComponentActivity — see
    // MissionInterceptionActivity's kdoc: it extends ComponentActivity (not plain
    // android.app.Activity) specifically because lifecycleScope requires a real
    // LifecycleOwner, which plain Activity does not implement. Not needed for
    // MissionAccessibilityService (fixed with a manual CoroutineScope instead — plain
    // AccessibilityService has no ComponentActivity-equivalent AndroidX subclass to switch to).
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // MasterKey /
    // EncryptedSharedPreferences, used by DbPassphraseProvider to store the SQLCipher
    // passphrase (DisciplineOsDatabase's kdoc requirement that the passphrase come from
    // Android Keystore-backed storage, not be hardcoded). Found via real CI failure — nothing
    // previously declared this dependency despite DbPassphraseProvider.kt needing it since
    // whenever that file was written; never caught until :app actually compiled for real.
    implementation("androidx.fragment:fragment-ktx:1.8.2") // Phase 3 (ROADMAP.md): onboarding
    // screens are Fragments navigated via Jetpack Navigation Component, not Compose —
    // matching the "no framework not already justified" stance activity_mission_interception.xml's
    // own top comment already committed this project to (that layout explicitly notes "no
    // Compose dependency exists yet" as the reason for its own plain-View approach). Adding
    // Compose now for onboarding alone would contradict that existing, deliberate decision
    // rather than build on it.
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    // Manual DI (AppContainer), not Hilt, per AppContainer.kt's own kdoc: Hilt is "a
    // reasonable escalation" once manual wiring becomes genuinely unwieldy, not an automatic
    // trigger at Phase 3's first screen — see that file's kdoc for the full reasoning. Revisit
    // once onboarding's real per-screen state (not just navigation) makes manual wiring
    // actually painful, not preemptively here.

    // --- Compose (design-system pass, see ROADMAP.md) ---
    // BOM pinned to 2024.09.00 deliberately, not the current 2026 BOM: this project stays on
    // compileSdk 34 (see android{} block above), and later Compose BOMs (1.12.0/2026.04+)
    // require compileSdk 37+. 2024.09.00 was the current BOM when compileSdk 34 was the norm
    // and is fully compatible with Compose Compiler 1.5.14 / Kotlin 1.9.24 above — bumping
    // compileSdk to chase a newer BOM is a separate, larger decision than this design-system
    // change, not bundled in here.
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1") // matches activity-ktx above
    implementation("androidx.fragment:fragment-ktx:1.8.2") // already declared above; Compose
    // interop (ComposeView hosted inside a Fragment) needs nothing additional beyond what
    // fragment-ktx already provides.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4") // collectAsStateWithLifecycle,
    // ties Compose state collection to the Fragment's view lifecycle rather than the
    // Fragment's own lifecycle — the officially recommended pattern for ComposeView-in-
    // Fragment (avoids over-collecting/leaking across navigation transitions).
    debugImplementation("androidx.compose.ui:ui-tooling") // Compose Preview support in Android
    // Studio; debug-only per Google's own recommended dependency split (never shipped in
    // release builds).

    testImplementation("junit:junit:4.13.2")
    // Mirrors :domain/build.gradle.kts's test dependency set exactly (see that file's
    // comments for the rationale on each) — added here for InterceptionControllerTest, the
    // first :app-module test that needs a real (in-memory) Room database rather than pure
    // JUnit against framework-free Kotlin.
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24") // kotlin.test.assertFailsWith,
    // used by InterceptionControllerTest for breakCommitment/ironCrisisExit's require()-thrown
    // IllegalArgumentException assertions. Found via real CI failure (build-and-test run #7,
    // ROADMAP.md §5.17) — "Unresolved reference: test" / "Unresolved reference: assertFailsWith"
    // — nothing had declared this before; :domain's own tests never needed it (none use
    // assertFailsWith), so this gap was specific to :app and only surfaced once CI actually
    // ran :app:compileDebugUnitTestKotlin for the first time (see §5.16's own addendum on why
    // that task wasn't wired into CI until this same pass).
    // fragment-testing (androidx.fragment:fragment-testing) was removed here — it was added
    // solely to support OnboardingPlaceholderFragmentTest, which was described across several
    // sessions but never actually written (see ROADMAP.md §3, "Known gap"). A dependency
    // justified by a test that doesn't exist is the wrong resting state; removed rather than
    // left as unexplained scaffolding. If real onboarding-screen tests are written later
    // (against the actual Tier Selection UI below, not the placeholder), re-add this then,
    // pointed at that real test.
}
