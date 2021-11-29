import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.reflections:reflections:0.10.2")

    implementation(project(":desktop"))
}

compose.desktop.application.mainClass = "MainKt"

tasks.withType<KotlinCompile>().all {
    kotlinOptions {
        languageVersion = "1.5"
        apiVersion = "1.5"
        jvmTarget = "${JavaVersion.VERSION_1_8}"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
}
