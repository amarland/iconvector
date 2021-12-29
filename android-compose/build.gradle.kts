plugins {
    id("com.android.library")
    kotlin("android")
}

val composeAndroidVersion: String by rootProject.extra
val okioVersion: String by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra

android {
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {}
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        languageVersion = "1.5"
        apiVersion = "1.5"
        jvmTarget = "${JavaVersion.VERSION_1_8}"
    }

    buildFeatures.compose = true
    composeOptions.kotlinCompilerExtensionVersion = composeAndroidVersion
}

dependencies {
    implementation("androidx.compose.ui:ui:$composeAndroidVersion")
    implementation("com.squareup.okio:okio:$okioVersion")

    implementation(project(":lib"))
    implementation(project(":lib")) {
        capabilities {
            requireCapability("com.amarland.iconvector:lib-compose-support")
        }
    }
}
