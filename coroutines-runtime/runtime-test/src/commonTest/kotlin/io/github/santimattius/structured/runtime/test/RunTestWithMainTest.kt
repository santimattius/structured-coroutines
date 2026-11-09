@file:OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunTestWithMainTest {

    @Test
    fun installsTheGivenDispatcherAsMainBeforeTheBlock(): TestResult {
        val dispatcher = StandardTestDispatcher()
        return runTestWithMain(dispatcher) {
            var ran = false
            launch(Dispatchers.Main) { ran = true }
            assertFalse(ran, "Main must be a queueing (standard) test dispatcher")
            dispatcher.scheduler.runCurrent()
            assertTrue(ran)
        }
    }

    @Test
    fun defaultMainIsStandardAndSharesTheTestScopeScheduler() = runTestWithMain {
        val provider = testDispatcherProvider()
        var onMain = 0L
        var onIo = 0L
        launch(Dispatchers.Main) {
            delay(1_000)
            onMain = currentTime
        }
        launch(provider.io) {
            delay(2_000)
            onIo = currentTime
        }
        runCurrent()
        assertEquals(0L, onMain, "default Main is standard: nothing runs eagerly")
        advanceUntilIdle()
        // One virtual clock: both dispatchers advanced by the same scheduler.
        assertEquals(1_000L, onMain)
        assertEquals(2_000L, onIo)
        assertEquals(2_000L, currentTime)
    }
}
