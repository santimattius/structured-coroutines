plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish.vanniktech)
}

dependencies {
    // Detekt API - compileOnly to avoid runtime conflicts
    compileOnly(libs.detekt.api)
    
    // Testing
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // Gradle 9 no longer auto-loads the JUnit Platform launcher
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-detekt-rules",
        version = project.version.toString()
    )
}
