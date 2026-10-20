/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects a `try/catch` placed INSIDE a `coroutineScope { }` lambda around `await()` / `join()`
 * of a child (`async` / `launch`) created in that same lambda (EXCEPT_004, Best Practice 5.4).
 *
 * The child is a structural child of the `coroutineScope`. When it fails, the failure cancels the
 * scope and `coroutineScope` rethrows to its caller, regardless of the surrounding `try/catch`.
 *
 * ```kotlin
 * // WARNING - the catch cannot stop the failure from leaving coroutineScope
 * suspend fun bad(): Int = coroutineScope {
 *     val d = async<Int> { error("boom") }
 *     try { d.await() } catch (e: Exception) { 0 }
 * }
 *
 * // GOOD - wrap the whole coroutineScope call
 * suspend fun good1(): Int =
 *     try { coroutineScope { async<Int> { error("boom") }.await() } } catch (e: Exception) { 0 }
 *
 * // GOOD - catch inside the child
 * suspend fun good2(): Int = coroutineScope {
 *     async { try { error("boom") } catch (e: Exception) { 0 } }.await()
 * }
 *
 * // GOOD - supervisorScope isolates its direct children
 * suspend fun good3(): Int = supervisorScope {
 *     val d = async<Int> { error("boom") }
 *     try { d.await() } catch (e: Exception) { 0 }
 * }
 * ```
 *
 * ## Detection logic
 *
 * 1. Matches `coroutineScope` calls and walks the lambda in source order.
 * 2. Tracks local variables initialized with `async` / `launch`, plus simple aliases
 *    (`val e = d`). Tracking is by variable name, consistent with [AsyncWithoutAwaitDetector].
 * 3. Reports every `try` that has at least one `catch` clause and whose try block contains
 *    `await()` / `join()` / `awaitAll(...)` / `joinAll(...)` on a tracked child, or on an inline
 *    `async { }` / `launch { }` call (e.g. `async { }.await()`).
 * 4. Does not descend into `async` / `launch` lambdas (catching inside the child is correct),
 *    `supervisorScope` or nested `coroutineScope` lambdas (a nested scope is reported once by its
 *    own call).
 *
 * ## Known limitations
 *
 * - Only children stored in local variables of the same lambda (or awaited inline) are tracked;
 *   Deferreds received as parameters, properties, collections or function results are not.
 * - Receivers wrapped in a smart cast or parentheses beyond simple parenthesis unwrapping are not
 *   resolved further.
 * - The arguments of `async` / `launch` are not inspected, so `async(SupervisorJob()) { }` is
 *   reported even though the catch is legitimate there.
 * - A catch that rethrows or intentionally observes the exception is still reported, hence the
 *   WARNING default severity.
 */
class MisplacedTryCatchInScopeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MisplacedTryCatchInScope",
            briefDescription = "try/catch inside coroutineScope cannot contain a child failure",
            explanation = """
                [EXCEPT_004] A try/catch placed inside a coroutineScope lambda around await() or join()
                of an async/launch created in that same lambda cannot stop the failure: the child is a
                structural child of the scope, so its failure cancels the scope and coroutineScope
                rethrows to its caller. Wrap the whole coroutineScope call in try/catch, catch inside
                the child block, or use supervisorScope.

                See: ${LintDocUrl.buildDocLink("54-except_004--misplaced-try-catch-inside-coroutinescope")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MisplacedTryCatchInScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val COROUTINE_SCOPE = "coroutineScope"
        private const val SUPERVISOR_SCOPE = "supervisorScope"
        private val CHILD_BUILDERS = setOf("async", "launch")
        private val PRUNED_CALLS = CHILD_BUILDERS + setOf(COROUTINE_SCOPE, SUPERVISOR_SCOPE)
        private val SINGLE_AWAITS = setOf("await", "join")
        private val MULTI_AWAITS = setOf("awaitAll", "joinAll")
    }

    override fun getApplicableMethodNames(): List<String> = listOf(COROUTINE_SCOPE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (node.methodName != COROUTINE_SCOPE) return
        val lambda = node.valueArguments.lastOrNull() as? ULambdaExpression ?: return

        val walker = ScopeWalker()
        lambda.body.accept(walker)

        val message = "[EXCEPT_004] try/catch inside coroutineScope cannot contain the failure of a " +
            "child created in the same scope: wrap the whole coroutineScope call, catch inside " +
            "the child, or use supervisorScope. " +
            "See: ${LintDocUrl.buildDocLink("54-except_004--misplaced-try-catch-inside-coroutinescope")}"

        for (tryNode in walker.misplacedTries) {
            context.report(ISSUE, tryNode, context.getLocation(tryNode), message)
        }
    }

    private fun isChildBuilder(expression: UElement?): Boolean =
        (expression?.let { unwrap(it) } as? UCallExpression)?.methodName in CHILD_BUILDERS

    private fun unwrap(element: UElement): UElement =
        if (element is UExpression) element.skipParenthesizedExprDown() else element

    /** Name of the simple local variable referenced by [expression], if any. */
    private fun referencedName(expression: UElement?): String? {
        val unwrapped = expression?.let { unwrap(it) } ?: return null
        return (unwrapped as? USimpleNameReferenceExpression)?.identifier
    }

    /**
     * Walks the lambda in source order, tracking children created in it and collecting the `try`
     * expressions that wrap an await/join on them.
     */
    private inner class ScopeWalker : AbstractUastVisitor() {
        private val children = mutableSetOf<String>()
        val misplacedTries = mutableListOf<UTryExpression>()

        override fun visitCallExpression(node: UCallExpression): Boolean =
            node.methodName in PRUNED_CALLS

        override fun visitVariable(node: UVariable): Boolean {
            val init = node.uastInitializer
            val name = node.name
            if (name != null && (isChildBuilder(init) || referencedName(init) in children)) {
                children.add(name)
            }
            return super.visitVariable(node)
        }

        override fun visitTryExpression(node: UTryExpression): Boolean {
            if (node.catchClauses.isNotEmpty() && awaitsTrackedChild(node.tryClause)) {
                misplacedTries.add(node)
            }
            return super.visitTryExpression(node)
        }

        private fun awaitsTrackedChild(tryBlock: UExpression): Boolean {
            val finder = AwaitFinder(children)
            tryBlock.accept(finder)
            return finder.found
        }
    }

    /** Looks for await/join/awaitAll/joinAll on tracked children, pruning child builders. */
    private inner class AwaitFinder(private val children: Set<String>) : AbstractUastVisitor() {
        var found: Boolean = false
            private set

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (found) return true
            if (node.methodName in PRUNED_CALLS) return true
            when (node.methodName) {
                in SINGLE_AWAITS -> if (isChild(node.receiver)) found = true
                in MULTI_AWAITS ->
                    if (containsChild(node.receiver) || node.valueArguments.any { containsChild(it) }) {
                        found = true
                    }
            }
            return found
        }

        private fun isChild(receiver: UElement?): Boolean =
            isChildBuilder(receiver) || referencedName(receiver) in children

        /** True when [root] (e.g. `listOf(a, b)`) references a tracked child or builds one inline. */
        private fun containsChild(root: UElement?): Boolean {
            if (root == null) return false
            var hit = false
            root.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.methodName in CHILD_BUILDERS) hit = true
                    return hit
                }

                override fun visitSimpleNameReferenceExpression(
                    node: USimpleNameReferenceExpression
                ): Boolean {
                    if (node.identifier in children) hit = true
                    return hit
                }
            })
            return hit
        }
    }
}
