# Current LLM modeling workflow

Updated: 2026-09-09

This document describes the implementation currently in the repository. It distinguishes shipped
behavior from proposed production hardening.

## One durable product, three internal outcomes

Users interact with one chatbot and never select a modeling engine.

```text
REST message
  -> durable assistant_turn
  -> DurableAssistantTurnWorker
  -> AgenticAssistantFacade / AgenticTurnService
  -> AgentTurnLoop(ADAPTIVE)
       -> CONCEPTUAL_GENERATION
       -> INSPECT_AGENT
       -> ANSWER -> read-only EXPLAIN_MODEL workflow
  -> private ModelWorkspace
  -> structural Ecore/EMF validation
  -> revision-checked atomic commit and checkpoint
```

Authentication, project/model scope, idempotency, leases, deadlines, cancellation, expected
revision, checkpoints, inverse patches, continuation, confirmation, source accounting, provider
audit, and SSE replay are deterministic and shared by both mutation strategies.

## Routing behavior

`MODRISS_AI_MODE=unified` is the normal production configuration. `agent-test` and
`conceptual-test` exist only to isolate strategies in acceptance tests. Parsing is strict; any
other value prevents startup.

The worker first uses durable structural state:

1. A turn with a prior checkpoint, modeling plan, source blueprint, or source analysis resumes as
   `RESUME_REPAIR` on the inspect/contract path.
2. A selected-element turn or server-confirmed destructive turn uses the inspect/contract path.
3. A fresh ordinary or source-backed turn without selected elements enters `ADAPTIVE`.

The adaptive decision is an LLM call with the closed schema:

```json
{ "strategy": "CONCEPTUAL_GENERATION|INSPECT_AGENT|ANSWER" }
```

The backend supplies only structural facts such as model level, model emptiness, source character
count, destructive-confirmation state, and the legal strategies. It does not inspect request
keywords. Safety restrictions are deterministic:

| Structural situation                | Legal semantic choices           | Effective mutation path                           |
| ----------------------------------- | -------------------------------- | ------------------------------------------------- |
| Empty, fresh, non-destructive model | conceptual or `ANSWER` intention | conceptual mutation or read-only explanation      |
| Non-empty, non-destructive model    | conceptual, inspect, or `ANSWER` | coherent evolution, surgical edit, or explanation |
| Selected elements                   | agent action loop                | inspect/contract agent                            |
| Resumed durable work                | persisted workflow               | inspect/contract plan/repair                      |
| Confirmed destructive request       | agent action loop                | inspect/contract agent with preconditions         |

If the strategy response is malformed, one bounded correction requests only the tiny schema.
Strategy calls and their tokens are included in the durable provider-call audit.

`ANSWER` maps to the enforced read-only explanation workflow. Mutation actions are unavailable on
that route.

## Conceptual generation and additive evolution

The conceptual strategy is implemented by `ConceptualInstanceModelWorkflow`.

### Blueprint and bounded conceptual slices

Before type selection, production durable turns ask the LLM for a structured requirement-
obligation ledger. Each obligation has a stable ID, natural-language statement, mandatory/optional
importance, exact source-unit IDs when applicable, and one or more LLM-selected exact creatable
EClasses expected to provide model evidence. Deterministic code does not infer these mappings from
prompt words. It checks that type selection retains a compatible mapping for every mandatory
obligation and persists the ledger before blueprinting so restart/resume cannot reinterpret it.

The blueprint pass assigns at most 96 stable temporary IDs, exact EClasses, containment ownership,
major reference targets, obligation allocation, source-unit allocation, and coherent slice numbers
without generating full attribute payloads. It is capped at 96 objects and 96 focused EClasses.
Source-backed blueprints receive one exhaustive LLM completeness critique before slice generation.
Rejected parseable blueprints are repaired through bounded LLM-authored remove/upsert patches, so
a local structural defect does not require another full blueprint response. The critic reports all
material omissions it can identify in one pass rather than revealing a small fixed batch across
successive repairs. Its CIM rubric distinguishes independently modelable actors, states, risks,
domain terms, and behaviors from scalar fields or cohesive sub-actions that can be represented by
the purpose and attributes of one owning conceptual object. The corrected plan is structurally
rechecked; the generated objects later receive a separate LLM obligation-evidence verdict instead
of repeatedly invoking a non-monotonic open-ended critic. Blueprint acceptance checks
both legal containment placement and closure of every required writable Ecore reference, so a rich
object cannot be generated before its required stable-ID dependencies are planned.

