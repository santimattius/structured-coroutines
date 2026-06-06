# Decision Guide — Kotlin Coroutines

Quick-reference guide for making correct decisions when using Kotlin Coroutines. Each section
addresses a common design question with a decision table and the options recommended by structured
concurrency practices.

---

## Table of Contents

1. [`launch` vs `async`](#1-launch-vs-async)
2. [Which scope to use?](#2-which-scope-to-use)
3. [`viewModelScope` vs `lifecycleScope`](#3-viewmodelscope-vs-lifecyclescope)
4. [`runTest` vs `runBlocking` in tests](#4-runtest-vs-runblocking-in-tests)
5. [Which Dispatcher to use?](#5-which-dispatcher-to-use)
6. [Error handling](#6-error-handling)
7. [`cancel()` vs `cancelChildren()`](#7-cancel-vs-cancelchildren)
8. [Loops in suspend functions](#8-loops-in-suspend-functions)
9. [`withTimeout` vs `withTimeoutOrNull`](#9-withtimeout-vs-withtimeoutornull)
10. [Cold vs Hot Flow (StateFlow / SharedFlow)](#10-cold-vs-hot-flow-stateflow--sharedflow)
11. [Kotlin Multiplatform dispatchers](#11-kotlin-multiplatform-dispatchers)
12. [Mutex vs synchronized](#12-mutex-vs-synchronized)
13. [Backend blocking in coroutines](#13-backend-blocking-in-coroutines)
14. [Interop: callbacks, flows, and futures](#14-interop-callbacks-flows-and-futures)

---

## 1. `launch` vs `async`

| Situation | Use | Why |
|-----------|-----|-----|
| Fire-and-forget work inside a scope | `launch` | No result needed; returns `Job`, not `Deferred` |
| Need the result of a coroutine (parallel operations) | `async` + `.await()` | Returns `Deferred<T>`; always call `.await()` |
| Two parallel operations, wait for both | `async` + `async` + `awaitAll()` | Parallelism with a single await point |
| Launching `async` but never calling `.await()` | **Never** | Use `launch` instead; exceptions stored in the `Deferred` are silently lost |

**Golden rule:** if you never call `.await()`, use `launch`. `async` without `await` is an anti-pattern (`SCOPE_002`).

**Exception handling difference (`EXCEPT_003`):** `CoroutineExceptionHandler` catches uncaught exceptions
from `launch` but **not** from `async` — `async` stores the exception in the `Deferred` and only
rethrows it when `.await()` is called. Always await a `Deferred`, or the exception is lost.

```kotlin
// ✅ Correct: parallel work with result
val a = async { fetchUserProfile() }
val b = async { fetchUserPosts() }
val profile = a.await()
val posts    = b.await()

// ✅ Correct: fire-and-forget
launch { sendAnalyticsEvent() }

// ❌ Anti-pattern: async without await — exception lost (SCOPE_002)
async { sendAnalyticsEvent() }
```

---

## 2. Which scope to use?

| Context | Recommended scope | Lifecycle | Notes |
|---------|-------------------|-----------|-------|
| ViewModel | `viewModelScope` | Cancelled in `onCleared()` | Android KTX; no manual creation needed |
| Activity / Fragment / LifecycleOwner | `lifecycleScope` | Cancelled when Lifecycle is `DESTROYED` | Android KTX; use `repeatOnLifecycle` for collection |
| Suspend function that needs child coroutines | `coroutineScope { }` | First failure cancels all siblings | Structured; waits for all children |
| Suspend function with independent children | `supervisorScope { }` | One child failure does not cancel siblings | For parallel tasks with independent failure semantics |
| Tests | `runTest` (`kotlinx-coroutines-test`) | Virtual time; scheduler-controlled | Replaces `runBlocking` + `delay` |
| Entry points only (`main()`, bridge code) | `runBlocking` | Blocks the current thread | **Never** inside suspend functions |
| **Never in production** | ~~`GlobalScope`~~ | No lifecycle → resource leaks | Anti-pattern `SCOPE_001` |

**`coroutineScope` vs `supervisorScope` — awaitAll semantics (`SCOPE_004`):**
Inside `coroutineScope { awaitAll(d1, d2) }`, the first exception cancels all other `Deferred`s
(structured concurrency). Use `supervisorScope { awaitAll(...) }` and handle each exception
individually when you need independent failure semantics.

**Decision tree:**

```
Are you in a ViewModel?
├── Yes → viewModelScope
└── No
    Are you in a Fragment/Activity/LifecycleOwner?
    ├── Yes → lifecycleScope  (+ repeatOnLifecycle if collecting a Flow)
    └── No
        Is this test code?
        ├── Yes → runTest { }
        └── No
            Should a failure in one child cancel the others?
            ├── Yes → coroutineScope { }
            └── No (independent failures) → supervisorScope { }
```

---

## 3. `viewModelScope` vs `lifecycleScope`

| | `viewModelScope` | `lifecycleScope` |
|---|---|---|
| **Artifact** | `androidx.lifecycle:lifecycle-viewmodel-ktx` | `androidx.lifecycle:lifecycle-runtime-ktx` |
| **Where to use** | Classes extending `ViewModel` | `Activity`, `Fragment`, or any `LifecycleOwner` |
| **Lifecycle** | Cancelled in `ViewModel.onCleared()` | Cancelled when the `Lifecycle` reaches `DESTROYED` |
| **Flow collection** | Preferred in ViewModel; expose flows with `stateIn` | Combine with `repeatOnLifecycle(STARTED)` to stop collection in background |
| **Typical use** | Network calls, business logic, repository layer | UI observation, collecting `StateFlow` from Fragment/Activity |

```kotlin
// ✅ ViewModel: data loading with automatic scope management
class OrderViewModel : ViewModel() {
    val orders = flow { emit(repo.getOrders()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ✅ Fragment: lifecycle-aware collection (ARCH_002)
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.orders.collect { renderOrders(it) }
    }
}
```

---

## 4. `runTest` vs `runBlocking` in tests

| | `runTest` | `runBlocking` |
|---|---|---|
| **Artifact** | `kotlinx-coroutines-test` | `kotlinx-coroutines-core` |
| **Virtual time** | ✅ `delay()` resolves instantly | ❌ `delay()` sleeps the real thread |
| **Test speed** | Very fast | Slow when delays are used |
| **TestCoroutineScheduler** | Included; `advanceTimeBy`, `runCurrent`, `advanceUntilIdle` | Not available |
| **Recommended use** | Every suspend function in tests | Only at entry points (`main()`) or non-test bridge code |
| **Anti-pattern** | — | `runBlocking { delay(1_000) }` in tests — slow and unnecessary |

Also replace `Dispatchers.Main` with `StandardTestDispatcher` in tests that use it, using
`Dispatchers.setMain(...)` in setup and `Dispatchers.resetMain()` in teardown (`TEST_003`).

```kotlin
// ✅ Correct: virtual time test
@Test
fun `order is cached after fetch`() = runTest {
    val repo = FakeOrderRepository()
    val vm = OrderViewModel(repo)
    advanceUntilIdle()           // resolves all delays without waiting
    assertEquals(listOf(fakeOrder), vm.orders.value)
}

// ❌ Anti-pattern: blocks the real thread for 1 second (TEST_001)
@Test
fun `order is cached after fetch`() = runBlocking {
    delay(1_000)
    // ...
}
```

**IDE intention available:** "Convert to runTest" replaces `runBlocking { delay(...) }` with
`runTest { advanceTimeBy(...) }` automatically.

---

## 5. Which Dispatcher to use?

| Type of work | Dispatcher | Notes |
|---|---|---|
| I/O operations (network, database, files) | `Dispatchers.IO` | Scalable thread pool for blocking I/O |
| CPU-intensive work (algorithms, parsing, serialization) | `Dispatchers.Default` | Pool limited to CPU cores; do not use for blocking I/O |
| UI updates, main thread interactions | `Dispatchers.Main` | Only for view interactions; **never** blocking code |
| Tests (injected dispatcher) | `StandardTestDispatcher` / `UnconfinedTestDispatcher` | For deterministic virtual-time testing |
| **Avoid in production** | ~~`Dispatchers.Unconfined`~~ | Unpredictable execution context; anti-pattern `DISPATCH_003` |

**Inject dispatchers for testability (`DISPATCH_005`):** Avoid hardcoding `Dispatchers.IO` or
`Dispatchers.Main` inside classes. Inject a `CoroutineDispatcher` parameter with a sensible default
and replace it with `StandardTestDispatcher` in tests.

```kotlin
// ✅ Correct: explicit dispatcher switch for blocking I/O
suspend fun loadOrders(): List<Order> = withContext(Dispatchers.IO) {
    api.fetchOrdersBlocking()
}

// ✅ Better: injected dispatcher for testability (DISPATCH_005)
class OrderRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun loadOrders(): List<Order> = withContext(ioDispatcher) {
        api.fetchOrdersBlocking()
    }
}

// ❌ Anti-pattern: blocking call on Main (DISPATCH_001)
viewModelScope.launch(Dispatchers.Main) {
    val orders = api.fetchOrdersSync()
}
```

---

## 6. Error handling

| Situation | Approach | Why |
|-----------|----------|-----|
| Child failure that must not cancel siblings | `supervisorScope { }` | Children are independent; errors don't automatically propagate up |
| Global uncaught exceptions from `launch` | `CoroutineExceptionHandler` in the scope context | Catches unhandled exceptions before they reach the parent `Job` |
| Uncaught exceptions from `async` | Always call `.await()` | `async` stores the exception in the `Deferred`; it is only thrown on `await()` — never caught by `CoroutineExceptionHandler` |
| `CancellationException` | **Always rethrow** | Has special cancellation semantics; swallowing it breaks the cancellation mechanism |
| Suspend calls in `finally` blocks | `withContext(NonCancellable) { }` | Ensures cleanup runs even when the coroutine is cancelled |
| Subclassing `CancellationException` for domain errors | **Never** (`EXCEPT_002`) | Use `Exception` or `RuntimeException` for business errors |

**`CoroutineExceptionHandler` only works with `launch` (`EXCEPT_003`):**

```kotlin
val handler = CoroutineExceptionHandler { _, throwable ->
    logger.error("Uncaught", throwable)
}
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + handler)

// ✅ handler catches the exception from launch
scope.launch { throw RuntimeException("boom") }

// ❌ handler does NOT catch exceptions from async — only .await() does
scope.async { throw RuntimeException("boom") }   // exception stored in Deferred
```

```kotlin
// ✅ Correct: supervisorScope for independent tasks
supervisorScope {
    val a = async { fetchUser() }
    val b = async { fetchOrders() }
    // if fetchUser fails, b keeps running
}

// ✅ Correct: rethrow CancellationException (CANCEL_003)
try {
    doWork()
} catch (e: Exception) {
    if (e is CancellationException) throw e   // never swallow
    logger.error(e)
}

// ✅ Correct: suspend cleanup in finally (CANCEL_004)
try {
    processFile()
} finally {
    withContext(NonCancellable) {
        closeFile()
    }
}
```

---

## 7. `cancel()` vs `cancelChildren()`

| | `scope.cancel()` | `scope.coroutineContext.job.cancelChildren()` |
|---|---|---|
| **Effect** | Cancels the scope's `Job`; no new children accepted | Cancels current children but keeps the scope active |
| **Reusability** | Scope becomes unusable | Scope can launch new coroutines after cancellation |
| **When to use** | Definitive teardown (e.g. `onCleared`) | When you need to "restart" work without destroying the scope |

```kotlin
// ✅ Correct: reuse the scope by cancelling only its children
scope.coroutineContext.job.cancelChildren()
scope.launch { newWork() }    // works correctly

// ❌ Anti-pattern: reuse scope after cancel (CANCEL_005)
scope.cancel()
scope.launch { newWork() }    // silently ignored
```

**IDE quick fix:** The `ScopeReuseAfterCancel` inspection offers to replace `cancel()` with
`cancelChildren()` automatically.

---

## 8. Loops in suspend functions

Any `for`/`while` loop inside a suspend function without a cooperation point (`delay`, `yield`,
`ensureActive`) blocks cancellation until the loop finishes (`CANCEL_001`).

| Cooperation point | Effect | Use when |
|---|---|---|
| `currentCoroutineContext().ensureActive()` | Throws `CancellationException` if the job is cancelled | Minimum overhead; only checks cancellation |
| `yield()` | Checks cancellation **and** yields the thread to other coroutines | You also want cooperative scheduling |
| `delay(0)` | Suspends and returns control | Similar to `yield`; useful in polling loops |

```kotlin
// ✅ Correct: ensureActive for cancellation support
for (item in hugeList) {
    currentCoroutineContext().ensureActive()
    process(item)
}

// ✅ Correct: yield for cooperative scheduling too
while (true) {
    yield()
    poll()
}

// ❌ Anti-pattern: loop without cooperation point (CANCEL_001)
for (item in hugeList) {
    process(item)   // cannot be cancelled until the loop ends
}
```

**IDE quick fix:** The `LoopWithoutYield` inspection offers to insert `ensureActive()` or `yield()`
at the start of the loop body.

---

## 9. `withTimeout` vs `withTimeoutOrNull`

| | `withTimeout` | `withTimeoutOrNull` |
|---|---|---|
| **On timeout** | Throws `TimeoutCancellationException` | Returns `null` |
| **Propagation** | `TimeoutCancellationException` is a `CancellationException` — if uncaught, it cancels the **parent scope** | `null` return is contained; does not affect the parent scope |
| **Resource cleanup** | Must use `finally` or `withContext(NonCancellable)` for cleanup | Same requirement; timeout can interrupt at any point |
| **Recommended when** | Timeout is truly exceptional (must be handled explicitly) | Timeout is an expected outcome; `null` is a valid result |

**`withTimeout` scope cancellation risk (`CANCEL_006`):**
An uncaught `TimeoutCancellationException` propagates up and cancels the **parent scope**,
potentially cancelling sibling coroutines.

> **Enforced by tooling:** `CANCEL_006` is detected as a warning by the **Detekt** rule
> `WithTimeoutScopeCancellation` and the **IntelliJ** inspection of the same name.
> Both report `withTimeout` calls that are not wrapped in a `try/catch` covering
> `TimeoutCancellationException` (or a parent type). A quick fix is available in the IDE
> to replace `withTimeout` with `withTimeoutOrNull` in one click.

```kotlin
// ❌ Anti-pattern: uncaught TimeoutCancellationException cancels parent scope
scope.launch {
    val result = withTimeout(1_000) { fetchData() }   // if timeout fires, scope is cancelled
}

// ✅ Correct: catch to contain the timeout
scope.launch {
    val result = try {
        withTimeout(1_000) { fetchData() }
    } catch (e: TimeoutCancellationException) {
        null   // timeout handled locally; scope is unaffected
    }
}

// ✅ Preferred: withTimeoutOrNull when null is acceptable
scope.launch {
    val result = withTimeoutOrNull(1_000) { fetchData() }
    if (result == null) { /* handle timeout */ }
}

// ✅ Correct: resource cleanup on timeout (CANCEL_007)
withTimeoutOrNull(1_000) {
    val conn = openConnection()
    try {
        conn.read()
    } finally {
        withContext(NonCancellable) { conn.close() }
    }
}
```

---

## 10. Cold vs Hot Flow (StateFlow / SharedFlow)

| | Cold `Flow` | `StateFlow` | `SharedFlow` |
|---|---|---|---|
| **Execution** | Starts on each `collect` call | Always active; shares one emission stream | Always active; shares one emission stream |
| **Current value** | None (re-executes from start) | Always has a current value (replay = 1) | Configurable replay (default 0) |
| **New collector** | Gets all values from scratch | Gets the latest value immediately | Gets last `replay` values |
| **Typical use** | One-shot data streams, repository calls | UI state in ViewModel | One-shot events, notifications with multiple subscribers |
| **Mutable variant** | — | `MutableStateFlow` | `MutableSharedFlow` |

**Decision tree (`FLOW_002`):**

```
Do you need to share state across multiple collectors?
├── No → cold Flow (each collector gets its own execution)
└── Yes
    Does the data represent current state (has a "last known value")?
    ├── Yes → StateFlow  (replay = 1, always a current value)
    └── No (one-shot events, notifications)
        → SharedFlow with replay = 0 and appropriate onBufferOverflow
```

```kotlin
// ✅ StateFlow for UI state — always has a value; replays to new collectors
class OrderViewModel : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()
}

// ✅ SharedFlow for one-shot events — replay = 0 so late collectors don't get past events
private val _events = MutableSharedFlow<UiEvent>()
val events: SharedFlow<UiEvent> = _events.asSharedFlow()

// ✅ stateIn to convert cold Flow to StateFlow in the ViewModel
val orders: StateFlow<List<Order>> = repo.ordersFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

// ❌ Anti-pattern: cold Flow as shared state — each collector starts a new network call
val orders: Flow<List<Order>> = flow { emit(api.fetchOrders()) }
```

**`collectLatest` note (`FLOW_003`):** Use `collectLatest` only when cancelling in-flight work is
acceptable (e.g. a search that is superseded by the next query). If the work inside the collector
must run to completion, use `collect` instead.

---

## 11. Kotlin Multiplatform dispatchers

| Situation | Use | Why |
|-----------|-----|-----|
| Blocking I/O in `commonMain` | Injected `CoroutineDispatcher` or `expect/actual` | `Dispatchers.IO` is missing on Native/JS (`KMP_001`) |
| Sync bridge on iOS main | Avoid `runBlocking` in `commonMain` | Deadlock / unsupported on JS (`KMP_002`) |
| Shared presenter with `MainScope()` | `onDestroy` / `dispose` calls `scope.cancel()` | Not tied to platform lifecycle (`KMP_003`) |

See [BEST_PRACTICES §11](BEST_PRACTICES_COROUTINES.md#11-kotlin-multiplatform).

---

## 12. Mutex vs synchronized

| Situation | Use | Why |
|-----------|-----|-----|
| Protect shared state in `suspend` code | `Mutex.withLock { }` | Does not block dispatcher threads (`CONCUR_001`) |
| Legacy Java lock from non-suspend API | `synchronized` only outside coroutines | `synchronized` in suspend blocks threads |

---

## 13. Backend blocking in coroutines

| Situation | Use | Why |
|-----------|-----|-----|
| JDBC / `Thread.sleep` in Ktor/Spring handler | `withContext(Dispatchers.IO) { }` | Avoid blocking event loop (`BACKEND_001`) |
| MDC / `ThreadLocal` after dispatcher switch | `withContext(Dispatchers.IO + MDCContext())` | Context does not propagate automatically (`BACKEND_002`) |
| Spring `@Transactional` + `suspend` | R2DBC or blocking bridge documented | JDBC blocks the coroutine thread |

Gradle: `useKtorBackendProfile()` or `useSpringBackendProfile()` presets.

---

## 14. Interop: callbacks, flows, and futures

| Situation | Use | Why |
|-----------|-----|-----|
| Callback → suspend | `suspendCancellableCoroutine` + `invokeOnCancellation` | `suspendCoroutine` ignores cancel (`INTEROP_001`) |
| Listener → Flow | `callbackFlow` + `awaitClose { unregister }` | Required since kotlinx-coroutines 1.6 (`INTEROP_002`) |
| Multi-coroutine emission | `channelFlow` | Wrong tool if you only wrap one callback (`INTEROP_003`) |
| `CompletableFuture` in suspend | `.await()` (jdk8/guava) | `.get()` blocks (`INTEROP_004`) |

---

## References

- [BEST_PRACTICES_COROUTINES.md](BEST_PRACTICES_COROUTINES.md) — Canonical best practices guide with rule codes and tool coverage matrix
- [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md) — Unified suppression IDs (Compiler, Detekt, Lint, IntelliJ)
- [GRADUAL_ADOPTION.md](GRADUAL_ADOPTION.md) — Gradual adoption strategy for existing projects
- [rule-codes.yml](rule-codes.yml) — Machine-readable rule code registry

---

*Living document — updated with each iteration of the plan.*
