# Metamodel Relations

## Shared Kernel

```mermaid
classDiagram
    class ModelElement {
        id
        name
        summary
        description
        modelTags
        lifecycleStatus
    }
    class TraceableElement {
        sourceReference
        traceId
        generatedFrom
        rationale
        reviewStatus
    }
    class SemanticRelationship
    class TraceModel
    class TraceLink {
        linkType
        confidence
        sourceElementId
        targetElementId
    }
    class ProductionReadinessAssessment
    class ReadinessFinding
    class ReadinessCheck
    class ManualDecision
    class StructuredDocument
    class TransformationAssumption

    ModelElement <|-- TraceableElement
    TraceableElement <|-- SemanticRelationship
    TraceableElement <|-- TraceModel
    TraceableElement <|-- TraceLink
    TraceableElement <|-- ProductionReadinessAssessment
    TraceableElement <|-- ReadinessFinding
    TraceableElement <|-- ReadinessCheck
    TraceableElement <|-- ManualDecision
    TraceableElement <|-- StructuredDocument
    TraceableElement <|-- TransformationAssumption
    TraceModel "1" *-- "*" TraceLink
    ProductionReadinessAssessment "1" *-- "*" ReadinessFinding
    ProductionReadinessAssessment "1" *-- "*" ReadinessCheck
    ProductionReadinessAssessment "1" *-- "*" ManualDecision
```

## CIM Root Containment

```mermaid
classDiagram
    class CIMModel
    class Requirement
    class RequirementRelationship
    class BusinessGoal
    class KPI
    class Stakeholder
    class Actor
    class Role
    class BusinessCapability
    class CapabilityDependency
    class BoundedContextCandidate
    class DomainEntity
    class ValueObject
    class DomainRelationship
    class AggregateCandidate
    class InformationItem
    class DataClassification
    class Command
    class Query
    class BusinessEvent
    class BusinessError
    class Condition
    class BusinessProcess
    class Policy
    class DecisionTable
    class Risk
    class Assumption
    class Hotspot
    class TransformationProfile
    class TraceModel
    class ProductionReadinessAssessment

    CIMModel *-- "*" Requirement
    CIMModel *-- "*" RequirementRelationship
    CIMModel *-- "*" BusinessGoal
    CIMModel *-- "*" KPI
    CIMModel *-- "*" Stakeholder
    CIMModel *-- "*" Actor
    CIMModel *-- "*" Role
    CIMModel *-- "*" BusinessCapability
    CIMModel *-- "*" CapabilityDependency
    CIMModel *-- "*" BoundedContextCandidate
    CIMModel *-- "*" DomainEntity
    CIMModel *-- "*" ValueObject
    CIMModel *-- "*" DomainRelationship
    CIMModel *-- "*" AggregateCandidate
    CIMModel *-- "*" InformationItem
    CIMModel *-- "*" DataClassification
    CIMModel *-- "*" Command
    CIMModel *-- "*" Query
    CIMModel *-- "*" BusinessEvent
    CIMModel *-- "*" BusinessError
    CIMModel *-- "*" Condition
    CIMModel *-- "*" BusinessProcess
    CIMModel *-- "*" Policy
    CIMModel *-- "*" DecisionTable
    CIMModel *-- "*" Risk
    CIMModel *-- "*" Assumption
    CIMModel *-- "*" Hotspot
    CIMModel *-- "0..1" TransformationProfile
    CIMModel *-- "0..1" TraceModel
    CIMModel *-- "0..1" ProductionReadinessAssessment
```

## PIM Root Containment

```mermaid
classDiagram
    class PIMModel
    class ServerlessService
    class ServiceElementMembership
    class DeploymentUnit
    class Environment
    class ImplementationProfile
    class PlatformCapability
    class PlatformMappingAssessment
    class Schema
    class BusinessRule
    class DecisionModel
    class Function
    class Api
    class ApiRoute
    class Trigger
    class EventType
    class EventChannel
    class DataStore
    class ObjectStore
    class Workflow
    class ExternalEndpoint
    class ExternalAdapter
    class IdentityProvider
    class Principal
    class ArchitecturePolicy
    class Flow
    class DataAccess
    class HumanTask
    class EscalationPolicy
    class ConfigurationSet
    class Secret

    PIMModel *-- "*" ServerlessService
    PIMModel *-- "*" ServiceElementMembership
    PIMModel *-- "*" DeploymentUnit
    PIMModel *-- "*" Environment
    PIMModel *-- "0..1" ImplementationProfile
    PIMModel *-- "*" PlatformCapability
    PIMModel *-- "*" PlatformMappingAssessment
    PIMModel *-- "*" Schema
    PIMModel *-- "*" EventType
    PIMModel *-- "*" BusinessRule
    PIMModel *-- "*" DecisionModel
    PIMModel *-- "*" ExternalEndpoint
    PIMModel *-- "*" IdentityProvider
    PIMModel *-- "*" Principal
    PIMModel *-- "*" ArchitecturePolicy
    PIMModel *-- "*" Flow
    PIMModel *-- "*" DataAccess
    PIMModel *-- "*" HumanTask
    PIMModel *-- "*" EscalationPolicy
    PIMModel *-- "*" ConfigurationSet
    PIMModel *-- "*" Secret
    ServerlessService *-- "*" Function
    ServerlessService *-- "*" Api
    ServerlessService *-- "*" EventChannel
    ServerlessService *-- "*" DataStore
    ServerlessService *-- "*" ObjectStore
    ServerlessService *-- "*" Workflow
    ServerlessService *-- "*" ExternalAdapter
    Api *-- "*" ApiRoute
    Function *-- "*" Trigger
```

