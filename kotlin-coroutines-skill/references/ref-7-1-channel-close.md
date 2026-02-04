# 7.1 Forgetting to Close Manual Channels

## Bad Practice

Creating a **`Channel`** manually and **never calling `close()`**. Consumers that use **`for (x in channel)`** will **block forever** when the producer stops sending.

## Recommended

- Prefer the **`produce { }`** builder, which **closes the channel automatically** when the coroutine terminates.
- If you manage channels manually, **define clearly** when and where **`close()`** is called (e.g. when producer is done or on error).

## Why

An open channel keeps consumers waiting for the next element. If the producer stops without closing, consumers never complete. `produce` ties the channel’s lifetime to the coroutine, so when the block finishes (or is cancelled), the channel is closed and consumers can finish.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `val ch = Channel<Int>(); launch { repeat(10) { ch.send(it) } }; for (x in ch) { }` (consumer blocks) | Use `produce { repeat(10) { send(it) } }` and iterate, or call `ch.close()` after the last `send`. |
| Manual channel in a function | Document: “Caller must close when done” or use `produce` so closing is automatic. |
