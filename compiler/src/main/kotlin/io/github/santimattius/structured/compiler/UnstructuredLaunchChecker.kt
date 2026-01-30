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
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects unstructured coroutine launches.
 *
 * This is the core checker for enforcing structured concurrency. It analyzes calls to
 * `launch` and `async` coroutine builders and verifies that they are being used with
 * properly structured scopes.
 *
 * ## Detected Violations
 *
 * ### 1. GlobalScope Usage (Best Practice 1.1)
 *
 * ```kotlin
 * // ❌ ERROR: GlobalScope bypasses structured concurrency
 * GlobalScope.launch { doWork() }
 * GlobalScope.async { computeValue() }
 * ```
 *
 * **Problem:** GlobalScope creates coroutines that live for the entire application lifetime,
 * breaking the parent-child relationship and preventing proper cancellation propagation.
 *
 * ### 2. Inline CoroutineScope Creation (Best Practice 1.3)
 *
 * ```kotlin
 * // ❌ ERROR: Inline scope creation
 * CoroutineScope(Dispatchers.IO).launch { doWork() }
 * CoroutineScope(Job()).async { computeValue() }
 * ```
 *
 * **Problem:** Creating a scope inline and immediately launching on it creates an orphan
 * coroutine that isn't tied to any lifecycle.
 *
 * ### 3. Unstructured Launch (Core Rule)
 *
 * ```kotlin
 * // ❌ ERROR: Scope not annotated with @StructuredScope
 * fun process(scope: CoroutineScope) {
 *     scope.launch { doWork() }  // scope not marked as structured
 * }
 * ```
 *
 * **Problem:** Using arbitrary scopes without explicitly marking them as structured
 * makes it hard to track coroutine lifecycles and ensure proper cleanup.
 *
 * ## Valid Usage
 *
 * ```kotlin
 * // ✅ GOOD: Using @StructuredScope annotation
 * fun process(@StructuredScope scope: CoroutineScope) {
 *     scope.launch { doWork() }
 * }
 *
 * // ✅ GOOD: Property annotated with @StructuredScope
 * class MyViewModel(@StructuredScope val scope: CoroutineScope) {
 *     fun load() {
 *         scope.launch { fetchData() }
 *     }
 * }
 *
 * // ✅ GOOD: Using structured concurrency patterns
 * suspend fun process() = coroutineScope {
 *     launch { doWork() }
 * }
 *
 * // ✅ GOOD: Android ViewModel scope (lifecycle-aware)
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }
 *     }
 * }
 *
 * // ✅ GOOD: Android Lifecycle scope
 * class MyActivity : AppCompatActivity() {
 *     fun load() {
 *         lifecycleScope.launch { fetchData() }
 *     }
 * }
 *
 * // ✅ GOOD: Compose rememberCoroutineScope
 * @Composable
 * fun MyComposable() {
 *     val scope = rememberCoroutineScope()
 *     Button(onClick = { scope.launch { doWork() } }) {
 *         Text("Click")
 *     }
 * }
 * ```
 *
 * ## Detection Logic
 *
 * 1. Identifies calls to `launch` or `async`
 * 2. Extracts the explicit receiver (the scope)
 * 3. Checks for GlobalScope → reports [StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE]
 * 4. Checks for inline CoroutineScope(...) → reports [StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE]
 * 5. Checks if receiver has @StructuredScope → if not, reports [StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH]
 *
 * @see StructuredCoroutinesErrors
 * @see <a href="https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency">Structured Concurrency</a>
 */
class UnstructuredLaunchChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Target coroutine builder function names.
         */
        private val COROUTINE_BUILDER_NAMES = setOf(
            Name.identifier("launch"),
            Name.identifier("async")
        )

        /**
         * ClassId for kotlinx.coroutines.GlobalScope.
         */
        private val GLOBAL_SCOPE_CLASS_ID = ClassId(
            FqName("kotlinx.coroutines"),
            Name.identifier("GlobalScope")
        )

        /**
         * ClassId for the @StructuredScope annotation.
         */
        private val STRUCTURED_SCOPE_ANNOTATION_CLASS_ID = ClassId(
            FqName("io.github.santimattius.structured.annotations"),
            Name.identifier("StructuredScope")
        )

        /**
         * Well-known framework scope CallableIds that are verified by package origin.
         * This ensures we only accept the real framework scopes, not user-defined properties
         * with the same name.
         *
         * Format: packageName to callableName
         */
        private val FRAMEWORK_SCOPE_CALLABLE_IDS: Set<CallableId> = setOf(
            // androidx.lifecycle.viewModelScope (extension on ViewModel)
            CallableId(FqName("androidx.lifecycle"), Name.identifier("viewModelScope")),
            // androidx.lifecycle.lifecycleScope (extension on LifecycleOwner)
            CallableId(FqName("androidx.lifecycle"), Name.identifier("lifecycleScope")),
            // org.koin.androidx.viewmodel (KMP Koin)
            CallableId(FqName("org.koin.androidx.viewmodel"), Name.identifier("viewModelScope")),
        )

        /**
         * Well-known framework scope function CallableIds (for function calls like rememberCoroutineScope()).
         */
        private val FRAMEWORK_SCOPE_FUNCTION_IDS: Set<CallableId> = setOf(
            // Jetpack Compose
            CallableId(FqName("androidx.compose.runtime"), Name.identifier("rememberCoroutineScope")),
            // Compose Multiplatform (same package in most cases)
            CallableId(FqName("androidx.compose.runtime"), Name.identifier("rememberCoroutineScope")),
        )

        /**
         * Fallback: Simple name check for cases where we can't resolve the callable ID
         * but the name matches a well-known framework scope.
         * This is less strict but provides compatibility when the framework isn't in classpath.
         */
        private val FRAMEWORK_SCOPE_NAMES = setOf(
            Name.identifier("viewModelScope"),
            Name.identifier("lifecycleScope"),
            Name.identifier("rememberCoroutineScope"),
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Step 1: Check if this is a call to launch or async
        if (!isCoroutineBuilderCall(expression)) return

        // Step 2: Get the explicit receiver (the scope on which launch/async is called)
        val receiver = expression.explicitReceiver ?: return

        // Step 3: Check for GlobalScope usage
        if (isGlobalScope(receiver, context)) {
            reporter.reportGlobalScopeUsage(expression, context)
            return
        }

        // Step 4: Check for inline CoroutineScope creation
        if (isInlineCoroutineScopeCreation(receiver)) {
            reporter.reportInlineCoroutineScope(expression, context)
            return
        }

        // Step 5: Check if receiver is annotated with @StructuredScope
        if (!isStructuredScope(receiver, context)) {
            reporter.reportUnstructuredLaunch(expression, context)
        }
    }

    /**
     * Checks if the function call is to `launch` or `async`.
     *
     * @param call The function call to check
     * @return true if this is a coroutine builder call
     */
    private fun isCoroutineBuilderCall(call: FirFunctionCall): Boolean {
        val calleeReference = call.calleeReference
        val name = calleeReference.name
        return name in COROUTINE_BUILDER_NAMES
    }

    /**
     * Checks if the receiver is GlobalScope.
     *
     * Handles both direct references (`GlobalScope.launch`) and indirect access
     * through variables typed as GlobalScope.
     *
     * @param receiver The receiver expression
     * @param context The checker context for symbol resolution
     * @return true if the receiver is GlobalScope
     */
    private fun isGlobalScope(
        receiver: FirExpression,
        context: CheckerContext
    ): Boolean {
        // Check if it's a direct reference to GlobalScope object
        if (receiver is FirResolvedQualifier) {
            return receiver.classId == GLOBAL_SCOPE_CLASS_ID
        }

        // Check the resolved type
        val classSymbol = receiver.resolvedType.toClassSymbol(context.session) ?: return false
        return classSymbol.classId == GLOBAL_SCOPE_CLASS_ID
    }

    /**
     * Checks if the receiver is an inline CoroutineScope creation.
     *
     * Detects patterns like:
     * - `CoroutineScope(Dispatchers.IO).launch { }`
     * - `CoroutineScope(Job() + Dispatchers.Default).async { }`
     *
     * @param receiver The receiver expression
     * @return true if this is an inline CoroutineScope creation
     */
    private fun isInlineCoroutineScopeCreation(receiver: FirExpression): Boolean {
        // If receiver is a function call to CoroutineScope(...), it's inline creation
        if (receiver is FirFunctionCall) {
            val calleeName = receiver.calleeReference.name
            return calleeName == Name.identifier("CoroutineScope")
        }
        return false
    }

    /**
     * Checks if the receiver is a structured scope.
     *
     * A scope is considered structured if:
     * 1. It has the @StructuredScope annotation
     * 2. It's a verified framework scope (viewModelScope, lifecycleScope, etc.) from the correct package
     * 3. It's the result of rememberCoroutineScope() call from Compose
     *
     * **Important:** Framework scopes are validated by their CallableId (package + name),
     * not just by name. This prevents false positives from user-defined properties
     * with the same name.
     *
     * @param receiver The receiver expression
     * @param context The checker context for symbol resolution
     * @return true if the receiver is a structured scope
     */
    private fun isStructuredScope(
        receiver: FirExpression,
        context: CheckerContext
    ): Boolean {
        // Case 1: Check for framework scope functions like rememberCoroutineScope()
        if (receiver is FirFunctionCall) {
            if (isFrameworkScopeFunction(receiver)) {
                return true
            }
        }

        // Case 2: Property access (e.g., viewModelScope.launch)
        if (receiver is FirPropertyAccessExpression) {
            val calleeReference = receiver.calleeReference

            // Try to verify the property is from a known framework package
            if (isFrameworkScopeProperty(receiver)) {
                return true
            }

            // Check if it's a value parameter (function parameter)
            val parameterSymbol = calleeReference.toResolvedValueParameterSymbol()
            if (parameterSymbol != null) {
                return hasStructuredScopeAnnotation(parameterSymbol, context)
            }

            // Check if it's a property (including properties from constructor parameters)
            val propertySymbol = calleeReference.toResolvedPropertySymbol()
            if (propertySymbol != null) {
                // Check annotation on the property itself
                return hasStructuredScopeAnnotation(propertySymbol, context)
            }
        }

        return false
    }

    /**
     * Checks if the function call is to a verified framework scope function.
     * Validates both the name AND the package to ensure it's the real framework function.
     *
     * @param call The function call expression
     * @return true if it's a verified framework scope function
     */
    private fun isFrameworkScopeFunction(call: FirFunctionCall): Boolean {
        val symbol = call.calleeReference.toResolvedCallableSymbol() ?: return false
        val callableId = symbol.callableId ?: return false

        // Check if the callable ID matches any known framework scope function
        if (callableId in FRAMEWORK_SCOPE_FUNCTION_IDS) {
            return true
        }

        // Additional check: verify by package prefix for Compose functions
        val packageName = callableId.packageName.asString()
        val callableName = callableId.callableName

        if (packageName.startsWith("androidx.compose.runtime") && callableName in FRAMEWORK_SCOPE_NAMES) {
            return true
        }

        return false
    }

    /**
     * Checks if the property access is to a verified framework scope property.
     * Validates both the name AND the package to ensure it's the real framework property.
     *
     * @param propertyAccess The property access expression
     * @return true if it's a verified framework scope property
     */
    private fun isFrameworkScopeProperty(propertyAccess: FirPropertyAccessExpression): Boolean {
        val symbol = propertyAccess.calleeReference.toResolvedCallableSymbol() ?: return false
        val callableId = symbol.callableId ?: return false

        // Check if the callable ID matches any known framework scope property
        if (callableId in FRAMEWORK_SCOPE_CALLABLE_IDS) {
            return true
        }

        // Additional check: verify by package prefix for extension properties
        // Framework scopes from androidx.lifecycle are always safe
        val packageName = callableId.packageName.asString()
        val callableName = callableId.callableName

        if (packageName.startsWith("androidx.lifecycle") && callableName in FRAMEWORK_SCOPE_NAMES) {
            return true
        }

        // Compose runtime scopes
        if (packageName.startsWith("androidx.compose.runtime") && callableName in FRAMEWORK_SCOPE_NAMES) {
            return true
        }

        return false
    }

    /**
     * Checks if a value parameter symbol has @StructuredScope annotation.
     *
     * @param symbol The value parameter symbol to check
     * @param context The checker context for session access
     * @return true if the parameter has the annotation
     */
    private fun hasStructuredScopeAnnotation(
        symbol: FirValueParameterSymbol,
        context: CheckerContext
    ): Boolean {
        return symbol.hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, context.session)
    }

    /**
     * Checks if a property symbol has @StructuredScope annotation.
     *
     * @param symbol The property symbol to check
     * @param context The checker context for session access
     * @return true if the property has the annotation
     */
    private fun hasStructuredScopeAnnotation(
        symbol: FirPropertySymbol,
        context: CheckerContext
    ): Boolean {
        return symbol.hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, context.session)
    }
}
