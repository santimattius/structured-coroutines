# 1.1 Using GlobalScope in Production Code

## Bad Practice

Launching coroutines in `GlobalScope` (e.g., `GlobalScope.launch { }` or `GlobalScope.async { }`). This breaks the coroutine tree, preventing proper cancellation and exception propagation, making it difficult to reason about job lifetimes.

## Recommended

Always use a meaningful `CoroutineScope`:

- **Framework scopes:** `viewModelScope`, `lifecycleScope`, `rememberCoroutineScope`
- **Injected scopes:** `applicationScope`, `backgroundScope`
- **Local scopes in suspend functions:** `coroutineScope { }`, `withContext { }`

Only launch independent processes in an external scope when absolutely necessary, and document it clearly.

## Why

`GlobalScope` is not bound to any parent. Work launched there is not cancelled when the caller (e.g. ViewModel, Activity) is destroyed, leading to leaks and wasted work. Structured concurrency requires a clear parent-child relationship so cancellation and exceptions propagate correctly.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `GlobalScope.launch { fetchData() }` | `viewModelScope.launch { fetchData() }` (Android) or inject `CoroutineScope` and use `scope.launch { }` |
