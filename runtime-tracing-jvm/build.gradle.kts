plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish.vanniktech)
}

// :runtime-tracing-jvm is a separate artifact so :runtime consumers never get androidx.tracing
// transitively. It versions on the same line as :runtime (RUNTIME_VERSION); allprojects {} in the
// root build.gradle.kts runs before this script.
version = project.findProperty("RUNTIME_VERSION")?.toString() ?: "0.1.0"

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":runtime"))
    // api: PerfettoTraceSink takes an androidx.tracing.Tracer.
    api(libs.androidx.tracing)
    // implementation: only PerfettoTraceSession (the file-backed driver) uses tracing-wire.
    implementation(libs.androidx.tracing.wire)

    testImplementation(project(":runtime-test"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-runtime-tracing-jvm",
        version = project.version.toString()
    )
}
