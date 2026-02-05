# 6.1 Slow Tests with Real Delays

## Bad Practice

Tests using **`runBlocking`** + real **`delay()`** to simulate network or backoff. This makes tests **slow**, **fragile**, and **machine-dependent**.

## Recommended

- Use **`kotlinx-coroutines-test`**: **`runTest { }`** with **virtual time**, **`advanceTimeBy`**, **`advanceUntilIdle`**, **`runCurrent`**.
- Inject **dispatchers** and replace them with **`StandardTestDispatcher`** (or **`UnconfinedTestDispatcher`** where appropriate) in tests.

## Why

Real delays make tests take seconds or minutes and can flake under load. Virtual time lets the test control “time” so delays complete instantly from the test’s perspective, giving fast, deterministic tests.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `runBlocking { delay(5000); doSomething() }` in test | `runTest { delay(5000); doSomething() }; advanceUntilIdle()` (or `advanceTimeBy(5000)`) so delay is virtual |
| Production code using `Dispatchers.Main`/`IO` without injection | Inject `CoroutineDispatcher` and in tests use `StandardTestDispatcher`; in `runTest` time is virtual. |
