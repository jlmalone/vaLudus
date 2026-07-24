"""Command-line validation for vaLudus artifacts."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from .runner import RunnerError, run_benchmark
from .uci import UciEngine, UciEngineError
from .validation import validate_artifact

def main() -> int:
    parser = argparse.ArgumentParser(description="Validate vaLudus benchmark artifacts.")
    subcommands = parser.add_subparsers(dest="command", required=True)
    for command in ("validate-benchmark", "validate-run"):
        validation = subcommands.add_parser(command)
        validation.add_argument("artifact", type=Path)
    run = subcommands.add_parser("run")
    run.add_argument("manifest", type=Path)
    run.add_argument("--adapter", required=True, help="Callable adapter in module:attribute notation.")
    run.add_argument("--output", required=True, type=Path)
    run.add_argument("--system-name", required=True)
    run.add_argument("--system-version", required=True)
    run.add_argument("--configuration", required=True)
    run.add_argument(
        "--reproducibility-tier",
        choices=["exact", "procedural", "exploratory"],
        default="procedural",
        help="Claim only the strongest tier supported by the captured conditions.",
    )
    engine_probe = subcommands.add_parser(
        "probe-engine", help="Run one bounded, zero-cost UCI analysis against a trusted local engine."
    )
    engine_probe.add_argument("--engine", required=True, type=Path)
    engine_probe.add_argument("--variant", required=True)
    engine_probe.add_argument("--fen", required=True)
    engine_probe.add_argument("--depth", required=True, type=int)
    engine_probe.add_argument("--timeout-seconds", default=10.0, type=float)
    args = parser.parse_args()
    if args.command == "run":
        try:
            report = run_benchmark(
                args.manifest, args.adapter, args.output, args.system_name,
                args.system_version, args.configuration, args.reproducibility_tier,
            )
        except RunnerError as error:
            print(f"RUN FAILED: {error}")
            return 2
        print(f"{report['status'].upper()}: {args.output / 'run-report.json'}")
        return 0 if report["status"] == "valid" else 1

    if args.command == "probe-engine":
        try:
            with UciEngine(args.engine, args.timeout_seconds) as engine:
                analysis = engine.analyse(args.fen, args.variant, args.depth)
        except UciEngineError as error:
            print(f"ENGINE PROBE FAILED: {error}")
            return 2
        print(json.dumps(analysis.as_dict(), indent=2, sort_keys=True))
        return 0

    artifact_type = "benchmark" if args.command == "validate-benchmark" else "run"
    errors = validate_artifact(args.artifact, artifact_type)
    if errors:
        print("INVALID")
        for error in errors:
            print(f"- {error}")
        return 1
    print("VALID")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
