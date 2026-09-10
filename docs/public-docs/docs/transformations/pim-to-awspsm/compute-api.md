# PIM → AWS PSM: Compute API

Compute and API binding converts provider-independent execution into Lambda and API Gateway resources. The rules choose HTTP API versus REST API, materialize routes after their parent API exists, infer conservative operational settings, carry contracts into OpenAPI models, and emit explicit manual decisions where PIM intent cannot safely determine AWS implementation.

Source module: `mde/transformations/pim-to-awspsm/compute-api.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                     | Role                                                                                   | Source                                                  |
| ----------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| `apiForRoute`                 | Resolves the owning PIM API for a route.                                               | `mde/transformations/pim-to-awspsm/compute-api.etl:151` |
| `materializeApiRoutes`        | Creates any API routes that were skipped because API resources were not available yet. | `mde/transformations/pim-to-awspsm/compute-api.etl:183` |
| `createHttpApiRouteFrom`      | Creates create http api route from.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:206` |
| `createRestApiRouteFrom`      | Creates create rest api route from.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:239` |
| `inferMemoryMb`               | Derives infer memory mb.                                                               | `mde/transformations/pim-to-awspsm/compute-api.etl:269` |
| `inferTimeoutSeconds`         | Derives infer timeout seconds.                                                         | `mde/transformations/pim-to-awspsm/compute-api.etl:286` |
| `inferReservedConcurrency`    | Derives infer reserved concurrency.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:307` |
| `createLambdaTracing`         | Creates create lambda tracing.                                                         | `mde/transformations/pim-to-awspsm/compute-api.etl:315` |
| `createLambdaLogging`         | Creates create lambda logging.                                                         | `mde/transformations/pim-to-awspsm/compute-api.etl:325` |
| `createLambdaEnvironment`     | Creates create lambda environment.                                                     | `mde/transformations/pim-to-awspsm/compute-api.etl:337` |
| `resourceNameEnv`             | Computes resource name env.                                                            | `mde/transformations/pim-to-awspsm/compute-api.etl:379` |
| `createLambdaRole`            | Creates create lambda role.                                                            | `mde/transformations/pim-to-awspsm/compute-api.etl:390` |
| `createVpcAttachmentDecision` | Creates create vpc attachment decision.                                                | `mde/transformations/pim-to-awspsm/compute-api.etl:403` |
| `routeNeedsAuth`              | Computes route needs auth.                                                             | `mde/transformations/pim-to-awspsm/compute-api.etl:416` |
| `inferAuthorizationType`      | Derives infer authorization type.                                                      | `mde/transformations/pim-to-awspsm/compute-api.etl:421` |
| `applyAuthorizerFallback`     | Computes apply authorizer fallback.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:464` |
| `mapHttpMethod`               | Computes map http method.                                                              | `mde/transformations/pim-to-awspsm/compute-api.etl:478` |
| `inferRouteTimeoutMillis`     | Derives infer route timeout millis.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:504` |
| `createApiIntegration`        | Creates create api integration.                                                        | `mde/transformations/pim-to-awspsm/compute-api.etl:512` |
| `requiresRestApi`             | Returns whether the receiver requires rest api.                                        | `mde/transformations/pim-to-awspsm/compute-api.etl:548` |
| `createApiStages`             | Creates create api stages.                                                             | `mde/transformations/pim-to-awspsm/compute-api.etl:553` |
| `fillApiStage`                | Computes fill api stage.                                                               | `mde/transformations/pim-to-awspsm/compute-api.etl:577` |
| `createApiAuthorizer`         | Creates create api authorizer.                                                         | `mde/transformations/pim-to-awspsm/compute-api.etl:605` |
| `findAuthorizerFor`           | Resolves find authorizer for.                                                          | `mde/transformations/pim-to-awspsm/compute-api.etl:656` |
| `createCors`                  | Creates create cors.                                                                   | `mde/transformations/pim-to-awspsm/compute-api.etl:668` |
| `schemaRefJson`               | Computes schema ref json.                                                              | `mde/transformations/pim-to-awspsm/compute-api.etl:703` |
| `attachOpenApiModels`         | Adds or records attach open api models.                                                | `mde/transformations/pim-to-awspsm/compute-api.etl:711` |
| `createRequestModel`          | Creates create request model.                                                          | `mde/transformations/pim-to-awspsm/compute-api.etl:724` |
| `createResponseModel`         | Creates create response model.                                                         | `mde/transformations/pim-to-awspsm/compute-api.etl:736` |
| `generateOpenApi`             | Computes generate open api.                                                            | `mde/transformations/pim-to-awspsm/compute-api.etl:748` |
| `openApiSchemas`              | Computes open api schemas.                                                             | `mde/transformations/pim-to-awspsm/compute-api.etl:765` |
| `addSchemaOnce`               | Adds or records add schema once.                                                       | `mde/transformations/pim-to-awspsm/compute-api.etl:785` |
| `openApiRequestBody`          | Computes open api request body.                                                        | `mde/transformations/pim-to-awspsm/compute-api.etl:792` |
| `openApiResponses`            | Computes open api responses.                                                           | `mde/transformations/pim-to-awspsm/compute-api.etl:802` |
| `statusForError`              | Computes status for error.                                                             | `mde/transformations/pim-to-awspsm/compute-api.etl:821` |
| `openApiSecurity`             | Computes open api security.                                                            | `mde/transformations/pim-to-awspsm/compute-api.etl:832` |
| `scopesJson`                  | Computes scopes json.                                                                  | `mde/transformations/pim-to-awspsm/compute-api.etl:846` |
| `openApiSecuritySchemes`      | Computes open api security schemes.                                                    | `mde/transformations/pim-to-awspsm/compute-api.etl:857` |
| `createApiKeyAndUsagePlan`    | Creates create api key and usage plan.                                                 | `mde/transformations/pim-to-awspsm/compute-api.etl:865` |
| `secretValue`                 | Computes secret value.                                                                 | `mde/transformations/pim-to-awspsm/compute-api.etl:905` |

