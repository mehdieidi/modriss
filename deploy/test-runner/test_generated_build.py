"""Verify dependency retries never hide a failed build or rerun failing tests."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class GeneratedBuildTest(unittest.TestCase):
    def run_build(self, failures, fail_test=False):
        template = Path("/methodology/modeling-framework/generation/awspsm-to-artifacts/templates/scripts/build.egl")
        if not template.is_file():
            template = Path(__file__).resolve().parents[2] / "methodology/modeling-framework/generation/awspsm-to-artifacts/templates/scripts/build.egl"
        script = template.read_text().split("%]", 1)[1].split("[% for", 1)[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build.sh").write_text(script)
            (root / "go").write_text('''#!/bin/sh
echo "$*" >> "$BUILD_CALLS"
if [ "$1" = "mod" ]; then
  count=0
  [ ! -f "$BUILD_COUNT" ] || count=$(cat "$BUILD_COUNT")
  count=$((count + 1))
  echo "$count" > "$BUILD_COUNT"
  [ "$count" -gt "$BUILD_FAILURES" ] || exit 1
elif [ "$BUILD_FAIL_TEST" = "1" ]; then
  exit 7
fi
''')
            (root / "sleep").write_text("#!/bin/sh\nexit 0\n")
            (root / "go").chmod(0o755)
            (root / "sleep").chmod(0o755)
            environment = {**os.environ, "PATH":str(root) + os.pathsep + os.environ["PATH"],
                           "BUILD_CALLS":str(root / "calls"), "BUILD_COUNT":str(root / "count"),
                           "BUILD_FAILURES":str(failures), "BUILD_FAIL_TEST":"1" if fail_test else "0"}
            result = subprocess.run(["bash", str(root / "build.sh")], env=environment, capture_output=True, text=True)
            return result, (root / "calls").read_text().splitlines()

    def test_transient_download_failures_retry_before_running_tests_once(self):
        result, calls = self.run_build(2)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls, ["mod tidy", "mod tidy", "mod tidy", "test ./..."])

    def test_exhausted_downloads_fail_without_running_tests(self):
        result, calls = self.run_build(3)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, ["mod tidy"] * 3)
        self.assertIn("failed after 3 attempts", result.stderr)

    def test_failed_tests_still_fail_without_retrying(self):
        result, calls = self.run_build(0, fail_test=True)
        self.assertEqual(result.returncode, 7)
        self.assertEqual(calls, ["mod tidy", "test ./..."])


if __name__ == "__main__":
    unittest.main()
