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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Utility functions for detecting coroutine patterns in IntelliJ PSI.
 */
object CoroutinePsiUtils {

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
     * Names of scope builders whose block has CoroutineScope as receiver (so ensureActive() is available).
     */
    val SCOPE_BUILDER_NAMES = setOf(
        "launch",
        "async",
        "coroutineScope",
        "supervisorScope",
        "withContext"
    )

    /**
     * Functions that provide cooperation points for cancellation.
     */
    val COOPERATION_POINTS = setOf(
        "yield",
        "ensureActive",
        "delay",
        "suspendCancellableCoroutine",
        "withTimeout",
        "withTimeoutOrNull"
    )

    /**
     * Known blocking methods that should not be called inside coroutines on Main dispatcher.
     */
    val BLOCKING_METHODS = setOf(
        "Thread.sleep",
        "Object.wait",
        "InputStream.read",
        "OutputStream.write",
        "Reader.read",
        "Writer.write",
        "BufferedReader.readLine",
        "Statement.execute",
        "Statement.executeQuery",
        "Statement.executeUpdate",
        "PreparedStatement.execute",
        "PreparedStatement.executeQuery",
        "PreparedStatement.executeUpdate",
        "Connection.prepareStatement",
        "ResultSet.next",
        "Call.execute",
        "BlockingQueue.take",
        "BlockingQueue.put",
        "CountDownLatch.await",
        "Semaphore.acquire",
        "Future.get",
        "Files.readAllBytes",
        "Files.readAllLines",
        "Files.write"
    )

    /**
     * Checks if a KtCallExpression is a GlobalScope call.
     */
    fun isGlobalScopeCall(call: KtCallExpression): Boolean {
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        val receiver = parent.receiverExpression
        return isGlobalScope(receiver) && call.calleeExpression?.text in COROUTINE_BUILDERS
    }

    /**
     * Checks if an expression is GlobalScope.
     */
    fun isGlobalScope(expression: KtExpression): Boolean {
        return when (expression) {
            is KtNameReferenceExpression -> expression.text == "GlobalScope"
            is KtDotQualifiedExpression -> {
                val selector = expression.selectorExpression?.text
                selector == "GlobalScope"
            }
            else -> false
        }
    }

