import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from valudus.runner import RunnerError, run_benchmark
from valudus.validation import validate_artifact


ROOT = Path(__file__).resolve().parents[1]


class ValidationTests(unittest.TestCase):
    def test_reference_benchmark_is_valid(self) -> None:
        self.assertEqual([], validate_artifact(ROOT / "examples/minimal-benchmark.json", "benchmark"))

    def test_reference_run_is_valid(self) -> None:
        self.assertEqual([], validate_artifact(ROOT / "examples/minimal-run.json", "run"))

    def test_missing_failure_cases_is_rejected(self) -> None:
        payload = json.loads((ROOT / "examples/minimal-benchmark.json").read_text())
        del payload["failure_cases"]
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json") as artifact:
            json.dump(payload, artifact)
            artifact.flush()
            errors = validate_artifact(Path(artifact.name), "benchmark")
        self.assertTrue(any("failure_cases" in error for error in errors))

    def test_runner_writes_hashed_evidence_and_valid_report(self) -> None:
        output = Path(tempfile.mkdtemp()) / "run"
        try:
            report = run_benchmark(
                ROOT / "examples/minimal-benchmark.json",
                "valudus.reference_adapter:evaluate",
                output,
                "reference-system",
                "0.1.0",
                "deterministic reference configuration",
            )
            self.assertEqual("valid", report["status"])
            self.assertEqual("procedural", report["reproducibility_tier"])
            self.assertEqual(1.0, report["metrics"]["exact_match_rate"])
            self.assertTrue((output / "evidence.jsonl").is_file())
            self.assertEqual([], validate_artifact(output / "run-report.json", "run"))
        finally:
            shutil.rmtree(output.parent)

    def test_runner_refuses_to_overwrite_existing_evidence(self) -> None:
        output = Path(tempfile.mkdtemp()) / "run"
        output.mkdir()
        try:
            with self.assertRaises(RunnerError):
                run_benchmark(
                    ROOT / "examples/minimal-benchmark.json",
                    "valudus.reference_adapter:evaluate",
                    output,
                    "reference-system",
                    "0.1.0",
                    "deterministic reference configuration",
                )
        finally:
            shutil.rmtree(output.parent)

    def test_adapter_failure_is_preserved_as_invalid_evidence(self) -> None:
        output = Path(tempfile.mkdtemp()) / "run"

        def failing_adapter(_: dict) -> dict:
            raise RuntimeError("deliberate adapter failure")

        try:
            with patch("valudus.runner.load_adapter", return_value=failing_adapter):
                report = run_benchmark(
                    ROOT / "examples/minimal-benchmark.json",
                    "ignored:adapter",
                    output,
                    "candidate-system",
                    "0.1.0",
                    "failure-path test configuration",
                )
            self.assertEqual("invalid", report["status"])
            self.assertIn("RuntimeError", report["failure_reasons"][0])
            self.assertIn("adapter_error", (output / "evidence.jsonl").read_text())
        finally:
            shutil.rmtree(output.parent)

    def test_unobserved_required_cost_invalidates_run(self) -> None:
        manifest = json.loads((ROOT / "examples/minimal-benchmark.json").read_text())
        manifest["budget"]["tokens"]["maximum"] = 10
        output = Path(tempfile.mkdtemp()) / "run"
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json") as artifact:
            json.dump(manifest, artifact)
            artifact.flush()

            def unmetered_adapter(input_data: dict) -> dict:
                return {
                    "output": {"sum": sum(input_data["operands"])},
                    "usage": {"tokens": None, "money_usd": 0.0},
                }

            try:
                with patch("valudus.runner.load_adapter", return_value=unmetered_adapter):
                    report = run_benchmark(
                        Path(artifact.name),
                        "ignored:adapter",
                        output,
                        "candidate-system",
                        "0.1.0",
                        "unmetered-cost test configuration",
                    )
                self.assertEqual("invalid", report["status"])
                self.assertTrue(
                    any("budget cannot be verified: tokens" in reason for reason in report["failure_reasons"])
                )
            finally:
                shutil.rmtree(output.parent)
