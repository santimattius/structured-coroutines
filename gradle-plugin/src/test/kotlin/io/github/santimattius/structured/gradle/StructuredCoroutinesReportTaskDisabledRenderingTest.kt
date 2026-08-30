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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [StructuredCoroutinesReportTask]'s distinct rendering of the "disabled" severity
 * state (text summary, HTML badge, HTML footnote).
 *
 * Relocated from `DisabledSeverityAdvisoryTest` (#68, Phase 2/PR2): that file's `message()` unit
 * tests and its `afterEvaluate` advisory-warning functional (TestKit) tests were deleted together
 * with `DisabledSeverityAdvisory` itself, because the advisory text ("not yet suppressed at
 * compile time") became factually false the moment `"disabled"` started really suppressing
 * diagnostics. These report-rendering tests are unrelated to that advisory — they pin how the
 * report task renders a rule that is configured `"disabled"` distinctly from `"error"`/
 * `"warning"` — so they are preserved here rather than deleted with the rest of that file.
 */
class StructuredCoroutinesReportTaskDisabledRenderingTest {

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
    fun `html report footnotes the severity-enforcement tracking issue when a rule is disabled`() {
        val task = buildReportTask(loopWithoutYieldSeverity = "disabled")
        task.generate()

        val html = task.outputDir.get().asFile.resolve("structured-coroutines-report.html").readText()

        assertTrue(
            StructuredCoroutinesReportTask.SEVERITY_ENFORCEMENT_TRACKING_ISSUE in html,
            "Expected the #68 tracking issue link in the HTML footnote",
        )
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