Type selection is an LLM-owned semantic decision over exact live EClass names. Deterministic code
checks the actual combined required Ecore closure, reports exact marginal closure savings when the
selection exceeds capacity, and preserves that diagnostic across a length-truncated correction.
Type selection has four bounded attempts. After a truncation, retries use only obligation candidate
EClasses, their exact closure costs, the request, and the latest diagnostic rather than repeating
the complete metamodel index.
Every selected semantic EClass must be instantiated by the blueprint; silently dropping selected
request concepts is a protocol error. No keyword router, business alias table, canned type choice, or
deterministic model generator is used.

When a required Ecore reference targets an abstract EClass, capacity accounting reserves a real
object for it and the blueprint prompt lists every exact creatable assignable subtype. The LLM chooses
the semantically appropriate subtype. Abstract or otherwise non-creatable blueprint objects are
rejected before slice generation.

Every mandatory obligation must be allocated to at least one planned object whose exact EClass is
one of the ledger mappings or an assignable concrete subtype. Each focused slice also rejects any
emitted association that is not an exact writable Ecore reference, is placed under the wrong
containment/reference collection, names an unknown or incompatible target, duplicates a single-valued feature,
or contradicts blueprint containment ownership. This keeps structural correction local instead of
spending the remaining generation budget before the complete compiler reports the defect. After
all private slices are staged, an independent bounded LLM verdict can report `SATISFIED`, `PARTIAL`,
or `MISSING` for every obligation and cite exact staged object IDs and structured
source-feature-target relationship evidence. This verdict runs only when
`MODRISS_AI_LLM_REVIEW_ENABLED=true`; the backend then verifies every cited ID and relationship. When
review is disabled, no verdict or judge call occurs and structural Ecore conformance alone gates
the candidate. There is no semantic fallback, and neither mode invokes EVL validation.

The Gemma profile normally uses two related objects per slice and durably falls back to one if
Arvan length-limits a response. DeepSeek-compatible profiles use one rich object per slice. Every
slice receives:

- the selected authoritative Ecore contracts;
- a compact persisted-model inventory with exact IDs, ownership, attributes, and references;
- the user request and supplied source units;
- the exact paper-style JSON contract.

Each response contains only the bounded one- or two-object slice and is keyed by instance ID. New
keys are temporary
IDs; updates must use exact persisted IDs and the persisted EClass. Every value contains:

```json
{
  "type": "ExactEClass",
  "attributes": [{ "dataType": "ExactEDataType", "attributeName": "name", "value": "value" }],
  "associations": {
    "compositions": [
      { "associationName": "children", "associatedClassName": "Child", "instanceID": "tmp-child" }
    ],
    "references": []
  },
  "evidence": []
}
```

Composition is written on the parent and points to the child. A non-containment reference is
written on its source and points to its target. JSON order is irrelevant.

Slices may reference IDs declared in later slices. The backend merges them only after every planned
ID has exactly one payload. A length-truncated response is abandoned and split into smaller slices.
Production durable turns then use the independent obligation verdict described above. The older
general conceptual-review schema remains only for non-durable compatibility/test construction.

### Deterministic compilation and repair

The compiler independently checks and compiles objects, attributes, compositions, references, and
evidence. It verifies:

- concrete EClasses and exact existing-ID types;
- legal attributes, datatypes, enum literals, multiplicity, and required values;
- containment feature, target type, single ownership, dependency cycles, and root placement;
- reference feature, target type, multiplicity, and temporary/persisted ID resolution;
- required references and source-evidence shape.

All IDs are resolved before commands are emitted. Creates are ordered by containment dependency.
A root containment is inferred only when the Ecore contract admits exactly one legal placement;
zero or multiple placements produce a diagnostic.

