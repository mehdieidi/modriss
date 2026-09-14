# AI modeling assistant

MODRISS provides one durable conversational assistant for CIM and PIM. PSM assistant sessions are
currently out of scope and are rejected by the API. The deployed provider is Arvan
`Gemma-4-31B-IT`.

## What it does

The assistant can create bounded fresh conceptual models, inspect or explain models, evolve an
existing model through checked actions, and use uploaded text as source material. It uses the live
Ecore metamodel rather than a separate hard-coded modeling vocabulary.

The assistant is intentionally honest about incomplete work. Provider truncation, structural
problems, capacity conflicts, missing mandatory requirements, revision conflicts, or needed
clarification can produce `PARTIAL`, `FAILED`, `CONFLICTED`, `NEEDS_INPUT`, or
`NEEDS_CONFIRMATION` instead of a shallow success.

## Automatic internal strategy

The UI exposes one chatbot. Internally, an LLM chooses one closed strategy allowed by structural
state:

- `CONCEPTUAL_GENERATION` for fresh CIM/PIM creation and coherent additive evolution of an
  existing model.
- `INSPECT_AGENT` for surgical edits and for selected-element, resumed, or destructive work.
- `ANSWER` for read-only model explanation.

The backend does not route business meaning with prompt keywords or canned templates.

## Conceptual generation and evolution

The conceptual workflow creates a durable requirement-obligation ledger, selects exact EClasses,
plans a stable-ID blueprint, performs an independent source-blueprint completeness review,
repairs rejected plans with bounded LLM-authored remove/upsert patches, generates private slices,
performs an independent LLM obligation review, compiles the result,
validates structural Ecore/EMF conformance, and then commits one atomic checkpoint.

No generated object is visible before the final checkpoint. If any mandatory obligation cannot be
proven with real staged object/reference evidence, the checkpoint is not published.

The private blueprint schema allows at most 96 objects/types and 64 requirement obligations,
further constrained by required Ecore closure and the provider-call budget. The reviewer rejects
source plans that cite a source span without separately representing its distinct named concepts.
Larger models still need a coherent multi-increment design; the assistant must not silently omit
requested concepts to fit the ceiling.

## Existing-model edits

For a non-empty model and a non-destructive request, the LLM may choose either coherent conceptual
evolution or the inspect/contract action loop. Selected-element, resumed, and destructive turns use
the inspect/contract path. Revision checks, containment/reference enforcement, destructive
confirmation, private staging, structural validation, and inverse patches protect existing
content. A live PIM evolution preserved 13 existing nodes and committed 11 new nodes in revision 3;
repeated CIM/PIM preservation campaigns are still required.

## Source-backed modeling

Uploaded `.md`, `.txt`, and `.json` content can be split into durable source units. Element
provenance is stored as `SOURCE_GROUNDED` or `INFERRED`. `coveragePercent` represents source and
mandatory-obligation accounting; it is not EVL semantic validity.

## Turn lifecycle and controls

Messages create asynchronous durable turns. Clients can poll status or replay turn events over
authenticated SSE. Supported controls include cancellation, continuation, destructive
confirmation, rebase, turn undo, checkpoint rollback, and feedback.

Terminal states are `SUCCEEDED`, `PARTIAL`, `NEEDS_INPUT`, `NEEDS_CONFIRMATION`, `CONFLICTED`,
`CANCELLED`, `TIMED_OUT`, and `FAILED`. `QUEUED` and `RUNNING` are non-terminal.

## Validation and safety

Assistant output is gated only by structural Ecore/EMF conformance through
`ModelService.validateStructural(...)`. The assistant does not invoke EVL, stored semantic
validation, or full `ModelService.validate(...)` during generation, repair, apply, review, or
commit. Run explicit model validation separately when semantic EVL feedback is required.

Structural validity alone does not prove that a model is useful. MODRISS also uses an LLM obligation
review and stronger live-evaluation gates, but the required repeated reliability campaigns are not
complete. The assistant must not yet be described as perfectly reliable or production-ready.

## Provider limitations

Arvan may emit extensive reasoning despite non-thinking controls and may finish a small structured
stage with `finish_reason=length`. MODRISS uses bounded stage-specific retries and durable accounting,
but broad PIM generation can still take several minutes or fail atomically.

For transport details, see [REST API](../reference/rest-api.md),
[Realtime API](../reference/realtime-api.md), and
[Configuration](../reference/configuration.md).
