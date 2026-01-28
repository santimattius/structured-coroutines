/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.WrapWithIODispatcherQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Inspection that detects blocking code on Dispatchers.Main.
 *
 * Running blocking operations (Thread.sleep, I/O, JDBC, etc.) on the main
 * dispatcher can cause ANRs on Android or freeze the UI on desktop.
 *
 * Example of problematic code:
 * ```kotlin
 * withContext(Dispatchers.Main) {
 *     Thread.sleep(1000)  // Blocks UI thread!
 *     loadFromDatabase()  // Blocking I/O on Main!
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * withContext(Dispatchers.IO) {
 *     Thread.sleep(1000)
 *     loadFromDatabase()
 * }
 * ```
 */
class MainDispatcherMisuseInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.main.dispatcher.display.name"
    override val descriptionKey = "inspection.main.dispatcher.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // Check if this is a blocking call
                if (!isBlockingCall(expression)) return

                // Check if we're inside a Main dispatcher context
                if (isInsideMainDispatcherContext(expression)) {
                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.main.dispatcher.blocking"),
                        WrapWithIODispatcherQuickFix()
                    )
                }
            }
        }
    }

    private fun isBlockingCall(call: KtCallExpression): Boolean {
        val calleeName = getFullyQualifiedCallName(call)
        return CoroutinePsiUtils.BLOCKING_METHODS.any { blocking ->
            calleeName.endsWith(blocking) || calleeName == blocking
        }
    }

    private fun getFullyQualifiedCallName(call: KtCallExpression): String {
        val parent = call.parent
        return if (parent is KtDotQualifiedExpression) {
            val receiverText = parent.receiverExpression.text
            val selectorText = call.calleeExpression?.text ?: ""
            "$receiverText.$selectorText"
        } else {
            call.calleeExpression?.text ?: ""
        }
    }

    private fun isInsideMainDispatcherContext(element: KtCallExpression): Boolean {
        var current = element.getParentOfType<KtLambdaArgument>(strict = true)

        while (current != null) {
            val parentCall = current.getParentOfType<KtCallExpression>(strict = true)
            if (parentCall != null) {
                val methodName = parentCall.calleeExpression?.text

                // Check withContext or launch/async with Main dispatcher
                if (methodName == "withContext" || methodName == "launch" || methodName == "async") {
                    if (CoroutinePsiUtils.usesMainDispatcher(parentCall)) {
                        return true
                    }
                }
            }

            current = current.getParentOfType<KtLambdaArgument>(strict = true)
        }

        return false
    }
}
