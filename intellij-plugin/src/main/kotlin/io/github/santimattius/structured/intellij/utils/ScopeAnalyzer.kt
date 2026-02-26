/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.utils

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Analyzer for CoroutineScope lifecycle and data flow analysis.
 *
 * This class provides analysis capabilities for detecting scope-related issues
 * such as reusing a cancelled scope.
 */
object ScopeAnalyzer {

    /**
     * Represents the state of a CoroutineScope variable.
     */
    enum class ScopeState {
        ACTIVE,
        CANCELLED,
        UNKNOWN
    }

    /**
     * Information about a scope variable's usage.
     */
    data class ScopeUsage(
        val variableName: String,
        val element: PsiElement,
        val usageType: UsageType,
        val offset: Int
    )

    enum class UsageType {
        CANCEL,
        LAUNCH,
        ASYNC,
        CANCEL_CHILDREN
    }

    /**
     * Analyzes a function for scope cancellation and reuse patterns.
     *
     * Returns a list of scope usages that indicate a scope was cancelled and then reused.
     */
    fun findScopeReuseAfterCancel(function: KtNamedFunction): List<ScopeReuseViolation> {
        val violations = mutableListOf<ScopeReuseViolation>()
        val scopeUsages = mutableMapOf<String, MutableList<ScopeUsage>>()

        // Collect all scope usages in the function
        function.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val methodName = expression.calleeExpression?.text ?: return
                val scopeName = getScopeNameFromCall(expression) ?: return
                val offset = expression.textOffset

                val usageType = when (methodName) {
                    "cancel" -> UsageType.CANCEL
                    "launch" -> UsageType.LAUNCH
                    "async" -> UsageType.ASYNC
                    "cancelChildren" -> UsageType.CANCEL_CHILDREN
                    else -> return
                }

                scopeUsages.getOrPut(scopeName) { mutableListOf() }
                    .add(ScopeUsage(scopeName, expression, usageType, offset))
            }
        })

        // Analyze each scope for cancel-then-reuse pattern
        for ((scopeName, usages) in scopeUsages) {
            val sortedUsages = usages.sortedBy { it.offset }

            var lastCancelOffset: Int? = null
            var lastCancelElement: PsiElement? = null

            for (usage in sortedUsages) {
                when (usage.usageType) {
                    UsageType.CANCEL -> {
                        lastCancelOffset = usage.offset
                        lastCancelElement = usage.element
                    }
                    UsageType.LAUNCH, UsageType.ASYNC -> {
                        // If we had a cancel() before this launch/async, it's a violation
                        if (lastCancelOffset != null && usage.offset > lastCancelOffset) {
                            violations.add(
                                ScopeReuseViolation(
                                    scopeName = scopeName,
                                    cancelElement = lastCancelElement!!,
                                    reuseElement = usage.element,
                                    reuseType = usage.usageType
                                )
                            )
                        }
                    }
                    UsageType.CANCEL_CHILDREN -> {
                        // cancelChildren() doesn't cancel the scope, so reset
                        lastCancelOffset = null
                        lastCancelElement = null
                    }
                }
            }
        }

        return violations
    }

    /**
     * Represents a violation where a scope is cancelled and then reused.
     */
    data class ScopeReuseViolation(
        val scopeName: String,
        val cancelElement: PsiElement,
        val reuseElement: PsiElement,
        val reuseType: UsageType
    )

    /**
     * Gets the scope name from a call expression (receiver of the call).
     */
    private fun getScopeNameFromCall(call: KtCallExpression): String? {
        val parent = call.parent
        return when (parent) {
            is KtDotQualifiedExpression -> {
                when (val receiver = parent.receiverExpression) {
                    is KtNameReferenceExpression -> receiver.text
                    is KtDotQualifiedExpression -> receiver.selectorExpression?.text
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Determines the type of scope based on its name and context.
     */
    fun getScopeType(scopeName: String, context: PsiElement?): ScopeType {
        return when {
            scopeName == "GlobalScope" -> ScopeType.GLOBAL
            scopeName == "viewModelScope" -> ScopeType.VIEW_MODEL
            scopeName == "lifecycleScope" -> ScopeType.LIFECYCLE
            scopeName == "rememberCoroutineScope" -> ScopeType.COMPOSE
            scopeName.endsWith("Scope") -> ScopeType.CUSTOM
            else -> ScopeType.UNKNOWN
        }
    }

    enum class ScopeType {
        GLOBAL,
        VIEW_MODEL,
        LIFECYCLE,
        COMPOSE,
        CUSTOM,
        UNKNOWN
    }

    /**
     * Determines the dispatcher type from a coroutine builder call.
     */
    fun getDispatcherType(call: KtCallExpression): DispatcherType {
        val arguments = call.valueArguments
        for (arg in arguments) {
            val argText = arg.text
            return when {
                argText.contains("Dispatchers.Main") -> DispatcherType.MAIN
                argText.contains("Dispatchers.IO") -> DispatcherType.IO
                argText.contains("Dispatchers.Default") -> DispatcherType.DEFAULT
                argText.contains("Dispatchers.Unconfined") -> DispatcherType.UNCONFINED
                else -> continue
            }
        }

        // Check withContext parent
        val parentCall = call.getParentOfType<KtCallExpression>(strict = true)
        if (parentCall?.calleeExpression?.text == "withContext") {
            return getDispatcherType(parentCall)
        }

        return DispatcherType.INHERITED
    }

    enum class DispatcherType {
        MAIN,
        IO,
        DEFAULT,
        UNCONFINED,
        INHERITED
    }

    /**
     * Checks if a deferred is awaited within the same scope.
     * Handles: direct assignment (val d = async {}; d.await()), and async inside initializer (val defs = xs.map { async {} }; defs.awaitAll()).
     */
    fun isDeferredAwaited(asyncCall: KtCallExpression): Boolean {
        val parent = asyncCall.parent

        // Check if async is assigned to a variable (direct or inside initializer, e.g. val defs = xs.map { async {} })
        val property = findPropertyContainingAsync(asyncCall)
        if (property != null) {
            val variableName = property.name ?: return false
            val block = property.getParentOfType<KtBlockExpression>(strict = false) ?: return false
            return blockContainsAwaitOnVariable(block, variableName, asyncCall)
        }

        // Check if async is used directly with await: scope.async { }.await()
        if (parent is KtDotQualifiedExpression) {
            val grandParent = parent.parent
            if (grandParent is KtDotQualifiedExpression) {
                val selector = grandParent.selectorExpression
                if (selector is KtCallExpression && selector.calleeExpression?.text == "await") {
                    return true
                }
            }
        }

        return false
    }

    private fun findPropertyContainingAsync(asyncCall: KtCallExpression): KtProperty? {
        val directProperty = when (val p = asyncCall.parent) {
            is KtDotQualifiedExpression -> p.parent as? KtProperty
            is KtProperty -> p
            else -> null
        }
        if (directProperty != null) return directProperty

        // Async inside initializer (e.g. val defs = xs.map { async { } })
        var current: PsiElement? = asyncCall.parent
        while (current != null) {
            if (current is KtProperty) {
                val init = current.initializer ?: break
                if (isDescendantOf(asyncCall, init)) return current
            }
            current = current.parent
        }
        return null
    }

    private fun isDescendantOf(element: PsiElement, ancestor: PsiElement): Boolean {
        var e: PsiElement? = element
        while (e != null) {
            if (e == ancestor) return true
            e = e.parent
        }
        return false
    }

    private fun blockContainsAwaitOnVariable(block: KtBlockExpression, varName: String, excludeCall: KtCallExpression): Boolean {
        // variable.await() or variable.awaitAll()
        for (dq in block.collectDescendantsOfType<KtDotQualifiedExpression>()) {
            val receiverName = when (val r = dq.receiverExpression) {
                is KtNameReferenceExpression -> r.getReferencedName()
                else -> r.text
            }
            val selectorStartsAwait = dq.selectorExpression?.text?.startsWith("await") == true
            val receiverMatches = receiverName == varName || (receiverName != null && receiverName.endsWith(".$varName"))
            if (receiverMatches && selectorStartsAwait) return true
        }
        // awaitAll(varName, ...) or list.awaitAll() where receiver is varName
        for (call in block.collectDescendantsOfType<KtCallExpression>()) {
            if (call === excludeCall) continue
            if (call.calleeExpression?.text == "awaitAll") {
                for (arg in call.valueArguments) {
                    val argText = arg.getArgumentExpression()?.text ?: continue
                    if (argText == varName || argText.endsWith(".$varName")) return true
                }
                val parent = call.parent as? KtDotQualifiedExpression ?: continue
                val receiverText = parent.receiverExpression.text
                if (receiverText == varName || receiverText.endsWith(".$varName")) return true
            }
        }
        return false
    }

    /**
     * Checks if a scope has the @StructuredScope annotation.
     */
    fun hasStructuredScopeAnnotation(element: PsiElement): Boolean {
        return when (element) {
            is KtParameter -> {
                element.annotationEntries.any {
                    it.shortName?.asString() == "StructuredScope"
                }
            }
            is KtProperty -> {
                element.annotationEntries.any {
                    it.shortName?.asString() == "StructuredScope"
                }
            }
            else -> false
        }
    }

    /**
     * Finds the declaration (KtParameter or KtProperty) for the scope used in a coroutine
     * builder call by name. For "scope.launch", looks up a parameter or property named "scope"
     * in the containing function or class and returns it so callers can check @StructuredScope.
     */
    fun findScopeDeclarationByName(call: KtCallExpression, scopeName: String): PsiElement? {
        val containingFunction = call.getParentOfType<KtNamedFunction>(strict = false) ?: return null
        // Check value parameters (e.g. @StructuredScope scope: CoroutineScope)
        val parameter = containingFunction.valueParameters.find { it.name == scopeName }
        if (parameter != null) return parameter
        // Check property in containing class (e.g. @StructuredScope val scope)
        val containingClass = containingFunction.getParentOfType<KtClass>(strict = false) ?: return null
        return containingClass.getBody()?.properties?.find { it.name == scopeName }
    }
}
