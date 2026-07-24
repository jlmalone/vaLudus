# vaLudus roadmap

> Build the Lab, Not the Cathedral

vaLudus grows by making a small number of evaluation claims inspectable end to end. A feature belongs in the project only when it strengthens a benchmark's ability to be reproduced, challenged, or falsified. The project does not need a broad evaluation platform before it can produce useful evidence.

## Foundation: complete and maintain

- Versioned benchmark manifests and run reports define the common artifact contract.
- The reference runner preserves fixture evidence before assembling an outcome.
- Validation rejects incomplete artifacts, and budgets, contamination disclosures, failures, and invalid runs are first-class results.
- The Gambit Siamese Capablanca benchmark exercises the contract against a real, bounded rule domain.

## First build cycle

1. **Harden the trusted-local runner.** Keep the reference adapter deliberately local and add focused fixtures for its declared invalid-run, budget, and evidence-preservation behavior.
2. **Make results easy to inspect.** Provide a stable command that summarizes a run report and its evidence without turning a single score into a broad capability claim.
3. **Add one independent benchmark.** Select a bounded task family with explicit held-out and adversarial material, then publish its manifest, fixture provenance, scorer, and a negative or failing reference result when that is what the evidence shows.
4. **Design isolation before external execution.** Specify a separate-process adapter boundary with explicit filesystem, network, timeout, and resource controls before accepting untrusted adapters or remote systems.

Each item is useful on its own. Do not block a reviewable benchmark or a reliable artifact improvement on a dashboard, account system, marketplace, or generalized orchestration layer.

## Decision gates

The following choices need owner direction before implementation because they change the kind of claim vaLudus can make:

- the first independent task family and the capability claim it should test;
- whether that benchmark uses a public, private, or refreshable held-out fixture set;
- which external systems, if any, may be evaluated and how their cost and configuration evidence will be captured;
- the isolation policy for adapters that are not trusted local code.

Until those decisions are made, contributions should improve the existing artifact contracts, reference runner, evidence inspection, or the documented benchmark-authoring process.
