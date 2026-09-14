# AWS PSM Code Generator Coverage

This matrix tracks the AWS PSM model-to-text generator in `mde/generation/awspsm-to-artifacts`.
The coverage surface is the EGX rule set plus the EGL/EOL helper logic reached by those rules.

PSM chatbot generation is currently out of scope. If a future assistant path produces PSM output,
the project boundary still requires structural Ecore/EMF gating only. These explicit generator
tests do not invoke EVL semantic validation.

## Executable Coverage

The primary tests are in `packages/java/mde-m2t-runner/src/test/java/io/mehdieidi/modriss/mde/generation`.

| Test                                                                                       | Coverage role                                                                                                                                                                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AwsPsmArtifactGenerationSyntaxTest.everyEglTemplateParsesWithoutErrors`                   | Parses every EGL template.                                                                                                                                                                                                                                                                                                                   |
| `AwsPsmArtifactGenerationSyntaxTest.egxCoordinatorReferencesExistingImportsAndTemplates`   | Ensures EGX imports and templates resolve.                                                                                                                                                                                                                                                                                                   |
| `EpsilonEgxGeneratorTest.generatesArtifactsForRepresentativeAwsPsmModel`                   | Executes every traceable EGX artifact rule against a synthetic AWS PSM model and checks source coverage plus artifact validity.                                                                                                                                                                                                              |
| `EpsilonEgxGeneratorTest.generatesSamForBroadAwsPsmResourceSurface`                        | Executes broad AWS resource, ASL, and structured-document variants and verifies rendered SAM/ASL/document evidence.                                                                                                                                                                                                                          |
| `EpsilonEgxGeneratorTest.honorsEgxGuardsForMinimalAwsPsmModel`                             | Exercises negative/guard behavior for empty stacks, default environment generation, API/ASL/Lambda guards, and trace exclusions.                                                                                                                                                                                                             |
| `EpsilonEgxGeneratorTest.generatedGoTestsRunAgainstSelectedAwsEmulatorWhenDockerAvailable` | Runs generated Go tests with the selected Floci/LocalStack endpoint when Go, Docker, and the provider image are available.                                                                                                                                                                                                                   |
| `EpsilonEgxGeneratorTest.deploysGeneratedAwsArtifactsToSelectedEmulatorAndExecutesThem`    | Generates from `src/test/resources/awspsm/e2e/localstack-serverless-system.awspsm.xmi`, packages the generated Lambda/ASL artifacts, deploys supported AWS artifacts to Floci or LocalStack, invokes Lambda, executes service APIs, starts the generated Step Functions workflow, and runs generated Go tests against the deployed function. |
| `EpsilonEgxGeneratorTest.generatesCompleteUniqueTraceForRepositoryPsmSample`               | Regression-runs the repository PSM sample and verifies trace completeness and SAM invariants.                                                                                                                                                                                                                                                |
| `EpsilonEgxGeneratorTest.preservesProtectedRegionsWhenRegeneratingExistingArtifacts`       | Verifies merge/protected-region preservation.                                                                                                                                                                                                                                                                                                |
| `EpsilonEgxGeneratorTest.generatorCoverageMatrixListsEveryEgxRule`                         | Fails when an EGX artifact rule is missing from this matrix.                                                                                                                                                                                                                                                                                 |

## Rule Matrix

| EGX rule                      | Generator rule id                             | Template                                  | Artifact kind           | Covered by                | Coverage evidence                                                                                                                                                                                                                            |
| ----------------------------- | --------------------------------------------- | ----------------------------------------- | ----------------------- | ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ProjectScaffold`             | `AWSPSM2ART_ProjectScaffold`                  | `docs/project-scaffold.egl`               | `PROJECT_SCAFFOLD`      | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `PackageManifest`             | `AWSPSM2ART_PackageManifest`                  | `infrastructure/go-mod.egl`               | `PACKAGE_MANIFEST`      | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `Gitignore`                   | `AWSPSM2ART_Gitignore`                        | `infrastructure/gitignore.egl`            | `GITIGNORE`             | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `Makefile`                    | `AWSPSM2ART_Makefile`                         | `scripts/makefile.egl`                    | `MAKEFILE`              | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `StackToSamTemplate`          | `AWSPSM2ART_Stack_To_SamTemplate`             | `infrastructure/sam-template.egl`         | `SAM_TEMPLATE`          | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `SamConfig`                   | `AWSPSM2ART_SamConfig`                        | `infrastructure/samconfig.egl`            | `SAM_CONFIG`            | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `StageEnvironment`            | `AWSPSM2ART_Stage_Environment`                | `infrastructure/env-json.egl`             | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `LocalEnvironmentFile`        | `AWSPSM2ART_Default_Local_Environment`        | `infrastructure/default-env-json.egl`     | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `DevEnvironmentFile`          | `AWSPSM2ART_Default_Dev_Environment`          | `infrastructure/default-env-json.egl`     | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `TestEnvironmentFile`         | `AWSPSM2ART_Default_Test_Environment`         | `infrastructure/default-env-json.egl`     | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `StagingEnvironmentFile`      | `AWSPSM2ART_Default_Staging_Environment`      | `infrastructure/default-env-json.egl`     | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `ProdExampleEnvironmentFile`  | `AWSPSM2ART_Default_Prod_Example_Environment` | `infrastructure/default-env-json.egl`     | `ENVIRONMENT_CONFIG`    | `EpsilonEgxGeneratorTest` | Representative fixture executes the rule; artifact trace records the rule id; generated artifacts are checked for model coverage and syntax/working-state invariants.                                                                        |
| `ApiToOpenApi`                | `AWSPSM2ART_Api_To_OpenApi`                   | `contracts/openapi.egl`                   | `OPENAPI`               | `EpsilonEgxGeneratorTest` | Representative fixture includes an HTTP API route, request/response models, AWS_IAM auth, and Lambda integration; generated OpenAPI is checked for modeled path, method, operation, schemas, security, and integration URI.                  |
| `WorkflowToAsl`               | `AWSPSM2ART_Workflow_To_Asl`                  | `contracts/asl.egl`                       | `ASL_JSON`              | `EpsilonEgxGeneratorTest` | Representative fixture includes a Step Functions state machine and ASL document; generated ASL JSON is parsed and checked for start state and terminal-state semantics.                                                                      |
| `LambdaToHandler`             | `AWSPSM2ART_Lambda_To_Handler`                | `lambda/go-handler.egl`                   | `LAMBDA_HANDLER`        | `EpsilonEgxGeneratorTest` | Representative fixture includes a Lambda function with code config; generated handler is checked for Go package, Lambda start call, shared import, generated error mapping, and LocalStack custom-runtime startup behavior.                  |
| `LambdaToLocalSchema`         | `AWSPSM2ART_Contract_To_JsonSchema`           | `contracts/json-schema.egl`               | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Representative fixture includes API invocation; generated local schema is parsed as JSON.                                                                                                                                                    |
| `LambdaToUnitTest`            | `AWSPSM2ART_Lambda_To_UnitTest`               | `tests/unit-test-go.egl`                  | `UNIT_TEST`             | `EpsilonEgxGeneratorTest` | Representative fixture emits generated Go unit tests; Go tests run when the Go toolchain exists.                                                                                                                                             |
| `LambdaToIntegrationTest`     | `AWSPSM2ART_Lambda_To_IntegrationTest`        | `tests/integration-test.egl`              | `INTEGRATION_TEST`      | `EpsilonEgxGeneratorTest` | Representative fixture emits generated Go integration tests; Go tests run when the Go toolchain exists, invoke the deployed LocalStack Lambda when endpoint/function env vars are present, and skip external AWS checks without environment. |
| `WorkflowToTest`              | `AWSPSM2ART_Workflow_To_Test`                 | `tests/workflow-test.egl`                 | `WORKFLOW_TEST`         | `EpsilonEgxGeneratorTest` | Representative fixture emits workflow tests for the modeled state machine; Go tests run when the Go toolchain exists.                                                                                                                        |
| `EventBridgeRuleToFixture`    | `AWSPSM2ART_EventBridgeRule_To_Fixture`       | `contracts/sample-event.egl`              | `EVENT_FIXTURE`         | `EpsilonEgxGeneratorTest` | Representative fixture includes an EventBridge rule; generated fixture JSON is parsed and checked for source stable id and EventBridge source.                                                                                               |
| `SqsQueueToFixture`           | `AWSPSM2ART_SqsQueue_To_Fixture`              | `contracts/sample-event.egl`              | `EVENT_FIXTURE`         | `EpsilonEgxGeneratorTest` | Representative fixture includes an SQS queue; generated fixture JSON is parsed and checked for source stable id and SQS source.                                                                                                              |
| `SnsTopicToFixture`           | `AWSPSM2ART_SnsTopic_To_Fixture`              | `contracts/sample-event.egl`              | `EVENT_FIXTURE`         | `EpsilonEgxGeneratorTest` | Representative fixture includes an SNS topic; generated fixture JSON is parsed and checked for source stable id and SNS source.                                                                                                              |
| `ApiSchemaCatalog`            | `AWSPSM2ART_Api_Schema_Catalog`               | `contracts/catalog-schema.egl`            | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Catalog JSON is generated and parsed.                                                                                                                                                                                                        |
| `EventSchemaCatalog`          | `AWSPSM2ART_Event_Schema_Catalog`             | `contracts/catalog-schema.egl`            | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Catalog JSON is generated and parsed.                                                                                                                                                                                                        |
| `MessageSchemaCatalog`        | `AWSPSM2ART_Message_Schema_Catalog`           | `contracts/catalog-schema.egl`            | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Catalog JSON is generated and parsed.                                                                                                                                                                                                        |
| `ErrorSchemaCatalog`          | `AWSPSM2ART_Error_Schema_Catalog`             | `contracts/catalog-schema.egl`            | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Catalog JSON is generated and parsed.                                                                                                                                                                                                        |
| `DataSchemaCatalog`           | `AWSPSM2ART_Data_Schema_Catalog`              | `contracts/catalog-schema.egl`            | `JSON_SCHEMA`           | `EpsilonEgxGeneratorTest` | Catalog JSON is generated and parsed.                                                                                                                                                                                                        |
| `StructuredDocumentArtifact`  | `AWSPSM2ART_StructuredDocument`               | `docs/structured-document.egl`            | `MARKDOWN`              | `EpsilonEgxGeneratorTest` | Representative fixture includes a Markdown structured document; generated document path and content are checked.                                                                                                                             |
| `TestFixturesReadme`          | `AWSPSM2ART_Test_Fixtures_Readme`             | `tests/fixtures-readme.egl`               | `TEST_FIXTURE_DOC`      | `EpsilonEgxGeneratorTest` | Generated test fixture README exists in the required project tree.                                                                                                                                                                           |
| `SharedRuntimeLogger`         | `AWSPSM2ART_SharedRuntimeLogger`              | `lambda/shared-logger.egl`                | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeTracer`         | `AWSPSM2ART_SharedRuntimeTracer`              | `lambda/shared-tracer.egl`                | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeMetrics`        | `AWSPSM2ART_SharedRuntimeMetrics`             | `lambda/shared-metrics.egl`               | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeErrors`         | `AWSPSM2ART_SharedRuntimeErrors`              | `lambda/shared-errors.egl`                | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeValidation`     | `AWSPSM2ART_SharedRuntimeValidation`          | `lambda/shared-validation.egl`            | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeConfig`         | `AWSPSM2ART_SharedRuntimeConfig`              | `lambda/shared-config.egl`                | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeIdempotency`    | `AWSPSM2ART_SharedRuntimeIdempotency`         | `lambda/shared-idempotency.egl`           | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeEventPublisher` | `AWSPSM2ART_SharedRuntimeEventPublisher`      | `lambda/shared-event-publisher.egl`       | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `SharedRuntimeDataAccess`     | `AWSPSM2ART_SharedRuntimeDataAccess`          | `lambda/shared-data-access.egl`           | `SHARED_RUNTIME`        | `EpsilonEgxGeneratorTest` | Shared Go runtime source is generated and included in Go-only artifact checks.                                                                                                                                                               |
| `ContractTests`               | `AWSPSM2ART_ContractTests`                    | `tests/contract-test.egl`                 | `CONTRACT_TEST`         | `EpsilonEgxGeneratorTest` | Generated contract test exists and runs with `go test ./...` when Go is installed.                                                                                                                                                           |
| `EventTests`                  | `AWSPSM2ART_EventTests`                       | `tests/event-test.egl`                    | `EVENT_TEST`            | `EpsilonEgxGeneratorTest` | Generated event test exists and runs with `go test ./...` when Go is installed.                                                                                                                                                              |
| `SecurityTests`               | `AWSPSM2ART_SecurityTests`                    | `tests/security-test.egl`                 | `SECURITY_TEST`         | `EpsilonEgxGeneratorTest` | Generated security test exists and runs with `go test ./...` when Go is installed.                                                                                                                                                           |
| `E2eTests`                    | `AWSPSM2ART_E2eTests`                         | `tests/e2e-test.egl`                      | `E2E_TEST`              | `EpsilonEgxGeneratorTest` | Generated e2e test exists and runs with `go test ./...` when Go is installed.                                                                                                                                                                |
| `ValidateModelsScript`        | `AWSPSM2ART_ValidateModelsScript`             | `scripts/validate-models.egl`             | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and checks generated reports/manual actions.                                                                                                                                                      |
| `ValidateTemplateScript`      | `AWSPSM2ART_ValidateTemplateScript`           | `scripts/validate-template.egl`           | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and is wired into CI validation.                                                                                                                                                                  |
| `ValidateContractsScript`     | `AWSPSM2ART_ValidateContractsScript`          | `scripts/validate-contracts.egl`          | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, checks OpenAPI shape, and runs generated contract tests.                                                                                                                                          |
| `BuildScript`                 | `AWSPSM2ART_BuildScript`                      | `scripts/build.egl`                       | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and includes Go build/test commands.                                                                                                                                                              |
| `TestScript`                  | `AWSPSM2ART_TestScript`                       | `scripts/test.egl`                        | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and invokes build in install-only mode.                                                                                                                                                           |
| `DeployScript`                | `AWSPSM2ART_DeployScript`                     | `scripts/deploy.egl`                      | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and enforces SAM/model validation before deploy.                                                                                                                                                  |
| `LocalInvokeScript`           | `AWSPSM2ART_LocalInvokeScript`                | `scripts/local-invoke.egl`                | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and performs SAM build before local invocation.                                                                                                                                                   |
| `LocalStartApiScript`         | `AWSPSM2ART_LocalStartApiScript`              | `scripts/local-start-api.egl`             | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and is present in the generated project tree.                                                                                                                                                     |
| `PackageScript`               | `AWSPSM2ART_PackageScript`                    | `scripts/package.egl`                     | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exists, uses strict shell settings, and enforces SAM/model validation before packaging.                                                                                                                                               |
| `EmulatorEnvScript`           | `AWSPSM2ART_EmulatorEnvScript`                | `scripts/emulator-env.egl`                | `SCRIPT`                | `EpsilonEgxGeneratorTest` | Script exports the selected Floci/LocalStack endpoint, region, local credentials, and metadata-service safeguards for generated tests.                                                                                                       |
| `CiValidate`                  | `AWSPSM2ART_CiValidate`                       | `cicd/github-actions-validate.egl`        | `CI_WORKFLOW`           | `EpsilonEgxGeneratorTest` | Workflow exists and is checked for Go-only setup and required validation gates.                                                                                                                                                              |
| `CiDeployDev`                 | `AWSPSM2ART_CiDeployDev`                      | `cicd/github-actions-deploy-dev.egl`      | `CI_WORKFLOW`           | `EpsilonEgxGeneratorTest` | Workflow exists and participates in trace coverage.                                                                                                                                                                                          |
| `CiDeployProd`                | `AWSPSM2ART_CiDeployProd`                     | `cicd/github-actions-deploy-prod.egl`     | `CI_WORKFLOW`           | `EpsilonEgxGeneratorTest` | Workflow exists and participates in trace coverage.                                                                                                                                                                                          |
| `Readme`                      | `AWSPSM2ART_Readme`                           | `docs/readme.egl`                         | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | README exists and participates in trace coverage/protected-region checks.                                                                                                                                                                    |
| `ArchitectureDoc`             | `AWSPSM2ART_ArchitectureDoc`                  | `docs/architecture.egl`                   | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | Architecture doc exists and participates in trace coverage/protected-region checks.                                                                                                                                                          |
| `ModelSummaryDoc`             | `AWSPSM2ART_ModelSummaryDoc`                  | `docs/model-summary.egl`                  | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | Model summary exists and participates in trace coverage.                                                                                                                                                                                     |
| `SecurityDoc`                 | `AWSPSM2ART_SecurityDoc`                      | `docs/security.egl`                       | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | Security doc exists and participates in trace coverage/protected-region checks.                                                                                                                                                              |
| `OperationsDoc`               | `AWSPSM2ART_OperationsDoc`                    | `docs/operations.egl`                     | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | Operations doc exists and participates in trace coverage/protected-region checks.                                                                                                                                                            |
| `DeploymentDoc`               | `AWSPSM2ART_DeploymentDoc`                    | `docs/deployment.egl`                     | `DOCUMENTATION`         | `EpsilonEgxGeneratorTest` | Deployment doc exists and participates in trace coverage/protected-region checks.                                                                                                                                                            |
| `IncidentRunbook`             | `AWSPSM2ART_IncidentRunbook`                  | `docs/runbook-incident-response.egl`      | `RUNBOOK`               | `EpsilonEgxGeneratorTest` | Incident runbook exists and participates in trace coverage/protected-region checks.                                                                                                                                                          |
| `RollbackRunbook`             | `AWSPSM2ART_RollbackRunbook`                  | `docs/runbook-rollback.egl`               | `RUNBOOK`               | `EpsilonEgxGeneratorTest` | Rollback runbook exists and participates in trace coverage/protected-region checks.                                                                                                                                                          |
| `TraceabilityDoc`             | `AWSPSM2ART_TraceabilityDoc`                  | `docs/traceability.egl`                   | `TRACEABILITY_DOC`      | `EpsilonEgxGeneratorTest` | Traceability doc exists and participates in trace coverage.                                                                                                                                                                                  |
| `ProtectedRegionsJson`        | `AWSPSM2ART_ProtectedRegionsJson`             | `docs/protected-regions-json.egl`         | `TRACE_REPORT`          | `EpsilonEgxGeneratorTest` | Trace JSON is generated and parsed.                                                                                                                                                                                                          |
| `ModelTraceJson`              | `AWSPSM2ART_ModelTraceJson`                   | `docs/model-trace-json.egl`               | `TRACE_REPORT`          | `EpsilonEgxGeneratorTest` | Trace JSON is generated and parsed.                                                                                                                                                                                                          |
| `IamRationaleReport`          | `AWSPSM2ART_IamRationaleReport`               | `infrastructure/iam-policy-rationale.egl` | `SECURITY_REPORT`       | `EpsilonEgxGeneratorTest` | IAM rationale report exists and participates in trace coverage.                                                                                                                                                                              |
| `SecurityReviewReport`        | `AWSPSM2ART_SecurityReviewReport`             | `docs/security-review.egl`                | `SECURITY_REPORT`       | `EpsilonEgxGeneratorTest` | Security review report exists and participates in trace coverage.                                                                                                                                                                            |
| `ManualActionsReport`         | `AWSPSM2ART_ManualActionsReport`              | `docs/manual-actions.egl`                 | `MANUAL_ACTIONS_REPORT` | `EpsilonEgxGeneratorTest` | Manual actions report exists and participates in trace coverage.                                                                                                                                                                             |
| `GenerationReport`            | `AWSPSM2ART_GenerationReport`                 | `docs/generation-report.egl`              | `GENERATION_REPORT`     | `EpsilonEgxGeneratorTest` | Generation report exists and is checked for manual actions and export gate summaries.                                                                                                                                                        |
| `ArtifactTraceJson`           | `AWSPSM2ART_ArtifactTraceJson`                | `docs/artifact-trace-json.egl`            | `TRACE_REPORT`          | `EpsilonEgxGeneratorTest` | Artifact trace JSON is generated, finalized, parsed, and checked for one row per generated file.                                                                                                                                             |

## EGL Template Inventory

This inventory is synchronized by `AwsPsmArtifactGenerationSyntaxTest.coverageReportListsEveryEglTemplate`.

- `cicd/github-actions-deploy-dev.egl`
- `cicd/github-actions-deploy-prod.egl`
- `cicd/github-actions-validate.egl`
- `contracts/asl.egl`
- `contracts/catalog-schema.egl`
- `contracts/json-schema.egl`
- `contracts/openapi.egl`
- `contracts/sample-event.egl`
- `docs/architecture.egl`
- `docs/artifact-trace-json.egl`
- `docs/deployment.egl`
- `docs/generation-report.egl`
- `docs/manual-actions.egl`
- `docs/model-summary.egl`
- `docs/model-trace-json.egl`
- `docs/operations.egl`
- `docs/project-scaffold.egl`
- `docs/protected-regions-json.egl`
- `docs/readme.egl`
- `docs/runbook-incident-response.egl`
- `docs/runbook-rollback.egl`
- `docs/security-review.egl`
- `docs/security.egl`
- `docs/structured-document.egl`
- `docs/traceability.egl`
- `infrastructure/default-env-json.egl`
- `infrastructure/env-json.egl`
- `infrastructure/gitignore.egl`
- `infrastructure/go-mod.egl`
- `infrastructure/iam-policy-rationale.egl`
- `infrastructure/sam-template.egl`
- `infrastructure/samconfig.egl`
- `lambda/go-handler.egl`
- `lambda/shared-config.egl`
- `lambda/shared-data-access.egl`
- `lambda/shared-errors.egl`
- `lambda/shared-event-publisher.egl`
- `lambda/shared-idempotency.egl`
- `lambda/shared-logger.egl`
- `lambda/shared-metrics.egl`
- `lambda/shared-tracer.egl`
- `lambda/shared-validation.egl`
- `scripts/build.egl`
- `scripts/deploy.egl`
- `scripts/emulator-env.egl`
- `scripts/local-invoke.egl`
- `scripts/local-start-api.egl`
- `scripts/makefile.egl`
- `scripts/package.egl`
- `scripts/test.egl`
- `scripts/validate-contracts.egl`
- `scripts/validate-models.egl`
- `scripts/validate-template.egl`
- `tests/contract-test.egl`
- `tests/e2e-test.egl`
- `tests/event-test.egl`
- `tests/fixtures-readme.egl`
- `tests/integration-test.egl`
- `tests/security-test.egl`
- `tests/unit-test-go.egl`
- `tests/workflow-test.egl`

## EOL Helper Operation Inventory

This inventory is synchronized by `AwsPsmArtifactGenerationSyntaxTest.coverageReportListsEveryEolOperation`.

### `cfn.eol`

- `cfn.eol:AWSPSMCORE!AwsResource.nestedAwsResources`
- `cfn.eol:AWSPSMCORE!SamStack.flattenedResources`
- `cfn.eol:AWSPSMCORE!SamStack.orderedResources`
- `cfn.eol:AWSPSMCORE!AwsResource.omitFromCfnTemplate`
- `cfn.eol:AWSPSMAPI!RestApiStage.omitFromCfnTemplate`
- `cfn.eol:AWSPSMAPI!ApiGatewayDeployment.omitFromCfnTemplate`
- `cfn.eol:AWSPSMAPI!ApiGatewayRoute.owningApi`
- `cfn.eol:AWSPSMAPI!ApiGatewayRoute.omitFromCfnTemplate`
- `cfn.eol:AWSPSMAPI!ApiGatewayRoute.openApiDefinitionOwnsRoute`
- `cfn.eol:AWSPSMAPI!HttpApiRoute.omitFromCfnTemplate`
- `cfn.eol:AWSPSMAPI!RestApiRoute.omitFromCfnTemplate` (keeps REST methods concrete)
- `cfn.eol:AWSPSMAPI!ApiGatewayIntegration.omitFromCfnTemplate`
- `cfn.eol:AWSPSMCORE!AwsNativeResource.omitFromCfnTemplate`
- `cfn.eol:AWSPSMEVENTS!EventBridgeSchedule.omitFromCfnTemplate`
- `cfn.eol:AWSPSMMESSAGING!SnsSubscription.omitFromCfnTemplate`
- `cfn.eol:AWSPSMCORE!AwsResource.emitCfnResourceYaml`
- `cfn.eol:AWSPSMCORE!AwsResource.emitCommonCfnAttributesYaml`
- `cfn.eol:AWSPSMCORE!AwsResource.cfnTypeText`
- `cfn.eol:AWSPSMCORE!AwsNativeResource.cfnTypeText`
- `cfn.eol:AWSPSMCORE!AwsResource.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCORE!AwsNativeResource.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!AwsLambdaFunction.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!AwsLambdaFunction.emitCfnPropertiesYaml`
- `cfn.eol:Any.defaultRuntimeIdentifier`
- `cfn.eol:AWSPSMCOMPUTE!LambdaEventSourceMapping.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!SqsLambdaEventSourceMapping.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!DynamoDbStreamLambdaEventSourceMapping.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!GenericLambdaEventSourceMapping.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaEventSourceMapping.emitLambdaEventSourceMappingPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!SqsLambdaEventSourceMapping.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!DynamoDbStreamLambdaEventSourceMapping.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!GenericLambdaEventSourceMapping.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaEventSourceMapping.emitEventSourceArnYaml`
- `cfn.eol:AWSPSMCOMPUTE!SqsLambdaEventSourceMapping.emitEventSourceArnYaml`
- `cfn.eol:AWSPSMCOMPUTE!DynamoDbStreamLambdaEventSourceMapping.emitEventSourceArnYaml`
- `cfn.eol:AWSPSMCOMPUTE!GenericLambdaEventSourceMapping.emitEventSourceArnYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaPermission.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaPermission.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaLayerVersion.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaLayerVersion.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaLayerPermission.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaLayerPermission.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaFunctionUrl.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaFunctionUrl.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaVersion.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaVersion.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaAlias.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaAlias.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!LambdaEventInvokeConfig.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!LambdaEventInvokeConfig.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!IamRole.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!IamRole.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!IamManagedPolicy.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!IamManagedPolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!IamPolicy.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!IamPolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!KmsKey.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!KmsKey.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!KmsAlias.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!KmsAlias.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!SecretsManagerSecret.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!SecretsManagerSecret.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!SecretRotationSchedule.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!SecretRotationSchedule.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!SecretsManagerResourcePolicy.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!SecretsManagerResourcePolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSECURITY!SsmParameter.cfnTypeText`
- `cfn.eol:AWSPSMSECURITY!SsmParameter.emitCfnPropertiesYaml`
- `cfn.eol:Any.ssmParameterTypeText`
- `cfn.eol:AWSPSMMESSAGING!SqsQueue.cfnTypeText`
- `cfn.eol:AWSPSMMESSAGING!SqsQueue.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMMESSAGING!SqsQueuePolicy.cfnTypeText`
- `cfn.eol:AWSPSMMESSAGING!SqsQueuePolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMMESSAGING!SnsTopic.cfnTypeText`
- `cfn.eol:AWSPSMMESSAGING!SnsTopic.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMMESSAGING!SnsSubscription.cfnTypeText`
- `cfn.eol:AWSPSMMESSAGING!SnsSubscription.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMMESSAGING!SnsTopicPolicy.cfnTypeText`
- `cfn.eol:AWSPSMMESSAGING!SnsTopicPolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeBus.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeBus.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeBusPolicy.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeBusPolicy.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeRule.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeRule.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventPattern.emitEventPatternYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeTarget.emitEventBridgeTargetYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeArchive.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeArchive.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeSchedule.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeSchedule.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgePipe.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgePipe.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeConnection.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeConnection.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMEVENTS!EventBridgeApiDestination.cfnTypeText`
- `cfn.eol:AWSPSMEVENTS!EventBridgeApiDestination.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSTORAGE!DynamoDbTable.cfnTypeText`
- `cfn.eol:AWSPSMSTORAGE!DynamoDbTable.emitCfnPropertiesYaml`
- `cfn.eol:Collection.emitDdbIndexesYaml`
- `cfn.eol:AWSPSMSTORAGE!S3Bucket.cfnTypeText`
- `cfn.eol:AWSPSMSTORAGE!S3Bucket.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMSTORAGE!S3BucketPolicy.cfnTypeText`
- `cfn.eol:AWSPSMSTORAGE!S3BucketPolicy.emitCfnPropertiesYaml`
- `cfn.eol:Collection.emitS3NotificationRulesYaml`
- `cfn.eol:AWSPSMSTORAGE!S3NotificationRule.emitSingleS3NotificationYaml`
- `cfn.eol:AWSPSMAPI!HttpApi.cfnTypeText`
- `cfn.eol:AWSPSMAPI!RestApi.cfnTypeText`
- `cfn.eol:AWSPSMAPI!WebSocketApi.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayApi.emitApiCommonPropertiesYaml`
- `cfn.eol:AWSPSMAPI!HttpApi.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!RestApi.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!WebSocketApi.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayIntegration.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayIntegration.emitCfnPropertiesYaml`
- `cfn.eol:apiGatewayLambdaIntegrationUri`
- `cfn.eol:AWSPSMAPI!HttpApiRoute.cfnTypeText`
- `cfn.eol:AWSPSMAPI!WebSocketRoute.cfnTypeText`
- `cfn.eol:AWSPSMAPI!RestApiRoute.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayRoute.emitApiGatewayV2RoutePropertiesYaml`
- `cfn.eol:AWSPSMAPI!HttpApiRoute.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!WebSocketRoute.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!RestApiRoute.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayStage.cfnTypeText`
- `cfn.eol:AWSPSMAPI!HttpApiStage.cfnTypeText`
- `cfn.eol:AWSPSMAPI!RestApiStage.cfnTypeText`
- `cfn.eol:AWSPSMAPI!WebSocketStage.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayStage.emitApiGatewayStagePropertiesYaml`
- `cfn.eol:AWSPSMCORE!AwsResource.refForResourceInSameStack`
- `cfn.eol:AWSPSMEVENTS!EventBridgeRule.deployableEventBridgeTargets`
- `cfn.eol:AWSPSMAPI!HttpApiStage.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!RestApiStage.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!WebSocketStage.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayAuthorizer.cfnTypeText`
- `cfn.eol:AWSPSMAPI!JwtAuthorizer.cfnTypeText`
- `cfn.eol:AWSPSMAPI!CognitoAuthorizer.cfnTypeText`
- `cfn.eol:AWSPSMAPI!LambdaAuthorizer.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayAuthorizer.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayDomainName.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayDomainName.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayBasePathMapping.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayBasePathMapping.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayApiKey.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayApiKey.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayUsagePlan.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayUsagePlan.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayUsagePlanKey.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayUsagePlanKey.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!ApiGatewayDeployment.cfnTypeText`
- `cfn.eol:AWSPSMAPI!ApiGatewayDeployment.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMAPI!WafWebAclAssociation.cfnTypeText`
- `cfn.eol:AWSPSMAPI!WafWebAclAssociation.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPool.cfnTypeText`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPool.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolClient.cfnTypeText`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolClient.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolGroup.cfnTypeText`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolGroup.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolDomain.cfnTypeText`
- `cfn.eol:AWSPSMIDENTITY!CognitoUserPoolDomain.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMIDENTITY!CognitoIdentityPool.cfnTypeText`
- `cfn.eol:AWSPSMIDENTITY!CognitoIdentityPool.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMNETWORKING!Vpc.cfnTypeText`
- `cfn.eol:AWSPSMNETWORKING!Vpc.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMNETWORKING!Subnet.cfnTypeText`
- `cfn.eol:AWSPSMNETWORKING!Subnet.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMNETWORKING!VpcEndpoint.cfnTypeText`
- `cfn.eol:AWSPSMNETWORKING!VpcEndpoint.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMNETWORKING!SecurityGroup.cfnTypeText`
- `cfn.eol:AWSPSMNETWORKING!SecurityGroup.emitCfnPropertiesYaml`
- `cfn.eol:Collection.emitSecurityGroupRulesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchLogGroup.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchLogGroup.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchMetricFilter.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchMetricFilter.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchLogSubscriptionFilter.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchLogSubscriptionFilter.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchAlarm.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchAlarm.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchCompositeAlarm.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchCompositeAlarm.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchDashboard.cfnTypeText`
- `cfn.eol:AWSPSMOBSERVABILITY!CloudWatchDashboard.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMWORKFLOW!StepFunctionStateMachine.cfnTypeText`
- `cfn.eol:AWSPSMWORKFLOW!StepFunctionStateMachine.emitCfnPropertiesYaml`
- `cfn.eol:AWSPSMCOMPUTE!CodeSigningConfig.cfnTypeText`
- `cfn.eol:AWSPSMCOMPUTE!CodeSigningConfig.emitCfnPropertiesYaml`

