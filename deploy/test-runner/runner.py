"""Disposable Floci worker. JSON-lines are durable observations, never simulated results."""

import base64
import copy
import hashlib
import io
import json
import os
import re
import runpy
from pathlib import Path
import subprocess
import sys
import time
import traceback
from urllib.parse import quote, urlencode, urlparse, unquote
import zipfile

import boto3
from botocore import UNSIGNED
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.config import Config
from botocore.credentials import Credentials
from botocore.exceptions import (
    ClientError,
    ConnectionClosedError,
    ConnectTimeoutError,
    EndpointConnectionError,
    ReadTimeoutError,
)
import yaml
from template_validation import (
    apply_floci_test_profile,
    validate_templates,
    validate_emulator_compatibility,
    order_templates,
)


class CfnLoader(yaml.SafeLoader):
    pass


def intrinsic(loader, tag, node):
    value = (loader.construct_sequence(node) if isinstance(node, yaml.SequenceNode)
             else loader.construct_mapping(node) if isinstance(node, yaml.MappingNode)
             else loader.construct_scalar(node))
    return {tag if tag in ("Ref", "Condition") else "Fn::" + tag: value}


CfnLoader.add_multi_constructor("!", intrinsic)


def clean(value):
    if isinstance(value, dict):
        return {k: clean(v) for k, v in value.items() if k != "ResponseMetadata"}
    if isinstance(value, (list, tuple)):
        return [clean(v) for v in value]
    if isinstance(value, bytes):
        return {"base64": base64.b64encode(value).decode()}
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


def emit(kind, **data):
    print(json.dumps({"type": kind, "at": time.time(), **clean(data)}, default=str), flush=True)


