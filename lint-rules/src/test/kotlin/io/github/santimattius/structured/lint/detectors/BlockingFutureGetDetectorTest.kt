/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class BlockingFutureGetDetectorTest {

    private val javaConcurrentFuture = """
        package java.util.concurrent
        interface Future<V> {
            fun get(): V
            fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): V
        }
        class CompletableFuture<V> : Future<V> {
            override fun get(): V = error("stub")
            override fun get(timeout: Long, unit: TimeUnit): V = error("stub")
        }
        class TimeUnit
    """.trimIndent()

    @Test
    fun warnsOnFutureGetInsideSuspend() {
        val code =
            """
            package test

            import java.util.concurrent.CompletableFuture
            import kotlinx.coroutines.withContext
            import kotlinx.coroutines.Dispatchers

            suspend fun load(): String {
                val future = CompletableFuture<String>()
                return withContext(Dispatchers.IO) {
                    future.get()
                }
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                TestFiles.kotlin(javaConcurrentFuture).indented(),
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(BlockingFutureGetDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[INTEROP_004]")
    }

    @Test
    fun cleanWhenGetOutsideCoroutine() {
        val code =
            """
            package test

            import java.util.concurrent.CompletableFuture

            fun blocking(): String = CompletableFuture<String>().get()
            """.trimIndent()

        TestLintTask.lint()
            .files(
                TestFiles.kotlin(javaConcurrentFuture).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(BlockingFutureGetDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun cleanWhenSuppressLintPresent() {
        val code =
            """
            package test

            import android.annotation.SuppressLint
            import java.util.concurrent.CompletableFuture
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch

            @SuppressLint("BlockingFutureGet")
            fun run(scope: CoroutineScope) {
                scope.launch {
                    CompletableFuture<String>().get()
                }
            }
            """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                TestFiles.kotlin(javaConcurrentFuture).indented(),
                TestFiles.kotlin(androidAnnotation).indented(),
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(BlockingFutureGetDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectClean()
    }
}
