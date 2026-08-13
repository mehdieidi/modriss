# How the Varka AI assistant works

Updated: 2026-08-13

## Request-to-commit path

1. `ChatbotController` authenticates a CIM/PIM session and accepts an idempotent asynchronous turn.
2. `DurableAssistantTurnWorker` claims the turn under a lease and reconstructs model/source state.
3. `AgentTurnLoop` asks the LLM for one closed strategy allowed by deterministic structural facts.
4. The selected workflow uses the same provider adapter and private `ModelWorkspace`.
5. Mutation output is compiled against live Ecore contracts.
6. `ModelService.validateStructural(...)` is the only assistant validation gate.
7. A successful candidate commits as an expected-revision checkpoint with an inverse patch.

## Adaptive routing

The allowlist is `CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, and `ANSWER`. Fresh empty models may use
conceptual generation. Existing models, selected elements, resumptions, and confirmed destructive
turns use the inspect/contract agent. `ANSWER` maps to read-only `EXPLAIN_MODEL`.

Routing does not inspect business keywords. The LLM owns intent interpretation; deterministic code
only restricts the legal workflow based on model state and safety facts.

## Conceptual workflow

`ConceptualInstanceModelWorkflow` is a durable prompt chain:

1. `conceptual_obligation_ledger` creates up to 12 stable mandatory/optional obligations and maps
   them to exact creatable candidate EClasses.
2. `conceptual_type_selection` chooses exact EClasses. The backend verifies mandatory coverage and
   the actual combined required closure. There are four bounded selection attempts.
3. `conceptual_blueprint` plans up to 16 stable object IDs/types, legal ownership, references,
   obligation/source allocations, and slices.
4. `conceptual_instance_slice` generates private object payloads. Large blueprints start at two
   objects per slice and fall back to one after a length truncation.
5. `conceptual_obligation_review` independently judges every obligation and cites exact staged
   object IDs and relationship triples.
6. The compiler resolves IDs, attributes, containment, references, required features, and evidence
   into `ModelCommandBatch`.

Candidate EClasses in an obligation are alternatives, not all required types. The backend verifies
that cited evidence is real but never maps prompt words or chooses a semantic subtype. Abstract
required targets are represented in closure, and exact creatable subtypes are offered to the LLM.

The obligation ledger, selected types, blueprint, slice size, generated work-item payloads,
diagnostics, and successful verdict are durable. Private generated objects do not become a model
revision until the final atomic checkpoint.

## Arvan behavior

`OpenAiCompatibleAssistantModelProvider` uses JSON content, temperature zero, and DeepSeek's
non-thinking request field. Arvan can still return extensive reasoning and `finish_reason=length`.
After type-selection truncation, the workflow sends a compact prompt containing only obligation
candidates, exact closure costs, the request, and the latest diagnostic.

Provider-call prompts, model, latency, token usage, finish reason, errors, and idempotent call keys
are persisted. Failed calls remain part of turn accounting.

## Inspect/contract actions

The normal closed actions are `plan_model_edit`, `inspect_model`, `describe_types`,
`commit_model_batch`, `answer_user`, and `ask_user`. Model tools return exact inventory/contracts
and compile mutations; they do not generate business semantics.

## Source and continuation state

Attachments become durable source units. Provenance is `SOURCE_GROUNDED` or `INFERRED` with an
assumption. Source coverage is accounting. Continuation creates a child turn and resumes persisted
work without duplicating committed checkpoints or private work items.

## Validation boundary

Assistant generation, review, repair, apply, and commit call only
`ModelService.validateStructural(...)`. EVL, stored semantic-validation endpoints,
`validateGeneratedXmi(...)`, and `ModelService.validate(...)` are outside assistant paths.

## Current limitation

The single conceptual checkpoint is bounded by a 16-object schema, required closure, and the
provider-call budget. A coherent multi-increment design is still required for larger models. The
latest obligation-gated PIM profile fails atomically and has not yet completed the repeated live
campaigns needed for production readiness.
