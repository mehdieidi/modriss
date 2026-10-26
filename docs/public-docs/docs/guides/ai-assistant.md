# AI-Assisted Modeling

The current MODRISS prototype implements a durable conversational assistant for CIM and PIM models. PSM sessions are outside its scope and the API rejects them. The deployed provider is Arvan `Gemma-4-31B-IT`.

## Scope and implemented operations

Users can ask the assistant to create a bounded conceptual model, inspect or explain a model, make checked edits to existing content, or use uploaded text as source material. It reads the live Ecore metamodel when working with model elements.

Provider truncation, structural problems, capacity conflicts, missing requirements, revision conflicts, and requests that need clarification can stop a turn before a complete result is ready. The assistant can return `PARTIAL`, `FAILED`, `CONFLICTED`, `NEEDS_INPUT`, or `NEEDS_CONFIRMATION` to show what happened.

## Response-strategy selection

The user works with one chatbot. Internally, the LLM selects a strategy that is allowed by the model's structural state:

| Strategy                 | Use                                                                                             |
| ------------------------ | ----------------------------------------------------------------------------------------------- |
| `CONCEPTUAL_GENERATION`  | Create a fresh CIM or PIM model, or make a coherent additive change to an existing one.         |
| `INSPECT_AGENT`          | Make a focused edit, work on selected elements, resume a turn, or handle a destructive request. |
| `ANSWER`                 | Explain a model without changing it.                                                            |
| `SERVERLESS_SUITABILITY` | Assess project suitability for serverless and MODRISS without changing a model.                 |

The backend does not infer business meaning from prompt keywords or canned templates.

## Serverless suitability analysis

The methodology's [serverless suitability analysis](serverless-suitability.md) explains all twelve
criteria, the assessment procedure, hybrid boundaries and the supporting research.

Paste a project specification into the chat, then ask for an assessment. The paperclip control is
currently disabled in the browser UI; document uploads remain available through the backend API.

> Would serverless and MODRISS suit this project? Explain your recommendation. If a hybrid architecture would work, identify which parts to model and which to deliver as external services.

Include the workload's peaks and idle periods, task durations and resource needs, response times or deadlines, and state and consistency requirements. Describe external integrations, security and data residency constraints, costs, operating responsibilities and any provider restrictions. You can answer follow-up questions in the same conversation.

The LLM uses the twelve criteria to explain whether serverless, a hybrid design or another approach
fits the project. The research guidance describes `SUITABLE`, `HYBRID`, `UNSUITABLE` and
`INSUFFICIENT_INFORMATION` outcomes, but the conversation has no required labels or report format.
With incomplete requirements, the assistant can give provisional advice and ask the questions that
matter. A detailed response should cite relevant sources and explain assumptions, conditions and
next steps. Missing facts remain unresolved, and other benefits cannot compensate for an unmet
mandatory constraint. The criteria's classification accuracy has not been scientifically validated.

A hybrid recommendation identifies a useful serverless subset to model in MODRISS and describes the
separately delivered components and their interfaces. Managed databases, storage, identity services
and third-party APIs can also be ordinary dependencies in a serverless design; their presence alone
does not require a hybrid recommendation. Check metamodel and generator support before proceeding,
including support for serverless containers or other managed products. The proposed runtime behavior
also needs verification.

The backend returns the LLM's answer directly, without semantic rejection rules or a canned
recommendation. It cannot produce an assessment when the provider is unavailable, credentials are
invalid or the request is cancelled or times out. These are reported as technical problems.

Assessment leaves the model unchanged. It creates no elements, runs no model validation and commits no checkpoint. Review the recommendation before asking for model creation in a separate turn. Uploads are assessed in full; if they exceed the provider's context limit, the request fails without silently omitting requirements. The project team remains responsible for the inception/G0 decision.

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

Submitting a request saves pending model edits and freezes the model while the assistant works.
Canvas edits, attribute changes, deletion, import, undo, and saving are disabled. Other tabs and
editors cannot save over an active assistant turn. Checkpoints can appear during the request, but
editing resumes only after the final persisted result has loaded on the canvas. Draft changes made
during the request are discarded. A disconnect keeps the model locked while the client reconnects;
requesting Stop keeps it locked until the backend stops and the last saved result is displayed.

`QUEUED` and `RUNNING` are non-terminal states. Terminal states are `SUCCEEDED`, `PARTIAL`, `NEEDS_INPUT`, `NEEDS_CONFIRMATION`, `CONFLICTED`, `CANCELLED`, `TIMED_OUT`, and `FAILED`.

## Structural validation in assistant workflows

Assistant-generated actions, patches, proposals, checkpoints, and model outputs are gated only by structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. Assistant generation, review, repair, apply, and commit paths do not invoke EVL, stored semantic validation, or full `ModelService.validate(...)`. Run explicit model validation separately when EVL feedback is needed.

Structural conformance does not establish that a model is useful. MODRISS also performs an LLM obligation review and uses live-evaluation gates. The required repeated reliability campaigns are not complete, so the assistant should not be described as perfectly reliable or production-ready.

## Provider limitations

Arvan can return extensive reasoning despite non-thinking controls. A small structured stage can also end with `finish_reason=length`. MODRISS uses bounded, stage-specific retries and durable accounting. Broad PIM generation may still take several minutes or fail atomically.

For transport details, see the [REST API](../reference/rest-api.md), [Realtime API](../reference/realtime-api.md), and [Configuration](../reference/configuration.md).
