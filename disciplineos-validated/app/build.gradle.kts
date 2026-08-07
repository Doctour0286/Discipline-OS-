plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Deliberately minimal — ROADMAP.md Phase 2/3 own the actual Accessibility Service,
// interception overlay, and onboarding UI. This module exists now only so :data and :domain
// have a real application module depending on them, giving CI a full AGP variant graph to
// build (manifest merging, resource linking, dex) rather than stopping at "two libraries
// compiled in isolation." No app code beyond a launcher-less Application class.
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
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