## AWS PSM Root and Resource Families

```mermaid
classDiagram
    class AwsPsmModel
    class AwsResource
    class AwsStage
    class SamStack
    class AwsLambdaFunction
    class ApiGatewayApi
    class DynamoDbTable
    class S3Bucket
    class SqsQueue
    class SnsTopic
    class EventBridgeBus
    class StepFunctionStateMachine
    class IamRole
    class KmsKey
    class CognitoUserPool
    class CloudWatchLogGroup
    class AwsRelationshipView
    class StructuredDocument
    class SamGlobals
    class AwsNamingPolicy
    class AwsTaggingPolicy
    class AwsSecurityBaseline
    class TraceModel
    class ProductionReadinessAssessment

    AwsPsmModel *-- "1..*" AwsStage
    AwsPsmModel *-- "1..*" SamStack
    AwsPsmModel *-- "0..1" SamGlobals
    AwsPsmModel *-- "0..1" AwsNamingPolicy
    AwsPsmModel *-- "0..1" AwsTaggingPolicy
    AwsPsmModel *-- "0..1" AwsSecurityBaseline
    AwsPsmModel *-- "0..1" TraceModel
    AwsPsmModel *-- "0..1" ProductionReadinessAssessment
    AwsPsmModel *-- "*" AwsRelationshipView
    AwsPsmModel *-- "*" StructuredDocument
    AwsResource <|-- AwsLambdaFunction
    AwsResource <|-- ApiGatewayApi
    AwsResource <|-- DynamoDbTable
    AwsResource <|-- S3Bucket
    AwsResource <|-- SqsQueue
    AwsResource <|-- SnsTopic
    AwsResource <|-- EventBridgeBus
    AwsResource <|-- StepFunctionStateMachine
    AwsResource <|-- IamRole
    AwsResource <|-- KmsKey
    AwsResource <|-- CognitoUserPool
    AwsResource <|-- CloudWatchLogGroup
    SamStack *-- "*" AwsResource
```

## Package Import Relations

```mermaid
flowchart LR
    kernel["shared/kernel"]
    cimRoot["cim-root"]
    cimOrg["cim-organization"]
    cimDomain["cim-domain-data"]
    cimBehavior["cim-behavior"]
    cimProcess["cim-process-policy"]
    cimGov["cim-governance"]
    pimRoot["pim-root"]
    pimCompute["pim-compute"]
    pimApi["pim-api"]
    pimData["pim-data"]
    pimIntegration["pim-integration"]
    pimWorkflow["pim-workflow"]
    pimPolicy["pim-policy/security/config"]
    psmRoot["awspsm-root"]
    psmCore["awspsm-core"]
    psmCompute["awspsm-compute"]
    psmApi["awspsm-api"]
    psmStorage["awspsm-storage"]
    psmMessaging["awspsm-messaging/events"]
    psmSecurity["awspsm-security/identity"]
    psmWorkflow["awspsm-workflow"]

    kernel --> cimRoot
    kernel --> pimRoot
    kernel --> psmRoot
    cimRoot --> cimOrg
    cimRoot --> cimDomain
    cimRoot --> cimBehavior
    cimRoot --> cimProcess
    cimRoot --> cimGov
    pimRoot --> pimCompute
    pimRoot --> pimApi
    pimRoot --> pimData
    pimRoot --> pimIntegration
    pimRoot --> pimWorkflow
    pimRoot --> pimPolicy
    psmRoot --> psmCore
    psmRoot --> psmCompute
    psmRoot --> psmApi
    psmRoot --> psmStorage
    psmRoot --> psmMessaging
    psmRoot --> psmSecurity
    psmRoot --> psmWorkflow
```
