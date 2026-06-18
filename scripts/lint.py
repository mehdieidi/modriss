#!/usr/bin/env python3
"""Run repository linters for one or more technology scopes."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "linting.json"
REQUIREMENTS_LINT = ROOT / "requirements-lint.txt"


def run(command: list[str], *, stdin: bytes | None = None) -> None:
    printable = " ".join(command)
    if stdin is not None:
        printable = f"{printable} < <stdin>"
    print(f"+ {printable}", flush=True)
    subprocess.run(command, cwd=ROOT, check=True, input=stdin)


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def resolve_scope_names(config: dict[str, Any], requested: list[str]) -> list[str]:
    scopes: dict[str, Any] = config["scopes"]
    if not requested or requested == ["all"]:
        return list(scopes["all"]["includes"])

    unknown = [scope for scope in requested if scope not in scopes]
    if unknown:
        available = ", ".join(sorted(scopes))
        raise ValueError(f"Unknown scope(s): {', '.join(unknown)}. Available: {available}")

    resolved: list[str] = []
    for scope in requested:
        if scope == "all":
            resolved.extend(scopes["all"]["includes"])
        else:
            resolved.append(scope)
    return list(dict.fromkeys(resolved))


def npm() -> str:
    return "npm.cmd" if os.name == "nt" else "npm"


def maven() -> str:
    return "mvn.cmd" if os.name == "nt" else "mvn"


def python_executable() -> str:
    return sys.executable


def ensure_node_dependencies() -> None:
    eslint = ROOT / "node_modules" / ".bin" / ("eslint.cmd" if os.name == "nt" else "eslint")
    if not eslint.exists():
        run([npm(), "ci"])


def ensure_python_lint_dependencies(scope_names: list[str]) -> None:
    if not {"python", "yaml"}.intersection(scope_names):
        return

    missing = False
    for module in ("ruff", "yamllint"):
        try:
            subprocess.run(
                [python_executable(), "-m", module, "--version"],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
        except (FileNotFoundError, subprocess.CalledProcessError):
            missing = True
            break

    if missing:
        run([python_executable(), "-m", "pip", "install", "-r", str(REQUIREMENTS_LINT)])


def run_maven_goals(goals: list[str]) -> None:
    run([maven(), *goals])


def run_npm_scripts(scripts: list[str]) -> None:
    ensure_node_dependencies()
    for script in scripts:
        run([npm(), "run", script])


def run_command(command: list[str]) -> None:
    resolved = [python_executable() if part == "python" else part for part in command]
    run(resolved)


def run_docker_scope(scope: dict[str, Any]) -> None:
    docker = shutil.which("docker")
    if docker is None:
        raise RuntimeError(
            "Docker is required for the docker scope but was not found on PATH."
        )

    command = [docker, *scope["command"][1:]]
    for dockerfile in scope["dockerfiles"]:
        path = ROOT / dockerfile
        if not path.exists():
            raise FileNotFoundError(f"Dockerfile not found: {path}")
        run(command, stdin=path.read_bytes())


def run_scope(config: dict[str, Any], scope_name: str) -> None:
    scope = config["scopes"][scope_name]
    print(f"==> Lint scope: {scope_name} ({scope['description']})", flush=True)

    if "mavenGoals" in scope:
        run_maven_goals(scope["mavenGoals"])
        return

    if "npmScripts" in scope:
        run_npm_scripts(scope["npmScripts"])
        return

    if scope_name == "docker":
        run_docker_scope(scope)
        return

    if "command" in scope:
        run_command(scope["command"])
        return

    raise ValueError(f"Scope '{scope_name}' has no runnable configuration.")


def list_scopes(config: dict[str, Any]) -> None:
    print("Available lint scopes:")
    for name in sorted(config["scopes"]):
        scope = config["scopes"][name]
        if name == "all":
            includes = ", ".join(scope["includes"])
            print(f"  {name:10} {scope['description']} (includes: {includes})")
        else:
            print(f"  {name:10} {scope['description']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--scope",
        action="append",
        dest="scopes",
        help="Lint scope to run (repeatable). Defaults to all scopes.",
    )
    parser.add_argument(
        "--list-scopes",
        action="store_true",
        help="List configured lint scopes and exit.",
    )
    args = parser.parse_args()
    config = load_config()

    if args.list_scopes:
        list_scopes(config)
        return 0

    try:
        scope_names = resolve_scope_names(config, args.scopes or ["all"])
        ensure_python_lint_dependencies(scope_names)
        for scope_name in scope_names:
            run_scope(config, scope_name)
    except (FileNotFoundError, RuntimeError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Linting failed: {error}", file=sys.stderr)
        return 1

    print("Linting passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
