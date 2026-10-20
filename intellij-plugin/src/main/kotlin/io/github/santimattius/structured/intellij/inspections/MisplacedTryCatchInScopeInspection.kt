/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * [EXCEPT_004] — `try/catch` placed INSIDE a `coroutineScope { }` lambda around `await()` / `join()`
 * of a child (`async` / `launch`) created in that same lambda.
 *
 * The child is a structural child of the scope: when it fails, the scope is cancelled and
 * `coroutineScope` rethrows to its caller, so the catch cannot stop propagation.
 *
 * ## Detection (PSI, name based, mirrors the compiler checker)
 *
 * 1. Matches `coroutineScope { }` calls.
 * 2. Walks the lambda in source order, tracking local `val`s initialized with `async` / `launch`
 *    plus simple aliases (`val e = d`).
 * 3. Reports each `try` with at least one `catch` whose try block calls `await` / `join` /
 *    `awaitAll` / `joinAll` on a tracked child, or on an inline `async { }` / `launch { }`.
 * 4. Does not descend into `async` / `launch` lambdas, `supervisorScope` or nested
 *    `coroutineScope` calls (a nested scope is reported once, by its own call).
 *
 * ## Limitations
 *
 * - No type resolution: children are tracked by variable name; shadowing is not modelled and
 *   smart-cast receivers (`(d as Deferred<Int>).await()`) are not unwrapped.
 * - `async`/`launch` arguments are not inspected, so `async(SupervisorJob()) { }` is still reported
 *   although the catch can be legitimate.
 * - A catch that rethrows is still reported (hence WARNING).
 */
class MisplacedTryCatchInScopeInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.misplaced.try.catch.in.scope.display.name"
    override val descriptionKey = "inspection.misplaced.try.catch.in.scope.description"

    override fun getShortName(): String = "MisplacedTryCatchInScope"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "coroutineScope") return

                val body = expression.lambdaArguments.lastOrNull()
                    ?.getLambdaExpression()?.bodyExpression ?: return

                val walker = ScopeWalker()
                walker.walk(body)
                val message = StructuredCoroutinesBundle.message("error.except.misplaced.try.catch.in.scope")
                for (tryExpression in walker.misplacedTries) {
                    // Highlight only the `try` keyword, not the whole (possibly long) block.
                    holder.registerProblem(tryExpression.firstChild ?: tryExpression, message)
                }
            }
        }

    /** Walks a `coroutineScope` lambda in source order, collecting misplaced `try` expressions. */
    private class ScopeWalker {
        private val children = mutableSetOf<String>()
        val misplacedTries = mutableListOf<KtTryExpression>()

        fun walk(element: PsiElement) {
            when (element) {
                is KtCallExpression -> if (isPrunedCall(element)) return
                is KtProperty -> {
                    val init = element.initializer
                    val name = element.name
                    if (name != null && init != null && !element.isVar && isChild(init, children)) {
                        children.add(name)
                    }
                }
                is KtTryExpression -> {
                    if (element.catchClauses.isNotEmpty() && awaitsTrackedChild(element.tryBlock)) {
                        misplacedTries.add(element)
                    }
                }
            }
            var child = element.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }

        private fun awaitsTrackedChild(tryBlock: PsiElement): Boolean = findAwait(tryBlock)

        private fun findAwait(element: PsiElement): Boolean {
            if (element is KtCallExpression) {
                if (isAwaitOrJoinOnChild(element)) return true
                if (isPrunedCall(element)) return false
            }
            var child = element.firstChild
            while (child != null) {
                if (findAwait(child)) return true
                child = child.nextSibling
            }
            return false
        }

        private fun isAwaitOrJoinOnChild(call: KtCallExpression): Boolean {
            val receiver = receiverOf(call)
            return when (call.calleeExpression?.text) {
                "await", "join" -> receiver != null && isChild(receiver, children)
                "awaitAll", "joinAll" ->
                    (receiver != null && containsChild(receiver)) ||
                        call.valueArguments.any { arg -> arg.getArgumentExpression()?.let(::containsChild) == true }
                else -> false
            }
        }

        private fun containsChild(root: PsiElement): Boolean {
            if (root is KtExpression && isChild(root, children)) return true
            var child = root.firstChild
            while (child != null) {
                if (containsChild(child)) return true
                child = child.nextSibling
            }
            return false
        }
    }

    private companion object {
        val CHILD_BUILDERS = setOf("async", "launch")
        val PRUNED_SCOPES = setOf("coroutineScope", "supervisorScope")

        fun isPrunedCall(call: KtCallExpression): Boolean {
            val name = call.calleeExpression?.text
            return name in CHILD_BUILDERS || name in PRUNED_SCOPES
        }

        /** Receiver of `receiver.call(...)`, or null when [call] is not the selector of a qualified expression. */
        fun receiverOf(call: KtCallExpression): KtExpression? {
            val parent = call.parent as? KtQualifiedExpression ?: return null
            return if (parent.selectorExpression == call) parent.receiverExpression else null
        }

        fun unwrap(expression: KtExpression): KtExpression {
            var current = expression
            while (current is KtParenthesizedExpression) {
                current = current.expression ?: return current
            }
            return current
        }

        /** True for `async { }` / `launch { }`, also when written as `scope.async { }`. */
        fun isChildBuilder(expression: KtExpression): Boolean {
            val e = unwrap(expression)
            val call = when (e) {
                is KtCallExpression -> e
                is KtQualifiedExpression -> e.selectorExpression as? KtCallExpression
                else -> null
            } ?: return false
            return call.calleeExpression?.text in CHILD_BUILDERS
        }

        /** True when [expression] is a tracked child by name or an inline child builder. */
        fun isChild(expression: KtExpression, children: Set<String>): Boolean {
            val e = unwrap(expression)
            if (isChildBuilder(e)) return true
            return e is KtNameReferenceExpression && e.getReferencedName() in children
        }
    }
}
