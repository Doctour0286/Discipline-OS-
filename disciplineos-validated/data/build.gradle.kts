plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // required for the ksp(...) Room compiler dependency below —
    // was missing; the `ksp(...)` dependency config doesn't exist until this plugin is applied.
    id("androidx.room")
}

android {
    namespace = "com.disciplineos.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // matches Instant usage without desugaring complexity; revisit if lower minSdk needed
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher for at-rest encryption — Architecture doc §3.1, PRD §40.
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
