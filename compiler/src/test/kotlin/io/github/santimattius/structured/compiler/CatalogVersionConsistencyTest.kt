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

    private val intellijPluginPropertiesFile: File by lazy {
        val rootDir = System.getProperty("structuredCoroutines.rootDir")
            ?: error("structuredCoroutines.rootDir system property not set — run via Gradle (:compiler:test)")
        File(rootDir, "intellij-plugin/gradle.properties")
    }

    private fun parseCatalogVersion(key: String): String {
        val pattern = Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        val content = catalogFile.readText()
        return pattern.find(content)?.groupValues?.get(1)
            ?: error("Key '$key' not found in ${catalogFile.absolutePath}")
    }

    private fun parseProperty(file: File, key: String): String {
        val pattern = Regex("""^\s*$key\s*=\s*(\S+)\s*$""", RegexOption.MULTILINE)
        val content = file.readText()
        return pattern.find(content)?.groupValues?.get(1)
            ?: error("Key '$key' not found in ${file.absolutePath}")
    }

    private fun parseSampleSeverityProperty(key: String): String = parseProperty(sampleSeverityPropertiesFile, key)

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

    @Test
    fun `intellij-plugin kotlinVersion is pinned independently at the documented value`() {
        // :intellij-plugin is an included build (see settings.gradle.kts / intellij-plugin/settings.gradle.kts)
        // with its own Kotlin Gradle Plugin pin, deliberately NOT tracking the root catalog's `kotlin` version.
        // It stays frozen at 2.4.0 — the documented IDE-supported version — until a released
        // intellij-platform-gradle-plugin maps the target IDE build to a newer Kotlin version.
        // See intellij-plugin/gradle.properties and docs/KOTLIN_2_4_READINESS_CHECKLIST.md.
        val documentedIntellijPluginKotlinVersion = "2.4.0"
        val intellijPluginKotlin = parseProperty(intellijPluginPropertiesFile, "kotlinVersion")

        assertEquals(
            documentedIntellijPluginKotlinVersion,
            intellijPluginKotlin,
            "intellij-plugin/gradle.properties kotlinVersion=$intellijPluginKotlin but the documented " +
                "IDE-supported pin is $documentedIntellijPluginKotlinVersion; :intellij-plugin is an " +
                "isolated included build and must stay pinned independently of the root catalog's " +
                "`kotlin` version until the unblock criteria in " +
                "docs/KOTLIN_2_4_READINESS_CHECKLIST.md are met."
        )
    }
}
