package io.github.santimattius.structured.runtime

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertSame

class IoCompatJvmTest {

    @Test
    fun ioCompat_isDispatchersIoOnJvm() {
        assertSame(Dispatchers.IO, Dispatchers.ioCompat)
    }
}
