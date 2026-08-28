// Migration notice: switched from KAPT to KSP for annotation processing.
// Reason: KSP avoids compatibility problems with AGP's built-in Kotlin
// support and is the recommended modern replacement for KAPT. Room's compiler is now invoked via KSP.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP plugin for annotation processing. Version should match Kotlin compatibility matrix.
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.progmob_progetto_melluso"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.progmob_progetto_melluso"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // KSP
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Location
    implementation(libs.google.play.services.location)

    // Firebase Auth
    implementation(libs.com.google.firebase.auth.ktx)
    implementation(libs.google.play.services.auth)

    // ViewModel KTX
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // MapLibre Native: free vector tiles (OpenFreeMap), no API key required
    implementation(libs.maplibre.android)

    // Coil: async image loading for the real photos of the points of interest
    implementation(libs.coil.compose)
}