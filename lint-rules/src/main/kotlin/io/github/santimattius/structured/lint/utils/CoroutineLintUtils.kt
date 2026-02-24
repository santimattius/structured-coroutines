/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.utils

import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.*

/**
 * Utility functions for detecting coroutine patterns in Android Lint rules.
 */
object CoroutineLintUtils {

    /**
     * Coroutine builder function names.
     */
    val COROUTINE_BUILDERS = setOf(
        "launch",
        "async",
        "runBlocking",
        "withContext",
        "coroutineScope",
        "supervisorScope"
    )

    /**
     * Names of scope builders whose block has CoroutineScope as receiver (ensureActive() available).
     */
    val SCOPE_BUILDER_NAMES = setOf(
        "launch",
        "async",
        "coroutineScope",
        "supervisorScope",
        "withContext"
    )

    /**
     * Framework scope property names (Android/Compose).
     */
    val FRAMEWORK_SCOPE_PROPERTIES = setOf(
        "viewModelScope",
        "lifecycleScope"
    )

    /**
     * Framework scope function names (Compose).
     */
    val FRAMEWORK_SCOPE_FUNCTIONS = setOf(
        "rememberCoroutineScope"
    )

    /**
     * Checks if a UCallExpression is a GlobalScope call.
     * Handles both simple names (GlobalScope) and fully qualified names (kotlinx.coroutines.GlobalScope).
     */
    fun isGlobalScopeCall(call: UCallExpression): Boolean {
        val receiver = call.receiver
        val receiverName = when (receiver) {
            is UQualifiedReferenceExpression -> receiver.asSourceString()
            is UReferenceExpression -> receiver.asSourceString()
            else -> null
        }
        val isGlobalScope = receiverName == "GlobalScope" ||
                           receiverName == "kotlinx.coroutines.GlobalScope"
        return isGlobalScope && call.methodName in COROUTINE_BUILDERS
    }

    /**
     * Checks if a UCallExpression is a framework scope call (viewModelScope, lifecycleScope, etc.).
     */
    fun isFrameworkScopeCall(call: UCallExpression): Boolean {
        val receiver = call.receiver
        
        // Handle UReferenceExpression (direct property access: viewModelScope.launch)
        if (receiver is UReferenceExpression) {
            val receiverName = receiver.asSourceString()
            if (receiverName in FRAMEWORK_SCOPE_PROPERTIES) {
                return true
            }
        }
        
        // Handle UQualifiedReferenceExpression (qualified access: this.viewModelScope.launch)
        if (receiver is UQualifiedReferenceExpression) {
            val receiverName = receiver.receiver.asSourceString()
            if (receiverName in FRAMEWORK_SCOPE_PROPERTIES) {
                return true
            }
        }
        
        // Check function-based scopes (rememberCoroutineScope())
        val methodName = call.methodName
        if (methodName in FRAMEWORK_SCOPE_FUNCTIONS) {
            return true
        }
        
        return false
    }

    /**
     * Checks if a UCallExpression is an inline CoroutineScope creation.
     * Example: CoroutineScope(Dispatchers.IO).launch { }
     */
    fun isInlineCoroutineScopeCreation(call: UCallExpression): Boolean {
        val receiver = call.receiver as? UCallExpression
        return receiver?.methodName == "CoroutineScope" &&
            call.methodName in COROUTINE_BUILDERS
    }

    /**
     * Checks if a UCallExpression is a runBlocking call.
     */
    fun isRunBlockingCall(call: UCallExpression): Boolean {
        return call.methodName == "runBlocking"
    }

