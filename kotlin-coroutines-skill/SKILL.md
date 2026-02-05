# Kotlin Coroutines Agent Skill — Playbook

Use this playbook to route to the right reference. For any Kotlin Coroutines question or code review, **identify the topic below** and **open the linked reference file** in `references/` for the detailed rule, bad/good practice, and quick fix.

## Triage: Topic → Reference

| Topic / Error / Question | Reference file |
|--------------------------|----------------|
| **GlobalScope**, scope lifetime, “where should I launch?” | [ref-1-1-global-scope.md](references/ref-1-1-global-scope.md) |
| **async without await**, fire-and-forget with async | [ref-1-2-async-without-await.md](references/ref-1-2-async-without-await.md) |
| **Breaking structured concurrency**, launching in external scope from suspend | [ref-1-3-breaking-structured-concurrency.md](references/ref-1-3-breaking-structured-concurrency.md) |
| **coroutineScope { launch { } }** as last line, “wait vs don’t wait” | [ref-2-1-launch-last-line-coroutine-scope.md](references/ref-2-1-launch-last-line-coroutine-scope.md) |
| **runBlocking** inside suspend, blocking in coroutines | [ref-2-2-runblocking-in-suspend.md](references/ref-2-2-runblocking-in-suspend.md) |
| **Blocking I/O on Default/Main**, wrong Dispatchers for I/O | [ref-3-1-blocking-wrong-dispatchers.md](references/ref-3-1-blocking-wrong-dispatchers.md) |
| **Dispatchers.Unconfined** in production | [ref-3-2-dispatchers-unconfined.md](references/ref-3-2-dispatchers-unconfined.md) |
| **Job() / SupervisorJob()** passed to launch/async/withContext | [ref-3-3-job-context-builders.md](references/ref-3-3-job-context-builders.md) |
| **Cancellation in loops**, long loops not responding to cancel | [ref-4-1-cancellation-intensive-loops.md](references/ref-4-1-cancellation-intensive-loops.md) |
| **Swallowing CancellationException**, catch Exception and cancel | [ref-4-2-swallowing-cancellation-exception.md](references/ref-4-2-swallowing-cancellation-exception.md) |
| **Suspend in finally**, cleanup that needs to suspend | [ref-4-3-suspend-cleanup-noncancellable.md](references/ref-4-3-suspend-cleanup-noncancellable.md) |
| **Reusing scope after cancel()**, cancelChildren vs cancel | [ref-4-4-reusing-cancelled-scope.md](references/ref-4-4-reusing-cancelled-scope.md) |
| **SupervisorJob() in a single builder** | [ref-5-1-supervisor-job-single-builder.md](references/ref-5-1-supervisor-job-single-builder.md) |
| **CancellationException for domain errors** (e.g. UserNotFound) | [ref-5-2-cancellation-exception-domain-errors.md](references/ref-5-2-cancellation-exception-domain-errors.md) |
| **Slow tests**, real delay() in tests | [ref-6-1-slow-tests-real-delays.md](references/ref-6-1-slow-tests-real-delays.md) |
| **Uncontrolled fire-and-forget in tests**, can’t wait in tests | [ref-6-2-uncontrolled-fire-and-forget-tests.md](references/ref-6-2-uncontrolled-fire-and-forget-tests.md) |
| **Channel not closed**, manual Channel without close() | [ref-7-1-channel-close.md](references/ref-7-1-channel-close.md) |
| **consumeEach with multiple consumers** | [ref-7-2-consume-each-multiple-consumers.md](references/ref-7-2-consume-each-multiple-consumers.md) |
| **Architecture**, layers (Data/Domain/Presentation), suspend vs callbacks | [ref-8-architecture-patterns.md](references/ref-8-architecture-patterns.md) |

## Agent behavior

1. **Identify** the practice or error from the user’s code or question (e.g. GlobalScope, runBlocking in suspend, swallowing CancellationException).
2. **Open** the corresponding reference from the table above (in `references/`).
3. **Apply** the strict rules from **SYSTEM_PROMPT.md** and the **bad / recommended / quick fix** from the reference.
4. **Respond** in the required format: Analysis → Erroneous code → Optimized code → Explanation.

If several practices apply (e.g. GlobalScope + wrong Dispatchers), use each relevant reference and combine the fixes in one optimized snippet.
