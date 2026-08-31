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

/**
 * The two enforcement modes a compilation can resolve to (#68, ADR-7).
 *
 * [GRACE] defers a *tightening* severity change (e.g. `warning` -> `error`) by one release: the
 * rule keeps reporting at its documented default severity, and an advisory names the future
 * enforcing release. [STRICT] applies every configured severity immediately, with no deferral.
 *
 * A *relaxation* (e.g. `error` -> `warning`, or anything -> `disabled`) always applies immediately
 * regardless of policy — it can only make a previously-failing build pass, never break one.
 */
enum class EnforcementPolicy { GRACE, STRICT }

/**
 * Fixed one-version grace-period window for severity tightening (#68, ADR-7).
 *
 * `DEFAULT_POLICY` is an *input* to [PluginConfiguration.effectiveSeverityOf] resolution, never
 * read at the point of use — this is what makes both GRACE and STRICT behavior unit-testable
 * without shipping two separate releases (see design.md "Testability" note). The 15th plugin
 * option `severityEnforcement` (`"grace"` | `"strict"`) lets a project opt into STRICT before
 * [ENFORCING_VERSION] ships, or stay on GRACE as a laggard escape hatch after it ships.
 *
 * ## Release sequence
 * - **1.2.0** (this release): `DEFAULT_POLICY = GRACE`. `disabled` and every relaxation enforce
 *   immediately; `warning -> error` tightening is deferred and only logs an advisory.
 * - **1.3.0** ([ENFORCING_VERSION]): one-line flip of `DEFAULT_POLICY` to `STRICT`. Tightening
 *   starts failing builds. `severityEnforcement = "grace"` stays honored as an explicit opt-out.
 */
object SeverityGracePeriod {
    /** The default enforcement policy shipped in this release. */
    val DEFAULT_POLICY: EnforcementPolicy = EnforcementPolicy.GRACE

    /**
     * The release in which [DEFAULT_POLICY] flips to [EnforcementPolicy.STRICT]. Every advisory
     * message MUST reference this constant directly (never a duplicated string literal), so a
     * half-done 1.3.0 flip (bumping this without updating the message, or vice versa) cannot
     * silently drift.
     */
    const val ENFORCING_VERSION: String = "1.3.0"
}

/**
 * A single rule whose configured severity is stricter than its currently-enforced severity under
 * [EnforcementPolicy.GRACE] (#68, ADR-7). [configured] is what the user asked for; [effective] is
 * what actually reports today (always the rule's documented default, per the direction rule in
 * [PluginConfiguration.effectiveSeverityOf]).
 */
data class DeferredTightening(
    val rule: ScoroutinesRule,
    val configured: RuleSeverity,
    val effective: RuleSeverity,
)

/**
 * Human-readable advisory naming the rule, the configured (tightened) value, the
 * currently-still-effective severity, and [SeverityGracePeriod.ENFORCING_VERSION] — the exact
 * fields the spec's "Tightening severity is deferred one release with an advisory" scenario
 * requires (#68).
 */
internal fun DeferredTightening.advisoryText(): String =
    "Structured Coroutines: rule '${rule.optionKey}' is configured to " +
        "\"${configured.name.lowercase()}\" but keeps reporting as " +
        "\"${effective.name.lowercase()}\" during the grace period. It will start enforcing " +
        "\"${configured.name.lowercase()}\" in version ${SeverityGracePeriod.ENFORCING_VERSION}. " +
        "Set severityEnforcement = \"strict\" to enforce it now."
