package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Demonstrates TEST_005: hardcoded [Dispatchers.IO] in a production class. */

class BadRepository {
    suspend fun load() = withContext(Dispatchers.IO) {
        "data"
    }
}
