# Assistant API, SSE, and WebSocket Sequences

## POST `/api/chatbot/sessions`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant P as ProjectService
    participant O as AssistantOrchestrator
    participant MR as AssistantMemoryRepository
    participant S as AssistantSessionStore
    Client->>C: token + projectId + modelType + modelName
    C->>P: Verify project access
    C->>O: startSession(user, projectId, level, title)
    O->>P: Load project and active model id
    opt active model exists
        O->>O: Load current model revision
    end
    O->>MR: ensureThread(user, project, level, title, modelId, revision)
    O->>S: create runtime session
    O-->>C: AssistantSession
    C-->>Client: sessionId, modelId null
```

## POST `/api/chatbot/sessions/{sessionId}/messages`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Hard as AssistantHardeningService
    participant Mem as AssistantMemoryRepository
    participant ChatMem as SpringAiChatMemoryService
    participant Models as ModelService
    participant Ctx as AssistantModelContextIndexService
    participant Cat as AssistantCatalogService
    participant Provider as AssistantModelProvider
    participant Patch as AssistantPatchCompiler
    participant RT as AssistantRealtimeHub

    Client->>C: message, modelId, revision, activeView, selections, draftPatch
    C->>O: handleMessage(user, sessionId, turnRequest)
    O->>Hard: checkRateLimit(userId)
    O->>Mem: append USER message
    O->>ChatMem: appendUser(threadId, content)
    alt AI disabled or provider unavailable
        O->>Mem: append disabled ASSISTANT message
        O->>RT: publish chat.assistant
        O-->>C: Explained response
    else AI enabled
        O->>Models: Resolve and load active model
        O->>Models: Validate stored model
        O->>Ctx: Snapshot compact context and cache by revision
        O->>Cat: Retrieve metamodel/EVL snippets
        O->>Provider: complete(RESPONDER prompt)
        Provider-->>O: Assistant explanation
        opt mode allows proposals
            O->>Provider: proposePatch(PLANNER prompt)
            Provider-->>O: SemanticModelPatch
            O->>O: Validate semantic grounding and operation limit
            O->>Patch: compile semantic patch to JSON patch + inverse
            O->>Patch: apply patch to preview model
            O->>Models: Validate preview
            alt low risk and GUARDED_APPLY
                O->>Models: patch stored model with expected revision
                O->>RT: publish model.updated
            else approval needed
                O->>Mem: save proposal with PROPOSED status
            end
        end
        O->>Mem: append ASSISTANT message and audit records
        O->>ChatMem: appendAssistant(threadId, content)
        O->>Mem: update rolling summary
        O->>RT: publish chat.assistant
        O-->>C: AssistantTurnResponse
    end
    C-->>Client: MessageResponse
```

## GET `/api/chatbot/sessions/{sessionId}/events`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant RT as AssistantRealtimeHub
    participant E as SseEmitter
    Client->>C: Open EventSource stream
    C->>RT: registerSse(sessionId, emitter)
    RT->>E: Track completion, timeout, error cleanup
    C->>RT: publish assistant.ready
    RT-->>Client: named SSE event assistant.ready
    loop Later assistant events
        RT-->>Client: chat.assistant, model.updated, proposal.rejected, assistant.choice
    end
```

## WebSocket `/ws/chatbot/sessions/{sessionId}`

```mermaid
sequenceDiagram
    actor Client
    participant Config as ChatbotWebSocketConfig
    participant H as ChatbotWebSocketHandler
    participant RT as AssistantRealtimeHub
    Client->>Config: Upgrade request /ws/chatbot/sessions/{id}
    Config->>H: Route accepted origin
    H->>RT: registerWebSocket(sessionId, WebSocketSession)
    H->>RT: publish assistant.ready
    RT-->>Client: JSON AssistantRealtimeEvent
    Client->>H: Optional text message
    H-->>Client: ignored; receive-only transport
    Client-->>H: Close
    H->>RT: unregisterWebSocket(sessionId)
```

## DELETE `/api/chatbot/sessions/{sessionId}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    participant ChatMem as SpringAiChatMemoryService
    participant S as AssistantSessionStore
    Client->>C: token + sessionId
    C->>O: clear(user, sessionId)
    O->>S: require runtime or durable session
    O->>Mem: clearThread(threadId)
    O->>ChatMem: clear(threadId)
    O->>S: clear runtime session
    C-->>Client: Empty response
```

## GET `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    Client->>C: token + sessionId + proposalId
    C->>O: proposal(user, sessionId, proposalId)
    O->>Mem: findProposal(proposalId)
    O->>O: Verify proposal belongs to user/project/level thread
    O-->>C: AssistantProposal
    C-->>Client: AssistantProposal
```

## POST `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/approve`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    participant Models as ModelService
    participant Patch as AssistantPatchCompiler
    participant RT as AssistantRealtimeHub
    Client->>C: token + sessionId + proposalId
    C->>O: approveProposal(...)
    O->>Mem: Load PROPOSED proposal
    O->>Models: Load current model
    O->>Patch: Recompile and preview semantic patch
    O->>Models: Validate preview
    alt mandatory validation passes
        O->>Models: patch model using stored modelRevision
        O->>Mem: status APPLIED and audit APPROVED/APPLIED
        O->>RT: publish model.updated and chat.assistant
        O-->>C: Proposal applied response
    else validation fails
        O->>Mem: status FAILED and audit FAILED
        O-->>C: 422 error
    end
    C-->>Client: MessageResponse or ApiErrorResponse
```

## POST `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/reject`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    participant RT as AssistantRealtimeHub
    Client->>C: token + sessionId + proposalId
    C->>O: rejectProposal(...)
    O->>Mem: Verify proposal ownership
    O->>Mem: status REJECTED and audit REJECTED
    O->>RT: publish proposal.rejected
    C-->>Client: Empty response
```

## POST `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    participant Models as ModelService
    participant Patch as AssistantPatchCompiler
    participant RT as AssistantRealtimeHub
    Client->>C: token + applied proposal id
    C->>O: undoProposal(...)
    O->>Mem: Load APPLIED proposal with inverse patch
    O->>Models: Load current model
    O->>Patch: Apply inverse patch to preview
    O->>Models: Validate preview
    alt mandatory validation passes
        O->>Models: patch current model with inverse patch
        O->>Mem: status UNDONE and audit UNDONE
        O->>RT: publish model.updated undo=true
        O-->>C: Proposal undone response
    else validation fails
        O->>Mem: audit UNDO_FAILED
        O-->>C: 422 error
    end
    C-->>Client: MessageResponse or ApiErrorResponse
```

## POST `/api/chatbot/sessions/{sessionId}/choices`

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant O as AssistantOrchestrator
    participant Mem as AssistantMemoryRepository
    participant RT as AssistantRealtimeHub
    Client->>C: token + choiceId + optionId
    C->>O: submitChoice(user, sessionId, choiceId, optionId)
    O->>Mem: append CHOICE audit
    O->>RT: publish assistant.choice
    C-->>Client: Empty response
```
