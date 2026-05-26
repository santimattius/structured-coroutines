package io.github.santimattius.structured.gradle

import java.io.File
import java.security.MessageDigest

/**
 * MVP baseline matcher for Detekt-style fingerprints.
 */
object BaselineProcessor {

    data class Fingerprint(
        val ruleId: String,
        val relativePath: String,
        val line: Int,
        val messageHash: String,
    )

    data class Finding(
        val ruleId: String,
        val relativePath: String,
        val line: Int,
        val message: String,
    ) {
        fun fingerprint(): Fingerprint = Fingerprint(
            ruleId = ruleId,
            relativePath = relativePath.replace('\\', '/'),
            line = line,
            messageHash = messageHash(message),
        )
    }

    fun loadBaseline(file: File): Set<Fingerprint> {
        if (!file.exists()) return emptySet()
        val text = file.readText()
        val regex = Regex(
            """<issue\s+id="([^"]+)"\s+file="([^"]+)"\s+line="(\d+)"\s+hash="([^"]+)"\s*/>""",
        )
        return regex.findAll(text).map { m ->
            Fingerprint(
                ruleId = m.groupValues[1],
                relativePath = m.groupValues[2],
                line = m.groupValues[3].toInt(),
                messageHash = m.groupValues[4],
            )
        }.toSet()
    }

    fun isBaselined(finding: Finding, baseline: Set<Fingerprint>): Boolean =
        finding.fingerprint() in baseline

    fun effectiveSeverity(
        finding: Finding,
        baseline: Set<Fingerprint>,
        mode: BaselineMode,
        configuredSeverity: String,
    ): String {
        if (baseline.isEmpty() || mode != BaselineMode.REPORT_NEW_ONLY) return configuredSeverity
        return if (isBaselined(finding, baseline)) "info" else configuredSeverity
    }

    fun writeBaseline(file: File, fingerprints: Collection<Fingerprint>) {
        file.parentFile?.mkdirs()
        val body = fingerprints.sortedWith(
            compareBy({ it.ruleId }, { it.relativePath }, { it.line }),
        ).joinToString("\n") { fp ->
            """    <issue id="${fp.ruleId}" file="${fp.relativePath}" line="${fp.line}" hash="${fp.messageHash}" />"""
        }
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <coroutines-baseline>
            $body
            </coroutines-baseline>
            """.trimIndent(),
        )
    }

    fun messageHash(text: String): String = sha256(text)

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
