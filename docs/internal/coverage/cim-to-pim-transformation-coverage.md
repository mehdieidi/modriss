# CIM-to-PIM Transformation Coverage

This document traces semantic test coverage for the CIM-to-PIM ETL transformation under
`mde/transformations/cim-to-pim`.

The transformation is implemented in Eclipse Epsilon ETL, whose official documentation describes ETL
as a rule-based, modular model-to-model transformation language for multiple source and target
models: <https://eclipse.dev/epsilon/doc/etl/>.

## Scope

- Transformation: `mde/transformations/cim-to-pim/cim-to-pim.etl`
- Helper libraries:
  - `mde/transformations/cim-to-pim/lib/common.eol`
  - `mde/transformations/cim-to-pim/lib/mapping.eol`
  - `mde/transformations/cim-to-pim/lib/pim-builders.eol`
  - `mde/transformations/cim-to-pim/lib/resolution.eol`
  - `mde/transformations/cim-to-pim/lib/trace-readiness.eol`
- Source metamodel: `mde/metamodels/cim/cim-combined.ecore`
- Target metamodel: `mde/metamodels/pim/pim-combined.ecore`
- Test class:
  `packages/java/mde-etl-runner/src/test/java/io/mehdieidi/modriss/mde/etl/CimToPimEtlRegressionTest.java`
- Verification command:
  `mvn -pl packages/java/mde-etl-runner -Dtest=CimToPimEtlRegressionTest test`
- Helper utility verification:
  `mvn -pl packages/java/mde-etl-runner -Dtest=EpsilonEtlExecutorTest#textHelpersPreserveLowerCamelIdentifiers test`

No CIM or PIM metamodel `.emf` files were changed for this coverage work.

## Coverage Status

Status: 100% of CIM-to-PIM ETL rules and lifecycle blocks are covered by regression tests or the
canonical repository sample assertions.

The coverage is semantic rather than line-only. Tests assert generated PIM meaning: service
boundaries, principals, adapters, schemas, function contracts, API routes, stores, access patterns,
workflows, policies, flows, deployment units, trace evidence, and readiness/manual-review evidence.

## Test Scenarios

| Test                                                                          | Purpose                                                                                                                                                                                                                |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `executesCimToPimTransformationForRepresentativeBusinessModel`                | Representative business model: capability, actor, aggregate, command, query, event, API, function, event type, store, deployment unit, readiness.                                                                      |
| `preservesRelationshipTargetMultiplicityAcrossPimProjections`                 | Regression for relationship mapping: target-end requiredness and many-valued cardinality are preserved in both contract schema fields and storage data fields.                                                         |
| `transformsClimateReliefSampleThroughDefaultProfileWithSpecCompletenessShape` | Canonical end-to-end sample: bounded contexts, workflows, adapters, policies, business rules, decision models, trace links, EVL semantic validity.                                                                     |
| `coversCrossBoundaryRelationshipAndReviewBacklogRules`                        | Synthetic branch fixture: cross-capability dependency, protected query, enum fields, relationship fields, external workflow task, NFR-derived operational policies, data protection attachment, manual review backlog. |
| `createsReviewablePlaceholderArtifactsForCimWithoutServiceBoundaries`         | Underspecified CIM: placeholder service, manual completion function, blocking manual decisions, deployable ownership of late placeholder artifacts.                                                                    |
| `generatedOutputsCoverEveryCimToPimTransformationRuleFamily`                  | Rule-family sentinel: asserts trace/readiness evidence for every `TR-*` family and generated PIM classes for every major target semantic area.                                                                         |
| `eolHelperLibrariesProduceExpectedSemanticArtifacts`                          | EOL helper semantics: naming, type mapping, schema builders, trace/readiness evidence, service resolution, idempotency, observability, resilience, ownership, deployment, and configuration helpers.                   |
| `reportsParseDiagnosticsForBrokenEtl`                                         | Runner failure behavior: malformed ETL yields structured validation/parse diagnostics.                                                                                                                                 |

## EOL Helper Matrix

