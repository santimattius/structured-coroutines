package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Actual for JVM and Android: `Dispatchers.IO`. */
@ExperimentalStructuredCoroutines
public actual val Dispatchers.ioCompat: CoroutineDispatcher get() = Dispatchers.IO
