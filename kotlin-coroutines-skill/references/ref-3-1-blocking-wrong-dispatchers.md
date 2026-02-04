# 3.1 Mixing Blocking Code with Wrong Dispatchers

## Bad Practice

Performing **blocking I/O** (files, JDBC, synchronous libraries) on **`Dispatchers.Default`** or **`Dispatchers.Main`**. This can freeze the UI or exhaust the thread pool through prolonged blocking.

## Recommended

- **`Dispatchers.Default`**: CPU-bound work.
- **`Dispatchers.Main`** / **`Main.immediate`**: UI updates.
- **`withContext(Dispatchers.IO)`** or a **limited parallelism dispatcher**: blocking I/O.
- Prefer **suspend APIs** over wrapping synchronous APIs where possible.

## Why

Default and Main have a limited number of threads. Blocking them with I/O stalls other coroutines (and on Android, the UI). `Dispatchers.IO` is sized for blocking work and avoids starving the rest of the app.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `launch(Dispatchers.Default) { File(path).readText() }` | `withContext(Dispatchers.IO) { File(path).readText() }` or suspend API |
| `Dispatchers.Main` with file/network blocking | Switch to `withContext(Dispatchers.IO) { }` for the blocking part; use Main only for UI. |
