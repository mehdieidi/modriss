# OrderPulse AWS PSM case study

OrderPulse is the persisted AWS PSM system used to exercise the MODRISS AWS PSM-to-artifacts
pipeline. It models a small but complete serverless order workflow: an HTTP API accepts an order,
DynamoDB stores it, SQS buffers processing, a Lambda consumer marks it `PROCESSED`, and a second API
operation reads it back. The PSM also includes a custom EventBridge bus and rule, an SNS topic, an
S3 receipt bucket, a Step Functions workflow, CloudWatch log groups and alarm, IAM role/policy, and
an SQS DLQ.

The source model is [`orderpulse.awspsm.xmi`](orderpulse.awspsm.xmi). The manually completed
generated project is [`generated-project`](generated-project/). The generated directory is ignored
by the repository's generic `generated-project/` ignore rule, but is intentionally retained as the
reproducible artifact under study.

## Requirements and quality expectations

| ID  | Expected behavior                                                                                                                                                                                                                        |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R1  | A valid `POST /orders` returns `201`, creates a deterministic order ID, stores a `PENDING` DynamoDB record, and enqueues the order.                                                                                                      |
| R2  | Invalid JSON or missing/empty fields returns `400` and does not create a valid order.                                                                                                                                                    |
| R3  | `GET /orders/{orderId}` returns `200` and the stored order, or `404` for an unknown ID.                                                                                                                                                  |
| R4  | The SQS consumer changes the order status to `PROCESSED`; malformed records are reported through partial-batch `batchItemFailures`.                                                                                                      |
| R5  | The generated infrastructure contains the API, Lambda functions, DynamoDB table, SQS queue/DLQ and source mapping, EventBridge bus/rule, SNS topic/subscription and queue policy, S3 controls, workflow, logs, alarm, and IAM resources. |
| Q1  | Order creation is idempotent for the same customer/item/amount input through the deterministic ID and generated idempotency boundary.                                                                                                    |
| Q2  | The generated project uses Linux `provided.al2023` Go bootstraps, explicit per-function artifact paths, bounded Lambda timeouts, structured error responses, and observable log groups.                                                  |
| Q3  | The generated IAM policy is least-privilege scoped for the modeled Lambda behavior: DynamoDB, SQS, and Lambda log resources are explicit CloudFormation ARN expressions.                                                                 |

## Generation

From the repository root:

```powershell
mvn -q -DskipTests -pl tools/mde-m2t-cli -am package
java -jar tools/mde-m2t-cli/target/mde-m2t-cli-0.0.1-SNAPSHOT.jar aws-psm-to-artifacts `
  --repo-root . `
  --source-model mde/samples/orderpulse/orderpulse.awspsm.xmi `
  --output-dir mde/samples/orderpulse/generated-project `
  --fail-if-output-not-empty `
  --log-file mde/samples/orderpulse/generation-report.json `
  --verbose
```

The final generation produced 83 artifacts and zero generator-reported manual issues. The generator
report, trace, and manual-action report are under `generated-project/generated/reports/`.

## Manual completion

The generated EGL protected regions were completed as follows:

1. `src/shared/data-access.go` was implemented with AWS SDK for Go v2 DynamoDB and SQS access,
   Floci endpoint selection through `AWS_ENDPOINT_URL`, order serialization, lookup, update, and
   message decoding.
2. `orderpulse-create-order/handler.go` was completed with request parsing, validation, persistence,
   queue submission, and `201` response construction.
3. `orderpulse-get-order/handler.go` was completed with path parameter lookup and `200`/`404`
   responses.
4. `orderpulse-process-order/handler.go` was completed with SQS record decoding, status updates,
   and per-message partial-batch failure reporting.
5. AWS SDK dependencies were added to `go.mod`; the three Linux `bootstrap` binaries were built
   into their generated `bin/<function-slug>/` directories.
6. The generated PowerShell build, emulator environment, preflight, and native workflow probes were
   parsed or executed on Windows.

No manual implementation was used to hide a generation failure. The generator source was fixed
where the generated artifact itself was wrong, then the affected output was regenerated and
rechecked.

## Generator defects found and fixed

- Failed EGX staging/publishing could carry a null report; the CLI then threw a secondary null
  pointer and hid the original generation error. The runner now returns a failed report with a
  diagnostic, and the CLI prints a safe root-cause summary.
- Epsilon's model-loading exception path could itself dereference a null model while formatting
  the message. The runner now extracts the internal cause safely.
- Go custom imports were emitted after the import block. The EGL template now places the protected
  import region inside the block.
- Generated Lambda result types lacked the SQS partial-batch response field. The Go template now
  emits `BatchItemFailures`.
- Generated known-error mapping returned the same status for all errors. Validation, not-found,
  and conflict errors now map to `400`, `404`, and `409` respectively.
