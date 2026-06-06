package io.github.santimattius.structured.sample.detekt

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*

/** Demonstrates INTEROP_004: blocking [CompletableFuture.get] inside coroutine code. */

suspend fun badAwaitFuture(future: CompletableFuture<String>): String = future.get()
