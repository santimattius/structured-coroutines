/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.AddCancellationExceptionCatchQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects catch clauses that may swallow CancellationException.
 *
 * In Kotlin Coroutines, CancellationException is used to signal cancellation.
 * Catching it (usually via catch(Exception) or catch(Throwable)) without
 * rethrowing breaks the cancellation mechanism.
 *
 * Example of problematic code:
 * ```kotlin
 * suspend fun doWork() {
 *     try {
 *         fetchData()
 *     } catch (e: Exception) {
 *         log(e)  // Swallows CancellationException!
 *     }
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * suspend fun doWork() {
 *     try {
 *         fetchData()
 *     } catch (e: CancellationException) {
 *         throw e  // Rethrow cancellation
 *     } catch (e: Exception) {
 *         log(e)
 *     }
 * }
 * ```
 */
class CancellationExceptionSwallowedInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.cancellation.swallowed.display.name"
    override val descriptionKey = "inspection.cancellation.swallowed.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCatchSection(catchClause: KtCatchClause) {
                super.visitCatchSection(catchClause)

                // Only check in suspend contexts
                if (!CoroutinePsiUtils.isInSuspendFunction(catchClause) &&
                    !CoroutinePsiUtils.isInsideCoroutineContext(catchClause)) {
                    return
                }

                // Check if this is a generic exception catch
                if (!CoroutinePsiUtils.catchesGenericException(catchClause)) return

                // Check if there's already a CancellationException handler
                val tryExpression = catchClause.parent as? KtTryExpression ?: return
                if (CoroutinePsiUtils.hasCancellationExceptionCatch(tryExpression)) return

                // Check if the catch body rethrows CancellationException
                val catchBody = catchClause.catchBody?.text ?: ""
                if (catchBody.contains("throw") && catchBody.contains("CancellationException")) {
                    return
                }

                // Check if it rethrows the caught exception for CancellationException
                val paramName = catchClause.catchParameter?.name ?: "e"
                if (catchBody.contains("if") && catchBody.contains("CancellationException") &&
                    catchBody.contains("throw $paramName")) {
                    return
                }

                holder.registerProblem(
                    catchClause.catchParameter ?: catchClause,
                    StructuredCoroutinesBundle.message("error.cancellation.swallowed"),
                    AddCancellationExceptionCatchQuickFix()
                )
            }
        }
    }
}
