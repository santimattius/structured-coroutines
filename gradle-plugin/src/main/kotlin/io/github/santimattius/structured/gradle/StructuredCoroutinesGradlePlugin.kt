package io.github.santimattius.structured.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Gradle plugin that integrates the Structured Coroutines compiler plugin.
 *
 * This plugin:
 * - Resolves the compiler plugin JAR artifact
 * - Passes it to the Kotlin compiler via -Xplugin
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     id("io.github.santimattius.structured-coroutines")
 * }
 * ```
 */
class StructuredCoroutinesGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val PLUGIN_ID = "io.github.santimattius.structured-coroutines"
        const val COMPILER_PLUGIN_ID = "io.github.santimattius.structured-coroutines"

        // These must match the published artifact coordinates
        const val GROUP_ID = "io.github.santimattius"
        const val ARTIFACT_ID = "structured-coroutines-compiler"
        const val VERSION = "0.6.0"
    }

    override fun apply(target: Project) {
        // Register the extension with default values
        val extension = target.extensions.create(
            "structuredCoroutines",
            StructuredCoroutinesExtension::class.java
        )

        // Set default severities
        extension.globalScopeUsage.convention("error")
        extension.inlineCoroutineScope.convention("error")
        extension.unstructuredLaunch.convention("error")
        extension.runBlockingInSuspend.convention("error")
        extension.jobInBuilderContext.convention("error")
        extension.dispatchersUnconfined.convention("warning")
        extension.cancellationExceptionSubclass.convention("error")
        extension.suspendInFinally.convention("warning")
        extension.cancellationExceptionSwallowed.convention("warning")
        extension.unusedDeferred.convention("error")
        extension.redundantLaunchInCoroutineScope.convention("warning")
        extension.loopWithoutYield.convention("warning")
        extension.excludeSourceSets.convention(emptyList())
        extension.excludeProjects.convention(emptyList())

        // Report defaults
        extension.reportOutputDir.convention(
            target.layout.buildDirectory.dir("reports/structured-coroutines")
        )
        extension.reportFormat.convention("all")

        // Register the report task
        target.tasks.register("structuredCoroutinesReport", StructuredCoroutinesReportTask::class.java) { task ->
            task.group = "reporting"
            task.description = "Generates an HTML/text report of the Structured Coroutines plugin configuration"

            task.projectName.set(target.name)
            task.pluginVersion.set(VERSION)

            task.globalScopeUsage.set(extension.globalScopeUsage)
            task.inlineCoroutineScope.set(extension.inlineCoroutineScope)
            task.unstructuredLaunch.set(extension.unstructuredLaunch)
            task.runBlockingInSuspend.set(extension.runBlockingInSuspend)
            task.jobInBuilderContext.set(extension.jobInBuilderContext)
            task.dispatchersUnconfined.set(extension.dispatchersUnconfined)
            task.cancellationExceptionSubclass.set(extension.cancellationExceptionSubclass)
            task.suspendInFinally.set(extension.suspendInFinally)
            task.cancellationExceptionSwallowed.set(extension.cancellationExceptionSwallowed)
            task.unusedDeferred.set(extension.unusedDeferred)
            task.redundantLaunchInCoroutineScope.set(extension.redundantLaunchInCoroutineScope)
            task.loopWithoutYield.set(extension.loopWithoutYield)

            task.excludedSourceSets.set(extension.excludeSourceSets)
            task.excludedProjects.set(extension.excludeProjects)

            task.reportFormat.set(extension.reportFormat)
            task.outputDir.set(extension.reportOutputDir)
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(StructuredCoroutinesExtension::class.java)
            ?: project.rootProject.extensions.findByType(StructuredCoroutinesExtension::class.java)
            ?: return true
        val excludedProjects = extension.excludeProjects.getOrElse(emptyList())
        if (project.path in excludedProjects) return false
        val excludedSourceSets = extension.excludeSourceSets.getOrElse(emptyList())
        if (kotlinCompilation.name in excludedSourceSets) return false
        return true
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = ARTIFACT_ID,
            version = VERSION
        )
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            val extension = kotlinCompilation.target.project.extensions.findByType(
                StructuredCoroutinesExtension::class.java
            ) ?: return@provider emptyList()
            
            buildList {
                // Pass severity configuration for each rule
                add(SubpluginOption("globalScopeUsage", extension.globalScopeUsage.get()))
                add(SubpluginOption("inlineCoroutineScope", extension.inlineCoroutineScope.get()))
                add(SubpluginOption("unstructuredLaunch", extension.unstructuredLaunch.get()))
                add(SubpluginOption("runBlockingInSuspend", extension.runBlockingInSuspend.get()))
                add(SubpluginOption("jobInBuilderContext", extension.jobInBuilderContext.get()))
                add(SubpluginOption("dispatchersUnconfined", extension.dispatchersUnconfined.get()))
                add(SubpluginOption("cancellationExceptionSubclass", extension.cancellationExceptionSubclass.get()))
                add(SubpluginOption("suspendInFinally", extension.suspendInFinally.get()))
                add(SubpluginOption("cancellationExceptionSwallowed", extension.cancellationExceptionSwallowed.get()))
                add(SubpluginOption("unusedDeferred", extension.unusedDeferred.get()))
                add(SubpluginOption("redundantLaunchInCoroutineScope", extension.redundantLaunchInCoroutineScope.get()))
                add(SubpluginOption("loopWithoutYield", extension.loopWithoutYield.get()))
            }
        }
    }
}
