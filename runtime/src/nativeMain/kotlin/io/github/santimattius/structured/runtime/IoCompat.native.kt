package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@ExperimentalStructuredCoroutines
public actual val Dispatchers.ioCompat: CoroutineDispatcher get() = Dispatchers.IO
