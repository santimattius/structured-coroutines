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

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Detekt rule that detects blocking calls inside coroutines.
 *
 * ## Problem (Best Practice 3.1)
 *
 * Calling blocking methods inside coroutines defeats the purpose of non-blocking
 * concurrency and can lead to thread starvation, especially when using limited
 * dispatchers like Dispatchers.Default or Dispatchers.Main.
 *
 * ```kotlin
 * // ❌ BAD: Blocking calls in coroutines
 * scope.launch {
 *     Thread.sleep(1000)  // Blocks the coroutine thread
 *     val data = inputStream.read()  // Blocking I/O
 *     val result = jdbcStatement.executeQuery()  // Blocking JDBC
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use non-blocking alternatives or wrap blocking calls in withContext(Dispatchers.IO):
 *
 * ```kotlin
 * // ✅ GOOD: Non-blocking alternatives
 * scope.launch {
 *     delay(1000)  // Non-blocking delay
 *     
 *     withContext(Dispatchers.IO) {
 *         // Blocking I/O wrapped properly
 *         val data = inputStream.read()
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   BlockingCallInCoroutine:
 *     active: true
 *     # Exclude common/iOS code (JVM-only rule)
 *     excludes: ['commonMain', 'iosMain']
 * ```
 *
 * ## Detected Methods
 *
 * - Thread.sleep()
 * - InputStream.read(), OutputStream.write()
 * - JDBC: Statement.execute*, Connection.prepareStatement, ResultSet.next
 * - OkHttp: Call.execute() (use enqueue instead)
 * - Retrofit: Call.execute() (use enqueue instead)
 * - BlockingQueue.take/put
 * - CountDownLatch.await
 * - Future.get()
 *
 * @see CoroutineDetektUtils.BLOCKING_METHODS for full list
 */
class BlockingCallInCoroutineRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "BlockingCallInCoroutine",
        severity = Severity.Warning,
        description = "Blocking call detected inside a coroutine. " +
            "This can block the coroutine thread and cause thread starvation. " +
            "Use non-blocking alternatives or wrap in withContext(Dispatchers.IO).",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        // Check if this is a blocking call
        if (!CoroutineDetektUtils.isBlockingCall(expression)) return

        // Check if we're inside a coroutine context
        if (!CoroutineDetektUtils.isInsideCoroutine(expression)) return

        // Report the issue
        val callName = CoroutineDetektUtils.getFullyQualifiedCallName(expression)
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = buildMessage(callName)
            )
        )
    }

    private fun buildMessage(callName: String): String {
        val suggestion = when {
            callName.contains("Thread.sleep") -> 
                "Use delay() instead of Thread.sleep()."
            callName.contains("InputStream") || callName.contains("OutputStream") ||
            callName.contains("Reader") || callName.contains("Writer") ->
                "Wrap blocking I/O in withContext(Dispatchers.IO) { }."
            callName.contains("Statement") || callName.contains("Connection") || 
            callName.contains("ResultSet") ->
                "Use a non-blocking database library (e.g., R2DBC, Exposed) or wrap in withContext(Dispatchers.IO) { }."
            callName.contains("Call.execute") ->
                "Use the asynchronous version (enqueue) or wrap in withContext(Dispatchers.IO) { }."
            callName.contains("Future.get") ->
                "Use suspendCancellableCoroutine to wrap the Future, or use Kotlin's async/await pattern."
            else ->
                "Wrap in withContext(Dispatchers.IO) { } to avoid blocking the coroutine thread."
        }
        
        return "Blocking call '$callName' inside coroutine. $suggestion"
    }
}
