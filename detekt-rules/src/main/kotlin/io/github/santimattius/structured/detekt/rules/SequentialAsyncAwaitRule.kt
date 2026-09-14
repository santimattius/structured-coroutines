/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * [CONCUR_003] — `async { }.await()` on one line defeats parallelism — use sequential `withContext` or parallel async.
 *
 * Heuristic: inline `await()` whose receiver call is directly `async { ... }`.
 */
class SequentialAsyncAwaitRule(config: Config = Config.empty) : Rule(
    config,
    description = "[CONCUR_003] Sequential `async {}.await()` only adds Deferred overhead — run work directly " +
            "or launch multiple deferreds without awaiting between the `async` calls. " +
            "See: ${DetektDocUrl.buildDocLink("15-concur_003--sequential-asyncawait")}",
) {

    override val ruleName: RuleName get() = RuleName("SequentialAsyncAwait")

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        val selector = expression.selectorExpression as? KtCallExpression ?: return
        if (selector.calleeExpression?.text != "await") return

        val receiverExpr = expression.receiverExpression
        val asyncCall = receiverExpr as? KtCallExpression ?: return
        if (asyncCall.calleeExpression?.text != "async") return

        report(
            Finding(
                entity = Entity.from(selector),
                message = "[CONCUR_003] `async { }.await()` is sequential — prefer ordinary suspend calls " +
                    "or `coroutineScope { async { }; async { } }` without intermediate await. " +
                    "See: ${DetektDocUrl.buildDocLink("15-concur_003--sequential-asyncawait")}"
            )
        )
    }
}
