# Metamodel Class Reference

Auto-generated reference for every class in the CIM, PIM, and PSM metamodels.
For each class this document lists whether it is abstract, which other concepts
reference it (`ref`), which concepts contain it (`val`), and its direct supertypes.

> Source: `mde/metamodels/**/*.emf`

## CIM (Computation-Independent Model)

**Modules:** `cim-behavior.emf`, `cim-domain-data.emf`, `cim-governance.emf`, `cim-organization.emf`, `cim-process-policy.emf`, `cim-root.emf`, `cim-transformation.emf`  
**Classes:** 56

### `cim-behavior.emf`

#### `BusinessError`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Command.possibleErrors`, `CommandOutcome.errors`, `ExceptionScenario.errors`
- **Incoming `val`:** `CIMModel.businessErrors`
- **Inherits:** `kernel.TraceableElement`

#### `BusinessEvent`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Actor.observesEvents`, `AggregateCandidate.emittedEvents`, `BoundedContextCandidate.events`, `BusinessCapability.containsEvents`, `BusinessError.emittedEvents`, `BusinessProcess.triggeringEvent`, `Command.expectedEvents`, `Command.rejectionEvents`, `CommandOutcome.emittedEvents`, `DecisionRule.resultingEvents`, `ExceptionScenario.resultingEvents`, `ExternalSystem.consumedEvents`, `ExternalSystem.producedEvents`, `Policy.emitsEvents`, `Policy.triggeredBy`
- **Incoming `val`:** `CIMModel.events`
- **Inherits:** `kernel.TraceableElement`

#### `Command`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Actor.issuesCommands`, `AggregateCandidate.handledCommands`, `BoundedContextCandidate.commands`, `BusinessCapability.containsCommands`, `BusinessEvent.expectedByCommands`, `BusinessEvent.rejectedByCommands`, `BusinessProcess.triggeringCommand`, `DecisionRule.resultingCommands`, `Policy.emitsCommands`, `Policy.guards`, `SecurityConstraint.constrainedCommands`
- **Incoming `val`:** `CIMModel.commands`
- **Inherits:** `kernel.TraceableElement`

#### `CommandOutcome`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Command.outcomes`
- **Inherits:** `kernel.TraceableElement`

#### `Condition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessProcess.postconditions`, `BusinessProcess.preconditions`, `Command.preconditions`, `DecisionStep.condition`, `ProcessTransition.conditionRef`
- **Incoming `val`:** `CIMModel.conditions`
- **Inherits:** `kernel.TraceableElement`

#### `Query`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Actor.issuesQueries`, `BoundedContextCandidate.queries`, `BusinessCapability.containsQueries`, `Policy.constrainsQueries`, `SecurityConstraint.constrainedQueries`
- **Incoming `val`:** `CIMModel.queries`
- **Inherits:** `kernel.TraceableElement`

### `cim-domain-data.emf`

#### `AggregateCandidate`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Command.targetAggregate`
- **Incoming `val`:** `CIMModel.aggregates`
- **Inherits:** `DomainConcept`

#### `BusinessInvariant`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AggregateCandidate.invariants`, `DomainEntity.invariants`
- **Inherits:** `kernel.TraceableElement`

#### `DataClassification`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `InformationItem.classification`
- **Incoming `val`:** `CIMModel.classifications`
- **Inherits:** `kernel.TraceableElement`

#### `DomainConcept`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `BusinessInvariant.constrainedConcepts`, `Condition.referencedConcepts`
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `DomainEntity`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AggregateCandidate.members`, `BoundedContextCandidate.entities`, `BusinessCapability.managesEntities`, `BusinessEvent.affects`, `Query.reads`
- **Incoming `val`:** `CIMModel.entities`
- **Inherits:** `DomainConcept`

#### `DomainRelationship`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DomainConcept.incomingRelationships`, `DomainConcept.outgoingRelationships`
- **Incoming `val`:** `CIMModel.relationships`
- **Inherits:** `kernel.SemanticRelationship`

#### `InformationItem`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessEvent.payload`, `Command.input`, `CommandOutcome.output`, `Condition.referencedInformation`, `DecisionTable.inputs`, `DecisionTable.outputs`, `DomainEntity.attributes`, `DomainEntity.identityAttributes`, `ExternalInteractionStep.exchangedInformation`, `ExternalSystem.exchangedInformation`, `InformationItem.parent`, `PrivacyConstraint.dataItems`, `Query.input`, `Query.output`, `SecurityConstraint.constrainedInformation`, `ValueObject.attributes`, `ValueObject.equalityAttributes`
- **Incoming `val`:** `CIMModel.informationItems`, `InformationItem.subItems`
- **Inherits:** `kernel.TraceableElement`

#### `LifecycleStateDefinition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DomainEntity.lifecycleStates`
- **Inherits:** `kernel.TraceableElement`

#### `ValueObject`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.valueObjects`
- **Inherits:** `DomainConcept`

### `cim-governance.emf`

#### `ComplianceConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `InformationItem.complianceConstraints`
- **Incoming `val`:** _none_
- **Inherits:** `NonFunctionalRequirement`

#### `NonFunctionalRequirement`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessCapability.constrainedBy`
- **Incoming `val`:** _none_
- **Inherits:** `cimorg.Requirement`

#### `PrivacyConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `InformationItem.privacyConstraints`
- **Incoming `val`:** _none_
- **Inherits:** `NonFunctionalRequirement`

#### `QualityScenario`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `NonFunctionalRequirement.scenarios`
- **Inherits:** `kernel.TraceableElement`

#### `SecurityConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `NonFunctionalRequirement`

### `cim-organization.emf`

#### `AcceptanceCriterion`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Requirement.acceptanceCriteria`
- **Inherits:** `kernel.TraceableElement`

#### `Actor`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessCapability.owner`, `BusinessProcess.triggeringActor`, `Command.issuedBy`, `PrivacyConstraint.dataSubjects`, `Query.issuedBy`, `Role.assignedTo`, `SecurityConstraint.constrainedActors`
- **Incoming `val`:** `CIMModel.actors`
- **Inherits:** `kernel.TraceableElement`

