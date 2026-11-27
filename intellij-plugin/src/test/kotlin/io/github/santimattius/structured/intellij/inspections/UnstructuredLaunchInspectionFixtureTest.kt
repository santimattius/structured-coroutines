package io.github.santimattius.structured.intellij.inspections

import com.intellij.psi.util.PsiTreeUtil
import io.github.santimattius.structured.intellij.fixture.InspectionFixtureTestCase
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression

class UnstructuredLaunchInspectionFixtureTest : InspectionFixtureTestCase() {
    private fun count(body: String): Int =
        highlights(
            UnstructuredLaunchInspection(),
            """
            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.*

            fun rememberAction(other: CoroutineScope): () -> Unit {
                val scope = rememberCoroutineScope()
                $body
            }
            """.trimIndent(),
        ).size

    fun `test accepts remembered scope captured by nested local functions`() {
        assertEquals(
            0,
            count(
                """
            fun report() {
                fun show() {
                    scope.launch { println("message") }
                    scope.async { "message" }
                }
                show()
            }
            return { report() }
        """,
            ),
        )
    }

    fun `test reports local function parameter shadowing remembered scope`() {
        assertEquals(
            2,
            count(
                """
            fun report(scope: CoroutineScope) {
                scope.launch { println("message") }
                scope.async { "message" }
            }
            return { report(other) }
        """,
            ),
        )
    }

    fun `test reports local property shadowing remembered scope`() {
        assertEquals(
            2,
            count(
                """
            fun report() {
                val scope = other
                scope.launch { println("message") }
                scope.async { "message" }
            }
            return { report() }
        """,
            ),
        )
    }

    fun `test framework scope lookup follows local functions only when opted in`() {
        val file =
            myFixture.configureByText(
                "Test.kt",
                """
                fun rememberAction(): () -> Unit {
                    val scope = rememberCoroutineScope()
                    fun report() { scope.launch { println("message") } }
                    return { report() }
                }
                """.trimIndent(),
            )
        val launch =
            PsiTreeUtil
                .findChildrenOfType(file, KtCallExpression::class.java)
                .single { it.calleeExpression?.text == "launch" }
        assertFalse(CoroutinePsiUtils.isFrameworkScopeCall(launch))
        assertTrue(CoroutinePsiUtils.isFrameworkScopeCall(launch, followLocalFunctions = true))
    }

    fun `test preserves direct and lambda captured remembered scopes`() {
        assertEquals(
            0,
            count(
                """
            scope.launch { println("message") }
            scope.async { "message" }
            return {
                scope.launch { println("message") }
                scope.async { "message" }
            }
        """,
            ),
        )
    }
}
