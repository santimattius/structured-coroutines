plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish.vanniktech)
}

// ──────────────────────────────────────────────────────────────────────────
// Forward-compat smoke source set — Kotlin readiness only, NOT production.
//
// Recompiles the checker sources (compiler/src/main/kotlin) against the
// test-scope-only `forward-compat-kotlin` coordinate (Kotlin 2.4.20) while KGP
// itself, the production `kotlin` catalog ref, and `compileKotlin`/`test` stay
// pinned at 2.4.0. Isolated classpath — does not affect any other source set or task.
// See docs/KOTLIN_2_4_READINESS_CHECKLIST.md for the bump criteria.
// ──────────────────────────────────────────────────────────────────────────
sourceSets {
    create("forwardCompatTest") {
        kotlin.srcDir("src/main/kotlin")
    }
}

val forwardCompatTestCompileOnly: Configuration by configurations.getting

dependencies {
    // Kotlin Compiler - compileOnly because it's provided at compile time
    compileOnly(libs.kotlin.compiler.embeddable)

    // Test dependencies - using Gradle TestKit for functional testing
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())

    // Forward-compat smoke source set - resolves the checker sources against
    // Kotlin 2.4.20 only; never touches the production compileOnly/testImplementation above.
    forwardCompatTestCompileOnly(libs.forward.compat.kotlin.compiler.embeddable)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        // Enable context parameters for FIR checker API
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileForwardCompatTestKotlin") {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        // Documented fallback (per proposal Risks table) if KGP 2.4.0 rejects
        // forward-compat-kotlin 2.4.20 metadata on this classpath. Scoped to
        // this task only — never applied to production compileKotlin.
        // freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

val forwardCompatTest by tasks.registering {
    group = "verification"
    description = "Smoke-compiles compiler checker sources against the forward-compat-kotlin " +
        "(Kotlin 2.4.20) forward-compat coordinate. Non-blocking readiness signal only."
    dependsOn(tasks.named("compileForwardCompatTestKotlin"))
}

tasks.test {
    useJUnitPlatform()

    // Publish to Maven Local so functional tests can resolve the plugin via mavenLocal()
    dependsOn(
        ":gradle-plugin:publishToMavenLocal",
        ":compiler:publishToMavenLocal",
        ":annotations:publishToMavenLocal"
    )

    systemProperty("structuredCoroutines.version", project.version.toString())
    systemProperty("structuredCoroutines.rootDir", project.rootProject.projectDir.absolutePath)
    systemProperty("kotlinVersion", libs.versions.kotlin.get())
    systemProperty("coroutinesVersion", libs.versions.kotlinx.coroutines.get())
}

// Configure JAR to include the service file
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Structured Coroutines Compiler Plugin",
            "Implementation-Version" to project.version
        )
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-compiler",
        version = project.version.toString()
    )
}

