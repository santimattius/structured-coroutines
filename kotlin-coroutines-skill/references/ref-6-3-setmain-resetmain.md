# 6.3 TEST_003 — Replacing Dispatchers.Main in Tests

## Bad Practice

Running tests that use `Dispatchers.Main` without replacing it. Behaviour depends on the real main
thread, which may not exist in CI or unit test environments, causing failures or hangs.

```kotlin
// BAD: ViewModel uses Dispatchers.Main; test will fail or be flaky in CI
class UserViewModel(private val repo: UserRepository) : ViewModel() {
    fun loadUser(id: String) {
        viewModelScope.launch {
            // viewModelScope uses Dispatchers.Main.immediate internally
            val user = repo.getUser(id)
            _state.value = user
        }
    }
}

// Test — no setMain; Dispatchers.Main is unavailable in unit tests
@Test
fun testLoadUser() = runTest {
    viewModel.loadUser("1") // crashes: "Module with Main dispatcher is missing"
}
```

## Recommended

Use `Dispatchers.setMain(StandardTestDispatcher())` (or `UnconfinedTestDispatcher`) before tests
and `Dispatchers.resetMain()` in tearDown so code that uses Main runs deterministically under the
test scheduler.

```kotlin
// GOOD: JUnit 4 rule or manual setup/teardown
class UserViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadUser() = runTest(testDispatcher) {
        viewModel.loadUser("1")
        advanceUntilIdle()
        assertEquals(expectedUser, viewModel.state.value)
    }
}

// GOOD: JUnit 5 extension (kotlinx-coroutines-test provides MainDispatcherRule)
// @get:Rule val mainDispatcherRule = MainDispatcherRule()
```

## Why

Unit tests run on a JVM without an Android Looper, so `Dispatchers.Main` is unavailable by default.
Replacing it with a `TestDispatcher` gives full virtual-time control and deterministic execution,
making assertions on state updated via `Dispatchers.Main` reliable in CI.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Tests using `Dispatchers.Main` without `setMain` | `Dispatchers.setMain(StandardTestDispatcher())` in `@Before`; `resetMain()` in `@After` |
