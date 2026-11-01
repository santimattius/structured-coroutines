@file:OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.instrumented
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Repository(private val dispatchers: DispatcherProvider) {
    suspend fun load(): String = withContext(dispatchers.io) {
        delay(1_000)
        "data"
    }
}

class TestDispatcherProviderTest {

    @Test
    fun exposesTheSchedulerItWasGiven() {
        val scheduler = TestCoroutineScheduler()
        assertSame(scheduler, TestDispatcherProvider(scheduler).scheduler)
    }

    @Test
    fun allFourDispatchersShareTheScheduler() {
        val provider = TestDispatcherProvider()
        var io = false
        var default = false
        var main = false
        var unconfined = false
        val scope = CoroutineScope(provider.main)
        scope.launch(provider.io) { io = true }
        scope.launch(provider.default) { default = true }
        scope.launch(provider.main) { main = true }
        scope.launch(provider.unconfined) { unconfined = true }

        assertFalse(io || default || main)
        assertTrue(unconfined) // UnconfinedTestDispatcher runs eagerly
        provider.scheduler.advanceUntilIdle()
        assertTrue(io && default && main && unconfined)
    }

    @Test
    fun delayUsesVirtualTime() {
        val provider = TestDispatcherProvider()
        var done = false
        CoroutineScope(provider.default).launch {
            delay(60 * 60 * 1_000L)
            done = true
        }
        provider.scheduler.runCurrent()
        assertFalse(done)
        provider.scheduler.advanceTimeBy(60 * 60 * 1_000L)
        provider.scheduler.runCurrent()
        assertTrue(done)
        assertEquals(60 * 60 * 1_000L, provider.scheduler.currentTime)
    }

    @Test
    fun testScopeExtensionSharesTestScheduler() = runTest {
        val provider = testDispatcherProvider()
        assertSame(testScheduler, (provider as TestDispatcherProvider).scheduler)
    }

    @Test
    fun runTestAdvancesWorkOnProviderDispatchers() = runTest {
        val provider = testDispatcherProvider()
        val result = Repository(provider).load()
        assertEquals("data", result)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun unconfinedFlagRunsLaunchesEagerly() {
        val provider = TestDispatcherProvider(unconfined = true)
        var io = false
        var default = false
        var main = false
        val scope = CoroutineScope(provider.main)
        scope.launch(provider.io) { io = true }
        scope.launch(provider.default) { default = true }
        scope.launch(provider.main) { main = true }
        assertTrue(io && default && main)
    }

    @Test
    fun standardDispatchersDoNotRunEagerlyByDefault() {
        val provider = TestDispatcherProvider()
        var ran = false
        CoroutineScope(provider.io).launch { ran = true }
        assertFalse(ran)
    }

    @Test
    fun testScopeExtensionHonoursUnconfinedFlag() = runTest {
        val provider = testDispatcherProvider(unconfined = true)
        var ran = false
        launch(provider.io) { ran = true }
        assertTrue(ran)
    }

    @Test
    fun instrumentedComposesWithTestProvider() = runTest {
        val instrumented = testDispatcherProvider().instrumented()
        val result = withContext(instrumented.io) {
            delay(1_000)
            "data"
        }
        assertEquals("data", result)
        assertEquals(1_000, testScheduler.currentTime)
        val io = instrumented.stats().single { it.label == "io" }
        assertTrue(io.totalDispatched >= 1)
    }
}
