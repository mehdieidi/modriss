# AI Assistant Turn and Autonomous Apply Lifecycle

## Turn Flow

```mermaid
flowchart TD
    start([User sends message])
    rate["Check rate limit and circuit state"]
    session["Resolve runtime or durable assistant session"]
    saveUser["Persist USER message in durable and Spring AI memory"]
    enabled{"AI enabled and provider available?"}
    disabled["Return deterministic disabled/explain response"]
    load["Resolve active model and revision"]
    conflict{"Requested revision stale?"}
    validate["Validate current model"]
    snapshot["Build or load compact model context"]
    retrieve["Retrieve metamodel/EVL snippets and validation issues"]
    plan["Run autonomous planner with retrieved context/tools"]
    compile["Ground, compile, preview, validate"]
    valid{"Valid model change?"}
    apply["Patch model immediately"]
    final["Persist ASSISTANT message, summary, audits"]
    publish["Publish chat.assistant and optional model.updated"]
    done([MessageResponse])

    start --> rate --> session --> saveUser --> enabled
    enabled -- no --> disabled --> final
    enabled -- yes --> load --> conflict
    conflict -- yes --> stale["409 Refresh required"] --> done
    conflict -- no --> validate --> snapshot --> retrieve --> plan --> compile --> valid
    valid -- yes --> apply --> final
    valid -- no --> final
    final --> publish --> done
```

## Applied Change State Machine

```mermaid
stateDiagram-v2
    [*] --> APPLIED: Valid autonomous change applied
    [*] --> FAILED: Validation or patch fails
    APPLIED --> UNDONE: User requests inverse patch and validation passes
    APPLIED --> FAILED: Undo validation fails
    UNDONE --> [*]
    FAILED --> [*]
```

## Semantic Patch Compilation

```mermaid
flowchart LR
    sem["SemanticModelPatch operations"]
    add["ADD_ELEMENT<br/>targetElementId, elementType, attributes"]
    connect["CONNECT_ELEMENTS<br/>sourceElementId, targetElementId, referenceName"]
    set["SET_ATTRIBUTE<br/>targetElementId, referenceName, attributes"]
    delete["DELETE_ELEMENT<br/>targetElementId"]
    compiler["AssistantPatchCompiler"]
    patch["Executable JSON patch"]
    inverse["Inverse JSON patch for undo"]
    affected["Affected element IDs"]

    sem --> add --> compiler
    sem --> connect --> compiler
    sem --> set --> compiler
    sem --> delete --> compiler
    compiler --> patch
    compiler --> inverse
    compiler --> affected
```

## Apply Sequence

```mermaid
sequenceDiagram
    actor User
    participant UI as Chat panel
    participant API as ChatbotController
    participant O as AssistantOrchestrator
    participant M as ModelService
    participant DB as PostgreSQL
    participant RT as RealtimeHub

    User->>UI: Send modeling request
    UI->>API: POST message
    API->>O: handleMessage
    O->>M: Load model
    O->>O: Retrieve context, plan, compile, preview, validate
    O->>M: Patch model
    M->>DB: Persist new revision
    O->>DB: Store applied proposal and audits
    O->>RT: model.updated
    O-->>API: MessageResponse
    API-->>UI: Applied response
    UI->>API: GET updated model when model payload absent
    UI->>UI: Refresh canvas if no newer local edits
```
