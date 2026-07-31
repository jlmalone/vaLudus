package org.valudus.society

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class SocietyPolicy(
    val id: String,
    val provenanceLabelEffectiveness: Double,
    val sharingFriction: Double,
)

data class SocietyBehavior(
    val neutralReach: Double = 0.70,
    val maximumExposureProbability: Double = 0.95,
    val neighborReachWeight: Double = 0.35,
    val interestReachWeight: Double = 0.15,
    val mediaBaseMinutes: Double = 35.0,
    val mediaInterestMinutes: Double = 85.0,
    val mediaStressMinutes: Double = 30.0,
    val mediaExposureReferenceMinutes: Double = 120.0,
    val mediaExposureWeight: Double = 0.50,
    val labelLiteracyWeight: Double = 0.65,
    val persuasionBase: Double = 0.08,
    val persuasionSusceptibilityWeight: Double = 0.24,
    val persuasionStressWeight: Double = 0.10,
    val correctionBase: Double = 0.06,
    val correctionLiteracyWeight: Double = 0.20,
    val correctionTrustWeight: Double = 0.50,
    val socialUpdateRate: Double = 0.08,
    val adoptionThreshold: Double = 0.50,
    val evidenceFalseScale: Double = 4.0,
    val evidenceCorrectionScale: Double = 4.0,
    val evidenceSocialScale: Double = 2.0,
)

data class SocietyExperimentPlan(
    val id: String,
    val version: String,
    val question: String,
    val scope: String,
    val beliefModel: String,
    val behavior: SocietyBehavior,
    val population: Int,
    val days: Int,
    val replications: Int,
    val seed: Int,
    val campaignStartDay: Int,
    val correctionStartDay: Int,
    val baseFalseReach: Double,
    val correctionReach: Double,
    val campaignArousal: Double,
    val shockStartDay: Int,
    val shockEndDay: Int,
    val shockIncomeMultiplier: Double,
    val essentialDailyCost: Double,
    val financialBuffer: Double,
    val baseline: SocietyPolicy,
    val intervention: SocietyPolicy,
    val minimumFalseBeliefScoreReduction: Double,
    val minimumLowResourceScoreReduction: Double,
    val minimumNeutralReachRetention: Double,
)

/**
 * Deterministic synthetic policy laboratory. Its coefficients are hypotheses inside a modeled
 * world, not estimates of real people or forecasts of real policy outcomes.
 */
object SocietySimulation {
    const val KERNEL_VERSION = "0.2.0"
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json

    fun readPlan(path: Path): SocietyExperimentPlan {
        val root = prettyJson.parseToJsonElement(path.readText()).jsonObject
        require(root.requiredString("schema_version") == "0.1") { "schema_version must be 0.1" }
        val world = root.requiredObject("world")
        val campaign = root.requiredObject("campaign")
        val economy = root.requiredObject("economy")
        val policies = root.requiredObject("policies")
        val success = root.requiredObject("success_criteria")
        val behavior = root["behavior"]?.jsonObject?.toBehavior() ?: SocietyBehavior()
        return SocietyExperimentPlan(
            id = root.requiredString("id"),
            version = root.requiredString("version"),
            question = root.requiredString("question"),
            scope = root.requiredString("scope"),
            beliefModel = root["belief_model"]?.jsonPrimitive?.content ?: "linear-persuasion",
            behavior = behavior,
            population = world.requiredInt("population"),
            days = world.requiredInt("days"),
            replications = world.requiredInt("replications"),
            seed = world.requiredInt("seed"),
            campaignStartDay = campaign.requiredInt("start_day"),
            correctionStartDay = campaign.requiredInt("correction_start_day"),
            baseFalseReach = campaign.requiredDouble("base_false_reach"),
            correctionReach = campaign.requiredDouble("correction_reach"),
            campaignArousal = campaign.requiredDouble("arousal"),
            shockStartDay = economy.requiredInt("shock_start_day"),
            shockEndDay = economy.requiredInt("shock_end_day"),
            shockIncomeMultiplier = economy.requiredDouble("shock_income_multiplier"),
            essentialDailyCost = economy.requiredDouble("essential_daily_cost"),
            financialBuffer = economy.requiredDouble("financial_buffer"),
            baseline = policies.requiredObject("baseline").toPolicy(),
            intervention = policies.requiredObject("intervention").toPolicy(),
            minimumFalseBeliefScoreReduction = success.requiredDouble("minimum_final_false_belief_score_reduction"),
            minimumLowResourceScoreReduction = success.requiredDouble("minimum_low_resource_false_belief_score_reduction"),
            minimumNeutralReachRetention = success.requiredDouble("minimum_neutral_reach_retention"),
        ).also(::validate)
    }

