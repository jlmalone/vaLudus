package org.valudus.core

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.measureNanoTime
import kotlinx.serialization.json.*

/** Public artifact checks shared by the command line and reference runner. */
object ContractValidator {
    fun validateBenchmark(document: JsonObject): List<String> {
        val required = setOf("schema_version", "id", "version", "task_family", "capability_claim", "metrics", "success_threshold", "failure_cases", "contamination", "budget", "fixtures", "evaluation", "execution")
        val errors = missing(document, required).toMutableList()
        if (document["schema_version"]?.jsonPrimitive?.content != "1.1") errors += "schema_version must be 1.1"
        if (document["metrics"] !is JsonArray || document["metrics"]!!.jsonArray.isEmpty()) errors += "metrics must be a non-empty list"
        val threshold = document["success_threshold"] as? JsonObject
        if (threshold?.get("metric")?.jsonPrimitive?.content != "exact_match_rate" || threshold["operator"]?.jsonPrimitive?.content != ">=" || threshold["value"] !is JsonPrimitive) errors += "success_threshold must target exact_match_rate using >= with a numeric value"
        if (document["failure_cases"] !is JsonArray || document["failure_cases"]!!.jsonArray.isEmpty()) errors += "failure_cases must be a non-empty list"
        if ((document["contamination"] as? JsonObject)?.get("residual_risk")?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "contamination must declare residual_risk"
        val budget = document["budget"] as? JsonObject
        val budgetFields = setOf("tokens", "money_usd", "wall_time_seconds", "compute", "memory_mb")
        if (budget == null || budgetFields.any { it !in budget }) errors += "budget must declare tokens, money_usd, wall_time_seconds, compute, and memory_mb"
        else for (field in budgetFields - "compute") {
            val maximum = (budget[field] as? JsonObject)?.get("maximum")
            if (maximum !is JsonNull && maximum !is JsonPrimitive) errors += "budget.$field.maximum must be a number or null"
        }
        val fixtures = document["fixtures"] as? JsonArray
        if (fixtures == null || fixtures.isEmpty()) errors += "fixtures must be a non-empty list"
        else {
            val ids = mutableSetOf<String>()
            fixtures.forEachIndexed { index, element ->
                val fixture = element as? JsonObject
                if (fixture == null) errors += "fixtures[$index] must be an object"
                else {
                    val id = fixture["id"]?.jsonPrimitive?.contentOrNull
                    if (id.isNullOrBlank()) errors += "fixtures[$index].id must be a non-empty string" else if (!ids.add(id)) errors += "fixture id '$id' is duplicated"
                    if (fixture["partition"]?.jsonPrimitive?.content !in setOf("development", "held_out", "adversarial")) errors += "fixtures[$index].partition must be development, held_out, or adversarial"
                    if (fixture["input"] !is JsonObject || fixture["expected"] !is JsonObject) errors += "fixtures[$index] must declare object input and expected values"
                }
            }
        }
        if ((document["evaluation"] as? JsonObject)?.get("scorer")?.jsonPrimitive?.content != "exact_match") errors += "evaluation must select the supported exact_match scorer"
        if ((document["execution"] as? JsonObject)?.get("seed") !is JsonPrimitive) errors += "execution must declare an integer seed"
        return errors
    }

    fun validateRun(document: JsonObject): List<String> {
        val required = setOf("schema_version", "run_id", "benchmark", "system", "reproducibility_tier", "environment", "resources", "metrics", "evidence", "status", "outcome")
        val errors = missing(document, required).toMutableList()
        if (document["schema_version"]?.jsonPrimitive?.content != "1.1") errors += "schema_version must be 1.1"
        if (document["reproducibility_tier"]?.jsonPrimitive?.content !in setOf("exact", "procedural", "exploratory")) errors += "reproducibility_tier must be exact, procedural, or exploratory"
        if (document["status"]?.jsonPrimitive?.content !in setOf("valid", "invalid", "incomplete")) errors += "status must be valid, invalid, or incomplete"
        if (document["outcome"]?.jsonPrimitive?.content !in setOf("passed", "failed", "invalid")) errors += "outcome must be passed, failed, or invalid"
        if (document["evidence"] !is JsonArray || document["evidence"]!!.jsonArray.isEmpty()) errors += "evidence must be a non-empty list"
        return errors
    }

    private fun missing(document: JsonObject, required: Set<String>) = (required - document.keys).takeIf { it.isNotEmpty() }?.let { listOf("missing required fields: ${it.sorted().joinToString(", ")}") }.orEmpty()
}

/** A trusted local reference adapter. Remote and untrusted adapters need a later isolated boundary. */
fun interface ReferenceAdapter { fun evaluate(input: JsonObject): JsonObject }

object ReferenceAdapters {
    fun resolve(name: String): ReferenceAdapter = when (name) {
        "reference-sum" -> ReferenceAdapter { input -> buildJsonObject { put("sum", input["operands"]!!.jsonArray.sumOf { it.jsonPrimitive.int }) } }
        "gambit-siamese-capablanca-reference" -> ReferenceAdapter { input -> GambitSiameseCapablanca.evaluate(input) }
        else -> error("unknown trusted reference adapter: $name")
    }
}

object EvaluationPipeline {
    private val json = Json { prettyPrint = true }

