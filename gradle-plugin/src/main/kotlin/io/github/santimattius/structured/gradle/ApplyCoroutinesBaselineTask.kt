package io.github.santimattius.structured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Post-processes a Detekt XML report using [BaselineProcessor] (REPORT_NEW_ONLY → info for baselined hits).
 */
abstract class ApplyCoroutinesBaselineTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    abstract val baselineFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val detektReportXml: RegularFileProperty

    @get:Input
    abstract val baselineMode: Property<String>

    @get:Input
    abstract val baselineEnabled: Property<Boolean>

    @get:OutputFile
    abstract val summaryReport: RegularFileProperty

    @TaskAction
    fun apply() {
        if (!baselineEnabled.get()) {
            logger.lifecycle("Coroutines baseline disabled — skipping applyCoroutinesBaseline.")
            return
        }
        val detektFile = detektReportXml.orNull?.asFile
        if (detektFile == null || !detektFile.exists()) {
            logger.lifecycle(
                "No Detekt XML at ${detektFile?.absolutePath ?: "(not configured)"} — skipping baseline apply.",
            )
            return
        }
        val baseline = BaselineProcessor.loadBaseline(baselineFile.get().asFile)
        val mode = BaselineMode.valueOf(baselineMode.get())
        val entries = DetektBaselineReportParser.parseReport(detektFile)
        val lines = buildList {
            add("Coroutines baseline apply (${mode.name})")
            add("Detekt report: ${detektFile.absolutePath}")
            add("Baseline entries: ${baseline.size}")
            add("")
            entries.forEach { entry ->
                val finding = entry.finding
                val configured = entry.detektSeverity
                val effective = BaselineProcessor.effectiveSeverity(finding, baseline, mode, configured)
                val baselined = BaselineProcessor.isBaselined(finding, baseline)
                add(
                    "${finding.relativePath}:${finding.line} [${finding.ruleId}] " +
                        "configured=$configured effective=$effective baselined=$baselined",
                )
            }
        }
        val out = summaryReport.get().asFile
        out.parentFile.mkdirs()
        out.writeText(lines.joinToString("\n"))
        val demoted = entries.count { BaselineProcessor.isBaselined(it.finding, baseline) }
        logger.lifecycle(
            "Wrote baseline summary (${entries.size} findings, $demoted baselined) to ${out.absolutePath}",
        )
    }
}
