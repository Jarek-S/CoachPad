plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "pl.net.orlik"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "eu.coachpad"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("coachpad.versionCode").get().toInt()
        versionName = providers.gradleProperty("coachpad.versionName").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The upload key lives outside the repo; its path and passwords come from
    // ~/.gradle/gradle.properties, so builds without them stay unsigned
    val uploadStoreFile = providers.gradleProperty("coachpad.upload.storeFile").orNull
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("coachpad.upload.storePassword").get()
                keyAlias = providers.gradleProperty("coachpad.upload.keyAlias").get()
                keyPassword = providers.gradleProperty("coachpad.upload.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            optimization {
                enable = false
            }
        }
    }
    // All languages in every install: the per-app language setting (locales_config) may pick one
    // the system doesn't use, and Google Play installs only the system's language splits
    bundle {
        language {
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

room {
    // Database schema snapshots (JSON), kept in git — needed later for migrations
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}