    fun run(planPath: Path, outputDirectory: Path): JsonObject {
        require(!outputDirectory.exists()) { "refusing to overwrite existing path: $outputDirectory" }
        val plan = readPlan(planPath)
        outputDirectory.createDirectories()
        val copiedPlan = outputDirectory.resolve("plan.json")
        Files.copy(planPath, copiedPlan)
        val dailyPath = outputDirectory.resolve("daily-metrics.jsonl")
        val agentPath = outputDirectory.resolve("agent-outcomes.jsonl")
        val replicationPath = outputDirectory.resolve("replications.jsonl")
        val summaries = mutableListOf<PairedSummary>()

        val elapsedNanos = measureNanoTime {
            dailyPath.bufferedWriter().use { dailyWriter ->
                agentPath.bufferedWriter().use { agentWriter ->
                    replicationPath.bufferedWriter().use { replicationWriter ->
                        repeat(plan.replications) { replication ->
                            val baseline = simulate(plan, plan.baseline, replication)
                            val intervention = simulate(plan, plan.intervention, replication)
                            baseline.daily.forEach { dailyWriter.writeLine(it.toJson(replication, plan.baseline.id)) }
                            intervention.daily.forEach { dailyWriter.writeLine(it.toJson(replication, plan.intervention.id)) }
                            baseline.agents.forEach { agentWriter.writeLine(it.toJson(replication, plan.baseline.id, plan.behavior.adoptionThreshold)) }
                            intervention.agents.forEach { agentWriter.writeLine(it.toJson(replication, plan.intervention.id, plan.behavior.adoptionThreshold)) }
                            PairedSummary(replication, baseline.summary, intervention.summary).also {
                                summaries += it
                                replicationWriter.writeLine(it.toJson())
                            }
                        }
                    }
                }
            }
        }

        val falseReductions = summaries.map { it.falseBeliefReduction }
        val lowResourceReductions = summaries.map { it.lowResourceFalseBeliefReduction }
        val neutralRetention = summaries.map { it.neutralReachRetention }
        val falseInterval = confidenceInterval(falseReductions)
        val lowResourceInterval = confidenceInterval(lowResourceReductions)
        val meanNeutralRetention = neutralRetention.average()
        val failures = buildList {
            if (falseInterval.mean < plan.minimumFalseBeliefScoreReduction) add("mean final false-belief score reduction did not meet the declared threshold")
            if (lowResourceInterval.mean < plan.minimumLowResourceScoreReduction) add("mean low-resource false-belief score reduction did not meet the declared threshold")
            if (meanNeutralRetention < plan.minimumNeutralReachRetention) add("mean neutral-reach retention did not meet the declared threshold")
        }
        val evidence = listOf(
            Evidence("plan.json", copiedPlan, 1),
            Evidence("daily-metrics.jsonl", dailyPath, plan.replications * plan.days * 2),
            Evidence("agent-outcomes.jsonl", agentPath, plan.replications * plan.population * 2),
            Evidence("replications.jsonl", replicationPath, plan.replications),
        )
        val evidenceDigest = sha256(evidence.joinToString("") { it.hash })
        val report = buildJsonObject {
            put("schema_version", "1.1")
            put("run_id", "${plan.id}-${plan.version}-${evidenceDigest.take(12)}")
            putJsonObject("benchmark") { put("id", plan.id); put("version", plan.version) }
            putJsonObject("system") {
                put("name", "valudus-society-reference")
                put("version", KERNEL_VERSION)
                put("configuration", "${plan.beliefModel}; paired ${plan.baseline.id} versus ${plan.intervention.id}; synthetic coefficients declared by kernel $KERNEL_VERSION")
            }
            put("reproducibility_tier", "exact")
            putJsonObject("environment") {
                put("platform", "${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                put("runtime", "Kotlin/JVM ${System.getProperty("java.version")}")
                put("seed", plan.seed)
            }
            putJsonObject("resources") {
                put("tokens", 0); put("money_usd", 0.0)
                put("wall_time_seconds", elapsedNanos / 1_000_000_000.0)
                put("compute", "one local JVM process")
                put("memory_mb", Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0))
            }
            putJsonObject("metrics") {
                put("replications", plan.replications)
                put("mean_final_false_belief_score_reduction", falseInterval.mean)
                put("final_false_belief_score_reduction_ci95_lower", falseInterval.lower)
                put("final_false_belief_score_reduction_ci95_upper", falseInterval.upper)
                put("mean_low_resource_false_belief_score_reduction", lowResourceInterval.mean)
                put("low_resource_false_belief_score_reduction_ci95_lower", lowResourceInterval.lower)
                put("low_resource_false_belief_score_reduction_ci95_upper", lowResourceInterval.upper)
                put("mean_neutral_reach_retention", meanNeutralRetention)
                put("mean_false_reshare_reduction", summaries.map { it.falseReshareReduction }.average())
            }
            putJsonArray("evidence") {
                evidence.forEach { item ->
                    add(buildJsonObject { put("path", item.name); put("sha256", item.hash); put("records", item.records) })
                }
            }
            putJsonArray("deviations") {}
            putJsonArray("failure_reasons") { failures.forEach { add(JsonPrimitive(it)) } }
            put("status", "valid")
            put("outcome", if (failures.isEmpty()) "passed" else "failed")
        }
        outputDirectory.resolve("run-report.json").writeText(prettyJson.encodeToString(JsonObject.serializer(), report) + "\n")
        return report
    }

