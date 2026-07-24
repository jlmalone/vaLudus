"""Reference rules for the Gambit Siamese Capablanca routing benchmark."""

from __future__ import annotations

from typing import Any


def evaluate(input_data: dict[str, Any]) -> dict[str, Any]:
    """Evaluate one published routing case with zero external resource use."""
    operation = input_data["operation"]
    if operation == "board_allowed":
        output = {"allowed": _board_allowed(input_data["board"], input_data["rotation"])}
    elif operation == "advance_pair":
        output = _advance_pair(
            input_data["board"],
            input_data["moved_color"],
            input_data["pair_phase"],
            input_data["moved_this_pair"],
            input_data["over"],
        )
    else:
        raise ValueError(f"unsupported operation: {operation}")
    return {"output": output, "usage": {"tokens": 0, "money_usd": 0.0}}


def _board_allowed(board: str, rotation: dict[str, Any]) -> bool:
    over = rotation["over"]
    if over[board]:
        return False
    other = "b" if board == "a" else "a"
    style = rotation["turn_style"]
    if style == "independent":
        return True
    if style == "alternating":
        return over[other] or (rotation["history_length"] % 2 == 0 and board == "a") or (
            rotation["history_length"] % 2 == 1 and board == "b"
        )
    if style == "simultaneous":
        return rotation["turn"][board] == rotation["pair_phase"] and not rotation["moved_this_pair"][board]
    raise ValueError(f"unsupported turn style: {style}")


def _advance_pair(
    board: str,
    moved_color: str,
    pair_phase: str,
    moved_this_pair: dict[str, bool],
    over: dict[str, bool],
) -> dict[str, Any]:
    next_moved = {**moved_this_pair, board: True}
    if (next_moved["a"] or over["a"]) and (next_moved["b"] or over["b"]):
        return {"moved_this_pair": {"a": False, "b": False}, "pair_phase": "b" if moved_color == "w" else "w"}
    return {"moved_this_pair": next_moved, "pair_phase": pair_phase}
