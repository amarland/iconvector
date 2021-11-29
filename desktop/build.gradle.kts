import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val okioVersion: String by rootProject.extra

    implementation(compose.desktop.common)
    implementation("com.squareup.okio:okio:$okioVersion")

    implementation(project(":lib"))
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions {
        languageVersion = "1.5"
        apiVersion = "1.5"
        jvmTarget = "${JavaVersion.VERSION_1_8}"
    }
}
