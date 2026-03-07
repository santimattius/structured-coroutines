# 9.1 FLOW_001 — Blocking Code in flow { } Builder

## Bad Practice

Performing blocking calls (e.g. `Thread.sleep`, synchronous file I/O, JDBC queries) inside
`flow { }`. The block runs in the collector's context; blocking can freeze the wrong thread (e.g.
Main) and flows without suspension points cooperate poorly with cancellation.

```kotlin
// BAD: blocks the collector's thread; freezes UI if collected on Main
fun loadItems(): Flow<Item> = flow {
    val items = database.queryAllItems() // blocking JDBC call
    Thread.sleep(1_000)                  // explicit blocking; never suspend
    items.forEach { emit(it) }
}
```

## Recommended

Keep the flow builder non-blocking. Use `flowOn(Dispatchers.IO)` to move emission to a different
context, or use suspend APIs inside the builder.

```kotlin
// GOOD: flowOn moves the blocking emission to IO; collector thread unaffected
fun loadItems(): Flow<Item> = flow {
    val items = database.queryAllItems() // still blocking, but runs on IO
    items.forEach { emit(it) }
}.flowOn(Dispatchers.IO)

// GOOD alternative: use suspend API directly
fun loadItems(): Flow<Item> = flow {
    val items = suspendingDatabase.getItems() // truly non-blocking suspend call
    items.forEach { emit(it) }
}
```

## Why

`flow { }` runs in the collector's coroutine context by default. If the collector is on
`Dispatchers.Main`, any blocking call inside the builder will freeze the UI thread.
`flowOn(Dispatchers.IO)` switches the upstream context (the builder side), keeping the downstream
(collector side) on its original dispatcher. Additionally, flows with blocking calls have no
suspension points, so they respond poorly to cancellation.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `flow { blockingOp() }` | `flow { blockingOp() }.flowOn(Dispatchers.IO)` or use suspend API inside builder |
