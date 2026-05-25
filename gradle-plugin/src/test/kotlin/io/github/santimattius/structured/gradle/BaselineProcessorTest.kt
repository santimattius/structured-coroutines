package io.github.santimattius.structured.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BaselineProcessorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `REPORT_NEW_ONLY demotes baselined finding to info`() {
        val finding = BaselineProcessor.Finding(
            ruleId = "SynchronizedInCoroutine",
            relativePath = "Foo.kt",
            line = 10,
            message = "[CONCUR_001] test",
        )
        val baseline = setOf(finding.fingerprint())
        val effective = BaselineProcessor.effectiveSeverity(
            finding,
            baseline,
            BaselineMode.REPORT_NEW_ONLY,
            "warning",
        )
        assertEquals("info", effective)
    }

    @Test
    fun `REPORT_NEW_ONLY keeps configured severity for new finding`() {
        val finding = BaselineProcessor.Finding(
            ruleId = "SynchronizedInCoroutine",
            relativePath = "Bar.kt",
            line = 5,
            message = "[CONCUR_001] test",
        )
        val baseline = setOf(
            BaselineProcessor.Fingerprint(
                ruleId = "SynchronizedInCoroutine",
                relativePath = "Foo.kt",
                line = 10,
                messageHash = BaselineProcessor.messageHash("other"),
            ),
        )
        val effective = BaselineProcessor.effectiveSeverity(
            finding,
            baseline,
            BaselineMode.REPORT_NEW_ONLY,
            "warning",
        )
        assertEquals("warning", effective)
    }

    @Test
    fun `disabled baseline leaves severity unchanged`() {
        val finding = BaselineProcessor.Finding(
            ruleId = "SynchronizedInCoroutine",
            relativePath = "Foo.kt",
            line = 10,
            message = "msg",
        )
        val effective = BaselineProcessor.effectiveSeverity(
            finding,
            setOf(finding.fingerprint()),
            BaselineMode.REPORT_NEW_ONLY,
            "error",
        )
        assertEquals("info", effective)
    }

    @Test
    fun `load and write baseline round trip`(@TempDir dir: File) {
        val file = File(dir, "coroutines-baseline.xml")
        val fp = BaselineProcessor.Fingerprint(
            ruleId = "RunBlockingInCommonMain",
            relativePath = "src/commonMain/kotlin/A.kt",
            line = 3,
            messageHash = BaselineProcessor.messageHash("[KMP_002]"),
        )
        BaselineProcessor.writeBaseline(file, listOf(fp))
        val loaded = BaselineProcessor.loadBaseline(file)
        assertEquals(1, loaded.size)
        assertTrue(loaded.contains(fp))
    }

    @Test
    fun `empty baseline never marks finding baselined`() {
        val finding = BaselineProcessor.Finding("A", "f.kt", 1, "m")
        assertFalse(BaselineProcessor.isBaselined(finding, emptySet()))
        assertEquals(
            "warning",
            BaselineProcessor.effectiveSeverity(finding, emptySet(), BaselineMode.REPORT_NEW_ONLY, "warning"),
        )
    }
}
