/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [DisabledSeverityAdvisory]. Pure function, no Gradle [org.gradle.api.Project]
 * dependency required — see design ADR-5 for the rationale.
 */
class DisabledSeverityAdvisoryTest {

    @Test
    fun `message returns null when no checks are disabled`() {
        assertNull(DisabledSeverityAdvisory.message(emptyList()))
    }

    @Test
    fun `message names the disabled check key`() {
        val message = DisabledSeverityAdvisory.message(listOf("loopWithoutYield"))

        assertTrue(message != null && "loopWithoutYield" in message, "Expected message to name the disabled key, was: $message")
    }

    @Test
    fun `message references the tracking issue URL`() {
        val message = DisabledSeverityAdvisory.message(listOf("loopWithoutYield"))

        assertTrue(
            message != null && DisabledSeverityAdvisory.TRACKING_ISSUE in message,
            "Expected message to reference ${DisabledSeverityAdvisory.TRACKING_ISSUE}, was: $message",
        )
    }

    @Test
    fun `message aggregates multiple disabled keys into one advisory`() {
        val message = DisabledSeverityAdvisory.message(listOf("loopWithoutYield", "suspendInFinally"))

        assertTrue(
            message != null && "loopWithoutYield" in message && "suspendInFinally" in message,
            "Expected message to name both disabled keys, was: $message",
        )
    }

    @Test
    fun `tracking issue points to issue 68`() {
        assertEquals("https://github.com/santimattius/structured-coroutines/issues/68", DisabledSeverityAdvisory.TRACKING_ISSUE)
    }

    // ============================================================================
    // Functional (Gradle TestKit) — verifies the plugin's afterEvaluate wiring
    // ============================================================================