#### `BoundedContextCandidate`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AggregateCandidate.context`
- **Incoming `val`:** `CIMModel.boundedContexts`
- **Inherits:** `kernel.TraceableElement`

#### `BusinessCapability`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BoundedContextCandidate.capabilities`, `BusinessGoal.refinedBy`, `BusinessProcess.owningCapability`, `Command.targetCapability`, `DomainEntity.owningCapability`, `Query.targetCapability`
- **Incoming `val`:** `CIMModel.capabilities`
- **Inherits:** `kernel.TraceableElement`

#### `BusinessGoal`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessCapability.supports`, `KPI.measures`, `Requirement.supportsGoals`, `Stakeholder.ownsGoals`
- **Incoming `val`:** `CIMModel.goals`
- **Inherits:** `kernel.TraceableElement`

#### `CapabilityDependency`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.capabilityDependencies`
- **Inherits:** `kernel.SemanticRelationship`

#### `ExternalSystem`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessEvent.causedByExternalSystems`, `BusinessEvent.consumedByExternalSystems`
- **Incoming `val`:** _none_
- **Inherits:** `Actor`

#### `KPI`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessGoal.measuredBy`
- **Incoming `val`:** `CIMModel.kpis`
- **Inherits:** `kernel.TraceableElement`

#### `Requirement`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessCapability.realizesRequirements`, `Requirement.conflictsWith`, `Requirement.dependsOn`, `Stakeholder.providesRequirements`
- **Incoming `val`:** `CIMModel.requirements`
- **Inherits:** `kernel.TraceableElement`

#### `RequirementRelationship`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.requirementRelationships`
- **Inherits:** `kernel.SemanticRelationship`

#### `Role`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Actor.playsRoles`, `ProcessStep.responsibleRoles`
- **Incoming `val`:** `CIMModel.roles`
- **Inherits:** `kernel.TraceableElement`

#### `Stakeholder`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessGoal.owners`
- **Incoming `val`:** `CIMModel.stakeholders`
- **Inherits:** `kernel.TraceableElement`

#### `UbiquitousLanguageTerm`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `BoundedContextCandidate.glossaryTerms`
- **Inherits:** `kernel.TraceableElement`

### `cim-process-policy.emf`

#### `BusinessProcess`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BusinessCapability.ownsProcesses`, `BusinessEvent.consumedByProcesses`
- **Incoming `val`:** `CIMModel.processes`
- **Inherits:** `kernel.TraceableElement`

#### `CommandStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `DecisionRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DecisionModel.rules`, `DecisionTable.rules`
- **Inherits:** `kernel.TraceableElement`

#### `DecisionStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `DecisionTable`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DecisionStep.decisionTable`, `Policy.decisionTable`
- **Incoming `val`:** `CIMModel.decisionTables`
- **Inherits:** `kernel.TraceableElement`

#### `EndStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `EventStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `ExceptionScenario`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `BusinessProcess.exceptions`
- **Inherits:** `kernel.TraceableElement`

#### `ExternalInteractionStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `HumanTaskStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `Policy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `BoundedContextCandidate.policies`, `BusinessEvent.causedByPolicies`, `BusinessEvent.consumedByPolicies`
- **Incoming `val`:** `CIMModel.policies`
- **Inherits:** `kernel.TraceableElement`

#### `PolicyStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `ProcessStep`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `BusinessProcess.steps`
- **Inherits:** `kernel.TraceableElement`

#### `ProcessTransition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `BusinessProcess.transitions`
- **Inherits:** `kernel.SemanticRelationship`

#### `QueryStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `StartStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

#### `TemporalConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `BusinessProcess.temporalConstraints`
- **Inherits:** `kernel.TraceableElement`

#### `WaitStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ProcessStep`

### `cim-root.emf`

#### `CIMModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

### `cim-transformation.emf`

#### `Assumption`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.assumptions`
- **Inherits:** `kernel.TransformationAssumption`

#### `Hotspot`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.hotspots`
- **Inherits:** `kernel.TraceableElement`

#### `Risk`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.risks`
- **Inherits:** `kernel.TraceableElement`

#### `TransformationProfile`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CIMModel.transformationProfile`
- **Inherits:** `kernel.TraceableElement`

## PIM (Platform-Independent Model)

**Modules:** `pim-api.emf`, `pim-compute.emf`, `pim-config.emf`, `pim-contracts.emf`, `pim-data.emf`, `pim-deployment.emf`, `pim-external.emf`, `pim-integration.emf`, `pim-policy.emf`, `pim-root.emf`, `pim-security.emf`, `pim-workflow.emf`  
**Classes:** 110

### `pim-api.emf`

#### `Api`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ServerlessService.apis`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`, `kernel.ConfigurableElement`

#### `ApiRoute`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Api.routes`
- **Inherits:** `kernel.TraceableElement`, `kernel.InvocationSource`, `kernel.FlowEndpoint`, `kernel.RouteEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`, `kernel.ConfigurableElement`

#### `ErrorMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiRoute.errorMappings`
- **Inherits:** `kernel.TraceableElement`

### `pim-compute.emf`

#### `ComputeElement`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

#### `Function`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiRoute.functionIntegration`, `ChoiceStep.invokesFunction`, `CompensationPolicy.compensationFunctions`, `ErrorHandler.handlerFunction`, `EventChannel.consumers`, `EventChannel.producers`, `TaskStep.invokesFunction`
- **Incoming `val`:** `ServerlessService.functions`
- **Inherits:** `ComputeElement`, `kernel.DeployableElement`, `kernel.InvocationTarget`, `kernel.FunctionTarget`, `kernel.SubscriptionTarget`, `kernel.RoutingTarget`, `kernel.ConfigurableElement`

#### `Trigger`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Function.triggers`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`

### `pim-config.emf`

