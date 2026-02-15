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

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*

/**
 * Detects classes that extend CancellationException.
 * 
 * Best Practice 5.2: Extending CancellationException for Domain Errors
 * 
 * Defining domain errors that inherit from CancellationException to "leverage" 
 * cancellation doesn't propagate upward like other exceptions; it only cancels 
 * the current coroutine and its children, which can break error logic.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Domain error extendiendo CancellationException
 * class UserNotFoundException : CancellationException("User not found")
 * 
 * suspend fun fetchUser(id: String) {
 *     if (user == null) {
 *         throw UserNotFoundException()  // Treated as cancellation!
 *     }
 * }
 * 
 * // ✅ GOOD - Regular exception para domain errors
 * class UserNotFoundException : Exception("User not found")
 * 
 * suspend fun fetchUser(id: String) {
 *     if (user == null) {
 *         throw UserNotFoundException()  // Proper exception handling
 *     }
 * }
 * ```
 */
class CancellationExceptionSubclassDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "CancellationExceptionSubclass",
            briefDescription = "Class extends CancellationException",
            explanation = """
                [EXCEPT_002] Defining domain errors that inherit from CancellationException doesn't
                propagate upward like other exceptions; it only cancels the current
                coroutine and its children, which can break error logic.

                For domain errors, use normal Exception or RuntimeException.
                Reserve CancellationException for true cancellation cases.

                See: ${LintDocUrl.buildDocLink("52-except_002--extending-cancellationexception-for-domain-errors")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                CancellationExceptionSubclassDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun visitClass(
        context: JavaContext,
        declaration: UClass
    ) {
        if (CoroutineLintUtils.extendsCancellationException(context, declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration as UElement),
                "Don't extend CancellationException for domain errors. Use Exception or RuntimeException instead"
            )
        }
    }
}
