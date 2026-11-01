package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A class that receives its dispatchers instead of hardcoding them (DISPATCH_005). */
private class Worker(private val dispatchers: DispatcherProvider) {
    suspend fun load(): Int = withContext(dispatchers.io) {
        delay(1_000)
        42
    }

    fun loadIn(scope: CoroutineScope, onDone: (Int) -> Unit) {
        scope.launch(dispatchers.default) {
            delay(500)
            onDone(7)
        }
    }
}

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class DispatcherProviderTest {

    @Test
    fun of_returnsTheSameDispatcherForAllFourProperties() = runTest {
        val dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)

        val provider = DispatcherProvider.of(dispatcher)

        assertSame(dispatcher, provider.main)
        assertSame(dispatcher, provider.io)
        assertSame(dispatcher, provider.default)
        assertSame(dispatcher, provider.unconfined)
    }

    @Test
    fun defaultProvider_defaultAndUnconfinedAreTheDispatchersSingletons() {
        assertSame(Dispatchers.Default, DefaultDispatcherProvider.default)
        assertSame(Dispatchers.Unconfined, DefaultDispatcherProvider.unconfined)
        assertSame(Dispatchers.ioCompat, DefaultDispatcherProvider.io)
    }

    @Test
    fun defaultProvider_isUsableWithoutTouchingMain() {
        val provider: DispatcherProvider = DefaultDispatcherProvider

        // Reading every property except `main` must not throw even where Dispatchers.Main
        // is unavailable (e.g. plain JVM).
        provider.io
        provider.default
        provider.unconfined
    }

    @Test
    fun ioCompat_isUsable() = runTest {
        assertEquals(1, withContext(Dispatchers.ioCompat) { 1 })
    }

    @Test
    fun injectedTestDispatcher_runsWorkWithVirtualTime() = runTest {
        val worker = Worker(DispatcherProvider.of(StandardTestDispatcher(testScheduler)))

        val result = worker.load()

        assertEquals(42, result)
        assertEquals(1_000, currentTime)
    }

    @Test
    fun injectedTestDispatcher_launchedWorkIsControlledByTheScheduler() = runTest {
        val worker = Worker(DispatcherProvider.of(StandardTestDispatcher(testScheduler)))
        var done = 0

        worker.loadIn(this) { done = it }
        assertEquals(0, done)
        advanceUntilIdle()

        assertEquals(7, done)
        assertEquals(500, currentTime)
    }
}
