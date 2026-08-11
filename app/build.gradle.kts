// Migration notice: switched from KAPT to KSP for annotation processing.
// Reason: KSP avoids compatibility problems with AGP's built-in Kotlin
// support and is the recommended modern replacement for KAPT. Room's compiler is now invoked via KSP.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP plugin for annotation processing. Version should match Kotlin compatibility matrix.
    alias(libs.plugins.ksp)
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
        versionCode = 1
        versionName = "1.0"

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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
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
    ksp("androidx.room:room-compiler:2.5.1")

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Location
    implementation(libs.google.play.services.location)

    // Firebase Auth
    implementation(libs.com.google.firebase.auth.ktx)

    // ViewModel KTX
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
}