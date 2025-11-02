plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.medreminder.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medreminder.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.15.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Transcription engine configuration
        // Set Google Cloud API key here to enable cloud transcription (empty = disabled)
        buildConfigField("String", "GOOGLE_CLOUD_API_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../medication-reminder.keystore")
            storePassword = "android123"
            keyAlias = "medication-reminder"
            keyPassword = "android123"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            // Only include ARM64 architecture for modern phones
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a")
            }
        }

        debug {
            // Debug keeps all architectures for emulator support
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager for notifications
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // CameraX for photo capture
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ML Kit for Translation and Language Identification
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("com.google.mlkit:language-id:17.0.5")

    // Kotlin Coroutines extensions for Play Services (required for ML Kit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Whisper.cpp native library (official Android JNI wrapper)
    implementation(project(":whisper"))

    // Testing - Unit Tests
    testImplementation("junit:junit:4.13.2")
    // Provide org.json on the unit test classpath so JSON parsing in JVM tests executes
    testImplementation("org.json:json:20231013")

    // Coroutines testing support
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Mocking framework
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")

    // Architecture Components testing (LiveData, ViewModel)
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Room testing support (in-memory database)
    testImplementation("androidx.room:room-testing:2.6.1")

    // Truth assertions (optional but recommended for readable tests)
    testImplementation("com.google.truth:truth:1.1.5")

    // WorkManager testing
    testImplementation("androidx.work:work-testing:2.9.0")

    // Testing - Instrumented Tests (Android)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("com.google.truth:truth:1.1.5")

    // Debug dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
