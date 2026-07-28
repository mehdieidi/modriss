# AI Modeling Assistant Architecture

## Component Architecture

```mermaid
flowchart TB
    ui["Frontend chat UI<br/>sessions, durable turns, SSE replay, controls"]
    api["ChatbotController<br/>REST and authenticated SSE"]
    worker["DurableAssistantTurnWorker"]
    facade["AgenticAssistantFacade"]
    loop["AgentTurnLoop"]
    codec["AgentActionCodec"]
    tools["AgentModelTools"]
    workspace["ModelWorkspace"]
    schema["AssistantMetamodelSchemaService<br/>Ecore contracts"]
    hard["AssistantHardeningService<br/>rate limit, retry, circuit breaker"]
    prompts["AssistantPromptGuard"]
    provider["ConfiguredAssistantModelProvider"]
    openai["OpenAiCompatibleAssistantModelProvider"]
    gemini["GeminiAssistantModelProvider"]
    nativeTools["Native tool-call adapter<br/>or strict JSON action fallback"]
    models["ModelService + ProjectService"]
    turns["AssistantTurnStore"]
    sessions["AssistantSessionStore"]
    memory["AssistantChatMemory"]
    uploads["UploadService"]
    splitter["SourceUnitSplitter<br/>source units and aliases"]
    undo["DurableTurnUndoService"]
    db[("PostgreSQL")]
    ai["OpenAI-compatible API or Gemini"]

    ui --> api
    api --> facade
    api --> turns
    api --> uploads
    api --> undo
    worker --> turns
    worker --> splitter
    worker --> facade
    facade --> sessions
    facade --> memory
    facade --> loop
    loop --> codec
    loop --> tools --> workspace
    tools --> schema
    tools --> models
    loop --> provider
    provider --> prompts --> hard --> openai --> nativeTools --> ai
    provider --> prompts --> hard --> gemini --> ai
    turns --> db
    sessions --> db
    memory --> db
    uploads --> db
    splitter --> turns
    undo --> models
```

## Assistant Workflow

```mermaid
stateDiagram-v2
    [*] --> QUEUED: POST message accepted
    QUEUED --> RUNNING: worker claims turn
    RUNNING --> SUCCEEDED: validated changes or answer committed
    RUNNING --> PARTIAL: some valid work committed and remaining work recorded
    RUNNING --> NEEDS_INPUT: user input required
    RUNNING --> NEEDS_CONFIRMATION: destructive batch requires confirmation
    RUNNING --> CONFLICTED: expected revision is stale
    RUNNING --> CANCELLED: cancellation requested
    RUNNING --> TIMED_OUT: deadline exceeded
    RUNNING --> FAILED: provider/tool/validation failure
    SUCCEEDED --> QUEUED: continue turn
    PARTIAL --> QUEUED: continue turn
    NEEDS_CONFIRMATION --> QUEUED: confirm turn
```

## Safety Boundary

```mermaid
flowchart LR
    user["User request + selected elements + attachments"]
    contracts["Ecore-derived contracts<br/>types, features, enums, containments"]
    source["Source units + source-document map<br/>for attachments"]
    workspace["In-memory ModelWorkspace"]
    agent["AgentTurnLoop"]
    tools["Validated model tools"]
    guards["Batch guards<br/>UUID ids, evidence checks, containment/reference normalization"]
    validate["Structural validation<br/>semantic EVL checked separately"]
    commit["Revision-checked commit"]
    checkpoint["Checkpoint + inverse patch"]
    storage["Persisted model revision"]

    user --> agent
    source --> agent
    contracts --> tools
    workspace --> tools
    agent --> tools
    tools --> guards --> workspace
    workspace --> validate
    validate --> commit --> storage
    commit --> checkpoint
    contracts -. "not raw EVL files" .- agent
```

Current live status: the source-backed one-story workflow reaches `SUCCEEDED` with 100% coverage and
a saved checkpoint, but semantic validation can still report `CIMModelHasSemanticCore` on the live
persisted model. The next fix is in the JSON/XMI persistence/validation path, not the HTTP turn
lifecycle.
