package org.valudus.governance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GovernanceHarnessTest {
    @Test fun `generator creates a large partitioned suite`() {
        val manifest = GovernanceHarness.buildManifest(caseCount = 120, seed = 19)
        val fixtures = manifest["fixtures"]!!.jsonArray
        assertEquals(120, fixtures.size)
        assertEquals(setOf("development", "held_out", "adversarial"), fixtures.map { it.jsonObject["partition"]!!.jsonPrimitive.content }.toSet())
    }

    @Test fun `explicit prohibitions reject before other controls`() {
        val input = inputOf("deploy", emptyList(), emptyList(), conflictPresent = true, permitted = emptyList(), prohibited = listOf("deploy"))
        assertEquals("reject", GovernanceHarness.adjudicate(input)["decision"]!!.jsonPrimitive.content)
    }

    @Test fun `missing controls escalate instead of guessing`() {
        val input = inputOf("publish", emptyList(), emptyList(), conflictPresent = true, permitted = listOf("publish"), prohibited = emptyList())
        val result = GovernanceHarness.adjudicate(input)
        assertEquals("escalate", result["decision"]!!.jsonPrimitive.content)
        assertTrue(result["reasons"]!!.jsonArray.size == 3)
    }

    @Test fun `reference pipeline preserves evidence and passes generated suite`() {
        val directory = createTempDirectory("valudus-governance")
        val manifest = directory.resolve("manifest.json")
        manifest.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), GovernanceHarness.buildManifest(24, 12)))
        val report = GovernancePipeline.runReference(manifest, directory.resolve("run"))
        assertEquals("valid", report["status"]!!.jsonPrimitive.content)
        assertEquals("passed", report["outcome"]!!.jsonPrimitive.content)
        assertTrue(directory.resolve("run/evidence.jsonl").toFile().isFile)
    }

    private fun inputOf(action: String, evidence: List<String>, approvals: List<String>, conflictPresent: Boolean, permitted: List<String>, prohibited: List<String>) = buildJsonObject {
        putJsonObject("charter") {
            putJsonArray("permitted_actions") { permitted.forEach { add(JsonPrimitive(it)) } }; putJsonArray("prohibited_actions") { prohibited.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("required_evidence") { putJsonArray("publish") { add(JsonPrimitive("audit")) } }
            putJsonObject("required_approvals") { putJsonArray("publish") { add(JsonPrimitive("reviewer")) } }
        }
        putJsonObject("proposal") {
            put("action", action); putJsonArray("evidence") { evidence.forEach { add(JsonPrimitive(it)) } }; putJsonArray("approvals") { approvals.forEach { add(JsonPrimitive(it)) } }
            put("conflict_present", conflictPresent); put("conflict_mitigated", false)
        }
    }
}
