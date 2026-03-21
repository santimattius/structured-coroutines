/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR Class Checker that detects classes extending `CancellationException`.
 *
 * ## Problem (Best Practice 5.2)
 *
 * Defining domain errors that inherit from `CancellationException` to "leverage" cancellation
 * is a bad practice:
 *
 * ```kotlin
 * // ❌ BAD: Domain error extending CancellationException
 * class UserNotFoundException : CancellationException("User not found")
 * ```
 *
 * This type of exception doesn't propagate upward like others; it only cancels the current
 * coroutine and its children, which can break error logic and make debugging difficult.
 *
 * ## Recommended Practice
 *
 * For domain errors, use normal `Exception` or `RuntimeException`:
 *
 * ```kotlin
 * // ✅ GOOD: Normal exception for domain errors
 * class UserNotFoundException : Exception("User not found")
 *
 * // ✅ GOOD: Custom domain exception hierarchy
 * sealed class DomainException : Exception()
 * class UserNotFoundException : DomainException()
 * ```
 *
 * Reserve `CancellationException` for true cancellation cases only.
 *
 * ## Detection
 *
 * This checker analyzes the class hierarchy and reports an error if any user-defined class
 * directly or indirectly extends `kotlinx.coroutines.CancellationException` or
 * `kotlin.coroutines.cancellation.CancellationException`.
 *
 * @see <a href="https://kotlinlang.org/docs/cancellation-and-timeouts.html">Kotlin Cancellation</a>
 */
@OptIn(SymbolInternals::class)
class CancellationExceptionSubclassChecker : FirClassChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * ClassIds for CancellationException in different packages.
         * - kotlinx.coroutines.CancellationException (main one used in coroutines)
         * - kotlin.coroutines.cancellation.CancellationException (standard library)
         * - java.util.concurrent.CancellationException (JVM)
         */
        private val CANCELLATION_EXCEPTION_CLASS_IDS = setOf(
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("CancellationException")),
            ClassId(FqName("kotlin.coroutines.cancellation"), Name.identifier("CancellationException")),
            ClassId(FqName("java.util.concurrent"), Name.identifier("CancellationException"))
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        // Check all supertypes of this class.
        // Use ConeClassLikeType.lookupTag.classId instead of ConeKotlinType.toClassSymbol()
        // to avoid a binary-incompatible API change in Kotlin 2.3.20 where the context-receiver
        // overload of toClassSymbol(FirSessionHolder, ConeKotlinType, FirSession) was removed.
        for (superTypeRef in declaration.superTypeRefs) {
            val superClassId = (superTypeRef.coneType as? ConeClassLikeType)
                ?.lookupTag?.classId ?: continue

            if (isCancellationExceptionOrSubclass(superClassId, context, mutableSetOf())) {
                reporter.reportCancellationExceptionSubclass(declaration, context)
                return
            }
        }
    }

    /**
     * Recursively checks if a class is CancellationException or extends it.
     * This handles cases like:
     * ```kotlin
     * open class MyBaseException : CancellationException()
     * class MyDomainError : MyBaseException() // Should also be flagged
     * ```
     */
    private fun isCancellationExceptionOrSubclass(
        classId: ClassId,
        context: CheckerContext,
        visited: MutableSet<ClassId>
    ): Boolean {
        // Avoid infinite loops in case of cyclic inheritance (shouldn't happen but be safe)
        if (classId in visited) return false
        visited.add(classId)

        // Direct match
        if (classId in CANCELLATION_EXCEPTION_CLASS_IDS) return true

        // Look up the supertypes of this class via the symbol provider.
        val classSymbol = context.session.symbolProvider.getClassLikeSymbolByClassId(classId)
        if (classSymbol != null) {
            val firClass = classSymbol.fir as? FirClass
            if (firClass != null) {
                for (superTypeRef in firClass.superTypeRefs) {
                    val superClassId = (superTypeRef.coneType as? ConeClassLikeType)
                        ?.lookupTag?.classId ?: continue
                    if (isCancellationExceptionOrSubclass(superClassId, context, visited)) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
