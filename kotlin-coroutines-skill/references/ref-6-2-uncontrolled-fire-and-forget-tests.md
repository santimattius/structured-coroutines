# 6.2 Uncontrolled Fire-and-Forget Coroutines in Tests

## Bad Practice

Classes that **launch work in external scopes** (e.g. GlobalScope or an injected “background” scope) **without a way to control them in tests**. You can’t wait for completion or manipulate time, so tests become flaky or slow.

## Recommended

- **Inject `CoroutineScope`** (or a factory) so tests can pass a **`TestScope`** or a scope that shares the **`TestCoroutineScheduler`** with **`runTest`**.
- Use **`backgroundScope`** from **`runTest`** when you need parallel processes under the same virtual time.

## Why

If production code uses a hard-coded or global scope, tests can’t advance virtual time for that work or wait for it to finish. Injecting the scope allows tests to use `runTest` and `TestScope` so all coroutines are driven by the same scheduler and tests stay deterministic and fast.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Class uses `GlobalScope.launch { }` | Inject `CoroutineScope` (e.g. `applicationScope`); in tests inject `runTest { backgroundScope }` or a scope with `StandardTestDispatcher`. |
| Test can’t wait for “background” work | Ensure the scope used for that work is the test scope or shares its scheduler so `advanceUntilIdle()` or `runCurrent()` runs it. |
