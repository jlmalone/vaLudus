"""A deterministic reference adapter used only by the example benchmark."""

from __future__ import annotations

from typing import Any


def evaluate(input_data: dict[str, Any]) -> dict[str, Any]:
    """Add declared integer operands and return a measured structured result."""
    operands = input_data["operands"]
    if not isinstance(operands, list) or not all(isinstance(value, int) for value in operands):
        raise ValueError("operands must be a list of integers")
    return {"output": {"sum": sum(operands)}, "usage": {"tokens": 0, "money_usd": 0.0}}
