package org.valudus.society

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.measureNanoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class SensitivityAxis(val path: String, val low: Double, val high: Double)

data class SocietySensitivityPlan(
    val id: String,
    val version: String,
    val question: String,
    val scope: String,
    val basePlan: String,
    val population: Int,
    val days: Int,
    val replications: Int,
    val models: List<String>,
    val axes: List<SensitivityAxis>,
    val requireBaselinePassAllModels: Boolean,
    val minimumConfigurationPassRate: Double,
    val maximumSignReversals: Int,
)

/** Runs one-factor-at-a-time robustness studies while retaining each child run's evidence. */
object SocietySensitivity {
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json

    fun readPlan(path: Path): SocietySensitivityPlan {
        val root = prettyJson.parseToJsonElement(path.readText()).jsonObject
        require(root.requiredString("schema_version") == "0.1") { "schema_version must be 0.1" }
        val world = root.requiredObject("world")
        val success = root.requiredObject("success_criteria")
        return SocietySensitivityPlan(
            id = root.requiredString("id"),
            version = root.requiredString("version"),
            question = root.requiredString("question"),
            scope = root.requiredString("scope"),
            basePlan = root.requiredString("base_plan"),
            population = world.requiredInt("population"),
            days = world.requiredInt("days"),
            replications = world.requiredInt("replications"),
            models = root.getValue("models").jsonArray.map { it.jsonPrimitive.content },
            axes = root.getValue("axes").jsonArray.map { element ->
                element.jsonObject.let { SensitivityAxis(it.requiredString("path"), it.requiredDouble("low"), it.requiredDouble("high")) }
            },
            requireBaselinePassAllModels = success.requiredBoolean("require_baseline_pass_all_models"),
            minimumConfigurationPassRate = success.requiredDouble("minimum_configuration_pass_rate"),
            maximumSignReversals = success.requiredInt("maximum_sign_reversals"),
        ).also(::validate)
    }

