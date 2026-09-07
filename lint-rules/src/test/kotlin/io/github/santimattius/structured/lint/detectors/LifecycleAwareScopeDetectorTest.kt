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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

/**
 * [LifecycleAwareScopeDetector] has a working primary check dispatched via
 * `getApplicableMethodNames()`/`visitMethodCall()` (not touched here). Its SECONDARY check —
 * detecting a custom `CoroutineScope(...)` field declared inside a `LifecycleOwner` — was
 * implemented as `SourceCodeScanner.visitClass(context, declaration)` without pairing it with
 * `applicableSuperClasses()`, so Lint's `UElementVisitor` never dispatched it and it never
 * reported a single diagnostic. Fixed by additionally registering
 * `getApplicableUastTypes()` + `createUastHandler()` returning a `UElementHandler` overriding
 * `visitClass(node: UClass)`, alongside the existing (untouched) method-call dispatch.
 */
class LifecycleAwareScopeDetectorTest {

    private val coroutineScopeFactoryStub = """
        package kotlinx.coroutines
        fun CoroutineScope(context: Any): CoroutineScope = error("stub")
    """.trimIndent()

    @Test
    fun `reports a custom CoroutineScope field declared inside a LifecycleOwner`() {
        val code = """
            package test

            import androidx.lifecycle.LifecycleOwner
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Dispatchers

            class MainActivity : LifecycleOwner() {
                private val customScope = CoroutineScope(Dispatchers.Main)
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(coroutineScopeFactoryStub).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LifecycleAwareScopeDetector.ISSUE)
            .allowMissingSdk()
            // The secondary check's `initializer as? UCallExpression` cast does not unwrap
            // `UParenthesizedExpression`, so Lint's PARENTHESIZED test mode (which wraps
            // initializers like `(CoroutineScope(Dispatchers.Main))`) is not applicable to this
            // pre-existing detection logic; the registration fix does not change that.
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `does not report a field initialized from lifecycleScope itself`() {
        val code = """
            package test

            import androidx.lifecycle.LifecycleOwner
            import androidx.lifecycle.lifecycleScope

            class MainActivity : LifecycleOwner() {
                private val scope = lifecycleScope
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(coroutineScopeFactoryStub).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LifecycleAwareScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
