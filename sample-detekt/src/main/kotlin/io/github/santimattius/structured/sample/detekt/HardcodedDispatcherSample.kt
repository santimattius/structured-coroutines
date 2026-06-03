package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HardcodedDispatcherSample {
    suspend fun load() = withContext(Dispatchers.IO) {
        Unit
    }
}
