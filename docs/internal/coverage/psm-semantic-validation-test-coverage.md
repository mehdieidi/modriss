# PSM Semantic Validation Test Coverage

This document traces the PSM EVL semantic validation coverage added for the AWS/serverless PSM DSML.

## Scope

- DSML: PSM only.
- EVL entrypoint: `mde/validation/psm/psm-semantic-validation.evl`.
- Test class: `packages/java/mde-evl-validator/src/test/java/io/mehdieidi/modriss/mde/validation/PsmSemanticValidationTest.java`.
- Verification command: `mvn -pl packages/java/mde-evl-validator -Dtest=PsmSemanticValidationTest test`.

Per the project validation boundary, chatbot/LLM assistant generated model output must still be gated only by structural Ecore/EMF conformance. These tests are an explicit semantic validation workflow for the PSM EVL rules.

## EVL Basis

The tests follow the Eclipse Epsilon EVL semantics documented in the [EVL documentation](https://eclipse.dev/epsilon/doc/evl/):

- `constraint` failures are mandatory validation failures.
- `critique` failures are non-mandatory quality findings.
- `guard` blocks limit when a rule applies.
- `check` blocks define the boolean condition that must hold for the selected model element.

## Test Strategy

- `repositoryPsmSamplePassesMandatorySemanticsAndReportsOptionalReadinessGaps` validates the checked-in PSM sample through the normal validation path and asserts that mandatory EVL diagnostics remain clean.
- `psmNegativeScenariosExerciseDocumentedSemanticRules` builds targeted invalid PSM models and asserts the exact mandatory/optional rule names that must execute.
- `psmEolHelpersHaveExplicitBehaviorCoverage` validates helper EOL operation behavior that is used by the EVL rules.
- `psmEolHelpersCoverExplicitTaggingPolicyBranch` validates the explicit tagging-policy branch of `activeTaggingPolicy()` and `requiredTagKeys()`.

Most generated negative fixtures run with structural validation disabled. This is intentional: the objective is to unit-test EVL guard/check semantics even when a fixture must omit a required Ecore reference or attribute to exercise the rule. The repository sample test still covers the normal structural-plus-semantic path.

## Helper EOL Coverage

The helper contracts exercise all operations in `mde/validation/psm/lib/psm-validation-helpers.eol`, including positive and fallback branches for:

- primitive helper predicates: `hasText`, `isTrueValue`, `isFalseValue`;
- model discovery and production scoping: `currentPsmModel`, `productionStageExists`, `productionStackIds`, `deployedToProductionStage`, `isProductionScoped`;
- resource flattening and labels: `nestedAwsResources`, `stackResourceSet`, `resourceLabel`, `stackLogicalIdKey`;
- tagging policy logic: `activeTaggingPolicy`, `requiredTagKeys`, `hasTagKey`, `duplicateTagKeys`, `missingRequiredTagKeys`;
- logical ID and dependency graph helpers: `duplicateResourceLogicalIdKeys`, `transitiveDependencyIds`, `resourcesWithDependencyCycles`;
- value-expression security: `usesSecureSource`;
- IAM statement helpers: `actionIsWildcard`, `resourceIsWildcard`, `hasLeastPrivilegeJustification`, `hasActionSide`, `hasResourceSide`, `isTrustPolicyStatement`;
- Lambda code helper: `isGeneratorManagedCodeSkeleton`;
- S3 public access logic: `blocksAllPublicAccess`;
- API helpers: `targetCount`, `apiRouteKeyPrefix`, `httpApiRouteKey`, `restApiRouteKey`, `webSocketRouteKey`, `duplicateHttpApiRouteKeys`, `duplicateRestApiRouteKeys`, `duplicateWebSocketRouteKeys`, `resolvedAccessLogGroup`;
- Step Functions helpers: `definitionCount`, `hasStateNamed`, `duplicateStateNames`.

The PSM helper contracts intentionally disable structural validation for helper-only fixtures that need partial objects, such as a route with no owning API, to exercise fallback branches directly.

## Scenario Matrix

| Scenario                                                                             | Coverage intent                                                                                                                                                             |
| ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `root-stage-stack`                                                                   | Root model stack/stage/prod-mode requirements and default region critique.                                                                                                  |
| `root-stack-resources-exist`                                                         | Empty-stack and deployable-stack resource requirements.                                                                                                                     |
| `stage-stack-core`                                                                   | Stack/stage uniqueness, prod deployment flags, SAM transform, validation tooling, and empty stack behavior.                                                                 |
| `resources-values-native`                                                            | Core AWS resource metadata, tags, logical IDs, dependencies, native resources, value expressions, and secure value handling.                                                |
| `compute`                                                                            | Lambda configuration, code package variants, environment variables, DLQ, event source mappings, provisioned concurrency, function URLs, and production critiques.           |
| `api`                                                                                | API Gateway APIs, routes, integrations, stages, access logs, authorizers, domains, usage plan keys, and quota-review critiques.                                             |
| `events-messaging-storage-security-identity-workflow-observability-networking-views` | EventBridge, SQS, SNS, DynamoDB, S3, IAM, KMS, Secrets Manager, SSM, Cognito, Step Functions, CloudWatch, VPC/security group, Lambda VPC attachment, and integration views. |
| `kernel-trace`                                                                       | Trace link reference/external identifier requirement.                                                                                                                       |

## Covered Rules

The test suite asserts execution of every executable PSM EVL rule currently listed in the test inventory:

```text
AccessLogStageRequiresGroupAndFormat
AccessLogsRequireLogGroupAndFormat
AlarmEvaluationSettingsValid
AlarmHasMetricAndThreshold
ApiDestinationHasEndpointAndConnection
ApiHasRoutes
ApiLambdaPermissionRecommended
ApiLambdaViewMatchesDeployableObjects
ApiShouldHaveStage
AslHasTerminalState
AutoPublishAliasRequiresVersionPublishing
AvoidAwsReservedTagPrefix
AvoidNotActionInAllowStatements
BatchSizePositive
CatchRuleHasErrorsAndNext
ChoiceRuleHasConditionAndNext
ChoiceStateHasChoices
CloudFormationRefHasTarget
CodeSigningDecisionHonored
CompositeAlarmShouldHaveActions
ConditionComplete
ConnectionAuthParametersMatchAuthorizationType
CredentialsArnMatchesCredentialsRole
CriticalEventTargetsHaveRetryOrDlq
CognitoAuthorizerShouldReferenceClients
DefaultRegionRecommended
DeployableResourceHasAwsType
DeployableStackHasResources
DlqHasExactlyOneTarget
DomainHasCertificate
DynamoAttributesCoverTableKeys
DynamoIndexKeysAreCoveredByAttributes
DynamoStreamMappingHasStartingPosition
DynamoStreamMappingRequiresStreamSpecification
DynamoTableHasValidPrimaryKeySchema
EnabledTtlHasAttributeName
EnvironmentVariableNameValid
EventBridgeLambdaPermissionRecommended
EventBridgeLambdaViewMatchesDeployableObjects
ExternalHttpSubscriptionsShouldHaveDlq
FifoQueueNameSuffix
FifoTopicNameSuffix
FifoTopicToSqsRequiresFifoQueue
FilterRuleHasValues
GetAttHasResourceAndAttribute
GsiProjectionIncludeHasAttributes
GsiProvisionedThroughputPositiveWhenPresent
ImageCodeHasImageUri
ImportedResourceHasImportIdentity
IntegrationHasSingleTarget
IntegrationTimeoutShouldNotExceedLambdaTimeout
JwtAuthorizerHasIssuerAndAudience
KmsAliasNameValid
KmsAlgorithmRequiresKmsKey
LambdaAuthorizerHasFunction
LambdaEphemeralStorageRange
LambdaHasCodeConfig
LambdaHasExecutionRole
LambdaHasLogGroup
LambdaHasSupportedCodeConfig
LambdaMemoryRange
LambdaPackageTypeMatchesCodeConfig
LambdaTimeoutRange
ListExpressionHasItems
LogicalIdUniqueInStack
LogicalIdValid
LongApiGatewayTimeoutRequiresQuotaReview
MapEntryHasKey
MapExpressionHasEntries
MaximumBatchingWindowRange
MfaDecisionMadeForProductionCriticalPools
MfaForProductionPrivilegedPools
ModeledVpcAttachmentShouldNotContradictRawIds
ModelHasStacks
ModelHasStages
NativeResourceTypeNameValid
NoAllowWildcardInProductionWithoutJustification
NoDependencyCycles
NoDirectSelfDependency
NonImportedResourceShouldNotHaveImportMetadata
NonLambdaTargetHasInvokeRole
NonTerminalStateHasNextOrTerminalType
NotificationRuleHasEventAndDestination
OAuthClientHasCallbackUrls
ParallelizationFactorRange
PartialBatchFailureRecommendedForSqs
PayPerRequestDoesNotUseProvisionedThroughput
PendingWindowRange
PipeHasSourceTargetAndRole
PlainTextHasLiteral
PortRangeValid
PreventUserExistenceErrorsRecommended
PrivateSubnetsRequireEndpointsWhenFlagged
ProdRequiresApproval
ProdShouldConfirmChangeset
ProductionAlarmShouldHaveActions
ProductionApiShouldHaveMetricsAndTracing
ProductionBucketsBlockPublicAccess
ProductionBucketsEncrypted
ProductionBucketsShouldVersion
ProductionFunctionUrlRequiresAuth
ProductionKmsKeyRotation
ProductionLambdaShouldHaveFailureDestinationOrDlq
ProductionLambdaShouldUseStructuredLogging
ProductionLambdaShouldUseTracing
ProductionLogGroupShouldUseKms
ProductionLogRetentionExplicit
ProductionModeHasProdStage
ProductionQueueShouldBeEncrypted
ProductionResourcesHaveRequiredTags
ProductionResourcesShouldRetainOnDelete
ProductionRoleShouldUsePermissionsBoundary
ProductionSecretShouldUseKms
ProductionStageShouldThrottle
ProductionStateMachineShouldLogAndTrace
ProductionTableShouldBeEncryptedWithKms
ProductionTableShouldHavePitRecoveryAndDeletionProtection
ProductionTopicShouldBeEncrypted
ProductionUserPoolShouldUseDeletionProtection
ProtectedRouteHasRequiredAuthConfiguration
ProvisionedConcurrencyPositive
ProvisionedModeNeedsThroughput
ProvisionedThroughputPositive
PublicAdminIngressRequiresReview
PublishAliasRequiresAliasName
QueueMessageSizeRangeValid
QueueTimingRangesValid
QueueVisibilityGreaterThanFunctionTimeout
RedrivePolicyValid
RelationshipViewHasEndpoints
ReplicationHasRoleAndRules
RequiredNativePropertyHasValue
RequiredPartialBatchFailureDecisionHonored
ReservedConcurrencyNonNegative
RetryPolicyRangesValid
RetryRuleRangesValid
RoleHasTrustPolicyStatements
RotationRequiredHasSchedule
RotationScheduleHasRulesOrLambda
RouteHasApi
RouteHasIntegration
RuleDoesNotMixPatternAndSchedule
RuleHasPatternOrSchedule
RuleHasTargets
SamTransformRecommended
ScheduleHasExpressionAndRole
ScheduleHasTarget
SecretEnvironmentValueUsesSecureReference
SecretLiteralReviewed
SecretNativePropertyUsesSecureExpression
SecretValueMustUseSecureReference
SecretHasValueOrGenerator
SecureParameterShouldUseKmsKey
SecureParameterUsesSecureType
SnsLambdaPermissionRecommended
SnsLambdaViewMatchesDeployableObjects
SqsFifoTargetHasMessageGroupId
SqsLambdaViewMatchesMapping
StackHasResources
StackLogicalIdsAreUnique
StackResourcesExist
StageDeploysAtLeastOneStack
StageHasAccountAndRegion
StageSpecificVariableShouldHaveRationale
StandardQueueShouldNotUseFifoSuffix
StartAtReferencesExistingState
StateMachineAslHasStates
StateMachineHasDefinition
StateMachineHasRole
StateMachineShouldUseSingleDefinitionSource
StateNamesUnique
StatementHasActionAndResourceSide
StepFunctionEventBridgeViewMatchesTarget
SubscriptionHasEndpointOrResource
TagKeyHasText
TagKeysAreUniquePerResource
TargetHasResourceOrArn
TargetIdsUniqueWithinRule
TaskStateHasResource
TerminalStateDoesNotHaveNext
TraceLinkHasReferenceOrExternalId
UnauthenticatedIdentitiesRequireReview
UniqueHttpRouteWithinApi
UniqueRestRouteWithinApi
UniqueStackNames
UniqueStageNames
UniqueWebSocketRouteKeyWithinApi
UsagePlanKeyUsesKnownType
ValidationToolsRecommended
ValueExpressionSourceShape
VpcAttachmentHasSubnetsAndSecurityGroups
VpcShouldEnableDns
WebsiteBucketShouldNotBeProductionCritical
ZipCodeHasExactlyOneLocation
ZipCodeHasRuntimeAndHandler
```

## Fixes Found

- `DynamoStreamMappingHasStartingPosition` was not reliably detecting an omitted enum attribute because the EMF default enum value made `isDefined()` true. The EVL now checks whether the `startingPosition` structural feature is explicitly set.
- `hasText()` used `trim().size()`, which did not treat blank strings as empty in the EOL runtime used by these tests. The PSM and shared helper implementations now use the documented `String.length()` operation after `trim()`.

No `.emf` metamodel files were changed.
