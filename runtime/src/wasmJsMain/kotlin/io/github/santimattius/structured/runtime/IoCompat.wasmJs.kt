package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@ExperimentalStructuredCoroutines
public actual val Dispatchers.ioCompat: CoroutineDispatcher get() = Dispatchers.Default
