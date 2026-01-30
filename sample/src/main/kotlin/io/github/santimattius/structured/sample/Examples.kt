package io.github.santimattius.structured.sample

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// ============================================================================
// INVALID EXAMPLES - These produce compilation errors (commented out)
// ============================================================================

/*
 * RULE 1 - ERROR: Inline CoroutineScope creation is not allowed.
 * Creating a scope inline bypasses structured concurrency.
 *
 * fun inlineScopeCreation() {
 *     CoroutineScope(Dispatchers.IO).launch {
 *         println("This is bad!")
 *     }
 * }
 */

/*
 * RULE 2 - ERROR: GlobalScope usage is prohibited.
 * GlobalScope lives forever and can cause resource leaks.
 *
 * fun globalScopeUsage() {
 *     GlobalScope.launch {
 *         println("This is also bad!")
 *     }
 * }
 */

/*
 * RULE 3 - ERROR: Unstructured scope without @StructuredScope annotation.
 *
 * fun unstructuredScope(scope: CoroutineScope) {
 *     scope.launch {
 *         println("Unstructured!")
 *     }
 * }
 */

/*
 * RULE 4 - ERROR: runBlocking inside suspend functions.
 * (Best Practice 2.2)
 * Blocks the current thread, defeats the purpose of coroutines.
 *
 * suspend fun badRunBlocking() {
 *     runBlocking {
 *         delay(1000)
 *     }
 * }
 */

/*
 * RULE 5 - ERROR: Job()/SupervisorJob() passed directly to builders.
 * (Best Practice 3.3 & 5.1)
 * Breaks parent-child relationship and structured concurrency.
 *
 * fun badJobInLaunch(@StructuredScope scope: CoroutineScope) {
 *     scope.launch(Job()) {           // ERROR!
 *         println("Bad!")
 *     }
 *     scope.launch(SupervisorJob()) { // ERROR!
 *         println("Also bad!")
 *     }
 *     withContext(SupervisorJob()) {  // ERROR!
 *         println("Still bad!")
 *     }
 * }
 */

/*
 * RULE 6 - WARNING: Dispatchers.Unconfined usage.
 * (Best Practice 3.2)
 * Makes execution unpredictable, avoid in production.
 *
 * fun badUnconfined(@StructuredScope scope: CoroutineScope) {
 *     scope.launch(Dispatchers.Unconfined) {  // WARNING!
 *         println("Unpredictable!")
 *     }
 * }
 */

// ============================================================================
// VALID EXAMPLES - These compile successfully
// ============================================================================

/**
 * OK: Parameter annotated with @StructuredScope
 */
fun loadData(@StructuredScope scope: CoroutineScope) {
    // This is valid: scope is annotated with @StructuredScope
    scope.launch {
        println("Loading data...")
    }

    scope.async {
        "Async operation"
    }
}

/**
 * OK: Property annotated with @StructuredScope
 */
class ScopeHolder {
    @StructuredScope
    val managedScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    fun usePropertyScope() {
        // This is valid: managedScope is annotated with @StructuredScope
        managedScope.launch {
            println("Using managed scope")
        }
    }
}

/**
 * OK: Constructor property annotated with @property:StructuredScope
 * Note: Use @property: use-site target to ensure annotation applies to the property
 */
class Service(@property:StructuredScope private val scope: CoroutineScope) {

    fun performAction() {
        // This is valid: scope property is annotated with @StructuredScope
        scope.launch {
            println("Performing action...")
        }
    }
}

/**
 * OK: Use supervisorScope { } instead of SupervisorJob() in builders
 * (Best Practice 5.1 - Correct way)
 */
class SafeService(@property:StructuredScope private val scope: CoroutineScope) {

    suspend fun performMultipleActions() {
        // Correct: use supervisorScope for independent child coroutines
        kotlinx.coroutines.supervisorScope {
            launch {
                println("Action 1 - if this fails, action 2 continues")
            }
            launch {
                println("Action 2 - independent of action 1")
            }
        }
    }
}

/**
 * OK: Use appropriate dispatchers instead of Unconfined
 * (Best Practice 3.2 - Correct way)
 */
fun useCorrectDispatchers(@StructuredScope scope: CoroutineScope) {
    // CPU-bound work
    scope.launch(Dispatchers.Default) {
        println("CPU intensive work")
    }

    // IO operations
    scope.launch(Dispatchers.IO) {
        println("IO operation")
    }
}

/**
 * Example of recommended pattern: inject a structured scope
 */
class Repository(
    @property:StructuredScope
    private val ioScope: CoroutineScope
) {
    fun fetchData() {
        ioScope.launch {
            // Perform IO operation
        }
    }

    suspend fun fetchDataAsync() = ioScope.async {
        // Return data
        "result"
    }
}
