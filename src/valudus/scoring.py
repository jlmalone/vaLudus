"""Pure scoring functions. New scorers belong here, not inside adapters."""

from __future__ import annotations

from typing import Any


def exact_match(output: Any, expected: Any) -> float:
    """Score one fixture as one for structural equality and zero otherwise."""
    return 1.0 if output == expected else 0.0


def aggregate_exact_match(scores: list[float]) -> float:
    """Return the mean exact-match score, or zero when no fixtures ran."""
    return sum(scores) / len(scores) if scores else 0.0
