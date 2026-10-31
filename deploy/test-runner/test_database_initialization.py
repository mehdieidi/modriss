"""Test the generated initializer's transaction and data-preservation contract."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest


class DatabaseInitializationTest(unittest.TestCase):
    def setUp(self):
        source = Path("/methodology/modeling-framework/generation/awspsm-to-artifacts/templates/scripts/database.egl")
        if not source.is_file():
            source = Path(__file__).resolve().parents[2] / "methodology/modeling-framework/generation/awspsm-to-artifacts/templates/scripts/database.egl"
        namespace = {"__name__":"initializer_test"}
        exec(compile(source.read_text().split("%]", 1)[1], str(source), "exec"), namespace)
        self.initialize = namespace["initialize"]
        self.resources = [
            {"LogicalResourceId":"Database", "ResourceType":"AWS::RDS::DBCluster", "PhysicalResourceId":"database"},
            {"LogicalResourceId":"Credentials", "ResourceType":"AWS::SecretsManager::Secret", "PhysicalResourceId":"arn:secret"}]
        self.manifest = {"version":1, "databases":[{"resource":"Database", "secret":"Credentials", "database":"app",
                                                  "statements":["CREATE TABLE items (id text PRIMARY KEY)"]}]}
        self.calls = []

    def call(self, service, operation, **parameters):
        self.calls.append((service, operation, parameters))
        if operation == "describe_db_clusters":
            return {"DBClusters":[{"DBClusterArn":"arn:database"}]}
        if operation == "begin_transaction":
            return {"transactionId":"transaction"}
        return {}

    def run_initialization(self, call=None):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(self.manifest))
            self.initialize(self.resources, str(path), call or self.call)

    def test_schema_and_fingerprint_commit_in_the_same_transaction(self):
        self.run_initialization()
        writes = [parameters for _, operation, parameters in self.calls if operation == "execute_statement"
                  and parameters.get("transactionId")]
        self.assertEqual(len(writes), 2)
        self.assertTrue(writes[0]["sql"].startswith("CREATE TABLE items"))
        self.assertEqual(self.calls[-1][1], "commit_transaction")

    def test_failed_ddl_rolls_back_without_committing(self):
        def call(service, operation, **parameters):
            if parameters.get("sql", "").startswith("CREATE TABLE items"):
                raise RuntimeError("invalid schema")
            return self.call(service, operation, **parameters)
        with self.assertRaisesRegex(RuntimeError, "invalid schema"):
            self.run_initialization(call)
        self.assertEqual(self.calls[-1][1], "rollback_transaction")
        self.assertFalse(any(operation == "commit_transaction" for _, operation, _ in self.calls))

    def test_changed_schema_is_rejected_without_mutating_application_tables(self):
        def call(service, operation, **parameters):
            result = self.call(service, operation, **parameters)
            if parameters.get("sql", "").startswith("SELECT fingerprint"):
                return {"records":[[{"stringValue":"older-fingerprint"}]]}
            return result
        with self.assertRaisesRegex(RuntimeError, "reviewed database migration"):
            self.run_initialization(call)
        self.assertFalse(any(operation == "begin_transaction" for _, operation, _ in self.calls))

    def test_partial_deployment_does_not_initialize_unselected_database(self):
        self.resources = []
        self.run_initialization()
        self.assertEqual(self.calls, [])

    def test_missing_credentials_fail_before_sql(self):
        self.resources.pop()
        with self.assertRaisesRegex(ValueError, "Credentials"):
            self.run_initialization()
        self.assertFalse(any(service == "rds-data" for service, _, _ in self.calls))


if __name__ == "__main__":
    unittest.main()
