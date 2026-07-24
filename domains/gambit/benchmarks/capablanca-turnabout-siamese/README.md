# Capablanca Turnabout and Siamese

## Claim

Given a declared Capablanca Turnabout or Capablanca Siamese state, a candidate configuration can
select legal, goal-directed actions under a fixed clock and resource budget. This is a bounded claim
about the named variants and source revision. It is not a claim of general chess ability.

## Variants and rules

- **Capablanca Turnabout:** 10×8 Capablanca with Archbishop and Chancellor pieces, Crazyhouse
  pockets, and drops.
- **Capablanca Siamese:** two Capablanca Turnabout boards with Bughouse-style capture transfer.
- **Handicaps:** a material-reduced start FEN for an explicitly named side and odds rung. Removed
  pieces begin nowhere: they are not reserve pieces and cannot return through drops.

The [source snapshot](SOURCE_SNAPSHOT.json) identifies the current reference contract. It is a
design anchor, not a release claim or a substitute for fixture provenance.

## Metrics

| Metric | Meaning |
|---|---|
| Legal action rate | Fraction of submitted actions accepted by the reference engine |
| Objective score | Fixture-specific success, such as mate, survival, material target, or coordination completion |
| Match outcome | Win, draw, loss, or invalid under a fixed opponent and clock policy |
| Handicap response | Outcome and resource change by odds rung and handicapped side |
| Prompt variation | Mean and range across controlled system and user prompt variants |
| Invalid-run rate | Fraction with missing evidence, malformed actions, or unobserved required cost |

Record Fairy-Stockfish's principal variation, score (centipawn or mate), depth, nodes, and elapsed
analysis time at each declared checkpoint. UCI centipawns are from the side to move, so reports
must also state the normalized point of view. Material evidence has two distinct measurements:
on-board material balance and an append-only capture ledger. A reserve drop changes the former but
must never erase the latter.

## First fixtures

1. A Turnabout position where the only legal reply is a reserve drop.
2. A Turnabout position that rejects a pawn drop on rank 1 or rank 8.
3. A reduced-start Turnabout position whose empty pockets prove an odds piece cannot re-enter.
4. A Siamese capture trace that transfers the captured base piece to the correct teammate pocket.
5. A Siamese Duo trace where one board is finished and the other must continue without a stalled
   pair phase.
6. Clock-bounded held-out game starts at even material and selected handicap rungs.

## Candidate prompt matrix

For a fixed fixture and model, compare a user-only request with the same request inside a
product-owned planning scaffold. Add meaning-preserving user-request paraphrases and one
instruction ablation at a time. Keep the position, opponent, clock, tools, model revision, and
seed policy fixed. The candidate must return canonical structured actions, not an unparsed chess
explanation.

## Deliberate non-goals

- No live Gambit game, account, identity, cloud history, or matchmaking access.
- No claim of model strength from public regression fixtures alone.
- No Elo comparison until opponent policies, clocks, partitions, and sampling policy are frozen.
