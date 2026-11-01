@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.benchmark

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.RingBufferSink
import io.github.santimattius.structured.runtime.StatsSink
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import io.github.santimattius.structured.runtime.TracingDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Runnable
import java.lang.management.ManagementFactory
import kotlin.coroutines.CoroutineContext

/**
 * Plain JVM harness measuring the cost `TracingDispatcher.dispatch` adds on top of a delegate.
 *
 * The delegate runs the block inline, so the numbers isolate the wrapper itself (no thread hop, no
 * queueing). Variants run round-robin after a shared warm-up so JIT and GC drift hit all of them
 * alike. It reports a documented number only: nothing here asserts on timing.
 *
 * Run: `./gradlew :runtime-benchmark:tracingBenchmark`.
 */
private class InlineDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

/** Enabled for [maxLevel] and below, keeps a reference to the last event so it cannot be elided. */
private class NoOpSink(private val maxLevel: TraceLevel) : TraceSink {
    @Volatile
    var last: TraceEvent? = null
    override fun isEnabled(level: TraceLevel): Boolean = level <= maxLevel
    override fun emit(event: TraceEvent) {
        last = event
    }
}

private class Variant(val name: String, val dispatcher: CoroutineDispatcher)

private class Result(val nsPerOp: DoubleArray, val bytesPerOp: DoubleArray)

private fun median(values: DoubleArray): Double = values.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

private val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

// Volatile so the JIT cannot fold the loop away; adds the same small constant to every variant.
@Volatile
private var blackhole = 0L

private val task = Runnable { blackhole++ }

private fun runOps(variant: Variant, ops: Int, context: CoroutineContext) {
    val dispatcher = variant.dispatcher
    for (i in 0 until ops) dispatcher.dispatch(context, task)
}

fun main() {
    val rounds = System.getProperty("benchRounds")?.toInt() ?: 10
    val ops = System.getProperty("benchOps")?.toInt() ?: 2_000_000
    val warmup = System.getProperty("benchWarmup")?.toInt() ?: 5
    val delegate = InlineDispatcher()
    val context: CoroutineContext = CoroutineName("bench")
    val threadId = Thread.currentThread().id

    val variants = listOf(
        Variant("baseline (unwrapped)", delegate),
        Variant("disabled (TraceSink.None)", TracingDispatcher(delegate, "bench", TraceSink.None)),
        Variant("no-op sink, Dispatch", TracingDispatcher(delegate, "bench", NoOpSink(TraceLevel.Dispatch))),
        Variant("no-op sink, Coroutine", TracingDispatcher(delegate, "bench", NoOpSink(TraceLevel.Coroutine))),
        Variant("RingBufferSink(1024), Dispatch", TracingDispatcher(delegate, "bench", RingBufferSink(1024, TraceLevel.Dispatch))),
        Variant("RingBufferSink(1024), Coroutine", TracingDispatcher(delegate, "bench", RingBufferSink(1024, TraceLevel.Coroutine))),
        Variant("StatsSink, Dispatch", TracingDispatcher(delegate, "bench", StatsSink(TraceLevel.Dispatch))),
        Variant("StatsSink, Coroutine", TracingDispatcher(delegate, "bench", StatsSink(TraceLevel.Coroutine))),
    )

    println("TracingDispatcher.dispatch overhead (inline delegate, single thread)")
    println("jvm=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} " +
        "os=${System.getProperty("os.name")} ${System.getProperty("os.arch")} cpus=${Runtime.getRuntime().availableProcessors()}")
    println("warmup=$warmup rounds, measured=$rounds rounds, ops/round=$ops")

    repeat(warmup) { variants.forEach { runOps(it, ops, context) } }

    val results = variants.associateWith { Result(DoubleArray(rounds), DoubleArray(rounds)) }
    for (round in 0 until rounds) {
        for (variant in variants) {
            val bytesBefore = threadBean.getThreadAllocatedBytes(threadId)
            val start = System.nanoTime()
            runOps(variant, ops, context)
            val elapsed = System.nanoTime() - start
            val bytes = threadBean.getThreadAllocatedBytes(threadId) - bytesBefore
            results.getValue(variant).nsPerOp[round] = elapsed.toDouble() / ops
            results.getValue(variant).bytesPerOp[round] = bytes.toDouble() / ops
        }
    }

    println()
    println("%-34s %10s %10s %10s %12s".format("variant", "median", "min", "max", "bytes/op"))
    println("%-34s %10s %10s %10s %12s".format("", "ns/op", "ns/op", "ns/op", "(median)"))
    for (variant in variants) {
        val r = results.getValue(variant)
        println(
            "%-34s %10.2f %10.2f %10.2f %12.1f".format(
                variant.name, median(r.nsPerOp), r.nsPerOp.min(), r.nsPerOp.max(), median(r.bytesPerOp),
            ),
        )
    }
    println()
    println("(blackhole=$blackhole)")
}
