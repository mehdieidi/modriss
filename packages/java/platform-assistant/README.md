# Platform assistant

`platform-assistant` is the provider-neutral runtime behind Varka's single modeling chatbot. The
supported product scope is CIM and PIM; `ChatbotController` rejects PSM assistant sessions with
HTTP 422. Production uses the Arvan OpenAI-compatible endpoint with `DeepSeek-V4-Flash`.

## Runtime path

```text
ChatbotController
  -> durable assistant turn
  -> DurableAssistantTurnWorker
  -> AgenticAssistantFacade / AgenticTurnService
  -> AgentTurnLoop(ADAPTIVE)
       -> CONCEPTUAL_GENERATION -> ConceptualInstanceModelWorkflow
       -> INSPECT_AGENT         -> checked inspect/contract action loop
       -> ANSWER                -> read-only EXPLAIN_MODEL mode
  -> private ModelWorkspace
  -> ModelService.validateStructural(...)
  -> expected-revision atomic checkpoint
```

Users never choose an internal strategy. The LLM returns one allowlisted value from
`CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, or `ANSWER`; deterministic structural facts restrict the
legal set. Request keywords, aliases, and regex routing are not used.

Fresh empty-model creation may use conceptual generation. Existing models, selected elements,
resumed work, and confirmed destructive requests use the inspect/contract path. `ANSWER` maps to a
read-only workflow that cannot mutate, checkpoint, validate, or repair.

## Conceptual generation

`ConceptualInstanceModelWorkflow` currently performs:

1. LLM requirement-obligation planning, persisted before type selection.
2. LLM exact-EClass selection from the live metamodel index.
3. Deterministic combined containment/reference closure and capacity checks.
4. A stable-ID blueprint with obligation and source-unit allocations.
5. Private, durable slice generation; large blueprints begin with two objects per slice and fall
   back to one after provider length truncation.
6. Independent LLM obligation review with exact object and relationship evidence.
7. Deterministic compilation into `ModelCommandBatch`.
8. Structural-only validation and one atomic checkpoint commit.

The blueprint schema admits at most 18 objects/types. Effective capacity is also constrained by
the provider-call budget and required Ecore closure. Abstract required targets count toward
capacity; the LLM chooses among exact creatable subtypes supplied by the backend.

The obligation ledger contains stable IDs, natural-language obligations, mandatory/optional
importance, source-unit IDs, and one to four LLM-selected candidate EClasses. Candidate EClasses
are alternatives, not a checklist. Deterministic code verifies IDs, types, allocations, cited
objects, and cited relationship triples. Every mandatory obligation must be `SATISFIED` before a
checkpoint can be published.

There is no business-semantic fallback model. Failure or unresolved coverage produces an honest
partial/failure state and leaves the persisted model unchanged.

## Inspect/contract path

The normal provider-visible actions are `plan_model_edit`, `inspect_model`, `describe_types`,
`commit_model_batch`, `answer_user`, and `ask_user`. Legacy source actions remain state-gated only
so older durable workflows can resume. Backend code owns stable IDs, exact contracts,
containment/reference enforcement, destructive confirmation, source evidence, private mutation,
validation, and persistence.

## Provider protocol

The deployed Arvan profile uses JSON content rather than native tool calls:

```dotenv
VARKA_AI_PROVIDER=openai
VARKA_AI_MODEL=DeepSeek-V4-Flash
VARKA_AI_MODE=unified
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
```

The adapter sends temperature zero and `thinking: {"type":"disabled"}`. Arvan can still return
large `reasoning_content` and `finish_reason=length`. Truncated type-selection retries therefore
use only the LLM obligation candidates, exact closure costs, the request, and the latest diagnostic.
The selector currently has four bounded attempts so the LLM can act on a final capacity diagnostic.

## Validation and evidence boundary

Assistant create, repair, apply, review, and commit paths may call only
`ModelService.validateStructural(...)`. They must not call `ModelService.validate(...)`, stored
semantic-validation endpoints, `validateGeneratedXmi(...)`, EVL validators, CLIs, or profiles.
EVL remains available only for explicit validation workflows outside the chatbot.

The implementation has strong focused regression coverage and repeatedly fails atomically, but the
obligation-gated PIM profile does not yet have a clean live acceptance pass or the required repeated
reliability campaigns. Structural validity alone is not semantic success. See:

- `docs/internal/ai/current-llm-workflow.md`
- `docs/internal/ai/live-eval-gate-report.md`
- `docs/internal/ai/assistant.md`
