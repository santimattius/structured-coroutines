@file:OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)

package io.github.santimattius.structured.runtime.test.junit4

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.test.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainCoroutinesTestRuleTest {

    private fun mainIsInstalled(): Boolean =
        try {
            Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext)
            true
        } catch (_: IllegalStateException) {
            false
        }

    private fun MainCoroutinesTestRule.run(body: () -> Unit) {
        apply(
            object : Statement() {
                override fun evaluate() = body()
            },
            Description.EMPTY,
        ).evaluate()
    }

    @Test
    fun installsMainBeforeTheTestAndResetsItAfter() {
        val rule = MainCoroutinesTestRule()
        var installedInside = false
        rule.run { installedInside = mainIsInstalled() }
        assertTrue(installedInside)
        assertTrue(!mainIsInstalled())
    }

    @Test
    fun resetsMainWhenTheTestFails() {
        val rule = MainCoroutinesTestRule()
        var installedInside = false
        assertFailsWith<IllegalArgumentException> {
            rule.run {
                installedInside = mainIsInstalled()
                throw IllegalArgumentException("boom")
            }
        }
        assertTrue(installedInside)
        assertTrue(!mainIsInstalled())
    }

    @Test
    fun defaultDispatcherIsStandardNotUnconfined() {
        val rule = MainCoroutinesTestRule()
        var ran = false
        rule.run {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { ran = true }
            assertTrue(!ran, "work on Main is queued until the scheduler advances")
            rule.testDispatcher.scheduler.runCurrent()
            assertTrue(ran)
        }
    }

    @Test
    fun usesTheGivenDispatcher() {
        val dispatcher = UnconfinedTestDispatcher()
        val rule = MainCoroutinesTestRule(dispatcher)
        assertSame(dispatcher, rule.testDispatcher)
    }

    @Test
    fun providerAndSchedulerOverloadsReuseTheScheduler() {
        val provider = TestDispatcherProvider()
        assertSame(provider.scheduler, MainCoroutinesTestRule(provider).testDispatcher.scheduler)
        val scheduler = TestCoroutineScheduler()
        assertSame(scheduler, MainCoroutinesTestRule(scheduler).testDispatcher.scheduler)
    }

    @Test
    fun mainAndProviderShareOneClockThroughTheRule() {
        val provider = TestDispatcherProvider()
        val rule = MainCoroutinesTestRule(provider)
        rule.run {
            var onMain = 0L
            var onIo = 0L
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
            scope.launch { delay(1_000); onMain = provider.scheduler.currentTime }
            kotlinx.coroutines.CoroutineScope(provider.io).launch {
                delay(2_000); onIo = provider.scheduler.currentTime
            }
            provider.scheduler.advanceUntilIdle()
            assertEquals(1_000L, onMain)
            assertEquals(2_000L, onIo)
        }
    }
}
