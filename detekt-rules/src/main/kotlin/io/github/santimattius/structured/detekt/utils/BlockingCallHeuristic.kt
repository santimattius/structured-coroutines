/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Shared blocking-call detection for [DISPATCH_001] and [BACKEND_001].
 */
object BlockingCallHeuristic {

    fun isBlockingCallInCoroutine(expression: KtCallExpression): Boolean {
        if (!CoroutineDetektUtils.isBlockingCall(expression)) return false
        return CoroutineDetektUtils.isInsideCoroutine(expression)
    }

    /**
     * [BACKEND_001] — blocking call in coroutine without an enclosing `withContext(IO)` (or injected IO dispatcher).
     */
    fun isBlockingCallInCoroutineWithoutIoContext(expression: KtCallExpression): Boolean {
        if (!isBlockingCallInCoroutine(expression)) return false
        return !isInsideWithContextIo(expression)
    }

    /**
     * Walks outward from [element] and returns true if any ancestor lambda is the body of
     * `withContext(<io-like-dispatcher>) { ... }`.
     */
    fun isInsideWithContextIo(element: KtElement): Boolean {
        var current: KtElement? = element
        while (current != null) {
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true)
            if (lambda != null) {
                val withContextCall = findWithContextCallForLambda(lambda)
                if (withContextCall != null && withContextUsesIoDispatcher(withContextCall)) {
                    return true
                }
            }
            current = current.parent as? KtElement
        }
        return false
    }

    private fun findWithContextCallForLambda(lambda: KtLambdaExpression): KtCallExpression? {
        val lambdaArg = lambda.parent as? KtLambdaArgument
        val callFromArg = lambdaArg?.parent?.parent as? KtCallExpression
        if (callFromArg != null && callFromArg.calleeExpression?.text == "withContext") {
            return callFromArg
        }
        val directParent = lambda.parent as? KtCallExpression
        if (directParent != null && directParent.calleeExpression?.text == "withContext") {
            return directParent
        }
        return lambda.getParentOfType<KtCallExpression>(strict = false)
            ?.takeIf { it.calleeExpression?.text == "withContext" }
    }

    private fun withContextUsesIoDispatcher(withContextCall: KtCallExpression): Boolean {
        val dispatcherArg = withContextCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        return dispatcherArgLooksLikeIo(dispatcherArg)
    }

    private fun dispatcherArgLooksLikeIo(arg: KtElement): Boolean {
        val text = arg.text
        if (text.contains("Dispatchers.IO") || text.endsWith(".IO")) return true
        if (text.contains("ioDispatcher", ignoreCase = true)) return true
        if (arg is KtBinaryExpression) {
            val left = arg.left
            val right = arg.right
            return (left != null && dispatcherArgLooksLikeIo(left)) ||
                (right != null && dispatcherArgLooksLikeIo(right))
        }
        if (arg is KtDotQualifiedExpression) {
            val selector = arg.selectorExpression?.text
            if (selector == "IO" && arg.receiverExpression.text.contains("Dispatchers")) return true
        }
        return false
    }
}
