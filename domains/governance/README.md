# Governance evaluation domain

This domain tests whether a candidate system applies an explicit, synthetic charter to a bounded proposal. It tests structured governance conformance, not whether a system should govern people, make legal determinations, or exercise real authority.

The first harness has three deliberate outcomes:

- `approve` only when the action is in scope and all declared controls are met;
- `reject` only when the charter explicitly prohibits the action;
- `escalate` when authority, evidence, approval, or conflict handling is incomplete.

The synthetic charter makes the oracle inspectable and lets benchmark authors generate hundreds of cases while preserving a crisp falsification condition. See the [pipeline](pipeline/README.md), [generator](generator/README.md), and [benchmark protocol](benchmarks/README.md).
