# Domain-owned evaluation paths

Every vaLudus domain owns three local concerns:

- `pipeline/` turns a declared system configuration and a domain task into durable evidence.
- `generator/` creates fixtures and records the source, seed, release timing, and contamination
  boundary.
- `benchmarks/` contains the bounded claims, manifests, scorers, and evidence instructions that
  the domain exposes for review.

vaLudus itself owns the shared artifact schema, validation, result comparability rules, and the
reference runner. A domain may add a task-specific scorer or simulator, but may not weaken the
evidence, budget, contamination, or invalid-run requirements.

The first placeholders are [Gambit](gambit/README.md), [Mindustry](mindustry/README.md),
[LunLunZhongWen](lunlunzhongwen/README.md), and the future
[Galactica society simulator](galactica-society/README.md).
