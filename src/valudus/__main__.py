"""Command-line validation for vaLudus artifacts."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REQUIRED_FIELDS = {
    "benchmark": {
        "schema_version", "id", "version", "task_family", "capability_claim",
        "metrics", "success_threshold", "failure_cases", "contamination", "budget",
    },
    "run": {
        "schema_version", "run_id", "benchmark", "system", "reproducibility_tier",
        "environment", "resources", "metrics", "evidence", "status",
    },
}


def validate_artifact(path: Path, artifact_type: str) -> list[str]:
    """Return contract errors for a JSON artifact without external dependencies."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [f"cannot read JSON: {error}"]
    if not isinstance(document, dict):
        return ["artifact must be a JSON object"]

    errors: list[str] = []
    missing = REQUIRED_FIELDS[artifact_type] - document.keys()
    if missing:
        errors.append(f"missing required fields: {', '.join(sorted(missing))}")
    if document.get("schema_version") != "1.0":
        errors.append("schema_version must be 1.0")
    if artifact_type == "benchmark":
        errors.extend(_validate_benchmark(document))
    else:
        errors.extend(_validate_run(document))
    return errors


def _validate_benchmark(document: dict) -> list[str]:
    errors: list[str] = []
    if not isinstance(document.get("metrics"), list) or not document["metrics"]:
        errors.append("metrics must be a non-empty list")
    if not isinstance(document.get("failure_cases"), list) or not document["failure_cases"]:
        errors.append("failure_cases must be a non-empty list")
    contamination = document.get("contamination")
    if not isinstance(contamination, dict) or not contamination.get("residual_risk"):
        errors.append("contamination must declare residual_risk")
    budget = document.get("budget")
    if not isinstance(budget, dict) or {"tokens", "money_usd", "wall_time_seconds", "compute", "memory_mb"} - budget.keys():
        errors.append("budget must declare tokens, money_usd, wall_time_seconds, compute, and memory_mb")
    return errors


def _validate_run(document: dict) -> list[str]:
    errors: list[str] = []
    if document.get("reproducibility_tier") not in {"exact", "procedural", "exploratory"}:
        errors.append("reproducibility_tier must be exact, procedural, or exploratory")
    if document.get("status") not in {"valid", "invalid", "incomplete"}:
        errors.append("status must be valid, invalid, or incomplete")
    if not isinstance(document.get("evidence"), list) or not document["evidence"]:
        errors.append("evidence must be a non-empty list")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate vaLudus benchmark artifacts.")
    parser.add_argument("command", choices=["validate-benchmark", "validate-run"])
    parser.add_argument("artifact", type=Path)
    args = parser.parse_args()
    artifact_type = "benchmark" if args.command == "validate-benchmark" else "run"
    errors = validate_artifact(args.artifact, artifact_type)
    if errors:
        print("INVALID")
        for error in errors:
            print(f"- {error}")
        return 1
    print("VALID")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
