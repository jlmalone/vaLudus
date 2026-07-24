# Capablanca family pipeline

This is the first executable vaLudus domain pipeline. It evaluates an agent against a pinned local
Gambit rules revision, never a public match or user history.

## Per-decision loop

1. Load the fixture's initial FEN, pockets, handicap, Siamese board state, rotation state, and
   clock policy.
2. Construct the complete candidate input from the frozen system configuration, the preserved user
   request, and a structured observation of the playable state.
3. Ask the candidate for one structured action: a board identifier when relevant and one canonical
   UCI move or drop.
4. Validate and apply the action using Gambit's reference engine. Record the full observation,
   prompt-layer identifiers, action, legality, resulting state digest, elapsed time, and cost.
5. Stop on game terminal state, fixture move limit, clock expiration, budget breach, malformed
   action, or an adapter failure. Persist evidence before aggregate scoring.

## Stages

| Stage | Scope | Passing evidence |
|---|---|---|
| Rules gate | Turnabout moves and drops, Siamese board routing, handicap-start integrity | Exact legal or illegal action decisions against pinned fixtures |
| Coordination gate | Two-board capture transfer, Duo, alternating, and keep-playing behavior | Correct next board, reserve, and turn phase after a trace |
| Play gate | Clock-bounded matches against a declared opponent policy | Legal-play rate, outcome, clock use, and budget use across held-out starts |
| Prompt-conditioned gate | Same task under controlled system and user prompt variants | Cell-level results and variation, not only a best prompt score |

The rules and coordination gates are necessary preconditions, not proof that a candidate plays
Capablanca well. Only the play gate can support a bounded game-playing claim.
