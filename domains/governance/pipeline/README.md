# Governance pipeline

The Kotlin harness owns deterministic adjudication and fixture generation. The shared vaLudus
artifact contract remains the authority for manifest validation, evidence, budgets, and run reports.

For a candidate system, write an adapter that accepts the fixture `input` object and returns the required vaLudus `output` and `usage` objects. Its `output` must exactly contain a `decision` and ordered `reasons` array. The candidate adapter must not use the reference oracle.

The reference adapter is a harness check, not a candidate baseline:

```sh
gradle run --args="run-reference --manifest /secure/held-out-governance-240.json --output /tmp/governance-reference-run"
```

The Kotlin reference oracle is a test harness, not a candidate adapter. Candidate execution and
untrusted-adapter isolation remain separate work and must preserve the repository-wide policy.
