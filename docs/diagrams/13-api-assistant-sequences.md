# Assistant API and Durable SSE Sequences

## POST `/api/chatbot/sessions`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant P as ProjectService
    participant A as AgenticAssistantFacade
    participant S as AssistantSessionStore
    Client->>C: X-Auth-Token + projectId + modelType + modelName + optional resumeSessionId/forceNew
    C->>P: Verify project access
    C->>A: startSession(user, projectId, level, modelName, resumeSessionId, forceNew)
    A->>S: create or resume durable thread/session
    S-->>A: AssistantSession
    A-->>C: AssistantSession
    C-->>Client: sessionId, modelId null
```

## POST `/api/chatbot/sessions/{sessionId}/messages`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant A as AgenticAssistantFacade
    participant U as UploadService
    participant T as AssistantTurnStore
    participant W as DurableAssistantTurnWorker

    Client->>C: message, modelId, expectedRevision/revision, selectedElementIds, attachmentIds, idempotencyKey
    C->>A: session(user, sessionId)
    C->>U: resolve uploaded or inline attachments
    alt duplicate idempotencyKey
        C->>T: findByIdempotency(sessionId, key)
        T-->>C: existing turn
        C-->>Client: 202 TurnAcceptedResponse(existing)
    else new durable turn
        C->>A: ensureModel(user, sessionId, modelId)
        A-->>C: starter model id/revision
        C->>T: create QUEUED AssistantTurn
        C->>T: save starter checkpoint and model.checkpoint event
        C-->>Client: 202 TurnAcceptedResponse(turnId, state, modelId, revision, cursor)
        W-->>T: asynchronously claim and process queued turn
    end
```

## GET `/api/chatbot/turns/{turnId}/events`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant T as AssistantTurnStore
    participant E as SseEmitter
    Client->>C: X-Auth-Token + Last-Event-ID or eventCursor
    C->>T: find(turnId) and verify owner
    C->>E: open 30 minute text/event-stream
    loop replay every 250 ms until terminal
        C->>T: events(turnId, cursor)
        T-->>C: events after cursor
        C-->>Client: id, event name, AssistantTurn.Event payload
    end
    C-->>Client: complete stream when turn is terminal
```

## Turn Control Endpoints

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant T as AssistantTurnStore
    participant Undo as DurableTurnUndoService

    Client->>C: GET /api/chatbot/turns/{turnId}
    C->>T: find, checkpoints, provenance
    C-->>Client: TurnStatusResponse

    Client->>C: POST /api/chatbot/turns/{turnId}/cancel
    C->>T: requestCancellation + audit
    C-->>Client: 202 Accepted

    Client->>C: POST /api/chatbot/turns/{turnId}/continue
    C->>T: create follow-up QUEUED turn from terminal turn
    C-->>Client: 202 TurnAcceptedResponse

    Client->>C: POST /api/chatbot/turns/{turnId}/confirm
    C->>T: create confirmed destructive follow-up turn
    C-->>Client: 202 TurnAcceptedResponse

    Client->>C: POST /api/chatbot/turns/{turnId}/undo
    C->>Undo: apply checkpoint inverse
    Undo-->>C: modelId, revision
    C-->>Client: TurnUndoResponse
```

## Attachments, Thread, and Clear

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant A as AgenticAssistantFacade
    participant P as ProjectService
    participant U as UploadService

    Client->>C: POST /api/chatbot/sessions/{sessionId}/attachments multipart file
    C->>A: session(user, sessionId)
    C->>P: verify project access
    C->>U: uploadAssistantAttachment(scope, file)
    U-->>C: UploadedFileRecord
    C-->>Client: AttachmentResponse

    Client->>C: GET /api/chatbot/sessions/{sessionId}/thread
    C->>A: thread(user, sessionId)
    A-->>C: messages, workflowState, provider metadata
    C-->>Client: ThreadResponse

    Client->>C: DELETE /api/chatbot/sessions/{sessionId}
    C->>A: clear(user, sessionId)
    C-->>Client: empty response
```
