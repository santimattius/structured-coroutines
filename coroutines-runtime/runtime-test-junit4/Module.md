# Module structured-coroutines-runtime-test-junit4

JUnit4 rule for `structured-coroutines-runtime-test`. JVM only. Separate artifact so the base test
module never depends on JUnit4. Shares the `@ExperimentalStructuredCoroutines` opt-in and the
`RUNTIME_VERSION` line of the runtime module.

# Package io.github.santimattius.structured.runtime.test.junit4

`MainCoroutinesTestRule`: sets a test dispatcher as `Dispatchers.Main` before each test and resets it
afterwards, including on failure.
