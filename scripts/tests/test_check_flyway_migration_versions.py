"""Regression coverage for migration version checks during module moves."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "check-flyway-migration-versions.py"
SPEC = importlib.util.spec_from_file_location("flyway_version_check", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class MigrationModuleMoveTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name).resolve()
        self.original_root = checker.ROOT
        self.original_locations = checker.LOCATIONS
        self.addCleanup(self.restore_checker)
        checker.ROOT = self.root
        self.platform = self.root / "packages/java/storage/src/main/resources/db/migration"
        self.original = self.root / "packages/java/original-assistant"
        self.renamed = self.root / "packages/java/renamed-assistant"
        self.assistant = self.renamed / "src/main/resources/db/assistant-migration"
        self.platform.mkdir(parents=True)
        original_migrations = self.original / "src/main/resources/db/assistant-migration"
        original_migrations.mkdir(parents=True)
        (self.platform / "V5__platform.sql").write_bytes(b"SELECT 5;\n")
        (original_migrations / "V2__assistant.sql").write_bytes(b"SELECT 2;\n")
        checker.LOCATIONS = [self.platform, self.assistant]
        self.git("init", "--quiet")
        self.git("config", "core.autocrlf", "false")
        self.git("add", ".")
        self.git(
            "-c", "user.name=Migration Test", "-c", "user.email=migration@example.test",
            "commit", "--quiet", "-m", "Baseline migrations",
        )
        self.original.rename(self.renamed)

    def restore_checker(self) -> None:
        checker.ROOT = self.original_root
        checker.LOCATIONS = self.original_locations

    def git(self, *arguments: str) -> None:
        subprocess.run(
            ["git", *arguments], cwd=self.root, check=True, capture_output=True,
        )

    def result(self) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            with mock.patch("sys.argv", [str(SCRIPT), "--changed-only"]):
                status = checker.main()
        return status, output.getvalue()

    def test_unstaged_unchanged_move_remains_in_baseline(self) -> None:
        self.assertEqual(set(), checker.added_paths())
        status, output = self.result()
        self.assertEqual(0, status, output)
        self.assertIn("current global max is V5; next is V6", output)

    def test_staged_unchanged_move_remains_in_baseline(self) -> None:
        self.git("add", "-A")
        self.assertEqual(set(), checker.added_paths())
        self.assertEqual(0, self.result()[0])

    def test_modified_move_still_requires_a_new_version(self) -> None:
        migration = self.assistant / "V2__assistant.sql"
        migration.write_bytes(b"SELECT 200;\n")
        self.assertIn(migration, checker.added_paths())
        status, output = self.result()
        self.assertEqual(1, status)
        self.assertIn("uses V2", output)

    def test_new_lower_version_is_rejected_after_move(self) -> None:
        migration = self.assistant / "V3__new_migration.sql"
        migration.write_bytes(b"SELECT 3;\n")
        self.assertEqual({migration}, checker.added_paths())
        status, output = self.result()
        self.assertEqual(1, status)
        self.assertIn("uses V3", output)

    def test_new_higher_version_is_accepted_after_move(self) -> None:
        migration = self.assistant / "V6__new_migration.sql"
        migration.write_bytes(b"SELECT 6;\n")
        self.assertEqual({migration}, checker.added_paths())
        status, output = self.result()
        self.assertEqual(0, status, output)


if __name__ == "__main__":
    unittest.main()
