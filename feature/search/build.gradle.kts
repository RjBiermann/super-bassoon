// Search feature (PLAN §7): field + autocomplete + history UI, search feed
// (`search:<q>` keys), creator-results row.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rjbiermann.giffyviewer.search"
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

android {
    testOptions {
        unitTests {
            // Robolectric reads the merged (debug) manifest + resources (issue #57 test).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    // Issue #11: the empty-state "Browse all" strips open browseable feeds —
    // the callback hands the host a FeedSource (one-way dep, no cycle).
    implementation(project(":feature:feed"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(libs.material.icons)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Issue #57: Robolectric JVM rung for the VM scope-load contract (MockWebServer
    // + in-memory Room, matching the feature:feed/#50 stage-2 harness style).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.retrofit)
    testImplementation(libs.okhttp.mockwebserver)
    // Issue #57 test replays the real converter + Json config end to end.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.retrofit.kotlinx.serialization)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
