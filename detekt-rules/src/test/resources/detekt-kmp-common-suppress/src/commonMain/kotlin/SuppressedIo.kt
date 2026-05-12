import kotlinx.coroutines.*

@Suppress("DispatchersIOInCommonMain")
suspend fun suppressedRead() = withContext(Dispatchers.IO) { 1 }
