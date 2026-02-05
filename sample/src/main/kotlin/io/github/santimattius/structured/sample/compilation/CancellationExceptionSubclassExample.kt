package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.CancellationException

/**
 * Compilation ERROR: cancellationExceptionSubclass
 *
 * Extending CancellationException for domain errors is not allowed.
 * Use Exception or RuntimeException for domain errors.
 */
class MyDomainError : CancellationException("Bad: domain error as CancellationException")