---

## `Function2AwsLambdaFunction`

**Source:** `f` to `COMPUTE!Function`  
**Target:** `l` to `AWSPSMCOMPUTE!AwsLambdaFunction`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:2`

### Why this rule exists

A PIM function becomes a Lambda resource with code configuration, runtime limits, role, logging, tracing, environment, idempotency, dead-letter, network, and policy decisions. The rule also creates explicit blocker/manual decisions for business logic and EFS work that cannot be generated safely from the model alone.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `l` (`AWSPSMCOMPUTE!AwsLambdaFunction`): Generated aws lambda function (l).

### Important behavior encoded in the rule

The rule directly assigns: `l.id`, `l.functionName`, `l.descriptionText`, `l.code`, `l.packageType`, `l.memorySizeMb`, `l.timeoutSeconds`, `l.architecture`, `l.ephemeralStorageMb`, `l.reservedConcurrentExecutions`, `l.recursiveLoopMode`, `l.snapStartApplyOn`, `l.runtimeManagementMode`, `l.publishVersion`, `l.autoPublishAlias`, `l.codeSigningDecision` ….
Manual decisions raised by this rule: `LAMBDA_BUSINESS_LOGIC_IMPLEMENTATION_REQUIRED`, `LAMBDA_EFS_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `LAMBDA_BUSINESS_LOGIC_IMPLEMENTATION_REQUIRED, LAMBDA_EFS_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the function actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:2`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Api2HttpApi`

**Source:** `api` to `API!Api`  
**Target:** `g` to `AWSPSMAPI!HttpApi`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:69`

### Why this rule exists

