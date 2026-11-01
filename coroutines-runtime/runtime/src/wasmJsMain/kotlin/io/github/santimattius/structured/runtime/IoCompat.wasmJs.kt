package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Actual for Wasm (JS): [Dispatchers.Default], as there is no dedicated IO dispatcher. */
@ExperimentalStructuredCoroutines
public actual val Dispatchers.ioCompat: CoroutineDispatcher get() = Dispatchers.Default