    /**
     * Checks if a UCallExpression uses Dispatchers.Unconfined.
     */
    fun usesUnconfinedDispatcher(call: UCallExpression): Boolean {
        val arguments = call.valueArguments
        for (arg in arguments) {
            val argSource = arg.asSourceString()
            if (argSource.contains("Dispatchers.Unconfined") || 
                argSource.contains("Dispatchers.UNCONFINED")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a UCallExpression uses Dispatchers.Main.
     * Uses AST traversal to be robust against argument reordering.
     */
    fun usesMainDispatcher(call: UCallExpression): Boolean {
        val arguments = call.valueArguments
        for (arg in arguments) {
            // Skip lambda arguments
            if (arg is ULambdaExpression) continue

            if (containsMainDispatcherReference(arg)) {
                return true
            }
        }
        return false
    }

    /**
     * Recursively checks if an expression contains a reference to Dispatchers.Main.
     */
    private fun containsMainDispatcherReference(expr: UExpression): Boolean {
        // Check if this is a qualified reference like Dispatchers.Main
        if (expr is UQualifiedReferenceExpression) {
            val receiver = expr.receiver
            val selector = expr.selector

            // Check for Dispatchers.Main or Dispatchers.Main.immediate
            val receiverSource = receiver.asSourceString()
            if (receiverSource == "Dispatchers" || receiverSource.endsWith(".Dispatchers")) {
                val selectorName = when (selector) {
                    is UReferenceExpression -> selector.asSourceString()
                    is UQualifiedReferenceExpression -> selector.receiver.asSourceString()
                    else -> null
                }
                if (selectorName == "Main") {
                    return true
                }
            }

            // Check for kotlinx.coroutines.Dispatchers.Main (fully qualified)
            if (receiverSource.contains("Dispatchers")) {
                val selectorSource = selector.asSourceString()
                if (selectorSource == "Main" || selectorSource.startsWith("Main.")) {
                    return true
                }
            }

            // Recurse into parts
            if (containsMainDispatcherReference(receiver)) return true
            if (selector is UExpression && containsMainDispatcherReference(selector)) return true
        }

        // Check the asSourceString which should contain the expression representation
        val sourceString = expr.asSourceString()
        val mainPattern = Regex("""Dispatchers\.Main(\b|\.immediate)?""")
        if (mainPattern.containsMatchIn(sourceString)) {
            return true
        }

        // Also check PSI text as fallback
        val psiText = expr.sourcePsi?.text
        if (psiText != null && mainPattern.containsMatchIn(psiText)) {
            return true
        }

        return false
    }

    /**
     * Returns true if the element is inside the block lambda of launch/async/coroutineScope/supervisorScope/withContext.
     * In that case ensureActive() can be used; otherwise use currentCoroutineContext().ensureActive(), yield(), or delay().
     */
    fun isInsideScopeBuilderBlock(element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is ULambdaExpression) {
                val parent = current.uastParent
                if (parent is UCallExpression && parent.methodName in SCOPE_BUILDER_NAMES) {
                    return true
                }
                // Lambda as value argument: parent may be argument list, then call
                var p: UElement? = parent
                while (p != null) {
                    if (p is UCallExpression && p.methodName in SCOPE_BUILDER_NAMES) {
                        return true
                    }
                    p = p.uastParent
                }
            }
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if the element is inside a suspend function.
     */
    fun isInSuspendFunction(context: JavaContext, element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            // UMethod represents both Java methods and Kotlin functions in UAST
            if (current is UMethod) {
                val method = current.javaPsi
                if (isSuspendMethod(method)) {
                    return true
                }
                // Also check source code for Kotlin suspend functions
                val source = current.sourcePsi?.text ?: ""
                if (source.contains("suspend fun") || source.contains("suspend ")) {
                    return true
                }
            }
            
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if a PsiMethod is a suspend function.
     */
    private fun isSuspendMethod(method: PsiMethod): Boolean {
        // For Kotlin suspend functions, check the source code
        val source = method.text
        if (source.contains("suspend fun") || source.contains("suspend ")) {
            return true
        }
        
        // Check for @JvmSynthetic annotation (often used for suspend functions)
        val annotations = method.annotations
        for (annotation in annotations) {
            if (annotation.qualifiedName == "kotlin.jvm.JvmSynthetic") {
                // Check if it's likely a suspend function by examining the signature
                val returnType = method.returnType?.canonicalText
                if (returnType != null && returnType.contains("Continuation")) {
                    return true
                }
            }
        }
        
        return false
    }

    /**
     * Gets the fully qualified name of a method call.
     */
    fun getFullyQualifiedMethodName(call: UCallExpression): String {
        val methodName = call.methodName ?: return ""
        val receiver = call.receiver
        
        return when (receiver) {
            is UQualifiedReferenceExpression -> {
                val receiverName = receiver.receiver.asSourceString()
                "$receiverName.$methodName"
            }
            is UCallExpression -> {
                val receiverName = receiver.methodName
                "$receiverName.$methodName"
            }
            else -> methodName
        }
    }

    /**
     * Checks if a call is a coroutine builder call.
     */
    fun isCoroutineBuilderCall(call: UCallExpression): Boolean {
        return call.methodName in COROUTINE_BUILDERS
    }

    /**
     * Checks if a class extends CancellationException.
     */
    fun extendsCancellationException(context: JavaContext, classElement: UClass): Boolean {
        val superTypes = classElement.uastSuperTypes
        for (superType in superTypes) {
            val typeName = superType.type.canonicalText
            if (typeName == "kotlinx.coroutines.CancellationException" ||
                typeName == "java.util.concurrent.CancellationException") {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a call expression is inside a finally block.
     */
    fun isInFinallyBlock(element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is UTryExpression) {
                val finallyClause = current.finallyClause
                if (finallyClause != null) {
                    val finallyPsi = finallyClause.sourcePsi
                    val elementPsi = element.sourcePsi
                    if (finallyPsi != null && elementPsi != null && 
                        PsiTreeUtil.isAncestor(finallyPsi, elementPsi, false)) {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if a call is wrapped in withContext(NonCancellable).
     */
    fun isWrappedInNonCancellable(call: UCallExpression): Boolean {
        var current: UElement? = call
        while (current != null) {
            if (current is UCallExpression && current.methodName == "withContext") {
                val arguments = current.valueArguments
                for (arg in arguments) {
                    val argSource = arg.asSourceString()
                    if (argSource.contains("NonCancellable")) {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }
}
