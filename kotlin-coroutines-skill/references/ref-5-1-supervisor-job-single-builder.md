# 5.1 Using SupervisorJob as an Argument in a Single Builder

## Bad Practice

Passing **`SupervisorJob()`** to protect a single coroutine: **`launch(SupervisorJob()) { }`**. The SupervisorJob becomes the parent of that coroutine, but the **real parent** (the scope’s Job) still uses a normal Job and can be cancelled when a sibling fails, so the intent is unclear and behavior is wrong.

## Recommended

- Use **SupervisorJob at the scope level**: **`CoroutineScope(SupervisorJob() + dispatcher + handler)`** so that **children** don’t cancel each other.
- Inside a suspend function, use **`supervisorScope { }`** when you want children to fail independently.

## Why

SupervisorJob is meant to define a scope where child failures don’t cancel the scope or siblings. Passing it into a single `launch` doesn’t create that scope; it only changes the immediate parent of that one coroutine and can break the existing scope hierarchy. Apply supervisor semantics at the scope (or `supervisorScope`) level.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `scope.launch(SupervisorJob()) { }` | Create a scope with `SupervisorJob()` once: `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and use `scope.launch { }`. Or use `supervisorScope { launch { } }` inside a suspend function. |
