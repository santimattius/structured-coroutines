# 4.4 Reusing a Cancelled CoroutineScope

## Bad Practice

Calling **`scope.cancel()`** and then trying to **launch more coroutines** in that scope. A cancelled Job doesn’t accept new children, and subsequent launches **fail silently** (or behave unpredictably).

## Recommended

- To **stop children but keep the scope usable**: use **`coroutineContext.job.cancelChildren()`**.
- **Cancel the scope’s Job** only when the scope will **no longer be reused**.

## Why

After `scope.cancel()`, the scope’s Job is in a terminal state. New `launch`/`async` calls on that scope are either ignored or immediately cancelled. If you need to reuse the scope (e.g. for retries or a new flow), cancel only the children, not the scope itself.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `scope.cancel(); scope.launch { newWork() }` | Don’t cancel the scope if you need to launch again; use `scope.coroutineContext.job.cancelChildren()` to stop current work only. |
| One-off scope that should stop all work | `scope.cancel()` is correct; do not launch again on that scope. |
