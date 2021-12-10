import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
}

group = "com.amarland"
version = "0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val composeJbVersion: String by rootProject.extra
val okioVersion: String by rootProject.extra

dependencies {
    implementation("org.jetbrains.compose.ui:ui:$composeJbVersion")
    implementation("com.squareup.okio:okio:$okioVersion")

    val batikVersion = "1.14"

    testImplementation(platform("org.junit:junit-bom:5.8.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.apache.xmlgraphics:batik-transcoder:$batikVersion")
    // https://stackoverflow.com/questions/45239099/apache-batik-no-writeadapter-is-available
    testImplementation("org.apache.xmlgraphics:batik-codec:$batikVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions {
        languageVersion = "1.5"
        apiVersion = "1.5"
        jvmTarget = "${JavaVersion.VERSION_1_8}"
    }
}
