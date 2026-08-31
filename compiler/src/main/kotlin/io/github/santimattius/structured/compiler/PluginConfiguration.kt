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

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

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

    /**
     * Resolves the raw configured tri-state severity for [rule] (#68, ADR-1/ADR-2) — what the
     * user asked for, ignoring the ADR-7 grace-period direction rule.
     *
     * Unlike [getSeverity], this recognizes `"disabled"` as the real [RuleSeverity.DISABLED]
     * state rather than silently falling back to a [Severity] value. An unrecognized value
     * (e.g. a typo) falls back to [ScoroutinesRule.defaultSeverity] with no build failure —
     * the same regression-safe fallback behavior `getSeverity` already had for #67.
     *
     * Use [resolvedSeverityOf] to get what actually reports at compile time; this method is the
     * raw resolution step that one builds on.
     */
    fun effectiveSeverityOf(rule: ScoroutinesRule): RuleSeverity =
        when (options[rule.optionKey]?.lowercase()) {
            "error" -> RuleSeverity.ERROR
            "warning" -> RuleSeverity.WARNING
            "disabled" -> RuleSeverity.DISABLED
            else -> rule.defaultSeverity.toRuleSeverity()
        }

    /**
     * The [EnforcementPolicy] this compilation resolves to (#68, ADR-7): the 15th plugin option
     * `severityEnforcement` (`"grace"` | `"strict"`, case-insensitive) overrides
     * [SeverityGracePeriod.DEFAULT_POLICY] when set to a recognized value; anything else
     * (unset, or an unrecognized value) falls back to [SeverityGracePeriod.DEFAULT_POLICY] with
     * no build failure — the same regression-safe fallback convention as [effectiveSeverityOf].
     */
    val policy: EnforcementPolicy = when (options["severityEnforcement"]?.lowercase()) {
        "grace" -> EnforcementPolicy.GRACE
        "strict" -> EnforcementPolicy.STRICT
        else -> SeverityGracePeriod.DEFAULT_POLICY
    }

    /**
     * Resolves the severity that actually reports at compile time for [rule] (#68, ADR-7) — the
     * grace-period *direction rule*, layered on top of [effectiveSeverityOf]:
     *
     * - `configured.rank <= default.rank` is a **relaxation**: applies immediately, regardless of
     *   [policy] — it can only make a previously-failing build pass, never break one.
     * - `configured.rank > default.rank` is a **tightening**: under [EnforcementPolicy.GRACE] it
     *   is deferred (the rule keeps reporting at [ScoroutinesRule.defaultSeverity]); under
     *   [EnforcementPolicy.STRICT] it applies immediately.
     */
    fun resolvedSeverityOf(rule: ScoroutinesRule): RuleSeverity {
        val configured = effectiveSeverityOf(rule)
        val default = rule.defaultSeverity.toRuleSeverity()
        val isTightening = configured.rank > default.rank
        return if (isTightening && policy == EnforcementPolicy.GRACE) default else configured
    }

    /**
     * Every rule currently deferred under [EnforcementPolicy.GRACE] (#68, ADR-7) — the
     * list-building companion the grace-period advisory (task 3.8) emits from. Empty under
     * [EnforcementPolicy.STRICT], since nothing is ever deferred there.
     */
    val deferredTightenings: List<DeferredTightening> by lazy {
        if (policy != EnforcementPolicy.GRACE) {
            emptyList()
        } else {
            ScoroutinesRule.entries.mapNotNull { rule ->
                val configured = effectiveSeverityOf(rule)
                val default = rule.defaultSeverity.toRuleSeverity()
                if (configured.rank > default.rank) {
                    DeferredTightening(rule = rule, configured = configured, effective = default)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Single report gate for every configurable checker (#68, ADR-5).
 *
 * Short-circuits when [rule] resolves (via [PluginConfiguration.resolvedSeverityOf]) to
 * [RuleSeverity.DISABLED] — this is the one place "disabled" actually suppresses a diagnostic,
 * replacing the 14 duplicated no-op call sites a per-checker `if` would require. Otherwise
 * selects the matching ERROR/WARNING twin via [StructuredCoroutinesErrors.factoryFor] (ADR-6):
 * the rule's default-named factory when [PluginConfiguration.resolvedSeverityOf] equals
 * [ScoroutinesRule.defaultSeverity], the suffixed twin otherwise. This is the natural completion
 * of Slice B — without it, the twin factories added in ADR-6 would be dead code.
 */
internal fun PluginConfiguration.report(
    reporter: DiagnosticReporter,
    rule: ScoroutinesRule,
    source: KtSourceElement?,
    context: CheckerContext,
) {
    val severity = resolvedSeverityOf(rule).toDiagnostic() ?: return
    reporter.reportOn(source, StructuredCoroutinesErrors.factoryFor(rule, severity), context)
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
