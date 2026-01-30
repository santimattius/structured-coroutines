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
}
