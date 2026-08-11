# How the Varka AI assistant works

This guide maps the current unified runtime to its implementation. The governing rule is:

> The LLM owns modeling semantics; the backend owns contracts, IDs, structural compilation,
> validation, durable state, and persistence.

## Request-to-commit path

1. `ChatbotController` authenticates the session, resolves attachments, requires an
   `idempotencyKey`, ensures a saved model, and creates a `QUEUED` turn.
2. `DurableAssistantTurnWorker` claims the turn under a renewable lease, prepares source units and
   persisted workflow context, and selects `ADAPTIVE`, `RESUME_REPAIR`, or the explicit test route.
3. `AgenticAssistantFacade` and `AgenticTurnService` load the expected model revision into a private
   `ModelWorkspace` and invoke `AgentTurnLoop`.
4. For a fresh unified turn, `AgentTurnLoop.runAdaptive` requests one strict strategy value. Empty
   models permit conceptual generation; non-empty models permit the inspect agent. Both permit an
   `ANSWER` intention.
5. The selected strategy produces a `ModelCommandBatch` or a read-only answer.
6. `AgentModelTools` and `ModelCommandCompiler` apply checked commands to the workspace.
7. `ModelWorkspace` calls the structural validator. A successful candidate is committed against the
   expected revision; rejected work never mutates persistence.
8. The worker finalizes checkpoints, inverse patches, source provenance, provider-call records,
   workflow state, and replayable events before marking the turn terminal.

## Main code paths

| Concern                           | Primary implementation                                                                  |
| --------------------------------- | --------------------------------------------------------------------------------------- |
| REST, attachment, and SSE API     | `apps/backend/.../api/ChatbotController.java`                                           |
| Durable execution and routing     | `apps/backend/.../assistant/DurableAssistantTurnWorker.java`                            |
| Spring wiring                     | `apps/backend/.../assistant/AiConfig.java`                                              |
| Session and turn orchestration    | `AgenticAssistantFacade`, `AgenticTurnService`                                          |
| Adaptive decision and action loop | `agent/AgentTurnLoop.java`, `AgentActionCodec.java`                                     |
| Paper conceptual workflow         | `agent/ConceptualInstanceModelWorkflow.java`                                            |
| Ecore guide and exact contracts   | `MetamodelGuideGenerator`, `TypeContractService`, `MetamodelKnowledgeService`           |
| Commands and workspace tools      | `AgentModelTools`, `ModelCommandCompiler`, `ModelWorkspace`                             |
| Arvan provider protocol           | `OpenAiCompatibleAssistantModelProvider`                                                |
| Provider selection/hardening      | `ConfiguredAssistantModelProvider`, `AssistantHardeningService`, `AssistantPromptGuard` |
| Durable storage                   | `AssistantTurnStore`, session/memory ports, JDBC implementations                        |

## Adaptive routing

The production configuration string is parsed by `DurableAssistantTurnWorker.AssistantMode` and
accepts only `unified`, `agent-test`, or `conceptual-test`. Normal clients cannot set it per request.

`runAdaptive` sends structural facts and the user request to the provider under the
`assistant_strategy` schema. It allows exactly `CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, or
`ANSWER`. One malformed/truncated strategy response can be corrected. The decision and correction
are audited like every other provider call.

The deterministic boundary can remove unsafe strategies but cannot infer business intent:

- `modelEmpty=true` permits conceptual mutation and disallows agent mutation;
- `modelEmpty=false` permits agent mutation and disallows conceptual mutation;
- selected, resumed, and confirmed-destructive work never reaches the conceptual route.

`ANSWER` enters the ordinary `AUTO` action loop. It is prompted to terminate with `answer_user`, but
this is not an enforced read-only boundary: the same mutation actions remain available. The
explicit read-only loop modes are not currently selected by the unified worker.

## Conceptual compilation

`ConceptualInstanceModelWorkflow` performs two LLM tasks through the same provider adapter:

1. `conceptual_type_selection` chooses at most eight exact EClasses.
2. `conceptual_instance_model` produces the complete paper-style instance JSON.

The backend expands the selected types with Ecore construction/required closure. During parsing and
compilation it accepts no unknown EClass, attribute, enum, association, target type, or ID. All
objects are registered before relationships are resolved, then creates are emitted in containment
dependency order. Existing IDs become updates; omitted existing content remains unchanged.

Unique legal root containment can be derived mechanically. Ambiguous or illegal placement, multiple
containment owners, cycles, missing required features, or unresolved references become repair
diagnostics. A repair receives the complete rejected document and must return a corrected complete
document. The conceptual compiler never generates domain fallback content.

## Agent action execution

The inspect/contract path uses a small action protocol, not arbitrary Java tools. Planning can
persist slices and work items. Inspection and `describe_types` return authoritative facts to the
same LLM. `commit_model_batch` is checked against exact contracts and the current inventory.

`AgentAction.Kind` still includes `analyze_source_units`, `plan_cim_blueprint`, and
`plan_source_model` to resume older source workflows. Backend state rejects those actions in normal
new source turns, which use `plan_model_edit`.

Provider `clientRef` values exist only inside a batch. The backend generates persisted UUIDs and
resolves same-batch connections. Deletion requires explicit server-controlled confirmation and
preconditions. Tool failures suitable for repair are converted to compact structured diagnostics.

## Source and continuation state

The worker splits attachments into source units, stores them, and supplies exact IDs to the selected
workflow. The agent path can persist source analysis, source blueprints, modeling plans, and work
items, then resume them through a continuation without replanning. Conceptual generation consumes
the selected source in one bounded complete response and does not currently persist a conceptual
instance ledger.

Coverage is computed from saved `SOURCE_GROUNDED`/`INFERRED` evidence. It must not be interpreted as
EVL validity or human semantic approval.

## Provider details

The production environment uses the Arvan OpenAI-compatible endpoint and
`DeepSeek-V4-Flash`. `VARKA_AI_OPENAI_PROTOCOL=json_schema` uses structured JSON content rather than
native tool calls. The adapter selects a schema based on the requested operation, sets temperature
zero, disables DeepSeek thinking with the required object form, normalizes compatible JSON
envelopes, and reports usage where supplied by the provider.

`ProviderCallBudget` bounds every route. `AssistantHardeningService` supplies request timeout,
retry/circuit-breaker, rate-limit, proxy, and cancellation controls. Failed calls are retained in
durable turn accounting.

## Structural-only validation

The mutation boundary is `ModelWorkspace` and `AgentModelTools.validateModel()`, which reaches
`ModelService.validateStructural(...)`. Assistant code must never substitute full stored/EVL
validation. EVL changes should be tested through explicit model-validation workflows, not assistant
apply tests.

## Operational tracing

Correlate a problem by `assistantTurnId`:

1. Read the durable turn state, final message, workflow kind, phase, and current work item.
2. Replay `assistant_turn_events` to identify the last completed stage.
3. Inspect `assistant_provider_calls` for schema, latency, finish reason, usage, and failure.
4. Inspect validation attempts and checkpoints.
5. For source work, inspect source units, provenance, and coverage before changing prompts.

Do not treat provider connectivity, structural validity, or one successful stochastic run as full
scenario acceptance.