    fun runReference(manifestPath: Path, adapterName: String, outputDirectory: Path, systemName: String, systemVersion: String, configuration: String, tier: String = "procedural"): JsonObject {
        require(!outputDirectory.exists()) { "output directory already exists: $outputDirectory" }
        require(tier in setOf("exact", "procedural", "exploratory")) { "reproducibility tier must be exact, procedural, or exploratory" }
        val manifest = readObject(manifestPath)
        ContractValidator.validateBenchmark(manifest).also { require(it.isEmpty()) { "invalid benchmark manifest: ${it.joinToString("; ")}" } }
        val adapter = ReferenceAdapters.resolve(adapterName)
        outputDirectory.createDirectories()
        val records = mutableListOf<JsonObject>()
        val failures = mutableListOf<String>()
        val elapsedNanos = measureNanoTime {
            manifest["fixtures"]!!.jsonArray.forEach { element ->
                val fixture = element.jsonObject
                val record = try {
                    val output = adapter.evaluate(fixture["input"]!!.jsonObject)
                    buildJsonObject {
                        put("fixture_id", fixture["id"]!!.jsonPrimitive.content); put("partition", fixture["partition"]!!.jsonPrimitive.content)
                        put("input_sha256", sha256(canonical(fixture["input"]!!))); put("expected_sha256", sha256(canonical(fixture["expected"]!!)))
                        put("output", output); putJsonObject("usage") { put("tokens", 0); put("money_usd", 0.0) }
                        put("score", if (output == fixture["expected"]!!.jsonObject) 1.0 else 0.0)
                    }
                } catch (error: Exception) {
                    val message = "fixture ${fixture["id"]!!.jsonPrimitive.content} failed: ${error::class.simpleName}: ${error.message}"
                    failures += message
                    buildJsonObject { put("fixture_id", fixture["id"]!!.jsonPrimitive.content); put("partition", fixture["partition"]!!.jsonPrimitive.content); put("adapter_error", message); put("score", 0.0) }
                }
                records += record
            }
        }
        val evidencePath = outputDirectory.resolve("evidence.jsonl")
        evidencePath.writeText(records.joinToString("\n", postfix = "\n") { compact(it) })
        val evidenceHash = sha256(evidencePath.readText(StandardCharsets.UTF_8))
        val rate = records.map { it["score"]!!.jsonPrimitive.double }.average()
        val budgetFailures = budgetFailures(manifest["budget"]!!.jsonObject, elapsedNanos / 1_000_000_000.0)
        val status = if (failures.isEmpty() && budgetFailures.isEmpty()) "valid" else "invalid"
        val threshold = manifest["success_threshold"]!!.jsonObject["value"]!!.jsonPrimitive.double
        val report = buildJsonObject {
            put("schema_version", "1.1"); put("run_id", "${manifest["id"]!!.jsonPrimitive.content}-${manifest["version"]!!.jsonPrimitive.content}-${evidenceHash.take(12)}")
            putJsonObject("benchmark") { put("id", manifest["id"]!!.jsonPrimitive.content); put("version", manifest["version"]!!.jsonPrimitive.content) }
            putJsonObject("system") { put("name", systemName); put("version", systemVersion); put("configuration", configuration) }
            put("reproducibility_tier", tier)
            putJsonObject("environment") { put("runtime", "Kotlin/JVM ${System.getProperty("java.version")}"); put("seed", manifest["execution"]!!.jsonObject["seed"]!!.jsonPrimitive.int) }
            putJsonObject("resources") { put("tokens", 0); put("money_usd", 0.0); put("wall_time_seconds", elapsedNanos / 1_000_000_000.0); put("compute", System.getProperty("os.arch")); put("memory_mb", Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0)) }
            putJsonObject("metrics") { put("exact_match_rate", rate) }
            putJsonArray("evidence") { add(buildJsonObject { put("path", "evidence.jsonl"); put("sha256", evidenceHash); put("records", records.size) }) }
            putJsonArray("deviations") {}; putJsonArray("failure_reasons") { (failures + budgetFailures).forEach { add(JsonPrimitive(it)) } }
            put("status", status); put("outcome", if (status == "invalid") "invalid" else if (rate >= threshold) "passed" else "failed")
        }
        outputDirectory.resolve("run-report.json").writeText(json.encodeToString(JsonObject.serializer(), report) + "\n")
        return report
    }

    fun readObject(path: Path): JsonObject = json.parseToJsonElement(path.readText()).jsonObject
    private fun budgetFailures(budget: JsonObject, seconds: Double): List<String> {
        val maximum = (budget["wall_time_seconds"] as? JsonObject)?.get("maximum")?.let { if (it is JsonNull) null else it.jsonPrimitive.double }
        return if (maximum != null && seconds > maximum) listOf("budget exceeded: wall_time_seconds $seconds > $maximum") else emptyList()
    }
    private fun canonical(element: JsonElement) = Json.encodeToString(JsonElement.serializer(), element)
    private fun compact(element: JsonElement) = Json.encodeToString(JsonElement.serializer(), element)
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
