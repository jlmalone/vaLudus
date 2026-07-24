"""Transparent Capablanca material measurements for benchmark evidence."""

from __future__ import annotations

from dataclasses import asdict, dataclass


CAPABLANCA_PIECE_VALUES = {
    "p": 100,
    "n": 320,
    "b": 330,
    "r": 500,
    "q": 900,
    "a": 720,
    "c": 870,
    "k": 0,
}


@dataclass(frozen=True)
class MaterialBalance:
    """On-board material in centipawns, excluding pockets and kings."""

    white_centipawns: int
    black_centipawns: int

    @property
    def white_minus_black(self) -> int:
        return self.white_centipawns - self.black_centipawns

    def as_dict(self) -> dict[str, int]:
        return {**asdict(self), "white_minus_black": self.white_minus_black}


def material_balance(fen: str) -> MaterialBalance:
    """Measure the board field of a 10×8 Capablanca or Turnabout FEN.

    Pockets are deliberately excluded: they are available material, not captured material. The
    game harness records capture events separately so a drop cannot erase capture history.
    """
    fields = fen.split()
    if not fields:
        raise ValueError("FEN must contain a board field")
    board = fields[0].split("[", maxsplit=1)[0]
    white = 0
    black = 0
    for symbol in board:
        if symbol.isdigit() or symbol in {"/", "~"}:
            continue
        value = CAPABLANCA_PIECE_VALUES.get(symbol.lower())
        if value is None:
            raise ValueError(f"unsupported Capablanca board symbol: {symbol}")
        if symbol.isupper():
            white += value
        else:
            black += value
    return MaterialBalance(white, black)
