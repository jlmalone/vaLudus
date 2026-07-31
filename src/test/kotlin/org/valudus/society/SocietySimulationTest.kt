package org.valudus.society

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.valudus.core.ContractValidator

class SocietySimulationTest {
    @Test fun `paired experiment is deterministic and preserves all evidence layers`() {
        val directory = createTempDirectory("valudus-society")
        val plan = directory.resolve("plan.json")
        plan.writeText(testPlan())

        val first = SocietySimulation.run(plan, directory.resolve("first"))
        val second = SocietySimulation.run(plan, directory.resolve("second"))

        assertEquals(emptyList(), ContractValidator.validateRun(first))
        assertEquals(first["metrics"], second["metrics"])
        assertEquals(directory.resolve("first/daily-metrics.jsonl").readText(), directory.resolve("second/daily-metrics.jsonl").readText())
        assertEquals(directory.resolve("first/agent-outcomes.jsonl").readText(), directory.resolve("second/agent-outcomes.jsonl").readText())
        assertEquals(directory.resolve("first/replications.jsonl").readText(), directory.resolve("second/replications.jsonl").readText())
        assertEquals(80, directory.resolve("first/daily-metrics.jsonl").readLines().size)
        assertEquals(192, directory.resolve("first/agent-outcomes.jsonl").readLines().size)
        assertEquals(4, directory.resolve("first/replications.jsonl").readLines().size)
        assertTrue(first["environment"]!!.jsonObject["runtime"]!!.jsonPrimitive.content.startsWith("Kotlin/JVM"))
    }

    @Test fun `provenance intervention reduces modeled false belief without erasing neutral reach`() {
        val directory = createTempDirectory("valudus-society-effect")
        val plan = directory.resolve("plan.json")
        plan.writeText(testPlan())

        val report = SocietySimulation.run(plan, directory.resolve("run"))
        val metrics = report["metrics"]!!.jsonObject

        assertTrue(metrics["mean_final_false_belief_score_reduction"]!!.jsonPrimitive.content.toDouble() > 0.0)
        assertTrue(metrics["mean_neutral_reach_retention"]!!.jsonPrimitive.content.toDouble() > 0.90)
        assertEquals("passed", report["outcome"]!!.jsonPrimitive.content)
    }

    private fun testPlan() = """
        {
          "schema_version": "0.1",
          "id": "test-policy-counterfactual",
          "version": "0.1.0",
          "question": "Does the test intervention change the synthetic belief score?",
          "scope": "Unit-test fixture only.",
          "world": {"population": 24, "days": 10, "replications": 4, "seed": 91},
          "campaign": {"start_day": 2, "correction_start_day": 6, "base_false_reach": 0.35, "correction_reach": 0.10, "arousal": 0.90},
          "economy": {"shock_start_day": 3, "shock_end_day": 8, "shock_income_multiplier": 0.55, "essential_daily_cost": 76.0, "financial_buffer": 700.0},
          "policies": {
            "baseline": {"id": "no-label", "provenance_label_effectiveness": 0.0, "sharing_friction": 0.0},
            "intervention": {"id": "label", "provenance_label_effectiveness": 0.85, "sharing_friction": 0.03}
          },
          "success_criteria": {
            "minimum_final_false_belief_score_reduction": 0.0,
            "minimum_low_resource_false_belief_score_reduction": 0.0,
            "minimum_neutral_reach_retention": 0.90
          }
        }
    """.trimIndent()
}
