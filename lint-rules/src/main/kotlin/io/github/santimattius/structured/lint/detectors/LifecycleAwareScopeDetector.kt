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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import io.github.santimattius.structured.lint.utils.AndroidLintUtils
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Validates correct usage of lifecycle-aware scopes (lifecycleScope).
 * 
 * Android-specific: Ensures that lifecycleScope is only used in LifecycleOwner 
 * components (Activity, Fragment) and that it's used correctly.
 * 
 * Example:
 * ```kotlin
 * // ✅ GOOD - lifecycleScope in Activity
 * class MainActivity : AppCompatActivity() {
 *     fun load() {
 *         lifecycleScope.launch { fetchData() }
 *     }
 * }
 * 
 * // ✅ GOOD - lifecycleScope in Fragment
 * class MyFragment : Fragment() {
 *     fun load() {
 *         lifecycleScope.launch { fetchData() }
 *     }
 * }
 * 
 * // ❌ BAD - lifecycleScope used outside LifecycleOwner
 * class MyRepository {
 *     fun load(activity: Activity) {
 *         activity.lifecycleScope.launch { }  // Wrong - should use viewModelScope or injected scope
 *     }
 * }
 * 
 * // ❌ BAD - Custom scope in LifecycleOwner
 * class MainActivity : AppCompatActivity() {
 *     private val customScope = CoroutineScope(Dispatchers.Main)
 *     
 *     fun load() {
 *         customScope.launch { }  // Not lifecycle-aware
 *     }
 * }
 * ```
 */
class LifecycleAwareScopeDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "LifecycleAwareScope",
            briefDescription = "Incorrect lifecycle-aware scope usage",
            explanation = """
                [ARCH_002] lifecycleScope should only be used in LifecycleOwner components
                (Activity, Fragment). Using custom scopes in lifecycle-aware components or
                lifecycleScope outside of them can lead to memory leaks.
                Use lifecycleScope in Activity/Fragment; use viewModelScope in ViewModels.

                See: ${LintDocUrl.buildDocLink("82-lifecycle-aware-flow-collection-android")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LifecycleAwareScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("launch", "async")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Check if lifecycleScope is used outside of a LifecycleOwner
        if (isLifecycleScopeUsedOutsideLifecycleOwner(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "lifecycleScope should only be used in LifecycleOwner components (Activity, Fragment). Use viewModelScope for ViewModels or inject a scope for other classes"
            )
        }
        
        // Check if a custom scope is used in a LifecycleOwner instead of lifecycleScope
        if (isCustomScopeInLifecycleOwner(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use lifecycleScope instead of creating a custom CoroutineScope in LifecycleOwner components"
            )
        }
    }
    
    /**
     * Checks if lifecycleScope is used outside of a LifecycleOwner component.
     */
    private fun isLifecycleScopeUsedOutsideLifecycleOwner(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val receiver = call.receiver as? UQualifiedReferenceExpression
        val receiverName = receiver?.receiver?.asSourceString() ?: return false
        
        // Check if this is a lifecycleScope call
        if (receiverName != "lifecycleScope") {
            return false
        }
        
        // Check if we're NOT inside a LifecycleOwner
        return !AndroidLintUtils.isInLifecycleOwner(context, call)
    }
    
    /**
     * Checks if a custom CoroutineScope is being used in a LifecycleOwner.
     */
    private fun isCustomScopeInLifecycleOwner(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        // Check if we're inside a LifecycleOwner
        if (!AndroidLintUtils.isInLifecycleOwner(context, call)) {
            return false
        }
        
        // Check if the receiver is a custom scope (not lifecycleScope or viewModelScope)
        val receiver = call.receiver as? UQualifiedReferenceExpression
        val receiverName = receiver?.receiver?.asSourceString() ?: return false
        
        // If it's lifecycleScope or viewModelScope, it's fine
        if (receiverName == "lifecycleScope" || receiverName == "viewModelScope") {
            return false
        }
        
        // If it's a framework scope function, it's fine
        if (CoroutineLintUtils.isFrameworkScopeCall(call)) {
            return false
        }
        
        // If it's GlobalScope, it's already caught by GlobalScopeUsageDetector
        if (receiverName == "GlobalScope") {
            return false
        }
        
        // Otherwise, it might be a custom scope
        return true
    }
    
    // NOTE (bugfix, dead-dispatch registration): this secondary check previously overrode
    // `SourceCodeScanner.visitClass(context, declaration)` without pairing it with
    // `applicableSuperClasses()` (the only registration mechanism `visitClass` responds to).
    // With neither `applicableSuperClasses()` nor `getApplicableUastTypes()` registered for
    // `UClass`, lint's `UElementVisitor` never dispatched it, so this secondary check never
    // reported a single diagnostic. Fixed by additionally registering `getApplicableUastTypes()`
    // + `createUastHandler()` for `UClass`, the same pairing already used by every other working
    // detector in this module (e.g. LoopWithoutYieldDetector). This is purely additive: the
    // primary check above keeps its own, already-working `getApplicableMethodNames()` /
    // `visitMethodCall()` registration untouched — both mechanisms coexist on the same detector.
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                node.accept(object : AbstractUastVisitor() {
                    override fun visitVariable(variable: UVariable): Boolean {
                        // Check for property initialization with CoroutineScope in LifecycleOwner
                        if (!AndroidLintUtils.isInLifecycleOwner(context, variable)) {
                            return super.visitVariable(variable)
                        }

                        val initializer = variable.uastInitializer as? UCallExpression
                            ?: return super.visitVariable(variable)

                        if (initializer.methodName == "CoroutineScope") {
                            context.report(
                                ISSUE,
                                variable.sourcePsi,
                                context.getLocation(variable as UElement),
                                "Don't create custom CoroutineScope in LifecycleOwner. Use lifecycleScope instead"
                            )
                        }
                        return super.visitVariable(variable)
                    }
                })
            }
        }
}
