plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aiupscaler"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aiupscaler"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    androidResources { noCompress += listOf("tflite", "bin") }
    buildFeatures { viewBinding = true }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val lifecycle = "2.7.0"
    val coroutines = "1.7.3"

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