    private fun simulate(plan: SocietyExperimentPlan, policy: SocietyPolicy, replication: Int): SimulationResult {
        val simulationSeed = plan.seed xor (replication * -1_640_531_527)
        val profileRandom = Random(simulationSeed xor 0x51A7C0DE)
        val eventRandom = Random(simulationSeed xor 0x0B51E55)
        val agents = MutableList(plan.population) { index -> AgentState.create(index, profileRandom) }
        val daily = mutableListOf<DailyMetrics>()
        var totalNeutralExposures = 0
        var totalFalseReshares = 0
        var peakFalseBeliefPrevalence = 0.0

        repeat(plan.days) { dayIndex ->
            val day = dayIndex + 1
            val previousBeliefs = agents.map { it.falseBelief }
            var falseExposures = 0
            var correctionExposures = 0
            var neutralExposures = 0
            var falseReshares = 0
            var workMinutes = 0
            var mediaMinutes = 0
            var leisureMinutes = 0
            var stressedAgents = 0

            agents.forEachIndexed { index, agent ->
                val workday = dayIndex % 7 < 5
                val shockActive = day in plan.shockStartDay..plan.shockEndDay
                val incomeMultiplier = if (shockActive) plan.shockIncomeMultiplier else 1.0
                val workToday = if (workday && agent.employed) 480 else 0
                val income = if (workToday > 0) agent.dailyIncome * incomeMultiplier else 0.0
                val available = agent.savings + income
                val unmetEssentials = (plan.essentialDailyCost - available).coerceAtLeast(0.0)
                agent.savings = (available - plan.essentialDailyCost).coerceAtLeast(0.0)
                agent.unmetEssentials += unmetEssentials
                val financialStress = maxOf(
                    1.0 - agent.savings / plan.financialBuffer,
                    unmetEssentials / plan.essentialDailyCost,
                ).coerceIn(0.0, 1.0)
                if (financialStress >= 0.5) stressedAgents += 1
                val mediaToday = (
                    plan.behavior.mediaBaseMinutes +
                        agent.interest * plan.behavior.mediaInterestMinutes +
                        financialStress * plan.behavior.mediaStressMinutes
                    ).toInt()
                val leisureToday = (1440 - 480 - 180 - workToday - mediaToday).coerceAtLeast(0)
                agent.workMinutes += workToday
                agent.mediaMinutes += mediaToday
                agent.leisureMinutes += leisureToday
                workMinutes += workToday
                mediaMinutes += mediaToday
                leisureMinutes += leisureToday

                val left = previousBeliefs[(index - 1 + agents.size) % agents.size]
                val right = previousBeliefs[(index + 1) % agents.size]
                val neighborBelief = (left + right) / 2.0
                val mediaExposureFactor = (
                    (1.0 - plan.behavior.mediaExposureWeight) +
                        plan.behavior.mediaExposureWeight * mediaToday / plan.behavior.mediaExposureReferenceMinutes
                    ).coerceIn(0.0, 2.0)
                val falseProbability = if (day >= plan.campaignStartDay) {
                    (
                        plan.baseFalseReach +
                            plan.behavior.neighborReachWeight * neighborBelief +
                            plan.behavior.interestReachWeight * agent.interest
                        ) * mediaExposureFactor * (1.0 - policy.sharingFriction)
                } else 0.0
                val correctionProbability = if (day >= plan.correctionStartDay) {
                    plan.correctionReach * trustFactor(agent.institutionalTrust, plan.behavior.correctionTrustWeight) *
                        mediaExposureFactor * (1.0 - policy.sharingFriction)
                } else 0.0
                val neutralProbability = plan.behavior.neutralReach * mediaExposureFactor * (1.0 - policy.sharingFriction)
                val falseExposure = eventRandom.nextDouble() < falseProbability.coerceIn(0.0, plan.behavior.maximumExposureProbability)
                val correctionExposure = eventRandom.nextDouble() < correctionProbability.coerceIn(0.0, plan.behavior.maximumExposureProbability)
                val neutralExposure = eventRandom.nextDouble() < neutralProbability.coerceIn(0.0, plan.behavior.maximumExposureProbability)
                val reshareDraw = eventRandom.nextDouble()

                if (falseExposure) {
                    falseExposures += 1
                    agent.falseExposures += 1
                }
                if (correctionExposure) {
                    correctionExposures += 1
                    agent.correctionExposures += 1
                }
                if (neutralExposure) {
                    neutralExposures += 1
                    agent.neutralExposures += 1
                }
                agent.falseBelief = updateBelief(
                    model = plan.beliefModel,
                    behavior = plan.behavior,
                    policy = policy,
                    agent = agent,
                    neighborBelief = neighborBelief,
                    financialStress = financialStress,
                    arousal = plan.campaignArousal,
                    falseExposure = falseExposure,
                    correctionExposure = correctionExposure,
                )
                if (falseExposure && reshareDraw < agent.falseBelief * plan.campaignArousal * (1.0 - policy.sharingFriction)) {
                    falseReshares += 1
                    agent.falseReshares += 1
                }
            }

            val prevalence = agents.count { it.falseBelief >= plan.behavior.adoptionThreshold }.toDouble() / agents.size
            peakFalseBeliefPrevalence = maxOf(peakFalseBeliefPrevalence, prevalence)
            totalNeutralExposures += neutralExposures
            totalFalseReshares += falseReshares
            daily += DailyMetrics(
                day = day,
                meanFalseBelief = agents.map { it.falseBelief }.average(),
                falseBeliefPrevalence = prevalence,
                falseExposures = falseExposures,
                correctionExposures = correctionExposures,
                neutralExposures = neutralExposures,
                falseReshares = falseReshares,
                economicallyStressedFraction = stressedAgents.toDouble() / agents.size,
                workMinutes = workMinutes,
                mediaMinutes = mediaMinutes,
                leisureMinutes = leisureMinutes,
                meanSavings = agents.map { it.savings }.average(),
                cumulativeUnmetEssentials = agents.sumOf { it.unmetEssentials },
            )
        }

        val lowResourceAgents = agents.filter { it.lowResource }
        return SimulationResult(
            daily = daily,
            agents = agents,
            summary = ScenarioSummary(
                finalMeanFalseBelief = agents.map { it.falseBelief }.average(),
                finalFalseBeliefPrevalence = agents.count { it.falseBelief >= plan.behavior.adoptionThreshold }.toDouble() / agents.size,
                peakFalseBeliefPrevalence = peakFalseBeliefPrevalence,
                lowResourceFinalMeanFalseBelief = lowResourceAgents.map { it.falseBelief }.average(),
                lowResourceFinalFalseBeliefPrevalence = lowResourceAgents.count { it.falseBelief >= plan.behavior.adoptionThreshold }.toDouble() / lowResourceAgents.size,
                neutralExposures = totalNeutralExposures,
                falseReshares = totalFalseReshares,
            ),
        )
    }

