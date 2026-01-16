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
        
        // These should match the published artifact coordinates
        const val GROUP_ID = "io.github.santimattius"
        const val ARTIFACT_ID = "structured-coroutines-compiler"
        const val VERSION = "0.1.0"
    }

    override fun apply(target: Project) {
        // No additional configuration needed
        // The compiler plugin is automatically applied by KotlinCompilerPluginSupportPlugin
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        // Apply to all Kotlin compilations
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
        // No additional options needed for the MVP
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}
