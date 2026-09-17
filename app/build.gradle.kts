plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aiupscaler"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aiupscaler"
        minSdk = 28                    // Android 9 (Pie)
        targetSdk = 35                 // Android 15
        versionCode = 10
        versionName = "10.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        resourceConfigurations += listOf("in", "en")

        // Vector drawable backward-compat
        vectorDrawables.useSupportLibrary = true
    }

    androidResources { noCompress += listOf("tflite", "bin") }
    buildFeatures { viewBinding = true }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true    // Java 8+ API di Android 9
    }
    kotlinOptions { jvmTarget = "17" }

    // Backward compat untuk namespace resource
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Activity & Fragment
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // TFLite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // Exif
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Desugaring — dukungan API modern di Android 9
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