#### `ConfigParameter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Environment.parameters`, `EnvironmentVariable.parameter`
- **Incoming `val`:** `ConfigurationSet.parameters`
- **Inherits:** `kernel.TraceableElement`

#### `ConfigurationSet`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Environment.configurationSets`
- **Incoming `val`:** `PIMModel.configurations`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.PolicyTarget`, `kernel.ConfigurableElement`

#### `CredentialRequirement`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Secret.usedForCredentials`
- **Incoming `val`:** `ExternalAdapter.credentials`
- **Inherits:** `kernel.TraceableElement`, `kernel.CredentialRequirementLike`

#### `EnvironmentVariable`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Environment.variables`, `Function.environmentVariables`
- **Incoming `val`:** `ConfigurationSet.environmentVariables`
- **Inherits:** `kernel.TraceableElement`

#### `Secret`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CredentialRequirement.secret`, `EnvironmentVariable.secret`, `Function.usesSecrets`
- **Incoming `val`:** `PIMModel.secrets`
- **Inherits:** `kernel.TraceableElement`, `kernel.ProtectedResource`, `kernel.PolicyTarget`

### `pim-contracts.emf`

#### `ApiContract`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Api.contract`
- **Inherits:** `kernel.TraceableElement`

#### `EventEnvelope`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventType.envelope`
- **Inherits:** `kernel.TraceableElement`

#### `EventType`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CallbackTaskConfig.completionEvents`, `CompensationPolicy.compensationEvents`, `DataChangeStream.emittedEvents`, `EventChannel.eventTypes`, `EventRoutingRule.eventTypes`, `Function.publishes`, `Function.subscribesTo`, `FunctionContract.emittedEvents`, `HumanTask.completionEvents`, `ObjectNotificationRule.emittedEvents`, `ObjectStore.emittedEvents`
- **Incoming `val`:** `PIMModel.eventTypes`
- **Inherits:** `kernel.TraceableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`

#### `FunctionContract`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `Schema`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiContract.errorSchemas`, `ApiContract.requestSchemas`, `ApiContract.responseSchemas`, `ApiRoute.requestSchema`, `ApiRoute.responseSchema`, `BusinessRule.inputSchemas`, `BusinessRule.outputSchemas`, `DecisionModel.inputs`, `DecisionModel.outputs`, `ErrorMapping.errorSchema`, `FunctionContract.errorSchemas`, `FunctionContract.inputSchema`, `FunctionContract.outputSchema`, `ObjectStore.objectMetadataSchemas`, `SchemaField.objectSchema`
- **Incoming `val`:** `PIMModel.schemas`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`

#### `SchemaConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Schema.constraints`
- **Inherits:** `kernel.TraceableElement`

#### `SchemaEnumLiteral`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SchemaField.enumValues`
- **Inherits:** `kernel.TraceableElement`

#### `SchemaField`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `Schema.fields`, `SchemaField.arrayItem`, `SchemaField.mapValue`
- **Inherits:** `kernel.TraceableElement`

#### `SchemaValidationConstraint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SchemaField.constraints`
- **Inherits:** `kernel.TraceableElement`

### `pim-data.emf`

#### `AccessPattern`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DataAccess.accessPatterns`, `DataModel.accessPatterns`, `IndexCandidate.supportsAccessPatterns`
- **Incoming `val`:** `DataStore.accessPatterns`
- **Inherits:** `kernel.TraceableElement`

#### `DataAccess`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.dataAccesses`
- **Inherits:** `kernel.TraceableElement`

#### `DataChangeStream`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DataStore.changeStream`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.InvocationSource`, `kernel.EventCarrier`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`

#### `DataField`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DataModel.storageFields`
- **Inherits:** `kernel.TraceableElement`

#### `DataModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DataAccess.dataModels`
- **Incoming `val`:** `DataStore.ownedDataModels`
- **Inherits:** `kernel.TraceableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`

#### `DataStore`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `StorageElement`, `kernel.RoutingTarget`, `kernel.SubscriptionTarget`

#### `IndexCandidate`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DataStore.indexCandidates`
- **Inherits:** `kernel.TraceableElement`

#### `ObjectNotificationRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObjectStore.notificationRules`
- **Inherits:** `kernel.TraceableElement`

#### `ObjectStore`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `StorageElement`, `kernel.InvocationSource`, `kernel.RoutingTarget`, `kernel.SubscriptionTarget`, `kernel.EventCarrier`

#### `StorageElement`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `Function.reads`, `Function.writes`
- **Incoming `val`:** `ServerlessService.stores`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`, `kernel.DataAccessTarget`, `kernel.ConfigurableElement`

### `pim-deployment.emf`

#### `DeploymentUnit`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.deploymentUnits`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`, `kernel.ConfigurableElement`

#### `Environment`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DeploymentUnit.targetEnvironments`
- **Incoming `val`:** `PIMModel.environments`
- **Inherits:** `kernel.TraceableElement`, `kernel.EnvironmentTarget`, `kernel.PolicyTarget`

#### `ImplementationProfile`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.implementationProfile`
- **Inherits:** `kernel.TraceableElement`

#### `PlatformCapability`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.platformCapabilities`
- **Inherits:** `kernel.TraceableElement`

#### `PlatformMappingAssessment`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.platformMappingAssessments`
- **Inherits:** `kernel.TraceableElement`

#### `ServerlessService`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `DeploymentUnit.services`
- **Incoming `val`:** `PIMModel.services`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`, `kernel.ConfigurableElement`

#### `ServiceElementMembership`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.serviceMemberships`
- **Inherits:** `kernel.TraceableElement`

### `pim-external.emf`

#### `ExternalAdapter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventChannel.externalConsumers`, `EventChannel.externalProducers`, `Function.callsAdapters`, `TaskStep.invokesAdapter`
- **Incoming `val`:** `ServerlessService.adapters`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.FlowEndpoint`, `kernel.InvocationTarget`, `kernel.FunctionTarget`, `kernel.SubscriptionTarget`, `kernel.RoutingTarget`, `kernel.ExternalCallTarget`, `kernel.ProtectedResource`, `kernel.PolicyTarget`, `kernel.ConfigurableElement`

#### `ExternalEndpoint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.externalEndpoints`
- **Inherits:** `kernel.TraceableElement`, `kernel.ExternalCallTarget`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

### `pim-integration.emf`

#### `EventBus`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventChannel`

