# Pipeline Architecture

## Goal

The reference pipeline converts a versioned benchmark manifest and a system adapter into an auditable run directory. It is deliberately small, dependency-free, and explicit about what it does not yet isolate.

## Layers

The implementation follows pragmatic layered architecture with Clean Architecture dependency rules:

- **Contracts** define the fixture and adapter boundary.
- **Validation** owns public artifact invariants and rejects incomplete experiments before execution.
- **Scoring** owns pure, reusable result calculations and does not know about files or adapters.
- **Runner** coordinates validation, adapter execution, resource observation, evidence creation, and report assembly.
- **CLI** translates command-line input into one validation or run use case.

Dependencies point inward. An adapter cannot decide how a result is scored, and a scorer cannot read or write experiment artifacts.

## Adapter boundary

An adapter is a Python callable referenced as `module:attribute`. It receives one JSON-object fixture input and returns a JSON object with two keys: `output`, the structured result, and `usage`, which declares non-negative `tokens` and `money_usd` values or `null` when unavailable. The runner deep-copies the input, records hashes of inputs and expected outputs, and catches per-fixture exceptions as evidence rather than discarding them.

This first adapter mechanism is intentionally in-process. It is appropriate for trusted local adapters only. Running untrusted systems requires a future isolated-process adapter with explicit network, filesystem, timeout, and resource controls.

## Artifact contract

`valudus run` refuses to write into an existing output directory. For a fresh directory it writes, in order:

1. `evidence.jsonl`, one execution record per fixture;
2. `run-report.json`, including the SHA-256 digest of the evidence file.

An adapter exception, unobserved mandatory cost, or measured budget breach produces a report with `status: "invalid"` and explicit `failure_reasons`. The command exits non-zero after preserving those artifacts. Negative and invalid outcomes therefore remain inspectable.

## Reproducibility and comparability

The runner records benchmark version, adapter-selected system identity, configuration, platform, Python version, seed, elapsed wall time, peak resident memory, metric values, and evidence hash. The hash makes later evidence changes detectable; it does not prevent filesystem modification. The reproducibility tier defaults to `procedural`, and authors may claim `exact` only when captured conditions support it. The runner does not claim that any two reports are comparable automatically. Benchmark authors must keep fixtures, scorer rules, partitions, and budgets compatible before comparing results.

## Running the reference experiment

```sh
PYTHONPATH=src python3 -m valudus run examples/minimal-benchmark.json \
  --adapter valudus.reference_adapter:evaluate \
  --output /tmp/valudus-reference-run \
  --system-name reference-system \
  --system-version 0.1.0 \
  --configuration "deterministic reference configuration" \
  --reproducibility-tier exact
```

Inspect `/tmp/valudus-reference-run/evidence.jsonl` before interpreting the aggregate score in `run-report.json`.