### `contracts.eol`

- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.routePath`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.routeMethod`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.operationIdText`
- `contracts.eol:AWSPSMAPI!ApiGatewayApi.emitOpenApiDocument`
- `contracts.eol:AWSPSMAPI!ApiGatewayApi.openApiSchemasYaml`
- `contracts.eol:AWSPSMAPI!ApiGatewayApi.collectOpenApiSchemaModels`
- `contracts.eol:AWSPSMAPI!ApiGatewayApi.openApiSecuritySchemesYaml`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.openApiOperation`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.sortedOpenApiResponseModels`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.openApiResponseDescription`
- `contracts.eol:AWSPSMAPI!ApiGatewayRoute.openApiSecuritySchemeName`
- `contracts.eol:AWSPSMAPI!ApiGatewayResponseModel.openapiSchemaName`
- `contracts.eol:AWSPSMAPI!ApiGatewayRequestModel.openapiModelName`
- `contracts.eol:AWSPSMAPI!ApiGatewayResponseModel.openapiModelName`
- `contracts.eol:AWSPSMWORKFLOW!AslDocument.emitAslDocumentJson`
- `contracts.eol:AWSPSMWORKFLOW!AslState.emitAslStateJson`
- `contracts.eol:AWSPSMWORKFLOW!AslState.aslStateTypeName`
- `contracts.eol:AWSPSMWORKFLOW!AslBranch.emitAslBranchJson`
- `contracts.eol:AWSPSMWORKFLOW!AslRetryRule.emitAslRetryJson`
- `contracts.eol:AWSPSMWORKFLOW!AslCatchRule.emitAslCatchJson`
- `contracts.eol:AWSPSMWORKFLOW!AslChoiceRule.emitAslChoiceJson`
- `contracts.eol:Collection.emitAslStringArray`
- `contracts.eol:KERNEL!TraceableElement.schemaTitle`
- `contracts.eol:indentJsonBlock`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.localInputSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.wrapLocalSchemaDocument`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.hasApiGatewayInvocation`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.apiGatewayProxyEventSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.sqsEventSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.dynamoStreamEventSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.eventBridgeEventSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.genericJsonEventSchemaJson`
- `contracts.eol:AWSPSMCOMPUTE!AwsLambdaFunction.requiresIdempotency`

### `iam.eol`

- `iam.eol:AWSPSMSECURITY!IamPolicyDocument.emitIamPolicyDocumentYaml`
- `iam.eol:AWSPSMSECURITY!IamPolicyDocument.uniqueIamStatements`
- `iam.eol:AWSPSMSECURITY!IamStatement.emitIamStatementYaml`
- `iam.eol:Collection.emitIamListYaml`
- `iam.eol:Any.normalizeIamResourceReference`
- `iam.eol:AWSPSMSECURITY!IamPolicyDocument.emitIamPolicyDocumentJson`
- `iam.eol:AWSPSMSECURITY!IamStatement.emitIamStatementJson`
- `iam.eol:AWSPSMSECURITY!IamStatement.emitIamPrincipalsJson`
- `iam.eol:Collection.emitJsonStringArrayValue`
- `iam.eol:Collection.emitJsonStringArray`

### `naming.eol`

- `naming.eol:Any.isPresent`
- `naming.eol:Any.textOrEmpty`
- `naming.eol:Any.orElseText`
- `naming.eol:Any.modelFeatureValue`
- `naming.eol:firstText`
- `naming.eol:String.normalizedToken`
- `naming.eol:Any.toSlug`
- `naming.eol:Any.toSnake`
- `naming.eol:Any.toCfnLogicalId`
- `naming.eol:Any.toCamel`
- `naming.eol:Any.safeFileStem`
- `naming.eol:KERNEL!TraceableElement.stableKey`
- `naming.eol:AWSPSMCORE!AwsResource.stableKey`
- `naming.eol:AWSPSMCORE!AwsResource.cfnLogicalId`
- `naming.eol:AWSPSMCOMPUTE!AwsLambdaFunction.functionSlug`
- `naming.eol:AWSPSMAPI!ApiGatewayApi.apiSlug`
- `naming.eol:AWSPSMWORKFLOW!StepFunctionStateMachine.workflowSlug`
- `naming.eol:AWSPSMEVENTS!EventBridgeRule.eventRuleSlug`
- `naming.eol:AWSPSMMESSAGING!SqsQueue.queueSlug`
- `naming.eol:AWSPSMMESSAGING!SnsTopic.topicSlug`
- `naming.eol:AWSPSMSTORAGE!DynamoDbTable.tableSlug`
- `naming.eol:AWSPSMSTORAGE!S3Bucket.bucketSlug`
- `naming.eol:AWSPSMCORE!SamStack.stackSlug`
- `naming.eol:AWSPSMCORE!AwsStage.stageSlug`
- `naming.eol:AWSPSMCORE!SamStack.stackTemplatePath`
- `naming.eol:Any.displayName`

### `paths.eol`

- `paths.eol:Any.sourceExtension`
- `paths.eol:Any.testExtension`
- `paths.eol:Map.primaryLanguage`
- `paths.eol:Map.sourceExtension`
- `paths.eol:Map.testExtension`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.functionPathSegment`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.handlerPath`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.localSchemaPath`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.generatedHandlerName`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.generatedCodeUri`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.unitTestPath`
- `paths.eol:AWSPSMCOMPUTE!AwsLambdaFunction.integrationTestPath`
- `paths.eol:AWSPSMAPI!ApiGatewayApi.openApiPath`
- `paths.eol:AWSPSMWORKFLOW!StepFunctionStateMachine.aslPath`
- `paths.eol:KERNEL!StructuredDocument.documentPath`
- `paths.eol:AWSPSMWORKFLOW!StepFunctionStateMachine.workflowTestPath`
- `paths.eol:KERNEL!TraceableElement.fixtureSlug`
- `paths.eol:AWSPSMEVENTS!EventBridgeRule.eventFixturePath`
- `paths.eol:AWSPSMMESSAGING!SqsQueue.queueFixturePath`
- `paths.eol:AWSPSMMESSAGING!SnsTopic.topicFixturePath`
- `paths.eol:AWSPSMCORE!AwsStage.envPath`
- `paths.eol:Any.pathKind`
- `paths.eol:String.pathKind`
- `paths.eol:Map.goModulePath`

### `protected-regions.eol`

- `protected-regions.eol:KERNEL!TraceableElement.manualRegionId`
- `protected-regions.eol:AWSPSMCORE!AwsResource.manualRegionId`
- `protected-regions.eol:Any.manualTodo`
- `protected-regions.eol:Any.markdownRegionStart`
- `protected-regions.eol:Any.markdownRegionEnd`

### `sam.eol`

- `sam.eol:AWSPSMCORE!SamStack.uniqueParameters`
- `sam.eol:AWSPSMCORE!SamStack.capabilitiesText`
- `sam.eol:AWSPSMCORE!AwsStage.stackDeployName`
- `sam.eol:AWSPSMCORE!AwsStage.parameterOverrideText`

### `trace.eol`

- `trace.eol:AWSPSM!AwsPsmModel.defaultEmitContext`
- `trace.eol:AWSPSM!AwsPsmModel.defaultStageName`
- `trace.eol:AWSPSM!AwsPsmModel.allModeledResources`
- `trace.eol:Map.recordArtifact`
- `trace.eol:Map.recordProtectedRegion`
- `trace.eol:Map.addManualIssue`
- `trace.eol:Map.addArtifactPlan`

### `validation.eol`

- `validation.eol:AWSPSM!AwsPsmModel.collectBlockingIssues`
- `validation.eol:AWSPSMCORE!AwsResource.supportsStableCrossStackReference`
- `validation.eol:Any.validIamActionText`
- `validation.eol:Any.validIamResourceText`
- `validation.eol:AWSPSM!AwsPsmModel.missingResourcePolicyPrincipalIssues`
- `validation.eol:AWSPSM!AwsPsmModel.issueTuple`

### `values.eol`

- `values.eol:spaces`
- `values.eol:Any.jsonEscape`
- `values.eol:Any.jsonString`
- `values.eol:Any.yamlScalar`
- `values.eol:Any.yamlPlainScalar`
- `values.eol:yamlBlock`
- `values.eol:emitYamlScalarProperty`
- `values.eol:emitYamlBooleanProperty`
- `values.eol:emitYamlIntegerProperty`
- `values.eol:emitYamlStringListProperty`
- `values.eol:Any.cfnPolicyName`
- `values.eol:Any.lambdaArchitectureText`
- `values.eol:Any.lambdaTracingModeText`
- `values.eol:Any.cloudWatchComparisonOperatorText`
- `values.eol:Any.iamEffectText`
- `values.eol:Any.httpMethodText`
- `values.eol:Any.snsProtocolText`
- `values.eol:refForResource`
- `values.eol:arnForResource`
- `values.eol:Any.normalizeCfnArnReference`
- `values.eol:AWSPSMCORE!ValueExpression.cfnInlineYaml`
- `values.eol:AWSPSMCORE!ValueExpression.cfnValueYaml`
- `values.eol:AWSPSMCORE!ValueExpression.emitCfnValueProperty`
- `values.eol:AWSPSMEVENTS!EventBridgeApiKeyAuthParameters.eventBridgeAuthYaml`
- `values.eol:AWSPSMEVENTS!EventBridgeBasicAuthParameters.eventBridgeAuthYaml`
- `values.eol:AWSPSMEVENTS!EventBridgeOAuthParameters.eventBridgeAuthYaml`
- `values.eol:AWSPSMCORE!NativeProperty.emitNativePropertyYaml`
- `values.eol:Collection.emitNativePropertiesYaml`
- `values.eol:Collection.emitTagsYaml`
- `values.eol:structuredYamlProperty`
- `values.eol:Collection.emitSamTagsYaml`
- `values.eol:Collection.emitTagMapYaml`
- `values.eol:AWSPSMCORE!CorsConfiguration.emitCorsYaml`

## Runtime Preconditions

Generated Go tests run when a Go toolchain is available on PATH or at `C:/Program Files/Go/bin/go.exe`.
The emulator-backed tests are wired to Docker with `AWS_ENDPOINT_URL`, region, and test credentials
loaded from `.env` with safe defaults. `AWS_EMULATOR=floci` is the default; set `localstack` for the
alternate profile. Tests reuse a healthy running provider or start its pinned image on a disposable
Docker network, and are skipped only when Docker and the selected image are unavailable.

The deployment test clears proxy variables for the selected emulator and AWS CLI subprocesses by default,
deploys supported CloudFormation resources, packages the generated Go Lambda bootstrap with
executable mode, uploads generated ASL definitions referenced by `DefinitionUri`, waits for Lambda
`function-active-v2`, invokes the generated Lambda, executes generated service resources through
SQS, DynamoDB, S3, SSM, Secrets Manager, SNS, EventBridge, and Step Functions APIs, and then runs
generated Go tests with `GENERATED_LAMBDA_FUNCTION_NAME` set. `.env` proxy values are used only
when the corresponding provider proxy switch is enabled.

Some emulator modes can omit the generated `AWS::Serverless::StateMachine` from this SAM
deployment. The test verifies the SAM template still contains the generated state machine, then
creates and executes the workflow from the generated ASL file to retain live ASL coverage. See
`docs/internal/artifacts/aws-psm-code-generator-e2e.md`.
