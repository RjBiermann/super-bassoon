// Phase 6: TV app (PLAN §8 UI-TV, §10 phase 6).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rjbiermann.giffyviewer.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rjbiermann.giffyviewer.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // CI (Phase 10): the base64 keystore decodes to this file; secrets
            // SIGNING_KEY (base64) + SIGNING_PASS drive the workflow.
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "../keystore/release.keystore")
            storePassword = System.getenv("SIGNING_PASS") ?: "giffy_local"
            keyAlias = "giffy"
            keyPassword = System.getenv("SIGNING_PASS") ?: "giffy_local"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:auth"))
    implementation(project(":core:player"))
    implementation(project(":feature:feed")) // FeedViewModel + FeedSource are shared
    implementation(project(":feature:auth"))
    implementation(project(":feature:settings"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.material.icons)
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
