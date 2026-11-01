@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val IO_PROPERTY = "kotlinx.coroutines.io.parallelism"

private inline fun <T> withIoProperty(value: String?, block: () -> T): T {
    val previous = System.getProperty(IO_PROPERTY)
    try {
        if (value == null) System.clearProperty(IO_PROPERTY) else System.setProperty(IO_PROPERTY, value)
        return block()
    } finally {
        if (previous == null) System.clearProperty(IO_PROPERTY) else System.setProperty(IO_PROPERTY, previous)
    }
}

private const val CORE_POOL_PROPERTY = "kotlinx.coroutines.scheduler.core.pool.size"

private inline fun <T> withCorePoolProperty(value: String?, block: () -> T): T {
    val previous = System.getProperty(CORE_POOL_PROPERTY)
    try {
        if (value == null) System.clearProperty(CORE_POOL_PROPERTY) else System.setProperty(CORE_POOL_PROPERTY, value)
        return block()
    } finally {
        if (previous == null) System.clearProperty(CORE_POOL_PROPERTY) else System.setProperty(CORE_POOL_PROPERTY, previous)
    }
}

class ParallelismJvmTest {

    private val cpus = Runtime.getRuntime().availableProcessors()

    @Test
    fun defaultParallelismIsAtLeastTwoAndCpuCount() {
        assertEquals(maxOf(2, cpus), inferredDefaultParallelism())
    }

    @Test
    fun defaultParallelismHonorsCorePoolSizeSystemProperty() {
        withCorePoolProperty("3") { assertEquals(3, inferredDefaultParallelism()) }
        withCorePoolProperty("1") { assertEquals(1, inferredDefaultParallelism()) }
    }

    @Test
    fun defaultParallelismFallsBackOnInvalidOrNonPositiveCorePoolSize() {
        for (bad in listOf("abc", "", "0", "-3", "1.5", " ")) {
            withCorePoolProperty(bad) { assertEquals(maxOf(2, cpus), inferredDefaultParallelism(), "value '$bad'") }
        }
    }

    @Test
    fun ioParallelismHonorsPositiveSystemProperty() {
        withIoProperty("7") { assertEquals(7, inferredIoParallelism()) }
    }

    @Test
    fun ioParallelismFallsBackWithoutProperty() {
        withIoProperty(null) { assertEquals(maxOf(64, cpus), inferredIoParallelism()) }
    }

    @Test
    fun ioParallelismFallsBackOnInvalidOrNonPositiveProperty() {
        for (bad in listOf("abc", "", "0", "-3", "1.5", " ")) {
            withIoProperty(bad) { assertEquals(maxOf(64, cpus), inferredIoParallelism(), "value '$bad'") }
        }
    }

    @Test
    fun providerReportsParallelismOnlyForDefaultAndIoOverDefaultProvider() {
        val traced = DefaultDispatcherProvider.traced(TraceSink.None)
        withIoProperty("12") {
            assertEquals(inferredDefaultParallelism(), traced.parallelism("default"))
            assertEquals(12, traced.parallelism("io"))
        }
        assertNull(traced.parallelism("main"))
        assertNull(traced.parallelism("unconfined"))
        assertNull(traced.parallelism("other"))
    }

    @Test
    fun providerReportsNullOverNonDefaultBase() {
        val traced = DispatcherProvider.of(Dispatchers.Default).traced(TraceSink.None)
        assertNull(traced.parallelism("default"))
        assertNull(traced.parallelism("io"))
        assertNull(traced.parallelism("main"))
    }

    @Test
    fun providerFunctionReferenceWorksWithPoolSampler() {
        val traced = DefaultDispatcherProvider.traced(TraceSink.None)
        val lambda: (String) -> Int? = traced::parallelism
        assertEquals(inferredDefaultParallelism(), lambda("default"))
    }
}
