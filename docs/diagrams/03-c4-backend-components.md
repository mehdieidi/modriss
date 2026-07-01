# C4 Level 3: Backend Components

```mermaid
flowchart LR
    client["Modeling Frontend"]

    subgraph api["API and Transport"]
        controllers["REST Controllers<br/>Auth, Project, Model, Transformation, Artifact, Modeling, Chatbot"]
        authSupport["AuthSupport"]
        realtime["AssistantRealtimeHub<br/>SSE + WebSocket fan-out"]
        errors["GlobalExceptionHandler"]
        logging["RequestLoggingFilter"]
    end

    subgraph application["Application Services"]
        auth["AuthService"]
        projects["ProjectService"]
        models["ModelService"]
        transformations["TransformationService"]
        jobs["MdeJobService"]
        artifacts["ArtifactService"]
        layouts["LayoutService + StoredViewLayoutService"]
        config["ModelingConfigService"]
        lock["ModelLockService"]
    end

    subgraph assistant["AI Assistant Components"]
        orchestrator["AssistantOrchestrator"]
        provider["ConfiguredAssistantModelProvider"]
        guard["PromptGuard + Hardening"]
        catalog["JdbcAssistantCatalog"]
        context["AssistantModelContextIndexService"]
        patch["AssistantPatchCompiler"]
        memory["AssistantMemoryRepository + SpringAiChatMemoryService"]
    end

    subgraph mde["Formal MDE Runtime"]
        xmi["XmiModelImportService + MetamodelResolver"]
        evl["EpsilonEvlValidator"]
        etl["EpsilonEtlExecutor"]
        egx["EpsilonEgxGenerator"]
    end

    store["Port: PlatformStore"]
    postgres["Adapter: PostgresPlatformStore"]
    db[("PostgreSQL + pgvector")]
    ai["OpenAI-compatible or Gemini"]

    client --> controllers
    client <--> realtime
    controllers --> authSupport --> auth
    controllers --> projects
    controllers --> models
    controllers --> transformations
    controllers --> jobs
    controllers --> artifacts
    controllers --> layouts
    controllers --> config
    controllers --> orchestrator
    errors -.-> controllers
    logging -.-> controllers

    transformations --> models
    transformations --> artifacts
    transformations --> lock
    jobs --> transformations
    layouts --> models
    models --> lock
    models --> xmi
    models --> evl
    transformations --> etl
    transformations --> egx

    orchestrator --> provider --> ai
    orchestrator --> guard
    orchestrator --> catalog
    orchestrator --> context
    orchestrator --> patch
    orchestrator --> memory
    orchestrator --> models
    orchestrator --> projects
    orchestrator --> realtime

    auth --> store
    projects --> store
    models --> store
    transformations --> store
    jobs --> store
    artifacts --> store
    store --> postgres --> db
    catalog --> db
    context --> db
    memory --> db
```