    fun run(sweepPath: Path, outputDirectory: Path): JsonObject {
        require(!outputDirectory.exists()) { "refusing to overwrite existing path: $outputDirectory" }
        val sweep = readPlan(sweepPath)
        val basePath = sweepPath.parent?.resolve(sweep.basePlan)?.normalize() ?: Path(sweep.basePlan)
        SocietySimulation.readPlan(basePath)
        val baseDocument = prettyJson.parseToJsonElement(basePath.readText()).jsonObject
        outputDirectory.createDirectories()
        val copiedSweep = outputDirectory.resolve("sweep-plan.json")
        Files.copy(sweepPath, copiedSweep)
        val configurationsPath = outputDirectory.resolve("configurations.jsonl")
        val variantsDirectory = outputDirectory.resolve("variants").also { it.createDirectories() }
        val results = mutableListOf<ConfigurationResult>()

        val elapsedNanos = measureNanoTime {
            configurationsPath.bufferedWriter().use { writer ->
                sweep.models.forEach { model ->
                    val configurations = listOf<Variation?>(null) + sweep.axes.flatMap { axis ->
                        listOf(Variation(axis, "low", axis.low), Variation(axis, "high", axis.high))
                    }
                    configurations.forEach { variation ->
                        val configurationId = configurationId(model, variation)
                        var document = baseDocument
                        document = document.withPath(listOf("id"), JsonPrimitive("${sweep.id}.$configurationId"))
                        document = document.withPath(listOf("version"), JsonPrimitive(sweep.version))
                        document = document.withPath(listOf("question"), JsonPrimitive(sweep.question))
                        document = document.withPath(listOf("scope"), JsonPrimitive("${sweep.scope} Configuration: $configurationId."))
                        document = document.withPath(listOf("belief_model"), JsonPrimitive(model))
                        document = document.withPath(listOf("world", "population"), JsonPrimitive(sweep.population))
                        document = document.withPath(listOf("world", "days"), JsonPrimitive(sweep.days))
                        document = document.withPath(listOf("world", "replications"), JsonPrimitive(sweep.replications))
                        if (variation != null) document = document.withPath(variation.axis.path.split('.'), JsonPrimitive(variation.value))

                        val configurationDirectory = variantsDirectory.resolve(configurationId).also { it.createDirectories() }
                        val inputPlan = configurationDirectory.resolve("input-plan.json")
                        inputPlan.writeText(prettyJson.encodeToString(JsonObject.serializer(), document) + "\n")
                        val runDirectory = configurationDirectory.resolve("run")
                        val report = SocietySimulation.run(inputPlan, runDirectory)
                        val result = ConfigurationResult.from(configurationId, model, variation, report, outputDirectory, runDirectory)
                        results += result
                        writer.write(compactJson.encodeToString(JsonObject.serializer(), result.toJson()))
                        writer.newLine()
                    }
                }
            }
        }

        val baselineResults = results.filter { it.axis == null }
        val configurationPassRate = results.count { it.outcome == "passed" }.toDouble() / results.size
        val signReversals = results.count { it.effect < 0.0 }
        val modelDisagreements = results.groupBy { it.variationKey }.count { (_, group) -> group.map { it.outcome }.toSet().size > 1 }
        val axisSummaries = sweep.models.flatMap { model ->
            val baseline = baselineResults.single { it.model == model }
            sweep.axes.map { axis ->
                val low = results.single { it.model == model && it.axis == axis.path && it.bound == "low" }
                val high = results.single { it.model == model && it.axis == axis.path && it.bound == "high" }
                AxisSummary(
                    model = model,
                    axis = axis.path,
                    baselineEffect = baseline.effect,
                    baselineLowResourceEffect = baseline.lowResourceEffect,
                    baselineNeutralReachRetention = baseline.neutralReachRetention,
                    baselineOutcome = baseline.outcome,
                    lowValue = axis.low,
                    lowEffect = low.effect,
                    lowResourceEffect = low.lowResourceEffect,
                    lowNeutralReachRetention = low.neutralReachRetention,
                    lowOutcome = low.outcome,
                    highValue = axis.high,
                    highEffect = high.effect,
                    highLowResourceEffect = high.lowResourceEffect,
                    highNeutralReachRetention = high.neutralReachRetention,
                    highOutcome = high.outcome,
                    fragile = listOf(low, high).any {
                        it.outcome != baseline.outcome || sign(it.effect) != sign(baseline.effect)
                    },
                )
            }
        }
        val axisSummaryPath = outputDirectory.resolve("axis-summaries.jsonl")
        axisSummaryPath.bufferedWriter().use { writer ->
            axisSummaries.forEach { summary ->
                writer.write(compactJson.encodeToString(JsonObject.serializer(), summary.toJson()))
                writer.newLine()
            }
        }
        val fragileAxes = axisSummaries.filter { it.fragile }.map { it.axis }.toSet().size
        val failedBaselineModels = baselineResults.filter { it.outcome != "passed" }.map { it.model }
        val modelPassRates = sweep.models.associateWith { model ->
            results.count { it.model == model && it.outcome == "passed" }.toDouble() / results.count { it.model == model }
        }
        val failures = buildList {
            if (sweep.requireBaselinePassAllModels && failedBaselineModels.isNotEmpty()) {
                add("baseline failed for belief models: ${failedBaselineModels.joinToString(", ")}")
            }
            if (configurationPassRate < sweep.minimumConfigurationPassRate) {
                add("configuration pass rate $configurationPassRate < ${sweep.minimumConfigurationPassRate}")
            }
            if (signReversals > sweep.maximumSignReversals) {
                add("sign reversals $signReversals > ${sweep.maximumSignReversals}")
            }
        }
        val configurationsHash = sha256(configurationsPath)
        val axisSummaryHash = sha256(axisSummaryPath)
        val sweepHash = sha256(copiedSweep)
        val evidenceDigest = sha256(sweepHash + configurationsHash + axisSummaryHash)
        val report = buildJsonObject {
            put("schema_version", "1.1")
            put("run_id", "${sweep.id}-${sweep.version}-${evidenceDigest.take(12)}")
            putJsonObject("benchmark") { put("id", sweep.id); put("version", sweep.version) }
            putJsonObject("system") {
                put("name", "valudus-society-sensitivity")
                put("version", SocietySimulation.KERNEL_VERSION)
                put("configuration", "one-factor-at-a-time across ${sweep.models.joinToString(",")}; child runs retain full evidence")
            }
            put("reproducibility_tier", "exact")
            putJsonObject("environment") {
                put("platform", "${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                put("runtime", "Kotlin/JVM ${System.getProperty("java.version")}")
                put("seed", baseDocument.requiredObject("world").requiredInt("seed"))
            }
            putJsonObject("resources") {
                put("tokens", 0); put("money_usd", 0.0)
                put("wall_time_seconds", elapsedNanos / 1_000_000_000.0)
                put("compute", "one local JVM process")
                put("memory_mb", Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0))
            }
            putJsonObject("metrics") {
                put("configurations", results.size)
                put("paired_worlds", results.size * sweep.replications)
                put("child_evidence_records", results.sumOf { it.evidenceRecords })
                put("configuration_pass_rate", configurationPassRate)
                put("baseline_models_passed", baselineResults.size - failedBaselineModels.size)
                put("sign_reversal_count", signReversals)
                put("model_disagreement_count", modelDisagreements)
                put("fragile_axis_count", fragileAxes)
                put("minimum_final_false_belief_score_reduction", results.minOf { it.effect })
                put("maximum_final_false_belief_score_reduction", results.maxOf { it.effect })
                sweep.models.forEach { model ->
                    val metricPrefix = model.replace('-', '_')
                    put("${metricPrefix}_configuration_pass_rate", modelPassRates.getValue(model))
                    put("${metricPrefix}_baseline_effect", baselineResults.single { it.model == model }.effect)
                }
            }
            putJsonArray("evidence") {
                add(buildJsonObject { put("path", "sweep-plan.json"); put("sha256", sweepHash); put("records", 1) })
                add(buildJsonObject { put("path", "configurations.jsonl"); put("sha256", configurationsHash); put("records", results.size) })
                add(buildJsonObject { put("path", "axis-summaries.jsonl"); put("sha256", axisSummaryHash); put("records", axisSummaries.size) })
            }
            putJsonArray("deviations") {}
            putJsonArray("failure_reasons") { failures.forEach { add(JsonPrimitive(it)) } }
            put("status", "valid")
            put("outcome", if (failures.isEmpty()) "passed" else "failed")
        }
        outputDirectory.resolve("run-report.json").writeText(prettyJson.encodeToString(JsonObject.serializer(), report) + "\n")
        return report
    }

