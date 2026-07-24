package org.valudus.governance

import java.nio.file.Path
import kotlin.io.path.Path

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) { "usage: generate --output PATH [--cases N] [--seed N] | run-reference --manifest PATH --output PATH" }
    val options = arguments.drop(1).chunked(2).associate { (key, value) -> key to value }
    when (arguments[0]) {
        "generate" -> {
            val output: Path = Path(requireNotNull(options["--output"]) { "--output is required" })
            val cases = options["--cases"]?.toInt() ?: 120
            val seed = options["--seed"]?.toInt() ?: 20_260_724
            GovernanceHarness.writeManifest(output, cases, seed)
            println("WROTE: $output ($cases fixtures, seed $seed)")
        }
        "run-reference" -> {
            val manifest = Path(requireNotNull(options["--manifest"]) { "--manifest is required" })
            val output = Path(requireNotNull(options["--output"]) { "--output is required" })
            val report = GovernancePipeline.runReference(manifest, output)
            println("${report["status"]}: $output/run-report.json")
        }
        else -> error("unknown command: ${arguments[0]}")
    }
}
