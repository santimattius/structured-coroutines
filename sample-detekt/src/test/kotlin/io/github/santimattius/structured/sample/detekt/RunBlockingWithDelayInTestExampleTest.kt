package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Intentionally triggers Detekt rule: RunBlockingWithDelayInTest (TEST_001).
 * Used to validate that :detekt-rules report runBlocking + delay in test files.
 * File name ends with "Test.kt" so the rule applies.
 */
class RunBlockingWithDelayInTestExampleTest {

    @org.junit.jupiter.api.Test
    fun slowTest() = runBlocking {
        delay(100)
    }
}
