plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.turboboost"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.turboboost"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "2.1"
    }

    val hasReleaseKeystore =
        System.getenv("KEYSTORE_PASSWORD") != null &&
        System.getenv("KEY_ALIAS") != null &&
        System.getenv("KEY_PASSWORD") != null &&
        file("${project.rootProject.projectDir}/release.jks").exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file("${project.rootProject.projectDir}/release.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        aidl = true
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
