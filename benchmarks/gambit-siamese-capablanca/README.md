# Gambit Siamese Capablanca Routing

This benchmark is the first vaLudus domain correctness gate. It evaluates a bounded rule-reasoning capability: determine whether a board may move and advance a simultaneous pair in a two-board game state.

It covers independent, alternating, and simultaneous turn styles, including the failure-prone condition where one board has finished and the other must continue without stalling. The expected results are exact structured outputs, so a passing result establishes only compliance with these declared rules.

## Provenance

`SOURCE_SNAPSHOT.json` pins the source revision, path, and SHA-256 digest from which these public cases were derived. It lets a reviewer establish precisely which rule contract this benchmark version represents without coupling the vaLudus runner to another working tree.

## Contamination boundary

All fixtures are public. This is therefore a regression and integration gate, not evidence of unseen variant-reasoning capability or game-playing strength. A comparative study must use separately generated, access-controlled cases and report their construction process, release timing, and residual exposure risk.

## Run the reference implementation

```sh
gradle run --args="run-reference --manifest benchmarks/gambit-siamese-capablanca/manifest.json --adapter gambit-siamese-capablanca-reference \
  --output /tmp/gambit-siamese-capablanca-reference \
  --system-name reference-rule-implementation \
  --system-version 0.1.0 \
  --configuration 'pinned public routing rules' \
  --reproducibility-tier exact"
```

Candidate execution is intentionally not available through this trusted reference boundary. It requires a future isolated adapter protocol described in the pipeline documentation.
