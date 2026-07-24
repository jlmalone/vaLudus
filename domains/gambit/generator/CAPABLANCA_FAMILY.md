# Capablanca family fixture generator

The generator has four families, all tied to a pinned Gambit source snapshot:

1. **Turnabout legality:** board moves, `@` drops, pocket ownership, promoted-piece demotion,
   check evasion, and illegal pawn drops on the first or eighth rank.
2. **Handicap starts:** every Capablanca material-odds rung for White and Black, with empty
   Turnabout pockets and correct castling-right removal when a rook is removed.
3. **Siamese coordination:** paired-board captures, reserve transfer to the opposite-colour
   teammate, independent, alternating, and Duo turn styles, plus a finished board that must not
   stall the live board.
4. **Game starts:** seeded, held-out positions and opponent policies for clock-bounded play.

Development fixtures may be public. Held-out and adversarial fixtures must be generated only after
the candidate model and all prompt configurations are frozen. The generator must retain its seed,
source snapshot, and every filtering or legality decision needed to reproduce the partition.
