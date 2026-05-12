package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Demonstrates TEST_004: runBlocking instead of runTest.
 * Used to validate that :detekt-rules report runBlocking in @Test functions.
 *
 * BAD: delay(5000) waits 5 real seconds, making the test slow.
 * GOOD: runTest uses virtual time — delay(5000) executes in microseconds.
 */
class RunBlockingInsteadOfRunTestSampleTest {

    // BAD: delay(5000) waits 5 real seconds
    @Test
    fun badSlowTest() = runBlocking {
        delay(5_000)
        // assertion
    }

    // GOOD: runTest uses virtual time — delay is instant
    @Test
    fun goodFastTest() = runTest {
        delay(5_000) // executes in microseconds
        // assertion
    }
}
