plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.disciplineos.domain"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // matches :data — Instant usage without desugaring complexity
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isIncludeAndroidResources = true // required for Robolectric
        }
    }
}

dependencies {
    // Domain sits directly on top of :data — it depends on Room *entities* and *DAO
    // interfaces* (UserDao, MissionDao, ViolationDao, LedgerDao) plus DisciplineOsDatabase
    // for transaction boundaries, but contains no Room annotations of its own. This module
    // is where PRD business rules (§12, §26.4, §27.2, §29, §35) live as plain Kotlin,
    // testable without an Android instrumentation target — see ROADMAP.md Phase 1.
    api(project(":data"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // withTransaction
    // Phase 2 (WardenVoiceProvider, ROADMAP.md): withTimeoutOrNull for the Architecture §2.1
    // "(c)" fallback strategy — hard timeout on the cloud-generation call. Not previously a
    // direct main-sourceset dependency; only pulled in transitively via
    // kotlinx-coroutines-test's test-only classpath before this.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1") // ApplicationProvider, for Robolectric+Room tests
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