    /**
     * Checks if a KtCallExpression is a framework scope call (viewModelScope, lifecycleScope, etc.).
     */
    fun isFrameworkScopeCall(call: KtCallExpression): Boolean {
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        val receiver = parent.receiverExpression

        // Check for direct property access: viewModelScope.launch
        if (receiver is KtNameReferenceExpression) {
            if (receiver.text in FRAMEWORK_SCOPE_PROPERTIES) {
                return true
            }
        }

        // Check for qualified access: this.viewModelScope.launch
        if (receiver is KtDotQualifiedExpression) {
            val selector = receiver.selectorExpression?.text
            if (selector in FRAMEWORK_SCOPE_PROPERTIES) {
                return true
            }
        }

        // Check function-based scopes (rememberCoroutineScope())
        if (receiver is KtCallExpression) {
            val receiverCallName = receiver.calleeExpression?.text
            if (receiverCallName in FRAMEWORK_SCOPE_FUNCTIONS) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if a KtCallExpression is an inline CoroutineScope creation.
     * Example: CoroutineScope(Dispatchers.IO).launch { }
     */
    fun isInlineCoroutineScopeCreation(call: KtCallExpression): Boolean {
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        val receiver = parent.receiverExpression as? KtCallExpression ?: return false
        return receiver.calleeExpression?.text == "CoroutineScope" &&
            call.calleeExpression?.text in COROUTINE_BUILDERS
    }

    /**
     * Checks if a KtCallExpression is a runBlocking call.
     */
    fun isRunBlockingCall(call: KtCallExpression): Boolean {
        return call.calleeExpression?.text == "runBlocking"
    }

    /**
     * Checks if a KtCallExpression is a coroutine builder call.
     */
    fun isCoroutineBuilderCall(call: KtCallExpression): Boolean {
        return call.calleeExpression?.text in COROUTINE_BUILDERS
    }

    /**
     * Checks if the element is inside a suspend function.
     */
    fun isInSuspendFunction(element: PsiElement): Boolean {
        val function = element.getParentOfType<KtNamedFunction>(strict = true)
        return function?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
    }

    /**
     * Returns true if the element is inside the block lambda of launch/async/coroutineScope/supervisorScope/withContext.
     * In that case CoroutineScope is the receiver and ensureActive() can be used without currentCoroutineContext().
     */
    fun isInsideScopeBuilderBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true) ?: run {
                current = current.parent
                continue
            }
            val call = lambda.parent as? KtCallExpression
            if (call != null && call.calleeExpression?.text in SCOPE_BUILDER_NAMES) {
                return true
            }
            val lambdaArg = lambda.parent as? KtLambdaArgument
            val callFromArg = lambdaArg?.parent?.parent as? KtCallExpression
            if (callFromArg != null && callFromArg.calleeExpression?.text in SCOPE_BUILDER_NAMES) {
                return true
            }
            current = lambda.parent
        }
        return false
    }

    /**
     * Checks if the element is inside a coroutine context (lambda of a coroutine builder).
     */
    fun isInsideCoroutineContext(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            // Check if we're inside a lambda argument of a coroutine builder
            val lambdaArg = current.getParentOfType<KtLambdaArgument>(strict = true)
            if (lambdaArg != null) {
                val callExpression = lambdaArg.getParentOfType<KtCallExpression>(strict = true)
                if (callExpression != null && isCoroutineBuilderCall(callExpression)) {
                    return true
                }
            }

            // Check lambda expression directly
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true)
            if (lambda != null) {
                val parentCall = lambda.parent?.parent as? KtCallExpression
                if (parentCall != null && isCoroutineBuilderCall(parentCall)) {
                    return true
                }
            }

            // Check if we're inside a suspend function
            val function = current.getParentOfType<KtNamedFunction>(strict = true)
            if (function?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) {
                return true
            }

            current = current.parent
        }
        return false
    }

    /**
     * Checks if a call expression uses Dispatchers.Main.
     */
    fun usesMainDispatcher(call: KtCallExpression): Boolean {
        val arguments = call.valueArguments
        for (arg in arguments) {
            val argText = arg.text
            if (argText.contains("Dispatchers.Main") ||
                argText.contains("Main") && argText.contains("Dispatchers")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a call expression uses Dispatchers.Unconfined.
     */
    fun usesUnconfinedDispatcher(call: KtCallExpression): Boolean {
        val arguments = call.valueArguments
        for (arg in arguments) {
            val argText = arg.text
            if (argText.contains("Dispatchers.Unconfined")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a call is inside a finally block.
     */
    fun isInFinallyBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtFinallySection) {
                return true
            }
            // Stop at function boundary
            if (current is KtNamedFunction || current is KtLambdaExpression) {
                return false
            }
            current = current.parent
        }
        return false
    }

    /**
     * Checks if a call is wrapped in withContext(NonCancellable).
     */
    fun isWrappedInNonCancellable(call: KtCallExpression): Boolean {
        var current: PsiElement? = call
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "withContext") {
                val arguments = current.valueArguments
                for (arg in arguments) {
                    if (arg.text.contains("NonCancellable")) {
                        return true
                    }
                }
            }
            // Stop at function boundary
            if (current is KtNamedFunction) {
                break
            }
            current = current.parent
        }
        return false
    }

    /**
     * Checks if a catch clause catches Exception or Throwable generically.
     */
    fun catchesGenericException(catchClause: KtCatchClause): Boolean {
        val typeReference = catchClause.catchParameter?.typeReference?.text
        return typeReference == "Exception" ||
               typeReference == "Throwable" ||
               typeReference == "java.lang.Exception" ||
               typeReference == "java.lang.Throwable"
    }

    /**
     * Checks if there's a CancellationException catch before the generic catch.
     */
    fun hasCancellationExceptionCatch(tryExpression: KtTryExpression): Boolean {
        for (catchClause in tryExpression.catchClauses) {
            val typeReference = catchClause.catchParameter?.typeReference?.text
            if (typeReference?.contains("CancellationException") == true) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the given class extends CancellationException.
     */
    fun extendsCancellationException(classDeclaration: KtClass): Boolean {
        val superTypes = classDeclaration.superTypeListEntries
        for (superType in superTypes) {
            val typeName = superType.text
            if (typeName.contains("CancellationException")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a call uses Job() or SupervisorJob() in its context argument.
     */
    fun usesJobInContext(call: KtCallExpression): Boolean {
        val arguments = call.valueArguments
        for (arg in arguments) {
            val argText = arg.text
            if (argText.contains("Job()") || argText.contains("SupervisorJob()")) {
                return true
            }
        }
        return false
    }

    /**
     * Finds the receiver scope name for a coroutine builder call.
     */
    fun getScopeName(call: KtCallExpression): String? {
        val parent = call.parent as? KtDotQualifiedExpression ?: return null
        val receiver = parent.receiverExpression
        return when (receiver) {
            is KtNameReferenceExpression -> receiver.text
            is KtDotQualifiedExpression -> receiver.selectorExpression?.text
            else -> null
        }
    }

    /**
     * Gets the containing function for a call expression.
     */
    fun getContainingFunction(element: PsiElement): KtNamedFunction? {
        return element.getParentOfType<KtNamedFunction>(strict = false)
    }

    /**
     * Checks if a function is a test function (has @Test annotation).
     */
    fun isTestFunction(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any {
            it.shortName?.asString() == "Test"
        }
    }

    /**
     * Checks if a function is a main function.
     */
    fun isMainFunction(function: KtNamedFunction): Boolean {
        return function.name == "main"
    }

    /**
     * Checks if the expression is a suspend function call.
     */
    fun isSuspendCall(call: KtCallExpression): Boolean {
        // This is a heuristic check - full resolution requires resolve context
        val calleeName = call.calleeExpression?.text ?: return false
        return calleeName in COOPERATION_POINTS ||
               calleeName.startsWith("suspend") ||
               calleeName.endsWith("Async") ||
               calleeName.endsWith("await")
    }

    /**
     * Checks if the class is a ViewModel subclass.
     */
    fun isViewModelClass(classDeclaration: KtClass): Boolean {
        val superTypes = classDeclaration.superTypeListEntries
        for (superType in superTypes) {
            val typeName = superType.text
            if (typeName.contains("ViewModel")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the class implements LifecycleOwner.
     */
    fun isLifecycleOwnerClass(classDeclaration: KtClass): Boolean {
        val superTypes = classDeclaration.superTypeListEntries
        for (superType in superTypes) {
            val typeName = superType.text
            if (typeName.contains("LifecycleOwner") ||
                typeName.contains("Activity") ||
                typeName.contains("Fragment") ||
                typeName.contains("AppCompatActivity") ||
                typeName.contains("ComponentActivity")) {
                return true
            }
        }
        return false
    }

    /**
     * Gets all call expressions in a function that match the given method name.
     */
    fun findCallsInFunction(function: KtNamedFunction, methodName: String): List<KtCallExpression> {
        val result = mutableListOf<KtCallExpression>()
        function.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.calleeExpression?.text == methodName) {
                    result.add(expression)
                }
            }
        })
        return result
    }
}
