# Difficulty and Handicap Calibration

This is a bounded, local-only calibration harness for the product's difficulty presets and material handicaps. It does not call a hosted service and therefore consumes no tokens.

`PLAN.json` pins one configuration per difficulty or handicap rung. Each configuration has a target player score, a local engine setting, random-move policy, and a hard maximum move time. Material-odds configurations deliberately use `random_move_chance: 0`: the handicap is material, not injected blunders.

Run a small batch of locally played or locally simulated games and record one JSON object per game in the `sample-results.jsonl` shape. The result collector calculates W/D/L, player score, 95% score uncertainty, average game length, average decision time, and a conservative tuning direction. It requires at least 12 games before recommending a setting change.

```sh
gradle run --args="summarize-difficulty \
  --plan domains/gambit/benchmarks/difficulty-handicap/PLAN.json \
  --results domains/gambit/benchmarks/difficulty-handicap/sample-results.jsonl \
  --output /tmp/difficulty-summary.json"
```

The sample is intentionally too small to tune. It proves the artifact path without pretending that four games establish a strength result. For an actual run, use at most 12 paired games per configuration, alternate colors, stop each game at 160 plies, and preserve the raw JSONL alongside its summary.
