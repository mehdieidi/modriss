# AI modeling assistant

Varka provides one durable conversational assistant for CIM and PIM. PSM assistant sessions are
currently out of scope and are rejected by the API. The deployed provider is Arvan
`DeepSeek-V4-Flash`.

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

- `CONCEPTUAL_GENERATION` for fresh empty CIM/PIM creation.
- `INSPECT_AGENT` for existing models, selected elements, resumed work, and destructive edits.
- `ANSWER` for read-only model explanation.

The backend does not route business meaning with prompt keywords or canned templates.

## Fresh-model generation

The conceptual workflow creates a durable requirement-obligation ledger, selects exact EClasses,
plans a stable-ID blueprint, generates private slices, independently reviews mandatory obligation
coverage, compiles the result, validates structural Ecore/EMF conformance, and then commits one
atomic checkpoint.

No generated object is visible before the final checkpoint. If any mandatory obligation cannot be
proven with real staged object/reference evidence, the checkpoint is not published.

The private blueprint schema allows at most 16 objects/types, further constrained by required
Ecore closure and the provider-call budget. Larger models need a future coherent multi-increment
design; the assistant must not silently omit requested concepts to fit the ceiling.

## Existing-model edits

Existing-model turns inspect the saved model, request exact contracts, and submit checked command
batches. Revision checks, containment/reference enforcement, destructive confirmation, private
staging, structural validation, and inverse patches protect existing content. Existing-model
preservation still needs broader repeated live evidence.

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

Structural validity alone does not prove that a model is useful. Varka also uses an LLM obligation
review and stronger live-evaluation gates, but the required repeated reliability campaigns are not
complete. The assistant must not yet be described as perfectly reliable or production-ready.

## Provider limitations

Arvan may emit extensive reasoning despite non-thinking controls and may finish a small structured
stage with `finish_reason=length`. Varka uses bounded stage-specific retries and durable accounting,
but broad PIM generation can still take several minutes or fail atomically.

For transport details, see [REST API](../reference/rest-api.md),
[Realtime API](../reference/realtime-api.md), and
[Configuration](../reference/configuration.md).