#### `EventChannel`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ServerlessService.channels`
- **Inherits:** `IntegrationElement`, `kernel.DeployableElement`, `kernel.InvocationSource`, `kernel.SubscriptionTarget`, `kernel.RoutingTarget`, `kernel.EventCarrier`

#### `EventFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `EventRoutingRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBus.routingRules`
- **Inherits:** `kernel.TraceableElement`, `kernel.InvocationSource`

#### `ExternalIntegrationFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `Flow`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.flows`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`

#### `IntegrationElement`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

#### `MessageFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `OrchestrationFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `PubSubFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `Queue`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Queue.deadLetterChannel`
- **Incoming `val`:** _none_
- **Inherits:** `EventChannel`

#### `RequestResponseFlow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `Flow`

#### `Schedule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ServerlessService.schedules`
- **Inherits:** `IntegrationElement`, `kernel.DeployableElement`, `kernel.InvocationSource`

#### `Subscription`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `PubSubFlow.subscriptions`
- **Incoming `val`:** `Topic.subscriptions`
- **Inherits:** `kernel.TraceableElement`

#### `Topic`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventChannel`

### `pim-policy.emf`

#### `AlertPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObservabilityConfig.alerts`
- **Inherits:** `PolicySetting`

#### `ArchitecturePolicy`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `Flow.policies`, `ServerlessService.constrainedBy`
- **Incoming `val`:** `PIMModel.policies`
- **Inherits:** `kernel.TraceableElement`

#### `BackupPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `StorageElement.backupPolicy`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `BatchPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Queue.batchPolicy`, `Trigger.batchPolicy`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `BusinessRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.businessRules`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`

#### `CachePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `CompliancePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `ConcurrencyPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Function.concurrency`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `CorsPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Api.cors`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `CostPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `DataProtectionPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `StorageElement.dataProtectionPolicies`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `DataQualityPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `DeadLetterPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ResiliencePolicy.deadLetter`
- **Inherits:** `PolicySetting`

#### `DecisionModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `PIMModel.decisionModels`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.PolicyTarget`

#### `DecisionRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DecisionModel.rules`, `DecisionTable.rules`
- **Inherits:** `kernel.TraceableElement`

#### `IdempotencyPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ExternalAdapter.idempotency`, `Function.idempotency`, `Workflow.idempotency`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `LoggingPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObservabilityConfig.logging`
- **Inherits:** `PolicySetting`

#### `MetricDimension`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CloudWatchAlarm.dimensions`, `CloudWatchMetricTransformation.dimensions`, `MetricPolicy.dimensions`
- **Inherits:** `kernel.TraceableElement`

#### `MetricPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObservabilityConfig.metrics`
- **Inherits:** `PolicySetting`

#### `ObservabilityConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Api.observability`, `EventChannel.observability`, `ExternalAdapter.observability`, `Flow.observability`, `Function.observability`, `Workflow.observability`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `OrderingPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `PolicySetting`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `RateLimitPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Api.rateLimit`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `ResiliencePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventChannel.resilience`, `EventRoutingRule.resilience`, `ExternalAdapter.resilience`, `Flow.resilience`, `Function.resilience`, `Trigger.resilience`, `Workflow.resilience`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `RetentionPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `StorageElement.retentionPolicy`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `RetryPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ResiliencePolicy.retry`, `WorkflowStep.retry`
- **Inherits:** `PolicySetting`

#### `Slo`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObservabilityConfig.slos`
- **Inherits:** `kernel.TraceableElement`

#### `TimeoutPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiRoute.timeout`, `CallbackTaskConfig.timeout`, `Function.timeout`, `HumanTask.timeout`, `ResiliencePolicy.timeout`
- **Incoming `val`:** _none_
- **Inherits:** `ArchitecturePolicy`

#### `TracingPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ObservabilityConfig.tracing`
- **Inherits:** `PolicySetting`

### `pim-root.emf`

#### `PIMModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

### `pim-security.emf`

#### `AuthPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Api.auth`
- **Incoming `val`:** _none_
- **Inherits:** `SecurityPolicy`

#### `AuthorizationPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiRoute.authorization`
- **Incoming `val`:** _none_
- **Inherits:** `SecurityPolicy`

#### `IdentityProvider`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AuthPolicy.identityProvider`
- **Incoming `val`:** `PIMModel.identityProviders`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

#### `Permission`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AuthorizationPolicy.permissions`
- **Incoming `val`:** `Principal.permissions`
- **Inherits:** `kernel.TraceableElement`

#### `Principal`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AuthorizationPolicy.allowedPrincipals`, `EscalationPolicy.escalateTo`, `HumanTask.assignees`, `IdentityProvider.principals`
- **Incoming `val`:** `PIMModel.principals`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

#### `SecurityPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `Function.securityPolicies`
- **Incoming `val`:** _none_
- **Inherits:** `policy.ArchitecturePolicy`

### `pim-workflow.emf`

#### `ApprovalTask`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `HumanTask`

#### `CallbackTaskConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `TaskStep.callbackConfig`
- **Inherits:** `kernel.TraceableElement`

#### `ChoiceStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `CompensationPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `StartStep.compensation`, `TaskStep.compensation`
- **Incoming `val`:** _none_
- **Inherits:** `policy.ArchitecturePolicy`

#### `ErrorHandler`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `WorkflowStep.catchHandlers`
- **Inherits:** `kernel.TraceableElement`

#### `EscalationPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `HumanTask.escalation`
- **Incoming `val`:** `PIMModel.escalationPolicies`
- **Inherits:** `kernel.TraceableElement`

