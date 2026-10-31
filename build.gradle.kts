@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    // Declared at the root so every module shares one classloader for the plugin; otherwise
    // :runtime (which also has the AGP KMP plugin) gets its own copy and the shared
    // MavenCentralBuildService cannot be injected into prepareMavenCentralPublishing.
    alias(libs.plugins.maven.publish.vanniktech) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// Binary-compatibility validation runs against :runtime's, :runtime-test's and :runtime-tracing-jvm's checked-in ABI dumps only.
// Dump layout: <module>/api/jvm/<module>.api (JVM, per-target dir because :runtime also has an Android
// target) and <module>/api/<module>.klib.api (klib targets; :runtime-test keeps the legacy flat
// <module>/api/<module>.api). :runtime-tracing-jvm is a plain JVM module: <module>/api/<module>.api.
// NOTE: the Android variant of :runtime has NO API dump and is NOT guarded by apiCheck: BCV 0.18.1
// does not support the com.android.kotlin.multiplatform.library plugin. Its source is shared with the
// JVM target (jvmAndAndroid), so the JVM dump covers it for now; revisit when BCV gains support.
// Every other module is excluded — most are not libraries with a public-ABI contract in the
// same sense (compiler plugin, Gradle plugin, IDE tooling, samples). The root project has no
// `check` lifecycle task in this build (no base/lifecycle plugin applied at root), so the BCV
// plugin never wires apiCheck into one — apiCheck is run as a dedicated advisory CI step
// (`:runtime:apiCheck :runtime-test:apiCheck :runtime-tracing-jvm:apiCheck` with `continue-on-error: true`, see .github/workflows/ci.yml) while
// :runtime is on the @ExperimentalStructuredCoroutines-gated 0.x line. Transition point
// (design it now, implement at 1.0.0): make apiCheck blocking in CI once the experimental gate
// is removed — no `check` lifecycle wiring is needed since this repo never runs one at root.
apiValidation {
    ignoredProjects.addAll(
        listOf(
            "annotations",
            "compiler",
            "gradle-plugin",
            "detekt-rules",
            "lint-rules",
            "intellij-plugin",
            "sample",
            "sample-detekt",
            "runtime-benchmark",
        )
    )
    klib {
        enabled = true
    }
}

allprojects {
    group = project.findProperty("PROJECT_GROUP")?.toString() ?: "io.github.santimattius"
    version = project.findProperty("PROJECT_VERSION")?.toString() ?: "0.1.0"

    repositories {
        mavenCentral()
        google()
    }

    // Skip signing when no signatory is configured (e.g. publishToMavenLocal without GPG)
    afterEvaluate {
        tasks.matching { it.name.startsWith("sign") }.configureEach {
            enabled = project.findProperty("signingInMemoryKey") != null
        }

        // Before publishing to Maven Local, remove existing artifacts for this publication in ~/.m2
        // so the same version is overwritten and consumers always get the latest local build.
        // Only clean the directory of the specific publication being published by this task to avoid
        // deleting artifacts from sibling publications (e.g. pluginMaven + marker in gradle-plugin).
        tasks.matching { it.name.endsWith("ToMavenLocal") && it.name != "publishToMavenLocal" }.configureEach {
            val taskName = this.name
            doFirst {
                val ext = project.extensions.findByName("publishing") as? org.gradle.api.publish.PublishingExtension ?: return@doFirst
                // Extract publication name from task: "publish{Name}PublicationToMavenLocal"
                val taskPubName = taskName.removePrefix("publish").removeSuffix("ToMavenLocal").removeSuffix("Publication")
                for (pub in ext.publications) {
                    if (pub is org.gradle.api.publish.maven.MavenPublication &&
                        pub.name.equals(taskPubName, ignoreCase = true)) {
                        val repo = file("${System.getProperty("user.home")}/.m2/repository")
                        val path = repo.resolve(pub.groupId.replace('.', '/')).resolve(pub.artifactId).resolve(pub.version)
                        if (path.exists()) {
                            path.deleteRecursively()
                            logger.lifecycle("Cleaned Maven local: $path")
                        }
                    }
                }
            }
        }
    }
}

// Run tests for all modules that have unit tests (excludes :sample, which fails compilation by design)
tasks.register("testAll") {
    group = "verification"
    description = "Runs tests in compiler, detekt-rules, gradle-plugin, lint-rules, intellij-plugin (excludes sample)"
    dependsOn(
        ":compiler:test",
        ":detekt-rules:test",
        ":gradle-plugin:test",
        ":lint-rules:test",
        ":intellij-plugin:test",
    )
}

// Validate that the sample project fails compilation with expected compiler rule codes (run as part of :compiler:test)
tasks.register("validateSample") {
    group = "verification"
    description = "Validates :sample with the compiler: runs compiler tests which include 'sample project fails compilation with expected rule codes'"
    dependsOn(":compiler:test")
}

tasks.register<Exec>("generateSkillRefs") {
    group = "documentation"
    description = "Regenerates kotlin-coroutines-skill references from BEST_PRACTICES (see tools/scripts/README.md)"
    workingDir = rootDir
    commandLine("python3", "tools/scripts/generate_refs.py")
}

tasks.register<Exec>("checkSkillRefs") {
    group = "verification"
    description = "Fails if generated kotlin-coroutines-skill references are out of date"
    workingDir = rootDir
    commandLine("python3", "tools/scripts/generate_refs.py", "--check")
}

// Toolkit and runtime artifacts have independent release trains. :lint-rules is intentionally
// excluded: it applies raw `maven-publish` with a hand-built MavenPublication (lint-JAR
// packaging), so it has no publishToMavenCentral task. It is published independently via
// .github/workflows/publish-lint-rules.yml.
tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publishes PROJECT_VERSION toolkit artifacts to Maven Central"
    dependsOn(
        ":annotations:publishToMavenCentral",
        ":compiler:publishToMavenCentral",
        ":detekt-rules:publishToMavenCentral",
        ":gradle-plugin:publishToMavenCentral",
    )
}

tasks.register("publishRuntimeToMavenCentral") {
    group = "publishing"
    description = "Publishes all RUNTIME_VERSION artifacts to Maven Central"
    dependsOn(
        ":runtime:publishToMavenCentral",
        ":runtime-test:publishToMavenCentral",
        ":runtime-tracing-jvm:publishToMavenCentral",
    )
}
