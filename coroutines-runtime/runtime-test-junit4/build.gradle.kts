plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish.vanniktech)
}

// :runtime-test-junit4 is a separate, JVM-only artifact so :runtime-test keeps its "no transitive
// test libs" guarantee (JUnit4 is never pulled by it). It versions on the same line as :runtime
// (RUNTIME_VERSION); allprojects {} in the root build.gradle.kts runs before this script.
version = project.findProperty("RUNTIME_VERSION")?.toString() ?: "0.1.0"

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    // api: the rule's public signatures expose TestDispatcherProvider and kotlinx-coroutines-test
    // types; :runtime-test already exposes :runtime and kotlinx-coroutines-test as api.
    api(project(":runtime-test"))
    // api: MainCoroutinesTestRule extends org.junit.rules.TestWatcher.
    api(libs.junit)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-runtime-test-junit4",
        version = project.version.toString()
    )
}