#### `FailureEndStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `HumanTask`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `TaskStep.humanTask`
- **Incoming `val`:** `PIMModel.humanTasks`
- **Inherits:** `kernel.TraceableElement`, `kernel.PolicyTarget`

#### `MapStateConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `MapStep.mapConfig`
- **Inherits:** `kernel.TraceableElement`

#### `MapStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `ParallelBranch`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ParallelStep.branches`
- **Inherits:** `kernel.TraceableElement`

#### `ParallelStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `PassStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `StartStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `SuccessEndStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `TaskStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `WaitStep`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `WorkflowStep`

#### `Workflow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiRoute.workflowIntegration`, `EventChannel.workflowConsumers`, `TaskStep.nestedWorkflow`
- **Incoming `val`:** `ServerlessService.workflows`
- **Inherits:** `kernel.TraceableElement`, `kernel.DeployableElement`, `kernel.InvocationTarget`, `kernel.WorkflowTarget`, `kernel.SubscriptionTarget`, `kernel.RoutingTarget`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`, `kernel.ConfigurableElement`

#### `WorkflowStep`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `ErrorHandler.nextStep`
- **Incoming `val`:** `ParallelBranch.steps`, `Workflow.steps`
- **Inherits:** `kernel.TraceableElement`, `kernel.FlowEndpoint`, `kernel.PolicyTarget`, `kernel.ProtectedResource`

#### `WorkflowTransition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ParallelBranch.transitions`, `Workflow.transitions`
- **Inherits:** `kernel.TraceableElement`

## PSM (Platform-Specific Model — AWS)

**Modules:** `awspsm-api.emf`, `awspsm-compute.emf`, `awspsm-core.emf`, `awspsm-events.emf`, `awspsm-identity.emf`, `awspsm-integrations.emf`, `awspsm-messaging.emf`, `awspsm-networking.emf`, `awspsm-observability.emf`, `awspsm-root.emf`, `awspsm-security.emf`, `awspsm-storage.emf`, `awspsm-workflow.emf`  
**Classes:** 206

### `awspsm-api.emf`

#### `ApiGatewayAccessLogSetting`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayApi.accessLogSetting`, `ApiGatewayStage.accessLogSetting`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayApi`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `ApiGatewayDomainName.api`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayApiKey`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayAuthorizer`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `ApiGatewayRoute.authorizer`
- **Incoming `val`:** `ApiGatewayApi.authorizers`
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayBasePathMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayDomainName.mappings`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayDeployment`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `RestApiStage.deployment`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayDomainName`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayApi.apiDomainName`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayIntegration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayIntegrationRequestTemplate`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayIntegration.requestTemplates`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayIntegrationResponseParameter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayIntegration.responseParameters`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayRequestModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayRoute.requestModels`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayRequestValidator`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `RestApiMethod.requestValidator`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayResponseModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayRoute.responseModels`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayRoute`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayApi.routes`
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayRouteSetting`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayRoute.routeSettings`, `ApiGatewayStage.defaultRouteSettings`
- **Inherits:** `kernel.TraceableElement`

#### `ApiGatewayStage`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `ApiGatewayBasePathMapping.stage`
- **Incoming `val`:** `ApiGatewayApi.stages`
- **Inherits:** `awspsmcore.AwsResource`, `awspsmcore.WafAssociableResource`

#### `ApiGatewayTracingConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayApi.tracingConfig`
- **Inherits:** `awspsmcore.TracingConfig`

#### `ApiGatewayUsagePlan`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `ApiGatewayUsagePlanKey`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CognitoAuthorizer`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayAuthorizer`

#### `HttpApi`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayApi`

#### `HttpApiRoute`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayRoute`

#### `HttpApiStage`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayStage`

#### `JwtAuthorizer`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayAuthorizer`

#### `LambdaAuthorizer`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayAuthorizer`

#### `RestApi`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayApi`

#### `RestApiMethod`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayRoute`

#### `RestApiResource`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `RestApiResource.parentResource`
- **Incoming `val`:** `RestApi.resources`
- **Inherits:** `awspsmcore.AwsResource`

#### `RestApiRoute`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayRoute`

#### `RestApiStage`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayApiKey.stages`, `ApiGatewayUsagePlan.apiStages`
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayStage`

#### `WafWebAclAssociation`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayApi.wafAssociation`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `WebSocketApi`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayApi`

#### `WebSocketRoute`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayRoute`

#### `WebSocketStage`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `ApiGatewayStage`

### `awspsm-compute.emf`

#### `AwsLambdaFunction`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayIntegration.lambdaTarget`, `EventBridgePipe.enrichmentFunction`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`, `awspsmcore.S3NotificationDestination`

#### `CodeSigningConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AwsLambdaFunction.codeSigningConfig`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `DynamoDbStreamLambdaEventSourceMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `LambdaEventSourceMapping`

#### `GenericLambdaEventSourceMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `LambdaEventSourceMapping`

#### `LambdaAlias`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `LambdaEventInvokeConfig.qualifierAlias`
- **Incoming `val`:** `AwsLambdaFunction.aliases`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaCodeConfig`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `LambdaDeadLetterConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.deadLetterConfig`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaDestinationConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `LambdaEventInvokeConfig.destinationConfig`, `LambdaEventSourceMapping.destinationConfig`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaEnvironmentVariable`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.environment`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaEventInvokeConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.eventInvokeConfigs`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaEventSourceMapping`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.eventSourceMappings`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaFileSystemConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.fileSystemConfigs`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaFunctionUrl`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.functionUrl`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaImageCodeConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `LambdaCodeConfig`

#### `LambdaImageConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `LambdaImageCodeConfig.imageConfig`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaInvocationBinding`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `LambdaLayerPermission`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaLayerVersion`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AwsLambdaFunction.layers`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaLoggingConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.logging`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaPermission`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayLambdaIntegrationView.permission`, `EventBridgeLambdaTargetView.permission`, `SnsLambdaSubscriptionView.permission`
- **Incoming `val`:** `AwsLambdaFunction.permissions`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaProvisionedConcurrencyConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `LambdaAlias.provisionedConcurrencyConfig`, `LambdaVersion.provisionedConcurrencyConfig`
- **Inherits:** `kernel.TraceableElement`

