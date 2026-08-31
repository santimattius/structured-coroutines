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
import org.jetbrains.kotlin.diagnostics.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 1.8/1.9 (severity-enforcement, #68): resolution matrix for
 * [PluginConfiguration.effectiveSeverityOf] — 14 rules x {unset, error, warning, disabled,
 * garbage}. This is the tri-state successor to `getSeverity`; unlike `getSeverity`, "disabled"
 * MUST resolve to [RuleSeverity.DISABLED], not silently fall back to a [Severity] value.
 *
 * Negative controls are mandatory (per design.md "Testing strategy"): the "unset" and "garbage"
 * cases below prove the fallback path independently of the "disabled"/"error"/"warning" paths,
 * so a resolver that always returns DISABLED (or always returns the default) cannot pass all of
 * them.
 */
@OptIn(CompilerConfiguration.Internals::class)
class PluginConfigurationEffectiveSeverityTest {

    private fun configOf(options: Map<String, String>): PluginConfiguration {
        val configuration = CompilerConfiguration()
        configuration.put(PluginConfiguration.OPTIONS_KEY, options)
        return PluginConfiguration(configuration)
    }

    private fun defaultRuleSeverityOf(rule: ScoroutinesRule): RuleSeverity = when (rule.defaultSeverity) {
        Severity.ERROR -> RuleSeverity.ERROR
        Severity.WARNING -> RuleSeverity.WARNING
        else -> error("No ScoroutinesRule documents a default of ${rule.defaultSeverity}")
    }

    @Test
    fun `unset rule resolves to its documented default tri-state, for all 14 rules`() {
        val pluginConfiguration = configOf(emptyMap())
        ScoroutinesRule.entries.forEach { rule ->
            assertEquals(
                defaultRuleSeverityOf(rule),
                pluginConfiguration.effectiveSeverityOf(rule),
                "rule=${rule.optionKey}",
            )
        }
    }

    @Test
    fun `error value resolves to ERROR, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val pluginConfiguration = configOf(mapOf(rule.optionKey to "error"))
            assertEquals(RuleSeverity.ERROR, pluginConfiguration.effectiveSeverityOf(rule), "rule=${rule.optionKey}")
        }
    }

    @Test
    fun `warning value resolves to WARNING, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val pluginConfiguration = configOf(mapOf(rule.optionKey to "warning"))
            assertEquals(RuleSeverity.WARNING, pluginConfiguration.effectiveSeverityOf(rule), "rule=${rule.optionKey}")
        }
    }

    @Test
    fun `disabled value resolves to the disabled tri-state, not a Severity fallback, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val pluginConfiguration = configOf(mapOf(rule.optionKey to "disabled"))
            assertEquals(RuleSeverity.DISABLED, pluginConfiguration.effectiveSeverityOf(rule), "rule=${rule.optionKey}")
        }
    }

    @Test
    fun `unrecognized garbage value falls back to the documented default with no failure, for all 14 rules`() {
        ScoroutinesRule.entries.forEach { rule ->
            val pluginConfiguration = configOf(mapOf(rule.optionKey to "disabed"))
            assertEquals(
                defaultRuleSeverityOf(rule),
                pluginConfiguration.effectiveSeverityOf(rule),
                "rule=${rule.optionKey}",
            )
        }
    }

    @Test
    fun `resolution is case-insensitive`() {
        val upper = configOf(mapOf("loopWithoutYield" to "ERROR"))
        val mixed = configOf(mapOf("loopWithoutYield" to "DiSaBlEd"))
        assertEquals(RuleSeverity.ERROR, upper.effectiveSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
        assertEquals(RuleSeverity.DISABLED, mixed.effectiveSeverityOf(ScoroutinesRule.LOOP_WITHOUT_YIELD))
    }
}
