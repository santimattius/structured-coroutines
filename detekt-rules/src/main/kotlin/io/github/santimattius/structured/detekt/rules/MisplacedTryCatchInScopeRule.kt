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

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTryExpression

/**
 * Detekt rule that detects a `try/catch` placed INSIDE a `coroutineScope { }` lambda around
 * `await()` / `join()` of a child (`async` / `launch`) created in that same lambda.
 *
 * ## Problem (Best Practice 5.4)
 *
 * The child is a structural child of the `coroutineScope`. When it fails, the failure cancels the
 * scope and `coroutineScope` rethrows to its caller, regardless of the surrounding `try/catch`.
 * The catch may even run, which is misleading, and the code after the `try` never completes
 * normally.
 *
 * ```kotlin
 * // WARNING: the catch cannot stop the failure from leaving coroutineScope
 * suspend fun bad(): Int = coroutineScope {
 *     val d = async<Int> { error("boom") }
 *     try { d.await() } catch (e: Exception) { 0 }
 * }
 *
 * // GOOD: wrap the whole coroutineScope call
 * suspend fun good1(): Int =
 *     try { coroutineScope { async<Int> { error("boom") }.await() } } catch (e: Exception) { 0 }
 *
 * // GOOD: catch inside the child
 * suspend fun good2(): Int = coroutineScope {
 *     async { try { error("boom") } catch (e: Exception) { 0 } }.await()
 * }
 *
 * // GOOD: supervisorScope isolates its direct children
 * suspend fun good3(): Int = supervisorScope {
 *     val d = async<Int> { error("boom") }
 *     try { d.await() } catch (e: Exception) { 0 }
 * }
 * ```
 *
 * ## Detection logic
 *
 * For every call named `coroutineScope`, the lambda is walked in source order, tracking (by name)
 * the local variables initialized with an `async` / `launch` call, plus simple aliases
 * (`val e = d`). A `try` expression with at least one `catch` clause is reported when its try
 * block calls `await()` / `join()` on a tracked variable or on an inline `async { }` /
 * `launch { }` call, or calls `awaitAll(...)` / `joinAll(...)` with a tracked child as receiver or
 * argument. The walk does not enter lambdas of `async` / `launch`, `supervisorScope` or nested
 * `coroutineScope` calls (a nested scope is reported once by its own call).
 *
 * ## Limitations
 *
 * This rule is PSI based (no type resolution), so it is a heuristic:
 * - Calls are matched by callee name only; smart-cast receivers are not unwrapped.
 * - Tracked children are matched by variable name, so shadowed names can confuse it.
 * - Arguments of `async` / `launch` are not inspected: `async(SupervisorJob()) { }` is reported
 *   although the catch can be legitimate in that case.
 * - A catch that rethrows or intentionally observes the exception is still reported, hence the
 *   Warning severity.
 * - A Deferred received as a parameter, or taken from a property or collection, is not tracked.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   MisplacedTryCatchInScope:
 *     active: true
 * ```
 */
class MisplacedTryCatchInScopeRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "MisplacedTryCatchInScope",
        severity = Severity.Warning,
        description = "[EXCEPT_004] try/catch inside coroutineScope around await()/join() of a child " +
            "created in the same scope cannot stop the failure from propagating. " +
            "See: ${DetektDocUrl.buildDocLink("54-except_004--misplaced-try-catch-inside-coroutinescope")}",
        debt = Debt.FIVE_MINS
    )

    private val childBuilderNames = setOf("async", "launch")
    private val prunedNames = setOf("async", "launch", "supervisorScope", "coroutineScope")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "coroutineScope") return

        val lambdaExpr = expression.lambdaArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
            ?: return
        val body = lambdaExpr.bodyExpression ?: return

        val children = mutableSetOf<String>()
        val misplaced = mutableListOf<KtTryExpression>()
        walk(body, children, misplaced)

        for (tryExpression in misplaced) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(tryExpression),
                    message = "[EXCEPT_004] try/catch inside coroutineScope around await()/join() of a child " +
                        "created in the same scope cannot stop the failure from propagating: the child is a " +
                        "structural child of the scope. Wrap the whole coroutineScope call, catch inside the " +
                        "async/launch block, or use supervisorScope. " +
                        "See: ${DetektDocUrl.buildDocLink("54-except_004--misplaced-try-catch-inside-coroutinescope")}"
                )
            )
        }
    }

    /** Walks [element] in source order, tracking children and collecting misplaced `try` expressions. */
    private fun walk(element: PsiElement, children: MutableSet<String>, misplaced: MutableList<KtTryExpression>) {
        if (element is KtCallExpression && element.calleeExpression?.text in prunedNames) return
        when (element) {
            is KtProperty -> {
                val name = element.name
                val init = element.initializer
                if (name != null && init != null && (isChildBuilder(init) || isTrackedReference(init, children))) {
                    children.add(name)
                }
            }
            is KtTryExpression -> {
                if (element.catchClauses.isNotEmpty() && awaitsTrackedChild(element.tryBlock, children)) {
                    misplaced.add(element)
                }
            }
            else -> {}
        }
        for (child in element.children) walk(child, children, misplaced)
    }

    private fun awaitsTrackedChild(tryBlock: PsiElement, children: Set<String>): Boolean {
        if (tryBlock is KtCallExpression && tryBlock.calleeExpression?.text in prunedNames) return false
        if (tryBlock is KtCallExpression && isAwaitOrJoinOnChild(tryBlock, children)) return true
        return tryBlock.children.any { awaitsTrackedChild(it, children) }
    }

    private fun isAwaitOrJoinOnChild(call: KtCallExpression, children: Set<String>): Boolean {
        val receiver = (call.parent as? KtQualifiedExpression)
            ?.takeIf { it.selectorExpression === call }
            ?.receiverExpression
        return when (call.calleeExpression?.text) {
            "await", "join" -> receiver != null &&
                (isChildBuilder(receiver) || isTrackedReference(receiver, children))
            "awaitAll", "joinAll" ->
                (receiver != null && containsChild(receiver, children)) ||
                    call.valueArguments.any { arg -> arg.getArgumentExpression()?.let { containsChild(it, children) } == true }
            else -> false
        }
    }

    /** True when [root] (e.g. `listOf(a, b)`) references a tracked child or builds one inline. */
    private fun containsChild(root: PsiElement, children: Set<String>): Boolean {
        if (root is KtExpression && (isChildBuilder(root) || isTrackedReference(root, children))) return true
        return root.children.any { containsChild(it, children) }
    }

    /** `async { }`, `launch { }` or `receiver.async { }`. */
    private fun isChildBuilder(expression: KtExpression): Boolean {
        val call = when (expression) {
            is KtCallExpression -> expression
            is KtQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        } ?: return false
        return call.calleeExpression?.text in childBuilderNames
    }

    private fun isTrackedReference(expression: KtExpression, children: Set<String>): Boolean =
        expression is KtNameReferenceExpression && expression.getReferencedName() in children
}
