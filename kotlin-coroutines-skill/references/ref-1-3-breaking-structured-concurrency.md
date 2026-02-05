# 1.3 Breaking Structured Concurrency

## Bad Practice

Inside a suspend function or `coroutineScope`, launching work in an **external scope** without justification (e.g., `backgroundScope.launch { }`). This means work won’t be cancelled with the caller, complicating resource cleanup, testing, and traceability.

## Recommended

- Respect structured concurrency by default.
- Inside suspend functions, create subtasks with **`coroutineScope { }`** + **`async`** / **`launch`**.
- Only use external scopes for truly background processes that **must survive** the current flow (e.g. offline analytics, deferred cache writes), and **document it**.

## Why

When you launch in an external scope from inside a suspend function, the new coroutine is not a child of the current scope. The caller can finish or be cancelled while that work keeps running, leading to leaks and tests that can’t wait for completion. Structured concurrency keeps parent and children tied so cancellation and ordering are predictable.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Inside suspend: `backgroundScope.launch { sync() }` | `coroutineScope { launch { sync() } }` so it’s a child and completes/cancels with the caller |
| Same, when work must outlive caller | Keep external scope but add a comment: “Intentionally outlives caller for offline sync.” |
