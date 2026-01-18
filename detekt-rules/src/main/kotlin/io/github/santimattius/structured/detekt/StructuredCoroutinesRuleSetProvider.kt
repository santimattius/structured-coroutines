/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt

import io.github.santimattius.structured.detekt.rules.BlockingCallInCoroutineRule
import io.github.santimattius.structured.detekt.rules.ExternalScopeLaunchRule
import io.github.santimattius.structured.detekt.rules.LoopWithoutYieldRule
import io.github.santimattius.structured.detekt.rules.RunBlockingWithDelayInTestRule
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
 * 1. **BlockingCallInCoroutineRule** (Best Practice 3.1)
 *    - Detects blocking calls like Thread.sleep(), JDBC, synchronous HTTP inside coroutines
 *    - Severity: Warning
 *    - Platforms: JVM only
 *
 * 2. **RunBlockingWithDelayInTestRule** (Best Practice 6.1)
 *    - Detects runBlocking with delay in tests (should use runTest)
 *    - Severity: Warning
 *    - Platforms: All
 *
 * 3. **ExternalScopeLaunchRule** (Best Practice 1.3)
 *    - Detects launching coroutines on external scopes from suspend functions
 *    - Severity: Warning (heuristic)
 *    - Platforms: All
 *
 * 4. **LoopWithoutYieldRule** (Best Practice 4.1)
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
            BlockingCallInCoroutineRule(config),
            RunBlockingWithDelayInTestRule(config),
            ExternalScopeLaunchRule(config),
            LoopWithoutYieldRule(config),
        )
    )
}
