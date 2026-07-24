"""Dependency-free validation for public vaLudus artifact contracts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


REQUIRED_FIELDS = {
    "benchmark": {
        "schema_version", "id", "version", "task_family", "capability_claim",
        "metrics", "success_threshold", "failure_cases", "contamination", "budget",
        "fixtures", "evaluation", "execution",
    },
    "run": {
        "schema_version", "run_id", "benchmark", "system", "reproducibility_tier",
        "environment", "resources", "metrics", "evidence", "status",
    },
}


def read_json_object(path: Path) -> tuple[dict[str, Any] | None, list[str]]:
    """Read a JSON object while returning user-actionable contract errors."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return None, [f"cannot read JSON: {error}"]
    if not isinstance(document, dict):
        return None, ["artifact must be a JSON object"]
    return document, []


def validate_artifact(path: Path, artifact_type: str) -> list[str]:
    """Return contract errors for a benchmark manifest or run report."""
    document, errors = read_json_object(path)
    if document is None:
        return errors
    return validate_document(document, artifact_type)


def validate_document(document: dict[str, Any], artifact_type: str) -> list[str]:
    """Validate an in-memory artifact at the public contract boundary."""
    errors: list[str] = []
    missing = REQUIRED_FIELDS[artifact_type] - document.keys()
    if missing:
        errors.append(f"missing required fields: {', '.join(sorted(missing))}")
    if document.get("schema_version") != "1.1":
        errors.append("schema_version must be 1.1")
    if artifact_type == "benchmark":
        errors.extend(_validate_benchmark(document))
    else:
        errors.extend(_validate_run(document))
    return errors


def _validate_benchmark(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    metrics = document.get("metrics")
    if not isinstance(metrics, list) or not metrics:
        errors.append("metrics must be a non-empty list")
    if not isinstance(document.get("failure_cases"), list) or not document["failure_cases"]:
        errors.append("failure_cases must be a non-empty list")
    contamination = document.get("contamination")
    if not isinstance(contamination, dict) or not contamination.get("residual_risk"):
        errors.append("contamination must declare residual_risk")
    errors.extend(_validate_budget(document.get("budget")))
    errors.extend(_validate_fixtures(document.get("fixtures")))
    evaluation = document.get("evaluation")
    if not isinstance(evaluation, dict) or evaluation.get("scorer") != "exact_match":
        errors.append("evaluation must select the supported exact_match scorer")
    execution = document.get("execution")
    if not isinstance(execution, dict) or not isinstance(execution.get("seed"), int):
        errors.append("execution must declare an integer seed")
    return errors


def _validate_budget(budget: Any) -> list[str]:
    required = {"tokens", "money_usd", "wall_time_seconds", "compute", "memory_mb"}
    if not isinstance(budget, dict) or required - budget.keys():
        return ["budget must declare tokens, money_usd, wall_time_seconds, compute, and memory_mb"]
    errors: list[str] = []
    for field in ("tokens", "money_usd", "wall_time_seconds", "memory_mb"):
        maximum = budget[field].get("maximum") if isinstance(budget[field], dict) else None
        if maximum is not None and (not isinstance(maximum, (int, float)) or maximum < 0):
            errors.append(f"budget.{field}.maximum must be a non-negative number or null")
    if not isinstance(budget["compute"], str) or not budget["compute"]:
        errors.append("budget.compute must be a non-empty description")
    return errors


def _validate_fixtures(fixtures: Any) -> list[str]:
    if not isinstance(fixtures, list) or not fixtures:
        return ["fixtures must be a non-empty list"]
    errors: list[str] = []
    identifiers: set[str] = set()
    for index, fixture in enumerate(fixtures):
        prefix = f"fixtures[{index}]"
        if not isinstance(fixture, dict):
            errors.append(f"{prefix} must be an object")
            continue
        identifier = fixture.get("id")
        if not isinstance(identifier, str) or not identifier:
            errors.append(f"{prefix}.id must be a non-empty string")
        elif identifier in identifiers:
            errors.append(f"fixture id {identifier!r} is duplicated")
        else:
            identifiers.add(identifier)
        if fixture.get("partition") not in {"development", "held_out", "adversarial"}:
            errors.append(f"{prefix}.partition must be development, held_out, or adversarial")
        if not isinstance(fixture.get("input"), dict) or not isinstance(fixture.get("expected"), dict):
            errors.append(f"{prefix} must declare object input and expected values")
    return errors


def _validate_run(document: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if document.get("reproducibility_tier") not in {"exact", "procedural", "exploratory"}:
        errors.append("reproducibility_tier must be exact, procedural, or exploratory")
    if document.get("status") not in {"valid", "invalid", "incomplete"}:
        errors.append("status must be valid, invalid, or incomplete")
    if not isinstance(document.get("evidence"), list) or not document["evidence"]:
        errors.append("evidence must be a non-empty list")
    return errors
