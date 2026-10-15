plugins {
    alias(libs.plugins.kotlin.jvm)
    // Gif/NicheRef are nav route/saved-state payloads (issue #13) — the
    // annotations land here, :core:network DTOs stay out (§2).
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
}
