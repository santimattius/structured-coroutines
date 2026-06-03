package io.github.santimattius.structured.sample.detekt

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BlockingFutureGetSample(private val scope: CoroutineScope) {
    fun start() {
        scope.launch(Dispatchers.Default) {
            val future = CompletableFuture<String>()
            future.get()
        }
    }
}
