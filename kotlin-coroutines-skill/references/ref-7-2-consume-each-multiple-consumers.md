# 7.2 Sharing consumeEach Among Multiple Consumers

## Bad Practice

Using **`consumeEach`** from **multiple coroutines** on the same channel. **`consumeEach`** consumes the channel (it’s a single-consumer API) and cancels/closes the channel when the block finishes, **breaking other consumers**.

## Recommended

- For **fan-out** (multiple consumers): use **`for (value in channel)`** in **each** consumer so they share the channel without one consumer “taking” it exclusively.
- Reserve **`consumeEach`** for **single-consumer** scenarios only.

## Why

`consumeEach` runs a block for each element until the channel is closed; it’s intended for one consumer. Using it from several coroutines leads to concurrent consumption of the same channel, which is not supported for regular channels, and to one consumer closing or cancelling the channel for everyone else.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Two coroutines both calling `channel.consumeEach { }` | Each consumer uses `for (value in channel) { ... }` (or use a `SharedFlow` / `StateFlow` if you need true fan-out / broadcast semantics). |
| Single consumer | `consumeEach` is fine. |
