# Floci AWS emulator

MODRISS can run generated AWS artifacts against Floci or LocalStack without changing the generated
CloudFormation, SAM, OpenAPI, or AWS SDK code. Floci is the default because it starts quickly and
uses the same single edge endpoint (`4566`) used by the generated tests.

## Select the provider

Copy `.env.example` to `.env` and choose one provider:

```dotenv
AWS_EMULATOR=floci
COMPOSE_PROFILES=${AWS_EMULATOR}
AWS_EMULATOR_ENDPOINT_URL=http://127.0.0.1:4566
AWS_DEFAULT_REGION=us-east-1
```

Use `AWS_EMULATOR=localstack` when a compatibility run requires LocalStack. The provider-specific
variables remain available; switching does not remove or migrate either emulator's data.

## Start and inspect

```powershell
powershell -ExecutionPolicy Bypass -File scripts/aws-emulator.ps1 start
powershell -ExecutionPolicy Bypass -File scripts/aws-emulator.ps1 status
```

```bash
./scripts/aws-emulator.sh start
./scripts/aws-emulator.sh status
```

The helper stops the opposite profile, recreates the selected container, and waits for its health
endpoint. Floci is reachable at `http://127.0.0.1:4566`; Caddy also exposes
`http://floci.localhost:8088` when the main Compose stack is running.

## Run generated projects

Generated projects contain `scripts/emulator-env.sh` and a `make emulator-env` target. Source the
helper before AWS CLI, SAM, or custom SDK commands:

```bash
source scripts/emulator-env.sh
aws sts get-caller-identity
sam deploy --stack-name case-study --no-confirm-changeset --no-fail-on-empty-changeset
make test
```

The helper sets dummy local credentials, region, `AWS_ENDPOINT_URL`, and metadata-service disable
flags. It accepts `AWS_EMULATOR=floci|localstack` and an optional
`AWS_EMULATOR_ENDPOINT_URL` override. Generated Go handlers return JSON string bodies compatible
with Lambda proxy and Function URL responses.

## Test workspace

The **Artifacts** tab shows **Test** and **Deploy to Test Environment** when the selected cloud
provider is **AWS · Built in**. The accented deployment button sits beside Download in the
**Advance** toolbar section. Other modeling tabs and uploaded cloud packages do not expose
these entry controls.

The local development Compose configuration builds the disposable worker image before starting
the backend. For a stack started with the base/production Compose files, build it before starting
the backend:

```bash
docker compose --profile test-tools build test-runner
docker compose up -d --build backend frontend
```

`scripts/dev.sh` also builds this image. The backend checks the runner image before deploying and
uses `MODRISS_TESTING_RUNNER_IMAGE`
(default `modriss-test-runner:local`) and `MODRISS_TESTING_EMULATOR_IMAGE`
(default `floci/floci:2.2.0`). Docker must have the Floci image available or be able to pull it.
REST Cognito evaluation requires Floci 2.2.0 or newer; older versions do not enforce these
authorizers. The Test image setting is independent of the shared CLI emulator's `FLOCI_IMAGE`.
When changing an existing project's Test image, the backend pulls it before replacing the old
instance. On the next deployment, the backend stops the old instance, copies its persistent volume to a
managed backup volume, and recreates the instance with the original volume. It never resets
project data to upgrade the emulator. If the backup fails, the old instance is restarted.
On first deployment, the backend creates a dedicated Floci container, Docker network, and named
data volume for the project. If the configured Floci image is not cached locally, the backend
pulls it and records that setup step in the deployment timeline. The disposable runner joins that project's network and connects to
its private `http://aws-emulator:4566` alias. No emulator ports are published to the host.
The shared Compose Floci service remains available for command-line development; Test workspaces
do not use it. The old `MODRISS_TESTING_NETWORK` and testing endpoint settings are no longer used.
Real AWS credentials are never passed to the worker. Each instance's default simulated account
is set to the project's stable account ID, so unsigned application requests address the right
resources without consuming the application's authentication header.

1. Generate AWS artifacts and complete the implementation in the artifact editor or your local IDE.
2. Save edits. To bring local changes back, open **Deployment** and import a ZIP of the refined
   UTF-8 project sources. This replaces the selected artifact's sources. A single enclosing project
   directory is removed; `.git`, `bin`, `.aws-sam`, and `node_modules` are excluded. Binary assets
   are not supported by the artifact text store. Source limits are 30 MB total, 3 MB per file, and
   3,000 files; the configured HTTP multipart upload limit also applies.
