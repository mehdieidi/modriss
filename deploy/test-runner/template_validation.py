"""Targeted template and emulator-compatibility checks before building project code.

These checks do not validate or mutate modeling-assistant output, and never change authentication.
They catch known malformed generated infrastructure while leaving AWS/SAM as the full validator.
"""
from urllib.parse import urlparse
import re


def validate_emulator_compatibility(documents, version):
    """Fail before build when the pinned emulator cannot provision known generated resources."""
    parsed = re.match(r"^(\d+)\.(\d+)\.(\d+)", version or "")
    if not parsed or tuple(map(int, parsed.groups())) > (2, 2, 0):
        return []
    diagnostics = []
    for path, template in documents.items():
        for logical, resource in template.get("Resources", {}).items():
            kind = resource.get("Type")
            published = kind == "AWS::Serverless::StateMachine" and resource.get("Properties", {}).get("AutoPublishAlias")
            if published or kind in ("AWS::StepFunctions::StateMachineVersion", "AWS::StepFunctions::StateMachineAlias"):
                diagnostics.append({"template":path, "resourceId":logical,
                    "field":"Properties.AutoPublishAlias" if published else "Type",
                    "message":f"Floci {version} does not provision published Step Functions versions/aliases.",
                    "remedy":"For an explicitly separate evaluation PSM, disable StepFunctionStateMachine.publishAlias and regenerate. "
                             "Evaluate version/alias behavior on a provider that supports it. The adapter does not silently remove it."})
            if kind == "AWS::SecretsManager::RotationSchedule":
                diagnostics.append({"template":path, "resourceId":logical, "field":"Type",
                    "message":f"Floci {version} does not provision Secrets Manager rotation schedules through CloudFormation.",
                    "remedy":"Evaluate secret rotation separately on a supported provider; retain the requirement in the model. "
                             "Do not treat a deployed secret alone as verified rotation."})
    return diagnostics


def order_templates(documents):
    """Order constant CloudFormation exports/imports before provisioning any selected stack."""
    owners = {}
    for path, template in documents.items():
        for output in template.get("Outputs", {}).values():
            name = output.get("Export", {}).get("Name")
            if isinstance(name, str):
                if name in owners and owners[name] != path:
                    raise ValueError(f"Duplicate CloudFormation export {name} in selected templates")
                owners[name] = path

    def imports(value):
        if isinstance(value, dict):
            name = value.get("Fn::ImportValue")
            if isinstance(name, str) and name in owners:
                yield owners[name]
            for item in value.values():
                yield from imports(item)
        elif isinstance(value, list):
            for item in value:
                yield from imports(item)

    dependencies = {path: set(imports(template)) - {path} for path, template in documents.items()}
    ordered = []
    while dependencies:
        ready = next((path for path, deps in dependencies.items() if not deps.intersection(dependencies)), None)
        if ready is None:
            raise ValueError("Cyclic CloudFormation imports between selected templates")
        ordered.append(ready)
        del dependencies[ready]
    return ordered