    private fun updateBelief(
        model: String,
        behavior: SocietyBehavior,
        policy: SocietyPolicy,
        agent: AgentState,
        neighborBelief: Double,
        financialStress: Double,
        arousal: Double,
        falseExposure: Boolean,
        correctionExposure: Boolean,
    ): Double {
        val labelResistance = policy.provenanceLabelEffectiveness * (
            (1.0 - behavior.labelLiteracyWeight) + behavior.labelLiteracyWeight * agent.mediaLiteracy
            )
        val falseEffect = (
            behavior.persuasionBase +
                behavior.persuasionSusceptibilityWeight * agent.susceptibility +
                behavior.persuasionStressWeight * financialStress
            ) * arousal * (1.0 - labelResistance.coerceIn(0.0, 1.0))
        val correctionEffect = (
            behavior.correctionBase + behavior.correctionLiteracyWeight * agent.mediaLiteracy
            ) * trustFactor(agent.institutionalTrust, behavior.correctionTrustWeight)
        return when (model) {
            "linear-persuasion" -> {
                var belief = agent.falseBelief
                if (falseExposure) belief += (1.0 - belief) * falseEffect
                if (correctionExposure) belief -= belief * correctionEffect
                belief += (neighborBelief - belief) * agent.socialConformity * behavior.socialUpdateRate
                belief.coerceIn(0.0, 1.0)
            }
            "evidence-accumulation" -> {
                val boundedBelief = agent.falseBelief.coerceIn(0.000_001, 0.999_999)
                var logOdds = ln(boundedBelief / (1.0 - boundedBelief))
                if (falseExposure) logOdds += falseEffect * behavior.evidenceFalseScale
                if (correctionExposure) logOdds -= correctionEffect * behavior.evidenceCorrectionScale
                logOdds += (neighborBelief - agent.falseBelief) * agent.socialConformity *
                    behavior.socialUpdateRate * behavior.evidenceSocialScale
                (1.0 / (1.0 + exp(-logOdds))).coerceIn(0.0, 1.0)
            }
            else -> error("unknown belief model: $model")
        }
    }

