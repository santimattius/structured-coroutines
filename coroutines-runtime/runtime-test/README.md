# Structured Coroutines Runtime Test

Part of the [runtime family](../README.md) (module map, dependency graph, build and publishing).

Test helpers for [`:runtime`](../runtime/README.md): `TestDispatcherProvider` and
`TestScope.testDispatcherProvider()`, a `DispatcherProvider` whose dispatchers share one test
scheduler (virtual time), plus `RecordingSink` (a `TraceSink` that records events for assertions)
and `TraceSinkContract` (reusable checks every `TraceSink` should pass). It is a separate artifact so production code never pulls
`kotlinx-coroutines-test` transitively.

```kotlin
commonTest.dependencies {
    implementation("io.github.santimattius:structured-coroutines-runtime-test:0.1.0-RC01")
}
```

Like `:runtime`, the API is gated by `@ExperimentalStructuredCoroutines` and versioned on the
independent `0.x` line (`RUNTIME_VERSION`). Installation, opt-in, usage and platform support:
see the [runtime README](../runtime/README.md#testing-with-runtime-test).

## Testing code that uses `Dispatchers.Main`

`runTestWithMain` is `runTest` plus `Dispatchers.setMain` before the block and
`Dispatchers.resetMain` in a `finally` (so it is reset even when the block throws). It works on
every target and with JUnit4, JUnit5 and `kotlin.test`.

```kotlin
@Test
fun loads() = runTestWithMain {
    val vm = UserViewModel(testDispatcherProvider())   // uses viewModelScope (Main)
    vm.load()
    advanceUntilIdle()                                  // advances Main and the provider together
    assertEquals(User("1"), vm.user.value)
}
```

- **Default dispatcher: `StandardTestDispatcher`.** Work launched on `Main` is queued until the
  scheduler advances, so ordering bugs show up. `UnconfinedTestDispatcher` runs eagerly and can hide
  them; pass `runTestWithMain(UnconfinedTestDispatcher()) { ... }` if you want that.
- **One virtual clock.** The default dispatcher uses the `TestScope`'s scheduler, like
  `testDispatcherProvider()`. A dispatcher you pass in also becomes the scope's context, so the scope
  adopts its scheduler.

### JUnit4 rule (`:runtime-test-junit4`)

For projects that prefer `@get:Rule`, the optional `structured-coroutines-runtime-test-junit4`
artifact provides `MainCoroutinesTestRule` (a `TestWatcher`: `setMain` in `starting`, `resetMain` in
`finished`, also on failure). It exposes `testDispatcher` and defaults to a `StandardTestDispatcher`;
overloads take a `TestCoroutineScheduler` or a `TestDispatcherProvider` to reuse its scheduler.

```kotlin
class UserViewModelTest {
    @get:Rule val mainRule = MainCoroutinesTestRule()

    @Test
    fun loads() = runTest(mainRule.testDispatcher) { /* ... */ }
}
```

**Why a separate artifact:** `TestWatcher` is JUnit4 and JVM-only. Putting it in `:runtime-test`
(for example in `jvmMain`) would pull JUnit4 into every JVM consumer, breaking this module's
no-transitive-test-libraries guarantee. See the [`runtime-test-junit4` README](../runtime-test-junit4/README.md).
