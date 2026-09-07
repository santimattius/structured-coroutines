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
import org.junit.Test

/**
 * [CancellationExceptionSubclassDetector] overrode `SourceCodeScanner.visitClass(context,
 * declaration)` without pairing it with `applicableSuperClasses()`, so Lint's `UElementVisitor`
 * never dispatched it — the detector never reported a single diagnostic. Fixed by switching to
 * `getApplicableUastTypes()` + `createUastHandler()` returning a `UElementHandler` overriding
 * `visitClass(node: UClass)`.
 */
class CancellationExceptionSubclassDetectorTest {

    private val cancellationExceptionStub = """
        package kotlinx.coroutines
        open class CancellationException(message: String? = null) : Exception(message)
    """.trimIndent()

    @Test
    fun `reports a class extending kotlinx coroutines CancellationException`() {
        val code = """
            package test

            import kotlinx.coroutines.CancellationException

            class UserNotFoundException(message: String) : CancellationException(message)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                TestFiles.kotlin(cancellationExceptionStub).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(CancellationExceptionSubclassDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `does not report a class extending plain Exception`() {
        val code = """
            package test

            class UserNotFoundException(message: String) : Exception(message)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                TestFiles.kotlin(cancellationExceptionStub).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(CancellationExceptionSubclassDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
