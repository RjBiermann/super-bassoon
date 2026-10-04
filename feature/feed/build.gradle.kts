plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.rjbiermann.giffyviewer.feature.feed"
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
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    implementation(libs.material.icons)
    implementation(libs.material.icons.extended)
    implementation(project(":core:ui"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    // retrofit2.HttpException is caught in the paging sources (pool-cap 400 →
    // end-of-pool); retrofit rides transitively via :core:network's API types —
    // this makes the class visible to this module's compile classpath.
    implementation(libs.retrofit)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // httpError(): retrofit2.HttpException + okhttp3.ResponseBody construction.
    testImplementation(libs.okhttp)
    testImplementation(libs.retrofit)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
