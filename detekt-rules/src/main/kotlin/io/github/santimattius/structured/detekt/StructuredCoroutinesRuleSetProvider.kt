/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt

import io.github.santimattius.structured.detekt.rules.BlockingCallInCoroutineRule
import io.github.santimattius.structured.detekt.rules.CancellationExceptionSubclassRule
import io.github.santimattius.structured.detekt.rules.ChannelNotClosedRule
import io.github.santimattius.structured.detekt.rules.ConsumeEachMultipleConsumersRule
import io.github.santimattius.structured.detekt.rules.CancellationExceptionSwallowedRule
import io.github.santimattius.structured.detekt.rules.DispatchersUnconfinedRule
import io.github.santimattius.structured.detekt.rules.ExternalScopeLaunchRule
import io.github.santimattius.structured.detekt.rules.FlowBlockingCallRule
import io.github.santimattius.structured.detekt.rules.GlobalScopeUsageRule
import io.github.santimattius.structured.detekt.rules.InlineCoroutineScopeRule
import io.github.santimattius.structured.detekt.rules.JobInBuilderContextRule
import io.github.santimattius.structured.detekt.rules.LoopWithoutYieldRule
import io.github.santimattius.structured.detekt.rules.RedundantLaunchInCoroutineScopeRule
import io.github.santimattius.structured.detekt.rules.RunBlockingInSuspendRule
import io.github.santimattius.structured.detekt.rules.RunBlockingWithDelayInTestRule
import io.github.santimattius.structured.detekt.rules.ScopeReuseAfterCancelRule
import io.github.santimattius.structured.detekt.rules.SuspendInFinallyRule
import io.github.santimattius.structured.detekt.rules.UnusedDeferredRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Detekt RuleSetProvider for Structured Coroutines rules.
 *
 * This provider registers custom rules that detect common coroutine anti-patterns
 * that cannot be detected at compile time but can be found through static analysis.
 *
 * ## Rules Included
 *
 * ### Compiler Plugin Rules
 *
 * 1. **GlobalScopeUsageRule** (Best Practice 1.1)
 *    - Detects GlobalScope.launch/async usage
 *    - Severity: CodeSmell (configurable)
 *    - Platforms: All
 *
 * 2. **InlineCoroutineScopeRule** (Best Practice 1.3)
 *    - Detects CoroutineScope(...).launch/async inline creation
 *    - Severity: CodeSmell (configurable)
 *    - Platforms: All
 *
 * 3. **RunBlockingInSuspendRule** (Best Practice 2.2)
 *    - Detects runBlocking calls inside suspend functions
 *    - Severity: CodeSmell
 *    - Platforms: All
 *
 * 4. **DispatchersUnconfinedRule** (Best Practice 3.2)
 *    - Detects Dispatchers.Unconfined usage
 *    - Severity: CodeSmell
 *    - Platforms: All
 *
 * 5. **CancellationExceptionSubclassRule** (Best Practice 5.2)
 *    - Detects classes extending CancellationException
 *    - Severity: CodeSmell (configurable)
 *    - Platforms: All
 *
 * ### Detekt-Only Rules
 *
 * 6. **BlockingCallInCoroutineRule** (Best Practice 3.1)
 *    - Detects blocking calls like Thread.sleep(), JDBC, synchronous HTTP inside coroutines
 *    - Severity: Warning
 *    - Platforms: JVM only
 *
 * 7. **RunBlockingWithDelayInTestRule** (Best Practice 6.1)
 *    - Detects runBlocking with delay in tests (should use runTest)
 *    - Severity: Warning
 *    - Platforms: All
 *
 * 8. **ExternalScopeLaunchRule** (Best Practice 1.3)
 *    - Detects launching coroutines on external scopes from suspend functions
 *    - Severity: Warning (heuristic)
 *    - Platforms: All
 *
 * 9. **LoopWithoutYieldRule** (Best Practice 4.1)
 *    - Detects loops without cooperation points (yield, ensureActive, delay)
 *    - Severity: Warning (heuristic)
 *    - Platforms: All
 *
 * ## Usage
 *
 * Add the dependency to your project:
 * ```kotlin
 * detektPlugins("io.github.santimattius:structured-coroutines-detekt-rules:0.1.0")
 * ```
 *
 * Configure in detekt.yml:
 * ```yaml
 * structured-coroutines:
 *   BlockingCallInCoroutine:
 *     active: true
 *   RunBlockingWithDelayInTest:
 *     active: true
 *   ExternalScopeLaunch:
 *     active: true
 *   LoopWithoutYield:
 *     active: true
 * ```
 */
class StructuredCoroutinesRuleSetProvider : RuleSetProvider {
    
    override val ruleSetId: String = "structured-coroutines"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            // Compiler Plugin Rules
            GlobalScopeUsageRule(config),
            InlineCoroutineScopeRule(config),
            RunBlockingInSuspendRule(config),
            DispatchersUnconfinedRule(config),
            CancellationExceptionSubclassRule(config),
            CancellationExceptionSwallowedRule(config),
            JobInBuilderContextRule(config),
            RedundantLaunchInCoroutineScopeRule(config),
            SuspendInFinallyRule(config),
            UnusedDeferredRule(config),

            // Detekt-Only Rules
            BlockingCallInCoroutineRule(config),
            RunBlockingWithDelayInTestRule(config),
            ExternalScopeLaunchRule(config),
            LoopWithoutYieldRule(config),
            ScopeReuseAfterCancelRule(config),
            ChannelNotClosedRule(config),
            ConsumeEachMultipleConsumersRule(config),
            FlowBlockingCallRule(config),
        )
    )
}
