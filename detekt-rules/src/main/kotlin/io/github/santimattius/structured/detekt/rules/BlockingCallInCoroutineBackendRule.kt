/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.BlockingCallHeuristic
import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.CodeSmell
import dev.detekt.api.Config
import dev.detekt.api.Debt
import dev.detekt.api.Entity
import dev.detekt.api.Issue
import dev.detekt.api.Rule
import dev.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * [BACKEND_001] — blocking JVM calls in coroutines without `withContext(Dispatchers.IO)` (or injected IO dispatcher).
 */
class BlockingCallInCoroutineBackendRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "BlockingCallInCoroutineBackend",
        severity = Severity.Warning,
        description = "[BACKEND_001] Blocking call in coroutine without IO dispatcher context. " +
            "Wrap blocking work in withContext(Dispatchers.IO) { }. " +
            "See: ${DetektDocUrl.buildDocLink("131-backend_001--blockingcallincoroutinebackend")}",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (!BlockingCallHeuristic.isBlockingCallInCoroutineWithoutIoContext(expression)) return

        val callName = CoroutineDetektUtils.getFullyQualifiedCallName(expression)
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[BACKEND_001] Blocking call '$callName' in coroutine without IO dispatcher context. " +
                    "Use withContext(Dispatchers.IO) { } or an injected @IoDispatcher. " +
                    "See: ${DetektDocUrl.buildDocLink("131-backend_001--blockingcallincoroutinebackend")}",
            ),
        )
    }
}
