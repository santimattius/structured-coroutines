package io.github.santimattius.structured.compiler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guard test that asserts the Kotlin and kotlinx-coroutines versions declared in
 * `gradle/libs.versions.toml` match the values injected as system properties by
 * `compiler/build.gradle.kts`. If this test fails, the functional-test scratch project
 * template will have drifted from the catalog and can reintroduce the KT-83341 ABI crash.
 */
class CatalogVersionConsistencyTest {

    private val catalogFile: File by lazy {
        val rootDir = System.getProperty("structuredCoroutines.rootDir")
            ?: error("structuredCoroutines.rootDir system property not set — run via Gradle (:compiler:test)")
        File(rootDir, "gradle/libs.versions.toml")
    }

    private fun parseCatalogVersion(key: String): String {
        val pattern = Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        val content = catalogFile.readText()
        return pattern.find(content)?.groupValues?.get(1)
            ?: error("Key '$key' not found in ${catalogFile.absolutePath}")
    }

    @Test
    fun `catalog kotlin version matches kotlinVersion system property`() {
        val catalogKotlin = parseCatalogVersion("kotlin")
        val propertyKotlin = System.getProperty("kotlinVersion")
            ?: error("kotlinVersion system property not set — check compiler/build.gradle.kts tasks.test block")

        assertEquals(
            catalogKotlin,
            propertyKotlin,
            "Catalog kotlin=$catalogKotlin but test system property kotlinVersion=$propertyKotlin; " +
                "functional-test template will drift from the catalog and can reintroduce the KT-83341 ABI crash."
        )
    }

    @Test
    fun `catalog kotlinx-coroutines version matches coroutinesVersion system property`() {
        val catalogCoroutines = parseCatalogVersion("kotlinx-coroutines")
        val propertyCoroutines = System.getProperty("coroutinesVersion")
            ?: error("coroutinesVersion system property not set — check compiler/build.gradle.kts tasks.test block")

        assertEquals(
            catalogCoroutines,
            propertyCoroutines,
            "Catalog kotlinx-coroutines=$catalogCoroutines but test system property coroutinesVersion=$propertyCoroutines; " +
                "functional-test template will drift from the catalog and can reintroduce the KT-83341 ABI crash."
        )
    }
}
