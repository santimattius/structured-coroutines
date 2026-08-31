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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Task 3.3/3.4 (severity-enforcement, #68, ADR-6): every configurable rule now has a paired
 * ERROR/WARNING [org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0]. The factory bound to the
 * rule's documented default severity keeps its original name (backward compatible for
 * `@Suppress`); the opposite-severity twin is suffixed `_WARNING` (for ERROR-default rules) or
 * `_ERROR` (for WARNING-default rules) — never the reverse, and never both renamed (ADR-6,
 * accepted breaking change: an existing `@Suppress("<NAME>")` only keeps matching on the
 * unreconfigured/default-severity path).
 *
 * [StructuredCoroutinesErrors.factoryFor] is the single selector both default and twin paths run
 * through at report time.
 */
class StructuredCoroutinesErrorsFactoryForTest {

    private val defaultNameOf: Map<ScoroutinesRule, String> = mapOf(
        ScoroutinesRule.GLOBAL_SCOPE_USAGE to "GLOBAL_SCOPE_USAGE",
        ScoroutinesRule.INLINE_COROUTINE_SCOPE to "INLINE_COROUTINE_SCOPE",
        ScoroutinesRule.UNSTRUCTURED_LAUNCH to "UNSTRUCTURED_COROUTINE_LAUNCH",
        ScoroutinesRule.RUN_BLOCKING_IN_SUSPEND to "RUN_BLOCKING_IN_SUSPEND",
        ScoroutinesRule.JOB_IN_BUILDER_CONTEXT to "JOB_IN_BUILDER_CONTEXT",
        ScoroutinesRule.DISPATCHERS_UNCONFINED to "DISPATCHERS_UNCONFINED_USAGE",
        ScoroutinesRule.CANCELLATION_EXCEPTION_SUBCLASS to "CANCELLATION_EXCEPTION_SUBCLASS",
        ScoroutinesRule.SUSPEND_IN_FINALLY to "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE",
        ScoroutinesRule.CANCELLATION_EXCEPTION_SWALLOWED to "CANCELLATION_EXCEPTION_SWALLOWED",
        ScoroutinesRule.UNUSED_DEFERRED to "UNUSED_DEFERRED",
        ScoroutinesRule.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE to "REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE",
        ScoroutinesRule.LOOP_WITHOUT_YIELD to "LOOP_WITHOUT_YIELD",
        ScoroutinesRule.SUSPEND_COROUTINE_WITHOUT_CANCELLATION to "SUSPEND_COROUTINE_WITHOUT_CANCELLATION",
        ScoroutinesRule.CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE to "CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE",
    )

    @Test
    fun `factoryFor at the rule's default severity keeps the original factory name, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val factory = StructuredCoroutinesErrors.factoryFor(rule, rule.defaultSeverity)
            assertEquals(defaultNameOf.getValue(rule), factory.name, "rule=${rule.optionKey}")
        }
    }

    @Test
    fun `factoryFor at the opposite severity returns a suffixed twin name, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val opposite = if (rule.defaultSeverity == Severity.ERROR) Severity.WARNING else Severity.ERROR
            val suffix = if (rule.defaultSeverity == Severity.ERROR) "_WARNING" else "_ERROR"
            val factory = StructuredCoroutinesErrors.factoryFor(rule, opposite)
            val expectedName = defaultNameOf.getValue(rule) + suffix
            assertEquals(expectedName, factory.name, "rule=${rule.optionKey}")
            assertNotEquals(defaultNameOf.getValue(rule), factory.name, "twin must not share the default name")
        }
    }

    @Test
    fun `every twin factory carries the opposite Severity from its default sibling`() {
        ScoroutinesRule.entries.forEach { rule ->
            val opposite = if (rule.defaultSeverity == Severity.ERROR) Severity.WARNING else Severity.ERROR
            val twin = StructuredCoroutinesErrors.factoryFor(rule, opposite)
            assertEquals(opposite, twin.severity, "rule=${rule.optionKey}")
        }
    }
}
