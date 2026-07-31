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
world. The next scientific steps are sensitivity sweeps, alternative behavioral models, empirical
calibration targets, and preregistered held-out scenarios.