    private fun trustFactor(trust: Double, weight: Double) = (1.0 - weight) + weight * trust

    private fun validate(plan: SocietyExperimentPlan) {
        require(plan.population >= 8) { "world.population must be at least 8" }
        require(plan.id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "plan id must be lowercase and path-safe" }
        require(plan.version.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "plan version must be lowercase and path-safe" }
        require(plan.question.isNotBlank() && plan.scope.isNotBlank()) { "question and scope must not be blank" }
        require(plan.beliefModel in setOf("linear-persuasion", "evidence-accumulation")) { "belief_model must be linear-persuasion or evidence-accumulation" }
        require(plan.days >= 2) { "world.days must be at least 2" }
        require(plan.replications >= 1) { "world.replications must be at least 1" }
        require(plan.campaignStartDay in 1..plan.days) { "campaign.start_day must fall within the simulation" }
        require(plan.correctionStartDay in plan.campaignStartDay..plan.days) { "campaign.correction_start_day must not precede the campaign" }
        require(plan.shockStartDay in 1..plan.days && plan.shockEndDay in plan.shockStartDay..plan.days) { "economy shock days must fall within the simulation" }
        require(plan.essentialDailyCost > 0.0 && plan.financialBuffer > 0.0) { "economic costs and buffer must be positive" }
        requireProbability("campaign.base_false_reach", plan.baseFalseReach)
        requireProbability("campaign.correction_reach", plan.correctionReach)
        requireProbability("campaign.arousal", plan.campaignArousal)
        requireProbability("economy.shock_income_multiplier", plan.shockIncomeMultiplier)
        requireProbability("success_criteria.minimum_final_false_belief_score_reduction", plan.minimumFalseBeliefScoreReduction)
        requireProbability("success_criteria.minimum_low_resource_false_belief_score_reduction", plan.minimumLowResourceScoreReduction)
        requireProbability("success_criteria.minimum_neutral_reach_retention", plan.minimumNeutralReachRetention)
        with(plan.behavior) {
            mapOf(
                "neutral_reach" to neutralReach,
                "maximum_exposure_probability" to maximumExposureProbability,
                "neighbor_reach_weight" to neighborReachWeight,
                "interest_reach_weight" to interestReachWeight,
                "media_exposure_weight" to mediaExposureWeight,
                "label_literacy_weight" to labelLiteracyWeight,
                "persuasion_base" to persuasionBase,
                "persuasion_susceptibility_weight" to persuasionSusceptibilityWeight,
                "persuasion_stress_weight" to persuasionStressWeight,
                "correction_base" to correctionBase,
                "correction_literacy_weight" to correctionLiteracyWeight,
                "correction_trust_weight" to correctionTrustWeight,
                "social_update_rate" to socialUpdateRate,
                "adoption_threshold" to adoptionThreshold,
            ).forEach { (name, value) -> requireProbability("behavior.$name", value) }
            mapOf(
                "media_base_minutes" to mediaBaseMinutes,
                "media_interest_minutes" to mediaInterestMinutes,
                "media_stress_minutes" to mediaStressMinutes,
                "media_exposure_reference_minutes" to mediaExposureReferenceMinutes,
                "evidence_false_scale" to evidenceFalseScale,
                "evidence_correction_scale" to evidenceCorrectionScale,
                "evidence_social_scale" to evidenceSocialScale,
            ).forEach { (name, value) -> require(value > 0.0) { "behavior.$name must be positive" } }
        }
        listOf(plan.baseline, plan.intervention).forEach { policy ->
            require(policy.id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "policy id must be lowercase and path-safe: ${policy.id}" }
            requireProbability("${policy.id}.provenance_label_effectiveness", policy.provenanceLabelEffectiveness)
            requireProbability("${policy.id}.sharing_friction", policy.sharingFriction)
        }
        require(plan.baseline.id != plan.intervention.id) { "baseline and intervention policy ids must differ" }
    }

