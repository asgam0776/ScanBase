plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

android {
    namespace = "com.scanbase.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scanbase.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.collection:collection-jvm:1.4.4",
        "androidx.annotation:annotation-jvm:1.9.1"
    )
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.compose.ui:ui:1.7.2")
    implementation("androidx.compose.foundation:foundation:1.7.2")
    implementation("androidx.compose.foundation:foundation-layout:1.7.2")
}
