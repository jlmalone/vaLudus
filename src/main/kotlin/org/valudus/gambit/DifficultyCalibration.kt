package org.valudus.gambit

import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.serialization.json.*

data class DifficultyConfig(val id: String, val targetScore: Double, val engineElo: Int, val randomChance: Double, val handicap: String)
data class GameResult(val configId: String, val playerScore: Double, val plies: Int, val decisionMs: Double)
data class CalibrationExecution(val gamesPerConfiguration: Int, val maxPlies: Int, val maxMoveTimeMs: Int)

/** Aggregates bounded, local game records. It never launches an engine or makes network calls. */
object DifficultyCalibration {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    fun readPlan(path: Path): Map<String, DifficultyConfig> {
        val root = readPlanRoot(path)
        return root["configurations"]!!.jsonArray.associate { element ->
            val config = element.jsonObject
            val id = config["id"]!!.jsonPrimitive.content
            id to DifficultyConfig(id, config["target_player_score"]!!.jsonPrimitive.double, config["engine_elo"]!!.jsonPrimitive.int, config["random_move_chance"]!!.jsonPrimitive.double, config["handicap"]!!.jsonPrimitive.content)
        }
    }

    /** Creates a fixed, paired color schedule before games are run. */
    fun writeSchedule(planPath: Path, seed: Long, outputPath: Path): JsonObject {
        require(!outputPath.exists()) { "refusing to overwrite existing output: $outputPath" }
        val root = readPlanRoot(planPath)
        val plan = readPlan(planPath)
        val execution = readExecution(root)
        require(execution.gamesPerConfiguration % 2 == 0) { "execution.games_per_configuration must be even for paired colors" }
        val games = mutableListOf<JsonObject>()
        plan.values.sortedBy { it.id }.forEach { config ->
            repeat(execution.gamesPerConfiguration / 2) { pairIndex ->
                val pairId = "${config.id}-pair-${(pairIndex + 1).toString().padStart(2, '0')}"
                listOf("white", "black").forEachIndexed { colorIndex, playerColor ->
                    games += buildJsonObject {
                        put("game_id", "${config.id}-${(pairIndex * 2 + colorIndex + 1).toString().padStart(2, '0')}")
                        put("configuration_id", config.id)
                        put("pair_id", pairId)
                        put("player_color", playerColor)
                        put("max_plies", execution.maxPlies)
                        put("max_move_time_ms", execution.maxMoveTimeMs)
                    }
                }
            }
        }
        games.shuffle(Random(seed))
        val schedule = buildJsonObject {
            put("schema_version", "1.0")
            put("purpose", "Deterministic paired-color difficulty and handicap calibration schedule")
            put("seed", seed)
            put("plan_sha256", sha256(planPath.readText()))
            putJsonObject("budget") { put("hosted_calls", 0); put("tokens", 0); put("money_usd", 0.0) }
            putJsonArray("games") { games.forEach { add(it) } }
        }
        outputPath.parent?.toFile()?.mkdirs()
        outputPath.writeText(json.encodeToString(JsonObject.serializer(), schedule) + "\n")
        return schedule
    }

    fun readResults(path: Path): List<GameResult> = path.readLines().filter { it.isNotBlank() }.mapIndexed { index, line ->
        val value = json.parseToJsonElement(line).jsonObject
        val score = value["player_score"]!!.jsonPrimitive.double
        require(score in setOf(0.0, 0.5, 1.0)) { "results line ${index + 1}: player_score must be 0, 0.5, or 1" }
        GameResult(value["configuration_id"]!!.jsonPrimitive.content, score, value["plies"]!!.jsonPrimitive.int, value["mean_decision_ms"]!!.jsonPrimitive.double)
    }

    fun summarize(plan: Map<String, DifficultyConfig>, results: List<GameResult>): JsonObject {
        val unknown = results.map { it.configId }.toSet() - plan.keys
        require(unknown.isEmpty()) { "results name unknown configurations: ${unknown.sorted().joinToString(", ")}" }
        return buildJsonObject {
            put("schema_version", "1.0")
            put("purpose", "Difficulty and material-handicap calibration summary")
            put("hosted_calls", 0); put("tokens", 0); put("money_usd", 0.0)
            putJsonArray("configurations") {
                plan.values.sortedBy { it.id }.forEach { config -> add(summary(config, results.filter { it.configId == config.id })) }
            }
        }
    }

    fun writeSummary(planPath: Path, resultsPath: Path, outputPath: Path): JsonObject {
        require(!outputPath.exists()) { "refusing to overwrite existing output: $outputPath" }
        val report = summarize(readPlan(planPath), readResults(resultsPath))
        outputPath.parent?.toFile()?.mkdirs()
        outputPath.writeText(json.encodeToString(JsonObject.serializer(), report) + "\n")
        return report
    }

    private fun summary(config: DifficultyConfig, games: List<GameResult>): JsonObject {
        val scores = games.map { it.playerScore }
        val count = games.size
        val mean = if (count == 0) 0.0 else scores.average()
        val variance = if (count < 2) 0.0 else scores.sumOf { (it - mean) * (it - mean) } / (count - 1)
        val error95 = if (count < 2) null else 1.96 * sqrt(variance / count)
        val delta = mean - config.targetScore
        val recommendation = when {
            count < 12 -> "collect_more_data"
            error95 != null && kotlin.math.abs(delta) <= maxOf(0.05, error95) -> "hold"
            delta > 0 -> "raise_engine_strength"
            else -> "lower_engine_strength"
        }
        val suggestedDelta = if (recommendation == "raise_engine_strength" || recommendation == "lower_engine_strength") kotlin.math.round(delta.coerceIn(-0.4, 0.4) * 200).toInt() else 0
        return buildJsonObject {
            put("configuration_id", config.id); put("handicap", config.handicap); put("engine_elo", config.engineElo); put("random_move_chance", config.randomChance); put("target_player_score", config.targetScore)
            put("games", count); put("wins", scores.count { it == 1.0 }); put("draws", scores.count { it == 0.5 }); put("losses", scores.count { it == 0.0 }); put("player_score", mean)
            if (error95 == null) put("score_error_95", JsonNull) else put("score_error_95", error95)
            put("mean_plies", if (count == 0) 0.0 else games.map { it.plies }.average()); put("mean_decision_ms", if (count == 0) 0.0 else games.map { it.decisionMs }.average())
            put("recommendation", recommendation); put("suggested_engine_elo_delta", suggestedDelta)
        }
    }

    private fun readPlanRoot(path: Path): JsonObject = json.parseToJsonElement(path.readText()).jsonObject

    private fun readExecution(root: JsonObject): CalibrationExecution {
        val execution = root["execution"]?.jsonObject ?: error("plan requires execution")
        val games = execution["games_per_configuration"]?.jsonPrimitive?.int ?: error("execution.games_per_configuration is required")
        val maxPlies = execution["max_plies"]?.jsonPrimitive?.int ?: error("execution.max_plies is required")
        val maxMoveTimeMs = execution["max_move_time_ms"]?.jsonPrimitive?.int ?: error("execution.max_move_time_ms is required")
        require(games > 0 && maxPlies > 0 && maxMoveTimeMs > 0) { "execution limits must be positive" }
        return CalibrationExecution(games, maxPlies, maxMoveTimeMs)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
