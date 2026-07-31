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
3. **Establish domain-local evaluation paths.** Mindustry, Gambit, and LunLunZhongWen own their own pipelines, fixture generators, and benchmarks while sharing vaLudus artifact contracts.
4. **Measure prompt-conditioned behavior.** Record the complete deployed prompt configuration, preserve the user's request as a distinct input layer, and report sensitivity across controlled prompt variants.
5. **Design isolation before external execution.** Specify a separate-process adapter boundary with explicit filesystem, network, timeout, and resource controls before accepting untrusted adapters or remote systems.

Each item is useful on its own. Do not block a reviewable benchmark or a reliable artifact improvement on a dashboard, account system, marketplace, or generalized orchestration layer.

## Decision gates

The following choices need owner direction before implementation because they change the kind of claim vaLudus can make:

- the first independent task family and the capability claim it should test;
- whether that benchmark uses a public, private, or refreshable held-out fixture set;
- which external systems, if any, may be evaluated and how their cost and configuration evidence will be captured;
- the isolation policy for adapters that are not trusted local code.

Until those decisions are made, contributions should improve the existing artifact contracts,
reference runner, evidence inspection, documented benchmark-authoring process, or the bounded
society experiment below.

## Society policy laboratory

The first owner-directed slice now fixes one synthetic question: whether provenance labels reduce
a false-information campaign during an income shock without excessive loss of neutral reach. Its
paired runner and evidence ledger are foundation work, not an endorsement of its behavioral model.

The first one-factor sweep and competing belief model are now executable. Move the work forward in
the following order:

1. Add interaction testing and global sensitivity sampling after inspecting the one-factor results.
2. Define empirical calibration targets for activity time, resource stress, exposure, and sharing,
   with documented provenance and acceptable error bands.
3. Add an isolated policy-agent interface whose powers are a small declared action set. Compare it
   with fixed baselines on held-out shocks.
4. Add supply, labour, and institution modules only when each brings an independently testable
   accounting identity or outcome claim. A separate economic-model project is justified once those
   modules need calibration against national accounts, household panels, or market data.
