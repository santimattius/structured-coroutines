/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.utils

import com.android.tools.lint.detector.api.JavaContext
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * Android-specific utility functions for Lint rules.
 */
object AndroidLintUtils {

    /**
     * Known blocking methods that should not be called on Main dispatcher.
     */
    val BLOCKING_METHODS = setOf(
        "Thread.sleep",
        "java.lang.Thread.sleep",
        "InputStream.read",
        "java.io.InputStream.read",
        "OutputStream.write",
        "java.io.OutputStream.write",
        "BufferedReader.readLine",
        "java.io.BufferedReader.readLine",
        "Statement.execute",
        "java.sql.Statement.execute",
        "Statement.executeQuery",
        "java.sql.Statement.executeQuery",
        "Connection.prepareStatement",
        "java.sql.Connection.prepareStatement",
        "ResultSet.next",
        "java.sql.ResultSet.next",
        "Call.execute",
        "okhttp3.Call.execute",
        "retrofit2.Call.execute",
        "Future.get",
        "java.util.concurrent.Future.get",
        "BlockingQueue.take",
        "java.util.concurrent.BlockingQueue.take",
        "CountDownLatch.await",
        "java.util.concurrent.CountDownLatch.await",
        "Semaphore.acquire",
        "java.util.concurrent.Semaphore.acquire"
    )

    /**
     * Checks if a call expression contains blocking calls.
     */
    fun containsBlockingCall(call: UCallExpression): Boolean {
        val methodName = call.methodName ?: return false
        val fullyQualifiedName = CoroutineLintUtils.getFullyQualifiedMethodName(call)
        
        // Direct match on fully qualified name
        if (fullyQualifiedName in BLOCKING_METHODS) {
            return true
        }
        
        // Check if any blocking method matches
        for (blocking in BLOCKING_METHODS) {
            // Extract the method name from the blocking pattern (e.g., "Thread.sleep" -> "sleep")
            val blockingMethodName = blocking.substringAfterLast(".")
            
            // Check if method name matches
            if (methodName == blockingMethodName || fullyQualifiedName.endsWith(blocking)) {
                // Additional check: verify the receiver matches
                val receiver = call.receiver
                val receiverName = when (receiver) {
                    is org.jetbrains.uast.UQualifiedReferenceExpression -> receiver.receiver.asSourceString()
                    is org.jetbrains.uast.UCallExpression -> receiver.methodName
                    else -> null
                }
                
                // Check if receiver matches the class in the blocking pattern
                val blockingClass = blocking.substringBeforeLast(".")
                if (receiverName != null && (receiverName.contains(blockingClass) || blockingClass.contains(receiverName))) {
                    return true
                }
                
                // Also check by simple method name match (for common cases like Thread.sleep)
                if (blockingMethodName == methodName && blockingClass.isNotEmpty()) {
                    return true
                }
            }
        }
        
        return false
    }

    /**
     * Checks if the element is inside a ViewModel class.
     */
    fun isInViewModel(context: JavaContext, element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is org.jetbrains.uast.UClass) {
                val className = current.qualifiedName ?: current.name
                val superTypes = current.uastSuperTypes
                
                // Check if extends ViewModel
                for (superType in superTypes) {
                    val typeName = superType.type.canonicalText
                    if (typeName == "androidx.lifecycle.ViewModel" ||
                        typeName == "android.arch.lifecycle.ViewModel") {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if the element is inside a LifecycleOwner (Activity, Fragment, etc.).
     */
    fun isInLifecycleOwner(context: JavaContext, element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is org.jetbrains.uast.UClass) {
                val superTypes = current.uastSuperTypes
                
                // Check if implements LifecycleOwner or extends Lifecycle-aware component
                for (superType in superTypes) {
                    val typeName = superType.type.canonicalText
                    if (typeName == "androidx.lifecycle.LifecycleOwner" ||
                        typeName == "androidx.appcompat.app.AppCompatActivity" ||
                        typeName == "androidx.fragment.app.Fragment" ||
                        typeName == "androidx.activity.ComponentActivity") {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if the file is a test file based on naming convention.
     */
    fun isTestFile(context: JavaContext, element: UElement): Boolean {
        val file = element.sourcePsi?.containingFile ?: return false
        val fileName = file.name
        
        return fileName.endsWith("Test.kt") ||
            fileName.endsWith("Tests.kt") ||
            fileName.endsWith("Spec.kt") ||
            fileName.contains("/test/") ||
            fileName.contains("/androidTest/")
    }
}
