"""Focused worker checks; run inside the worker image with this directory mounted read-only."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock
import zipfile

from runner import CfnLoader, Worker
from botocore.exceptions import ClientError
import yaml


class WorkerTest(unittest.TestCase):
    def worker(self, **request):
        return Worker({"accountId": "123456789012", "projectId": "project", "jobId": "job", **request})

    def test_relational_query_uses_saved_database_identity_and_retains_parameters(self):
        database = {"id":"database", "PhysicalResourceId":"database", "ResourceType":"AWS::RDS::DBCluster",
                    "actions":["query"], "database":{"resourceArn":"arn:database", "secretArn":"arn:secret", "database":"app"}}
        parameters = [{"name":"id", "value":{"stringValue":"case-1"}}]
        worker = self.worker(resources=[database], resourceId="database", action="query",
                             input={"sql":"SELECT :id AS id", "parameters":parameters, "transactionId":"transaction",
                                    "resourceArn":"another-database"})
        worker.call = MagicMock(return_value={"columnMetadata":[{"name":"id"}], "records":[[{"stringValue":"case-1"}]]})
        with patch("runner.emit") as emitted:
            worker.execute()
        worker.call.assert_called_once_with("rds-data", "execute_statement", resourceArn="arn:database",
            secretArn="arn:secret", database="app", sql="SELECT :id AS id", includeResultMetadata=True,
            parameters=parameters, transactionId="transaction")
        self.assertEqual(emitted.call_args.kwargs["result"]["rows"], [{"id":"case-1"}])

    def test_relational_rollback_uses_the_selected_database_without_database_parameter(self):
        database = {"id":"database", "PhysicalResourceId":"database", "ResourceType":"AWS::RDS::DBCluster",
                    "actions":["rollback-transaction"], "database":{"resourceArn":"arn:database", "secretArn":"arn:secret", "database":"app"}}
        worker = self.worker(resources=[database], resourceId="database", action="rollback-transaction", input={"transactionId":"transaction"})
        worker.call = MagicMock(return_value={"transactionStatus":"Transaction Rolled Back"})
        with patch("runner.emit"):
            worker.execute()
        worker.call.assert_called_once_with("rds-data", "rollback_transaction", resourceArn="arn:database",
            secretArn="arn:secret", transactionId="transaction")

    def test_imported_rest_models_receive_real_schemas_and_local_reference_definitions(self):
        worker = self.worker()
        worker.client = MagicMock()
        worker.client.return_value.get_model.return_value = {"schema":"{}"}
        worker.call = MagicMock()
        body = {"components":{"schemas":{
            "Input":{"type":"object","properties":{"child":{"$ref":"#/components/schemas/Child"}},"required":["child"]},
            "Child":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}}}}
        worker.synchronize_rest_models("api", body)
        calls = worker.call.call_args_list
        self.assertEqual(len(calls), 2)
        self.assertTrue(all(call.args == ("apigateway", "update_model") for call in calls))
        updated = json.loads(calls[0].kwargs["patchOperations"][0]["value"])
        self.assertEqual(updated["properties"]["child"]["$ref"], "#/definitions/Child")
        self.assertEqual(updated["definitions"]["Child"]["required"], ["id"])
        self.assertEqual(body["components"]["schemas"]["Input"]["properties"]["child"]["$ref"], "#/components/schemas/Child")

    def test_unchanged_rest_model_is_retained_and_missing_model_is_created(self):
        worker = self.worker()
        worker.client = MagicMock()
        worker.client.return_value.get_model.side_effect = [
            {"schema":'{"type":"object"}'},
            ClientError({"Error":{"Code":"NotFoundException","Message":"not found"}},"GetModel")]
        worker.call = MagicMock()
        worker.synchronize_rest_models("api", {"components":{"schemas":{
            "Existing":{"type":"object"}, "Missing":{"type":"string"}}}})
        worker.call.assert_called_once_with("apigateway","create_model",restApiId="api",name="Missing",
            contentType="application/json",schema='{"type": "string"}')

    def test_rejects_non_emulator_endpoints(self):
        with patch.dict(os.environ, {"MODRISS_TEST_ENDPOINT": "https://lambda.us-east-1.amazonaws.com"}):
            with self.assertRaises(ValueError):
                self.worker()

    def test_project_paths_cannot_escape_the_snapshot(self):
        worker = self.worker()
        with self.assertRaises(ValueError):
            worker.local("../../outside")
        self.assertTrue(worker.local("src/handler.go").is_relative_to(worker.root))

    def test_api_paths_cannot_escape_the_resource_proxy(self):
        worker = self.worker()
        resource = {"PhysicalResourceId": "api", "ResourceType": "AWS::ApiGateway::RestApi", "stages": ["test"]}
        for path in ("https://example.com", "//example.com", "/../../_floci", "/%2e%2e/_floci", "/%252e%252e/admin", "/x?url=elsewhere"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                worker.http(resource, {"path": path, "stage": "test"})

    def test_bootstrap_archive_retains_linux_executable_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "function"
            path.mkdir()
            (path / "bootstrap").write_text("binary fixture")
            worker = self.worker()
            captured = []
            worker.call = lambda service, operation, **args: captured.append(args)
            worker.upload(path, "bucket", "job")
            with zipfile.ZipFile(io.BytesIO(captured[0]["Body"])) as archive:
                self.assertEqual(archive.getinfo("bootstrap").external_attr >> 16 & 0o777, 0o755)

    def test_api_authorization_reaches_the_application_unchanged(self):
        resource = {"PhysicalResourceId": "api", "ResourceType": "AWS::ApiGateway::RestApi", "stages": ["test"]}
        response = MagicMock(code=200, headers={})
        response.read.return_value = b'{}'
        for headers in ({"Authorization": "Bearer example"}, {"authorization": "Bearer expired"}, {}):
            with self.subTest(headers=headers), patch("urllib.request.build_opener") as factory:
                factory.return_value.open.return_value = response
                self.worker(jobId="run").http(resource, {"stage": "test", "headers": headers})
                sent = factory.return_value.open.call_args.args[0]
                self.assertEqual(sent.get_header("Authorization"), next(iter(headers.values()), None))
                self.assertIsNone(sent.get_header("X-amz-date"))

    def test_api_redirects_are_not_followed(self):
        import urllib.error
        resource = {"PhysicalResourceId": "api", "ResourceType": "AWS::ApiGatewayV2::Api", "stages": ["test"]}
        with patch("urllib.request.build_opener") as factory:
            redirect = urllib.error.HTTPError("http://aws-emulator:4566/", 302, "Found",
                                             {"Location": "https://example.com"}, io.BytesIO(b"redirect"))
            factory.return_value.open.side_effect = redirect
            result = self.worker().http(resource, {"stage": "test", "headers": {"Authorization": "Bearer example"}})
            self.assertEqual(result["statusCode"], 302)
            self.assertIsNone(factory.call_args.args[0].redirect_request(None, None, 302, "Found", {}, "https://example.com"))

    def test_aws_iam_signing_is_explicit_and_never_replaces_a_bearer_token(self):
        resource = {"PhysicalResourceId": "api", "ResourceType": "AWS::ApiGateway::RestApi", "stages": ["test"]}
        with self.assertRaisesRegex(ValueError, "cannot be combined"):
            self.worker().http(resource, {"stage":"test", "auth":"aws-iam", "headers":{"Authorization":"Bearer token"}})
        response = MagicMock(code=200, headers={})
        response.read.return_value = b'{}'
        with patch("urllib.request.build_opener") as factory:
            factory.return_value.open.return_value = response
            self.worker().http(resource, {"stage":"test", "auth":"aws-iam"})
            authorization = factory.return_value.open.call_args.args[0].get_header("Authorization")
            self.assertTrue(authorization.startswith("AWS4-HMAC-SHA256 "))
            self.assertIn("Credential=test/", authorization)

    def test_template_detection_accepts_cloudformation_without_optional_version(self):
        worker = self.worker(files={"infra/main.yml": "Resources: {Table: {Type: 'AWS::DynamoDB::Table'}}",
                                    "workflow.json": '{"States":{}}'})
        self.assertEqual(worker.template_paths(), ["infra/main.yml"])
        self.assertEqual(yaml.load("Value: !Sub 'a-${Name}'", Loader=CfnLoader)["Value"], {"Fn::Sub": "a-${Name}"})

    def test_actions_cannot_address_resources_from_another_deployment(self):
        with self.assertRaises(ValueError):
            self.worker(resourceId="another-project/resource").resource()

    def test_shared_emulator_default_account_is_rejected_before_deployment(self):
        worker = self.worker(kind="DEPLOY")
        worker.call = MagicMock(return_value={"Account":"123456789012"})
        worker.session = MagicMock()
        worker.session.client.return_value.get_caller_identity.return_value = {"Account":"000000000000"}
        with patch("urllib.request.urlopen") as health:
            health.return_value.__enter__.return_value = io.BytesIO(b'{"version":"test","services":{}}')
            with self.assertRaisesRegex(ValueError,"dedicated Floci"):
                worker.run()

    def test_empty_rollback_complete_stack_is_removed_before_retry(self):
        worker = self.worker()
        cfn = MagicMock()
        cfn.list_stack_resources.return_value = {"StackResourceSummaries": [
            {"LogicalResourceId": "DeletedTable", "ResourceStatus": "DELETE_COMPLETE",
             "PhysicalResourceId": "table"},
            {"LogicalResourceId": "RejectedBus", "ResourceStatus": "CREATE_FAILED"},
        ]}
        cfn.describe_stacks.side_effect = ClientError(
            {"Error": {"Code": "ValidationError", "Message": "Stack does not exist"}}, "DescribeStacks")
        worker.recover_rollback_complete_stack(cfn, "empty-stack")
        cfn.delete_stack.assert_called_once_with(StackName="empty-stack")

    def test_rollback_complete_stack_with_retained_resources_is_preserved(self):
        worker = self.worker()
        cfn = MagicMock()
        cfn.list_stack_resources.return_value = {"StackResourceSummaries": [
            {"LogicalResourceId": "RetainedTable", "ResourceStatus": "DELETE_SKIPPED",
             "PhysicalResourceId": "retained-table"},
        ]}
        with self.assertRaisesRegex(ValueError, "must be preserved"):
            worker.recover_rollback_complete_stack(cfn, "retained-stack")
        cfn.delete_stack.assert_not_called()

    def test_rest_routes_are_discovered_when_floci_omits_embedded_methods(self):
        api = MagicMock()
        api.get_resources.return_value = {"items":[{"id":"secure","path":"/secure"}]}
        def method(**args):
            if args["httpMethod"] == "GET":
                return {"httpMethod":"GET","authorizationType":"CUSTOM"}
            raise ClientError({"Error":{"Code":"NotFoundException","Message":"Invalid method specified"}},"GetMethod")
        api.get_method.side_effect = method
        self.assertEqual(self.worker().rest_routes(api,"api"),[{"path":"/secure","method":"GET"}])

    def test_rest_route_discovery_retains_pagination_and_does_not_hide_other_failures(self):
        api = MagicMock()
        api.get_resources.side_effect = [{"items":[{"id":"one","path":"/one","resourceMethods":{"POST":{}}}],"position":"next"},
                                         {"items":[{"id":"two","path":"/two","resourceMethods":{"GET":{}}}]}]
        self.assertEqual(self.worker().rest_routes(api,"api"),[{"path":"/one","method":"POST"},{"path":"/two","method":"GET"}])
        api.get_method.assert_not_called()
        api.get_resources.side_effect = None
        api.get_resources.return_value = {"items":[{"id":"secure","path":"/secure"}]}
        api.get_method.side_effect = ClientError({"Error":{"Code":"AccessDeniedException","Message":"Denied"}},"GetMethod")
        with self.assertRaises(ClientError):
            self.worker().rest_routes(api,"api")

    def test_code_only_redeployment_updates_real_lambda_without_replacing_storage(self):
        before = {"Resources": {"Function": {"Type": "AWS::Lambda::Function", "Properties": {
            "Runtime": "provided.al2023", "Code": {"S3Bucket": "source", "S3Key": "old"}}},
            "Table": {"Type": "AWS::DynamoDB::Table", "Properties": {"TableName": "orders"}}}}
        after = json.loads(json.dumps(before))
        after["Resources"]["Function"]["Properties"]["Code"]["S3Key"] = "new"
        worker = self.worker()
        class Cfn:
            def list_stack_resources(self, **kwargs):
                return {"StackResourceSummaries": [{"LogicalResourceId": "Function", "PhysicalResourceId": "deployed-function"}]}
        worker.client = lambda service: Cfn()
        calls = []
        worker.call = lambda *args, **kwargs: calls.append((args, kwargs))
        self.assertTrue(worker.update_lambda_packages(before, after, "stack"))
        self.assertEqual(calls[0], (("lambda", "update_function_code"),
                                   {"FunctionName": "deployed-function", "S3Bucket": "source", "S3Key": "new"}))
        after["Resources"]["Table"]["Properties"]["TableName"] = "changed-orders"
        self.assertFalse(worker.update_lambda_packages(before, after, "stack"))


if __name__ == "__main__":
    unittest.main()
