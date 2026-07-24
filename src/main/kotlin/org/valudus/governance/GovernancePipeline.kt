package org.valudus.governance

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Evidence-first runner for validating a generated suite against the Kotlin oracle. */
object GovernancePipeline {
    private val json = Json { prettyPrint = true }

    fun runReference(manifestPath: Path, outputDirectory: Path): JsonObject {
        require(!outputDirectory.exists()) { "refusing to overwrite existing path: $outputDirectory" }
        val manifest = json.parseToJsonElement(manifestPath.readText()).jsonObject
        val fixtures = manifest.requiredArray("fixtures")
        outputDirectory.createDirectories()
        val started = System.nanoTime()
        val records = fixtures.map { fixtureElement ->
            val fixture = fixtureElement.jsonObject
            val input = fixture.requiredObject("input")
            val expected = fixture.requiredObject("expected")
            val actual = GovernanceHarness.adjudicate(input)
            buildJsonObject {
                put("fixture_id", fixture.requiredString("id")); put("partition", fixture.requiredString("partition"))
                put("expected", expected); put("output", actual); put("score", if (expected == actual) 1.0 else 0.0)
            }
        }
        val evidencePath = outputDirectory.resolve("evidence.jsonl")
        evidencePath.writeText(records.joinToString(separator = "\n", postfix = "\n") { json.encodeToString(JsonObject.serializer(), it) })
        val evidenceHash = sha256(evidencePath.readText(StandardCharsets.UTF_8).toByteArray())
        val exactMatchRate = records.map { it.requiredNumber("score") }.average()
        val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
        val report = buildJsonObject {
            put("schema_version", "1.1")
            put("run_id", "${manifest.requiredString("id")}-${manifest.requiredString("version")}-${evidenceHash.take(12)}")
            putJsonObject("benchmark") { put("id", manifest.requiredString("id")); put("version", manifest.requiredString("version")) }
            putJsonObject("system") { put("name", "governance-reference"); put("version", "0.1.0"); put("configuration", "deterministic synthetic charter oracle") }
            put("reproducibility_tier", "exact")
            putJsonObject("environment") { put("runtime", "Kotlin/JVM"); put("seed", manifest.requiredObject("execution").requiredNumber("seed").toInt()) }
            putJsonObject("resources") { put("tokens", 0); put("money_usd", 0.0); put("wall_time_seconds", elapsedSeconds); put("compute", "one trusted local process"); put("memory_mb", JsonNull) }
            putJsonObject("metrics") { put("exact_match_rate", exactMatchRate) }
            putJsonArray("evidence") { add(buildJsonObject { put("path", "evidence.jsonl"); put("sha256", evidenceHash); put("records", records.size) }) }
            putJsonArray("deviations") {}
            putJsonArray("failure_reasons") {}
            put("status", "valid"); put("outcome", if (exactMatchRate == 1.0) "passed" else "failed")
        }
        outputDirectory.resolve("run-report.json").writeText(json.encodeToString(JsonObject.serializer(), report) + "\n")
        return report
    }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun JsonObject.requiredArray(name: String) = getValue(name).jsonArray
    private fun JsonObject.requiredObject(name: String) = getValue(name).jsonObject
    private fun JsonObject.requiredString(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.requiredNumber(name: String): Double = getValue(name).jsonPrimitive.content.toDouble()
}
