"""Small, evidence-oriented UCI client for trusted local chess engines."""

from __future__ import annotations

from dataclasses import asdict, dataclass
import queue
import re
import subprocess
import threading
import time
from pathlib import Path


class UciEngineError(RuntimeError):
    """A local engine did not satisfy the UCI contract in the declared time."""


@dataclass(frozen=True)
class UciInfo:
    """The latest principal-variation observation emitted before ``bestmove``."""

    score_kind: str
    score_value: int
    depth: int | None
    nodes: int | None
    nps: int | None
    pv: str | None

    def as_dict(self) -> dict[str, int | str | None]:
        return asdict(self)


@dataclass(frozen=True)
class UciAnalysis:
    """One bounded engine search over an explicit FEN."""

    bestmove: str
    info: UciInfo | None
    elapsed_seconds: float

    def as_dict(self) -> dict[str, object]:
        return {
            "bestmove": self.bestmove,
            "info": self.info.as_dict() if self.info else None,
            "elapsed_seconds": self.elapsed_seconds,
        }


_SCORE = re.compile(r"\bscore\s+(cp|mate)\s+(-?\d+)")
_FIELD = {
    "depth": re.compile(r"\bdepth\s+(\d+)"),
    "nodes": re.compile(r"\bnodes\s+(\d+)"),
    "nps": re.compile(r"\bnps\s+(\d+)"),
}
_PV = re.compile(r"\bpv\s+(.+)$")


def parse_uci_info(line: str) -> UciInfo | None:
    """Extract a score-bearing UCI ``info`` line without guessing missing fields."""
    score = _SCORE.search(line)
    if not line.startswith("info ") or score is None:
        return None
    fields: dict[str, int | None] = {}
    for name, pattern in _FIELD.items():
        match = pattern.search(line)
        fields[name] = int(match.group(1)) if match else None
    pv = _PV.search(line)
    return UciInfo(
        score_kind=score.group(1),
        score_value=int(score.group(2)),
        depth=fields["depth"],
        nodes=fields["nodes"],
        nps=fields["nps"],
        pv=pv.group(1) if pv else None,
    )


class UciEngine:
    """A serial UCI session for a trusted, local executable.

    The class is intentionally not a sandbox. Callers must not supply an untrusted executable.
    """

    def __init__(self, executable: Path, timeout_seconds: float = 10.0) -> None:
        self.executable = executable
        self.timeout_seconds = timeout_seconds
        self._lines: queue.Queue[str] = queue.Queue()
        self._process: subprocess.Popen[str] | None = None
        self._reader: threading.Thread | None = None

    def __enter__(self) -> "UciEngine":
        if not self.executable.is_file():
            raise UciEngineError(f"engine executable does not exist: {self.executable}")
        self._process = subprocess.Popen(
            [str(self.executable)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        self._reader = threading.Thread(target=self._read_output, daemon=True)
        self._reader.start()
        self._send("uci")
        self._until(lambda line: line == "uciok")
        self._ready()
        return self

    def __exit__(self, *_: object) -> None:
        if self._process is None:
            return
        try:
            self._send("quit")
            self._process.wait(timeout=1.0)
        except (OSError, subprocess.TimeoutExpired):
            self._process.terminate()
            try:
                self._process.wait(timeout=1.0)
            except subprocess.TimeoutExpired:
                self._process.kill()
        finally:
            self._process = None

    def analyse(self, fen: str, variant: str, depth: int) -> UciAnalysis:
        """Return one depth-bounded analysis of a fully declared position."""
        if not fen.strip():
            raise UciEngineError("FEN must not be empty")
        if depth < 1:
            raise UciEngineError("depth must be at least 1")
        self._send(f"setoption name UCI_Variant value {variant}")
        self._send("ucinewgame")
        self._ready()
        self._send(f"position fen {fen}")
        started = time.perf_counter()
        self._send(f"go depth {depth}")
        latest: UciInfo | None = None
        while True:
            line = self._next_line()
            parsed = parse_uci_info(line)
            if parsed:
                latest = parsed
            if line.startswith("bestmove "):
                move = line.split(maxsplit=2)[1]
                return UciAnalysis(move, latest, time.perf_counter() - started)

    def _ready(self) -> None:
        self._send("isready")
        self._until(lambda line: line == "readyok")

    def _send(self, command: str) -> None:
        if self._process is None or self._process.stdin is None:
            raise UciEngineError("engine session is not running")
        try:
            self._process.stdin.write(command + "\n")
            self._process.stdin.flush()
        except OSError as error:
            raise UciEngineError(f"cannot write UCI command: {error}") from error

    def _read_output(self) -> None:
        assert self._process is not None and self._process.stdout is not None
        for line in self._process.stdout:
            self._lines.put(line.rstrip("\r\n"))

    def _until(self, predicate: object) -> str:
        while True:
            line = self._next_line()
            if callable(predicate) and predicate(line):
                return line

    def _next_line(self) -> str:
        try:
            return self._lines.get(timeout=self.timeout_seconds)
        except queue.Empty as error:
            raise UciEngineError(
                f"engine did not answer within {self.timeout_seconds:.1f}s"
            ) from error
