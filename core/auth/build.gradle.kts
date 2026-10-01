// Populated in Phase 5 (see PLAN.md §10, AGENTS-AUTH.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rjbiermann.giffyviewer.core.auth"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
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
    api(libs.kotlinx.coroutines.core)
    api(project(":core:model"))
    implementation(libs.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
