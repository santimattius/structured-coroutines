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

/**
 * Task 1.1/1.2 (severity-enforcement, #68, ADR-1): [ScoroutinesRule] is the single registry of
 * the 14 configurable rules, replacing the 14 hand-written `val`s in [PluginConfiguration] and
 * the duplicated key literals in `StructuredCoroutinesGradlePlugin`. This test pins its 14
 * entries against the defaults currently hard-coded in `PluginConfiguration.kt:41-54`.
 */
class ScoroutinesRuleTest {

    // The exact optionKey -> defaultSeverity pairs from PluginConfiguration.kt:41-54, before
    // ScoroutinesRule existed. This is the ground truth this enum MUST reproduce exactly.
    private val expectedDefaults: Map<String, Severity> = mapOf(
        "globalScopeUsage" to Severity.ERROR,
        "inlineCoroutineScope" to Severity.ERROR,
        "unstructuredLaunch" to Severity.ERROR,
        "runBlockingInSuspend" to Severity.ERROR,
        "jobInBuilderContext" to Severity.ERROR,
        "dispatchersUnconfined" to Severity.WARNING,
        "cancellationExceptionSubclass" to Severity.ERROR,
        "suspendInFinally" to Severity.WARNING,
        "cancellationExceptionSwallowed" to Severity.WARNING,
        "unusedDeferred" to Severity.ERROR,
        "redundantLaunchInCoroutineScope" to Severity.WARNING,
        "loopWithoutYield" to Severity.WARNING,
        "suspendCoroutineWithoutCancellation" to Severity.ERROR,
        "callbackFlowWithoutAwaitClose" to Severity.ERROR,
    )

    @Test
    fun `has exactly 14 entries`() {
        assertEquals(14, ScoroutinesRule.entries.size)
    }

    @Test
    fun `each entry's optionKey and defaultSeverity match current PluginConfiguration defaults`() {
        val actual = ScoroutinesRule.entries.associate { it.optionKey to it.defaultSeverity }
        assertEquals(expectedDefaults, actual)
    }

    @Test
    fun `optionKeys are unique`() {
        val keys = ScoroutinesRule.entries.map { it.optionKey }
        assertEquals(keys.size, keys.toSet().size, "optionKey must be unique per rule: $keys")
    }
}
