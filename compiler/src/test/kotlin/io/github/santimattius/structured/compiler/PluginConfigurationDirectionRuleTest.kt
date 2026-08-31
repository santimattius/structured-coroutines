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

import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3.5/3.6 (severity-enforcement, #68, ADR-7): the grace-period *direction rule*, layered on
 * top of [PluginConfiguration.effectiveSeverityOf] by [PluginConfiguration.resolvedSeverityOf].
 *
 * - `configured.rank <= default.rank` is a **relaxation**: it can only make a previously-failing
 *   build pass, so it applies immediately regardless of [EnforcementPolicy].
 * - `configured.rank > default.rank` is a **tightening**: under [EnforcementPolicy.GRACE] it is
 *   deferred (the rule keeps reporting at its documented default severity); under
 *   [EnforcementPolicy.STRICT] it applies immediately.
 *
 * [PluginConfiguration.deferredTightenings] is the list-building companion the grace-period
 * advisory (task 3.8) consumes: every rule currently deferred under GRACE, and nothing else.
 */
@OptIn(CompilerConfiguration.Internals::class)
class PluginConfigurationDirectionRuleTest {

    private fun configOf(options: Map<String, String>): PluginConfiguration {
        val configuration = CompilerConfiguration()
        configuration.put(PluginConfiguration.OPTIONS_KEY, options)
        return PluginConfiguration(configuration)
    }

    // --- Relaxations: configured.rank <= default.rank -> immediate, regardless of policy ---

    @Test
    fun `error relaxed to disabled applies immediately under default GRACE policy`() {
        // unusedDeferred defaults to ERROR; disabled has rank 0 <= ERROR's rank 2.
        val config = configOf(mapOf("unusedDeferred" to "disabled"))
        assertEquals(RuleSeverity.DISABLED, config.resolvedSeverityOf(ScoroutinesRule.UNUSED_DEFERRED))
    }

    @Test
    fun `error relaxed to warning applies immediately even under explicit strict policy`() {
        // globalScopeUsage defaults to ERROR; warning has rank 1 <= ERROR's rank 2.
        val config = configOf(mapOf("globalScopeUsage" to "warning", "severityEnforcement" to "strict"))
        assertEquals(RuleSeverity.WARNING, config.resolvedSeverityOf(ScoroutinesRule.GLOBAL_SCOPE_USAGE))
    }

    @Test
    fun `configured equal to default is neither a relaxation nor a tightening and applies as configured`() {
        // suspendInFinally defaults to WARNING; configuring warning explicitly is rank-equal.
        val config = configOf(mapOf("suspendInFinally" to "warning"))
        assertEquals(RuleSeverity.WARNING, config.resolvedSeverityOf(ScoroutinesRule.SUSPEND_IN_FINALLY))
    }

    // --- Tightening under GRACE (default policy): deferred, default stays enforced ---

    @Test
    fun `warning tightened to error is deferred under default GRACE policy, default stays enforced`() {
        // loopWithoutYield defaults to WARNING; error has rank 2 > WARNING's rank 1.
        val config = configOf(mapOf("loopWithoutYield" to "error"))
        assertEquals(RuleSeverity.WARNING, config.resolvedSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
    }

    @Test
    fun `tightening deferred under GRACE for every WARNING-default rule`() {
        val warningDefaultRules = ScoroutinesRule.entries.filter { it.defaultSeverity == org.jetbrains.kotlin.diagnostics.Severity.WARNING }
        assertTrue(warningDefaultRules.isNotEmpty(), "sanity: at least one WARNING-default rule must exist")
        warningDefaultRules.forEach { rule ->
            val config = configOf(mapOf(rule.optionKey to "error"))
            assertEquals(
                RuleSeverity.WARNING,
                config.resolvedSeverityOf(rule),
                "rule=${rule.optionKey} must stay deferred at its WARNING default under GRACE",
            )
        }
    }

    // --- Tightening under explicit STRICT policy: applies immediately ---

    @Test
    fun `warning tightened to error applies immediately when severityEnforcement is strict`() {
        val config = configOf(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "strict"))
        assertEquals(RuleSeverity.ERROR, config.resolvedSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
    }

    @Test
    fun `severityEnforcement value is case-insensitive`() {
        val config = configOf(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "STRICT"))
        assertEquals(RuleSeverity.ERROR, config.resolvedSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
    }

    @Test
    fun `unrecognized severityEnforcement value falls back to the default GRACE policy`() {
        val config = configOf(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "garbage"))
        assertEquals(RuleSeverity.WARNING, config.resolvedSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
    }

    // --- deferredTightenings: the list-building companion for the grace-period advisory ---

    @Test
    fun `deferredTightenings is empty when nothing is configured`() {
        val config = configOf(emptyMap())
        assertEquals(emptyList(), config.deferredTightenings)
    }

    @Test
    fun `deferredTightenings is empty when only relaxations are configured`() {
        val config = configOf(mapOf("unusedDeferred" to "disabled", "globalScopeUsage" to "warning"))
        assertEquals(emptyList(), config.deferredTightenings)
    }

    @Test
    fun `deferredTightenings contains one entry per deferred tightening under GRACE`() {
        val config = configOf(mapOf("loopWithoutYield" to "error", "suspendInFinally" to "error"))
        val deferred = config.deferredTightenings
        assertEquals(2, deferred.size)
        assertTrue(
            deferred.any {
                it.rule == ScoroutinesRule.LOOP_WITHOUT_YIELD &&
                    it.configured == RuleSeverity.ERROR &&
                    it.effective == RuleSeverity.WARNING
            },
        )
        assertTrue(
            deferred.any {
                it.rule == ScoroutinesRule.SUSPEND_IN_FINALLY &&
                    it.configured == RuleSeverity.ERROR &&
                    it.effective == RuleSeverity.WARNING
            },
        )
    }

    @Test
    fun `deferredTightenings is empty under explicit strict policy even with a tightened rule`() {
        val config = configOf(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "strict"))
        assertEquals(emptyList(), config.deferredTightenings)
    }
}
