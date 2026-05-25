package io.github.santimattius.structured.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DetektBaselineReportParserTest {

    @Test
    fun `parses detekt xml error entries`(@TempDir dir: File) {
        val xml = File(dir, "detekt.xml")
        xml.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <checkstyle version="4.3">
            <file name="src/commonMain/kotlin/Foo.kt">
            <error line="10" column="5" severity="warning" message="[CONCUR_001] synchronized" source="src/commonMain/kotlin/Foo.kt" />
            <error line="5" column="1" severity="error" message="[KMP_002] runBlocking" source="src/commonMain/kotlin/Bar.kt" />
            </file>
            </checkstyle>
            """.trimIndent(),
        )
        val entries = DetektBaselineReportParser.parseReport(xml)
        assertEquals(2, entries.size)
        assertEquals("CONCUR_001", entries[0].finding.ruleId)
        assertEquals("warning", entries[0].detektSeverity)
        assertEquals("KMP_002", entries[1].finding.ruleId)
        assertEquals("error", entries[1].detektSeverity)
    }

    @Test
    fun `uses detekt rule id attribute when present`(@TempDir dir: File) {
        val xml = File(dir, "detekt.xml")
        xml.writeText(
            """
            <error line="1" column="1" severity="warning" message="msg"
                source="Foo.kt" id="SynchronizedInCoroutine" />
            """.trimIndent(),
        )
        val entries = DetektBaselineReportParser.parseReport(xml)
        assertEquals(1, entries.size)
        assertEquals("SynchronizedInCoroutine", entries.single().finding.ruleId)
    }

    @Test
    fun `toFingerprints matches baseline processor format`(@TempDir dir: File) {
        val xml = File(dir, "detekt.xml")
        xml.writeText(
            """<error line="2" severity="warning" message="[FLOW_006] x" source="A.kt" />""",
        )
        val fps = DetektBaselineReportParser.toFingerprints(DetektBaselineReportParser.parseReport(xml))
        assertEquals(1, fps.size)
        assertEquals("FLOW_006", fps.single().ruleId)
        assertTrue(fps.single().relativePath.endsWith("A.kt"))
    }
}
