# Current LLM modeling workflow

Updated: 2026-08-12

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
       -> ANSWER intention -> ordinary AUTO action loop
  -> private ModelWorkspace
  -> structural Ecore/EMF validation
  -> revision-checked atomic commit and checkpoint
```

Authentication, project/model scope, idempotency, leases, deadlines, cancellation, expected
revision, checkpoints, inverse patches, continuation, confirmation, source accounting, provider
audit, and SSE replay are deterministic and shared by both mutation strategies.

## Routing behavior

`VARKA_AI_MODE=unified` is the normal production configuration. `agent-test` and
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

| Structural situation                | Legal semantic choices              | Effective mutation path                                            |
| ----------------------------------- | ----------------------------------- | ------------------------------------------------------------------ |
| Empty, fresh, non-destructive model | conceptual or `ANSWER` intention    | conceptual for an explicit mutation decision; `ANSWER` enters AUTO |
| Non-empty, fresh model              | inspect agent or `ANSWER` intention | both inspect and `ANSWER` enter AUTO                               |
| Selected elements                   | agent action loop                   | inspect/contract agent                                             |
| Resumed durable work                | persisted workflow                  | inspect/contract plan/repair                                       |
| Confirmed destructive request       | agent action loop                   | inspect/contract agent with preconditions                          |

If the strategy response is malformed, one bounded correction requests only the tiny schema.
Strategy calls and their tokens are included in the durable provider-call audit.

`ANSWER` maps to the enforced read-only explanation workflow. Mutation actions are unavailable on
that route.

## Conceptual generation

The conceptual strategy is implemented by `ConceptualInstanceModelWorkflow`.

### Blueprint and bounded conceptual slices

The blueprint pass assigns at most eight stable temporary IDs, exact EClasses, containment ownership, major
reference targets, source-unit allocation, and coherent slice numbers without generating full
attribute payloads. It is capped at 8 objects and 12 focused EClasses. Blueprint acceptance checks
both legal containment placement and closure of every required writable Ecore reference, so a rich
object cannot be generated before its required stable-ID dependencies are planned.

Each Arvan/DeepSeek slice contains one rich object. Providers without DeepSeek's observed
completion behavior may pack at most two. Every slice receives:

- the selected authoritative Ecore contracts;
- a compact persisted-model inventory with exact IDs, ownership, attributes, and references;
- the user request and supplied source units;
- the exact paper-style JSON contract.

Each response contains only the bounded slice (one object on DeepSeek, at most two otherwise) and
is keyed by instance ID. New keys are temporary
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
Before compilation, a structured LLM reviewer checks requirement coverage, duplication,
relationships, naming, abstraction, grounding, and unsupported invention; corrections must be
bounded replacement objects rather than prose.

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
persistence. Existing-model evolution and all destructive work currently use this path.

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

Coverage is accounting, not semantic validation. A source-backed turn cannot claim success while
relevant source units remain unaccounted. Large-source durable slicing currently belongs to the
inspect/contract workflow; the conceptual path is still a bounded complete-response workflow.

## Provider protocol

The deployed configuration is:

```dotenv
VARKA_AI_PROVIDER=openai
VARKA_AI_MODEL=DeepSeek-V4-Flash
VARKA_AI_MODE=unified
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
```

`json_schema` selects structured JSON content in the provider adapter. For the Arvan endpoint this
is transported using JSON-object response format, schema-specific prompting, temperature zero, and
DeepSeek's `thinking: {"type":"disabled"}` request field. Native OpenAI tool calls are not used in
the deployed configuration.

The same `OpenAiCompatibleAssistantModelProvider` handles strategy selection, conceptual type
selection, conceptual generation, and agent actions. Provider-call latency, prompt/completion token
usage, prompts, model, and failures are persisted, including failed turns.

The deployed 18-call ceiling reserves up to two calls for adaptive routing and 16 for conceptual
work. The conceptual budget accommodates the blueprint, eight single-object slices, quality review,
and bounded slice, review, and compiler corrections. Stage-specific completion limits keep strategy,
blueprint, slice, and review responses substantially below the provider's broad global maximum.

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

| Fixture                 | Agent acceptance | Conceptual acceptance                    | Unified acceptance                                                                        |
| ----------------------- | ---------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------- |
| `create-cim-library`    | Passed           | Passed in an optimized run               | The two-object profile passed once, then failed live; the one-object profile awaits rerun |
| `cim-feature-evolution` | Passed           | Failed the second persisted-model update | Passed through the agent path                                                             |
| `source-to-cim-pantry`  | Passed           | Passed with complete coverage            | Passed with complete coverage                                                             |
| `create-pim-serverless` | Passed           | Passed after bounded contract selection  | Passed through conceptual generation                                                      |

The evidence supports conceptual generation for bounded empty models and the agent for persisted
updates. It does not support a claim of perfect reliability. Detailed call, latency, token, repair,
structure, preservation, and failure evidence is in
[assistant-approach-comparison.md](assistant-approach-comparison.md) and the reports under `target/`.

## Known limitations

- The conceptual blueprint, per-object payloads, reduced slice size, provider-call audit, and token
  totals are durable. Lease recovery resumes missing IDs without exposing a partial model revision.
- Conceptual updates to non-empty models have not passed the feature-evolution acceptance fixture
  reliably and are therefore excluded by the production safety rule.
- The staged library protocol had one successful two-object-slice run, but a later final-profile
  run truncated two slices and exhausted the review reserve with zero commits. The one-object
  profile still requires a successful live rerun and the repeated-run release campaign.
- Structural validity does not prove conceptual usefulness or EVL semantic validity.
- The live paraphrase corpus and repeated-run production SLO gate remain incomplete.