| Helper library        | Operations covered by semantic assertions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `common.eol`          | cache initialization through repeatable transformation execution; `camelCase`, `pascalCase`, `kebab`, `pluralize`, `removeSuffix`, `idFor`, `nameFor`, `collectionText`, `joinText`, `isTrue`, `containDeployableOnce`, `allServiceFunctions`, `serviceOwningDeployable`, `addTriggerToService` through generated names, paths, service ownership, triggers, and deployment contents.                                                                                                                                                                                     |
| `mapping.eol`         | `mapFieldType`, `deriveFormat`, `isPersonal`, `isSensitive`, `isSecret`, `isSensitiveClassification`, `mapPrincipalKind`, `inferStoreKind`, `inferConsistency`, `inferHttpMethod`, `inferCommandExecutionModel`, `inferComputeProfile`, `mapWorkflowKind`, `mapRiskSeverity`, and `mapSeverity` through generated schema fields, data fields, policies, routes, functions, stores, workflows, findings, and checks.                                                                                                                                                       |
| `pim-builders.eol`    | `fieldFor`, `fieldForWithDefault`, `createSchemaField`, `splitAllowedValues`, `createDataField`, `addCorrelationFields`, `ensureSchemaField`, `addEventMetadataFields`, `defaultEventEnvelope`, `createCommandRequestSchema`, `createCommandResponseSchema`, `createQueryRequestSchema`, `createQueryResponseSchema`, `createDefaultObservability`, `createDefaultResilience`, `createIdempotencyPolicy`, and `createIdempotencyPolicyForTarget` through generated contracts, schemas, event envelopes, observability/resilience/idempotency policies, and enum literals. |
| `resolution.eol`      | `serviceFor`, `resolveServiceFor`, `commandFunction`, `queryFunction`, `policyFunction`, `eventTypeFor`, `schemaForEntity`, `schemaForDomainConcept`, `storeForAggregate`, `storeForEntity`, `adapterFor`, `workflowFor`, `policyTargetsFor`, `policyTargetsForAll`, `ensureApiForService`, and `ensureEventBusForService` through service-contained deployables, policy attachments, route integrations, event channels, and fallback placeholder service behavior.                                                                                                      |
| `trace-readiness.eol` | `pimRoot`, `readinessRoot`, `traceRoot`, `markGeneratedHelperWithSourceId`, `markGeneratedHelper`, `copyCommonTrace`, `addTrace`, `mapTraceConfidence`, `traceGenerated`, `isTargetModelReference`, `appendTargetAffectedElements`, `affectedStableKey`, `manualDecision`, `readinessFinding`, and `readinessCheck` through generated trace links, rule IDs, affected target elements, manual decisions, findings, and readiness checks.                                                                                                                                  |

## Rule Matrix

