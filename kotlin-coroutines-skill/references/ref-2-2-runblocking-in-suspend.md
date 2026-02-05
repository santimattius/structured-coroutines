# 2.2 Using runBlocking Inside Suspend Functions

## Bad Practice

Calling **`runBlocking`** from coroutine-based code, especially **inside suspend functions**. This blocks the current thread, breaks the non-blocking model, and can cause deadlocks or ANRs on Android.

## Recommended

- Use **`runBlocking`** only as a **bridge** from purely blocking code to coroutines (e.g. `main()`, console scripts, legacy APIs that are not suspend).
- Inside suspend functions: use **suspend APIs** or wrap blocking operations with **`withContext(Dispatchers.IO)`**.

## Why

`runBlocking` blocks the thread until the block completes. From inside a suspend function you’re often on a limited pool (e.g. Main or Default); blocking can freeze the UI or exhaust threads and lead to deadlocks. Suspend + `withContext(IO)` keeps the model non-blocking and avoids ANRs.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `suspend fun load() { runBlocking { delay(100) } }` | `suspend fun load() { delay(100) }` or `withContext(Dispatchers.IO) { blockingCall() }` |
| In `main()` or test entry: `runBlocking { }` | Keep `runBlocking` only at the boundary; inside use suspend/Flow. |
