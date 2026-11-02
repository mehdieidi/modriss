import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock
from botocore.exceptions import ClientError, ReadTimeoutError
import yaml

from runner import Worker
from template_validation import (
    apply_floci_test_profile,
    validate_templates,
    validate_emulator_compatibility,
    order_templates,
)


class TemplateValidationTest(unittest.TestCase):
    def test_ambiguous_cloudformation_create_recovers_without_resubmitting(self):
        worker = Worker({"accountId":"123456789012", "projectId":"project", "jobId":"job"})
        cfn = MagicMock()
        cfn.create_stack.side_effect = ReadTimeoutError(
            endpoint_url="http://aws-emulator:4566", error=TimeoutError("read timed out"))
        cfn.describe_stacks.return_value = {"Stacks":[{"StackStatus":"CREATE_IN_PROGRESS"}]}

        with contextlib.redirect_stdout(io.StringIO()):
            recovered = worker.submit_stack_operation(cfn, "create_stack", "stack", {"StackName":"stack"})

        self.assertEqual(recovered["StackStatus"], "CREATE_IN_PROGRESS")
        cfn.create_stack.assert_called_once_with(StackName="stack", OnFailure="DO_NOTHING")
        cfn.describe_stacks.assert_called_once_with(StackName="stack")

    def test_already_existing_stack_is_adopted_after_create_race(self):
        worker = Worker({"accountId":"123456789012", "projectId":"project", "jobId":"job"})
        cfn = MagicMock()
        cfn.create_stack.side_effect = ClientError(
            {"Error":{"Code":"AlreadyExistsException", "Message":"stack already exists"}}, "CreateStack")
        cfn.describe_stacks.return_value = {"Stacks":[{"StackStatus":"CREATE_IN_PROGRESS"}]}

        with contextlib.redirect_stdout(io.StringIO()):
            recovered = worker.submit_stack_operation(cfn, "create_stack", "stack", {"StackName":"stack"})

        self.assertEqual(recovered["StackStatus"], "CREATE_IN_PROGRESS")
        cfn.create_stack.assert_called_once()
        cfn.describe_stacks.assert_called_once_with(StackName="stack")

    def test_workflow_version_and_rotation_limits_are_reported_before_build(self):
        documents = {"template.yaml":{"Resources":{
            "Workflow":{"Type":"AWS::Serverless::StateMachine", "Properties":{"AutoPublishAlias":"live"}},
            "Rotation":{"Type":"AWS::SecretsManager::RotationSchedule", "Properties":{}},
            "Database":{"Type":"AWS::RDS::DBCluster", "Properties":{}}}}}
        issues = validate_emulator_compatibility(documents, "2.2.0")
        self.assertEqual([issue["resourceId"] for issue in issues], ["Workflow", "Rotation"])
        self.assertIn("Floci Test profile", issues[0]["remedy"])
        self.assertEqual(validate_emulator_compatibility(documents, "3.0.0"), [])

    def test_floci_profile_removes_sam_publication_but_keeps_workflow_definition(self):
        documents = {"template.yaml":{"Resources":{
            "Workflow":{"Type":"AWS::Serverless::StateMachine", "Properties":{
                "AutoPublishAlias":"live", "UseAliasAsEventTarget":True,
                "DeploymentPreference":{"Type":"LINEAR", "Percentage":10},
                "Definition":{"StartAt":"Done",
                "States":{"Done":{"Type":"Succeed"}}}}}}}}
        original = yaml.safe_dump(documents)

        profile = apply_floci_test_profile(documents, "2.2.0")

        workflow = documents["template.yaml"]["Resources"]["Workflow"]
        self.assertEqual(workflow["Type"], "AWS::Serverless::StateMachine")
        self.assertEqual(workflow["Properties"]["Definition"], {
            "StartAt":"Done", "States":{"Done":{"Type":"Succeed"}}})
        self.assertNotIn("AutoPublishAlias", workflow["Properties"])
        self.assertNotIn("UseAliasAsEventTarget", workflow["Properties"])
        self.assertNotIn("DeploymentPreference", workflow["Properties"])
        self.assertEqual(profile["id"], "floci-test")
        self.assertEqual(profile["adjustments"][0]["resourceId"], "Workflow")
        self.assertEqual(profile["adjustments"][0]["aliasName"], "live")
        self.assertEqual(profile["adjustments"][0]["removedFields"], [
            "AutoPublishAlias", "UseAliasAsEventTarget", "DeploymentPreference"])
        self.assertEqual(validate_emulator_compatibility(documents, "2.2.0"), [])
        self.assertEqual(yaml.safe_load(original)["template.yaml"]["Resources"]["Workflow"]["Properties"]["AutoPublishAlias"], "live")

    def test_floci_profile_marks_lambda_runtime_only_in_the_in_memory_template(self):
        documents = {"template.yaml":{"Resources":{
            "Function":{"Type":"AWS::Lambda::Function", "Properties":{
                "Environment":{"Variables":{"EXISTING":"preserved"}}}},
            "SamFunction":{"Type":"AWS::Serverless::Function", "Properties":{}},
        }}}
        original = yaml.safe_dump(documents)

        profile = apply_floci_test_profile(documents, "2.2.0")

        resources = documents["template.yaml"]["Resources"]
        self.assertEqual(resources["Function"]["Properties"]["Environment"]["Variables"], {
            "EXISTING":"preserved", "MODRISS_TEST_EMULATOR":"true"})
        self.assertEqual(resources["SamFunction"]["Properties"]["Environment"]["Variables"], {
            "MODRISS_TEST_EMULATOR":"true"})
        self.assertEqual(profile["testOnlyLambdaRuntimeMarkerCount"], 2)
        self.assertIn("saved AWS artifacts are unchanged", profile["syntheticFixtureIdentity"])
        self.assertNotIn("MODRISS_TEST_EMULATOR", original)

    def test_floci_profile_keeps_explicit_alias_resources_visible_as_unsupported(self):
        documents = {"template.yaml":{"Resources":{
            "Version":{"Type":"AWS::StepFunctions::StateMachineVersion", "Properties":{}},
            "Alias":{"Type":"AWS::StepFunctions::StateMachineAlias", "Properties":{}}}}}
        profile = apply_floci_test_profile(documents, "2.2.0")
        issues = validate_emulator_compatibility(documents, "2.2.0")
        self.assertEqual(profile["adjustments"], [])
        self.assertEqual({issue["resourceId"] for issue in issues}, {"Version", "Alias"})
        self.assertTrue(all("cannot be safely rewritten" in issue["remedy"] for issue in issues))

    def test_floci_profile_omits_only_unreferenced_eventbridge_api_destinations(self):
        documents = {"template.yaml":{"Resources":{
            "Connection":{"Type":"AWS::Events::Connection", "Properties":{"Name":"unused"}},
            "Destination":{"Type":"AWS::Events::ApiDestination", "Properties":{
                "ConnectionArn":{"Fn::GetAtt":["Connection", "Arn"]}}},
            "Function":{"Type":"AWS::Lambda::Function", "Properties":{"Code":{"ZipFile":"handler"}}},
        }}}

        profile = apply_floci_test_profile(documents, "2.2.0")

        self.assertEqual(set(documents["template.yaml"]["Resources"]), {"Function"})
        self.assertEqual({item["resourceId"] for item in profile["adjustments"]}, {"Connection", "Destination"})
        self.assertTrue(all("saved AWS artifact is unchanged" in item["effect"] for item in profile["adjustments"]))
        self.assertEqual(validate_emulator_compatibility(documents, "2.2.0"), [])

    def test_floci_profile_preserves_and_reports_referenced_eventbridge_api_destinations(self):
        documents = {"template.yaml":{"Resources":{
            "Connection":{"Type":"AWS::Events::Connection", "Properties":{"Name":"used"}},
            "Destination":{"Type":"AWS::Events::ApiDestination", "Properties":{
                "ConnectionArn":{"Fn::GetAtt":["Connection", "Arn"]}}},
            "Rule":{"Type":"AWS::Events::Rule", "Properties":{"Targets":[{
                "Arn":{"Fn::GetAtt":["Destination", "Arn"]}}]}},
        }}}

        profile = apply_floci_test_profile(documents, "2.2.0")
        issues = validate_emulator_compatibility(documents, "2.2.0")

        self.assertEqual(profile["adjustments"], [])
        self.assertEqual(set(documents["template.yaml"]["Resources"]), {"Connection", "Destination", "Rule"})
        self.assertEqual({issue["resourceId"] for issue in issues}, {"Connection", "Destination"})
        self.assertTrue(all("unchanged AWS artifact" in issue["remedy"] for issue in issues))

    def test_deploy_expands_sam_without_publishing_workflow_alias_and_records_profile(self):
        source = yaml.safe_dump({
            "Transform":"AWS::Serverless-2016-10-31",
            "Resources":{"Workflow":{"Type":"AWS::Serverless::StateMachine", "Properties":{
                "Name":"alias-profile-test", "Role":"arn:aws:iam::123456789012:role/test",
                "Definition":{"StartAt":"Done", "States":{"Done":{"Type":"Succeed"}}},
                "AutoPublishAlias":"live", "UseAliasAsEventTarget":True,
                "DeploymentPreference":{"Type":"LINEAR", "Percentage":10}}}}})
        files = {"template.yaml":source}
        with tempfile.TemporaryDirectory() as directory:
            worker = Worker({"accountId":"123456789012", "projectId":"alias-profile-test",
                "jobId":"job", "files":files})
            worker.root = Path(directory)
            worker.emulator_version = "2.2.0"
            missing = lambda: ClientError(
                {"Error":{"Code":"ValidationError", "Message":"Stack does not exist"}}, "DescribeStacks")
            cfn = MagicMock()
            cfn.describe_stacks.side_effect = [missing(), missing(),
                {"Stacks":[{"StackStatus":"CREATE_COMPLETE", "Outputs":[],
                    "StackId":"arn:aws:cloudformation:us-east-1:123456789012:stack/test/id"}]}]
            cfn.describe_stack_events.return_value = {"StackEvents":[]}
            cfn.list_stack_resources.return_value = {"StackResourceSummaries":[]}
            worker.client = lambda service: {"cloudformation":cfn, "iam":MagicMock(), "s3":MagicMock()}[service]

            with contextlib.redirect_stdout(io.StringIO()) as output:
                worker.deploy()

            sent = json.loads(cfn.create_stack.call_args.kwargs["TemplateBody"])
            deployed_types = {resource["Type"] for resource in sent["Resources"].values()}
            self.assertIn("AWS::StepFunctions::StateMachine", deployed_types)
            self.assertNotIn("AWS::StepFunctions::StateMachineVersion", deployed_types)
            self.assertNotIn("AWS::StepFunctions::StateMachineAlias", deployed_types)
            self.assertIn("AutoPublishAlias", yaml.safe_load(source)["Resources"]["Workflow"]["Properties"])
            events = [json.loads(line) for line in output.getvalue().splitlines() if line.startswith("{")]
            profile_event = next(event for event in events if event["type"] == "deployment-profile")
            self.assertEqual(profile_event["profile"]["adjustments"][0]["resourceId"], "Workflow")
            result_event = next(event for event in events if event["type"] == "result")
            self.assertEqual(result_event["result"]["deploymentProfile"]["id"], "floci-test")

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
            cfn.list_stack_resources.return_value = {"StackResourceSummaries":[
                {"LogicalResourceId":"RetainedTable", "ResourceStatus":"DELETE_SKIPPED",
                 "PhysicalResourceId":"retained-table"}]}
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