#### `LambdaTracingConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.tracing`
- **Inherits:** `awspsmcore.TracingConfig`

#### `LambdaUrlCorsConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `LambdaFunctionUrl.cors`
- **Inherits:** `awspsmcore.CorsConfiguration`

#### `LambdaVersion`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `LambdaAlias.version`
- **Incoming `val`:** `AwsLambdaFunction.versions`
- **Inherits:** `awspsmcore.AwsResource`

#### `LambdaZipCodeConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `LambdaCodeConfig`

#### `SamFunctionEvent`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.samEvents`
- **Inherits:** `LambdaInvocationBinding`

#### `SqsLambdaEventSourceMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `LambdaEventSourceMapping`

### `awspsm-core.emf`

#### `AwsNamingPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.namingPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `AwsNativeResource`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsResource`

#### `AwsResource`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** `ApiGatewayRequestModel.schemaResource`, `ApiGatewayResponseModel.schemaResource`, `AslState.invokedResource`, `AwsPsmModel.allResources`, `AwsResource.dependsOn`, `CfnOutput.resource`, `CloudWatchAlarm.alarmActionResources`, `CloudWatchAlarm.insufficientDataActionResources`, `CloudWatchAlarm.monitoredResource`, `CloudWatchAlarm.okActionResources`, `CloudWatchCompositeAlarm.alarmActionResources`, `CloudWatchCompositeAlarm.okActionResources`, `CloudWatchLogSubscriptionFilter.destinationResource`, `CognitoLambdaConfig.createAuthChallenge`, `CognitoLambdaConfig.customMessage`, `CognitoLambdaConfig.defineAuthChallenge`, `CognitoLambdaConfig.postAuthentication`, `CognitoLambdaConfig.postConfirmation`, `CognitoLambdaConfig.preAuthentication`, `CognitoLambdaConfig.preSignUp`, `CognitoLambdaConfig.preTokenGeneration`, `CognitoLambdaConfig.userMigration`, `CognitoLambdaConfig.verifyAuthChallengeResponse`, `EventBridgePipe.sourceResource`, `EventBridgePipe.targetResource`, `EventBridgeTarget.targetResource`, `GenericLambdaEventSourceMapping.eventSourceResource`, `LambdaDestinationConfig.onFailure`, `LambdaDestinationConfig.onSuccess`, `SecretRotationSchedule.rotationLambda`, `SnsSubscription.endpointResource`, `ValueExpression.resource`
- **Incoming `val`:** `SamStack.resources`
- **Inherits:** `kernel.TraceableElement`

#### `AwsSecurityBaseline`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.securityBaseline`
- **Inherits:** `kernel.TraceableElement`

#### `AwsStage`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.stages`
- **Inherits:** `kernel.TraceableElement`

#### `AwsTag`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsResource.tags`, `AwsStage.stageTags`, `AwsTaggingPolicy.requiredTags`
- **Inherits:** `kernel.TraceableElement`

#### `AwsTaggingPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.taggingPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `CfnCondition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SamStack.conditions`
- **Inherits:** `kernel.TraceableElement`

#### `CfnMapping`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SamStack.mappings`
- **Inherits:** `kernel.TraceableElement`

#### `CfnOutput`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SamStack.outputs`
- **Inherits:** `kernel.TraceableElement`

#### `CfnParameter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ValueExpression.parameter`
- **Incoming `val`:** `SamStack.parameters`
- **Inherits:** `kernel.TraceableElement`

#### `CorsConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayApi.cors`, `S3Bucket.cors`
- **Inherits:** `kernel.TraceableElement`

#### `NamedValueExpression`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ValueExpression.entries`
- **Inherits:** `kernel.TraceableElement`

#### `NativeProperty`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsNativeResource.properties`, `AwsResource.metadata`, `AwsResource.overrides`, `SamFunctionEvent.properties`, `SamGlobals.apiGlobals`, `SamGlobals.functionGlobals`, `SamGlobals.httpApiGlobals`, `SamGlobals.stateMachineGlobals`, `SamStack.globals`, `SamStack.metadata`, `SamStateMachineEvent.properties`
- **Inherits:** `kernel.TraceableElement`

#### `ResourceImport`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsResource.importDetails`
- **Inherits:** `kernel.TraceableElement`

#### `SamGlobals`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.samGlobals`
- **Inherits:** `kernel.TraceableElement`

#### `SamStack`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AwsResource.stack`, `AwsStage.deploysStacks`
- **Incoming `val`:** `AwsPsmModel.stacks`
- **Inherits:** `kernel.TraceableElement`

#### `TracingConfig`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `ValueExpression`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CfnCondition.expressionValue`, `CfnParameter.defaultValue`, `EventBridgeTarget.input`, `SecretsManagerSecret.secretValue`, `SsmParameter.value`, `ValueExpression.items`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-events.emf`

#### `AwsRetryPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeTarget.retryPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeApiDestination`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeApiKeyAuthParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeAuthParameters`

#### `EventBridgeArchive`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventBridgeBus.archives`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeAuthParameters`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeBasicAuthParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeAuthParameters`

#### `EventBridgeBatchTargetParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeTargetParameters`

#### `EventBridgeBus`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventBridgeRule.bus`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeBusPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventBridgeBus.policies`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeConnection`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeFlexibleTimeWindow`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeSchedule.flexibleTimeWindow`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeHttpParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeConnection.invocationHttpParameters`, `EventBridgeOAuthParameters.oauthHttpParameters`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeHttpTargetParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeTargetParameters`

#### `EventBridgeInputTransformer`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeTarget.inputTransformer`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeOAuthParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeAuthParameters`

