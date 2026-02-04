# 2.1 Using launch on the Last Line of coroutineScope

## Bad Practice

Ending a suspend function with **`coroutineScope { launch { } }`**. This looks like fire-and-forget, but `coroutineScope` still **waits for all children** to complete, so the function doesn’t return until that `launch` finishes.

## Recommended

- If you **want** the function to wait: run the body directly inside `coroutineScope` (or in the same scope) **without** wrapping in `launch`.
- If you **don’t** want to wait: launch in an **explicit external scope** so it’s clear you’re breaking structured concurrency for that task.

## Why

The pattern `coroutineScope { launch { } }` is misleading: readers may think the function returns immediately, but it doesn’t. Making the choice explicit (wait vs. fire-and-forget in another scope) avoids confusion and wrong assumptions about completion and cancellation.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `suspend fun doWork() { coroutineScope { launch { heavy() } } }` (intent: wait) | `suspend fun doWork() { coroutineScope { heavy() } }` or `launch { heavy() }` inside same scope |
| Same (intent: don’t wait) | `externalScope.launch { heavy() }` and document that it outlives the caller |
