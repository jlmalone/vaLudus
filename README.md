# vaLudus

> Build the Lab, Not the Cathedral

vaLudus is an open laboratory for rigorous evaluation of intelligences. It develops an inspectable pipeline for designing, running, challenging, and reporting benchmarks across systems and domains.

## Abstract

Evaluation is often reduced to a leaderboard score. That shortcut obscures the question a result can actually answer: under which tasks, budgets, conditions, and assumptions did this particular system succeed or fail? vaLudus treats evaluation as a falsifiable experimental practice. A benchmark is not only a dataset and a scorer; it is a versioned claim with declared scope, resource limits, contamination controls, adversarial checks, evidence, and conditions that would invalidate its conclusion.

The project begins from a simple premise: evaluations should be useful to researchers, builders, and critics even when the result is negative, expensive, non-reproducible, or vulnerable to contamination.

## Problem: why current evaluations fall short

Many current evaluations provide useful signals, but their headline results can overstate what has been established.

- **Narrow scores are mistaken for broad capability.** A result on a fixed task distribution is often read as evidence of general competence without testing transfer, boundary conditions, or failure modes.
- **Benchmarks can be optimized rather than solved.** Public tasks, familiar formats, scorer loopholes, and prompt-specific tuning invite gaming that does not transfer to the intended capability.
- **Reproduction is underspecified.** Missing configuration, environment, seed, tool, and input-provenance details make it difficult to distinguish genuine progress from an irreproducible result.
- **Costs are externalized.** Tokens, money, latency, accelerator use, memory, and human effort are frequently absent from comparisons, even when they decide practical viability.
- **Contamination is underreported.** Training exposure, public test material, and iterative tuning against evaluation feedback can invalidate a claim while leaving the nominal score intact.
- **Failure is hidden.** Invalid runs, robustness regressions, and negative findings are less visible than wins, which makes it harder to understand where a system is unreliable.

## Design principles

vaLudus aims for:

- **Scalability:** protocols can describe small controlled studies and large repeated runs without changing their evidence standard.
- **Generalisability:** benchmarks distinguish in-distribution success from transfer and state the task family to which a conclusion applies.
- **Reproducibility:** manifests, run reports, inputs, configurations, environments, seeds, and evidence are versioned wherever possible.
- **Resistance to gaming:** benchmark authors identify shortcuts, use held-out or refreshable material when needed, and add adversarial or counterfactual checks.
- **Falsifiability:** each capability claim names its failure cases, invalid-run conditions, and evidence that would disconfirm it.
- **Robustness:** ordinary variation and relevant adversarial conditions are part of the result, not an afterthought.
- **Cost awareness:** tokens, money, elapsed time, processing power, memory, and material human effort are reported as first-class measurements.
- **Contamination discipline:** exposure risks, mitigations, and residual uncertainty are explicit, never silently assumed away.

## Capabilities targeted

The laboratory targets measurable capabilities of any intelligence, including but not limited to:

- perception and representation;
- structured reasoning and planning;
- learning, adaptation, and transfer;
- tool use and environment interaction;
- communication, collaboration, and instruction following;
- reliability, calibration, error recovery, and safety-relevant behavior;
- resource-efficient performance under declared constraints.

No score is treated as a claim of general intelligence. A conclusion applies only to its declared task family, system version, budget, environment, and contamination assumptions.

## Evaluation model

An evaluation has four independent layers:

1. A benchmark manifest declares the task, capability claim, metrics, scope, failure cases, contamination controls, and required resources.
2. A run report binds one system and one execution environment to that manifest, with raw evidence and observed resource use.
3. A validation step rejects incomplete or internally inconsistent artifacts before results are compared.
4. An interpretation records limits, robustness checks, and the conditions under which a conclusion would be false.

## Add a new benchmark

1. Copy `examples/minimal-benchmark.json` and give the manifest a stable `id` and semantic `version`.
2. State a bounded capability claim, task family, input provenance, metric direction and units, and a success threshold.
3. Name concrete failure cases, invalid-run conditions, likely gaming strategies, robustness checks, and contamination risk with mitigations and residual risk.
4. Declare budgets for tokens, money, wall time, compute, and memory. Use `not applicable` only when a measure truly cannot apply.
5. Add fixtures with `development`, `held_out`, or `adversarial` partitions, select a scorer, and add any domain adapter under a directory owned by the benchmark.
6. Run each system configuration through the pipeline. It preserves fixture-level evidence before producing a hashed run report with resource observations and validity status.
7. Inspect failure records, validate the artifacts, run the benchmark's focused checks, and document what result would falsify the original claim.

The manifest and run-report schemas are deliberately domain-neutral. Domain adapters may add detail, but cannot omit the core evidence requirements.

## Repository layout

- `schemas/` contains versioned machine-readable contracts for benchmark manifests and run reports.
- `src/valudus/` contains the first reference validation tools.
- `domains/` contains domain-owned pipeline, generator, and benchmark placeholders.
- `examples/` contains a minimal general-purpose benchmark and run report.
- `docs/` records the evaluation protocol.
- `tests/` verifies the reference tooling.

## Quick start

The initial reference tool uses only the Python standard library.

```sh
PYTHONPATH=src python3 -m unittest discover -s tests -v
PYTHONPATH=src python3 -m valudus validate-benchmark examples/minimal-benchmark.json
PYTHONPATH=src python3 -m valudus validate-run examples/minimal-run.json
PYTHONPATH=src python3 -m valudus run examples/minimal-benchmark.json \
  --adapter valudus.reference_adapter:evaluate \
  --output /tmp/valudus-reference-run \
  --system-name reference-system \
  --system-version 0.1.0 \
  --configuration "deterministic reference configuration" \
  --reproducibility-tier exact
```

See [the pipeline architecture](docs/PIPELINE.md) for the adapter contract, artifact order, failure behavior, and current isolation boundary.

## Status

The repository is intentionally small at inception. Its first milestone is a stable, domain-neutral artifact contract. Future adapters can support different kinds of intelligence and task environments without weakening the common evidence standard.

See [the evaluation protocol](docs/EVALUATION_PROTOCOL.md), [domain layout](domains/README.md),
[prompt-conditioned evaluation protocol](docs/PROMPT_CONDITIONED_EVALUATION.md),
[roadmap](docs/ROADMAP.md), and [contribution guide](CONTRIBUTING.md).

## Synthetic governance harness

The first governance domain tests a bounded, synthetic charter-conformance claim. It uses an
inspectable deterministic oracle: explicit prohibitions reject, while missing authority, evidence,
approval, or conflict mitigation escalates. It is not a real-world governance, legal, or policy
decision system.

Generate a fresh fixture suite after candidate configuration is frozen:

```sh
gradle run --args="generate --output /secure/governance-held-out-240.json --cases 240 --seed 8128"
```

Run the Kotlin reference pipeline to validate generated expected results and preserve its evidence:

```sh
gradle run --args="run-reference --manifest /secure/governance-held-out-240.json --output /tmp/governance-reference-run"
```

Candidate adapters and isolated execution are the next integration layer. See the
[governance domain](domains/governance/README.md) for the protocol and boundaries.