    private fun requireProbability(name: String, value: Double) = require(value in 0.0..1.0) { "$name must be between 0 and 1" }
    private fun JsonObject.toPolicy() = SocietyPolicy(requiredString("id"), requiredDouble("provenance_label_effectiveness"), requiredDouble("sharing_friction"))
    private fun JsonObject.toBehavior() = SocietyBehavior(
        neutralReach = requiredDouble("neutral_reach"),
        maximumExposureProbability = requiredDouble("maximum_exposure_probability"),
        neighborReachWeight = requiredDouble("neighbor_reach_weight"),
        interestReachWeight = requiredDouble("interest_reach_weight"),
        mediaBaseMinutes = requiredDouble("media_base_minutes"),
        mediaInterestMinutes = requiredDouble("media_interest_minutes"),
        mediaStressMinutes = requiredDouble("media_stress_minutes"),
        mediaExposureReferenceMinutes = requiredDouble("media_exposure_reference_minutes"),
        mediaExposureWeight = requiredDouble("media_exposure_weight"),
        labelLiteracyWeight = requiredDouble("label_literacy_weight"),
        persuasionBase = requiredDouble("persuasion_base"),
        persuasionSusceptibilityWeight = requiredDouble("persuasion_susceptibility_weight"),
        persuasionStressWeight = requiredDouble("persuasion_stress_weight"),
        correctionBase = requiredDouble("correction_base"),
        correctionLiteracyWeight = requiredDouble("correction_literacy_weight"),
        correctionTrustWeight = requiredDouble("correction_trust_weight"),
        socialUpdateRate = requiredDouble("social_update_rate"),
        adoptionThreshold = requiredDouble("adoption_threshold"),
        evidenceFalseScale = requiredDouble("evidence_false_scale"),
        evidenceCorrectionScale = requiredDouble("evidence_correction_scale"),
        evidenceSocialScale = requiredDouble("evidence_social_scale"),
    )
    private fun JsonObject.requiredObject(name: String) = getValue(name).jsonObject
    private fun JsonObject.requiredString(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.requiredInt(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.requiredDouble(name: String) = getValue(name).jsonPrimitive.double
    private fun java.io.BufferedWriter.writeLine(value: JsonObject) { write(compactJson.encodeToString(JsonObject.serializer(), value)); newLine() }
    private fun confidenceInterval(values: List<Double>): Interval {
        val mean = values.average()
        if (values.size == 1) return Interval(mean, mean, mean)
        val variance = values.sumOf { (it - mean).pow(2) } / (values.size - 1)
        val margin = 1.96 * sqrt(variance / values.size)
        return Interval(mean, mean - margin, mean + margin)
    }
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

    private data class Evidence(val name: String, val path: Path, val records: Int) { val hash = sha256(path) }
    private data class Interval(val mean: Double, val lower: Double, val upper: Double)
    private data class SimulationResult(val daily: List<DailyMetrics>, val agents: List<AgentState>, val summary: ScenarioSummary)
    private data class ScenarioSummary(
        val finalMeanFalseBelief: Double,
        val finalFalseBeliefPrevalence: Double,
        val peakFalseBeliefPrevalence: Double,
        val lowResourceFinalMeanFalseBelief: Double,
        val lowResourceFinalFalseBeliefPrevalence: Double,
        val neutralExposures: Int,
        val falseReshares: Int,
    )

    private data class PairedSummary(val replication: Int, val baseline: ScenarioSummary, val intervention: ScenarioSummary) {
        val falseBeliefReduction = baseline.finalMeanFalseBelief - intervention.finalMeanFalseBelief
        val lowResourceFalseBeliefReduction = baseline.lowResourceFinalMeanFalseBelief - intervention.lowResourceFinalMeanFalseBelief
        val neutralReachRetention = intervention.neutralExposures.toDouble() / baseline.neutralExposures.coerceAtLeast(1)
        val falseReshareReduction = (baseline.falseReshares - intervention.falseReshares).toDouble() / baseline.falseReshares.coerceAtLeast(1)
        fun toJson() = buildJsonObject {
            put("replication", replication)
            putJsonObject("baseline") { put("final_mean_false_belief", baseline.finalMeanFalseBelief); put("final_false_belief_prevalence", baseline.finalFalseBeliefPrevalence); put("peak_false_belief_prevalence", baseline.peakFalseBeliefPrevalence); put("low_resource_final_mean_false_belief", baseline.lowResourceFinalMeanFalseBelief); put("low_resource_final_false_belief_prevalence", baseline.lowResourceFinalFalseBeliefPrevalence); put("neutral_exposures", baseline.neutralExposures); put("false_reshares", baseline.falseReshares) }
            putJsonObject("intervention") { put("final_mean_false_belief", intervention.finalMeanFalseBelief); put("final_false_belief_prevalence", intervention.finalFalseBeliefPrevalence); put("peak_false_belief_prevalence", intervention.peakFalseBeliefPrevalence); put("low_resource_final_mean_false_belief", intervention.lowResourceFinalMeanFalseBelief); put("low_resource_final_false_belief_prevalence", intervention.lowResourceFinalFalseBeliefPrevalence); put("neutral_exposures", intervention.neutralExposures); put("false_reshares", intervention.falseReshares) }
            putJsonObject("paired_effects") { put("final_false_belief_score_reduction", falseBeliefReduction); put("low_resource_false_belief_score_reduction", lowResourceFalseBeliefReduction); put("neutral_reach_retention", neutralReachRetention); put("false_reshare_reduction", falseReshareReduction) }
        }
    }

    private data class DailyMetrics(
        val day: Int,
        val meanFalseBelief: Double,
        val falseBeliefPrevalence: Double,
        val falseExposures: Int,
        val correctionExposures: Int,
        val neutralExposures: Int,
        val falseReshares: Int,
        val economicallyStressedFraction: Double,
        val workMinutes: Int,
        val mediaMinutes: Int,
        val leisureMinutes: Int,
        val meanSavings: Double,
        val cumulativeUnmetEssentials: Double,
    ) {
        fun toJson(replication: Int, policy: String) = buildJsonObject {
            put("replication", replication); put("policy", policy); put("day", day)
            put("mean_false_belief", meanFalseBelief); put("false_belief_prevalence", falseBeliefPrevalence)
            put("false_exposures", falseExposures); put("correction_exposures", correctionExposures)
            put("neutral_exposures", neutralExposures); put("false_reshares", falseReshares)
            put("economically_stressed_fraction", economicallyStressedFraction)
            put("work_minutes", workMinutes); put("media_minutes", mediaMinutes); put("leisure_minutes", leisureMinutes)
            put("mean_savings", meanSavings); put("cumulative_unmet_essentials", cumulativeUnmetEssentials)
        }
    }

    private data class AgentState(
        val id: Int,
        val lowResource: Boolean,
        val employed: Boolean,
        val dailyIncome: Double,
        val initialSavings: Double,
        var savings: Double,
        val interest: Double,
        val mediaLiteracy: Double,
        val susceptibility: Double,
        val institutionalTrust: Double,
        val socialConformity: Double,
        var falseBelief: Double,
        var unmetEssentials: Double = 0.0,
        var workMinutes: Int = 0,
        var mediaMinutes: Int = 0,
        var leisureMinutes: Int = 0,
        var falseExposures: Int = 0,
        var correctionExposures: Int = 0,
        var neutralExposures: Int = 0,
        var falseReshares: Int = 0,
    ) {
        fun toJson(replication: Int, policy: String, adoptionThreshold: Double) = buildJsonObject {
            put("replication", replication); put("policy", policy); put("agent_id", id); put("low_resource", lowResource)
            put("employed", employed); put("daily_income", dailyIncome); put("initial_savings", initialSavings); put("final_savings", savings); put("unmet_essentials", unmetEssentials)
            put("interest", interest); put("media_literacy", mediaLiteracy); put("susceptibility", susceptibility)
            put("institutional_trust", institutionalTrust); put("social_conformity", socialConformity)
            put("final_false_belief", falseBelief); put("false_belief_adopted", falseBelief >= adoptionThreshold)
            put("false_exposures", falseExposures); put("correction_exposures", correctionExposures)
            put("neutral_exposures", neutralExposures); put("false_reshares", falseReshares)
            put("work_minutes", workMinutes); put("media_minutes", mediaMinutes); put("leisure_minutes", leisureMinutes)
        }

        companion object {
            fun create(id: Int, random: Random): AgentState {
                val resourceDraw = random.nextDouble()
                val lowResource = id % 4 == 0
                val resourcePosition = if (lowResource) resourceDraw * 0.25 else 0.25 + resourceDraw * 0.75
                val initialSavings = 40.0 + resourcePosition * 960.0
                return AgentState(
                    id = id,
                    lowResource = lowResource,
                    employed = random.nextDouble() < 0.86,
                    dailyIncome = 78.0 + random.nextDouble() * 74.0,
                    initialSavings = initialSavings,
                    savings = initialSavings,
                    interest = 0.1 + random.nextDouble() * 0.9,
                    mediaLiteracy = 0.1 + random.nextDouble() * 0.8,
                    susceptibility = 0.1 + random.nextDouble() * 0.8,
                    institutionalTrust = 0.15 + random.nextDouble() * 0.7,
                    socialConformity = 0.1 + random.nextDouble() * 0.8,
                    falseBelief = 0.02 + random.nextDouble() * 0.12,
                )
            }
        }
    }
}
