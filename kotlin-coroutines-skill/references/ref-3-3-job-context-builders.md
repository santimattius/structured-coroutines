# 3.3 Passing Job() Directly as Context to Builders

## Bad Practice

Using **`launch(Job()) { }`** or **`withContext(SupervisorJob()) { }`** to control errors. This **breaks the parent-child relationship** and structured concurrency: the new Job becomes an independent parent.

## Recommended

- Let builders use the **Job from the current scope** so the coroutine tree stays intact.
- For supervisor semantics:
  - Inside suspend functions: **`supervisorScope { }`**.
  - At scope level: **`CoroutineScope(SupervisorJob() + dispatcher + handler)`** (do not pass that Job into individual builders).

## Why

Passing a new `Job()` or `SupervisorJob()` into `launch`/`async`/`withContext` makes that job the parent of the new coroutine, not the scope’s job. The scope no longer waits for or cancels this work, and exception behavior becomes inconsistent. Supervisor semantics should be at the scope (or `supervisorScope`) level, not per builder.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `launch(Job()) { }` | `launch { }` (use scope’s job) |
| `launch(SupervisorJob()) { }` | `supervisorScope { launch { } }` or use a scope created with `SupervisorJob()` and then `scope.launch { }`. |