class Worker:
    def __init__(self, request):
        self.request = request
        self.endpoint = os.environ.get("MODRISS_TEST_ENDPOINT", "http://aws-emulator:4566").rstrip("/")
        parsed = urlparse(self.endpoint)
        # The operator chooses the emulator address; project files cannot supply AWS endpoints.
        if parsed.scheme != "http" or parsed.hostname not in (
            "aws-emulator", "floci", "localhost", "127.0.0.1", "host.docker.internal"
        ) or parsed.port != 4566:
            raise ValueError("Test endpoint must be the local Floci HTTP service on port 4566")
        self.account = request["accountId"]
        self.region = request.get("region", "us-east-1")
        self.session = boto3.Session(aws_access_key_id=self.account,
                                     aws_secret_access_key="modriss-emulator-only",
                                     region_name=self.region)
        self.clients = {}
        self.root = Path.cwd().resolve()
        self.resources = request.get("resources", [])
        self.started = int(time.time() * 1000)

    def client(self, service):
        if service not in self.clients:
            # CloudFormation requests can take several minutes in Floci while it provisions
            # resources such as RDS. Retrying a timed-out CreateStack is unsafe: the first
            # request may have been accepted, and the retry then fails with AlreadyExists.
            # Make one transport attempt and recover the stack's state explicitly below.
            retries = {"total_max_attempts": 1} if service == "cloudformation" else {"max_attempts": 1}
            self.clients[service] = self.session.client(
                service, endpoint_url=self.endpoint,
                config=Config(connect_timeout=5, read_timeout=125,
                              retries=retries, s3={"addressing_style": "path"}))
        return self.clients[service]

    @staticmethod
    def _missing_stack(error):
        return error.response.get("Error", {}).get("Code") in (
            "ValidationError", "ResourceNotFoundException")

    def _recover_accepted_stack_operation(
        self, cfn, stack, operation, original_error, submitted_at, previous_status
    ):
        """Resolve a lost CloudFormation response without resubmitting a mutating request."""
        emit("stage", title=f"Checking whether Floci accepted CloudFormation {operation}",
             stack=stack, recovery="ambiguous-response")
        deadline = time.monotonic() + 45
        last_error = None
        while time.monotonic() < deadline:
            try:
                response = cfn.describe_stacks(StackName=stack)
                stacks = response.get("Stacks", [])
                if stacks:
                    info = stacks[0]
                    status = info.get("StackStatus", "")
                    accepted = operation == "create_stack" or status != previous_status
                    if not accepted and status.endswith("_IN_PROGRESS"):
                        accepted = True
                    if not accepted:
                        try:
                            events = cfn.describe_stack_events(StackName=stack).get("StackEvents", [])
                            accepted = any(
                                event.get("Timestamp") and event["Timestamp"].timestamp() >= submitted_at
                                and str(event.get("ResourceStatus", "")).startswith("UPDATE_")
                                for event in events
                            )
                        except (ClientError, ConnectionClosedError, ConnectTimeoutError,
                                EndpointConnectionError, ReadTimeoutError, OSError) as error:
                            last_error = error
                    if accepted:
                        emit("stage", title="Recovered the accepted CloudFormation operation",
                             stack=stack, status=status)
                        return info
            except ClientError as error:
                if not self._missing_stack(error):
                    last_error = error
            except (ConnectionClosedError, ConnectTimeoutError, EndpointConnectionError,
                    ReadTimeoutError, OSError) as error:
                last_error = error
            time.sleep(1)

        detail = f"; last status lookup failed: {last_error}" if last_error else ""
        raise RuntimeError(
            f"CloudFormation {operation} for stack {stack} returned an ambiguous transport error "
            f"({original_error}), and Floci did not expose the stack during 45 seconds of recovery{detail}. "
            "The request was not resubmitted to avoid duplicate resources. Inspect the project's Floci "
            "CloudFormation state before retrying deployment."
        ) from original_error

    def submit_stack_operation(self, cfn, operation, stack, arguments, previous_status=None):
        """Submit once; if the response is lost, adopt the stack and let the normal waiter continue."""
        submitted_at = time.time()
        try:
            emit("stage", title=f"Submitting CloudFormation {operation}", stack=stack)
            return getattr(cfn, operation)(**arguments,
                **({"OnFailure": "DO_NOTHING"} if operation == "create_stack" else {}))
        except ClientError as error:
            code = error.response.get("Error", {}).get("Code", "")
            if operation == "update_stack" and "No updates are to be performed" in str(error):
                return None
            if operation != "create_stack" or code not in ("AlreadyExistsException", "AlreadyExists"):
                raise
            # A previous or delayed request created it after our existence check. Do not
            # mistake that idempotency race for a failed deployment.
            return self._recover_accepted_stack_operation(
                cfn, stack, operation, error, submitted_at, previous_status)
        except (ConnectionClosedError, ConnectTimeoutError, EndpointConnectionError,
                ReadTimeoutError, OSError) as error:
            return self._recover_accepted_stack_operation(
                cfn, stack, operation, error, submitted_at, previous_status)

    def call(self, service, operation, **args):
        detail = {key: ({"bytes": len(value)} if isinstance(value, bytes)
                        else {"characters": len(value)} if key == "TemplateBody" else clean(value))
                  for key, value in args.items()}
        emit("stage", title=f"{service}.{operation}", detail=detail)
        result = getattr(self.client(service), operation)(**args)
        return result

    def local(self, path, base=None):
        target = ((base or self.root) / path).resolve()
        if not target.is_relative_to(self.root):
            raise ValueError(f"Path escapes the project: {path}")
        return target

    def materialize(self):
        for name, content in self.request.get("files", {}).items():
            path = self.local(name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.replace("\r\n", "\n") if name.endswith(".sh") else content, encoding="utf-8")

    def command(self, argv, variables=None):
        emit("stage", title="Running " + " ".join(argv))
        env = {**os.environ, "AWS_ACCESS_KEY_ID": self.account,
               "AWS_SECRET_ACCESS_KEY": "modriss-emulator-only", "AWS_SESSION_TOKEN": "",
               "AWS_DEFAULT_REGION": self.region, "AWS_REGION": self.region,
               "AWS_ENDPOINT_URL": self.endpoint, "AWS_EC2_METADATA_DISABLED": "true",
               "AWS_CONFIG_FILE": "/dev/null", "AWS_SHARED_CREDENTIALS_FILE": "/dev/null"}
        for key, value in (variables or {}).items():
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key) or key.startswith(("AWS_", "MODRISS_TEST_", "LD_")) or key in ("PATH", "HOME", "PYTHONPATH"):
                raise ValueError(f"Script variable {key} is reserved by the emulator runner")
            env[key] = str(value)
        proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, cwd=self.root, env=env)
        size = 0
        for line in proc.stdout:
            size += len(line)
            if size <= 750000:
                emit("log", message=line.rstrip())
        if proc.wait() != 0:
            raise RuntimeError(f"Command exited with status {proc.returncode}: {' '.join(argv)}")

    def template_paths(self):
        paths = []
        for name, body in self.request.get("files", {}).items():
            if not name.endswith((".yaml", ".yml", ".json")) or name.startswith((".aws-sam/", "node_modules/")):
                continue
            try:
                template = yaml.load(body, Loader=CfnLoader)
                if isinstance(template, dict) and isinstance(template.get("Resources"), dict):
                    paths.append(name)
            except yaml.YAMLError:
                continue
        return paths

    def upload(self, path, bucket, prefix):
        if path.is_dir():
            data = io.BytesIO()
            with zipfile.ZipFile(data, "w", zipfile.ZIP_DEFLATED) as archive:
                for file in sorted(path.rglob("*")):
                    if file.is_file():
                        entry = zipfile.ZipInfo(str(file.relative_to(path)).replace("\\", "/"))
                        entry.external_attr = (0o100755 if file.name == "bootstrap" or os.access(file, os.X_OK)
                                               else 0o100644) << 16
                        entry.compress_type = zipfile.ZIP_DEFLATED
                        archive.writestr(entry, file.read_bytes())
            content = data.getvalue()
        else:
            content = path.read_bytes()
        key = f"{prefix}/{hashlib.sha256(content).hexdigest()}/{path.name}"
        self.call("s3", "put_object", Bucket=bucket, Key=key, Body=content)
        return bucket, key

    def package(self, template, base, bucket, prefix):
        for resource in template.get("Resources", {}).values():
            props = resource.get("Properties", {})
            for field in ("CodeUri", "ContentUri", "DefinitionUri", "TemplateURL"):
                value = props.get(field)
                if not isinstance(value, str) or "://" in value:
                    continue
                path = self.local(value, base)
                if not path.exists():
                    raise ValueError(f"Missing {field}: {path.relative_to(self.root)}. Complete and build the project first.")
                b, k = self.upload(path, bucket, prefix)
                props[field] = f"s3://{b}/{k}" if field != "TemplateURL" else f"{self.endpoint}/{b}/{k}"
            code = props.get("Code")
            if isinstance(code, str) and "://" not in code:
                b, k = self.upload(self.local(code, base), bucket, prefix)
                props["Code"] = {"S3Bucket": b, "S3Key": k}
        return template

    @staticmethod
    def _eventbridge_fixture_auth(value, logical_id):
        """Replace secret dynamic references with opaque, deterministic test-only credentials."""
        if isinstance(value, dict):
            return {key: Worker._eventbridge_fixture_auth(item, logical_id) for key, item in value.items()}
        if isinstance(value, list):
            return [Worker._eventbridge_fixture_auth(item, logical_id) for item in value]
        if not isinstance(value, str) or not value.startswith("{{resolve:secretsmanager:"):
            return value
        digest = hashlib.sha256((logical_id + "\0" + value).encode()).hexdigest()[:20]
        return "modriss-test-" + digest

    @staticmethod
    def _event_resource_name(properties, logical_id):
        name = properties.get("Name")
        if not isinstance(name, str) or not name or "${" in name or "{{resolve:" in name:
            raise ValueError(f"Floci Events resource {logical_id} needs a concrete generated Name")
        return name

    @staticmethod
    def _replace_event_resource_references(value, arn_by_id):
        if isinstance(value, list):
            return [Worker._replace_event_resource_references(item, arn_by_id) for item in value]
        if not isinstance(value, dict):
            return value
        if isinstance(value.get("Ref"), str) and value["Ref"] in arn_by_id:
            return arn_by_id[value["Ref"]]
        get_att = value.get("Fn::GetAtt")
        if get_att is not None:
            target, attribute = (get_att.split(".", 1) if isinstance(get_att, str)
                                 else (get_att[0], get_att[1]))
            if target in arn_by_id:
                if attribute != "Arn":
                    raise ValueError(f"Floci Events resource {target} only exposes Arn to CloudFormation references")
                return arn_by_id[target]
        substitution = value.get("Fn::Sub")
        if isinstance(substitution, str):
            for logical_id, arn in arn_by_id.items():
                substitution = substitution.replace("${" + logical_id + "}", arn)
            value["Fn::Sub"] = substitution
        elif isinstance(substitution, list) and substitution and isinstance(substitution[0], str):
            template_text, variables = substitution[0], substitution[1]
            for logical_id, arn in arn_by_id.items():
                placeholder = "${" + logical_id + "}"
                if placeholder in template_text:
                    variables.pop(logical_id, None)
                    template_text = template_text.replace(placeholder, arn)
            value["Fn::Sub"] = [template_text, variables]
        return {key: Worker._replace_event_resource_references(item, arn_by_id)
                for key, item in value.items()}

    def materialize_floci_event_resources(self, documents):
        """Provision modeled EventBridge connections/destinations using Floci's Events API.

        Floci 2.2 implements these Events operations but its CloudFormation provider does
        not. The test deployment therefore provisions those modeled resources through the
        same service API and rewrites only the disposable in-memory templates.
        """
        planned = []
        for path, template in documents.items():
            for logical, resource in template.get("Resources", {}).items():
                if resource.get("Type") in ("AWS::Events::Connection", "AWS::Events::ApiDestination"):
                    planned.append((path, logical, resource))
        if not planned:
            return []

        events = self.client("events")
        operations = set(events.meta.method_to_api_mapping)
        required_operations = {"create_connection", "describe_connection", "update_connection",
                               "create_api_destination", "describe_api_destination", "update_api_destination"}
        missing = required_operations - operations
        if missing:
            raise ValueError("Floci Events API is missing operations required for modeled API destinations: "
                             + ", ".join(sorted(missing)))

        arns_by_template = {}
        adjustments = []
        # Connections must exist before ApiDestinations can bind to their ARNs.
        for path, logical, resource in planned:
            if resource.get("Type") != "AWS::Events::Connection":
                continue
            properties = resource.get("Properties", {})
            name = self._event_resource_name(properties, logical)
            auth_type = properties.get("AuthorizationType")
            if not isinstance(auth_type, str) or not auth_type:
                raise ValueError(f"Floci Events connection {logical} has no AuthorizationType")
            auth = self._eventbridge_fixture_auth(copy.deepcopy(properties.get("AuthParameters", {})), logical)
            arguments = {"Name": name, "AuthorizationType": auth_type}
            if auth:
                arguments["AuthParameters"] = auth
            emit("stage", title="Provisioning Floci EventBridge connection", resource=logical, name=name,
                 authorizationType=auth_type, credentials="synthetic test fixture")
            try:
                existing = events.describe_connection(Name=name)
            except ClientError as error:
                if error.response.get("Error", {}).get("Code") not in (
                        "ResourceNotFoundException", "ResourceNotFound", "NotFoundException"):
                    raise RuntimeError(f"Could not inspect Floci EventBridge connection {logical} ({name})") from error
                try:
                    existing = events.create_connection(**arguments)
                except ClientError as create_error:
                    if create_error.response.get("Error", {}).get("Code") not in (
                            "ResourceAlreadyExistsException", "AlreadyExistsException", "AlreadyExists"):
                        raise RuntimeError(f"Could not provision Floci EventBridge connection {logical} ({name})") from create_error
                    existing = events.describe_connection(Name=name)
                    arn = existing.get("ConnectionArn")
                    if not arn:
                        raise RuntimeError(f"Floci did not return the ARN for EventBridge connection {logical}")
                    existing = events.update_connection(**arguments)
            else:
                arn = existing.get("ConnectionArn")
                if not arn:
                    raise RuntimeError(f"Floci did not return the ARN for EventBridge connection {logical}")
                existing = events.update_connection(**arguments)
            arn = existing.get("ConnectionArn")
            if not arn:
                raise RuntimeError(f"Floci did not return the ARN for EventBridge connection {logical}")
            arns_by_template.setdefault(path, {})[logical] = arn
            adjustments.append({"template": path, "resourceId": logical,
                "resourceType": "AWS::Events::Connection", "provisioner": "Floci Events API",
                "effect": "Provisioned with synthetic test-only credentials; credentials are not emitted."})

        for path, logical, resource in planned:
            if resource.get("Type") != "AWS::Events::ApiDestination":
                continue
            properties = resource.get("Properties", {})
            name = self._event_resource_name(properties, logical)
            connection = properties.get("ConnectionArn")
            if isinstance(connection, dict):
                connection = self._replace_event_resource_references(
                    connection, arns_by_template.get(path, {}))
            if not isinstance(connection, str) or not connection.startswith("arn:"):
                raise ValueError(f"Floci API destination {logical} does not resolve to a modeled EventBridge connection ARN")
            endpoint = properties.get("InvocationEndpoint")
            method = properties.get("HttpMethod")
            if not isinstance(endpoint, str) or not endpoint.startswith(("http://", "https://")):
                raise ValueError(f"Floci API destination {logical} needs a concrete HTTP(S) InvocationEndpoint")
            if method not in ("POST", "GET", "HEAD", "OPTIONS", "PATCH", "PUT", "DELETE"):
                raise ValueError(f"Floci API destination {logical} has an invalid HttpMethod")
            arguments = {"Name": name, "ConnectionArn": connection,
                         "InvocationEndpoint": endpoint, "HttpMethod": method}
            rate = properties.get("InvocationRateLimitPerSecond")
            if rate is not None:
                arguments["InvocationRateLimitPerSecond"] = int(rate)
            emit("stage", title="Provisioning Floci EventBridge API destination", resource=logical, name=name,
                 endpoint=endpoint, httpMethod=method)
            try:
                existing = events.describe_api_destination(Name=name)
            except ClientError as error:
                if error.response.get("Error", {}).get("Code") not in (
                        "ResourceNotFoundException", "ResourceNotFound", "NotFoundException"):
                    raise RuntimeError(f"Could not inspect Floci API destination {logical} ({name})") from error
                try:
                    existing = events.create_api_destination(**arguments)
                except ClientError as create_error:
                    if create_error.response.get("Error", {}).get("Code") not in (
                            "ResourceAlreadyExistsException", "AlreadyExistsException", "AlreadyExists"):
                        raise RuntimeError(f"Could not provision Floci API destination {logical} ({name})") from create_error
                    existing = events.describe_api_destination(Name=name)
                    arn = existing.get("ApiDestinationArn")
                    if not arn:
                        raise RuntimeError(f"Floci did not return the ARN for API destination {logical}")
                    existing = events.update_api_destination(**arguments)
            else:
                arn = existing.get("ApiDestinationArn")
                if not arn:
                    raise RuntimeError(f"Floci did not return the ARN for API destination {logical}")
                existing = events.update_api_destination(**arguments)
            arn = existing.get("ApiDestinationArn")
            if not arn:
                raise RuntimeError(f"Floci did not return the ARN for API destination {logical}")
            arns_by_template.setdefault(path, {})[logical] = arn
            adjustments.append({"template": path, "resourceId": logical,
                "resourceType": "AWS::Events::ApiDestination", "provisioner": "Floci Events API",
                "effect": "Provisioned from the modeled connection, endpoint, method, and rate limit."})

        for path, template in list(documents.items()):
            arns = arns_by_template.get(path, {})
            template["Resources"] = {
                logical: resource for logical, resource in template.get("Resources", {}).items()
                if resource.get("Type") not in ("AWS::Events::Connection", "AWS::Events::ApiDestination")
            }
            for resource in template.get("Resources", {}).values():
                dependencies = resource.get("DependsOn", [])
                dependencies = [dependencies] if isinstance(dependencies, str) else dependencies
                dependencies = [dependency for dependency in dependencies if dependency not in arns]
                if dependencies:
                    resource["DependsOn"] = dependencies
                else:
                    resource.pop("DependsOn", None)
            rewritten = self._replace_event_resource_references(template, arns)
            template.clear()
            template.update(rewritten)
            documents[path] = template

        return adjustments

    def stack_events(self, stack):
        return self.client("cloudformation").describe_stack_events(StackName=stack).get("StackEvents", [])

    def recover_rollback_complete_stack(self, cfn, stack):
        """Remove a failed initial stack only after confirming it owns no surviving resources."""
        resources = []
        token = None
        while True:
            arguments = {"StackName": stack}
            if token:
                arguments["NextToken"] = token
            page = cfn.list_stack_resources(**arguments)
            resources.extend(page.get("StackResourceSummaries", []))
            token = page.get("NextToken")
            if not token:
                break

        surviving = [
            item for item in resources
            if item.get("ResourceStatus") != "DELETE_COMPLETE"
            and not (
                item.get("ResourceStatus") == "CREATE_FAILED"
                and not item.get("PhysicalResourceId")
            )
        ]
        if surviving:
            names = ", ".join(
                f"{item.get('LogicalResourceId', 'unknown')} ({item.get('ResourceStatus', 'unknown')})"
                for item in surviving[:8]
            )
            raise ValueError(
                f"Stack {stack} is from a rolled-back initial deployment and still has resources "
                f"that must be preserved: {names}. No resources were deleted; clean up or recover "
                "the retained resources before retrying deployment."
            )

        emit("stage", title="Removing empty rolled-back CloudFormation stack", stack=stack)
        try:
            cfn.delete_stack(StackName=stack)
        except ClientError as error:
            if not self._missing_stack(error):
                raise
            return

        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            try:
                response = cfn.describe_stacks(StackName=stack)
                stacks = response.get("Stacks", [])
                if not stacks or stacks[0].get("StackStatus") == "DELETE_COMPLETE":
                    return
                info = stacks[0]
                if info.get("StackStatus") == "DELETE_FAILED":
                    raise RuntimeError(
                        f"Could not remove empty rolled-back stack {stack}: "
                        f"{info.get('StackStatusReason', 'CloudFormation deletion failed.')}"
                    )
            except ClientError as error:
                if self._missing_stack(error):
                    return
                raise
            time.sleep(1)
        raise TimeoutError(f"Timed out removing empty rolled-back stack {stack}.")

    def deploy(self):
        self.materialize()
        paths = self.template_paths()
        selected = self.request.get("input", {}).get("templates", paths)
        if not selected or any(path not in paths for path in selected):
            raise ValueError("Select at least one generated CloudFormation/SAM template")
        documents = {name: yaml.load(self.local(name).read_text(), Loader=CfnLoader) for name in selected}
        diagnostics = validate_templates(documents)
        profile = apply_floci_test_profile(documents, getattr(self, "emulator_version", ""))
        diagnostics.extend(validate_emulator_compatibility(documents, getattr(self, "emulator_version", "")))
        emit("validation", title="Deployment template preflight", diagnostics=diagnostics)
        if diagnostics:
            first = diagnostics[0]
            raise ValueError(f"Template preflight found {len(diagnostics)} issue(s). "
                             f"{first['template']} · {first['resourceId']} · {first['field']}: "
                             f"{first['message']} {first['remedy']} See the preflight observation for all issues.")
        if "cognito_user_pools" in json.dumps(documents).lower() and hasattr(self, "emulator_version"):
            version = re.match(r"^(\d+)\.(\d+)\.(\d+)", self.emulator_version)
            if not version or tuple(map(int, version.groups())) < (2, 2, 0):
                raise ValueError("REST Cognito authentication requires Floci 2.2.0 or newer. "
                                 "Upgrade this project's emulator while retaining its data volume, then redeploy. "
                                 "Older versions do not enforce Cognito tokens and cannot evaluate protected APIs reliably.")
        profile["adjustments"].extend(self.materialize_floci_event_resources(documents))
        emit("deployment-profile", title="Floci Test deployment profile", profile=profile)
        selected = order_templates(documents)
        for name in selected:
            stack = "modriss-" + self.request["projectId"].replace("-", "")[:20] + "-" + hashlib.sha256(name.encode()).hexdigest()[:10]
            try:
                info = self.client("cloudformation").describe_stacks(StackName=stack)["Stacks"][0]
            except ClientError as error:
                if error.response["Error"]["Code"] not in ("ValidationError", "ResourceNotFoundException"):
                    raise
                continue
            if info.get("StackStatus") == "ROLLBACK_COMPLETE":
                self.recover_rollback_complete_stack(self.client("cloudformation"), stack)
        # Use the project's real build, including developer refinements and Go unit tests.
        if (self.root / "scripts/build.sh").is_file():
            self.command(["bash", "scripts/build.sh"])
        bucket = f"modriss-test-artifacts-{self.account}"
        s3 = self.client("s3")
        try:
            s3.head_bucket(Bucket=bucket)
        except ClientError:
            s3.create_bucket(Bucket=bucket, **({"CreateBucketConfiguration": {"LocationConstraint": self.region}}
                                              if self.region != "us-east-1" else {}))
        stacks, all_resources, outputs = [], [], []
        for index, name in enumerate(selected):
            template = copy.deepcopy(documents[name])
            template = self.package(template, self.local(name).parent, bucket, "sources")
            stack = "modriss-" + self.request["projectId"].replace("-", "")[:20] + "-" + hashlib.sha256(name.encode()).hexdigest()[:10]
            params = self.request.get("input", {}).get("parameters", {})
            transforms = template.get("Transform", [])
            if transforms == "AWS::Serverless-2016-10-31" or "AWS::Serverless-2016-10-31" in transforms:
                from samtranslator.parser.parser import Parser
                from samtranslator.translator.translator import Translator
                from samtranslator.translator.managed_policy_translator import ManagedPolicyLoader
                emit("stage", title="Expanding SAM with the AWS SAM translator")
                for entry in template.get("Resources", {}).values():
                    props = entry.get("Properties", {})
                    if entry.get("Type") == "AWS::Serverless::Api" and not any(
                        field in props for field in ("DefinitionBody", "DefinitionUri", "OpenApiVersion")
                    ):
                        # AWS SAM supports both formats; Floci's import API expects OpenAPI 3.
                        props["OpenApiVersion"] = "3.0.1"
                policies = ManagedPolicyLoader(self.client("iam"))
                template = Translator(None, Parser(), boto_session=self.session).translate(
                    template, parameter_values=dict(params), get_managed_policy_map=policies.load)
            definitions = copy.deepcopy(template)
            # Older Floci CFN imports HTTP API bodies by creating routes again on update.
            # Provision the API shell through CFN, then apply its definition via reimport/put.
            for logical, entry in template.get("Resources", {}).items():
                if entry.get("Type") not in ("AWS::ApiGateway::RestApi", "AWS::ApiGatewayV2::Api"):
                    continue
                props = entry.get("Properties", {})
                if "Body" in props or "BodyS3Location" in props:
                    props.pop("Body", None)
                    props.pop("BodyS3Location", None)
                    props.setdefault("Name", logical)
                    if entry["Type"] == "AWS::ApiGatewayV2::Api":
                        props.setdefault("ProtocolType", "HTTP")
            arguments = dict(StackName=stack, TemplateBody=json.dumps(template),
                             Capabilities=["CAPABILITY_IAM", "CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND"],
                             Parameters=[{"ParameterKey": k, "ParameterValue": str(v)} for k, v in params.items()
                                         if k in template.get("Parameters", {})])
            cfn = self.client("cloudformation")
            try:
                previous_stack = cfn.describe_stacks(StackName=stack)["Stacks"][0]
                exists = True
                if previous_stack.get("StackStatus") == "ROLLBACK_COMPLETE":
                    self.recover_rollback_complete_stack(cfn, stack)
                    exists = False
            except ClientError as error:
                if "does not exist" not in str(error) and "not exist" not in str(error):
                    raise
                exists = False
            previous = cfn.get_template(StackName=stack).get("TemplateBody") if exists else None
            if isinstance(previous, str):
                previous = yaml.load(previous, Loader=CfnLoader)
            old_parameters = {item["ParameterKey"]: item["ParameterValue"] for item in previous_stack.get("Parameters", [])} if exists else {}
            same_parameters = all(old_parameters.get(item["ParameterKey"]) == item["ParameterValue"] for item in arguments["Parameters"])
            stable = exists and previous_stack.get("StackStatus") in ("CREATE_COMPLETE", "UPDATE_COMPLETE")
            if stable and previous == template and same_parameters:
                emit("stage", title="Infrastructure template unchanged; refreshing deployed API definitions")
            elif stable and same_parameters and self.update_lambda_packages(previous, template, stack):
                emit("stage", title="Updated Lambda packages; infrastructure resources retained")
            else:
                self.submit_stack_operation(cfn, "update_stack" if exists else "create_stack", stack, arguments,
                                            previous_stack.get("StackStatus") if exists else None)
            seen = set()
            deadline = time.monotonic() + 480
            while True:
                for event in reversed(self.stack_events(stack)):
                    if event["EventId"] not in seen and event["Timestamp"].timestamp() * 1000 >= self.started:
                        seen.add(event["EventId"])
                        emit("cloudformation", stack=stack, **event)
                info = cfn.describe_stacks(StackName=stack)["Stacks"][0]
                status = info["StackStatus"]
                if status in ("CREATE_COMPLETE", "UPDATE_COMPLETE"):
                    break
                if "FAILED" in status or "ROLLBACK" in status or time.monotonic() > deadline:
                    raise RuntimeError(f"Stack {stack}: {status}. {info.get('StackStatusReason', '')}")
                time.sleep(1)
            stacks.append({"name": stack, "template": name, "status": status})
            outputs.extend(info.get("Outputs", []))
            resources = cfn.list_stack_resources(StackName=stack)["StackResourceSummaries"]
            for resource in resources:
                logical = resource["LogicalResourceId"]
                resource["properties"] = definitions.get("Resources", {}).get(logical, {}).get("Properties", {})
                resource["stack"] = stack
                resource["id"] = stack + "/" + logical
            self.synchronize_apis(definitions, resources, params, stack, info)
            all_resources.extend(resources)
        self.resources = all_resources
        if (self.root / "database/manifest.json").is_file():
            initializer = self.local("scripts/database.py")
            if not initializer.is_file():
                raise ValueError("Generated database manifest requires scripts/database.py")
            emit("stage", title="Initializing generated relational schemas")
            runpy.run_path(str(initializer))["initialize"](all_resources,
                str(self.root / "database/manifest.json"), self.call)
        emit("result", result={"stacks": stacks, "resources": self.inventory(), "outputs": outputs,
                               "accountId": self.account, "region": self.region,
                               "deploymentProfile": profile})

    def update_lambda_packages(self, previous, current, stack):
        """Use Lambda's real update API for code-only changes; preserve emulator data and event mappings."""
        if not isinstance(previous, dict):
            return False
        before, after = copy.deepcopy(previous), copy.deepcopy(current)
        changes = []
        for name, entry in after.get("Resources", {}).items():
            old = before.get("Resources", {}).get(name, {})
            if entry.get("Type") != "AWS::Lambda::Function" or old.get("Type") != entry["Type"]:
                continue
            props, old_props = entry.get("Properties", {}), old.get("Properties", {})
            code, old_code = props.pop("Code", None), old_props.pop("Code", None)
            if code != old_code:
                if not isinstance(code, dict) or not {"S3Bucket", "S3Key"}.issubset(code):
                    return False
                changes.append((name, code))
        if before != after or not changes:
            return False
        physical = {resource["LogicalResourceId"]: resource["PhysicalResourceId"] for resource in
                    self.client("cloudformation").list_stack_resources(StackName=stack)["StackResourceSummaries"]}
        if any(name not in physical for name, _ in changes):
            return False
        for name, code in changes:
            self.call("lambda", "update_function_code", FunctionName=physical[name],
                      **{key: code[key] for key in ("S3Bucket", "S3Key", "S3ObjectVersion") if key in code})
        return True

    def synchronize_apis(self, template, resources, parameters, stack, stack_info):
        """Apply OpenAPI through AWS's API import API, including on Floci versions that omit CFN Body imports."""
        by_name = {resource["LogicalResourceId"]: resource for resource in resources}
        values = {name: spec.get("Default") for name, spec in template.get("Parameters", {}).items()}
        values.update(parameters)
        values.update({"AWS::Region": self.region, "AWS::AccountId": self.account,
                       "AWS::Partition": "aws", "AWS::StackName": stack,
                       "AWS::StackId": stack_info["StackId"], "AWS::URLSuffix": "amazonaws.com"})

        def reference(name):
            if name in values:
                return values[name]
            if name in by_name:
                return by_name[name]["PhysicalResourceId"]
            raise ValueError(f"Cannot resolve API definition reference {name}")

        def attribute(name, field):
            if field != "Arn":
                raise ValueError(f"Cannot resolve API definition attribute {name}.{field}")
            resource = by_name[name]
            physical, kind = resource["PhysicalResourceId"], resource["ResourceType"]
            if physical.startswith("arn:"):
                return physical
            if kind == "AWS::Lambda::Function":
                return self.client("lambda").get_function_configuration(FunctionName=physical)["FunctionArn"]
            if kind == "AWS::IAM::Role":
                return self.client("iam").get_role(RoleName=physical)["Role"]["Arn"]
            if kind == "AWS::DynamoDB::Table":
                return self.client("dynamodb").describe_table(TableName=physical)["Table"]["TableArn"]
            if kind == "AWS::Cognito::UserPool":
                return self.client("cognito-idp").describe_user_pool(UserPoolId=physical)["UserPool"]["Arn"]
            raise ValueError(f"Cannot resolve API definition ARN for {name}")

        def resolve(value):
            if isinstance(value, list):
                return [resolve(item) for item in value]
            if not isinstance(value, dict):
                return value
            if len(value) == 1:
                if "Ref" in value:
                    return reference(value["Ref"])
                if "Fn::GetAtt" in value:
                    parts = value["Fn::GetAtt"]
                    return attribute(*(parts.split(".", 1) if isinstance(parts, str) else parts))
                if "Fn::ImportValue" in value:
                    name = resolve(value["Fn::ImportValue"])
                    for page in self.client("cloudformation").get_paginator("list_exports").paginate():
                        for export in page.get("Exports", []):
                            if export["Name"] == name:
                                return export["Value"]
                    raise ValueError(f"Cannot resolve CloudFormation export {name} in API definition")
                if "Fn::Join" in value:
                    return value["Fn::Join"][0].join(str(resolve(item)) for item in value["Fn::Join"][1])
                if "Fn::Sub" in value:
                    spec = value["Fn::Sub"]
                    text, variables = (spec, {}) if isinstance(spec, str) else (spec[0], resolve(spec[1]))
                    def substitute(match):
                        name = match.group(1)
                        if name.startswith("!"):
                            return "$" + "{" + name[1:] + "}"
                        if name in variables:
                            return str(variables[name])
                        return str(attribute(*name.split(".", 1)) if "." in name else reference(name))
                    return re.sub(r"\$\{([^}]+)\}", substitute, text)
                if any(key.startswith("Fn::") for key in value):
                    raise ValueError(f"Unsupported intrinsic in API definition: {next(iter(value))}")
            return {key: resolve(item) for key, item in value.items()}

        for resource in resources:
            if resource["ResourceType"] not in ("AWS::ApiGateway::RestApi", "AWS::ApiGatewayV2::Api"):
                continue
            props = resource["properties"]
            body = props.get("Body")
            if body is None and props.get("BodyS3Location"):
                location = resolve(props["BodyS3Location"])
                body = yaml.load(self.client("s3").get_object(Bucket=location["Bucket"], Key=location["Key"])["Body"].read(), Loader=CfnLoader)
            if body is None:
                continue
            body = resolve(body)
            physical = resource["PhysicalResourceId"]
            if resource["ResourceType"] == "AWS::ApiGateway::RestApi":
                self.call("apigateway", "put_rest_api", restApiId=physical,
                          mode=props.get("Mode", "overwrite"), body=json.dumps(body).encode())
                self.synchronize_rest_models(physical, body)
                for stage in self.client("apigateway").get_stages(restApiId=physical).get("item", []):
                    self.call("apigateway", "create_deployment", restApiId=physical, stageName=stage["stageName"])
            else:
                self.call("apigatewayv2", "reimport_api", ApiId=physical, Body=json.dumps(body))
                for stage in self.client("apigatewayv2").get_stages(ApiId=physical).get("Items", []):
                    if not stage.get("AutoDeploy"):
                        deployment = self.call("apigatewayv2", "create_deployment", ApiId=physical)
                        self.call("apigatewayv2", "update_stage", ApiId=physical,
                                  StageName=stage["StageName"], DeploymentId=deployment["DeploymentId"])

    def synchronize_rest_models(self, api_id, body):
        """Repair empty model placeholders left by Floci's OpenAPI importer through real SDK calls."""
        schemas = body.get("components", {}).get("schemas", body.get("definitions", {}))
        api = self.client("apigateway")
        for name, schema in schemas.items():
            normalized = copy.deepcopy(schema)
            needed = set()

            def rewrite(value):
                if isinstance(value, dict):
                    reference = value.get("$ref", "")
                    for prefix in ("#/components/schemas/", "#/definitions/"):
                        if reference.startswith(prefix):
                            dependency = reference[len(prefix):]
                            value["$ref"] = "#/definitions/" + dependency
                            if dependency in schemas:
                                needed.add(dependency)
                    for item in value.values():
                        rewrite(item)
                elif isinstance(value, list):
                    for item in value:
                        rewrite(item)

            rewrite(normalized)
            definitions = normalized.setdefault("definitions", {}) if needed else {}
            while needed - definitions.keys():
                dependency = sorted(needed - definitions.keys())[0]
                definitions[dependency] = copy.deepcopy(schemas[dependency])
                rewrite(definitions[dependency])
            try:
                previous = api.get_model(restApiId=api_id, modelName=name).get("schema", "{}")
            except ClientError as error:
                if error.response["Error"]["Code"] != "NotFoundException":
                    raise
                self.call("apigateway", "create_model", restApiId=api_id, name=name,
                          contentType="application/json", schema=json.dumps(normalized))
                continue
            if json.loads(previous) != normalized:
                self.call("apigateway", "update_model", restApiId=api_id, modelName=name,
                          patchOperations=[{"op":"replace", "path":"/schema", "value":json.dumps(normalized)}])

    def rest_routes(self, api, physical):
        routes, position = [], None
        while True:
            page = api.get_resources(restApiId=physical, limit=500, embed=["methods"],
                                     **({"position": position} if position else {}))
            for resource in page.get("items", []):
                methods = resource.get("resourceMethods")
                if methods is None:
                    # Floci 2.0.1 omits embedded methods in GetResources/GetResource.
                    # Query real method definitions rather than inventing routes from a template.
                    methods = {}
                    for method in ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "ANY"):
                        try:
                            methods[method] = api.get_method(restApiId=physical, resourceId=resource["id"], httpMethod=method)
                        except ClientError as error:
                            if error.response["Error"]["Code"] not in ("NotFoundException", "404"):
                                raise
                routes.extend({"path": resource["path"], "method": method} for method in methods)
            following = page.get("position")
            if not following or following == position:
                break
            position = following
        return sorted(routes, key=lambda route: (route["path"], route["method"]))

    def inventory(self):
        result = []
        for resource in self.resources:
            item = {**resource}
            kind, physical = item.get("ResourceType", ""), item.get("PhysicalResourceId", "")
            item["name"] = item.get("LogicalResourceId", physical)
            item["actions"] = []
            try:
                if kind == "AWS::Lambda::Function":
                    item["actions"] = ["invoke", "logs", "inspect"]
                elif kind == "AWS::StepFunctions::StateMachine":
                    item["actions"] = ["start", "executions", "inspect"]
                    info = self.client("stepfunctions").describe_state_machine(stateMachineArn=physical)
                    item["definition"] = json.loads(info["definition"])
                elif kind == "AWS::DynamoDB::Table":
                    item["actions"] = ["scan", "put", "inspect"]
                elif kind == "AWS::RDS::DBCluster":
                    item["actions"] = ["inspect"]
                    manifest = self.root / "database/manifest.json"
                    if manifest.is_file():
                        entry = next((database for database in json.loads(manifest.read_text()).get("databases", [])
                                      if database.get("resource") == item["LogicalResourceId"]), None)
                        if entry:
                            secrets = [secret for secret in self.resources if secret["LogicalResourceId"] == entry["secret"]
                                       and secret["ResourceType"] == "AWS::SecretsManager::Secret"]
                            if len(secrets) != 1:
                                raise ValueError("Generated relational credentials are missing or ambiguous")
                            cluster = self.client("rds").describe_db_clusters(DBClusterIdentifier=physical)["DBClusters"][0]
                            item["database"] = {"resourceArn":cluster["DBClusterArn"], "secretArn":secrets[0]["PhysicalResourceId"],
                                                "database":entry["database"], "tables":entry.get("tables", [])}
                    if item.get("database"):
                        item["actions"] = ["query", "tables", "begin-transaction", "commit-transaction", "rollback-transaction", "inspect"]
                elif kind == "AWS::S3::Bucket":
                    item["actions"] = ["list", "get", "put", "inspect"]
                elif kind == "AWS::SQS::Queue":
                    item["actions"] = ["send", "peek", "inspect"]
                elif kind == "AWS::SNS::Topic":
                    item["actions"] = ["publish", "inspect"]
                elif kind == "AWS::Events::EventBus":
                    item["actions"] = ["publish", "inspect"]
                elif kind == "AWS::Logs::LogGroup":
                    item["actions"] = ["logs", "inspect"]
                elif kind in ("AWS::ApiGateway::RestApi", "AWS::ApiGatewayV2::Api"):
                    item["actions"] = ["request", "inspect"]
                    if kind == "AWS::ApiGateway::RestApi":
                        api = self.client("apigateway")
                        item["routes"] = self.rest_routes(api, physical)
                        item["stages"] = [s["stageName"] for s in api.get_stages(restApiId=physical).get("item", [])]
                    else:
                        api = self.client("apigatewayv2")
                        item["routes"] = [{"method": r["RouteKey"].split(" ")[0],
                                           "path": r["RouteKey"].partition(" ")[2] or "/"}
                                          for r in api.get_routes(ApiId=physical).get("Items", [])]
                        item["stages"] = [s["StageName"] for s in api.get_stages(ApiId=physical).get("Items", [])]
                else:
                    item["actions"] = ["inspect"]
            except Exception as error:
                item["discoveryError"] = str(error)
            namespace = kind.split("::")[1] if "::" in kind else ""
            service = {"ApiGateway": "apigateway", "ApiGatewayV2": "apigatewayv2",
                       "StepFunctions": "stepfunctions", "Events": "events", "Logs": "logs",
                       "CloudWatch": "cloudwatch", "Cognito": "cognito-idp", "SecretsManager": "secretsmanager"}.get(namespace, namespace.lower())
            if service in self.session.get_available_services():
                operations = sorted(self.client(service).meta.method_to_api_mapping)
                defaults = {
                    "AWS::IAM::Role": ("get_role", {"RoleName": physical}),
                    "AWS::DynamoDB::Table": ("describe_table", {"TableName": physical}),
                    "AWS::Lambda::Function": ("get_function_configuration", {"FunctionName": physical}),
                    "AWS::StepFunctions::StateMachine": ("describe_state_machine", {"stateMachineArn": physical}),
                    "AWS::S3::Bucket": ("get_bucket_location", {"Bucket": physical}),
                    "AWS::RDS::DBCluster": ("describe_db_clusters", {"DBClusterIdentifier": physical}),
                    "AWS::RDS::DBInstance": ("describe_db_instances", {"DBInstanceIdentifier": physical}),
                    "AWS::Kinesis::Stream": ("describe_stream", {"StreamName": physical}),
                    "AWS::Events::Rule": ("describe_rule", {"Name": physical}),
                    "AWS::SNS::Topic": ("get_topic_attributes", {"TopicArn": physical}),
                    "AWS::SSM::Parameter": ("get_parameter", {"Name": physical}),
                }
                operation, parameters = defaults.get(kind, ("", {}))
                item["sdk"] = {"service": service, "operations": operations,
                               "example": {"operation": operation, "parameters": parameters}}
                item["actions"].append("aws-api")
            result.append(clean(item))
        return result

    def resource(self):
        identity = self.request.get("resourceId")
        resource = next((r for r in self.resources if r["id"] == identity), None)
        if resource is None:
            raise ValueError("Resource does not belong to this deployment")
        return resource

    def logs(self, group):
        return self.call("logs", "filter_log_events", logGroupName=group, limit=200,
                         startTime=self.request.get("input", {}).get("since", 0))

    def execute(self):
        resource = self.resource()
        action = self.request["action"]
        if action not in resource.get("actions", []):
            raise ValueError("Unsupported action for this resource")
        physical = resource["PhysicalResourceId"]
        kind = resource["ResourceType"]
        data = self.request.get("input", {})
        result = None
        if action == "aws-api":
            sdk = resource.get("sdk", {})
            service, operation = sdk.get("service"), data.get("operation", "")
            if operation not in sdk.get("operations", []):
                raise ValueError("Choose an AWS SDK operation from this resource's supported operation list")
            parameters = data.get("parameters", {})
            for account in re.findall(r"arn:aws[^:]*:[^:]*:[^:]*:(\d{12}):", json.dumps(parameters)):
                if account != self.account:
                    raise ValueError("AWS requests must stay within this project's emulator account")
            if "QueueUrl" in parameters:
                queue = parameters["QueueUrl"]
                parameters["QueueUrl"] = self.endpoint + "/" + queue.split("/", 3)[-1]
            result = self.call(service, operation, **parameters)
            for field in ("Body", "Payload"):
                if hasattr(result.get(field), "read"):
                    content = result[field].read(1000001)
                    result[field] = content[:1000000].decode(errors="replace")
                    result["truncated"] = len(content) > 1000000
        elif action == "inspect":
            result = resource
        elif kind == "AWS::RDS::DBCluster":
            config = resource.get("database", {})
            common = {key: config[key] for key in ("resourceArn", "secretArn", "database")}
            if action in ("query", "tables"):
                sql = data.get("sql", "SELECT 1") if action == "query" else (
                    "SELECT table_name, column_name, data_type, is_nullable FROM information_schema.columns "
                    "WHERE table_schema = 'public' ORDER BY table_name, ordinal_position LIMIT 500")
                arguments = {"sql":sql, "includeResultMetadata":True, "parameters":data.get("parameters", [])}
                if data.get("transactionId"):
                    arguments["transactionId"] = data["transactionId"]
                result = self.call("rds-data", "execute_statement", **common, **arguments)
                names = [column.get("label", column.get("name", str(index)))
                         for index, column in enumerate(result.get("columnMetadata", []))]
                result["rows"] = [dict(zip(names, [next(iter(field.values()), None) if "isNull" not in field else None
                                                  for field in row])) for row in result.get("records", [])]
            elif action == "begin-transaction":
                result = self.call("rds-data", "begin_transaction", **common)
            elif action in ("commit-transaction", "rollback-transaction"):
                result = self.call("rds-data", action.replace("-", "_"), resourceArn=common["resourceArn"],
                                   secretArn=common["secretArn"], transactionId=data["transactionId"])
        elif kind == "AWS::Lambda::Function" and action == "invoke":
            result = self.call("lambda", "invoke", FunctionName=physical,
                               Payload=json.dumps(data.get("payload", {})).encode(), LogType="Tail")
            body = result["Payload"].read(2000000).decode(errors="replace")
            try:
                result["Payload"] = json.loads(body)
            except ValueError:
                result["Payload"] = body
            result["logs"] = base64.b64decode(result.pop("LogResult", "")).decode(errors="replace")
            if result.get("FunctionError"):
                emit("result", result=result)
                raise RuntimeError(f"Lambda {physical}: {result['FunctionError']}")
        elif kind == "AWS::StepFunctions::StateMachine":
            sfn = self.client("stepfunctions")
            if action == "executions":
                result = self.call("stepfunctions", "list_executions", stateMachineArn=physical, maxResults=100)
            elif action == "start":
                started = self.call("stepfunctions", "start_execution", stateMachineArn=physical,
                                    name="modriss-" + self.request["jobId"], input=json.dumps(data.get("payload", {})))
                execution = started["executionArn"]
                emit("execution", executionArn=execution)
                cursor, seen = None, set()
                deadline = time.monotonic() + 300
                while True:
                    history = sfn.get_execution_history(executionArn=execution, maxResults=1000)
                    for event in history.get("events", []):
                        if event["id"] not in seen:
                            seen.add(event["id"])
                            emit("workflow", **event)
                    # Preserve all pages, including histories longer than 1,000 events.
                    while history.get("nextToken"):
                        history = sfn.get_execution_history(executionArn=execution, maxResults=1000,
                                                            nextToken=history["nextToken"])
                        for event in history.get("events", []):
                            if event["id"] not in seen:
                                seen.add(event["id"])
                                emit("workflow", **event)
                    info = sfn.describe_execution(executionArn=execution)
                    emit("progress", status=info["status"], executionArn=execution)
                    if info["status"] != "RUNNING":
                        result = info
                        if info["status"] != "SUCCEEDED":
                            emit("result", result=info)
                            raise RuntimeError(f"Workflow {info['status']}: {info.get('error', '')} {info.get('cause', '')}")
                        break
                    if time.monotonic() > deadline:
                        result = {**info, "observationEnded": True,
                                  "message": "Workflow is still running. Refresh execution history to continue observing."}
                        break
                    time.sleep(1)
        elif kind == "AWS::DynamoDB::Table":
            if action == "scan":
                page_size = max(1, min(100, int(data.get("limit", 25))))
                result = self.call("dynamodb", "scan", TableName=physical, Limit=page_size,
                                   **({"ExclusiveStartKey": data["cursor"]} if data.get("cursor") else {}))
            elif action == "put":
                result = self.call("dynamodb", "put_item", TableName=physical, Item=data["item"])
        elif kind == "AWS::S3::Bucket":
            if action == "list":
                page_size = max(1, min(100, int(data.get("limit", 25))))
                result = self.call("s3", "list_objects_v2", Bucket=physical, MaxKeys=page_size,
                                   Prefix=data.get("prefix", ""),
                                   **({"ContinuationToken": data["cursor"]} if data.get("cursor") else {}))
            elif action == "get":
                result = self.call("s3", "get_object", Bucket=physical, Key=data["key"])
                body = result["Body"].read(1000001)
                result["Body"] = body[:1000000].decode(errors="replace")
                result["BodyBase64"] = base64.b64encode(body[:1000000]).decode()
                result["truncated"] = len(body) > 1000000
            elif action == "put":
                result = self.call("s3", "put_object", Bucket=physical, Key=data["key"],
                                   Body=data.get("body", "").encode(), ContentType=data.get("contentType", "text/plain"))
        elif kind == "AWS::SQS::Queue":
            queue = self.endpoint + "/" + physical.split("/", 3)[-1] if physical.startswith("http") else physical
            if action == "send":
                args = {"QueueUrl": queue, "MessageBody": json.dumps(data.get("payload", {}))}
                if queue.endswith(".fifo"):
                    args.update(MessageGroupId=data.get("groupId", "modriss"), MessageDeduplicationId=self.request["jobId"])
                result = self.call("sqs", "send_message", **args)
            elif action == "peek":
                result = self.call("sqs", "receive_message", QueueUrl=queue, MaxNumberOfMessages=10,
                                   VisibilityTimeout=0, WaitTimeSeconds=0, MessageAttributeNames=["All"])
        elif kind == "AWS::SNS::Topic":
            result = self.call("sns", "publish", TopicArn=physical, Message=json.dumps(data.get("payload", {})))
        elif kind == "AWS::Events::EventBus":
            result = self.call("events", "put_events", Entries=[{"EventBusName": physical,
                "Source": data.get("source", "modriss.test"), "DetailType": data.get("detailType", "CaseStudyEvent"),
                "Detail": json.dumps(data.get("payload", {}))}])
            if result.get("FailedEntryCount", 0):
                emit("result", result=result)
                raise RuntimeError("EventBridge rejected one or more events")
        elif action == "logs":
            group = "/aws/lambda/" + physical if kind == "AWS::Lambda::Function" else physical
            result = self.logs(group)
        elif action == "request":
            result = self.http(resource, data)
        if result is None:
            raise ValueError("Action is not implemented for this resource")
        emit("result", result=result)

    def http(self, resource, data):
        import urllib.request
        import urllib.error
        api_id = resource["PhysicalResourceId"]
        stage = data.get("stage", next(iter(resource.get("stages", [])), "Prod"))
        path = data.get("path", "/")
        if not path.startswith("/") or path.startswith("//") or "?" in path or "#" in path:
            raise ValueError("Supply an API-relative path; use the query field for parameters")
        decoded = unquote(unquote(path))
        if "\\" in decoded or any(part in (".", "..") for part in decoded.split("/")) or any(ord(c) < 32 for c in decoded):
            raise ValueError("API paths cannot contain traversal or control characters")
        if stage not in resource.get("stages", []):
            raise ValueError("Stage is not part of this deployed API")
        if resource["ResourceType"] == "AWS::ApiGateway::RestApi":
            url = f"{self.endpoint}/restapis/{quote(api_id, safe='')}/{quote(stage, safe='')}/_user_request_{path}"
        else:
            url = f"{self.endpoint}/execute-api/{quote(api_id, safe='')}/{quote(stage, safe='')}{path}"
        if data.get("query"):
            url += "?" + urlencode(data["query"])
        method = data.get("method", "GET").upper()
        if method not in ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"):
            raise ValueError("Invalid HTTP method")
        body = data.get("body")
        body = (body if isinstance(body, str) else json.dumps(body)).encode() if body is not None else None
        headers = {"Content-Type": "application/json", **data.get("headers", {}),
                   "X-Modriss-Test-Run": self.request["jobId"]}
        # Application requests preserve the caller's headers, including missing Authorization.
        # Only management SDK calls use AWS signing. The dedicated emulator's default account
        # selects this project without changing the application's authentication contract.
        authentication = data.get("auth", "application")
        if authentication not in ("application", "aws-iam"):
            raise ValueError("API auth must be application or aws-iam")
        if authentication == "aws-iam":
            if any(name.lower() == "authorization" for name in headers):
                raise ValueError("AWS IAM signing cannot be combined with a supplied Authorization header")
            signed = AWSRequest(method=method, url=url, data=body, headers=headers)
            # The runner's 12-digit access key selects a project account for AWS
            # management calls, but Floci's API Gateway IAM verifier requires a
            # registered key. Its built-in test/test key resolves to the dedicated
            # instance's FLOCI_DEFAULT_ACCOUNT_ID, which the deploy check verifies.
            SigV4Auth(Credentials("test", "test"), "execute-api", self.region).add_auth(signed)
            headers = dict(signed.headers)
        emit("stage", title=f"{method} {path}", url=url)
        # No redirects: a project API cannot turn this proxy into an arbitrary HTTP client.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None
        opener = urllib.request.build_opener(NoRedirect())
        try:
            response = opener.open(urllib.request.Request(url, body, headers, method=method), timeout=120)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read(1000001)
        result = {"statusCode": response.code, "headers": dict(response.headers),
                  "body": content[:1000000].decode(errors="replace"), "truncated": len(content) > 1000000}
        if response.code >= 400:
            emit("result", result=result)
            raise RuntimeError(f"API returned HTTP {response.code}")
        return result

    def observe(self):
        """Fetch live histories or downstream logs, bounded and explicitly scoped to this deployment."""
        data = self.request.get("input", {})
        if data.get("executionArn"):
            resource = self.resource()
            sfn = self.client("stepfunctions")
            info = sfn.describe_execution(executionArn=data["executionArn"])
            if info["stateMachineArn"] != resource["PhysicalResourceId"]:
                raise ValueError("Execution does not belong to this workflow")
            history = sfn.get_execution_history(executionArn=data["executionArn"], maxResults=1000,
                                               **({"nextToken": data["cursor"]} if data.get("cursor") else {}))
            for event in history.get("events", []):
                emit("workflow", **event)
            emit("result", result={"execution": info, "history": history})
        else:
            emit("result", result={"resources": self.inventory()})

    def storage_observations(self, phase):
        for resource in self.resources:
            kind = resource.get("ResourceType")
            physical = resource.get("PhysicalResourceId", "")
            try:
                if kind == "AWS::DynamoDB::Table":
                    data = self.client("dynamodb").scan(TableName=physical, Limit=25)
                elif kind == "AWS::RDS::DBCluster" and resource.get("database"):
                    config = resource["database"]
                    data = {"database":config["database"], "tables":{}}
                    common = {key:config[key] for key in ("resourceArn", "secretArn", "database")}
                    for table in config.get("tables", [])[:5]:
                        identifier = '"' + table["name"].replace('"', '""') + '"'
                        data["tables"][table["name"]] = self.client("rds-data").execute_statement(
                            **common, sql=f"SELECT * FROM {identifier} LIMIT 25", includeResultMetadata=True)
                elif kind == "AWS::S3::Bucket":
                    data = self.client("s3").list_objects_v2(Bucket=physical, MaxKeys=100)
                elif kind == "AWS::SQS::Queue":
                    queue = self.endpoint + "/" + physical.split("/", 3)[-1] if physical.startswith("http") else physical
                    data = self.client("sqs").get_queue_attributes(QueueUrl=queue, AttributeNames=["All"])
                else:
                    continue
                emit("storage", title=f"{phase} · {resource['name']}", phase=phase,
                     resourceId=resource["id"], data=data,
                     note="Bounded live observation; DynamoDB up to 25 items, S3 up to 100 keys, SQL up to 5 tables / 25 rows each. Concurrent activity may change data.")
            except Exception as error:
                emit("observation-warning", title=f"Cannot inspect {resource['name']}", message=str(error))

    def downstream_logs(self):
        for resource in self.resources:
            if resource.get("ResourceType") != "AWS::Lambda::Function":
                continue
            group = "/aws/lambda/" + resource["PhysicalResourceId"]
            try:
                response = self.client("logs").filter_log_events(logGroupName=group,
                    startTime=self.started, limit=100)
                for event in response.get("events", []):
                    emit("application-log", title=resource["name"], group=group, **event)
            except ClientError as error:
                if error.response["Error"]["Code"] != "ResourceNotFoundException":
                    emit("observation-warning", title=f"Cannot read {group}", message=str(error))

    def run(self):
        emit("stage", title="Connecting to Floci", accountId=self.account, region=self.region)
        import urllib.request
        with urllib.request.urlopen(self.endpoint + "/_floci/health", timeout=5) as response:
            health = json.load(response)
        if not isinstance(health.get("services"), dict) or not health.get("version"):
            raise ValueError("The backend endpoint did not return a valid Floci health response")
        emit("emulator-health", title="Floci " + health["version"], health=health)
        self.emulator_version = health["version"]
        identity = self.call("sts", "get_caller_identity")
        if identity.get("Account") != self.account:
            raise ValueError("The emulator did not select this project's account namespace")
        unsigned_sts = self.session.client("sts", endpoint_url=self.endpoint,
                                          config=Config(signature_version=UNSIGNED, connect_timeout=5, read_timeout=10))
        if unsigned_sts.get_caller_identity().get("Account") != self.account:
            raise ValueError("This job requires a dedicated Floci instance with the project's default account. Redeploy first.")
        kind = self.request["kind"]
        if kind == "DEPLOY":
            self.deploy()
        elif kind == "SCRIPT":
            self.materialize()
            script = self.request["resourceId"]
            path = self.local(script)
            if not path.is_file() or not script.startswith(("scripts/", "tests/")) or path.suffix not in (".sh", ".py"):
                raise ValueError("Choose an existing shell or Python script under scripts/ or tests/")
            data = self.request.get("input", {})
            arguments = data.get("args", [])
            if not isinstance(arguments, list) or len(arguments) > 100 or any(not isinstance(arg, str) or len(arg) > 2000 for arg in arguments):
                raise ValueError("Script args must be an array of at most 100 strings")
            self.command(["bash" if path.suffix == ".sh" else "python", str(path), *arguments], data.get("env", {}))
            emit("result", result={"script": script, "exitCode": 0})
        elif kind == "OBSERVE":
            self.observe()
        else:
            observe = self.request.get("action") not in ("inspect", "logs", "executions", "scan", "list", "get", "peek")
            if observe:
                self.storage_observations("Before")
            try:
                self.execute()
            finally:
                if observe:
                    # Give asynchronous delivery a short observation window; report only real logs.
                    window = self.request.get("input", {}).get("observeSeconds", 5 if self.request.get("action") in ("publish", "send") else 1)
                    window = max(0, min(30, int(window)))
                    for second in range(window):
                        time.sleep(1)
                        emit("progress", title="Observing downstream activity", elapsedSeconds=second + 1)
                    self.downstream_logs()
                    self.storage_observations("After")


if __name__ == "__main__":
    try:
        Path("/tmp/project").mkdir(parents=True, exist_ok=True)
        os.chdir("/tmp/project")
        Worker(json.load(sys.stdin)).run()
        emit("complete", status="SUCCEEDED")
    except Exception as error:
        emit("error", message=str(error), errorType=type(error).__name__)
        emit("complete", status="FAILED")
        sys.exit(1)
