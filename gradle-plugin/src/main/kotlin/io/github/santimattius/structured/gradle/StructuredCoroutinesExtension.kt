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

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring Structured Coroutines compiler plugin.
 *
 * This extension allows you to configure the severity level for each rule,
 * enabling gradual migration from warnings to errors.
 *
 * ## Usage
 *
 * ```kotlin
 * structuredCoroutines {
 *     // Configure severity for specific rules
 *     globalScopeUsage.set("error")  // or "warning"
 *     inlineCoroutineScope.set("error")
 *     unstructuredLaunch.set("error")
 *     runBlockingInSuspend.set("error")
 *     jobInBuilderContext.set("error")
 *     dispatchersUnconfined.set("warning")  // Default is warning
 *     cancellationExceptionSubclass.set("error")
 *     suspendInFinally.set("warning")  // Default is warning
 *     cancellationExceptionSwallowed.set("warning")  // Default is warning
 *     unusedDeferred.set("error")
 *     redundantLaunchInCoroutineScope.set("warning")  // Default is warning
 * }
 * ```
 *
 * ## Severity Levels
 *
 * - `"error"` - Reports as compilation error (blocks build)
 * - `"warning"` - Reports as warning (allows build to succeed)
 *
 * ## Default Behavior
 *
 * If a rule is not configured, it uses its default severity:
 * - Most rules default to `"error"`
 * - Some rules default to `"warning"` (e.g., `dispatchersUnconfined`, `suspendInFinally`)
 */
interface StructuredCoroutinesExtension {
    
    /**
     * Severity for GlobalScope usage rule.
     * Default: "error"
     */
    val globalScopeUsage: Property<String>
    
    /**
     * Severity for inline CoroutineScope creation rule.
     * Default: "error"
     */
    val inlineCoroutineScope: Property<String>
    
    /**
     * Severity for unstructured launch rule.
     * Default: "error"
     */
    val unstructuredLaunch: Property<String>
    
    /**
     * Severity for runBlocking in suspend functions rule.
     * Default: "error"
     */
    val runBlockingInSuspend: Property<String>
    
    /**
     * Severity for Job()/SupervisorJob() in builder context rule.
     * Default: "error"
     */
    val jobInBuilderContext: Property<String>
    
    /**
     * Severity for Dispatchers.Unconfined usage rule.
     * Default: "warning"
     */
    val dispatchersUnconfined: Property<String>
    
    /**
     * Severity for CancellationException subclass rule.
     * Default: "error"
     */
    val cancellationExceptionSubclass: Property<String>
    
    /**
     * Severity for suspend calls in finally without NonCancellable rule.
     * Default: "warning"
     */
    val suspendInFinally: Property<String>
    
    /**
     * Severity for catch(Exception) swallowing CancellationException rule.
     * Default: "warning"
     */
    val cancellationExceptionSwallowed: Property<String>
    
    /**
     * Severity for unused Deferred (async without await) rule.
     * Default: "error"
     */
    val unusedDeferred: Property<String>
    
    /**
     * Severity for redundant launch in coroutineScope rule.
     * Default: "warning"
     */
    val redundantLaunchInCoroutineScope: Property<String>

    /**
     * Source set (compilation) names to exclude from the compiler plugin.
     * Excluded compilations will not run the Structured Coroutines plugin.
     * Use for legacy modules or test source sets during migration.
     * Example: `excludeSourceSets("legacyMain", "test")`
     */
    val excludeSourceSets: ListProperty<String>

    /**
     * Project paths to exclude from the compiler plugin (e.g. `:legacy-module`, `:app:old`).
     * All compilations of excluded projects will not run the plugin.
     */
    val excludeProjects: ListProperty<String>

    /**
     * Excludes the given source set (compilation) names from the plugin.
     * Names match [KotlinCompilation.getName] (e.g. "main", "test", "jvmMain").
     */
    fun excludeSourceSets(vararg names: String) {
        excludeSourceSets.set(excludeSourceSets.getOrElse(emptyList()) + names.toList())
    }

    /**
     * Excludes the given project paths from the plugin.
     * Use project path format (e.g. ":subproject", ":app:feature").
     */
    fun excludeProjects(vararg paths: String) {
        excludeProjects.set(excludeProjects.getOrElse(emptyList()) + paths.toList())
    }

    /**
     * Applies the **strict** profile: 7 rules as error, 4 as warning (defaults).
     * Use for greenfield projects or when you want the build to fail on violations.
     */
    fun useStrictProfile() {
        globalScopeUsage.set("error")
        inlineCoroutineScope.set("error")
        unstructuredLaunch.set("error")
        runBlockingInSuspend.set("error")
        jobInBuilderContext.set("error")
        cancellationExceptionSubclass.set("error")
        unusedDeferred.set("error")
        dispatchersUnconfined.set("warning")
        suspendInFinally.set("warning")
        cancellationExceptionSwallowed.set("warning")
        redundantLaunchInCoroutineScope.set("warning")
    }

    /**
     * Applies the **gradual** profile: all 11 rules as warning.
     * Use when migrating a legacy project so the build does not fail while you fix issues.
     */
    fun useGradualProfile() {
        globalScopeUsage.set("warning")
        inlineCoroutineScope.set("warning")
        unstructuredLaunch.set("warning")
        runBlockingInSuspend.set("warning")
        jobInBuilderContext.set("warning")
        cancellationExceptionSubclass.set("warning")
        unusedDeferred.set("warning")
        dispatchersUnconfined.set("warning")
        suspendInFinally.set("warning")
        cancellationExceptionSwallowed.set("warning")
        redundantLaunchInCoroutineScope.set("warning")
    }

    /**
     * Applies the **relaxed** profile: same as gradual (all rules as warning).
     * Use when you want to see findings without blocking the build.
     */
    fun useRelaxedProfile() {
        useGradualProfile()
    }
}