def validate_templates(documents):
    diagnostics = []
    for path, template in documents.items():
        resources = template.get("Resources", {})
        known = set(resources) | set(template.get("Parameters", {}))

        def references(logical, value, field="Properties"):
            if isinstance(value, dict):
                target = value.get("Ref")
                attribute = value.get("Fn::GetAtt")
                if attribute:
                    target = attribute.split(".", 1)[0] if isinstance(attribute, str) else attribute[0]
                if isinstance(target, str) and target not in known and not target.startswith("AWS::"):
                    # SAM's implicit APIs and function execution roles are created during expansion.
                    implicit = target in ("ServerlessRestApi", "ServerlessHttpApi") or (
                        target.endswith("Role") and resources.get(target[:-4], {}).get("Type") == "AWS::Serverless::Function")
                    if not implicit:
                        issue(logical, field, f"Reference {target} is not defined in this template.",
                              "Complete the modeled AWS resource mapping or supply a CloudFormation parameter/import for resources in another stack. Regenerate or refine the template.")
                for key, item in value.items():
                    references(logical, item, field + "." + key)
            elif isinstance(value, list):
                for index, item in enumerate(value):
                    references(logical, item, field + f"[{index}]")

        def issue(logical, field, message, remedy):
            diagnostics.append({"template": path, "resourceId": logical, "field": field,
                                "message": message, "remedy": remedy})

        def required(logical, props, fields):
            for field in fields:
                if props.get(field) in (None, "", []):
                    issue(logical, field, f"Missing required {field}.",
                          "Correct the API/authorizer configuration and regenerate artifacts, or complete the refined template.")

        def jwt(logical, configuration):
            if not isinstance(configuration, dict):
                issue(logical, "JwtConfiguration", "A JWT authorizer requires issuer and audience settings.",
                      "Supply the actual identity-provider issuer URL and application client audiences.")
                return
            configuration = {"Issuer": configuration.get("Issuer", configuration.get("issuer")),
                             "Audience": configuration.get("Audience", configuration.get("audience"))}
            required(logical, configuration, ("Issuer", "Audience"))
            issuer = configuration.get("Issuer")
            if isinstance(issuer, str) and (urlparse(issuer).scheme != "https" or not urlparse(issuer).hostname
                                           or any(char.isspace() for char in issuer)):
                issue(logical, "Issuer", "JWT issuer must be an HTTPS URL, not prose or a validation-rule expression.",
                      "Use the identity provider's issuer URL; configure its client IDs separately as Audience.")
            audience = configuration.get("Audience")
            if audience is not None and not isinstance(audience, (list, dict)):
                issue(logical, "Audience", "JWT audiences must be a list of client IDs.", "Supply an Audience list.")

        for logical, resource in resources.items():
            kind, props = resource.get("Type"), resource.get("Properties", {})
            if not isinstance(props, dict):
                issue(logical, "Properties", "Resource properties must be an object.", "Correct the template structure.")
                continue
            references(logical, props)
            if kind == "AWS::SNS::Subscription":
                protocol, endpoint = props.get("Protocol"), props.get("Endpoint")
                symbolic_url = isinstance(endpoint, dict) and len(endpoint) == 1 and next(iter(endpoint)) in (
                    "Ref", "Fn::Sub", "Fn::Join", "Fn::If", "Fn::GetAtt")
                if symbolic_url and "Fn::GetAtt" in endpoint:
                    attribute = endpoint["Fn::GetAtt"]
                    symbolic_url = (attribute.split(".", 1)[-1] if isinstance(attribute, str) else attribute[-1]) != "Arn"
                valid_url = isinstance(endpoint, str) and urlparse(endpoint).scheme == protocol and urlparse(endpoint).hostname and not any(char.isspace() for char in endpoint)
                if protocol in ("http", "https") and not symbolic_url and not valid_url:
                    issue(logical, "Endpoint", "HTTP subscriptions require an actual endpoint URL; descriptions and resource ARNs cannot receive HTTP messages.",
                          "Set ExternalEndpoint.endpointUri to a sandbox URL and regenerate PSM/artifacts, or refine the subscription's Endpoint and Protocol.")
            elif kind == "AWS::Cognito::UserPool":
                protection = props.get("DeletionProtection")
                if protection is not None and not isinstance(protection, dict) and protection not in ("ACTIVE", "INACTIVE"):
                    issue(logical, "DeletionProtection", "Cognito DeletionProtection must be ACTIVE or INACTIVE, not a Boolean.",
                          "Regenerate the pool template or use the AWS string enum value.")
            elif kind == "AWS::ApiGateway::Authorizer":
                required(logical, props, ("RestApiId", "Name", "Type"))
                authorization = props.get("Type")
                if authorization is not None and authorization not in ("TOKEN", "REQUEST", "COGNITO_USER_POOLS"):
                    issue(logical, "Type", "REST authorizers support TOKEN, REQUEST, or COGNITO_USER_POOLS; native JWT requires an HTTP API.",
                          "Keep required REST validation and configure Cognito/a JWT-validating Lambda, or explicitly choose HTTP API.")
                if authorization in ("TOKEN", "REQUEST"):
                    required(logical, props, ("AuthorizerUri",))
                if authorization in ("TOKEN", "COGNITO_USER_POOLS"):
                    required(logical, props, ("IdentitySource",))
                if authorization == "COGNITO_USER_POOLS":
                    required(logical, props, ("ProviderARNs",))
                for field in ("AuthorizerType", "JwtConfiguration", "Tags", "AuthorizerPayloadFormatVersion", "EnableSimpleResponses"):
                    if field in props:
                        issue(logical, field, f"{field} is not a REST authorizer property.", "Use the correct REST authorizer schema.")
            elif kind == "AWS::ApiGatewayV2::Authorizer":
                required(logical, props, ("ApiId", "Name", "AuthorizerType", "IdentitySource"))
                if not isinstance(props.get("IdentitySource", []), (list, dict)):
                    issue(logical, "IdentitySource", "HTTP authorizer IdentitySource must be a list.", "Use ['$request.header.Authorization'].")
                if props.get("AuthorizerType") == "JWT":
                    jwt(logical, props.get("JwtConfiguration"))
                elif props.get("AuthorizerType") == "REQUEST":
                    required(logical, props, ("AuthorizerUri",))
                elif props.get("AuthorizerType") is not None:
                    issue(logical, "AuthorizerType", "API Gateway v2 authorizers support JWT or REQUEST.", "Select a supported authorizer type.")
            elif kind == "AWS::ApiGateway::Method":
                required(logical, props, ("RestApiId", "ResourceId", "HttpMethod", "AuthorizationType"))
                if props.get("AuthorizationType") not in (None, "NONE", "AWS_IAM", "CUSTOM", "COGNITO_USER_POOLS"):
                    issue(logical, "AuthorizationType", "REST methods do not support native JWT authorization.",
                          "Configure Cognito or a Lambda authorizer; do not remove the authentication requirement.")
                if props.get("AuthorizationType") in ("CUSTOM", "COGNITO_USER_POOLS"):
                    required(logical, props, ("AuthorizerId",))
                if isinstance(props.get("ResourceId"), str) and props["ResourceId"].startswith("/"):
                    issue(logical, "ResourceId", "A URL path is not an API Gateway resource ID.",
                          "Define methods in OpenAPI or reference an AWS::ApiGateway::Resource ID.")
            if kind not in ("AWS::Serverless::Api", "AWS::Serverless::HttpApi", "AWS::ApiGateway::RestApi", "AWS::ApiGatewayV2::Api"):
                continue
            rest = kind in ("AWS::Serverless::Api", "AWS::ApiGateway::RestApi")
            body = props.get("DefinitionBody", props.get("Body", {}))
            if not isinstance(body, dict):
                continue
            schemes = body.get("components", {}).get("securitySchemes", body.get("securityDefinitions", {}))
            secured = set()
            for security in [body.get("security", [])] + [operation.get("security", body.get("security", []))
                    for route in body.get("paths", {}).values() if isinstance(route, dict)
                    for method, operation in route.items() if isinstance(operation, dict)
                    and method.lower() in ("get", "post", "put", "patch", "delete", "head", "options", "x-amazon-apigateway-any-method")]:
                for requirement in security:
                    secured.update(requirement)
            for name in secured:
                scheme = schemes.get(name, {})
                extension = scheme.get("x-amazon-apigateway-authorizer", {})
                sam_authorizers = props.get("Auth", {}).get("Authorizers", {})
                iam = scheme.get("x-amazon-apigateway-authtype") == "awsSigv4"
                api_key = scheme.get("type") == "apiKey" and scheme.get("name", "").lower() == "x-api-key"
                if not extension and name not in sam_authorizers and not iam and not api_key:
                    issue(logical, f"securitySchemes.{name}", "Protected OpenAPI security scheme has no executable API Gateway authorizer.",
                          "Configure a SAM authorizer or x-amazon-apigateway-authorizer; a bearer declaration alone does not enforce authentication.")
                if extension.get("type", "").lower() == "jwt":
                    if rest:
                        issue(logical, f"securitySchemes.{name}", "Native JWT authorizers cannot protect a REST API.",
                              "Use Cognito or a JWT-validating Lambda authorizer, or explicitly choose HTTP API.")
                    jwt(logical, extension.get("jwtConfiguration"))
    return diagnostics
