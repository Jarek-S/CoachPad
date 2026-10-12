import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module — no Android dependencies, shared by :wear and :mobile.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    // api: :wear and :mobile use the message classes (and their serializers) directly
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
