# Evaluation Protocol

## Scientific posture

vaLudus treats each result as a bounded empirical claim. A claim is credible only when an independent evaluator can identify the task, inputs, system configuration, scoring procedure, resources, and known threats to validity.

## Required artifacts

### Benchmark manifest

The manifest is immutable once a comparative run begins. It declares:

- the task family and target capability;
- benchmark version and input provenance;
- primary and secondary metrics, including direction and units;
- an explicit success threshold;
- named failure cases and invalid-run conditions;
- robustness and anti-gaming checks;
- contamination risk, mitigations, and residual risk;
- declared limits for tokens, money, time, compute, and memory.

### Run report

The run report identifies the system and benchmark versions, captures environment and seed information, reports resource observations, records metric values, and links to evidence. It also states deviations and invalidation status.

## Comparison rules

Compare runs only when their benchmark version, task partition, scoring rules, and declared resource policies are compatible. A better primary score does not dominate a result that exceeds a mandatory budget, fails a required robustness check, or carries unacknowledged contamination risk.

## Resistance to gaming

Benchmarks should identify likely shortcuts, include counterfactual or adversarial checks where appropriate, and refresh material when exposure threatens validity. The test harness, scorer, and hidden material should be versioned and access-controlled independently from participant-facing specifications when such separation is needed.

## Falsifiability and failure cases

Every benchmark must name outcomes that disconfirm its intended capability claim. Failure cases are first-class results. A run may be marked invalid when its artifact contract is violated, its evidence is unavailable, or a declared execution constraint is breached.

## Reproducibility tiers

- **Exact**: another evaluator can rerun the same artifact with pinned inputs, configuration, environment, and seed.
- **Procedural**: another evaluator can regenerate an equivalent run using a documented process, despite unavoidable variation.
- **Exploratory**: key conditions are not yet sufficient for independent replication; the result is not suitable for direct ranking.
