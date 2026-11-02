@file:OptIn(DelicateTracingApi::class, ExperimentalContextPropagation::class)

package io.github.santimattius.structured.runtime.tracing

import androidx.tracing.Counter
import androidx.tracing.DelicateTracingApi
import androidx.tracing.EventMetadataCloseable
import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.PropagationToken
import androidx.tracing.Tracer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/** One slice opened through [FakeTracer]. */
internal class Begun(val category: String, val name: String, val flowIds: List<Long>?, val threadId: Long)

internal class FakeToken(val flowIds: List<Long>) : PropagationToken {
    override fun contextElementOrNull(): CoroutineContext.Element? = null
}

/** In-memory [Tracer]: records slices, closes and counter values so the sink mapping is observable. */
internal class FakeTracer(
    @Volatile var enabled: Boolean = true,
    @Volatile var throwing: Boolean = false,
) : Tracer() {
    val begun = CopyOnWriteArrayList<Begun>()
    val closed = CopyOnWriteArrayList<String>()
    val counters = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()

    private fun failIfThrowing() {
        if (throwing) throw IllegalStateException("boom")
    }

    override fun tokenForManualPropagation(flowIds: List<Long>): PropagationToken = FakeToken(flowIds)

    override fun tokenFromThreadContext(): PropagationToken = FakeToken(emptyList())

    override suspend fun tokenFromCoroutineContext(): PropagationToken = FakeToken(emptyList())

    override fun beginSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable {
        failIfThrowing()
        begun += Begun(category, name, (token as? FakeToken)?.flowIds, Thread.currentThread().id)
        return EventMetadataCloseable(closeable = AutoCloseable { closed += name })
    }

    override suspend fun beginCoroutineSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable = beginSectionWithMetadata(category, name, token, isRoot)

    override fun isCategoryEnabled(category: String): Boolean {
        failIfThrowing()
        return enabled
    }

    override fun counter(category: String, name: String): Counter {
        failIfThrowing()
        val values = counters.getOrPut(name) { CopyOnWriteArrayList() }
        return object : Counter {
            override fun name(): String = name
            override fun setValue(value: Long) {
                values += value
            }
            override fun setValue(value: Double) {
                values += value.toLong()
            }
        }
    }

    override fun writeInstant(category: String, name: String, token: PropagationToken?): EventMetadataCloseable =
        EventMetadataCloseable()

    fun counterValues(name: String): List<Long> = counters[name]?.toList().orEmpty()
}
