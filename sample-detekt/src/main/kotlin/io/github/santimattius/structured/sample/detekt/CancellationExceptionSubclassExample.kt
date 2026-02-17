package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CancellationException

/**
 * Intentionally triggers Detekt rule: CancellationExceptionSubclass (CANCEL_002).
 * Used to validate that :detekt-rules report subclasses of CancellationException.
 */
class CustomCancellationException : CancellationException("Custom")
