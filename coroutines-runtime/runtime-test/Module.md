# Module structured-coroutines-runtime-test

Test doubles and contract checks for `structured-coroutines-runtime`. It is a separate artifact so
production code never receives `kotlinx-coroutines-test` transitively. Targets: JVM, JS, Wasm and
Native (macOS, iOS, watchOS, tvOS, Linux, Windows). It shares the `@ExperimentalStructuredCoroutines`
opt-in and the `RUNTIME_VERSION` line of the runtime module.

# Package io.github.santimattius.structured.runtime.test

A virtual-time `DispatcherProvider` (`TestDispatcherProvider`, `testDispatcherProvider`), a
recording `TraceSink` (`RecordingSink`) and reusable contract checks for custom sinks
(`TraceSinkContract`).
