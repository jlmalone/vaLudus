package org.valudus.society

import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.valudus.core.ContractValidator

class SocietySensitivityTest {
    private val root = Path(System.getProperty("user.dir"))

    @Test fun `published sweep declares 118 configurations and 944 paired worlds`() {
        val plan = SocietySensitivity.readPlan(
            root.resolve("domains/galactica-society/benchmarks/provenance-label/SENSITIVITY.json"),
        )
        val configurations = plan.models.size * (1 + plan.axes.size * 2)
        assertEquals(118, configurations)
        assertEquals(944, configurations * plan.replications)
        assertEquals(setOf("linear-persuasion", "evidence-accumulation"), plan.models.toSet())
    }

    @Test fun `sweep runs every bound against both belief models with child evidence`() {
        val directory = createTempDirectory("valudus-sensitivity")
        val basePlan = root.resolve("domains/galactica-society/benchmarks/provenance-label/PLAN.json")
        val sweepPlan = directory.resolve("sweep.json")
        sweepPlan.writeText(sweepPlan(basePlan.toString()))

        val output = directory.resolve("run")
        val report = SocietySensitivity.run(sweepPlan, output)
        val secondOutput = directory.resolve("second-run")
        SocietySensitivity.run(sweepPlan, secondOutput)
        val configurations = output.resolve("configurations.jsonl").readLines().map {
            Json.parseToJsonElement(it).jsonObject
        }

        assertEquals(emptyList(), ContractValidator.validateRun(report))
        assertEquals(output.resolve("configurations.jsonl").readText(), secondOutput.resolve("configurations.jsonl").readText())
        assertEquals(output.resolve("axis-summaries.jsonl").readText(), secondOutput.resolve("axis-summaries.jsonl").readText())
        assertEquals(6, configurations.size)
        assertEquals(2, output.resolve("axis-summaries.jsonl").readLines().size)
        assertEquals(12, report["metrics"]!!.jsonObject["paired_worlds"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, configurations.filter { it["variation"]!!.jsonPrimitive.content == "base" }.size)
        assertEquals(2, configurations.filter { it["variation"]!!.jsonPrimitive.content == "base" }
            .map { it["mean_final_false_belief_score_reduction"]!!.jsonPrimitive.content }.toSet().size)
        configurations.forEach {
            assertTrue(output.resolve(it["run_report_path"]!!.jsonPrimitive.content).toFile().isFile)
            assertTrue(it["child_evidence_sha256"]!!.jsonPrimitive.content.matches(Regex("[a-f0-9]{64}")))
        }
    }

    private fun sweepPlan(basePlan: String) = """
        {
          "schema_version": "0.1",
          "id": "test-sensitivity",
          "version": "0.1.0",
          "question": "Do both test models run across both bounds?",
          "scope": "Unit-test fixture only.",
          "base_plan": "$basePlan",
          "world": {"population": 12, "days": 60, "replications": 2},
          "models": ["linear-persuasion", "evidence-accumulation"],
          "axes": [
            {"path": "policies.intervention.provenance_label_effectiveness", "low": 0.45, "high": 0.90}
          ],
          "success_criteria": {
            "require_baseline_pass_all_models": false,
            "minimum_configuration_pass_rate": 0.0,
            "maximum_sign_reversals": 6
          }
        }
    """.trimIndent()
}