#### `EventBridgePipe`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventBridgeBus.rules`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeSchedule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `EventBridgeSqsTargetParameters`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `EventBridgeTargetParameters`

#### `EventBridgeTarget`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeRule.targets`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeTargetParameters`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeTarget.parameters`
- **Inherits:** `kernel.TraceableElement`

#### `EventPattern`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeRule.eventPattern`
- **Inherits:** `kernel.TraceableElement`

#### `HttpParameter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `EventBridgeHttpParameters.bodyParameters`, `EventBridgeHttpParameters.headerParameters`, `EventBridgeHttpParameters.queryStringParameters`, `EventBridgeHttpTargetParameters.headerParameters`, `EventBridgeHttpTargetParameters.pathParameterValues`, `EventBridgeHttpTargetParameters.queryStringParameters`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-identity.emf`

#### `CognitoAccountRecoverySetting`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPool.accountRecoverySetting`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoEmailConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPool.emailConfiguration`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoIdentityPool`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CognitoLambdaConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPool.lambdaConfig`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoOAuthConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPoolClient.oauthConfiguration`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoPasswordPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPool.passwordPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoRecoveryMechanism`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoAccountRecoverySetting.recoveryMechanisms`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoSchemaAttribute`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CognitoUserPool.schemaAttributes`
- **Inherits:** `kernel.TraceableElement`

#### `CognitoUserPool`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CognitoUserPoolClient`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CognitoAuthorizer.clients`, `CognitoUserPool.clients`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CognitoUserPoolDomain`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CognitoUserPool.domain`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CognitoUserPoolGroup`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CognitoUserPool.groups`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

### `awspsm-integrations.emf`

#### `ApiGatewayLambdaIntegrationView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `AwsRelationshipView`

- **Abstract:** Yes (abstract)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsPsmModel.relationshipViews`
- **Inherits:** `kernel.TraceableElement`

#### `EventBridgeLambdaTargetView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `S3LambdaNotificationView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `S3QueueNotificationView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `S3TopicNotificationView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `SnsLambdaSubscriptionView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `SqsLambdaEventSourceView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

#### `StepFunctionEventBridgeTargetView`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `AwsRelationshipView`

### `awspsm-messaging.emf`

#### `SnsFilterRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SnsSubscription.filterRules`
- **Inherits:** `kernel.TraceableElement`

#### `SnsSubscription`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SnsTopic.subscriptions`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SnsTopic`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `LambdaDeadLetterConfig.targetTopic`, `SnsTopicPolicy.topics`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`, `awspsmcore.S3NotificationDestination`

#### `SnsTopicPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `S3TopicNotificationView.topicPolicy`, `SnsTopic.topicPolicies`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SqsQueue`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `EventBridgeTarget.deadLetterQueue`, `LambdaDeadLetterConfig.targetQueue`, `SnsSubscription.deadLetterQueue`, `SqsQueuePolicy.queues`, `SqsRedriveAllowPolicy.sourceQueues`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`, `awspsmcore.S3NotificationDestination`

#### `SqsQueuePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `S3QueueNotificationView.queuePolicy`, `SqsQueue.queuePolicies`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SqsRedriveAllowPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SqsQueue.redriveAllowPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `SqsRedrivePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SqsQueue.redrivePolicy`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-networking.emf`

#### `SecurityGroup`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SecurityGroupRule.sourceSecurityGroup`, `VpcAttachmentConfig.securityGroups`, `VpcEndpoint.securityGroups`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SecurityGroupRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SecurityGroup.egressRules`, `SecurityGroup.ingressRules`
- **Inherits:** `kernel.TraceableElement`

#### `Subnet`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `VpcAttachmentConfig.subnets`, `VpcEndpoint.subnets`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `Vpc`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SecurityGroup.vpc`, `Subnet.vpc`, `VpcAttachmentConfig.vpc`, `VpcEndpoint.vpc`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `VpcAttachmentConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AwsLambdaFunction.vpcConfig`
- **Inherits:** `kernel.TraceableElement`

#### `VpcEndpoint`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `VpcEndpointReference`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `VpcAttachmentConfig.requiredEndpoints`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-observability.emf`

#### `CloudWatchAlarm`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchCompositeAlarm`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchDashboard`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchLogGroup`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayAccessLogSetting.destinationLogGroup`, `ApiGatewayApi.accessLogGroup`, `ApiGatewayStage.accessLogGroup`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchLogSubscriptionFilter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CloudWatchLogGroup.subscriptionFilters`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchMetricFilter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `CloudWatchLogGroup.metricFilters`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `CloudWatchMetricTransformation`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CloudWatchMetricFilter.metricTransformations`
- **Inherits:** `kernel.TraceableElement`

#### `MetricDimension`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `CloudWatchAlarm.dimensions`, `CloudWatchMetricTransformation.dimensions`, `MetricPolicy.dimensions`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-root.emf`

#### `AwsPsmModel`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

### `awspsm-security.emf`

#### `GenerateSecretStringConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SecretsManagerSecret.generateSecretString`
- **Inherits:** `kernel.TraceableElement`

#### `IamCondition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `IamStatement.conditions`
- **Inherits:** `kernel.TraceableElement`

#### `IamInlinePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `IamRole.inlinePolicies`
- **Inherits:** `kernel.TraceableElement`

#### `IamManagedPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `IamRole.managedPolicies`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `IamPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `IamPolicyDocument`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `ApiGatewayApi.apiPolicy`, `DynamoDbTable.resourcePolicy`, `EventBridgeBusPolicy.policyDocument`, `KmsKey.keyPolicy`, `S3BucketPolicy.policyDocument`, `SecretsManagerResourcePolicy.resourcePolicy`, `SnsTopicPolicy.policyDocument`, `SqsQueuePolicy.policyDocument`
- **Inherits:** `kernel.TraceableElement`

#### `IamPrincipal`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `IamStatement.principals`
- **Inherits:** `kernel.TraceableElement`

