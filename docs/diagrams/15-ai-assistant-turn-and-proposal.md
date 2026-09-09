# AI assistant durable turn and checkpoint lifecycle

## Turn flow

```mermaid
sequenceDiagram
    participant C as Client
    participant A as ChatbotController
    participant T as AssistantTurnStore
    participant W as DurableAssistantTurnWorker
    participant L as AgentTurnLoop / workflow
    participant P as Arvan Gemma-4-31B-IT
    participant M as ModelService

    C->>A: POST session message + idempotencyKey + expected revision
    A->>T: persist QUEUED turn and accepted event
    A-->>C: 202 turnId, deadlineAt, eventCursor
    W->>T: claim lease, mark RUNNING
    W->>L: execute adaptive workflow
    L->>P: strategy and bounded stage calls
    L->>T: persist workflow plan, work items, calls, events
    L->>M: apply candidate to private workspace
    L->>M: validateStructural(candidate)
    alt obligations and structure pass
        W->>T: atomic checkpoint + inverse patch + resulting revision
        W->>T: SUCCEEDED + final event
    else clarification/confirmation/conflict/partial/failure
        W->>T: terminal state and exact diagnostic
        Note over T,M: Saved model remains unchanged unless a coherent checkpoint was committed.
    end
    C->>A: GET turn status or replay turn SSE
```

## States and controls

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
    PARTIAL --> QUEUED: continue creates child turn
    NEEDS_CONFIRMATION --> QUEUED: confirm
    CONFLICTED --> QUEUED: rebase
```

Controls are cancellation, continuation, destructive confirmation, rebase, turn undo, checkpoint
rollback, and feedback. New durable turns do not use an approve/reject proposal stage. Legacy
proposal detail/undo endpoints remain for compatibility with already-applied proposal records.

Every checkpoint is revision-checked and stores an inverse patch. Undo and rollback create a new
revision; they do not rewrite history.
