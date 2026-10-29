plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "com.spy247.app"
    
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.spy247.app"
        
        // Package name for Firebase app ID: spy247-app
        minSdk = 21  // Works on Android 5.0+ (Lollipop)
        targetSdk = 34
        
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Core AndroidX dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // CameraX for camera & screen capture (if needed)
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    
    // MediaRecorder for mic & video encoding
    implementation("com.google.android.material:material:1.9.0")    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database:23.0.0")

    // Media3 UI (VideoView used in layout XML)
    implementation("androidx.media3:media3-ui:1.5.0")

    // Gson for JSON serialization of media chunks
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Glide or Coil for displaying live camera feed on controller
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Lottie for animated loading states
    implementation("com.airbnb.android:lottie:6.2.0")
}
