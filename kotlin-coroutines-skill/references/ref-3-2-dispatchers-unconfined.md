# 3.2 Abusing Dispatchers.Unconfined

## Bad Practice

Using **`Dispatchers.Unconfined`** in production to avoid thread switches. Code runs on whatever thread **resumes** it, making execution unpredictable and potentially running blocking calls on the UI thread.

## Recommended

- Reserve **`Dispatchers.Unconfined`** for **special cases** or legacy testing.
- In production, use an explicit dispatcher: **Default**, **Main**, **IO**, single-thread, or (on JVM) a custom dispatcher or Loom.

## Why

Unconfined doesn’t pin the coroutine to any thread; it resumes on the thread that resumes it. That can be the Main thread, so blocking or CPU-heavy work can cause ANRs or non-deterministic behavior. Explicit dispatchers make thread usage clear and safe.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `launch(Dispatchers.Unconfined) { api.call() }` | `launch(Dispatchers.IO) { api.call() }` or `withContext(Dispatchers.Default) { }` for CPU |
| Unconfined in tests to avoid switching | Prefer `StandardTestDispatcher` / `runTest` with virtual time. |