3. Choose the artifact, stacks, and template parameter values, then select **Build & deploy to
   Floci**. The worker captures the saved sources, checks API/authorizer template contracts before
   building, runs `scripts/build.sh` when present, packages
   local SAM code and definition paths, and creates or updates the selected CloudFormation stacks.
   Failed builds and unsupported resources produce failed deployment evidence. A failed newer
   deployment blocks testing of the previous snapshot until a successful deployment restores a
   known environment. Redeployment retains emulator data according to the template's update behavior.
   Preflight failures list every detected issue with its template, resource, field, and remedy.
   Selected stacks are ordered by constant CloudFormation exports/imports, so shared identity
   pools are provisioned before APIs that use them. New stack creates request `OnFailure=DO_NOTHING`.
   Floci 2.2.0 currently ignores this AWS preservation option and can still roll a failed create
   back. Failed stacks cannot be mistaken for unchanged, successful deployments.
   An initial stack already in `ROLLBACK_COMPLETE` cannot be updated by AWS. Its retained
   resources need recovery/import, or an archived environment and a fresh deployment; the worker
   reports this condition without deleting the retained data.
4. Select an API, function, workflow, event resource, storage resource, log group, or source script.
   Choose its action and edit the JSON input. API routes and deployed stages are discovered from
   Floci. API requests accept `method`, `path`, `stage`, `query`, `headers`, and `body` fields.
   Edited request JSON stays in memory per resource/action during refreshes and executions;
   switching projects clears those drafts. Bearer tokens are not saved in browser storage.
   DynamoDB writes use AWS `AttributeValue` JSON, for example `{"item":{"id":{"S":"case-1"}}}`.
   Script inputs accept `args` (an array of strings) and `env` (project-specific variables).
   Runner credentials, emulator endpoints, and interpreter paths are reserved.
5. Add a requirement or scenario label, execute, and inspect the actual response and timeline.
   Workflow diagrams use the deployed ASL and are colored from execution-history events; select
   a state to inspect its observed input and output. For long-running workflows, use **List
   executions** and select an execution to retrieve its current history.
6. Use **Execution history** and **Export evidence** to retain the source fingerprint, scenario,
   exact submitted input, timestamps, observed events, errors, and response for your case study.

The initial interactive adapters cover REST and HTTP API Gateway, Lambda invocation and logs,
Step Functions execution/history, DynamoDB scan/put, S3 list/read/write, SQS send/peek, SNS publish,
EventBridge publish, CloudWatch log groups, and shell/Python scripts under `scripts/` and `tests/`.
Other provisioned resource types remain visible with their definitions for inspection. Discovery
errors are shown on the affected resource. **Advanced AWS request** exposes the emulator's AWS SDK
operations for additional services and resource types; choose an operation and supply its SDK
parameters. These requests use the selected project's emulator account and retain their responses
as execution evidence. Runtime-specific scripts require their tools in the
worker image; the supplied image includes Python, boto3, AWS CLI, Go, Bash, and Make.

SAM is expanded with AWS's SAM translator. The adapter selects OpenAPI 3 for automatically
synthesized REST definitions and imports resolved REST/HTTP definitions through API Gateway's
management APIs before refreshing stages. This avoids incomplete API provisioning in older Floci
versions. These management calls appear in deployment evidence. Shell script CRLF line endings
are normalized in the disposable workspace; the saved source snapshot remains unchanged.
Floci 2.2 can import request models as empty schemas. The adapter installs the generated schemas
through API Gateway's model APIs before refreshing stages, including local schema references.
GET/HEAD/DELETE object inputs are generated as query/path parameters. Supply them in `query`
and the API path; body-oriented operations such as POST use `body` and its schema validation.
For Floci versions that omit method names from REST resource listings, route discovery queries
the actual API Gateway method definitions. Missing methods are excluded; other discovery failures
remain visible on the affected API.
Unchanged infrastructure is retained. Changes confined to packaged Lambda code use Lambda's real
code-update API so redeployment preserves tables, queues, and event mappings on older Floci
versions. Infrastructure changes still use CloudFormation and can expose emulator compatibility
limits; inspect deployment diagnostics before retrying a failed update.

