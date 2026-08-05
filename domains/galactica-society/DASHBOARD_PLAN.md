# Static society evidence dashboard plan

## Outcome

Add one Kotlin command that reads a completed `simulate-society` run and writes one
self-contained HTML file. The dashboard presents the standard paired baseline and intervention
experiment first. It never runs or changes a simulation.

The first implementation ends after one full dashboard has been generated and inspected. A
sensitivity-sweep view remains deferred until that inspection establishes which comparisons are
useful.

## Command boundary

The proposed command is:

```sh
gradle run --args="render-society-dashboard \
  --run /path/to/completed-society-run \
  --output /path/to/society-dashboard.html"
```

The command runs in one local JVM and makes zero hosted calls. It writes no files inside the input
run. An existing output path causes a refusal instead of an overwrite.

The dashboard is one HTML document with inline CSS and SVG. It has no server, application
framework, CDN asset, remote font, analytics request, or network dependency. Simulation controls,
parameter editors, and rerun buttons are outside this slice.

## Evidence gate

The renderer consumes these artifacts from one completed run directory:

- `plan.json` for the declared question, scope, model, policies, population, days, replications,
  and success criteria;
- `run-report.json` for validity, outcome, aggregate metrics, resource observations, evidence
  hashes, and failure reasons;
- `daily-metrics.jsonl` for day-level baseline and intervention series; and
- `replications.jsonl` for paired-effect distributions.

`agent-outcomes.jsonl` remains in the evidence ledger, but the first dashboard does not load or
plot every agent record. This keeps the rendering bound small while the replication and daily
artifacts cover the first scientific questions.

Rendering fails before publication when any required file is missing or malformed, a reported
record count or SHA-256 digest does not match, a policy or replication cannot be paired, daily rows
are incomplete, or required fields disagree across artifacts. Both `passed` and `failed` benchmark
outcomes are renderable when the run itself is valid. The dashboard displays that outcome without
reinterpretation.

All displayed statistics come from the verified artifacts. The renderer may calculate bounded
groupings such as daily means or histogram bins, but it must not impute missing observations,
smooth results, alter thresholds, or synthesize new runs.

## First dashboard

The page opens with a permanent synthetic-evidence notice. It states that coefficients are
hypotheses and that results are neither empirical estimates nor forecasts of people or policy.

The main content is:

1. **Run identity:** benchmark and kernel versions, belief model, policy pair, seed, population,
   days, replications, validity, and outcome.
2. **Declared decision:** the three success thresholds beside the observed mean effects and the
   report's failure reasons.
3. **Paired effect summary:** final false-belief score reduction with its 95% interval,
   low-resource reduction with its interval, neutral-reach retention, and false-reshare reduction.
4. **Daily paired view:** baseline and intervention mean false belief by day, with the campaign,
   correction, and economic-shock periods labeled from `plan.json`.
5. **Replication distributions:** compact plots for final false-belief reduction, low-resource
   reduction, neutral-reach retention, and false-reshare reduction. Zero and declared thresholds
   remain visually explicit.
6. **Evidence and resources:** source paths, hashes, record counts, wall time, observed memory,
   tokens, and money.

Charts use accessible inline SVG with visible legends, units, and a tabular numeric fallback.
Color is never the only way to distinguish baseline, intervention, thresholds, or failure state.
The report outcome and failure reasons remain visible when a chart is unavailable.

## Kotlin implementation shape

Keep the implementation inside the existing Kotlin/JVM application:

- a strict reader validates the run report and streams the two JSONL inputs;
- bounded aggregators retain per-day totals, replication effects, and fixed-bin distributions;
- a view model contains only escaped text and finite numeric values needed by the page;
- a renderer emits deterministic HTML, inline CSS, and SVG; and
- `ValudusCli` exposes the command without adding a server or web dependency.

HTML-escape every artifact string before rendering. Reject non-finite numbers, paths outside the
selected run directory, duplicate logical rows, and evidence entries that resolve through a
symlink. Write to a sibling temporary file, check every ceiling, then atomically move the complete
file into place. A failure leaves the source run untouched and no partial dashboard at the target.

## Resource ceilings

The first implementation has these hard ceilings:

- 30 seconds of renderer wall time;
- 256 MiB maximum JVM heap; and
- 2 MiB maximum HTML output.

Stream JSONL rather than loading whole evidence files. Abort with a clear diagnostic when a limit
would be exceeded. After successful publication, the command reports renderer wall time, peak
observed memory, output bytes, hosted calls, and tokens. These observations stay outside the
deterministic HTML. Hosted calls and tokens must both remain zero.

## Acceptance

The first slice is complete when focused tests and one full paired run demonstrate that:

- changed, missing, truncated, unpaired, or hash-mismatched evidence fails closed;
- a valid passed run and a valid failed run both render their original outcomes faithfully;
- identical inputs produce byte-identical HTML with no runtime timestamp or other nondeterministic
  field;
- the HTML contains no external resource URL or simulation control;
- the full output stays within 30 seconds, 256 MiB, and 2 MiB; and
- the generated file opens locally with legible charts, tables, evidence hashes, and synthetic
  scope language.

Stop after inspecting that first generated dashboard. Sensitivity axes, model comparisons, and
interactive exploration require a separate follow-up based on what the inspection reveals.