#### `IamRole`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayIntegration.credentialsRole`, `CloudWatchLogSubscriptionFilter.role`, `CognitoUserPoolGroup.role`, `EventBridgeTarget.role`, `IamPolicy.roles`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `IamStatement`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `IamPolicyDocument.statements`
- **Inherits:** `kernel.TraceableElement`

#### `KmsAlias`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `KmsKey.aliases`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `KmsKey`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AwsLambdaFunction.kmsKey`, `CloudWatchLogGroup.kmsKey`, `DynamoDbReplicaSpecification.kmsKey`, `DynamoDbSseSpecification.kmsKey`, `DynamoDbTable.kmsKey`, `S3BucketEncryption.kmsKey`, `S3ReplicationDestination.replicaKmsKey`, `SecretsManagerSecret.kmsKey`, `SnsTopic.kmsKey`, `SqsQueue.kmsKey`, `SsmParameter.kmsKey`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SecretRotationRules`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `SecretRotationSchedule.rotationRules`
- **Inherits:** `kernel.TraceableElement`

#### `SecretRotationSchedule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SecretsManagerSecret.rotationSchedule`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SecretValueExpression`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.ValueExpression`

#### `SecretsManagerResourcePolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SecretsManagerSecret.resourcePolicy`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SecretsManagerSecret`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SecretValueExpression.secretRef`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SsmParameter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `SsmParameterValueExpression.ssmParameter`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `SsmParameterValueExpression`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.ValueExpression`

### `awspsm-storage.emf`

#### `DynamoDbAttributeDefinition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.attributeDefinitions`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbBackupPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.backupPolicy`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbGlobalSecondaryIndex`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.globalSecondaryIndexes`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbKeySchemaElement`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbGlobalSecondaryIndex.keySchema`, `DynamoDbLocalSecondaryIndex.keySchema`, `DynamoDbTable.keySchema`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbLocalSecondaryIndex`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.localSecondaryIndexes`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbOnDemandThroughput`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbGlobalSecondaryIndex.onDemandThroughput`, `DynamoDbTable.onDemandThroughput`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbProjection`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbProvisionedThroughput`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbGlobalSecondaryIndex.provisionedThroughput`, `DynamoDbTable.provisionedThroughput`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbReplicaSpecification`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.replicas`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbSseSpecification`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.sseSpecification`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbStreamSpecification`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.streamSpecification`
- **Inherits:** `kernel.TraceableElement`

#### `DynamoDbTable`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `DynamoDbTimeToLiveSpecification`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `DynamoDbTable.timeToLiveSpecification`
- **Inherits:** `kernel.TraceableElement`

#### `S3Bucket`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `S3BucketEncryption`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.encryption`
- **Inherits:** `kernel.TraceableElement`

#### `S3BucketPolicy`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `S3Bucket.bucketPolicy`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `S3LifecycleConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.lifecycle`
- **Inherits:** `kernel.TraceableElement`

#### `S3LifecycleFilter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3LifecycleRule.filter`, `S3ReplicationRule.filter`
- **Inherits:** `kernel.TraceableElement`

#### `S3LifecycleRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3LifecycleConfiguration.rules`
- **Inherits:** `kernel.TraceableElement`

#### `S3NotificationConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.notificationConfiguration`
- **Inherits:** `kernel.TraceableElement`

#### `S3NotificationRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3NotificationConfiguration.rules`
- **Inherits:** `kernel.TraceableElement`

#### `S3OwnershipControls`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.ownershipControls`
- **Inherits:** `kernel.TraceableElement`

#### `S3OwnershipRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3OwnershipControls.rules`
- **Inherits:** `kernel.TraceableElement`

#### `S3PublicAccessBlockConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.publicAccessBlock`
- **Inherits:** `kernel.TraceableElement`

#### `S3ReplicationConfiguration`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3Bucket.replicationConfiguration`
- **Inherits:** `kernel.TraceableElement`

#### `S3ReplicationDestination`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3ReplicationRule.destination`
- **Inherits:** `kernel.TraceableElement`

#### `S3ReplicationRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3ReplicationConfiguration.rules`
- **Inherits:** `kernel.TraceableElement`

#### `S3TagFilter`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3LifecycleFilter.tags`
- **Inherits:** `kernel.TraceableElement`

#### `S3Transition`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `S3LifecycleRule.transitions`
- **Inherits:** `kernel.TraceableElement`

### `awspsm-workflow.emf`

#### `AslBranch`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AslMapConfig.itemProcessor`, `AslState.branches`
- **Inherits:** `kernel.TraceableElement`

#### `AslCatchRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AslState.catch`
- **Inherits:** `kernel.TraceableElement`

#### `AslChoiceRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AslState.choices`
- **Inherits:** `kernel.TraceableElement`

#### `AslDocument`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `StepFunctionStateMachine.aslDocument`
- **Inherits:** `kernel.StructuredDocument`

#### `AslMapConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AslState.mapConfig`
- **Inherits:** `kernel.TraceableElement`

#### `AslRetryRule`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `AslState.retry`
- **Inherits:** `kernel.TraceableElement`

#### `AslState`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `AslState.nextState`
- **Incoming `val`:** `AslBranch.states`, `AslDocument.states`
- **Inherits:** `kernel.TraceableElement`

#### `SamStateMachineEvent`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `StepFunctionStateMachine.samEvents`
- **Inherits:** `kernel.TraceableElement`

#### `StepFunctionLoggingConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `StepFunctionStateMachine.logging`
- **Inherits:** `kernel.TraceableElement`

#### `StepFunctionStateMachine`

- **Abstract:** No (concrete)
- **Incoming `ref`:** `ApiGatewayIntegration.stateMachineTarget`
- **Incoming `val`:** _none_
- **Inherits:** `awspsmcore.AwsResource`

#### `StepFunctionTracingConfig`

- **Abstract:** No (concrete)
- **Incoming `ref`:** _none_
- **Incoming `val`:** `StepFunctionStateMachine.tracing`
- **Inherits:** `awspsmcore.TracingConfig`