A rejected document is returned to the LLM with the complete rejected JSON and a precise compiler
diagnostic. The correction must be a complete document that preserves valid content. A successful
document compiles to the same `ModelCommandBatch` used by the agent path. Omission never deletes
existing content, and conceptual output cannot move or delete objects.

## Inspect/contract agent

The inspect path is a bounded model-directed action loop. Each provider response must be one
allowlisted action:

| Action               | Purpose                                                        |
| -------------------- | -------------------------------------------------------------- |
| `plan_model_edit`    | Create a durable modeling plan and coherent checkpoint slices. |
| `inspect_model`      | Read the root inventory, exact elements, or neighborhoods.     |
| `describe_types`     | Retrieve exact Ecore contracts and construction closure.       |
| `commit_model_batch` | Submit creates, updates, connections, deletions, and evidence. |
| `answer_user`        | Finish without mutation.                                       |
| `ask_user`           | Request information required for safe progress.                |

The codec also retains `analyze_source_units`, `plan_cim_blueprint`, and `plan_source_model` for
older persisted source workflows. They are state-gated compatibility actions, not the normal entry
point for a new source turn; new source work begins with `plan_model_edit`.

Plans, exact contracts, inspection results, and compiler errors are fed back to the same LLM. The
backend resolves batch `clientRef` values to UUIDs, checks containment/references/evidence and
destructive preconditions, applies the batch to a private workspace, and validates it before
persistence. This path is mandatory for selected-element, resumed, and destructive work and remains
available to the LLM for surgical non-destructive edits.

Packaged skills are loaded from formal workflow state, level, source presence, and model
emptiness. They guide the LLM but cannot mutate or validate a model.

## Source-backed turns

Uploaded `.md`, `.txt`, and `.json` files are stored and split into bounded
`assistant_source_units`. The worker persists selected-unit context and, for durable agent work,
can persist source analysis, a blueprint, modeling work items, and partial checkpoints across
continuations.

Every source-backed created or inferred element must carry evidence:

- `SOURCE_GROUNDED` with an exact source-unit ID; or
- `INFERRED` with an explicit assumption and no source-unit ID.

Source-unit allocations in a structurally accepted LLM blueprint are authoritative provenance.
Slice compilation carries those allocations onto generated objects when Gemma omits the redundant
evidence fields. This prevents false partial coverage and duplicate continuation without creating
business-model content outside the LLM workflow.

Coverage is accounting, not semantic validation. A source-backed turn cannot claim success while
relevant source units remain unaccounted. Large-source progressive checkpointing currently belongs
primarily to the inspect/contract workflow. The conceptual path durably persists its obligation
ledger, selected types, blueprint, slice size, and private generated work items, but still publishes
only one final atomic checkpoint.

## Provider protocol

The active normal-mode deployment is:

```dotenv
MODRISS_AI_PROVIDER=openai
MODRISS_AI_MODEL=Gemma-4-31B-IT
MODRISS_AI_MODE=unified
MODRISS_AI_METAMODEL_MODE=normal
MODRISS_AI_OPENAI_PROTOCOL=json_schema
MODRISS_AI_NATIVE_TOOLS_PREFERRED=false
MODRISS_AI_FORCED_TOOL_CHOICE_RELIABLE=false
```

`json_schema` selects structured JSON content in the provider adapter. For the Arvan endpoint this
is transported using JSON-object response format, schema-specific prompting, temperature zero, and
a non-thinking request hint when the endpoint accepts it. Native OpenAI tool calls are not used in
the deployed configuration.

The same `OpenAiCompatibleAssistantModelProvider` handles strategy selection, conceptual type
selection, conceptual generation, and agent actions. Provider-call latency, prompt/completion token
usage, prompts, model, and failures are persisted, including failed turns.

The full-source profile uses a 40-call ceiling for normal turns and 64 for source-backed turns. Up to
two calls are reserved for adaptive routing. The conceptual budget accommodates obligation
interpretation, semantic type selection, a blueprint, private slices, optional independent
obligation review, and bounded selection, slice, review, and compiler corrections. Effective
object capacity is derived from the remaining call budget and
slice size and is capped by the 64-object schema. Stage-specific completion limits keep every
response bounded below the provider's broad global maximum.