An HTTP-shaped PIM API becomes an AWS SAM HttpApi when its semantics do not require REST-specific capabilities. The rule establishes conservative logs, tracing, metrics, CORS, OpenAPI, stages, and authorizers while leaving route materialization to a later phase.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : (api.apiStyle = PIMTYPES!ApiStyle#RESOURCE_ORIENTED_HTTP or api.apiStyle = PIMTYPES!ApiStyle#RPC_HTTP or api.apiStyle = PIMTYPES!ApiStyle#WEBHOOK) and not requiresRestApi(api)
```

### What it creates

- `g` (`AWSPSMAPI!HttpApi`): Generated http api (g).

### Important behavior encoded in the rule

The rule directly assigns: `g.id`, `g.apiName`, `g.descriptionText`, `g.accessLogsEnabled`, `g.tracingEnabled`, `g.metricsEnabled`, `g.corsEnabled`, `g.protocolType`, `g.disableExecuteApiEndpoint`, `g.minimumCompressionSize`, `g.failOnWarnings`, `g.openApiVersion`, `g.cors`, `g.definitionBody`, `g.accessLogGroup`.

### How to troubleshoot or repair it

Verify the api instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:69`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Api2RestApi`

**Source:** `api` to `API!Api`  
**Target:** `g` to `AWSPSMAPI!RestApi`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:101`

### Why this rule exists

A PIM API that needs API keys, usage plans, or other REST semantics becomes a SAM RestApi. The guard is the provider capability boundary; choosing REST here avoids forcing REST-only behavior into every API while still producing the richer concrete resource when required.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : (api.apiStyle = PIMTYPES!ApiStyle#RESOURCE_ORIENTED_HTTP or api.apiStyle = PIMTYPES!ApiStyle#RPC_HTTP or api.apiStyle = PIMTYPES!ApiStyle#WEBHOOK) and requiresRestApi(api)
```

### What it creates

- `g` (`AWSPSMAPI!RestApi`): Generated rest api (g).

### Important behavior encoded in the rule

The rule directly assigns: `g.id`, `g.apiName`, `g.descriptionText`, `g.accessLogsEnabled`, `g.tracingEnabled`, `g.metricsEnabled`, `g.corsEnabled`, `g.endpointType`, `g.minimumCompressionSize`, `g.failOnWarnings`, `g.apiKeyRequiredByDefault`, `g.openApiVersion`, `g.cors`, `g.definitionBody`, `g.accessLogGroup`.

### How to troubleshoot or repair it

Verify the api instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:101`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Api2NativeUnsupportedApi`

**Source:** `api` to `API!Api`  
**Target:** `n` to `AWSPSMCORE!AwsNativeResource`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:132`

### Why this rule exists

Unsupported API styles are not silently dropped. The rule creates a review-required native placeholder with the original PIM style and a blocking decision, preserving the architecture and making provider incompatibility visible instead of generating a misleading HTTP API.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : not (api.apiStyle = PIMTYPES!ApiStyle#RESOURCE_ORIENTED_HTTP or api.apiStyle = PIMTYPES!ApiStyle#RPC_HTTP)
```

### What it creates

- `n` (`AWSPSMCORE!AwsNativeResource`): Review-required placeholder for an unsupported mapping.

### Important behavior encoded in the rule

The rule directly assigns: `n.id`, `n.cloudFormationType`.
Manual decisions raised by this rule: `API_STYLE_UNSUPPORTED_DIRECTLY`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `API_STYLE_UNSUPPORTED_DIRECTLY` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the api actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:132`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ApiRoute2HttpApiRoute`

**Source:** `r` to `API!ApiRoute`  
**Target:** `gr` to `AWSPSMAPI!HttpApiRoute`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:163`

### Why this rule exists

An API route becomes an HttpApi route only after its parent PIM API has a concrete HTTP equivalent. The guard protects against orphan routes and prevents REST-specific routing from being emitted into an HTTP API.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : apiForRoute(r).isDefined() and awsEquivalent(apiForRoute(r)).isDefined() and not requiresRestApi(apiForRoute(r))
```

### What it creates

- `gr` (`AWSPSMAPI!HttpApiRoute`): Generated http api route (gr).

### Important behavior encoded in the rule

The rule delegates most construction to a helper operation; follow the source link and the referenced helper when investigating field-level behavior.

### How to troubleshoot or repair it

Verify the api route instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:163`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ApiRoute2RestApiRoute`

**Source:** `r` to `API!ApiRoute`  
**Target:** `gr` to `AWSPSMAPI!RestApiRoute`  
**Source location:** `mde/transformations/pim-to-awspsm/compute-api.etl:173`

### Why this rule exists

This is the REST counterpart of route binding. It waits for the concrete RestApi parent and delegates detailed integration, models, authorization, and OpenAPI behavior to the route builder so route output remains consistent with the chosen API flavor.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : apiForRoute(r).isDefined() and awsEquivalent(apiForRoute(r)).isDefined() and requiresRestApi(apiForRoute(r))
```

### What it creates

- `gr` (`AWSPSMAPI!RestApiRoute`): Generated rest api route (gr).

### Important behavior encoded in the rule

The rule delegates most construction to a helper operation; follow the source link and the referenced helper when investigating field-level behavior.

### How to troubleshoot or repair it

Verify the api route instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/compute-api.etl:173`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
