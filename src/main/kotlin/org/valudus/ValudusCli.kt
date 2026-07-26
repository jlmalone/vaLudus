package org.valudus

import java.nio.file.Path
import kotlin.io.path.Path
import kotlinx.serialization.json.jsonArray
import org.valudus.core.ContractValidator
import org.valudus.core.EvaluationPipeline
import org.valudus.core.UciEngine
import org.valudus.gambit.DifficultyCalibration
import org.valudus.governance.main as governanceMain

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) { "usage: validate-benchmark PATH | validate-run PATH | run-reference ... | probe-engine ... | schedule-difficulty ... | summarize-difficulty ... | generate ..." }
    if (arguments[0] == "generate") return governanceMain(arguments)
    when (arguments[0]) {
        "validate-benchmark", "validate-run" -> {
            require(arguments.size == 2) { "usage: ${arguments[0]} PATH" }
            val errors = if (arguments[0] == "validate-benchmark") ContractValidator.validateBenchmark(EvaluationPipeline.readObject(Path(arguments[1]))) else ContractValidator.validateRun(EvaluationPipeline.readObject(Path(arguments[1])))
            if (errors.isEmpty()) println("VALID") else { println("INVALID"); errors.forEach { println("- $it") }; error("artifact validation failed") }
        }
        "run-reference" -> {
            val options = arguments.drop(1).chunked(2).associate { require(it.size == 2) { "missing option value" }; it[0] to it[1] }
            val report = EvaluationPipeline.runReference(Path(required(options, "--manifest")), required(options, "--adapter"), Path(required(options, "--output")), required(options, "--system-name"), required(options, "--system-version"), required(options, "--configuration"), options["--reproducibility-tier"] ?: "procedural")
            println("${report["status"]}: ${required(options, "--output")}/run-report.json")
        }
        "probe-engine" -> {
            val options = arguments.drop(1).chunked(2).associate { require(it.size == 2) { "missing option value" }; it[0] to it[1] }
            UciEngine(Path(required(options, "--engine")), options["--timeout-seconds"]?.toDouble() ?: 10.0).start().use { engine ->
                val result = engine.analyse(required(options, "--fen"), required(options, "--variant"), required(options, "--depth").toInt())
                println("{\"bestmove\":\"${result.bestmove}\",\"elapsed_seconds\":${result.elapsedSeconds}}")
            }
        }
        "summarize-difficulty" -> {
            val options = arguments.drop(1).chunked(2).associate { require(it.size == 2) { "missing option value" }; it[0] to it[1] }
            val report = DifficultyCalibration.writeSummary(Path(required(options, "--plan")), Path(required(options, "--results")), Path(required(options, "--output")))
            println("WROTE: ${required(options, "--output")} (${report["configurations"]!!.jsonArray.size} configurations)")
        }
        "schedule-difficulty" -> {
            val options = arguments.drop(1).chunked(2).associate { require(it.size == 2) { "missing option value" }; it[0] to it[1] }
            val schedule = DifficultyCalibration.writeSchedule(Path(required(options, "--plan")), required(options, "--seed").toLong(), Path(required(options, "--output")))
            println("WROTE: ${required(options, "--output")} (${schedule["games"]!!.jsonArray.size} games)")
        }
        else -> error("unknown command: ${arguments[0]}")
    }
}

private fun required(options: Map<String, String>, name: String) = requireNotNull(options[name]) { "$name is required" }