    private fun validate(plan: SocietySensitivityPlan) {
        val safe = Regex("[a-z0-9][a-z0-9._-]*")
        require(plan.id.matches(safe) && plan.version.matches(safe)) { "id and version must be lowercase and path-safe" }
        require(plan.question.isNotBlank() && plan.scope.isNotBlank() && plan.basePlan.isNotBlank()) { "question, scope, and base_plan must not be blank" }
        require(plan.population >= 8 && plan.days >= 2 && plan.replications >= 1) { "world must contain at least 8 people, 2 days, and 1 replication" }
        require(plan.models.isNotEmpty() && plan.models.distinct().size == plan.models.size) { "models must be non-empty and unique" }
        require(plan.models.all { it in setOf("linear-persuasion", "evidence-accumulation") }) { "unsupported belief model" }
        require(plan.axes.isNotEmpty() && plan.axes.map { it.path }.distinct().size == plan.axes.size) { "axes must be non-empty and unique" }
        require(plan.axes.all { it.path.matches(Regex("[a-z0-9_]+(\\.[a-z0-9_]+)+")) && it.low != it.high }) { "each axis needs a nested path and distinct low and high values" }
        require(plan.minimumConfigurationPassRate in 0.0..1.0) { "minimum_configuration_pass_rate must be between 0 and 1" }
        require(plan.maximumSignReversals >= 0) { "maximum_sign_reversals must not be negative" }
    }

    private fun JsonObject.withPath(path: List<String>, value: JsonElement): JsonObject {
        require(path.isNotEmpty()) { "override path must not be empty" }
        val key = path.first()
        require(key in this) { "override path does not exist: ${path.joinToString(".")}" }
        return JsonObject(mapValues { (candidate, current) ->
            if (candidate != key) current
            else if (path.size == 1) value
            else current.jsonObject.withPath(path.drop(1), value)
        })
    }

    private fun configurationId(model: String, variation: Variation?): String {
        if (variation == null) return "$model-base"
        return "$model-${variation.axis.path.replace('.', '-')}-${variation.bound}"
    }

