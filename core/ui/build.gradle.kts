plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rjbiermann.giffyviewer.core.ui"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    implementation(libs.material.icons)
    implementation(libs.material.icons.extended)

    testImplementation(libs.junit)
    // Issue #50 stage 2: Compose UI tests on the JVM rung (Robolectric) so
    // `check` runs them on the 2-core CI — no emulator needed.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    // Registers ComponentActivity in the manifest for createComposeRule().
    debugImplementation(libs.compose.ui.test.manifest)
}

android {
    testOptions {
        unitTests {
            // Robolectric reads the merged (debug) manifest + resources.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
