package io.github.santimattius.structured.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class StateHolderTest {

    @Test
    fun initialValue_isExposedByValueAndState() {
        val holder = StateHolder(5)

        assertEquals(5, holder.value)
        assertEquals(5, holder.state.value)
    }

    @Test
    fun set_replacesValue() {
        val holder = StateHolder("a")

        holder.set("b")

        assertEquals("b", holder.value)
        assertEquals("b", holder.state.value)
    }

    @Test
    fun update_appliesTransformToCurrentValue() {
        val holder = StateHolder(1)

        holder.update { it + 41 }

        assertEquals(42, holder.value)
    }

    @Test
    fun update_isAtomicUnderConcurrentUpdates() = runTest {
        val holder = StateHolder(0)
        val coroutines = 100
        val perCoroutine = 100

        repeat(coroutines) {
            launch {
                repeat(perCoroutine) {
                    holder.update { it + 1 }
                    yield()
                }
            }
        }
        advanceUntilIdle()

        assertEquals(coroutines * perCoroutine, holder.value)
    }

    @Test
    fun state_isNotCastableToMutableStateFlow() {
        val holder = StateHolder(0)

        assertTrue(holder.state !is MutableStateFlow<*>)
    }

    @Test
    fun state_conflatesIntermediateUpdates() = runTest {
        val holder = StateHolder(0)
        val seen = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            holder.state.collect { seen += it }
        }

        // Unconfined collector sees the initial value immediately.
        assertEquals(listOf(0), seen)
        holder.set(1)
        holder.set(2)

        assertEquals(2, holder.value)
        assertEquals(2, seen.last())
        job.cancel()
    }

    @Test
    fun state_conflates_whenCollectorIsSlow() = runTest {
        val holder = StateHolder(0)
        val seen = mutableListOf<Int>()
        // Standard dispatcher: collector doesn't run until the scheduler advances.
        val job = launch { holder.state.collect { seen += it } }

        holder.set(1)
        holder.set(2)
        holder.set(3)
        advanceUntilIdle()

        assertEquals(listOf(3), seen)
        job.cancel()
    }
}
