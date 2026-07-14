# AI Assistant Durable Turn and Checkpoint Lifecycle

## Turn Flow

```mermaid
flowchart TD
    start([User sends message])
    auth["Authenticate and resolve session"]
    attach["Resolve uploaded/inline attachments"]
    idem{"Existing idempotency key?"}
    existing["Return existing turn acceptance"]
    ensure["Ensure active model and starter revision"]
    create["Create QUEUED assistant_turn"]
    checkpoint["Save starter checkpoint and model.checkpoint event"]
    accepted["Return 202 TurnAcceptedResponse"]
    claim["Worker claims turn"]
    run["Run AgentTurnLoop with metamodel-checked tools"]
    state{"Outcome"}
    commit["Commit validated model revision"]
    saveCheckpoint["Save checkpoint, provenance, provider calls, events"]
    terminal["Persist terminal state and final message"]
    controls["Client polls status or replays SSE events"]

    start --> auth --> attach --> idem
    idem -- yes --> existing --> controls
    idem -- no --> ensure --> create --> checkpoint --> accepted --> controls
    create --> claim --> run --> state
    state -- committed work --> commit --> saveCheckpoint --> terminal
    state -- needs input/confirmation/partial/failure --> terminal
    terminal --> controls
```

## Durable Turn States

```mermaid
stateDiagram-v2
    [*] --> QUEUED
    QUEUED --> RUNNING
    RUNNING --> SUCCEEDED
    RUNNING --> PARTIAL
    RUNNING --> NEEDS_INPUT
    RUNNING --> NEEDS_CONFIRMATION
    RUNNING --> CONFLICTED
    RUNNING --> CANCELLED
    RUNNING --> TIMED_OUT
    RUNNING --> FAILED
    SUCCEEDED --> [*]
    PARTIAL --> [*]
    NEEDS_INPUT --> [*]
    NEEDS_CONFIRMATION --> [*]
    CONFLICTED --> [*]
    CANCELLED --> [*]
    TIMED_OUT --> [*]
    FAILED --> [*]
```

## Checkpoint Undo

```mermaid
sequenceDiagram
    actor User
    participant UI as Chat panel
    participant API as ChatbotController
    participant Undo as DurableTurnUndoService
    participant M as ModelService
    participant DB as PostgreSQL

    User->>UI: Undo completed turn checkpoint
    UI->>API: POST /api/chatbot/turns/{turnId}/undo
    API->>Undo: undo(user, turn)
    Undo->>DB: Load turn checkpoints and inverse patch
    Undo->>M: Apply inverse against current model revision
    M->>DB: Persist new model revision
    Undo-->>API: modelId, revision
    API-->>UI: TurnUndoResponse
    UI->>API: reload model if needed
```
