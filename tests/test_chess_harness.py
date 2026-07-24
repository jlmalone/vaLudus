import unittest

from valudus.capablanca_metrics import material_balance
from valudus.uci import parse_uci_info


class ChessHarnessTests(unittest.TestCase):
    def test_material_balance_uses_gambit_capablanca_values(self) -> None:
        balance = material_balance("10/10/10/10/10/10/10/RNBAQKCBNR[] w KQkq - 0 1")
        self.assertEqual(4_790, balance.white_centipawns)
        self.assertEqual(0, balance.black_centipawns)
        self.assertEqual(4_790, balance.white_minus_black)

    def test_material_balance_excludes_turnabout_pockets(self) -> None:
        balance = material_balance("10/10/10/10/10/10/10/10[Qac] w - - 0 1")
        self.assertEqual(0, balance.white_centipawns)
        self.assertEqual(0, balance.black_centipawns)

    def test_parse_uci_info_preserves_score_and_search_measurements(self) -> None:
        parsed = parse_uci_info(
            "info depth 12 seldepth 16 score cp -43 nodes 12345 nps 456789 pv a2a3 a7a6"
        )
        self.assertIsNotNone(parsed)
        assert parsed is not None
        self.assertEqual("cp", parsed.score_kind)
        self.assertEqual(-43, parsed.score_value)
        self.assertEqual(12, parsed.depth)
        self.assertEqual(12_345, parsed.nodes)
        self.assertEqual("a2a3 a7a6", parsed.pv)

    def test_parse_uci_info_ignores_non_score_lines(self) -> None:
        self.assertIsNone(parse_uci_info("info string classical evaluation enabled"))