| Module                       | Rule or block                                 | Covered by                                                              |
| ---------------------------- | --------------------------------------------- | ----------------------------------------------------------------------- |
| `cim-to-pim.etl`             | `pre ValidateInput`                           | All successful transformation tests, plus malformed ETL diagnostic test |
| `root-scaffolding.etl`       | `CIMModel2PIMModel`                           | representative, sample, placeholder, rule-family sentinel               |
| `root-scaffolding.etl`       | `DefaultEnvironmentsAndImplementationProfile` | sample, placeholder, rule-family sentinel                               |
| `root-scaffolding.etl`       | `Requirement2BusinessRule`                    | sample                                                                  |
| `root-scaffolding.etl`       | `BusinessGoal2BusinessRule`                   | representative, synthetic branch                                        |
| `root-scaffolding.etl`       | `UbiquitousLanguageTerm2BusinessRule`         | sample                                                                  |
| `root-scaffolding.etl`       | `KPI2ReadinessCheck`                          | sample                                                                  |
| `root-scaffolding.etl`       | `Stakeholder2ManualDecision`                  | sample                                                                  |
| `root-scaffolding.etl`       | `post InitialReadinessChecks`                 | all successful transformation tests                                     |
| `boundaries-security.etl`    | `BoundedContext2Service`                      | sample                                                                  |
| `boundaries-security.etl`    | `Capability2Service`                          | representative, synthetic branch                                        |
| `boundaries-security.etl`    | `Actor2Principal`                             | representative, synthetic branch                                        |
| `boundaries-security.etl`    | `Role2Principal`                              | sample                                                                  |
| `boundaries-security.etl`    | `ExternalSystem2Adapter`                      | synthetic branch, sample                                                |
| `boundaries-security.etl`    | `post EnsureServiceExists`                    | placeholder                                                             |
| `domain-data.etl`            | `Entity2Schema`                               | representative, sample, synthetic branch                                |
| `domain-data.etl`            | `ValueObject2Schema`                          | sample                                                                  |
| `domain-data.etl`            | `DomainRelationship2SchemaReference`          | synthetic branch                                                        |
| `domain-data.etl`            | `post AttachRelationshipDataFields`           | synthetic branch                                                        |
| `domain-data.etl`            | `Aggregate2DataStore`                         | representative, sample, synthetic branch                                |
| `domain-data.etl`            | `DataClassification2DataProtectionPolicy`     | synthetic branch, sample                                                |
| `domain-data.etl`            | `PrivacyConstraint2Policies`                  | sample                                                                  |
| `domain-data.etl`            | `ComplianceConstraint2CompliancePolicy`       | sample                                                                  |
| `behavior-contracts.etl`     | `BusinessError2ErrorSchema`                   | sample                                                                  |
| `behavior-contracts.etl`     | `BusinessEvent2EventType`                     | representative, sample                                                  |
| `behavior-contracts.etl`     | `Command2Function`                            | representative, sample                                                  |
| `behavior-contracts.etl`     | `UserInitiatedCommand2ApiRoute`               | representative, sample                                                  |
| `behavior-contracts.etl`     | `Query2Function`                              | representative, synthetic branch, sample                                |
| `behavior-contracts.etl`     | `Query2ApiRoute`                              | representative, synthetic branch, sample                                |
| `behavior-contracts.etl`     | `Query2AccessPattern`                         | representative, synthetic branch, sample                                |
| `process-policy.etl`         | `Policy2PolicyHandlerFunction`                | sample                                                                  |
| `process-policy.etl`         | `Policy2ArchitecturePolicy`                   | sample                                                                  |
| `process-policy.etl`         | `DecisionTable2ChoiceLogic`                   | sample                                                                  |
| `process-policy.etl`         | `DecisionTable2DecisionModel`                 | sample                                                                  |
| `process-policy.etl`         | `BusinessProcess2Workflow`                    | synthetic branch, sample                                                |
| `process-policy.etl`         | `StartStep2WorkflowStartStep`                 | synthetic branch, sample                                                |
| `process-policy.etl`         | `EndStep2WorkflowSuccessEndStep`              | synthetic branch, sample                                                |
| `process-policy.etl`         | `DecisionStep2WorkflowChoiceStep`             | sample                                                                  |
| `process-policy.etl`         | `WaitLikeStep2WorkflowWaitStep`               | sample                                                                  |
| `process-policy.etl`         | `ProcessStep2WorkflowTaskStep`                | synthetic branch, sample                                                |
| `process-policy.etl`         | `ProcessTransition2WorkflowTransition`        | synthetic branch, sample                                                |
| `process-policy.etl`         | `post AttachWorkflowDetails`                  | synthetic branch, sample                                                |
| `process-policy.etl`         | `ExceptionScenario2ErrorHandler`              | sample                                                                  |
| `process-policy.etl`         | `SecurityConstraint2SecurityPolicies`         | sample                                                                  |
| `process-policy.etl`         | `TemporalConstraint2TimeoutPolicy`            | synthetic branch, sample                                                |
| `process-policy.etl`         | `AvailabilityReliabilityNfr2ResiliencePolicy` | sample                                                                  |
| `process-policy.etl`         | `PerformanceNfr2TimeoutPolicy`                | synthetic branch, sample                                                |
| `process-policy.etl`         | `AuditOperabilityNfr2Observability`           | sample                                                                  |
| `process-policy.etl`         | `CostNfr2CostPolicy`                          | sample                                                                  |
| `process-policy.etl`         | `DataQualityNfr2SchemaConstraintAndReadiness` | sample                                                                  |
| `process-policy.etl`         | `GenericComplianceNfr2CompliancePolicy`       | sample                                                                  |
| `integration-deployment.etl` | `Risk2ReadinessFinding`                       | sample                                                                  |
| `integration-deployment.etl` | `Assumption2ReadinessFinding`                 | sample                                                                  |
| `integration-deployment.etl` | `post LinkAndDerive`                          | representative, sample, synthetic branch                                |
| `integration-deployment.etl` | `post DeploymentAndConfiguration`             | representative, sample, synthetic branch, placeholder                   |
| `integration-deployment.etl` | `post ValidationAndReadinessClosure`          | representative, sample, synthetic branch, placeholder                   |

## Bug Fixed

The placeholder test found a real ordering issue in `ValidationAndReadinessClosure`: when a CIM had
no executable behavior, the transformation created `fn_tbd_manual_completion` after deployment units
and service memberships had already been generated. The function was contained by the service but was
missing from the deployment unit and service membership trace.

Fix: `ValidationAndReadinessClosure` now adds the late placeholder function to the service
membership list and the existing deployment unit that owns the placeholder service.

The helper test also found a naming helper defect: the Java-backed `camelCase` operation used by EOL
lowercased existing lower-camel identifiers such as `customerId` into `customerid`. This broke the
intended schema/API/data-field semantics for business identifiers.

Fix: `EtlTextChecks.camelCase(...)` now recognizes existing lower-camel boundaries while still
normalizing spaced, kebab-case, and underscore names. A direct unit test covers this helper behavior.

## Validation Boundary

These are model transformation tests. They execute ETL and inspect generated PIM XMI. The existing
sample test also runs PIM EVL semantic validation for the canonical sample.

The AI modeling assistant validation boundary remains unchanged: assistant-generated actions,
patches, proposals, checkpoints, and model outputs must be gated only by structural Ecore/EMF
conformance and may call only `ModelService.validateStructural(...)` for generated model output
validation. EVL semantic validation is only for explicit user/model validation workflows outside
chatbot assistant apply/repair/commit paths.
