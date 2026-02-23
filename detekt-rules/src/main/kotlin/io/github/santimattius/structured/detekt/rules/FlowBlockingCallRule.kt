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
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
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
class FlowBlockingCallRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "FlowBlockingCall",
        severity = Severity.Warning,
        description = "[FLOW_001] Blocking code inside flow { } builder. " +
            "The block runs in the collector's context; use flowOn(Dispatchers.IO) or suspend APIs. " +
            "See: ${DetektDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (!CoroutineDetektUtils.isBlockingCall(expression)) return
        if (!CoroutineDetektUtils.isInsideFlowBuilder(expression)) return

        val callName = CoroutineDetektUtils.getFullyQualifiedCallName(expression)
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[FLOW_001] Blocking call '$callName' inside flow { }. " +
                    "Use flowOn(Dispatchers.IO) or suspend APIs. " +
                    "See: ${DetektDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}"
            )
        )
    }
}
