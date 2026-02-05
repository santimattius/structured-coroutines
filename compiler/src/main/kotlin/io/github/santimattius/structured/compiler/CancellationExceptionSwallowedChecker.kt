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

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTryExpressionChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThrowExpression
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR Try Expression Checker that detects `catch(Exception)` blocks in suspend functions
 * that may swallow `CancellationException`.
 *
 * ## Problem (Best Practice 4.2)
 *
 * Doing `catch (e: Exception)` and treating `CancellationException` the same as any
 * other error can prevent cancellation from propagating correctly:
 *
 * ```kotlin
 * // ❌ BAD: Swallows CancellationException
 * suspend fun bad() {
 *     try {
 *         work()
 *     } catch (e: Exception) {
 *         log(e)  // CancellationException is caught and not re-thrown!
 *     }
 * }
 * ```
 *
 * This leaves coroutines alive when they should terminate, breaking structured concurrency.
 *
 * ## Recommended Practice
 *
 * When catching `Exception` or `Throwable`, handle `CancellationException` separately:
 *
 * ```kotlin
 * // ✅ GOOD: Explicit CancellationException handling
 * suspend fun good() {
 *     try {
 *         work()
 *     } catch (e: CancellationException) {
 *         throw e  // Always re-throw!
 *     } catch (e: Exception) {
 *         log(e)
 *     }
 * }
 *
 * // ✅ GOOD: Using ensureActive() in catch block
 * suspend fun alsoGood() {
 *     try {
 *         work()
 *     } catch (e: Exception) {
 *         ensureActive()  // Re-throws if cancelled
 *         log(e)
 *     }
 * }
 * ```
 *
 * ## Detection
 *
 * This checker:
 * 1. Only analyzes code inside `suspend` functions or suspend lambdas (e.g. inside `launch { }`)
 * 2. Looks for `catch(e: Exception)` or `catch(e: Throwable)` blocks
 * 3. Verifies there's no separate `catch(CancellationException)` clause
 * 4. Checks if the catch block re-throws or calls `ensureActive()`
 * 5. Reports a warning if cancellation might be swallowed
 *
 * @see <a href="https://kotlinlang.org/docs/cancellation-and-timeouts.html">Kotlin Cancellation</a>
 */
class CancellationExceptionSwallowedChecker : FirTryExpressionChecker(MppCheckerKind.Common) {

    companion object {
        // Exception types that would catch CancellationException
        private val BROAD_EXCEPTION_CLASS_IDS = setOf(
            ClassId(FqName("kotlin"), Name.identifier("Exception")),
            ClassId(FqName("kotlin"), Name.identifier("Throwable")),
            ClassId(FqName("java.lang"), Name.identifier("Exception")),
            ClassId(FqName("java.lang"), Name.identifier("Throwable"))
        )

        // CancellationException class IDs
        private val CANCELLATION_EXCEPTION_CLASS_IDS = setOf(
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("CancellationException")),
            ClassId(FqName("kotlin.coroutines.cancellation"), Name.identifier("CancellationException")),
            ClassId(FqName("java.util.concurrent"), Name.identifier("CancellationException"))
        )

        private val ENSURE_ACTIVE_NAME = Name.identifier("ensureActive")
        private val CURRENT_COROUTINE_CONTEXT_NAME = Name.identifier("currentCoroutineContext")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirTryExpression) {
        // Only check inside suspend functions or suspend lambdas (e.g. launch { }, async { })
        if (!isInsideSuspendContext(context)) return

        // Check if there's a catch block that catches broad exceptions
        val catches = expression.catches
        if (catches.isEmpty()) return

        // Look for catch(CancellationException) - if present, the code is safe
        val hasCancellationExceptionCatch = catches.any { catchClause ->
            val catchType = catchClause.parameter.returnTypeRef.coneType
            val classSymbol = catchType.toClassSymbol(context.session)
            classSymbol?.classId in CANCELLATION_EXCEPTION_CLASS_IDS
        }

        if (hasCancellationExceptionCatch) return

        // Look for catch(Exception) or catch(Throwable)
        for (catchClause in catches) {
            val catchType = catchClause.parameter.returnTypeRef.coneType
            val classSymbol = catchType.toClassSymbol(context.session) ?: continue

            if (classSymbol.classId in BROAD_EXCEPTION_CLASS_IDS) {
                // Check if the catch block properly handles cancellation
                val catchBlock = catchClause.block
                if (!handlesCancellationProperly(catchBlock, catchClause.parameter.name)) {
                    reporter.reportCancellationExceptionSwallowed(expression, context)
                    return
                }
            }
        }
    }

    /**
     * Checks if we're currently inside a suspend context: either a suspend function
     * or a suspend lambda (e.g. the block of launch { }, async { }, withContext { }).
     * This ensures we flag catch(Exception) that may swallow CancellationException
     * inside coroutine builders, not only in top-level suspend functions.
     */
    @OptIn(SymbolInternals::class)
    private fun isInsideSuspendContext(context: CheckerContext): Boolean {
        for (element in context.containingDeclarations) {
            val symbol = element as FirBasedSymbol<*>
            val declaration = symbol.fir
            when (declaration) {
                is FirSimpleFunction -> if (declaration.status.isSuspend) return true
                else -> if (isSuspendLambda(declaration)) return true
            }
        }
        return false
    }

    /**
     * Returns true if the declaration is a suspend lambda (e.g. the block of
     * launch { }, async { }). In FIR, lambdas are represented as functions
     * (e.g. FirAnonymousFunction extends FirFunction); we detect suspend via status.
     */
    private fun isSuspendLambda(declaration: FirDeclaration): Boolean {
        val fn = declaration as? FirFunction ?: return false
        return fn.status.isSuspend
    }

    /**
     * Checks if a catch block properly handles cancellation by:
     * - Re-throwing the exception (or throwing anything)
     * - Calling ensureActive()
     * - Checking for CancellationException and re-throwing
     */
    private fun handlesCancellationProperly(block: FirBlock, parameterName: Name): Boolean {
        for (statement in block.statements) {
            if (handlesCancellation(statement, parameterName)) {
                return true
            }
        }
        return false
    }

    /**
     * Recursively checks if a statement handles cancellation properly.
     */
    private fun handlesCancellation(statement: FirStatement, parameterName: Name): Boolean {
        return when (statement) {
            // Direct throw statement
            is FirThrowExpression -> true
            
            // Call to ensureActive()
            is FirFunctionCall -> {
                val calleeName = statement.calleeReference.name
                calleeName == ENSURE_ACTIVE_NAME || calleeName == CURRENT_COROUTINE_CONTEXT_NAME
            }
            
            // Check nested blocks
            is FirBlock -> {
                statement.statements.any { handlesCancellation(it, parameterName) }
            }
            
            else -> false
        }
    }
}
