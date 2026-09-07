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
 * [CancellationExceptionSwallowedDetector] overrode `SourceCodeScanner.visitClass(context,
 * declaration)` without pairing it with `applicableSuperClasses()`, so Lint's `UElementVisitor`
 * never dispatched it — the detector never reported a single diagnostic. Fixed by switching to
 * `getApplicableUastTypes()` + `createUastHandler()` returning a `UElementHandler` overriding
 * `visitClass(node: UClass)`.
 */
class CancellationExceptionSwallowedDetectorTest {

    @Test
    fun `reports catch Exception in suspend function that swallows cancellation`() {
        val code = """
            package test

            class Sample {
                suspend fun risky() {
                    try {
                        work()
                    } catch (e: Exception) {
                        log(e)
                    }
                }

                suspend fun work() {}
                fun log(e: Exception) {}
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(TestFiles.kotlin(code).indented())
            .issues(CancellationExceptionSwallowedDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report when CancellationException is rethrown before the broad catch`() {
        val code = """
            package test

            class CancellationException : Exception()

            class Sample {
                suspend fun risky() {
                    try {
                        work()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(e)
                    }
                }

                suspend fun work() {}
                fun log(e: Exception) {}
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(TestFiles.kotlin(code).indented())
            .issues(CancellationExceptionSwallowedDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
