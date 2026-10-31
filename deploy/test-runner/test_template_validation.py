import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock
import yaml

from runner import Worker
from template_validation import validate_templates, validate_emulator_compatibility, order_templates


class TemplateValidationTest(unittest.TestCase):
    def test_workflow_version_and_rotation_limits_are_reported_before_build(self):
        documents = {"template.yaml":{"Resources":{
            "Workflow":{"Type":"AWS::Serverless::StateMachine", "Properties":{"AutoPublishAlias":"live"}},
            "Rotation":{"Type":"AWS::SecretsManager::RotationSchedule", "Properties":{}},
            "Database":{"Type":"AWS::RDS::DBCluster", "Properties":{}}}}}
        issues = validate_emulator_compatibility(documents, "2.2.0")
        self.assertEqual([issue["resourceId"] for issue in issues], ["Workflow", "Rotation"])
        self.assertIn("does not silently remove", issues[0]["remedy"])
        self.assertEqual(validate_emulator_compatibility(documents, "3.0.0"), [])
    def test_shared_pool_owner_is_deployed_before_importing_api(self):
        documents = {"api.yaml":{"Resources":{"Api":{"Properties":{"ProviderARNs":[{"Fn::ImportValue":"pool-Arn"}]}}}},
                     "identity.yaml":{"Outputs":{"Pool":{"Export":{"Name":"pool-Arn"}}}}}
        self.assertEqual(order_templates(documents), ["identity.yaml", "api.yaml"])

    def test_cyclic_imports_fail_before_provisioning(self):
        documents = {name:{"Outputs":{"Export":{"Export":{"Name":name}}},
                           "Resources":{"Ref":{"Fn::ImportValue":other}}}
                     for name, other in [("a","b"),("b","a")]}
        with self.assertRaisesRegex(ValueError,"Cyclic"):
            order_templates(documents)

    def check(self, resources):
        dependencies = {"Api":{"Type":"AWS::ApiGateway::RestApi"}, "Pool":{"Type":"AWS::Cognito::UserPool"},
                        "Validator":{"Type":"AWS::Lambda::Function"}}
        return validate_templates({"template.yaml": {"Resources": {**dependencies, **resources}}})

    def test_dangling_storage_and_foreign_stack_references_are_actionable(self):
        issues = self.check({"Function":{"Type":"AWS::Lambda::Function","Properties":{
            "Environment":{"Variables":{"STORE":{"Ref":"UnmappedStore"}}}}}})
        self.assertEqual(len(issues), 1)
        self.assertIn("UnmappedStore", issues[0]["message"])
        self.assertIn("another stack", issues[0]["remedy"])

    def test_pool_deletion_protection_uses_the_aws_string_enum(self):
        resource = {"Pool":{"Type":"AWS::Cognito::UserPool","Properties":{"DeletionProtection":True}}}
        self.assertEqual(self.check(resource)[0]["field"], "DeletionProtection")
        resource["Pool"]["Properties"]["DeletionProtection"] = "ACTIVE"
        self.assertEqual(self.check(resource), [])

    def test_http_subscription_needs_a_url_instead_of_adapter_metadata_arn(self):
        resource = {"Subscription":{"Type":"AWS::SNS::Subscription","Properties":{
            "Protocol":"https","Endpoint":{"Fn::GetAtt":["Validator","Arn"]}}}}
        self.assertEqual(self.check(resource)[0]["field"], "Endpoint")
        resource["Subscription"]["Properties"]["Endpoint"] = "https://sandbox.example.test/events"
        self.assertEqual(self.check(resource), [])
        resource["Subscription"]["Properties"]["Endpoint"] = {"Fn::Sub":"https://${AWS::Region}.example.test/events"}
        self.assertEqual(self.check(resource), [])

    def test_original_tags_only_authorizer_names_missing_fields(self):
        issues = self.check({"AuthenticationAuthorizer": {"Type": "AWS::ApiGateway::Authorizer",
            "Properties": {"Tags": [{"Key": "Environment", "Value": "dev"}]}}})
        self.assertEqual({issue["field"] for issue in issues}, {"RestApiId", "Name", "Type", "Tags"})
        self.assertTrue(all(issue["resourceId"] == "AuthenticationAuthorizer" for issue in issues))

    def test_preflight_fails_before_building_or_provisioning(self):
        files = {"template.yaml": yaml.safe_dump({"Resources": {"Broken": {
            "Type": "AWS::ApiGateway::Authorizer", "Properties": {"Tags": []}}}}),
            "scripts/build.sh": "exit 99"}
        with tempfile.TemporaryDirectory() as directory:
            worker = Worker({"accountId":"123456789012", "projectId":"project", "jobId":"job", "files":files})
            worker.root = Path(directory)
            worker.command, worker.client = MagicMock(), MagicMock()
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError,"Broken.*RestApiId"):
                worker.deploy()
            worker.command.assert_not_called()
            worker.client.assert_not_called()

    def test_older_emulator_cannot_silently_accept_unenforced_cognito_authentication(self):
        template = {"Resources":{"Api":{"Type":"AWS::ApiGateway::RestApi"},"Auth":{"Type":"AWS::ApiGateway::Authorizer","Properties":{
            "RestApiId":{"Ref":"Api"},"Name":"auth","Type":"COGNITO_USER_POOLS", "IdentitySource":"method.request.header.Authorization",
            "ProviderARNs":["arn:aws:cognito-idp:us-east-1:123456789012:userpool/test"]}}}}
        with tempfile.TemporaryDirectory() as directory:
            worker = Worker({"accountId":"123456789012", "projectId":"project", "jobId":"job",
                "files":{"template.yaml":yaml.safe_dump(template),"scripts/build.sh":"exit 99"}})
            worker.root = Path(directory)
            worker.emulator_version = "2.0.1"
            worker.command, worker.client = MagicMock(), MagicMock()
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError,"Floci 2.2.0"):
                worker.deploy()
            worker.command.assert_not_called()
            worker.client.assert_not_called()

    def test_legacy_rollback_requires_recovery_before_build_without_deleting_data(self):
        with tempfile.TemporaryDirectory() as directory:
            worker = Worker({"accountId":"123456789012", "projectId":"project", "jobId":"job",
                "files":{"template.yaml":"Resources: {}", "scripts/build.sh":"exit 99"}})
            worker.root = Path(directory)
            worker.command = MagicMock()
            cfn = MagicMock()
            cfn.describe_stacks.return_value = {"Stacks":[{"StackStatus":"ROLLBACK_COMPLETE"}]}
            worker.client = MagicMock(return_value=cfn)
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError,"retained resources"):
                worker.deploy()
            worker.command.assert_not_called()
            cfn.delete_stack.assert_not_called()
            cfn.update_stack.assert_not_called()

    def test_native_jwt_and_path_as_resource_id_are_rejected_for_rest(self):
        issues = self.check({"Route":{"Type":"AWS::ApiGateway::Method", "Properties": {
            "RestApiId":{"Ref":"Api"},"ResourceId":"/grants","HttpMethod":"POST","AuthorizationType":"JWT"}}})
        self.assertEqual({issue["field"] for issue in issues},{"ResourceId","AuthorizationType"})

    def test_valid_rest_cognito_and_lambda_authorizers(self):
        base={"RestApiId":{"Ref":"Api"},"Name":"authentication", "IdentitySource":"method.request.header.Authorization"}
        self.assertEqual(self.check({
            "Cognito":{"Type":"AWS::ApiGateway::Authorizer", "Properties":{**base,"Type":"COGNITO_USER_POOLS","ProviderARNs":[{"Fn::GetAtt":["Pool","Arn"]}]}},
            "Lambda":{"Type":"AWS::ApiGateway::Authorizer", "Properties":{**base,"Type":"TOKEN","AuthorizerUri":{"Fn::Join":["",["arn:aws:apigateway:","/invocations"]]}}}}),[])

    def test_http_jwt_uses_audience_list_and_real_issuer_url(self):
        props={"ApiId":{"Ref":"Api"},"Name":"jwt","AuthorizerType":"JWT",
               "IdentitySource":["$request.header.Authorization"],"JwtConfiguration":{"Issuer":"https://issuer.example.com","Audience":["client"]}}
        self.assertEqual(self.check({"Auth":{"Type":"AWS::ApiGatewayV2::Authorizer","Properties":props}}),[])
        props["JwtConfiguration"]={"Issuer":"validate token signature and expiry","Audience":[]}
        self.assertEqual({issue["field"] for issue in self.check({"Auth":{"Type":"AWS::ApiGatewayV2::Authorizer","Properties":props}})}, {"Issuer","Audience"})

    def test_openapi_bearer_declaration_requires_executable_authorizer(self):
        body={"openapi":"3.0.3","paths":{"/grants":{"get":{"security":[{"jwt":[]}]}}},
              "components":{"securitySchemes":{"jwt":{"type":"http","scheme":"bearer"}}}}
        resources={"Api":{"Type":"AWS::Serverless::HttpApi","Properties":{"DefinitionBody":body}}}
        self.assertEqual(self.check(resources)[0]["field"],"securitySchemes.jwt")
        body["components"]["securitySchemes"]["jwt"]["x-amazon-apigateway-authorizer"]={
            "type":"jwt","jwtConfiguration":{"issuer":"https://issuer.example.com","audience":["client"]}}
        self.assertEqual(self.check(resources),[])

    def test_sam_authorizer_definition_is_a_valid_security_binding(self):
        self.assertEqual(self.check({"Api":{"Type":"AWS::Serverless::Api","Properties":{
            "Auth":{"Authorizers":{"Token":{"FunctionArn":{"Fn::GetAtt":["Validator","Arn"]}}}},
            "DefinitionBody":{"paths":{"/grants":{"get":{"security":[{"Token":[]}]}}}}}}}),[])


if __name__ == "__main__":
    unittest.main()
