"""Typed contracts at the boundary between benchmark authors and the runner."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Protocol


JsonObject = dict[str, Any]


@dataclass(frozen=True)
class Fixture:
    """One independently scored input with an expected structured result."""

    identifier: str
    partition: str
    input: JsonObject
    expected: JsonObject


class Adapter(Protocol):
    """A system adapter maps one fixture input to an output and usage record."""

    def __call__(self, input_data: Mapping[str, Any]) -> JsonObject:
        """Return `{output, usage}` without mutating the input."""
