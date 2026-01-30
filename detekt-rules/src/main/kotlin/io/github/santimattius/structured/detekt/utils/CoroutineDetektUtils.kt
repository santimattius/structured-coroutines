/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Utility functions for detecting coroutine patterns in Detekt rules.
 */
object CoroutineDetektUtils {

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
     * Known blocking methods that should not be called inside coroutines.
     * Format: "fully.qualified.ClassName.methodName" or "ClassName.methodName"
     */
    val BLOCKING_METHODS = setOf(
        // Thread blocking
        "Thread.sleep",
        "java.lang.Thread.sleep",
        
        // Object wait/notify
        "Object.wait",
        "java.lang.Object.wait",
        
        // I/O blocking
        "InputStream.read",
        "java.io.InputStream.read",
        "OutputStream.write",
        "java.io.OutputStream.write",
        "Reader.read",
        "java.io.Reader.read",
        "Writer.write",
        "java.io.Writer.write",
        "BufferedReader.readLine",
        "java.io.BufferedReader.readLine",
        
        // JDBC blocking
        "Statement.execute",
        "java.sql.Statement.execute",
        "Statement.executeQuery",
        "java.sql.Statement.executeQuery",
        "Statement.executeUpdate",
        "java.sql.Statement.executeUpdate",
        "PreparedStatement.execute",
        "java.sql.PreparedStatement.execute",
        "PreparedStatement.executeQuery",
        "java.sql.PreparedStatement.executeQuery",
        "PreparedStatement.executeUpdate",
        "java.sql.PreparedStatement.executeUpdate",
        "Connection.prepareStatement",
        "java.sql.Connection.prepareStatement",
        "ResultSet.next",
        "java.sql.ResultSet.next",
        
        // OkHttp synchronous
        "Call.execute",
        "okhttp3.Call.execute",
        
        // Retrofit synchronous
        "retrofit2.Call.execute",
        
        // BlockingQueue
        "BlockingQueue.take",
        "java.util.concurrent.BlockingQueue.take",
        "BlockingQueue.put",
        "java.util.concurrent.BlockingQueue.put",
        
        // CountDownLatch
        "CountDownLatch.await",
        "java.util.concurrent.CountDownLatch.await",
        
        // Semaphore
        "Semaphore.acquire",
        "java.util.concurrent.Semaphore.acquire",
        
        // Future blocking
        "Future.get",
        "java.util.concurrent.Future.get",
        
        // Files (Java NIO blocking operations)
        "Files.readAllBytes",
        "java.nio.file.Files.readAllBytes",
        "Files.readAllLines",
        "java.nio.file.Files.readAllLines",
        "Files.write",
        "java.nio.file.Files.write",
    )

    /**
     * Checks if the given element is inside a coroutine context.
     * This includes being inside launch, async, withContext, etc.
     */
    fun isInsideCoroutine(element: KtElement): Boolean {
        var current: KtElement? = element
        
        while (current != null) {
            // Check if we're inside a lambda argument of a coroutine builder
            val lambdaArg = current.getParentOfType<KtLambdaArgument>(strict = true)
            if (lambdaArg != null) {
                val callExpression = lambdaArg.getParentOfType<KtCallExpression>(strict = true)
                if (callExpression != null && isCoroutineBuilderCall(callExpression)) {
                    return true
                }
            }
            
            // Check if we're inside a suspend function
            val function = current.getParentOfType<KtNamedFunction>(strict = true)
            if (function != null && function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SUSPEND_KEYWORD)) {
                return true
            }
            
            current = current.parent as? KtElement
        }
        
        return false
    }

    /**
     * Checks if a call expression is a coroutine builder call.
     */
    fun isCoroutineBuilderCall(callExpression: KtCallExpression): Boolean {
        val calleeName = callExpression.calleeExpression?.text ?: return false
        return calleeName in COROUTINE_BUILDERS
    }

    /**
     * Checks if a call expression is a blocking call.
     */
    fun isBlockingCall(callExpression: KtCallExpression): Boolean {
        val calleeName = getFullyQualifiedCallName(callExpression)
        return BLOCKING_METHODS.any { blocking ->
            calleeName.endsWith(blocking) || calleeName == blocking
        }
    }

    /**
     * Gets the fully qualified name of a call expression.
     * For `foo.bar.baz()`, returns "foo.bar.baz".
     */
    fun getFullyQualifiedCallName(callExpression: KtCallExpression): String {
        val parent = callExpression.parent
        return if (parent is KtDotQualifiedExpression) {
            val receiverText = parent.receiverExpression.text
            val selectorText = callExpression.calleeExpression?.text ?: ""
            "$receiverText.$selectorText"
        } else {
            callExpression.calleeExpression?.text ?: ""
        }
    }

    /**
     * Checks if a function is a suspend function.
     */
    fun isSuspendFunction(function: KtNamedFunction): Boolean {
        return function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SUSPEND_KEYWORD)
    }

    /**
     * Checks if a call is to a cooperation point function.
     */
    fun isCooperationPoint(callExpression: KtCallExpression): Boolean {
        val calleeName = callExpression.calleeExpression?.text ?: return false
        return calleeName in COOPERATION_POINTS
    }

    /**
     * Checks if the file is a test file based on naming convention.
     */
    fun isTestFile(fileName: String): Boolean {
        return fileName.endsWith("Test.kt") ||
            fileName.endsWith("Tests.kt") ||
            fileName.endsWith("Spec.kt") ||
            fileName.contains("/test/") ||
            fileName.contains("/androidTest/")
    }
}