API requests preserve the supplied `Authorization` header, including `Bearer` tokens; requests
without that header remain unauthenticated. Management SDK calls use separate dummy AWS signing
credentials. There is no need to change the application's identity header for local tests.
For an API that explicitly uses AWS IAM authentication, add `"auth":"aws-iam"` to the request
input to sign it with the project's dummy AWS credentials. This mode rejects a supplied
`Authorization` header to prevent silently replacing application credentials. The default mode
is `"auth":"application"`. Authentication behavior remains subject to the authorizer and
features supported by the pinned Floci version; header forwarding alone does not establish JWT
signature validation or equivalence to real AWS.
The live fixture also exercises a REST TOKEN Lambda authorizer. The workspace records Floci's
actual status without translating it. The fixture's application-level checks return 401 for
missing, invalid, or expired opaque tokens and 403 for denied access; these are header-preservation
tests. Cognito tests use actual tokens minted by Floci and a REST user-pool authorizer.

For a PIM API with an `AuthPolicy.identityProvider`, generation uses a Cognito REST authorizer,
including when the route also requires roles. Shared pools use CloudFormation exports/imports.
Native JWT authorizers belong to HTTP APIs. An API that needs REST request validation must use
Cognito or a JWT-validating Lambda authorizer. For an external HTTP JWT provider, configure
`AuthPolicy.tokenValidationRules` as `issuer=https://issuer.example;audience=client-id`;
validation prose and role names are not issuer URLs or client audiences.
Actual OAuth scopes are retained in route security requirements. Review placeholders are recorded
as blocking refinement decisions. Cognito authenticates the token; modeled role and case-ownership
rules still require implementation in the handler or a reviewed authorizer. Those prose requirements
are retained as explicit blocking refinement decisions rather than being treated as implemented scopes.

After updating these generator rules, regenerate **PIM → AWS PSM → artifacts**. Existing snapshots
retain the old templates. A generated project still needs business implementation: unresolved
workflow decisions remain explicit `Modriss.WorkflowRequiresRefinement` failure states, and
unreachable draft branches are omitted from deployable ASL until their routing is completed.
The emitted ASL remains valid while identifying incomplete requirements. Deploying the complete
application also requires actual external endpoint URLs and reviewed business implementation.
PIM `RELATIONAL` stores now map automatically to typed Aurora PostgreSQL clusters/writers and
generated credential secrets. Data models become SQL tables with typed columns and primary keys.
The Test deployment initializes these schemas through Floci's real RDS Data API after provisioning
the stacks. Missing fields/keys and relational change-event publication remain explicit decisions.
The existing DynamoDB mapping remains available for key-value/document/cache stores.

Relational clusters appear under **Storage** in the Test workspace. **Browse tables and columns**
shows the actual database schema. **Run SQL** accepts `sql`, Data API `parameters`, and an optional
`transactionId`. **Begin transaction**, **Commit transaction**, and **Roll back transaction** expose
actual database transaction controls; use the returned ID for subsequent statements. Read results
include convenient `rows` alongside the original Data API metadata/records. Bounded before/after
storage observations include up to five generated SQL tables and 25 rows per table.

Unchanged schema redeployment retains data. A changed generated initial schema stops with a
migration message; it does not drop tables or silently pretend that new columns were applied.
The generated `scripts/database.py` requires Python and boto3 when deploying outside the supplied
worker. Floci must be able to pull its PostgreSQL image and start database containers through Docker.
See [PSM storage](../dsml/psm/awspsm-storage.md) for the editable schema and resource fields.
The Test workspace applies the **Floci Test** deployment profile to an in-memory copy of each
selected template. For a SAM state machine with `AutoPublishAlias`, the worker removes alias
publication, alias event-target, and alias deployment-preference fields before SAM expansion, while
preserving the workflow definition and deploying the executable state machine. The saved artifact,
PSM, and production-oriented generated template remain unchanged. The deployment result and
timeline identify every adjusted template and state machine. This profile does not evaluate version
publication or alias routing. Explicit
`AWS::StepFunctions::StateMachineVersion` and `AWS::StepFunctions::StateMachineAlias` resources
remain preflight errors because their routing cannot be safely inferred. Floci also cannot
provision Secrets Manager rotation schedules through CloudFormation; configured schedules are
reported before the build and must be evaluated separately. Incomplete rotation schedules without
a rotation Lambda remain visible in the model/readiness report.
Generated functions omit reserved concurrency when no limit is modeled. An explicit zero
reservation disables invocation; use it only when intentionally pausing a function.
Unrefined business handlers can return `500` with `NOT_IMPLEMENTED` after successful
authentication and input validation. Complete those handlers before expecting business success.

