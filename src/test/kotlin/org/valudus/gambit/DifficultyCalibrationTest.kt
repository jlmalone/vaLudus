package org.valudus.gambit

import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DifficultyCalibrationTest {
    private val root = Path(System.getProperty("user.dir"))
    private val planPath = root.resolve("domains/gambit/benchmarks/difficulty-handicap/PLAN.json")

    @Test fun `plan keeps material odds sound and bounded`() {
        val plan = DifficultyCalibration.readPlan(planPath)
        assertEquals(13, plan.size)
        assertTrue(plan.values.filter { it.handicap != "none" }.all { it.randomChance == 0.0 && it.engineElo >= 1320 })
    }

    @Test fun `small samples do not issue a tuning command`() {
        val report = DifficultyCalibration.summarize(DifficultyCalibration.readPlan(planPath), DifficultyCalibration.readResults(root.resolve("domains/gambit/benchmarks/difficulty-handicap/sample-results.jsonl")))
        val beginner = report["configurations"]!!.jsonArray.first { it.jsonObject["configuration_id"]!!.jsonPrimitive.content == "beginner-1" }.jsonObject
        assertEquals("collect_more_data", beginner["recommendation"]!!.jsonPrimitive.content)
        assertEquals(0, beginner["suggested_engine_elo_delta"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun `sustained overperformance recommends a stronger engine`() {
        val plan = mapOf("test" to DifficultyConfig("test", 0.5, 1600, 0.0, "none"))
        val report = DifficultyCalibration.summarize(plan, List(12) { GameResult("test", 1.0, 40, 100.0) })
        val entry = report["configurations"]!!.jsonArray.single().jsonObject
        assertEquals("raise_engine_strength", entry["recommendation"]!!.jsonPrimitive.content)
        assertTrue(entry["suggested_engine_elo_delta"]!!.jsonPrimitive.content.toInt() > 0)
    }

    @Test fun `schedule is deterministic and preserves paired colors`() {
        val directory = createTempDirectory("difficulty-schedule-")
        val firstPath = directory.resolve("first.json")
        val secondPath = directory.resolve("second.json")
        val first = DifficultyCalibration.writeSchedule(planPath, 8128, firstPath)
        val second = DifficultyCalibration.writeSchedule(planPath, 8128, secondPath)

        assertEquals(firstPath.readText(), secondPath.readText())
        assertEquals(156, first["games"]!!.jsonArray.size)
        first["games"]!!.jsonArray.groupBy { it.jsonObject["pair_id"]!!.jsonPrimitive.content }.values.forEach { pair ->
            assertEquals(setOf("white", "black"), pair.map { it.jsonObject["player_color"]!!.jsonPrimitive.content }.toSet())
            assertTrue(pair.all { it.jsonObject["max_plies"]!!.jsonPrimitive.int == 160 })
        }
    }
}
