package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A dispatcher for blocking IO that is usable from common code, where `Dispatchers.IO` does
 * not resolve.
 *
 * Resolves to `Dispatchers.IO` on JVM and Native, and to [Dispatchers.Default] on JS and Wasm
 * (which have no dedicated IO dispatcher).
 *
 * Example:
 * ```
 * suspend fun readConfig() = withContext(Dispatchers.ioCompat) { readFile() }
 * ```
 *
 * Rule: KMP_001 (`Dispatchers.IO` in commonMain). See `docs/BEST_PRACTICES_COROUTINES.md`
 * section 11.1.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * Platform availability (`expect`/`actual`):
 * - JVM, Android and Native targets (Linux, macOS, iOS, watchOS, tvOS, Windows): `Dispatchers.IO`.
 * - JS and Wasm targets (`js`, `wasmJs`, `wasmWasi`): [Dispatchers.Default], because they are
 *   single-threaded and have no dedicated IO dispatcher.
 *
 * @see DefaultDispatcherProvider
 */
@ExperimentalStructuredCoroutines
public expect val Dispatchers.ioCompat: CoroutineDispatcher
