# Module structured-coroutines-runtime

Optional Kotlin Multiplatform helpers that are the compliant replacement for the patterns flagged
by the Structured Coroutines compiler plugin, Detekt rules and Android Lint rules. Instead of only
reporting what not to write, the module ships the safe version.

Every declaration is gated by [ExperimentalStructuredCoroutines][io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines]
(a warning-level opt-in) while the module is on its `0.x` line, so the API may change without notice.

Targets: JVM, Android, JS (browser, Node.js), Wasm (JS, WASI) and Native (macOS, iOS, watchOS,
tvOS, Linux, Windows). APIs that exist only on some targets say so in their documentation.

Main entry points:
- Cancellation: `runCatchingCancellable`, `withCleanup`, `withTimeoutResult`.
- Concurrency: `parallel`, `supervisedAll`, `concurrentOrdered`, `hedged`.
- Flow and state: `launchInSafely`, `stateInWhileSubscribed`, `warmIn`, `safeCallbackFlow`,
  `StateHolder`, `EventChannel`.
- Dispatchers: `DispatcherProvider`, `DefaultDispatcherProvider`, `ioCompat`.
- Diagnostics and tracing: `TracingDispatcher`, `traced`, `TraceSink` implementations
  (`StatsSink`, `RingBufferSink`, `LoggingSink`, `CompositeSink`, `AndroidTraceSink`),
  `PoolSampler` and `PoolSignal`.

# Package io.github.santimattius.structured.runtime

Public API of the runtime toolkit.

Helpers that run inside an existing coroutine respect structured concurrency: they never launch
into a scope the caller does not own, they let `CancellationException` propagate, and anything they
start finishes before they return. Diagnostic types (tracing, metrics, signals) are opt-in and have
no effect unless they are wired in explicitly.
