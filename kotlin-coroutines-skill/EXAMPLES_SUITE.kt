// Package optional: use for copy-paste into agent prompts or integrate into your project.
// import kotlinx.coroutines.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * EXAMPLES_SUITE.kt
 *
 * Three intentional anti-patterns for testing the Kotlin Coroutines Agent Skill.
 * Each example violates best practices; the agent should suggest an optimized version
 * following Structured Concurrency and the skill's strict rules.
 */

// =============================================================================
// EXAMPLE 1: Memory / lifecycle leak — GlobalScope and no tied lifecycle
// =============================================================================

class UserRepositoryErroneous {

    fun syncUser(userId: String) {
        GlobalScope.launch {
            // Simulates network call; outlives any caller (Activity/ViewModel).
            delay(2000)
            saveToCache(userId)
        }
    }

    private suspend fun saveToCache(userId: String) {
        withContext(Dispatchers.IO) {
            // Persist user...
        }
    }
}

// =============================================================================
// EXAMPLE 2: Wrong exception handling — swallowing CancellationException
// =============================================================================

class PaymentServiceErroneous {

    suspend fun processPayment(amount: Double): Result<Unit> = runBlocking {
        try {
            withContext(Dispatchers.Default) {
                validateAmount(amount)
                chargePayment(amount)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // BAD: CancellationException is an Exception; cancellation won't propagate.
            Result.failure(e)
        }
    }

    private fun validateAmount(amount: Double) {}
    private suspend fun chargePayment(amount: Double) { delay(100) }
}

// =============================================================================
// EXAMPLE 3: Wrong Dispatchers — blocking I/O on Default / Main
// =============================================================================

class FileLoaderErroneous(
    private val scope: CoroutineScope
) {

    fun loadConfig(path: String, onResult: (String) -> Unit) {
        scope.launch(Dispatchers.Default) {
            // BAD: File I/O is blocking; Default is for CPU-bound work.
            val content = File(path).readText()
            onResult(content)
        }
    }

    suspend fun loadConfigSuspend(path: String): String {
        // BAD: Blocking read on whatever dispatcher the caller uses (e.g. Main).
        return File(path).readText()
    }
}
