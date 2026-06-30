plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// The engine module. Phase 1B moved analysis/data(Room)/cloud/record/export/settings here from :app
// (packages kept as com.cartrip.analyzer.* — not renamed). :app depends on this and keeps the legacy
// UI as the runnable oracle. Builds via the OneDrive relocate toolchain (see HANDOFF 2.1).
android {
    namespace = "com.cartrip.engine"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Room schema export (moved from :app in Phase 1B): captures the DB schema as JSON under schemas/ for
// migration tests and compile-time validation.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Engine runtime deps (moved from :app in Phase 1B). Room is exposed via `api` so :app + the legacy
    // UI keep consuming DAO / entity / Flow types; the rest are engine-internal (cloud / record / export)
    // and stay `implementation`.
    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ContextCompat (registerReceiver / RECEIVER_*) + NotificationCompat — used by record/ notifications.
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.dhatim:fastexcel:0.18.4")
    // record/RecordingService uses Dispatchers.Main; coroutines-android reached :app transitively via
    // lifecycle-* (which stays in :app), so declare it explicitly here.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
