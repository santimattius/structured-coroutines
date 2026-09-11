/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Detekt rule that detects blocking calls inside `flow { }` builder.
 *
 * ## Problem (Best Practice 9.1 - FLOW_001)
 *
 * The flow builder block runs in the collector's context. Blocking calls (e.g. Thread.sleep,
 * synchronous I/O) can freeze the wrong thread (e.g. Main) and flows without suspension
 * points cooperate poorly with cancellation.
 *
 * ## Recommended Practice
 *
 * Keep the flow builder non-blocking. Use `flowOn(Dispatchers.IO)` to move emission to
 * a different context, or use suspend APIs inside the builder.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   FlowBlockingCall:
 *     active: true
 * ```
 *
 * @see CoroutineDetektUtils.BLOCKING_METHODS for detected methods
 */
class FlowBlockingCallRule(config: Config = Config.empty) : Rule(
    config,
    description = "[FLOW_001] Blocking code inside flow { } builder. " +
            "The block runs in the collector's context; use flowOn(Dispatchers.IO) or suspend APIs. " +
            "See: ${DetektDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}",
) {

    override val ruleName: RuleName get() = RuleName("FlowBlockingCall")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        if (!CoroutineDetektUtils.isBlockingCall(expression)) return
        if (!CoroutineDetektUtils.isInsideFlowBuilder(expression)) return

        val callName = CoroutineDetektUtils.getFullyQualifiedCallName(expression)
        report(
            Finding(
                entity = Entity.from(expression),
                message = "[FLOW_001] Blocking call '$callName' inside flow { }. " +
                    "Use flowOn(Dispatchers.IO) or suspend APIs. " +
                    "See: ${DetektDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}"
            )
        )
    }
}
