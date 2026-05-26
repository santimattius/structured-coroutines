package io.github.santimattius.structured.gradle

import java.io.File

/**
 * Parses Detekt XML report entries for baseline fingerprinting and severity adjustment.
 */
data class ParsedDetektFinding(
    val finding: BaselineProcessor.Finding,
    val detektSeverity: String,
)

object DetektBaselineReportParser {

    private val errorTagRegex = Regex(
        """<error\s+([^>]+)\s*/>""",
        RegexOption.IGNORE_CASE,
    )
    private val attrRegex = Regex("""(\w+)="([^"]*)"""")
    private val ruleCodeInMessageRegex = Regex(
        """\[(SCOPE|RUNBLOCK|DISPATCH|CANCEL|EXCEPT|TEST|ARCH|INTEROP|FLOW|CONCUR|KMP|COMPOSE|BACKEND)_\d{3}]""",
    )

    fun parseReport(file: File): List<ParsedDetektFinding> {
        if (!file.exists()) return emptyList()
        return errorTagRegex.findAll(file.readText()).mapNotNull { match ->
            val attrs = attrRegex.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val source = attrs["source"] ?: return@mapNotNull null
            val line = attrs["line"]?.toIntOrNull() ?: return@mapNotNull null
            val message = attrs["message"] ?: ""
            val ruleId = extractRuleId(attrs["id"], message)
            ParsedDetektFinding(
                finding = BaselineProcessor.Finding(
                    ruleId = ruleId,
                    relativePath = source.replace('\\', '/'),
                    line = line,
                    message = message,
                ),
                detektSeverity = attrs["severity"] ?: "warning",
            )
        }.toList()
    }

    fun toFingerprints(entries: List<ParsedDetektFinding>): List<BaselineProcessor.Fingerprint> =
        entries.map { it.finding.fingerprint() }

    private fun extractRuleId(detektId: String?, message: String): String {
        if (!detektId.isNullOrBlank()) return detektId
        return ruleCodeInMessageRegex.find(message)?.value?.trim('[', ']') ?: "Unknown"
    }
}