    private fun sign(value: Double) = when { value > 0.0 -> 1; value < 0.0 -> -1; else -> 0 }
    private fun sha256(path: Path): String = Files.newInputStream(path).use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun JsonObject.requiredObject(name: String) = getValue(name).jsonObject
    private fun JsonObject.requiredString(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.requiredInt(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.requiredDouble(name: String) = getValue(name).jsonPrimitive.double
    private fun JsonObject.requiredBoolean(name: String) = getValue(name).jsonPrimitive.content.toBooleanStrict()

    private data class Variation(val axis: SensitivityAxis, val bound: String, val value: Double)

    private data class AxisSummary(
        val model: String,
        val axis: String,
        val baselineEffect: Double,
        val baselineLowResourceEffect: Double,
        val baselineNeutralReachRetention: Double,
        val baselineOutcome: String,
        val lowValue: Double,
        val lowEffect: Double,
        val lowResourceEffect: Double,
        val lowNeutralReachRetention: Double,
        val lowOutcome: String,
        val highValue: Double,
        val highEffect: Double,
        val highLowResourceEffect: Double,
        val highNeutralReachRetention: Double,
        val highOutcome: String,
        val fragile: Boolean,
    ) {
        fun toJson() = buildJsonObject {
            put("belief_model", model); put("axis", axis); put("fragile", fragile)
            putJsonObject("baseline") { put("effect", baselineEffect); put("low_resource_effect", baselineLowResourceEffect); put("neutral_reach_retention", baselineNeutralReachRetention); put("outcome", baselineOutcome) }
            putJsonObject("low") { put("value", lowValue); put("effect", lowEffect); put("low_resource_effect", lowResourceEffect); put("neutral_reach_retention", lowNeutralReachRetention); put("outcome", lowOutcome) }
            putJsonObject("high") { put("value", highValue); put("effect", highEffect); put("low_resource_effect", highLowResourceEffect); put("neutral_reach_retention", highNeutralReachRetention); put("outcome", highOutcome) }
        }
    }

    private data class ConfigurationResult(
        val id: String,
        val model: String,
        val axis: String?,
        val bound: String?,
        val value: Double?,
        val outcome: String,
        val effect: Double,
        val lowResourceEffect: Double,
        val neutralReachRetention: Double,
        val reportPath: String,
        val childEvidenceHash: String,
        val evidenceRecords: Int,
    ) {
        val variationKey = axis?.let { "$it:$bound" } ?: "base"

        fun toJson() = buildJsonObject {
            put("configuration_id", id); put("belief_model", model)
            if (axis == null) put("variation", "base") else {
                put("variation", bound!!); put("axis", axis); put("value", value!!)
            }
            put("outcome", outcome)
            put("mean_final_false_belief_score_reduction", effect)
            put("mean_low_resource_false_belief_score_reduction", lowResourceEffect)
            put("mean_neutral_reach_retention", neutralReachRetention)
            put("run_report_path", reportPath); put("child_evidence_sha256", childEvidenceHash)
            put("child_evidence_records", evidenceRecords)
        }

        companion object {
            fun from(
                id: String,
                model: String,
                variation: Variation?,
                report: JsonObject,
                outputDirectory: Path,
                runDirectory: Path,
            ): ConfigurationResult {
                val metrics = report.requiredObject("metrics")
                val reportPath = runDirectory.resolve("run-report.json")
                val evidence = report.getValue("evidence").jsonArray
                return ConfigurationResult(
                    id = id,
                    model = model,
                    axis = variation?.axis?.path,
                    bound = variation?.bound,
                    value = variation?.value,
                    outcome = report.requiredString("outcome"),
                    effect = metrics.requiredDouble("mean_final_false_belief_score_reduction"),
                    lowResourceEffect = metrics.requiredDouble("mean_low_resource_false_belief_score_reduction"),
                    neutralReachRetention = metrics.requiredDouble("mean_neutral_reach_retention"),
                    reportPath = outputDirectory.relativize(reportPath).toString(),
                    childEvidenceHash = sha256(evidence.joinToString("") { it.jsonObject.requiredString("sha256") }),
                    evidenceRecords = evidence.sumOf { it.jsonObject.requiredInt("records") },
                )
            }
        }
    }
}
