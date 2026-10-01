import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("verify", Path(__file__).parents[1] / "verify.py")
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


class MavenArgumentsTest(unittest.TestCase):
    def test_quoted_maven_arguments_reach_subprocess_intact(self):
        argv = [
            "verify.py", "--skip-format", "--skip-lint",
            "--maven-args=-Dmessage=\"hello world\" '-Dpath=C:/build directory' -pl apps/backend",
        ]
        with patch.object(sys, "argv", argv), patch.object(VERIFY, "run") as run:
            self.assertEqual(0, VERIFY.main())
        self.assertEqual(
            [VERIFY.maven(), "test", "-Dmessage=hello world", "-Dpath=C:/build directory",
             "-pl", "apps/backend"],
            run.call_args.args[0],
        )

    def test_unbalanced_quotes_fail(self):
        argv = ["verify.py", "--skip-format", "--skip-lint", '--maven-args=-Dmessage="broken']
        with (patch.object(sys, "argv", argv), patch.object(VERIFY, "run"),
              self.assertRaises(ValueError)):
            VERIFY.main()
