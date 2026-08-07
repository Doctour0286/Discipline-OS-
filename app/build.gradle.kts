plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Originally deliberately minimal (this module existed only so :data and :domain had a real
// application module depending on them, giving CI a full AGP variant graph to build). Stale
// as of Phase 2: this module now contains the real Accessibility Service, interception
// Activity, layout/strings/manifest resources, and their test coverage — see ROADMAP.md
// Phase 2 for what actually lives here now. Phase 3 (onboarding UI) is still ahead of this.
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}