    private fun createTestProject(structuredCoroutinesBlock: String): File {
        val projectDir = File.createTempFile("disabled-severity-test-project", "").apply {
            delete()
            mkdirs()
        }

        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "disabled-severity-test-project"

            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
            }
            """.trimIndent(),
        )

        val pluginVersion = System.getProperty("structuredCoroutines.version", "0.3.0")
        val kotlinVersion = System.getProperty("kotlinVersion")
            ?: error("kotlinVersion system property not set — check gradle-plugin/build.gradle.kts tasks.test block")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("io.github.santimattius.structured-coroutines") version "$pluginVersion"
            }

            structuredCoroutines {
                $structuredCoroutinesBlock
            }

            kotlin {
                jvmToolchain(17)
            }
            """.trimIndent(),
        )

        return projectDir
    }

    private fun runBuild(projectDir: File): String =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("help")
            .forwardOutput()
            .build()
            .output

    @Test
    fun `disabled severity emits an advisory warning naming the check and issue 68`() {
        val projectDir = createTestProject("""loopWithoutYield.set("disabled")""")

        val output = runBuild(projectDir)

        assertTrue("BUILD SUCCESSFUL" in output, "Expected successful build but got:\n$output")
        assertTrue("loopWithoutYield" in output, "Expected output to name the disabled check, was:\n$output")
        assertTrue(DisabledSeverityAdvisory.TRACKING_ISSUE in output, "Expected output to reference #68, was:\n$output")
    }

    @Test
    fun `disabled severity advisory fires once per project not once per compilation`() {
        val projectDir = createTestProject("""loopWithoutYield.set("disabled")""")

        val output = runBuild(projectDir)

        // A default kotlin("jvm") project configures at least two KotlinCompilations (main, test).
        // If the advisory were wired into applyToCompilation instead of afterEvaluate, it would
        // appear more than once in the captured output.
        val occurrences = Regex(Regex.escape(DisabledSeverityAdvisory.TRACKING_ISSUE)).findAll(output).count()
        assertEquals(1, occurrences, "Expected exactly one advisory (once per project), found $occurrences in:\n$output")
    }

    @Test
    fun `unrecognized severity value does not trigger the disabled advisory`() {
        val projectDir = createTestProject("""loopWithoutYield.set("disabed")""")

        val output = runBuild(projectDir)

        assertTrue("BUILD SUCCESSFUL" in output, "Expected successful build but got:\n$output")
        assertFalse(DisabledSeverityAdvisory.TRACKING_ISSUE in output, "Typo value must not trigger the disabled advisory, was:\n$output")
    }

    // ============================================================================
    // Report task rendering — "disabled" must render distinctly from error/warning
    // ============================================================================

    private fun buildReportTask(loopWithoutYieldSeverity: String): StructuredCoroutinesReportTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testReport", StructuredCoroutinesReportTask::class.java).get()

        task.projectName.set("report-test-project")
        task.pluginVersion.set("test-version")

        task.globalScopeUsage.set("error")
        task.inlineCoroutineScope.set("error")
        task.unstructuredLaunch.set("error")
        task.runBlockingInSuspend.set("error")
        task.jobInBuilderContext.set("error")
        task.dispatchersUnconfined.set("warning")
        task.cancellationExceptionSubclass.set("error")
        task.suspendInFinally.set("warning")
        task.cancellationExceptionSwallowed.set("warning")
        task.unusedDeferred.set("error")
        task.redundantLaunchInCoroutineScope.set("warning")
        task.loopWithoutYield.set(loopWithoutYieldSeverity)
        task.suspendCoroutineWithoutCancellation.set("error")
        task.callbackFlowWithoutAwaitClose.set("error")

        task.excludedSourceSets.set(emptyList())
        task.excludedProjects.set(emptyList())

        task.reportFormat.set("all")
        task.outputDir.set(project.layout.buildDirectory.dir("reports/structured-coroutines"))

        return task
    }

    @Test
    fun `text report shows a distinct DISABLED severity, not coerced to error or warning`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "disabled")
        task.generate()

        val text = task.outputDir.get().asFile.resolve("structured-coroutines-report.txt").readText()
        val loopWithoutYieldLine = text.lines().single { "CANCEL_001" in it }

        assertTrue("DISABLED" in loopWithoutYieldLine, "Expected DISABLED severity in line: $loopWithoutYieldLine")
        assertFalse("ERROR" in loopWithoutYieldLine, "Disabled rule must not render as ERROR: $loopWithoutYieldLine")
        assertFalse("WARNING" in loopWithoutYieldLine, "Disabled rule must not render as WARNING: $loopWithoutYieldLine")
    }

    @Test
    fun `text report summary counts the disabled rule separately`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "disabled")
        task.generate()

        val text = task.outputDir.get().asFile.resolve("structured-coroutines-report.txt").readText()
        val summaryLine = text.lines().single { it.trimStart().startsWith("Summary:") }

        assertTrue("1 disabled" in summaryLine, "Expected summary to count the disabled rule, was: $summaryLine")
    }

    @Test
    fun `html report uses a distinct disabled badge class, not the warning badge`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "disabled")
        task.generate()

        val html = task.outputDir.get().asFile.resolve("structured-coroutines-report.html").readText()

        assertTrue("badge-disabled" in html, "Expected a dedicated badge-disabled CSS class in HTML output")
        assertTrue(">DISABLED<" in html, "Expected the HTML badge text to read DISABLED")
    }

    @Test
    fun `html report footnotes the deferred enforcement tracking issue when a rule is disabled`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "disabled")
        task.generate()

        val html = task.outputDir.get().asFile.resolve("structured-coroutines-report.html").readText()

        assertTrue(DisabledSeverityAdvisory.TRACKING_ISSUE in html, "Expected the #68 tracking issue link in the HTML footnote")
    }

    @Test
    fun `report renders error and warning severities unchanged when nothing is disabled`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "warning")
        task.generate()

        val text = task.outputDir.get().asFile.resolve("structured-coroutines-report.txt").readText()
        val summaryLine = text.lines().single { it.trimStart().startsWith("Summary:") }

        assertFalse("disabled" in summaryLine, "Summary must not mention disabled rules when none are disabled: $summaryLine")
    }
}
