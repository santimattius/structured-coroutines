/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.Severity

/**
 * Single registry of the 14 configurable Structured Coroutines rules (#68, ADR-1).
 *
 * Each entry carries the Gradle DSL / CLI option key ([optionKey]) and the rule's documented
 * default severity ([defaultSeverity]) when no override is configured. This enum is the single
 * source of truth consumed by:
 * - [StructuredCoroutinesCommandLineProcessor] to declare `pluginOptions`
 * - [PluginConfiguration] for severity resolution (`effectiveSeverityOf`)
 * - the future twin-factory selection (ADR-6, Slice B)
 *
 * It intentionally holds no reference to any `KtDiagnosticFactory` to avoid an initialization
 * cycle with `StructuredCoroutinesErrors`'s `init` block.
 */
enum class ScoroutinesRule(val optionKey: String, val defaultSeverity: Severity) {
    GLOBAL_SCOPE_USAGE("globalScopeUsage", Severity.ERROR),
    INLINE_COROUTINE_SCOPE("inlineCoroutineScope", Severity.ERROR),
    UNSTRUCTURED_LAUNCH("unstructuredLaunch", Severity.ERROR),
    RUN_BLOCKING_IN_SUSPEND("runBlockingInSuspend", Severity.ERROR),
    JOB_IN_BUILDER_CONTEXT("jobInBuilderContext", Severity.ERROR),
    DISPATCHERS_UNCONFINED("dispatchersUnconfined", Severity.WARNING),
    CANCELLATION_EXCEPTION_SUBCLASS("cancellationExceptionSubclass", Severity.ERROR),
    SUSPEND_IN_FINALLY("suspendInFinally", Severity.WARNING),
    CANCELLATION_EXCEPTION_SWALLOWED("cancellationExceptionSwallowed", Severity.WARNING),
    UNUSED_DEFERRED("unusedDeferred", Severity.ERROR),
    REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE("redundantLaunchInCoroutineScope", Severity.WARNING),
    LOOP_WITHOUT_YIELD("loopWithoutYield", Severity.WARNING),
    SUSPEND_COROUTINE_WITHOUT_CANCELLATION("suspendCoroutineWithoutCancellation", Severity.ERROR),
    CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE("callbackFlowWithoutAwaitClose", Severity.ERROR),
}
