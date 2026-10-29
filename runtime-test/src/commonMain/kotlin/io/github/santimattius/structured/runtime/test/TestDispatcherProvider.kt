package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * [DispatcherProvider] for tests: all four dispatchers are test dispatchers sharing a single
 * [scheduler], so virtual time stays consistent no matter which dispatcher work runs on.
 *
 * [main], [io] and [default] are `StandardTestDispatcher`s (work is queued until the scheduler
 * advances) and [unconfined] is an `UnconfinedTestDispatcher`. With `unconfined = true`, [main],
 * [io] and [default] are unconfined too, so launches run eagerly.
 *
 * Example:
 * ```
 * @Test
 * fun loadsUser() = runTest {
 *     val repo = UserRepository(testDispatcherProvider())
 *     assertEquals(User("1"), repo.load())
 * }
 * ```
 *
 * Rules: DISPATCH_005 (inject dispatchers for testability), TEST_005 (no hardcoded dispatcher in
 * a class). See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param scheduler the scheduler shared by every dispatcher; pass `testScheduler` of a
 *   `runTest` block (or use [testDispatcherProvider]) to advance both together.
 * @param unconfined when true, [main], [io] and [default] run eagerly like [unconfined].
 */
@ExperimentalStructuredCoroutines
@OptIn(ExperimentalCoroutinesApi::class)
public class TestDispatcherProvider(
    public val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    unconfined: Boolean = false,
) : DispatcherProvider {

    private val standard: CoroutineDispatcher =
        if (unconfined) UnconfinedTestDispatcher(scheduler) else StandardTestDispatcher(scheduler)

    override val main: CoroutineDispatcher = standard
    override val io: CoroutineDispatcher = standard
    override val default: CoroutineDispatcher = standard
    override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher(scheduler)
}

/**
 * Creates a [TestDispatcherProvider] on this scope's `testScheduler`, so `runTest { }` and the
 * provider advance virtual time together.
 *
 * Example:
 * ```
 * @Test
 * fun loadsUser() = runTest {
 *     val repo = UserRepository(testDispatcherProvider())
 *     assertEquals(User("1"), repo.load()) // delays inside io skip virtual time
 * }
 * ```
 *
 * Rules: DISPATCH_005, TEST_005. See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the test scope whose `testScheduler` is shared.
 * @param unconfined when true, main/io/default run eagerly.
 * @return a provider bound to this scope's scheduler.
 * @see TestDispatcherProvider
 */
@ExperimentalStructuredCoroutines
public fun TestScope.testDispatcherProvider(unconfined: Boolean = false): DispatcherProvider =
    TestDispatcherProvider(testScheduler, unconfined)
