# Structured Coroutines Runtime Benchmark

Part of the [runtime family](../README.md). An opt-in JVM harness that measures the per-dispatch
overhead of `TracingDispatcher` (disabled sink, no-op sink, `StatsSink`, `RingBufferSink`) against an
unwrapped dispatcher. It is not published, not part of `build`/`check`/CI, and asserts nothing.

```
./gradlew :runtime-benchmark:tracingBenchmark
# optional: -PbenchRounds=10 -PbenchOps=2000000 -PbenchWarmup=5
```

Indicative numbers: [`runtime` README](../runtime/README.md#overhead).
