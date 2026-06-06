package io.github.santimattius.structured.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpringBackendProfileTest {

    @Test
    fun `StructuredCoroutinesExtension exposes useSpringBackendProfile`() {
        val names = StructuredCoroutinesExtension::class.java.methods.map { it.name }.toSet()
        assertTrue(names.contains("useSpringBackendProfile"))
        assertTrue(names.contains("useKtorBackendProfile"))
    }
}
