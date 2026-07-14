#!/usr/bin/env python3
"""Create or print the next global Flyway migration filename."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCATIONS = {
    "platform": ROOT / "packages/java/platform-storage-postgres/src/main/resources/db/migration",
    "assistant": ROOT / "packages/java/platform-assistant/src/main/resources/db/assistant-migration",
}
MIGRATION_RE = re.compile(r"^V(?P<version>\d+)__(?P<description>[A-Za-z0-9_]+)\.sql$")


def slug(value: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9]+", "_", value.strip().lower()).strip("_")
    if not normalized:
        raise SystemExit("migration description must contain at least one alphanumeric character")
    return normalized


def migrations() -> list[tuple[int, Path]]:
    found: list[tuple[int, Path]] = []
    for directory in LOCATIONS.values():
        for path in directory.glob("V*.sql"):
            match = MIGRATION_RE.match(path.name)
            if match:
                found.append((int(match.group("version")), path))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("description", help="Human-readable migration description")
    parser.add_argument(
        "--location",
        choices=LOCATIONS.keys(),
        default="platform",
        help="Migration directory to create the file in",
    )
    parser.add_argument(
        "--print-only",
        action="store_true",
        help="Print the next path without creating the file",
    )
    args = parser.parse_args()

    next_version = max((version for version, _ in migrations()), default=0) + 1
    path = LOCATIONS[args.location] / f"V{next_version}__{slug(args.description)}.sql"

    if args.print_only:
        print(path.relative_to(ROOT).as_posix())
        return 0

    if path.exists():
        raise SystemExit(f"migration already exists: {path.relative_to(ROOT).as_posix()}")

    path.write_text("-- Add migration SQL here.\n", encoding="utf-8", newline="\n")
    print(f"created {path.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