LLM review is configurable with `MODRISS_AI_LLM_REVIEW_ENABLED`. When disabled, no reviewer or judge
call runs and only structural Ecore/EMF conformance gates the candidate. Progressive conceptual
work is durable: one bounded automatic recovery pass can reuse the obligation ledger, selected
types, blueprint, and generated objects without requiring a user-visible Resume action.
Provider calls are inserted into an idempotent durable ledger as they finish. Turn-level call and
token totals are reconciled from that ledger after insertion, including when cancellation
interrupts the ordinary worker completion path.

## Commit and validation boundary

Both mutation paths produce a candidate `ModelWorkspace`. The only assistant gate is structural
Ecore/EMF conformance through `ModelService.validateStructural(...)`. A valid candidate is then
committed against the expected revision inside the durable checkpoint transaction.

Assistant create, repair, apply, and commit must not call:

- `ModelService.validate(...)`;
- stored semantic-validation endpoints;
- `validateGeneratedXmi(...)`;
- `EpsilonEvlValidator` or EVL CLIs/profiles.

EVL is available only when a user explicitly initiates model validation outside the chatbot.

## Current live evidence

| User journey                       | Latest evidence                                                                                   |
| ---------------------------------- | ------------------------------------------------------------------------------------------------- |
| Active model/metamodel explanation | Succeeded without mutation using the read-only explanation path.                                  |
| Source attachment to CIM           | Succeeded with complete source-unit coverage and stored provenance.                               |
| Fresh CIM                          | Succeeded through obligation-gated conceptual generation.                                         |
| Fresh PIM                          | Succeeded through obligation-gated conceptual generation.                                         |
| Existing CIM evolution             | Earlier unified agent-path scenario succeeded; post-routing repeated evidence remains incomplete. |
| Existing PIM conceptual evolution  | 2026-09-09: preserved 13 nodes, added 11, revision 3, 10 calls, 268 seconds, structurally valid.  |

The evidence supports all four required journeys at least once, including additive evolution of a
persisted PIM. It does not support a claim of perfect reliability. Detailed call, latency, token,
structure, and preservation evidence is in
[assistant-approach-comparison.md](assistant-approach-comparison.md) and
[live-eval-gate-report.md](live-eval-gate-report.md).

## Known limitations

- The conceptual blueprint, per-object payloads, reduced slice size, provider-call audit, and token
  totals are durable. Lease recovery resumes missing IDs without exposing a partial model revision.
- Non-empty, non-destructive models now permit LLM selection of conceptual evolution. One PIM
  preservation scenario passed; repeated PIM and post-change CIM evolution campaigns remain open.
- One successful run is not a reliability distribution. The full fixture matrix, paraphrases, and
  ten-run campaigns remain incomplete.
- Structural validity is the chatbot's required validation boundary; human usefulness still needs
  repeated evaluation. EVL is intentionally outside chatbot apply/repair/commit paths.
- The live paraphrase corpus and repeated-run production SLO gate remain incomplete.
- The 2026-08-14 Gemma run proves one successful automatic-recovery path, not repeated production
  reliability. The ten-run campaign, paraphrase corpus, restart matrix, persisted-model evolution,
  and human usefulness review remain open.
- With LLM review disabled, structural validity does not guarantee that every generated name or
  design choice is optimal; this is an explicit tradeoff rather than hidden reviewer coverage.
- The exact doctor-booking prompt has two successful structurally valid runs out of three; the
  remaining historical run failed safely on malformed provider JSON.
- Gemma remains the only validated active model. Historical provider transport, truncation, and
  malformed-JSON failures were fail-closed, but transport/restart campaigns remain incomplete.
- Required writable Ecore references are validated within each conceptual slice. Association
  buckets, features, targets, types, multiplicities, ownership, and read-only inverse references
  are normalized or rejected against exact contracts before commit.
- The latest backend JAR is deployed and healthy locally. Docker Hub returned HTTP 403 for base-image
  metadata during the conventional image rebuild, so clean container recreation remains to be
  verified when registry access recovers.
- Expired `RUNNING` turns whose worker leases have ended are now finalized as `TIMED_OUT` during
  polling, preventing a restart from leaving a permanent working indicator.
