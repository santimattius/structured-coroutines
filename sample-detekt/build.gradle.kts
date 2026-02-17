plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // Test source set used for RunBlockingWithDelayInTest rule example
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(project.layout.projectDirectory.file("detekt.yml")))
    // Do not fail build; this module exists to validate that rules report (run detekt to see 14 findings)
    ignoreFailures = true
}

dependencies {
    detektPlugins(project(":detekt-rules"))
}
