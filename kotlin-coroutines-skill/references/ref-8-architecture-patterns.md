# 8. Architecture Patterns

## General Recommendations

| Pattern | Description |
|--------|-------------|
| **Favor suspend and Flow APIs** | Prefer suspend functions and Flow over callbacks, `Future`, or Rx. Use `suspendCancellableCoroutine` for well-made, cancellable bridges to callback-based APIs. |
| **Maintain structured concurrency** | Use clear scopes, parent/child hierarchy, `coroutineScope` in domain functions, and `SupervisorJob` only where decoupling failures makes sense. |
| **Separate responsibilities by layer** | **Data:** repositories with encapsulated suspend APIs. **Domain:** orchestration with `coroutineScope` + `async` for controlled concurrency. **Presentation:** launch in UI scopes (e.g. `viewModelScope`, `lifecycleScope`) so work is cancelled with the lifecycle. |
| **Test coroutines properly** | Use virtual time and controlled scopes (`runTest`, `TestDispatcher`, injected scope) instead of real delays and global scopes. |

## Layer summary

- **Data:** Expose suspend/Flow; no raw `GlobalScope` or unstructured launches.
- **Domain:** Use `coroutineScope` and `async`/`launch` for parallelism; avoid launching in external scopes unless documented.
- **Presentation:** Use `viewModelScope` / `lifecycleScope` / `rememberCoroutineScope` so cancellation is automatic when the screen or ViewModel is destroyed.

## Related references

- Scopes and structured concurrency: ref-1-1, ref-1-3
- Dispatchers and blocking: ref-3-1, ref-3-2
- Exceptions and SupervisorJob: ref-4-2, ref-5-1, ref-5-2
- Testing: ref-6-1, ref-6-2
