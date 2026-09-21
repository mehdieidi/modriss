# AI-Assisted Modeling

The current MODRISS prototype implements a durable conversational assistant for CIM and PIM models. PSM sessions are outside its scope and the API rejects them. The deployed provider is Arvan `Gemma-4-31B-IT`.

## Scope and implemented operations

Users can ask the assistant to create a bounded conceptual model, inspect or explain a model, make checked edits to existing content, or use uploaded text as source material. It reads the live Ecore metamodel when working with model elements.

Provider truncation, structural problems, capacity conflicts, missing requirements, revision conflicts, and requests that need clarification can stop a turn before a complete result is ready. The assistant can return `PARTIAL`, `FAILED`, `CONFLICTED`, `NEEDS_INPUT`, or `NEEDS_CONFIRMATION` to show what happened.

## Response-strategy selection

The user works with one chatbot. Internally, the LLM selects a strategy that is allowed by the model's structural state:

| Strategy                | Use                                                                                             |
| ----------------------- | ----------------------------------------------------------------------------------------------- |
| `CONCEPTUAL_GENERATION` | Create a fresh CIM or PIM model, or make a coherent additive change to an existing one.         |
| `INSPECT_AGENT`         | Make a focused edit, work on selected elements, resume a turn, or handle a destructive request. |
| `ANSWER`                | Explain a model without changing it.                                                            |

The backend does not infer business meaning from prompt keywords or canned templates.

## Conceptual generation and evolution

The conceptual workflow first records requirement obligations and selects the exact EClasses needed. It plans a stable-ID blueprint, checks that source concepts have been represented, and can repair a rejected plan with bounded LLM-authored remove/upsert patches. The assistant then generates private slices, performs an independent obligation review, compiles the result, checks structural Ecore/EMF conformance, and commits one atomic checkpoint.

Generated objects remain private until the checkpoint is committed. Each mandatory obligation must have evidence from staged objects and references. If evidence is missing, the checkpoint is not published.

The private blueprint allows up to 96 objects or types and 64 requirement obligations. Required Ecore closure and the provider-call budget can lower the effective capacity. During review, the system rejects a source plan that cites a source span without separately representing its distinct named concepts. Larger models need a coherent multi-increment plan; the assistant must not omit requested concepts to fit the schema ceiling.

## Editing an existing model

For a non-empty model and a non-destructive request, the LLM can choose coherent conceptual evolution or the inspect/contract action loop. Selected-element, resumed, and destructive turns use the inspect/contract path.

Revision checks, containment and reference enforcement, destructive confirmation, private staging, structural validation, and inverse patches protect existing content. A recorded PIM evolution retained 13 existing nodes and added 11 nodes in revision 3. Repeated CIM and PIM preservation campaigns are still required.

## Source-backed modeling

Uploaded Markdown, text, and JSON files can be split into durable source units. Each model element records provenance as `SOURCE_GROUNDED` or `INFERRED`. `coveragePercent` accounts for source material and mandatory obligations. It does not measure EVL semantic validity.

## Turn lifecycle and controls

Messages create asynchronous, durable turns. A client can poll a turn's status or replay its events over authenticated SSE. Available controls include cancellation, continuation, destructive confirmation, rebase, turn undo, checkpoint rollback, and feedback.

`QUEUED` and `RUNNING` are non-terminal states. Terminal states are `SUCCEEDED`, `PARTIAL`, `NEEDS_INPUT`, `NEEDS_CONFIRMATION`, `CONFLICTED`, `CANCELLED`, `TIMED_OUT`, and `FAILED`.

## Structural validation in assistant workflows

Assistant-generated actions, patches, proposals, checkpoints, and model outputs are gated only by structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. Assistant generation, review, repair, apply, and commit paths do not invoke EVL, stored semantic validation, or full `ModelService.validate(...)`. Run explicit model validation separately when EVL feedback is needed.

Structural conformance does not establish that a model is useful. MODRISS also performs an LLM obligation review and uses live-evaluation gates. The required repeated reliability campaigns are not complete, so the assistant should not be described as perfectly reliable or production-ready.

## Provider limitations

Arvan can return extensive reasoning despite non-thinking controls. A small structured stage can also end with `finish_reason=length`. MODRISS uses bounded, stage-specific retries and durable accounting. Broad PIM generation may still take several minutes or fail atomically.

For transport details, see the [REST API](../reference/rest-api.md), [Realtime API](../reference/realtime-api.md), and [Configuration](../reference/configuration.md).
