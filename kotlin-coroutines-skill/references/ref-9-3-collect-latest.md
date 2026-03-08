# 9.3 FLOW_003 — collectLatest Cancels Previous Work

## Bad Practice

Using `collectLatest { }` when the work inside the collector must run to completion. Each new
emission cancels the previous block; only one "instance" of the collector runs at a time.

```kotlin
// BAD: each new search query cancels the ongoing save operation
viewModelScope.launch {
    searchQuery.collectLatest { query ->
        val results = search(query)   // may be cancelled mid-way by next query
        saveToHistory(results)        // often never completes; data is lost
    }
}
```

## Recommended

Use `collectLatest` only when cancelling in-flight work is acceptable and desirable (e.g. search
results replaced by the next query). For work that must complete, use `collect` or handle
concurrency explicitly.

```kotlin
// GOOD: collectLatest for UI search — old result not needed when new query arrives
viewModelScope.launch {
    searchQuery.collectLatest { query ->
        _results.value = search(query) // cancelled and restarted on new query; fine
    }
}

// GOOD: collect when each item must be fully processed
viewModelScope.launch {
    itemStream.collect { item ->
        processAndSave(item) // must complete; use collect, not collectLatest
    }
}
```

## Why

`collectLatest` is intentionally designed to cancel the previous block when a new value arrives.
This is correct for search-style UX (show only the latest result), but wrong when each item must be
processed to completion (e.g. analytics events, writes to disk, state machines). Misusing
`collectLatest` silently drops work in progress, leading to lost data or incomplete state
transitions that are difficult to reproduce.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `collectLatest { mustComplete(it) }` | `collect { mustComplete(it) }` when work must complete |
