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
        versionCode = 8
        versionName = "8.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        resourceConfigurations += listOf("in", "en")
    }

    androidResources {
        noCompress += listOf("tflite", "bin")
    }

    buildFeatures {
        viewBinding = true
    }

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
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // TFLite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // Exif
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // RecyclerView (for settings)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
