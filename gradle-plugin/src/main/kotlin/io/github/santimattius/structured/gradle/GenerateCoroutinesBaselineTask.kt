package io.github.santimattius.structured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * MVP: documents how to refresh the baseline; merges placeholder when no Detekt XML is wired yet.
 *
 * Full multi-subproject merge is deferred — run `./gradlew detektMain --create-baseline` per module
 * and copy fingerprints into [baselineFile], or extend this task in a follow-up.
 */
abstract class GenerateCoroutinesBaselineTask : DefaultTask() {

    @get:OutputFile
    abstract val baselineFile: RegularFileProperty

    @get:Input
    abstract val autoUpdate: Property<Boolean>

    @get:Input
    abstract val detektReportXmlPaths: ListProperty<String>

    @TaskAction
    fun generate() {
        val out = baselineFile.get().asFile
        if (!autoUpdate.get()) {
            logger.lifecycle(
                "Baseline autoUpdate is false. Set baseline.autoUpdate=true or delete $out to regenerate.",
            )
        }
        val fromXml = detektReportXmlPaths.getOrElse(emptyList())
            .flatMap { path -> DetektBaselineReportParser.toFingerprints(DetektBaselineReportParser.parseReport(java.io.File(path))) }
        if (fromXml.isEmpty()) {
            logger.lifecycle(
                "No Detekt XML inputs configured. Wrote empty baseline template to ${out.absolutePath}. " +
                    "Populate via detekt --create-baseline or wire detektReportXmlPaths on this task.",
            )
            BaselineProcessor.writeBaseline(out, emptyList())
        } else {
            BaselineProcessor.writeBaseline(out, fromXml)
            logger.lifecycle("Wrote ${fromXml.size} baseline entries to ${out.absolutePath}")
        }
    }
}
