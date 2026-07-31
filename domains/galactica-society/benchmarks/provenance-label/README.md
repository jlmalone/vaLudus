# Synthetic provenance-label counterfactual

This first executable society experiment asks one bounded question: within the declared synthetic
behavioral model, does a provenance label reduce adoption and resharing of a high-arousal false
claim during an income shock without suppressing too much neutral information? The primary effect
uses the continuous false-belief score because a binary adoption threshold can hide changes when
both scenarios saturate. Threshold prevalence remains in the evidence as a diagnostic.

It runs 200 paired replications. Each baseline and intervention pair starts with the same synthetic
population and consumes the same random stream. The paired difference therefore measures the
effect of the modeled policy rather than differences between randomly generated populations.

```sh
gradle run --args="simulate-society \
  --plan domains/galactica-society/benchmarks/provenance-label/PLAN.json \
  --output /tmp/valudus-provenance-label"
```

The output directory contains:

- `plan.json`, the exact experiment input;
- `daily-metrics.jsonl`, daily activity, economic-stress, exposure, belief, and resharing totals;
- `agent-outcomes.jsonl`, final individual outcomes for distributional inspection;
- `replications.jsonl`, paired effects for every replicated world; and
- `run-report.json`, evidence hashes, aggregate effects, 95% normal-approximation confidence
  intervals, resource observations, and the declared pass or fail outcome.

The kernel does not establish that the behavioral coefficients are realistic. A passing result
means only that the intervention meets the declared thresholds inside this versioned synthetic
world. The companion study below tests sensitivity and an alternative behavioral model. Further
scientific steps require empirical calibration targets and preregistered held-out scenarios.

## Sensitivity and competing models

The companion sweep varies every declared behavioral coefficient plus selected campaign, policy,
and economic assumptions one at a time at a low and high bound. It repeats the experiment using
both `linear-persuasion` and `evidence-accumulation` belief dynamics.

```sh
gradle run --args="sweep-society \
  --plan domains/galactica-society/benchmarks/provenance-label/SENSITIVITY.json \
  --output /tmp/valudus-provenance-sensitivity"
```

The declared sweep contains 118 configurations and 944 paired worlds. Each configuration keeps a
full child evidence directory. The top-level `configurations.jsonl` links to each child report and
records a digest of its raw evidence hashes. `axis-summaries.jsonl` places each low and high effect
beside its model baseline. The aggregate `run-report.json` records pass rate, sign reversals,
cross-model disagreement, fragile axes, and the observed effect range.

One-factor-at-a-time analysis does not expose interactions between coefficients. Its job is to
find obvious fragility cheaply before global sampling, factorial designs, or empirical calibration.
