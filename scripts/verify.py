#!/usr/bin/env python3
"""Run repository verification: format check, lint, and Maven tests."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def run(command: list[str]) -> None:
    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def python_executable() -> str:
    return sys.executable


def maven() -> str:
    return "mvn.cmd" if os.name == "nt" else "mvn"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-format", action="store_true", help="Skip format check")
    parser.add_argument("--skip-lint", action="store_true", help="Skip lint")
    parser.add_argument("--skip-tests", action="store_true", help="Skip Maven tests")
    parser.add_argument(
        "--lint-scope",
        default="all",
        help="Lint scope passed to scripts/lint.py (default: all)",
    )
    parser.add_argument(
        "--maven-args",
        default="",
        help="Extra arguments forwarded to mvn test (quoted string)",
    )
    args = parser.parse_args()

    if not args.skip_format:
        run([python_executable(), "scripts/format.py", "--check"])

    if not args.skip_lint:
        lint_cmd = [python_executable(), "scripts/lint.py"]
        if args.lint_scope != "all":
            lint_cmd.extend(["--scope", *args.lint_scope.split(",")])
        run(lint_cmd)

    if not args.skip_tests:
        test_cmd = [maven(), "test"]
        if args.maven_args.strip():
            test_cmd.extend(args.maven_args.split())
        run(test_cmd)

    print("verify: all requested checks passed", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
