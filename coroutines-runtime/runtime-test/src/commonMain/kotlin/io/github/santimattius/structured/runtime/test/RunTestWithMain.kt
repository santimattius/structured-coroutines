package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Like `runTest`, but also installs a test dispatcher as `Dispatchers.Main` for the duration of
 * [block], and always resets it afterwards, including when [block] throws. Use it for code that
 * reads `Dispatchers.Main` directly (for example an Android `viewModelScope`). Works on every
 * target and with JUnit4, JUnit5 and `kotlin.test`.
 *
 * Default dispatcher: when [dispatcher] is `null`, a `StandardTestDispatcher` is created on this
 * scope's `testScheduler`. It is the default on purpose: work launched on `Dispatchers.Main` is
 * queued until the scheduler advances, so ordering bugs surface. An `UnconfinedTestDispatcher`
 * runs eagerly and can hide them; pass one explicitly if you want that behavior.
 *
 * Sharing one clock: the default dispatcher uses the scheduler of the [TestScope], exactly as
 * [testDispatcherProvider] does, so `Dispatchers.Main` and injected dispatchers advance virtual
 * time together. When you pass your own [dispatcher], it is also used as the context of the
 * [TestScope], so the scope adopts its scheduler and the same guarantee holds.
 *
 * Example:
 * ```
 * @Test
 * fun loads() = runTestWithMain {
 *     val vm = UserViewModel(testDispatcherProvider()) // uses viewModelScope (Main)
 *     vm.load()
 *     advanceUntilIdle()
 *     assertEquals(User("1"), vm.user.value)
 * }
 * ```
 *
 * Rules: DISPATCH_005 (inject dispatchers for testability), TEST_005. See
 * `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param dispatcher the dispatcher to install as `Main`, or `null` for a `StandardTestDispatcher`
 *   on this scope's `testScheduler`.
 * @param block the test body.
 * @return the `runTest` result (a promise on JS).
 */
@ExperimentalStructuredCoroutines
@OptIn(ExperimentalCoroutinesApi::class)
public fun runTestWithMain(
    dispatcher: TestDispatcher? = null,
    block: suspend TestScope.() -> Unit,
): TestResult = runTest(context = dispatcher ?: EmptyCoroutineContext) {
    Dispatchers.setMain(dispatcher ?: StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}