Instances remain running across backend restarts and use a per-project persistent data volume
with Floci's hybrid storage. Ordinary redeployment reuses the instance and its data. Restarting
or replacing an emulator requires redeployment before new tests can run: stored resource IDs
must not be used against an uncertain runtime. Deployments from the older shared-account
adapter also require redeployment into the dedicated instance; their evidence remains readable.
Existing shared-emulator data is not automatically copied. The backend reconciles deleted projects
every 60 seconds and removes their managed instance, Lambda containers, data volume, and network.
These dynamically created Docker resources are not removed by `docker compose down`; retain the
project to keep its test environment, or delete the project while the backend is running to clean
it up. Each Floci instance is limited to two CPUs and 2 GB memory, in addition to its Lambda
containers and disposable execution workers.

The backend also captures actual Lambda container stdout/stderr using the project's network and
Floci's account, region, service, and resource labels. This covers emulator versions that expose runtime output in Docker
without persisting it to CloudWatch. Those records identify their source explicitly; Lambda log
reads return them as `runtimeEvents` alongside the CloudWatch response. Project code never receives
the Docker socket. Runtime capture is limited to 20 matching containers, 200 tail lines per poll,
and 2,000 observations / 2 MB per job; output from containers removed before collection may be unavailable.

Execution jobs run in disposable containers with no host mounts, a read-only root filesystem,
bounded temporary storage, and CPU/memory/process limits. Project editors can execute; project
members can read evidence. The queue permits two concurrent projects and one operation per project.
Jobs time out after 15 minutes. A backend restart marks unfinished jobs interrupted; inspect Floci
before retrying because already-created emulator resources may remain.

Runner image builds honor `HTTP_PROXY` / `HTTPS_PROXY`, with optional
`MODRISS_TEST_BUILD_HTTP_PROXY` / `MODRISS_TEST_BUILD_HTTPS_PROXY` overrides. A proxy bound to
host loopback must be reachable from Docker, for example through `host.docker.internal` on
Docker Desktop.

Observations are bounded: DynamoDB snapshots include up to 25 items per table, S3 snapshots up to
100 keys, resource reads expose pagination cursors, logs are bounded, and evidence captures up to
5,000 events / 8 MB. State-machine startup observes for five minutes, then retains the execution ARN
for further inspection. Storage and downstream logs are live observations during the run's time
window; concurrent emulator traffic can also contribute to them. They do not prove causation.
SQS inspection receives messages with zero visibility timeout and does not delete them, but active
consumers can receive or remove messages independently. Changing artifact files does not alter a
deployed snapshot; a notice prompts redeployment.

Run the real integration check explicitly against the local stack:

```bash
node apps/frontend/tests/test-environment.live.mjs
```

It imports the HTTP architecture sample, generates an artifact through the production pipeline,
imports a developer-refined Go/SAM fixture, and checks real deployment, APIs, Lambdas, workflows,
data, events, logs, scripts, access isolation, snapshot evidence, and desktop/mobile UI. It writes
screenshots and execution evidence under `target/`.

## State and compatibility

The Compose profile uses `FLOCI_STORAGE_MODE=memory` by default, so recreating the container gives a
clean evaluation run. Set `persistent` (and keep the `modriss-floci-data` volume) when state must
survive restarts. Remove that named volume only when intentionally discarding emulator state.

Set `FLOCI_CFN_ALLOW_STUB_UNSUPPORTED_RESOURCE_TYPES=false` for strict thesis evidence: an
unsupported CloudFormation type fails the stack instead of silently becoming a synthetic resource.
Floci provisions Lambda code in Docker, so the Compose service mounts the Docker socket. Keep this
mount limited to local development and never expose the emulator publicly.

Floci and LocalStack implement the same AWS-compatible API surface for the services used by the
generated tests (Lambda, API Gateway, S3, SQS, SNS, DynamoDB, IAM, CloudFormation, and
Step Functions). Always run a final deployment in a real AWS account before production claims.
