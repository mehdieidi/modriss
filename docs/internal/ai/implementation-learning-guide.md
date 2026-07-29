# How the Varka AI Assistant Works

This guide connects the current assistant runtime to the code that implements it. For operator
setup, see [assistant.md](assistant.md). For the public summary, see
[ai-assistant.md](../../public-docs/docs/guides/ai-assistant.md).

The central rule is:

> The LLM can suggest actions, but the backend owns every model read, mutation, validation,
> checkpoint, event, and commit.

## Runtime Shape

Varka uses one durable assistant-turn runtime:

1. `ChatbotController` accepts a session-scoped message and requires a request-body
   `idempotencyKey`.
2. The controller resolves attachments, ensures the active model, creates a `QUEUED`
   `AssistantTurn`, saves a starter checkpoint, and returns `202 Accepted`.
3. `DurableAssistantTurnWorker` claims queued turns and calls `AgenticAssistantFacade`.
4. `AgenticAssistantFacade` runs `AgentTurnLoop` with `AgentModelTools` over a `ModelWorkspace`.
5. Tool calls are checked by `AssistantMetamodelSchemaService` and live Ecore-derived contracts.
6. Valid workspace changes are committed through model services against the expected revision.
7. The backend records events, checkpoints, source provenance, provider-call usage, and final turn
   state.

## Main Code Paths

| Concern                       | Primary classes                                                                                              |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------ |
| HTTP and SSE API              | `apps/backend/.../api/ChatbotController.java`                                                                |
| Durable worker and undo       | `apps/backend/.../assistant/DurableAssistantTurnWorker.java`, `DurableTurnUndoService.java`                  |
| Turn orchestration            | `platform-assistant/.../application/AgenticAssistantFacade.java`                                             |
| Agent loop and action parsing | `platform-assistant/.../agent/AgentTurnLoop.java`, `AgentActionCodec.java`                                   |
| Model tools                   | `platform-assistant/.../tools/AgentModelTools.java`, `workspace/ModelWorkspace.java`                         |
| Metamodel contracts           | `AssistantMetamodelSchemaService`, `MetamodelContractGraph`, `EcoreContractExtractor`                        |
| Providers                     | `ConfiguredAssistantModelProvider`, `OpenAiCompatibleAssistantModelProvider`, `GeminiAssistantModelProvider` |
| Persistence                   | `AssistantTurnStore`, `AssistantSessionStore`, `AssistantChatMemory`, JDBC implementations                   |

## Provider and Configuration

Provider calls are optional and disabled by default with `VARKA_AI_ENABLED=false`. The active
provider is `openai` or `gemini`; `openai-compatible` and `openai_compatible` normalize to the
OpenAI-compatible path. `VARKA_AI_RESPONDER_MODEL` is the active model selector. Planner and
summarizer model variables are accepted for compatibility, but the current resolver uses the
responder model for assistant roles.

`AssistantHardeningService` handles rate limits, retries, circuit breaking, timeouts, and optional
AI-only proxy routing. Provider token streaming is not exposed; progress is represented as durable
turn events.

## Tools and Safety

The LLM does not write the database directly. It asks the backend to execute named actions such as:

- `describe_types`
- `read_model`
- `search_model`
- `create_elements`
- `update_elements`
- `delete_elements`
- `connect_elements`
- `validate_model`
- `plan_work`

Before a mutation changes the workspace, the backend verifies type names, features, enum values,
containment, reference targets, and model level ownership against the Ecore contract graph. Before
a committed model revision is stored, the resulting model is structurally validated and checked
against the expected revision.

Destructive batches move the turn to `NEEDS_CONFIRMATION`; a confirmed follow-up turn is created
through `POST /api/chatbot/turns/{turnId}/confirm`.

## Durable Events

Clients submit work over REST and follow progress with authenticated SSE:

```http
GET /api/chatbot/turns/{turnId}/events
X-Auth-Token: <token>
Last-Event-ID: <optional cursor>
```

Events are stored in `assistant_turn_events`, so clients can reconnect with `Last-Event-ID` or the
`eventCursor` query parameter. Polling `GET /api/chatbot/turns/{turnId}` is the fallback.

## Storage

Assistant migrations live in `packages/java/platform-assistant/src/main/resources/db/assistant-migration`.
The current durable schema is introduced by `V14__assistant_baseline.sql` and refined by V15-V18.

Important tables:

- `assistant_threads`, `assistant_messages`, `assistant_thread_summaries`
- `assistant_turns`, `assistant_turn_events`, `assistant_checkpoints`
- `assistant_source_units`, `assistant_element_provenance`
- `assistant_provider_calls`, `assistant_action_audits`
- `SPRING_AI_CHAT_MEMORY`
- `assistant_rate_limits` (schema reserved; runtime limiter is in memory)

The old assistant proposal, retrieval-document, model-context, and pending-interaction tables are
dropped by the squashed V14 migration. Projects, ordinary models, revisions, and XMI payloads are
not modified by that assistant-state migration.

## Operational Notes

- Restart or rebuild the backend after metamodel changes so Ecore-derived contracts and packaged
  `mde/` assets are fresh.
- Run explicit model validation after EVL changes when reviewing semantic readiness; EVL does not
  gate assistant apply behavior.
- Use `POST /api/chatbot/turns/{turnId}/undo` to apply a checkpoint inverse when available.
- Use `POST /api/chatbot/turns/{turnId}/continue` for partial completed work.
- Use `POST /api/chatbot/turns/{turnId}/cancel` to request cancellation of queued/running work.
