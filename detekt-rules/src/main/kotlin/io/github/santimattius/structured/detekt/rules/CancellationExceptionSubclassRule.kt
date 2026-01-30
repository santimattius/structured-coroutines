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

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

/**
 * Detekt rule that detects classes extending CancellationException.
 *
 * ## Problem (Best Practice 5.2)
 *
 * Domain errors should not extend CancellationException. CancellationException is a special
 * exception used by the coroutines framework for cancellation propagation. Extending it for
 * domain errors can cause unexpected behavior in cancellation handling.
 *
 * ```kotlin
 * // ❌ BAD: Domain error extending CancellationException
 * class UserNotFoundException : CancellationException("User not found")
 *
 * suspend fun fetchUser(id: String) {
 *     if (user == null) {
 *         throw UserNotFoundException()  // Treated as cancellation!
 *     }
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use regular Exception for domain errors:
 *
 * ```kotlin
 * // ✅ GOOD: Regular exception for domain errors
 * class UserNotFoundException : Exception("User not found")
 *
 * suspend fun fetchUser(id: String) {
 *     if (user == null) {
 *         throw UserNotFoundException()  // Proper exception handling
 *     }
 * }
 *
 * // ✅ GOOD: Handle cancellation separately
 * suspend fun process() {
 *     try {
 *         fetchUser(id)
 *     } catch (e: CancellationException) {
 *         throw e  // Re-throw cancellation
 *     } catch (e: UserNotFoundException) {
 *         handleError(e)  // Handle domain error
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   CancellationExceptionSubclass:
 *     active: true
 *     severity: error  # or warning
 * ```
 */
class CancellationExceptionSubclassRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "CancellationExceptionSubclass",
        severity = Severity.CodeSmell,
        description = "Domain errors should not extend CancellationException. " +
            "CancellationException is reserved for coroutine cancellation. " +
            "Use regular Exception for domain errors.",
        debt = Debt.TEN_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        // Check supertypes for CancellationException
        val superTypeList = klass.superTypeListEntries
        if (superTypeList.isEmpty()) return

        for (superTypeEntry in superTypeList) {
            if (isCancellationException(superTypeEntry)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(klass),
                        message = buildMessage(klass.name ?: "class")
                    )
                )
                return  // Only report once per class
            }
        }
    }

    private fun isCancellationException(superTypeEntry: KtSuperTypeListEntry): Boolean {
        val typeReference = superTypeEntry.typeReference ?: return false
        val typeText = typeReference.text

        // Check for CancellationException (simple name or fully qualified)
        return typeText == "CancellationException" ||
            typeText.endsWith(".CancellationException") ||
            typeText.contains("CancellationException")
    }

    private fun buildMessage(className: String): String {
        return "Class '$className' extends CancellationException. " +
            "CancellationException is reserved for coroutine cancellation. " +
            "Use regular Exception for domain errors to avoid unexpected cancellation behavior."
    }
}
