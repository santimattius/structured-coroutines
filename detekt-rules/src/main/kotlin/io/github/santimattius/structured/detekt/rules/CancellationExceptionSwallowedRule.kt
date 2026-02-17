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
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtTryExpression

/**
 * Detekt rule that detects catch(Exception) or catch(Throwable) that may swallow
 * CancellationException in coroutine code (suspend functions or coroutine builder blocks).
 *
 * ## Problem (Best Practice 4.3 - CANCEL_003)
 *
 * Catching Exception without rethrowing CancellationException breaks coroutine cancellation.
 * CancellationException must propagate so the coroutine can be cancelled properly.
 *
 * ```kotlin
 * // ❌ BAD: Swallows CancellationException
 * viewModelScope.launch {
 *     try {
 *         val message = getRandomMessage()
 *         _state.update { it.copy(message = message) }
 *     } catch (ex: Exception) {
 *         _state.update { it.copy(message = "Error: ${ex.message}") }
 *     }
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Handle CancellationException separately:
 *
 * ```kotlin
 * // ✅ GOOD
 * viewModelScope.launch {
 *     try {
 *         val message = getRandomMessage()
 *         _state.update { it.copy(message = message) }
 *     } catch (e: CancellationException) {
 *         throw e
 *     } catch (ex: Exception) {
 *         _state.update { it.copy(message = "Error: ${ex.message}") }
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   CancellationExceptionSwallowed:
 *     active: true
 *     severity: warning
 * ```
 */
class CancellationExceptionSwallowedRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "CancellationExceptionSwallowed",
        severity = Severity.CodeSmell,
        description = "[CANCEL_003] Catching Exception or Throwable without rethrowing CancellationException " +
            "breaks coroutine cancellation. Add a catch (e: CancellationException) { throw e } clause first, " +
            "or use ensureActive() in the catch block. " +
            "See: ${DetektDocUrl.buildDocLink("43-cancel_003--swallowing-cancellationexception")}",
        debt = Debt.TEN_MINS
    )

    override fun visitTryExpression(expression: KtTryExpression) {
        super.visitTryExpression(expression)
        for (catchClause in expression.catchClauses) {
            if (!catchesGenericException(catchClause)) continue
            if (hasCancellationExceptionCatch(expression)) continue
            if (catchBodyRethrowsCancellationException(catchClause)) continue
            if (!CoroutineDetektUtils.isInsideCoroutine(catchClause)) continue
            val entity = catchClause.catchParameter ?: catchClause
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(entity),
                    message = "[CANCEL_003] catch(Exception) may swallow CancellationException. " +
                        "Add catch (e: CancellationException) { throw e } before the generic catch, " +
                        "or use ensureActive() in the catch block. " +
                        "See: ${DetektDocUrl.buildDocLink("43-cancel_003--swallowing-cancellationexception")}"
                )
            )
        }
    }

    private fun catchesGenericException(catchClause: KtCatchClause): Boolean {
        val typeRef = catchClause.catchParameter?.typeReference?.text ?: return false
        return typeRef == "Exception" ||
            typeRef == "Throwable" ||
            typeRef == "java.lang.Exception" ||
            typeRef == "java.lang.Throwable"
    }

    private fun hasCancellationExceptionCatch(tryExpression: KtTryExpression): Boolean {
        return tryExpression.catchClauses.any { clause ->
            clause.catchParameter?.typeReference?.text?.contains("CancellationException") == true
        }
    }

    private fun catchBodyRethrowsCancellationException(catchClause: KtCatchClause): Boolean {
        val body = catchClause.catchBody?.text ?: return false
        if (body.contains("throw") && body.contains("CancellationException")) return true
        val paramName = catchClause.catchParameter?.name ?: "e"
        return body.contains("if") && body.contains("CancellationException") && body.contains("throw $paramName")
    }
}
