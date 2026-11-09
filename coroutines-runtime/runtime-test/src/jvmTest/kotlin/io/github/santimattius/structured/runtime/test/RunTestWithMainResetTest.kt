@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * On the JVM without a UI module, `Dispatchers.Main` throws once the test override is reset, which
 * makes "Main was reset" observable.
 */
class RunTestWithMainResetTest {

    private fun mainIsInstalled(): Boolean =
        try {
            Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext)
            true
        } catch (_: IllegalStateException) {
            false
        }

    @Test
    fun resetsMainAfterSuccessfulBlock() {
        var installedInside = false
        runTestWithMain { installedInside = mainIsInstalled() }
        assertTrue(installedInside)
        assertTrue(!mainIsInstalled())
    }

    @Test
    fun resetsMainWhenTheBlockThrows() {
        var installedInside = false
        assertFailsWith<IllegalArgumentException> {
            runTestWithMain {
                installedInside = mainIsInstalled()
                throw IllegalArgumentException("boom")
            }
        }
        assertTrue(installedInside)
        assertTrue(!mainIsInstalled())
    }
}
