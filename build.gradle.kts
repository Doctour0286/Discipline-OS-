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
