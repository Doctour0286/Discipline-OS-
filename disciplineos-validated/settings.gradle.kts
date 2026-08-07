pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DisciplineOS"

// ROADMAP.md §3/§4: ":data" and ":domain" already existed as Android library modules with
// no settings.gradle.kts tying them into one buildable project. ":app" is new here — a
// minimal skeleton, not real app code (ROADMAP.md Phase 2/3 own the actual app UI/enforcement
// loop) — added because CI needs at least one `com.android.application` module to produce a
// real AGP variant graph end-to-end, and because ":data"/":domain" alone, with nothing
// depending on them, is an easy way for a dependency-resolution problem to hide until Phase 2
// actually needs it.
include(":app")
include(":data")
include(":domain")
