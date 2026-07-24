"""Execution orchestration that preserves evidence before producing a report."""

from __future__ import annotations

import copy
import importlib
import os
import platform
import resource
import time
from pathlib import Path
from typing import Any

from .contracts import Adapter
from .scoring import aggregate_exact_match, exact_match
from .serialization import sha256_file, sha256_json, write_json
from .validation import read_json_object, validate_document


class RunnerError(Exception):
    """A predictable execution error that should be shown to benchmark users."""


def load_adapter(reference: str) -> Adapter:
    """Load a callable adapter using a `module:attribute` reference."""
    if reference.count(":") != 1:
        raise RunnerError("adapter must use module:attribute notation")
    module_name, attribute_name = reference.split(":")
    try:
        adapter = getattr(importlib.import_module(module_name), attribute_name)
    except (ImportError, AttributeError) as error:
        raise RunnerError(f"cannot load adapter {reference}: {error}") from error
    if not callable(adapter):
        raise RunnerError("adapter reference must resolve to a callable")
    return adapter


def run_benchmark(
    manifest_path: Path,
    adapter_reference: str,
    output_directory: Path,
    system_name: str,
    system_version: str,
    configuration: str,
    reproducibility_tier: str = "procedural",
) -> dict[str, Any]:
    """Execute fixtures, write evidence first, then return the run report."""
    manifest, read_errors = read_json_object(manifest_path)
    if manifest is None:
        raise RunnerError("; ".join(read_errors))
    validation_errors = validate_document(manifest, "benchmark")
    if validation_errors:
        raise RunnerError("invalid benchmark manifest: " + "; ".join(validation_errors))
    if reproducibility_tier not in {"exact", "procedural", "exploratory"}:
        raise RunnerError("reproducibility tier must be exact, procedural, or exploratory")
    if output_directory.exists():
        raise RunnerError(f"output directory already exists: {output_directory}")

    adapter = load_adapter(adapter_reference)
    output_directory.mkdir(parents=True)
    evidence_path = output_directory / "evidence.jsonl"
    started = time.perf_counter()
    records: list[dict[str, Any]] = []
    scores: list[float] = []
    execution_errors: list[str] = []

    with evidence_path.open("w", encoding="utf-8") as evidence:
        for fixture in manifest["fixtures"]:
            input_data = copy.deepcopy(fixture["input"])
            fixture_started = time.perf_counter()
            record: dict[str, Any] = {
                "fixture_id": fixture["id"],
                "partition": fixture["partition"],
                "input_sha256": sha256_json(input_data),
                "expected_sha256": sha256_json(fixture["expected"]),
            }
            try:
                adapter_result = adapter(input_data)
                output, usage = _parse_adapter_result(adapter_result)
                score = exact_match(output, fixture["expected"])
                record.update({"output": output, "usage": usage, "score": score})
            except Exception as error:  # Evidence must include adapter failures.
                score = 0.0
                message = f"fixture {fixture['id']} failed: {type(error).__name__}: {error}"
                execution_errors.append(message)
                record.update({"adapter_error": message, "score": score})
            record["wall_time_seconds"] = time.perf_counter() - fixture_started
            evidence.write(__import__("json").dumps(record, sort_keys=True) + "\n")
            records.append(record)
            scores.append(score)

    wall_time_seconds = time.perf_counter() - started
    memory_mb = _peak_memory_mb()
    resources = {
        "tokens": _sum_usage(records, "tokens"),
        "money_usd": _sum_usage(records, "money_usd"),
        "wall_time_seconds": wall_time_seconds,
        "compute": platform.processor() or "unspecified processor",
        "memory_mb": memory_mb,
    }
    budget_breaches = _budget_breaches(manifest["budget"], resources)
    evidence_hash = sha256_file(evidence_path)
    status = "valid" if not execution_errors and not budget_breaches else "invalid"
    report = {
        "schema_version": "1.1",
        "run_id": f"{manifest['id']}-{manifest['version']}-{evidence_hash[:12]}",
        "benchmark": {"id": manifest["id"], "version": manifest["version"]},
        "system": {"name": system_name, "version": system_version, "configuration": configuration},
        "reproducibility_tier": reproducibility_tier,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "seed": manifest["execution"]["seed"],
        },
        "resources": resources,
        "metrics": {"exact_match_rate": aggregate_exact_match(scores)},
        "evidence": [{"path": "evidence.jsonl", "sha256": evidence_hash, "records": len(records)}],
        "deviations": [],
        "failure_reasons": execution_errors + budget_breaches,
        "status": status,
    }
    write_json(output_directory / "run-report.json", report)
    return report


def _peak_memory_mb() -> float:
    """Normalize `ru_maxrss` across macOS and Linux."""
    maximum = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return maximum / (1024 * 1024) if platform.system() == "Darwin" else maximum / 1024


def _parse_adapter_result(adapter_result: Any) -> tuple[dict[str, Any], dict[str, float | None]]:
    """Validate the adapter response before it becomes persisted experiment evidence."""
    if not isinstance(adapter_result, dict):
        raise TypeError("adapter result must be a JSON object")
    output = adapter_result.get("output")
    usage = adapter_result.get("usage")
    if not isinstance(output, dict):
        raise TypeError("adapter result.output must be a JSON object")
    if not isinstance(usage, dict) or set(usage) != {"tokens", "money_usd"}:
        raise TypeError("adapter result.usage must declare tokens and money_usd")
    normalized_usage: dict[str, float | None] = {}
    for field in ("tokens", "money_usd"):
        value = usage[field]
        if value is not None and (not isinstance(value, (int, float)) or value < 0):
            raise TypeError(f"adapter result.usage.{field} must be non-negative or null")
        normalized_usage[field] = value
    return output, normalized_usage


def _sum_usage(records: list[dict[str, Any]], field: str) -> float | None:
    """Sum an observed usage field, retaining unknown when any fixture lacks it."""
    values = [record.get("usage", {}).get(field) for record in records]
    return None if any(value is None for value in values) else sum(values)


def _budget_breaches(budget: dict[str, Any], observed: dict[str, Any]) -> list[str]:
    breaches: list[str] = []
    for resource_name in ("tokens", "money_usd", "wall_time_seconds", "memory_mb"):
        value = observed[resource_name]
        maximum = budget[resource_name]["maximum"]
        if maximum is not None and value is None:
            breaches.append(f"budget cannot be verified: {resource_name} was not observed")
        elif maximum is not None and value > maximum:
            breaches.append(f"budget exceeded: {resource_name} {value:.6f} > {maximum}")
    return breaches
