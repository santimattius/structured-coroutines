package io.github.santimattius.structured.gradle

/**
 * Builds the "Suggested Learning Path" section for HTML reports (v0.9.0).
 */
object LearningPathGenerator {

    data class RuleLearningMeta(
        val code: String,
        val title: String,
        val tier: String,
        val impactScore: Int,
        val fixMinutes: Int,
    )

    private val v090Rules = listOf(
        RuleLearningMeta("CONCUR_001", "SynchronizedInCoroutine", "High", 8, 15),
        RuleLearningMeta("CONCUR_002", "SharedMutableStateInCoroutine", "Low", 4, 25),
        RuleLearningMeta("CONCUR_004", "RedundantWithContext", "Low", 3, 10),
        RuleLearningMeta("FLOW_006", "StateInWithEagerlyStrategy", "Medium", 6, 10),
        RuleLearningMeta("FLOW_007", "LaunchInWithUnstructuredScope", "Medium", 7, 12),
        RuleLearningMeta("FLOW_008", "SideEffectInMapOperator", "Low", 3, 15),
        RuleLearningMeta("KMP_002", "RunBlockingInCommonMain", "Critical", 10, 20),
        RuleLearningMeta("KMP_003", "MainScopeWithoutCancel", "Medium", 6, 15),
        RuleLearningMeta("BACKEND_001", "BlockingCallInCoroutineBackend", "High", 9, 20),
        RuleLearningMeta("BACKEND_002", "ThreadLocalNotPropagated", "High", 8, 15),
    )

    private val tierOrder = listOf("Critical", "High", "Medium", "Low")

    fun renderHtml(
        violationCounts: Map<String, Int>? = null,
    ): String {
        val sorted = v090Rules.sortedWith(
            compareBy<RuleLearningMeta> { tierOrder.indexOf(it.tier) }
                .thenByDescending { meta ->
                    val count = violationCounts?.get(meta.code) ?: 0
                    if (violationCounts == null) meta.impactScore.toDouble()
                    else (count * meta.impactScore).toDouble() / meta.fixMinutes
                },
        )
        val hasCounts = violationCounts != null && violationCounts.values.any { it > 0 }
        val rows = sorted.joinToString("\n") { meta ->
            val count = violationCounts?.get(meta.code) ?: 0
            val score = if (hasCounts) count else meta.impactScore
            """        <li><span class="tier-${meta.tier.lowercase()}">${meta.tier}</span> <code>${meta.code}</code> ${meta.title} — score $score, ~${meta.fixMinutes} min</li>"""
        }
        val footer = if (hasCounts) {
            "<p class=\"excl\">Ordered by (count × impact) / fix_minutes from Detekt input.</p>"
        } else {
            "<p class=\"excl\">Violation counts unavailable — ordered by static impact_score.</p>"
        }
        return """
    <section id="learning-path">
      <h2>Suggested Learning Path</h2>
      $footer
      <ol>
$rows
      </ol>
    </section>
        """.trimIndent()
    }
}
