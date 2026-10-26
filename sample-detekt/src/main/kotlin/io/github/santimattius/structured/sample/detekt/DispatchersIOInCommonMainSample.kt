package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Demonstrates KMP_001: Dispatchers.IO in commonMain.
 * Used to validate that :detekt-rules report Dispatchers.IO in shared source sets.
 *
 * NOTE: This rule applies to files under commonMain source sets in KMP modules.
 * This sample is placed under the JVM main source set because sample-detekt uses the
 * kotlin.jvm plugin. In a real KMP project, the violation occurs in commonMain where
 * Dispatchers.IO is not part of the common API (JVM and Native only), so it fails to compile in commonMain.
 *
 * BAD: Dispatchers.IO is unresolved in commonMain
 */

// BAD: Dispatchers.IO — would not compile if this were commonMain
suspend fun badFetchDataKmp(): String = withContext(Dispatchers.IO) {
    "data"
}

// GOOD option A: inject dispatcher (testable and KMP-safe)
class GoodRepository(private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun fetchData(): String = withContext(ioDispatcher) {
        "data"
    }
}

// GOOD option B: expect/actual per platform
// In commonMain:
//   expect val ioDispatcher: CoroutineDispatcher
// In androidMain/jvmMain:
//   actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
// In iosMain:
//   actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
