# Galactica society simulator

This domain is for a closed economic simulation whose institutions and participants are
operated by AI agents. It is a research environment for testing coordination, allocation,
resilience, calibration, and failure recovery. It is not an authority to make real economic,
governance, financial, or person-affecting decisions.

The simulator belongs beside the benchmarks because its purpose is to create falsifiable evidence
about the kinds of agents that could eventually be trusted with larger responsibilities. It must
remain an explicitly modeled world with inspectable rules, incentives, initial conditions, and
failure criteria.

The first executable slice is the
[synthetic provenance-label counterfactual](benchmarks/provenance-label/README.md). It couples
daily activity totals, a simple household resource state, information exposure, belief updates,
and a fixed governance intervention. It is intentionally narrower than a society model: its
current value is the paired experiment and evidence machinery, not behavioral realism.

See [pipeline](pipeline/README.md), [generator](generator/README.md), and
[benchmarks](benchmarks/README.md). The
[society simulation landscape](../../docs/SOCIETY_SIMULATION_LANDSCAPE.md) records related
generative, economic, policy, daily-activity, and social-media systems considered during scoping.
