import kotlinx.coroutines.*

suspend fun readFromCommon() = withContext(Dispatchers.IO) { 1 }
