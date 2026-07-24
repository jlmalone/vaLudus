import json
import tempfile
import unittest
from pathlib import Path

from valudus.__main__ import validate_artifact


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
