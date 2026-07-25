# Pipeline Architecture

## Goal

The Kotlin/JVM reference pipeline converts a versioned benchmark manifest and a trusted local reference adapter into an auditable run directory. It is deliberately small and explicit about what it does not yet isolate.

## Layers

The implementation follows pragmatic layered architecture with Clean Architecture dependency rules:

- **Contracts** define the fixture and adapter boundary.
- **Validation** owns public artifact invariants and rejects incomplete experiments before execution.
- **Scoring** owns pure, reusable result calculations and does not know about files or adapters.
- **Runner** coordinates validation, adapter execution, resource observation, evidence creation, and report assembly.
- **CLI** translates command-line input into one validation or run use case.

Dependencies point inward. An adapter cannot decide how a result is scored, and a scorer cannot read or write experiment artifacts.

## Trusted reference adapter boundary

Current adapters are Kotlin implementations selected by a stable name. They receive one JSON-object fixture input and return a structured JSON output. The runner records hashes of inputs and expected outputs and catches per-fixture exceptions as evidence rather than discarding them.

This first adapter mechanism is intentionally in-process. It is appropriate for trusted local reference implementations only. Running candidate systems requires a future isolated-process adapter with explicit network, filesystem, timeout, and resource controls.

## Artifact contract

`run-reference` refuses to write into an existing output directory. For a fresh directory it writes, in order:

1. `evidence.jsonl`, one execution record per fixture;
2. `run-report.json`, including the SHA-256 digest of the evidence file.

An adapter exception, unobserved mandatory cost, or measured budget breach produces a report with `status: "invalid"`, `outcome: "invalid"`, and explicit `failure_reasons`. A valid run separately receives `outcome: "passed"` or `outcome: "failed"` by its declared threshold. The command exits non-zero only for invalid execution, so negative capability results remain inspectable experiment artifacts.

## Reproducibility and comparability

The runner records benchmark version, selected system identity, configuration, JVM runtime, seed, elapsed wall time, memory allocation, metric values, and evidence hash. The hash makes later evidence changes detectable; it does not prevent filesystem modification. The reproducibility tier defaults to `procedural`, and authors may claim `exact` only when captured conditions support it. The runner does not claim that any two reports are comparable automatically. Benchmark authors must keep fixtures, scorer rules, partitions, and budgets compatible before comparing results.

## Running the reference experiment

```sh
gradle run --args="run-reference --manifest examples/minimal-benchmark.json --adapter reference-sum \
  --output /tmp/valudus-reference-run \
  --system-name reference-system \
  --system-version 0.1.0 \
  --configuration 'deterministic reference configuration' \
  --reproducibility-tier exact"
```

Inspect `/tmp/valudus-reference-run/evidence.jsonl` before interpreting the aggregate score in `run-report.json`.
