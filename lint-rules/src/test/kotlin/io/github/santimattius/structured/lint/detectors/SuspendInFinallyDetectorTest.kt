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
import org.junit.Test

/**
 * [SuspendInFinallyDetector] overrode `SourceCodeScanner.visitClass(context, declaration)`
 * without pairing it with `applicableSuperClasses()` (the only registration mechanism that
 * dispatches that specific callback). With no registration at all, Lint's `UElementVisitor`
 * never invoked it, so the detector never reported a single diagnostic. Fixed by switching to
 * `getApplicableUastTypes()` + `createUastHandler()` returning a `UElementHandler` overriding
 * `visitClass(node: UClass)` — the same pattern already used by `LoopWithoutYieldDetector`.
 */
class SuspendInFinallyDetectorTest {

    @Test
    fun `reports unprotected suspend call in finally block`() {
        val code = """
            package test

            class Sample {
                suspend fun cleanup() {
                    try {
                        doWork()
                    } finally {
                        saveToDb()
                    }
                }

                suspend fun doWork() {}
                suspend fun saveToDb() {}
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(TestFiles.kotlin(code).indented())
            .issues(SuspendInFinallyDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report suspend call in finally wrapped in withContext NonCancellable`() {
        val code = """
            package test

            object NonCancellable

            fun withContext(scope: Any, block: () -> Unit) {
                block()
            }

            class Sample {
                suspend fun cleanup() {
                    try {
                        doWork()
                    } finally {
                        withContext(NonCancellable) {
                            saveToDb()
                        }
                    }
                }

                suspend fun doWork() {}
                suspend fun saveToDb() {}
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(TestFiles.kotlin(code).indented())
            .issues(SuspendInFinallyDetector.ISSUE)
            .allowMissingSdk()
            // withContext/NonCancellable here are local stub declarations with a single fixed
            // positional signature (not named parameters) — Lint's REORDER_ARGUMENTS test mode
            // fuzzes named-argument call sites, which is not applicable to this stub-based test.
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }
}
