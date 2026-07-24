import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from valudus.runner import run_benchmark
from valudus.validation import validate_artifact


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "benchmarks/gambit-siamese-capablanca/manifest.json"


class GambitSiameseCapablancaBenchmarkTests(unittest.TestCase):
    def test_manifest_is_valid(self) -> None:
        self.assertEqual([], validate_artifact(MANIFEST, "benchmark"))

    def test_reference_adapter_passes_all_published_cases(self) -> None:
        output = Path(tempfile.mkdtemp()) / "run"
        try:
            report = run_benchmark(
                MANIFEST,
                "valudus.gambit_siamese_capablanca:evaluate",
                output,
                "reference-rule-implementation",
                "0.1.0",
                "pinned public routing rules",
                "exact",
            )
            self.assertEqual("valid", report["status"])
            self.assertEqual("passed", report["outcome"])
            self.assertEqual(1.0, report["metrics"]["exact_match_rate"])
        finally:
            shutil.rmtree(output.parent)

    def test_incorrect_but_well_formed_adapter_fails_threshold(self) -> None:
        output = Path(tempfile.mkdtemp()) / "run"

        def incorrect_adapter(_: dict) -> dict:
            return {"output": {"allowed": False}, "usage": {"tokens": 0, "money_usd": 0.0}}

        try:
            with patch("valudus.runner.load_adapter", return_value=incorrect_adapter):
                report = run_benchmark(
                    MANIFEST,
                    "ignored:adapter",
                    output,
                    "incorrect-rule-implementation",
                    "0.1.0",
                    "threshold behavior test",
                )
            self.assertEqual("valid", report["status"])
            self.assertEqual("failed", report["outcome"])
            self.assertLess(report["metrics"]["exact_match_rate"], 1.0)
        finally:
            shutil.rmtree(output.parent)

    def test_source_snapshot_is_well_formed(self) -> None:
        snapshot = json.loads((MANIFEST.parent / "SOURCE_SNAPSHOT.json").read_text())
        self.assertEqual(64, len(snapshot["source_sha256"]))
        self.assertEqual(40, len(snapshot["source_commit"]))
