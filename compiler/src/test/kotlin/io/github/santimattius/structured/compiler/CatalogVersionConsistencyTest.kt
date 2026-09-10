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

    private val sampleSeverityPropertiesFile: File by lazy {
        val rootDir = System.getProperty("structuredCoroutines.rootDir")
            ?: error("structuredCoroutines.rootDir system property not set — run via Gradle (:compiler:test)")
        File(rootDir, "sample-severity/gradle.properties")
    }

    private fun parseCatalogVersion(key: String): String {
        val pattern = Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        val content = catalogFile.readText()
        return pattern.find(content)?.groupValues?.get(1)
            ?: error("Key '$key' not found in ${catalogFile.absolutePath}")
    }

    private fun parseSampleSeverityProperty(key: String): String {
        val pattern = Regex("""^\s*$key\s*=\s*(\S+)\s*$""", RegexOption.MULTILINE)
        val content = sampleSeverityPropertiesFile.readText()
        return pattern.find(content)?.groupValues?.get(1)
            ?: error("Key '$key' not found in ${sampleSeverityPropertiesFile.absolutePath}")
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

    @Test
    fun `sample-severity kotlinVersion matches root catalog kotlin version`() {
        val catalogKotlin = parseCatalogVersion("kotlin")
        val sampleSeverityKotlin = parseSampleSeverityProperty("kotlinVersion")

        assertEquals(
            catalogKotlin,
            sampleSeverityKotlin,
            "Root catalog kotlin=$catalogKotlin but sample-severity/gradle.properties " +
                "kotlinVersion=$sampleSeverityKotlin; sample-severity is a standalone build and does " +
                "not read the root catalog, so it can silently diverge from the production Kotlin " +
                "version unless kept in sync manually."
        )
    }
}
