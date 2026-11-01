# Structured Coroutines Runtime Test

Part of the [runtime family](../README.md) (module map, dependency graph, build and publishing).

Test helpers for [`:runtime`](../runtime/README.md): `TestDispatcherProvider` and
`TestScope.testDispatcherProvider()`, a `DispatcherProvider` whose dispatchers share one test
scheduler (virtual time), plus `RecordingSink` (a `TraceSink` that records events for assertions)
and `TraceSinkContract` (reusable checks every `TraceSink` should pass). It is a separate artifact so production code never pulls
`kotlinx-coroutines-test` transitively.

```kotlin
commonTest.dependencies {
    implementation("io.github.santimattius:structured-coroutines-runtime-test:0.1.0")
}
```

Like `:runtime`, the API is gated by `@ExperimentalStructuredCoroutines` and versioned on the
independent `0.x` line (`RUNTIME_VERSION`). Installation, opt-in, usage and platform support:
see the [runtime README](../runtime/README.md#testing-with-runtime-test).
