# Governance fixture generator

Generate a fresh manifest after candidate model, adapter, and prompt settings are frozen. Keep the held-out manifest and its seed access-controlled until the run is complete.

```sh
gradle run --args="generate --output /secure/held-out-governance-240.json --cases 240 --seed 8128"
```

The generator refuses to overwrite an existing file. It creates development, held-out, and adversarial partitions and covers approval, explicit prohibition, missing authority, missing evidence, missing approval, and unresolved-conflict cases. A generated artifact is a specific benchmark version: preserve it with the run evidence rather than regenerating it after seeing results.
