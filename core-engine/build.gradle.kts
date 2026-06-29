plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Phase 0 scaffold. This module currently holds only the premium Entitlements seam (pure Kotlin).
// In Phase 1 it becomes the real engine: analysis/, data/ (Room), record/, with a stable public API
// that the new UI module depends on. It is intentionally an Android library now so the multi-module +
// OneDrive relocate toolchain is validated against the real target type before code is moved in.
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
