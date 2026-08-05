# Minimal Turnabout executor plan

## Outcome

Add a trusted-local Kotlin executor that proves the bounded Capablanca Turnabout match loop with
one unhandicapped `intermediate` color pair. This first slice runs two short games and preserves raw
evidence. It does not establish playing strength or calibrate a difficulty setting.

The executor stays in vaLudus. It reads the pinned Gambit source snapshot and canonical start FEN,
but requires no Gambit repository change.

## Smoke boundary

The first invocation accepts an explicit local Fairy-Stockfish executable, the existing
`PLAN.json`, and a new output directory. It selects only the `intermediate` configuration:

- `engine_elo`: 1600;
- `random_move_chance`: 0.0; and
- `handicap`: `none`.

The pair starts from Gambit's canonical Turnabout FEN with the UCI variant `capahouse`. The labeled
player takes White in one game and Black in the other. Each game stops after eight plies unless a
terminal state or failure occurs first. Every engine request uses a 250 ms move limit, and the
entire two-game process has a 30-second wall-time ceiling.

Both sides are explicit trusted-local engine configurations in the run manifest. Reusing the same
pinned executable on both sides is acceptable for this executor smoke, but the artifact must still
identify the logical side, color, options, and process for every move. The smoke validates process
control, color swapping, move application, evidence durability, and cleanup. It yields no tuning
recommendation.

The command must not accept a full schedule in this slice. Running any handicap configuration,
more than one pair, or the existing 156-game schedule requires a separate request.

## Preflight

Before starting an engine process, the executor validates:

- the plan schema and exact `intermediate` values;
- the canonical start FEN and `capahouse` variant against the pinned source snapshot;
- the engine path as an explicit regular executable file;
- the engine binary SHA-256 and declared engine identity;
- a fresh or exactly resumable output directory;
- zero hosted-call and token budgets; and
- the eight-ply, 250 ms, two-game, and 30-second smoke limits.

Each engine must complete the UCI handshake and advertise every option the executor relies on.
Unsupported strength limiting, malformed engine output, an identity mismatch, or an unobserved
required limit ends the run as a recorded failure.

## Append-safe evidence

The output directory contains:

- `run-manifest.json`, created once with the plan hash, source-snapshot hash, engine hash, start
  FEN, variant, side configurations, color pair, limits, platform, runtime, and zero-cost budget;
- `events.jsonl`, the append-only source of truth for process, handshake, game, move, stop, failure,
  and cleanup events;
- `games.jsonl`, a derived per-game ledger written only from validated raw events; and
- `run-report.json`, a fail-closed smoke summary with evidence hashes and resource observations.

Every raw event has a run ID, monotonically increasing sequence, game and ply identity when
applicable, monotonic elapsed time, event type, and the previous event hash. Move events preserve
the pre-move FEN or position command, acting side, raw UCI lines needed to support the decision,
selected move, elapsed decision time, and resulting position command. Failure events retain the
reason and last valid sequence.

Flush each complete JSON line before the next engine action. Never truncate or rewrite the raw
ledger. Resume first verifies the immutable manifest, the event hash chain, the last complete JSON
line, game order, colors, and engine identity. Any mismatch or partial final line refuses resume
and preserves the existing files for inspection.

The process supervisor records start and exit state for every child and always attempts bounded
cleanup. It contains only the processes launched for this run and never kills engines by name or a
broad process match.

## Resource evidence

Record at least:

- executor wall and CPU time;
- per-engine process wall and observed CPU time;
- peak resident memory for the executor and each child when the platform exposes it;
- move count, decision time, timeout count, exit status, and stdout/stderr bytes; and
- output file byte and record counts.

An unavailable observation is explicit, with the measurement method and reason. A field required
by the run manifest cannot silently become zero. The 30-second ceiling includes startup, both
games, evidence flushes, and child cleanup. A ceiling breach appends a failure and ends the pair.

## Fail-closed summary

The summarizer reads `run-manifest.json` and `events.jsonl` from the beginning. It recomputes hashes
and derived game records rather than trusting prior summary files. It produces a valid smoke result
only when:

- exactly two game starts form the declared opposite-color pair;
- events and ply numbers are complete, ordered, and hash-linked;
- each move belongs to the correct side and is accepted by the declared local rules authority;
- each game reaches a legitimate terminal state or the declared eight-ply smoke stop;
- all processes exit or are contained; and
- required resource and cost observations are present.

The report distinguishes `smoke_passed`, `smoke_failed`, and `invalid`. It always sets
`calibration_eligible` to `false` for this slice. An eight-ply stop has no player score and must not
be converted into a draw. Do not feed smoke records into `summarize-difficulty`, publish W/D/L, or
recommend an engine adjustment.

## Kotlin implementation shape

Keep the new code narrow:

- extend the existing UCI client with explicit process identity, bounded `go movetime`, position
  advancement, option validation, and captured protocol evidence;
- add a Turnabout smoke coordinator under the Gambit package;
- add append-only event and resource record types with strict JSON decoding;
- add an independent smoke summarizer; and
- expose one CLI command whose options cannot widen the declared first slice.

Use the existing JSON and SHA-256 patterns. Keep the source snapshot, start FEN, plan, and raw
engine transcript as evidence inputs rather than copying rules into an unversioned second source of
truth.

## Acceptance

The first slice is complete when focused tests cover successful pairing, swapped colors, the
eight-ply stop, timeout, malformed output, unsupported options, illegal or rejected moves, hash
mismatch, truncated evidence, resume refusal, missing resource data, and child cleanup.

One explicit local smoke must then show two games, no more than eight plies each, 250 ms move
limits, completion within 30 seconds, inspectable append-only evidence, zero hosted calls, zero
tokens, and no calibration claim. Stop and inspect the raw match evidence before requesting broader
coverage.
