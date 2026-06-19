#!/usr/bin/env python3
"""Verify Flyway migrations apply from an empty PostgreSQL database (CI helper)."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    maven = "mvn.cmd" if os.name == "nt" else "mvn"
    subprocess.run(
        [
            maven,
            "-q",
            "-pl",
            "packages/java/platform-storage-postgres",
            "-am",
            "test",
            "-Dtest=FlywayMigrationVerificationTest",
        ],
        cwd=ROOT,
        check=True,
    )
    print("flyway-verify: migrations applied successfully from empty database", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
