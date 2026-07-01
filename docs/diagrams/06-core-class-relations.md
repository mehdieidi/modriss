# Core Class Relations

Feature libraries under `packages/java/` own the classes below. Package roots:
`platform.identity.*`, `platform.project.*`, `platform.model.*`, `platform.modeling.*`,
`platform.artifact.*`, `platform.transformation.*`, `platform.storage.*`.

## Application Services and Persistence

```mermaid
classDiagram
    class PlatformStore {
        <<interface>>
        +objectMapper()
        +read(path, type)
        +require(path, type, message)
        +write(path, value)
        +writeBytesAtomically(path, bytes)
        +readBytes(path)
        +deleteIfExists(path)
        +list(directory, type)
        +deleteTree(directory)
    }
    class PostgresPlatformStore
    class AuthService
    class ProjectService
    class ModelService
    class ArtifactService
    class TransformationService
    class MdeJobService
    class ModelLockService
    class StoredViewLayoutService
    class LayoutService
    class XmiModelImportService
    class MetamodelResolver {
        <<interface>>
    }
    class EpsilonEvlValidator
    class EpsilonEtlExecutor
    class EpsilonEgxGenerator

    PlatformStore <|.. PostgresPlatformStore
    AuthService --> PlatformStore
    ProjectService --> PlatformStore
    ProjectService --> AuthService
    ModelService --> PlatformStore
    ModelService --> ProjectService
    ModelService --> ModelLockService
    ModelService --> XmiModelImportService
    ModelService --> MetamodelResolver
    ModelService --> EpsilonEvlValidator
    ArtifactService --> PlatformStore
    ArtifactService --> ProjectService
    TransformationService --> PlatformStore
    TransformationService --> ModelService
    TransformationService --> ArtifactService
    TransformationService --> ModelLockService
    TransformationService --> EpsilonEtlExecutor
    TransformationService --> EpsilonEgxGenerator
    MdeJobService --> PlatformStore
    MdeJobService --> ProjectService
    MdeJobService --> ModelService
    MdeJobService --> TransformationService
    StoredViewLayoutService --> ModelService
    StoredViewLayoutService --> LayoutService
```

## Domain Records

```mermaid
classDiagram
    class UserRecord
    class AuthSession
    class ProjectRecord
    class ProjectMember
    class ModelRecord
    class ArtifactRecord
    class MdeJobRecord
    class ModelLevel {
        <<enumeration>>
        CIM
        PIM
        PSM
    }
    class MdeJobStatus {
        <<enumeration>>
        QUEUED
        RUNNING
        SUCCEEDED
        FAILED
        CANCELLED
    }
    class MdeJobOperation {
        <<enumeration>>
        CIM_TO_PIM
        PIM_TO_PSM
        PSM_TO_ARTIFACT
        VALIDATE
    }

    UserRecord "1" --> "*" AuthSession
    UserRecord "1" --> "*" ProjectRecord : owns
    ProjectRecord "1" --> "*" ProjectMember
    ProjectMember "*" --> "1" UserRecord
    ProjectRecord "1" --> "*" ModelRecord
    ModelRecord --> ModelLevel
    ProjectRecord "1" --> "*" ArtifactRecord
    ProjectRecord "1" --> "*" MdeJobRecord
    MdeJobRecord --> MdeJobStatus
    MdeJobRecord --> MdeJobOperation
```

## Assistant Classes

```mermaid
classDiagram
    class AssistantOrchestrator
    class AssistantModelProvider {
        <<interface>>
        +complete(prompt)
        +proposePatch(prompt)
    }
    class ConfiguredAssistantModelProvider
    class OpenAiCompatibleAssistantModelProvider
    class GeminiAssistantModelProvider
    class JdbcAssistantCatalog
    class AssistantModelContextIndexService
    class AssistantPatchCompiler
    class AssistantMemoryRepository
    class SpringAiChatMemoryService
    class AssistantRealtimeHub
    class AssistantHardeningService
    class AssistantPromptGuard
    class SemanticModelPatch
    class AssistantProposal

    AssistantModelProvider <|.. ConfiguredAssistantModelProvider
    AssistantModelProvider <|.. OpenAiCompatibleAssistantModelProvider
    AssistantModelProvider <|.. GeminiAssistantModelProvider
    ConfiguredAssistantModelProvider --> OpenAiCompatibleAssistantModelProvider
    ConfiguredAssistantModelProvider --> GeminiAssistantModelProvider
    AssistantOrchestrator --> AssistantModelProvider
    AssistantOrchestrator --> JdbcAssistantCatalog
    AssistantOrchestrator --> AssistantModelContextIndexService
    AssistantOrchestrator --> AssistantPatchCompiler
    AssistantOrchestrator --> AssistantMemoryRepository
    AssistantOrchestrator --> SpringAiChatMemoryService
    AssistantOrchestrator --> AssistantRealtimeHub
    AssistantOrchestrator --> AssistantHardeningService
    AssistantModelProvider --> AssistantPromptGuard
    AssistantModelProvider --> AssistantHardeningService
    AssistantPatchCompiler --> SemanticModelPatch
    AssistantOrchestrator --> AssistantProposal
```