- Multiple Lambda functions were all emitted with `CodeUri: '.'`, although the generated build
  script creates function-specific bootstrap directories. The path helper now emits
  `bin/<function-slug>` and the regression expectations were updated.
- EventBridge rules with a custom bus could silently land on the default bus when an XMI reference
  was unresolved. The renderer now resolves an unambiguous same-stack bus and emits
  `EventBusName`; the Floci deployment confirmed the rule ARN contains `orderpulse-bus`.
- The model was corrected to use valid PSM enum literals (`ENABLED`, `STRICT_BLOCK_ALL`, and `SQS`)
  and Step Functions `nextState` values matching state names.
- SNS subscriptions with unresolved standalone-XMI topic references now resolve an unambiguous topic
  in the same stack. The model now includes an SNS-to-SQS subscription and the required queue policy.
- IAM policy statements with Principals previously lost their Resource field. The IAM renderer now
  emits Resource independently, and SAM lint caught and confirmed the correction.
- Windows PowerShell templates initially violated `param` ordering, concatenated `GOARCH` with the
  invocation operator, and used an unavailable role-name helper. These generated-script defects
  were fixed and the generated PowerShell build/workflow scripts now execute successfully.

## Verification performed

Static and artifact checks:

- AWS PSM generation completed successfully with 83 artifacts and zero manual issues.
- Generated JSON artifacts parse successfully.
- `sam validate --lint --template-file template.yaml` passes.
- The generator’s own model loading/semantic preflight completed without diagnostics. A separate
  repository EVL CLI run was also attempted; it exposes a pre-existing structural-loader
  incompatibility for richer cross-package relationship resources (the same loader fails on the
  repository’s richer Smart Makerspace PSM). That EVL failure is recorded, not masked, and does not
  affect the successful generator load or SAM artifact validation.
- Linux Go builds for all three handlers pass.
- The generated PowerShell scripts parse; `scripts/build.ps1` runs `go mod tidy`, all generated Go
  tests, and all three Linux bootstrap builds successfully.
- `go mod tidy; go test ./...` passes for contract, end-to-end, event, integration, unit, and
  workflow test packages.
- SAM local build on Windows remains blocked by SAM's `CustomMakeBuilder` because `make` is not
  installed. The generated PowerShell build provides a working Windows-native build path, and
  deployment of the resulting `provided.al2023` binaries was successful.

Floci checks were run at `http://127.0.0.1:4566` in `us-east-1` using stack `orderpulse-floci`:

- SAM deployment reached `UPDATE_COMPLETE`, and the modeled resources were enumerated through the
  AWS APIs.
- Direct Lambda invocation returned `201`; DynamoDB contained the order and the SQS event-source
  mapping changed it to `PROCESSED`.
- Direct `GetOrder` returned `200` for the created record and `404` for a missing record.
- Invalid create input returned `400`.
- A mixed process batch returned exactly one `batchItemFailures` entry for the malformed message,
  while the valid record was updated.
- HTTP API invocation through Floci returned `201` for POST, `200` for GET, and `404` for a missing
  order.
- An SNS publish delivered through the modeled SNS subscription and SQS event-source mapping, and
  the order reached `PROCESSED`.
- `PutEvents` on `orderpulse-bus` succeeded; `list-rules --event-bus-name orderpulse-bus` showed
  the generated rule with the custom-bus ARN.
- SQS redrive attributes, SNS subscription, DynamoDB status, versioned S3 bucket, public-access
  block configuration, and Lambda log groups were inspected.
- The generated native workflow probe created/updated the modeled state machine, started an
  execution, and observed `SUCCEEDED` in Floci.

Two CloudFormation-emulator limitations remain and are recorded as limitations rather than hidden:

- Floci does not materialize the SAM Step Functions resource in `list-state-machines`. The generated
  native workflow probe works around this emulator-specific gap by deploying the same generated ASL
  through the Step Functions API and verifying a successful execution. The SAM template remains
  valid; native materialization is still needed for an emulator-only SAM parity test.
- Floci does not retain the generated S3 public-access-block configuration during CloudFormation
  deployment, although the generated template contains all four block flags. The E2E test applies
  the same configuration through `put-public-access-block` and verifies it through the S3 API. This
  is an emulator capability gap, not a missing generator branch.

Floci could not pull the public `provided.al2023` image because the Docker daemon's registry TLS
connection timed out. To exercise the real generated binaries, a local test-only image based on the
already-cached `python:3.13-alpine` image was tagged as
`public.ecr.aws/lambda/provided:al2023`; after restarting Floci to clear its failed image cache,
the same deployed functions executed successfully. This workaround is test infrastructure only
and is not part of the generated application. The remaining manual step is intentional: business
logic is protected-region content that the PSM does not infer. The completed handlers are therefore
tested as manual completion of generated extension points, while infrastructure defects are fixed
in the model/generator and regenerated.
