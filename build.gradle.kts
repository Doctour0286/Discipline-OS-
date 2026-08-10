// Top-level build file — declares plugin versions once (`apply false` here, applied per
// module in each module's own build.gradle.kts) so :app, :data, and :domain don't each pin
// their own, potentially drifting, AGP/Kotlin/KSP/Room versions.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("androidx.room") version "2.6.1" apply false
}

// Compose is applied per-module via app/build.gradle.kts's buildFeatures.compose + the
// androidx.compose.compiler dependency (composeOptions { kotlinCompilerExtensionVersion }),
// not as a separate top-level plugin here. As of Kotlin 2.0+, Compose Compiler ships as its
// own Gradle plugin (org.jetbrains.kotlin.plugin.compose) tied 1:1 to the Kotlin version —
// but this project is pinned to Kotlin 1.9.24 (see above), which predates that model. For
// Kotlin < 2.0, Google's own compatibility guidance is the legacy composeOptions {
// kotlinCompilerExtensionVersion = "..." } approach with an explicit compiler-artifact
// version, not the newer plugin. Bumping to Kotlin 2.0+ to get the newer plugin model is a
// separate, larger decision (AGP/KSP/Room version cascade) than this design-system change —
// deliberately not bundled in here.
