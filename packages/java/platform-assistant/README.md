# Platform assistant

`platform-assistant` contains the provider-neutral modeling runtime behind Varka's one user-facing
chatbot. Production does not expose an "agent" versus "conceptual" choice. The durable worker
selects an internal workflow and `AgentTurnLoop` performs the bounded LLM decision.

```text
DurableAssistantTurnWorker
  -> AgenticAssistantFacade -> AgenticTurnService -> AgentTurnLoop
       -> adaptive strategy decision
          -> ConceptualInstanceModelWorkflow -> Ecore compiler -> ModelCommandBatch
          -> inspect/contract action loop -> AgentModelTools -> ModelCommandBatch
          -> ANSWER intention -> ordinary AUTO action loop
       -> ModelWorkspace -> structural validation -> revision-checked commit
```

## Production strategy rules

The LLM returns one allowlisted strategy value: `CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, or
`ANSWER`. Deterministic facts restrict the legal set; request keywords do not route work.

- An empty model can select conceptual generation or the `ANSWER` intention.
- A non-empty model can select the inspect/contract agent or the `ANSWER` intention.
- Selected-element, resumed durable, and confirmed-destructive turns bypass conceptual generation.
- `agent-test` and `conceptual-test` are acceptance-test overrides. `unified` is the only normal
  production mode, and unknown configuration values fail startup.

The production restriction of conceptual generation to empty models reflects current live evidence:
the conceptual workflow works well for bounded empty-model generation but has not reliably passed
persisted-model feature evolution.

Implementation caveat: `ANSWER` currently maps to `WorkflowMode.AUTO`, the same ordinary action
loop used by `INSPECT_AGENT`; it is not a hard read-only capability boundary. The prompt should end
with `answer_user`, but the backend does not yet prevent that AUTO loop from proposing a mutation.
Explicit `EXPLAIN_MODEL`/`EXPLAIN_METAMODEL` modes are read-only, but the unified worker does not
currently route fresh turns to them.

## Inspect/contract action path

The provider-visible action interface is:

- `plan_model_edit`
- `inspect_model`
- `describe_types`
- `commit_model_batch`
- `answer_user`
- `ask_user`

`AgentAction` also retains `analyze_source_units`, `plan_cim_blueprint`, and `plan_source_model` so
older persisted source workflows can resume. Normal new source turns are instructed to use
`plan_model_edit`; the legacy actions are accepted only in their bounded workflow states.

The backend owns IDs, exact Ecore contracts, containment and reference checks, source evidence,
destructive confirmation, workspace mutation, validation, and persistence. Provider `clientRef`
values are temporary same-batch references; persisted element IDs are backend-generated UUIDs.

## Conceptual path

`ConceptualInstanceModelWorkflow` implements the paper-style JSON intermediate representation:

1. A structured LLM pass selects at most eight exact EClasses from the authoritative type index.
2. The backend adds deterministic containment and required-reference closure and renders compact
   Ecore contracts.
3. The LLM returns a complete conceptual JSON object keyed by temporary or persisted instance IDs.
4. Each object contains its exact `type`, attribute triples, parent-owned compositions,
   non-containment references, and optional source evidence.
5. The deterministic compiler resolves IDs and dependencies and emits a `ModelCommandBatch`.
6. Compiler or structural diagnostics can trigger a bounded corrected-complete-response attempt.

The compiler does not invent domain objects, attribute values, or relationships. Conceptual output
cannot delete or move persisted objects, and omission never means deletion. The current response
boundary is ten objects for ordinary generation and eight for source-backed generation; this is
not yet a multi-response conceptual staging implementation.

## Provider and validation boundary

The deployed provider is the Arvan OpenAI-compatible endpoint with `DeepSeek-V4-Flash`, JSON-content
structured output, temperature zero, native tools disabled, and
`thinking: {"type":"disabled"}`. Both internal strategies use the same
`OpenAiCompatibleAssistantModelProvider`; there is no conceptual-specific provider adapter.

Assistant create, repair, apply, and commit paths are gated only by
`ModelService.validateStructural(...)`. They do not call EVL, stored semantic-validation endpoints,
or `ModelService.validate(...)`. EVL remains available only through explicit user-initiated model
validation outside the chatbot.

Source attachments are split into durable source units. Committed evidence is persisted as
`SOURCE_GROUNDED` or `INFERRED`, and incomplete source accounting cannot be reported as successful
completion.

See:

- `docs/internal/ai/assistant.md`
- `docs/internal/ai/current-llm-workflow.md`
- `docs/internal/ai/conceptual-instance-paper-traceability.md`
- `docs/internal/ai/assistant-approach-comparison.md`
