# Contributing

Contributions should make evaluation claims easier to reproduce, challenge, and refine.

## Submission standard

A benchmark proposal must include a versioned manifest, a clear capability claim, measurable success and failure conditions, an account of likely gaming strategies, and a contamination-risk assessment. A run report must identify the evaluated system, exact configuration, environment, budgets, resource observations, raw evidence location, and any deviations from the protocol.

Do not submit results that conceal material budget, prompt, tool, data, or environment details. If a result cannot be reproduced, label it exploratory rather than comparative.

## Design principles

- Separate task construction from system execution and score interpretation.
- Prefer held-out and refreshable evaluation material when contamination is plausible.
- Report negative results and invalid runs.
- Predeclare metrics and stopping rules where practical.
- Measure resource use rather than assuming it is negligible.
- State what outcome would falsify the benchmark's claim.

## Scope discipline

New domain adapters must preserve the core artifact contracts. Domain-specific assumptions belong with the adapter, while the benchmark manifest and run report remain comparable across the project.
