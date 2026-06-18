#!/usr/bin/env python3
"""Format or check every supported source file using pinned repository settings."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "formatting.json"


def run(command: list[str]) -> None:
    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def normalized_files(config: dict[str, object]) -> list[Path]:
    excluded = set(config["excludedDirectories"])
    extensions = set(config["normalizedExtensions"])
    files: list[Path] = []

    for path in ROOT.rglob("*"):
        relative_parts = path.relative_to(ROOT).parts
        if any(part in excluded for part in relative_parts):
            continue
        if path.is_file() and path.suffix.lower() in extensions:
            files.append(path)

    return sorted(files)


def normalized_content(path: Path) -> str:
    content = path.read_text(encoding="utf-8")
    lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    return "\n".join(line.rstrip() for line in lines).rstrip("\n") + "\n"


def normalize_text_files(config: dict[str, object], check: bool) -> bool:
    changed: list[Path] = []
    for path in normalized_files(config):
        normalized = normalized_content(path)
        if path.read_text(encoding="utf-8") == normalized:
            continue
        changed.append(path.relative_to(ROOT))
        if not check:
            path.write_text(normalized, encoding="utf-8", newline="\n")

    if changed:
        action = "Require formatting" if check else "Normalized"
        print(f"{action} {len(changed)} domain/config files:")
        for path in changed:
            print(f"  {path}")
    return not changed


def prettier_command(config: dict[str, object], check: bool) -> list[str]:
    npm = "npm.cmd" if os.name == "nt" else "npm"
    script = "format:prettier:check" if check else "format:prettier"
    return [npm, "run", script, "--", *config["prettierPatterns"]]


def ensure_node_dependencies() -> None:
    prettier = ROOT / "node_modules" / ".bin" / ("prettier.cmd" if os.name == "nt" else "prettier")
    if not prettier.exists():
        npm = "npm.cmd" if os.name == "nt" else "npm"
        run([npm, "ci"])


def maven_command(check: bool) -> list[str]:
    maven = "mvn.cmd" if os.name == "nt" else "mvn"
    return [maven, "spotless:check" if check else "spotless:apply"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check without changing files.")
    args = parser.parse_args()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    try:
        run(maven_command(args.check))
        ensure_node_dependencies()
        run(prettier_command(config, args.check))
        normalized = normalize_text_files(config, args.check)
    except (FileNotFoundError, subprocess.CalledProcessError) as error:
        print(f"Formatting failed: {error}", file=sys.stderr)
        return 1

    if args.check and not normalized:
        return 1
    print("Formatting check passed." if args.check else "Formatting complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
