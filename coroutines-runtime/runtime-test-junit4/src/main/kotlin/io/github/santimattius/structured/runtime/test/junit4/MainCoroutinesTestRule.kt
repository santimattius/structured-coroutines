package io.github.santimattius.structured.runtime.test.junit4

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.test.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 rule that installs [testDispatcher] as `Dispatchers.Main` before each test and resets it
 * afterwards, whether the test passes or fails. It is the `@get:Rule` counterpart of
 * `runTestWithMain` from `:runtime-test` and performs the same `setMain` / `resetMain` pair.
 *
 * Default dispatcher: a `StandardTestDispatcher`. Work launched on `Dispatchers.Main` is queued
 * until the scheduler advances, so ordering bugs surface; an `UnconfinedTestDispatcher` runs
 * eagerly and can hide them. Pass one explicitly if you want eager execution.
 *
 * Sharing one clock: to make `Dispatchers.Main` and injected dispatchers advance virtual time
 * together, build the rule from the same [TestDispatcherProvider] (or scheduler) the code under
 * test uses, and run the test with that scheduler (for example `runTest(testDispatcher)`).
 *
 * Example:
 * ```
 * class UserViewModelTest {
 *     @get:Rule val mainRule = MainCoroutinesTestRule()
 *
 *     @Test
 *     fun loads() = runTest(mainRule.testDispatcher) {
 *         val vm = UserViewModel() // uses viewModelScope (Main)
 *         vm.load()
 *         advanceUntilIdle()
 *         assertEquals(User("1"), vm.user.value)
 *     }
 * }
 * ```
 *
 * Rules: DISPATCH_005 (inject dispatchers for testability), TEST_005. See
 * `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param testDispatcher the dispatcher installed as `Main`.
 */
@ExperimentalStructuredCoroutines
@OptIn(ExperimentalCoroutinesApi::class)
public class MainCoroutinesTestRule(
    public val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    /** Installs a `StandardTestDispatcher` on [scheduler] as `Main`. */
    public constructor(scheduler: TestCoroutineScheduler) : this(StandardTestDispatcher(scheduler))

    /** Installs a `StandardTestDispatcher` on the [provider]'s scheduler as `Main`. */
    public constructor(provider: TestDispatcherProvider) : this(provider.scheduler)

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
