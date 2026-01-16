package io.github.santimattius.structured.sample

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

// ============================================================================
// INVALID EXAMPLES - These produce compilation errors (commented out)
// ============================================================================

/*
 * ERROR: Inline CoroutineScope creation is not allowed.
 * Creating a scope inline bypasses structured concurrency.
 *
 * fun inlineScopeCreation() {
 *     CoroutineScope(Dispatchers.IO).launch {
 *         println("This is bad!")
 *     }
 * }
 */

/*
 * ERROR: GlobalScope usage is prohibited.
 * GlobalScope lives forever and can cause resource leaks.
 *
 * fun globalScopeUsage() {
 *     GlobalScope.launch {
 *         println("This is also bad!")
 *     }
 * }
 */

/*
 * ERROR: Unstructured scope without @StructuredScope annotation.
 *
 * fun unstructuredScope(scope: CoroutineScope) {
 *     scope.launch {
 *         println("Unstructured!")
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
