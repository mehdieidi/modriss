#!/usr/bin/env python3
"""Check Flyway migration version safety across all backend migration locations."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCATIONS = [
    ROOT / "packages/java/platform-storage-postgres/src/main/resources/db/migration",
    ROOT / "packages/java/platform-assistant/src/main/resources/db/assistant-migration",
]
MIGRATION_RE = re.compile(r"^V(?P<version>\d+)__(?P<description>[A-Za-z0-9_]+)\.sql$")


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def all_migration_paths() -> list[Path]:
    paths: list[Path] = []
    for directory in LOCATIONS:
      paths.extend(sorted(directory.glob("V*.sql")))
    return paths


def added_paths() -> set[Path]:
    paths: set[Path] = set()
    commands = [
        ["git", "diff", "--name-only", "--diff-filter=AR", "--cached"],
        ["git", "diff", "--name-only", "--diff-filter=AR"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    ]
    for command in commands:
        try:
            result = subprocess.run(
                command,
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
        except (OSError, subprocess.CalledProcessError):
            continue
        for line in result.stdout.splitlines():
            path = (ROOT / line).resolve()
            if any(path.is_relative_to(directory.resolve()) for directory in LOCATIONS):
                paths.add(path)
    return paths


def version(path: Path) -> int | None:
    match = MIGRATION_RE.match(path.name)
    if not match:
        return None
    return int(match.group("version"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--changed-only",
        action="store_true",
        help="Require only newly added migration files to be above the existing maximum",
    )
    args = parser.parse_args()

    paths = all_migration_paths()
    errors: list[str] = []
    versions: dict[Path, int] = {}
    by_version: dict[int, list[Path]] = defaultdict(list)

    for path in paths:
        parsed = version(path)
        if parsed is None:
            errors.append(f"invalid Flyway migration filename: {rel(path)}")
            continue
        versions[path.resolve()] = parsed
        by_version[parsed].append(path)

    for migration_version, migration_paths in sorted(by_version.items()):
        if len(migration_paths) > 1:
            joined = ", ".join(rel(path) for path in migration_paths)
            errors.append(f"duplicate Flyway version V{migration_version}: {joined}")

    added = added_paths() if args.changed_only else set()
    if added:
        baseline_versions = [
            migration_version
            for path, migration_version in versions.items()
            if path not in added
        ]
        baseline_max = max(baseline_versions, default=0)
        added_versions = sorted((versions[path], path) for path in added if path in versions)
        for migration_version, path in added_versions:
            if migration_version <= baseline_max:
                errors.append(
                    "new migration must be above the existing global maximum "
                    f"V{baseline_max}: {rel(path)} uses V{migration_version}"
                )
    elif args.changed_only:
        print("flyway-version-check: no new migration files detected")

    if errors:
        for error in errors:
            print(f"flyway-version-check: {error}", file=sys.stderr)
        print(
            "flyway-version-check: use scripts/flyway-next-migration.py to allocate the next version",
            file=sys.stderr,
        )
        return 1

    current_max = max(by_version.keys(), default=0)
    print(f"flyway-version-check: current global max is V{current_max}; next is V{current_max + 1}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
