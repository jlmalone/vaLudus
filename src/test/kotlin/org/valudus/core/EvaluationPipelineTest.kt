package org.valudus.core

import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EvaluationPipelineTest {
    private val root = Path(System.getProperty("user.dir"))

    @Test fun `public manifests validate`() {
        val manifest = EvaluationPipeline.readObject(root.resolve("examples/minimal-benchmark.json"))
        assertTrue(ContractValidator.validateBenchmark(manifest).isEmpty())
        val run = EvaluationPipeline.readObject(root.resolve("examples/minimal-run.json"))
        assertTrue(ContractValidator.validateRun(run).isEmpty())
    }

    @Test fun `sum reference run writes valid evidence first`() {
        val output = createTempDirectory("valudus-sum").resolve("run")
        val report = EvaluationPipeline.runReference(root.resolve("examples/minimal-benchmark.json"), "reference-sum", output, "reference-system", "0.1.0", "deterministic reference configuration")
        assertEquals("valid", report["status"]!!.jsonPrimitive.content)
        assertEquals("passed", report["outcome"]!!.jsonPrimitive.content)
        assertTrue(output.resolve("evidence.jsonl").toFile().isFile)
        assertTrue(ContractValidator.validateRun(EvaluationPipeline.readObject(output.resolve("run-report.json"))).isEmpty())
    }

    @Test fun `gambit reference adapter passes published rules`() {
        val output = createTempDirectory("valudus-gambit").resolve("run")
        val report = EvaluationPipeline.runReference(root.resolve("benchmarks/gambit-siamese-capablanca/manifest.json"), "gambit-siamese-capablanca-reference", output, "reference-rule-implementation", "0.1.0", "pinned public routing rules", "exact")
        assertEquals(1.0, report["metrics"]!!.jsonObject["exact_match_rate"]!!.jsonPrimitive.content.toDouble())
        assertEquals("passed", report["outcome"]!!.jsonPrimitive.content)
    }

    @Test fun `chess helpers retain material and UCI measurements`() {
        assertEquals(4_790, materialBalance("10/10/10/10/10/10/10/RNBAQKCBNR[] w KQkq - 0 1").whiteCentipawns)
        val info = parseUciInfo("info depth 12 seldepth 16 score cp -43 nodes 12345 nps 456789 pv a2a3 a7a6")!!
        assertEquals(-43, info.scoreValue)
        assertEquals(12_345, info.nodes)
        assertEquals("a2a3 a7a6", info.pv)
    }
}
