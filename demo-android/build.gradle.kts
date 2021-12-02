plugins {
    id("com.android.application")
    kotlin("android")
}

val composeAndroidVersion: String by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra

android {
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "com.amarland.iconvector.demo.android"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        languageVersion = "1.5"
        apiVersion = "1.5"
        jvmTarget = "${JavaVersion.VERSION_1_8}"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }

    buildFeatures.compose = true
    composeOptions.kotlinCompilerExtensionVersion = composeAndroidVersion
}

dependencies {
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.compose.ui:ui:$composeAndroidVersion")
    implementation("androidx.compose.material:material:$composeAndroidVersion")
    implementation("androidx.activity:activity-compose:1.3.1")

    implementation(project(":android"))
}
