package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*

/** Triggers KMP_003. */
class MainScopeWithoutCancelSample {
    private val scope = MainScope()
}
