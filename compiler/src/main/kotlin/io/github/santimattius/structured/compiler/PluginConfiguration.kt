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
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.diagnostics.Severity

/**
 * Configuration for the Structured Coroutines compiler plugin.
 *
 * Options are stored in [CompilerConfiguration] as a single [Map]<[String],[String]> under
 * [OPTIONS_KEY]. A [CommandLineProcessor] (or a test) populates that map before constructing
 * this class. Values are `"error"` or `"warning"` (case-insensitive); anything else falls
 * back to the default severity for that rule.
 *
 * Using a single map key avoids the [CompilerConfigurationKey] reference-equality trap:
 * [CompilerConfigurationKey.create] does not override equals/hashCode, so two calls with
 * the same string produce unequal keys and `configuration.get` always returns null.
 */
class PluginConfiguration(configuration: CompilerConfiguration) {

    companion object {
        /**
         * Single static key used to store all plugin option key→value pairs in
         * [CompilerConfiguration].
         */
        val OPTIONS_KEY: CompilerConfigurationKey<Map<String, String>> =
            CompilerConfigurationKey.create("io.github.santimattius.structured-coroutines.options")
    }

    private val options: Map<String, String> = configuration.get(OPTIONS_KEY) ?: emptyMap()

    val globalScopeUsage: Severity = getSeverity("globalScopeUsage", Severity.ERROR)
    val inlineCoroutineScope: Severity = getSeverity("inlineCoroutineScope", Severity.ERROR)
    val unstructuredLaunch: Severity = getSeverity("unstructuredLaunch", Severity.ERROR)
    val runBlockingInSuspend: Severity = getSeverity("runBlockingInSuspend", Severity.ERROR)
    val jobInBuilderContext: Severity = getSeverity("jobInBuilderContext", Severity.ERROR)
    val dispatchersUnconfined: Severity = getSeverity("dispatchersUnconfined", Severity.WARNING)
    val cancellationExceptionSubclass: Severity = getSeverity("cancellationExceptionSubclass", Severity.ERROR)
    val suspendInFinally: Severity = getSeverity("suspendInFinally", Severity.WARNING)
    val cancellationExceptionSwallowed: Severity = getSeverity("cancellationExceptionSwallowed", Severity.WARNING)
    val unusedDeferred: Severity = getSeverity("unusedDeferred", Severity.ERROR)
    val redundantLaunchInCoroutineScope: Severity = getSeverity("redundantLaunchInCoroutineScope", Severity.WARNING)
    val loopWithoutYield: Severity = getSeverity("loopWithoutYield", Severity.WARNING)
    val suspendCoroutineWithoutCancellation: Severity = getSeverity("suspendCoroutineWithoutCancellation", Severity.ERROR)
    val callbackFlowWithoutAwaitClose: Severity = getSeverity("callbackFlowWithoutAwaitClose", Severity.ERROR)

    private fun getSeverity(key: String, defaultSeverity: Severity): Severity =
        when (options[key]?.lowercase()) {
            "error" -> Severity.ERROR
            "warning" -> Severity.WARNING
            else -> defaultSeverity
        }

    /**
     * Resolves the effective tri-state severity for [rule] (#68, ADR-1/ADR-2).
     *
     * Unlike [getSeverity], this recognizes `"disabled"` as the real [RuleSeverity.DISABLED]
     * state rather than silently falling back to a [Severity] value. An unrecognized value
     * (e.g. a typo) falls back to [ScoroutinesRule.defaultSeverity] with no build failure —
     * the same regression-safe fallback behavior `getSeverity` already had for #67.
     *
     * This method does not yet apply the ADR-7 grace-period direction rule (Phase 3); it is the
     * raw resolution step that phase builds on.
     */
    fun effectiveSeverityOf(rule: ScoroutinesRule): RuleSeverity =
        when (options[rule.optionKey]?.lowercase()) {
            "error" -> RuleSeverity.ERROR
            "warning" -> RuleSeverity.WARNING
            "disabled" -> RuleSeverity.DISABLED
            else -> rule.defaultSeverity.toRuleSeverity()
        }
}

/**
 * Every [ScoroutinesRule.defaultSeverity] is either [Severity.ERROR] or [Severity.WARNING] —
 * no rule defaults to disabled — so this conversion is total in practice; any other [Severity]
 * value indicates a rule was misconfigured with an unsupported default.
 */
private fun Severity.toRuleSeverity(): RuleSeverity = when (this) {
    Severity.ERROR -> RuleSeverity.ERROR
    Severity.WARNING -> RuleSeverity.WARNING
    else -> error("Unsupported default Severity for a ScoroutinesRule: $this")
}

/**
 * Tri-state severity (#68, ADR-2): [org.jetbrains.kotlin.diagnostics.Severity] has no
 * disabled/off member, so it cannot represent a rule that must report nothing at all.
 *
 * [rank] is an explicit constructor value, never `ordinal`. ADR-7's relax/tighten direction
 * rule compares `configured.rank` against `default.rank`; if rank were derived from enum
 * declaration order, reordering these entries would silently invert that comparison.
 */
enum class RuleSeverity(val rank: Int) {
    DISABLED(0),
    WARNING(1),
    ERROR(2);

    /** `null` means DISABLED — the only state [Severity] cannot represent. */
    fun toDiagnostic(): Severity? = when (this) {
        DISABLED -> null
        WARNING -> Severity.WARNING
        ERROR -> Severity.ERROR
    }
}
