# Structured Coroutines Runtime Test (JUnit4)

Part of the [runtime family](../README.md). Optional, JVM-only companion of
[`:runtime-test`](../runtime-test/README.md) that ships `MainCoroutinesTestRule`, a JUnit4 rule that
installs a test dispatcher as `Dispatchers.Main` before each test and resets it afterwards (also
when the test fails).

```kotlin
dependencies {
    testImplementation("io.github.santimattius:structured-coroutines-runtime-test-junit4:0.1.0-RC01")
}
```

Works in Android local unit tests (they run on the JVM). Requires `@OptIn(ExperimentalStructuredCoroutines::class)`.

```kotlin
class UserViewModelTest {
    @get:Rule val mainRule = MainCoroutinesTestRule()          // StandardTestDispatcher

    @Test
    fun loads() = runTest(mainRule.testDispatcher) {
        val vm = UserViewModel()                               // uses viewModelScope (Main)
        vm.load()
        advanceUntilIdle()
    }
}

// Share one virtual clock with injected dispatchers:
val provider = TestDispatcherProvider()
@get:Rule val mainRule = MainCoroutinesTestRule(provider)      // or MainCoroutinesTestRule(scheduler)
```

- **Default: `StandardTestDispatcher`.** Work on `Main` is queued until the scheduler advances;
  `UnconfinedTestDispatcher` runs eagerly and can hide ordering bugs. Pass one explicitly if needed.
- **Why a separate artifact:** `TestWatcher` is JUnit4 and JVM-only; shipping it from `:runtime-test`
  would add JUnit4 to every JVM consumer. This module depends on `:runtime-test` and JUnit4 (`api`).
- Prefer `runTestWithMain` from `:runtime-test` if you do not need a rule (multiplatform, any framework).
