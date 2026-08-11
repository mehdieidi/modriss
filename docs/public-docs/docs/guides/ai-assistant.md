# AI modeling assistant

Varka provides one durable modeling chatbot for CIM, PIM, and PSM. You describe the desired model,
ask a question, select canvas elements when relevant, or attach source text. The backend
automatically chooses an internal modeling strategy; there is no agent/conceptual mode selector in
the UI or API.

## What it can do

- Explain the current model or authoritative metamodel.
- Generate bounded CIM or PIM content from an empty saved model.
- Turn `.md`, `.txt`, or `.json` source documents into source-grounded CIM content.
- Add features to an existing model while preserving unrelated elements.
- Inspect selected elements and their ownership/references before surgical edits.
- Pause for missing input or explicit confirmation before destructive changes.
- Save durable checkpoints that can be continued, rebased, rolled back, or undone.

The LLM decides the model's business meaning, objects, values, and relationships. Backend code
supplies exact Ecore contracts and handles IDs, containment order, reference resolution, source
accounting, validation, persistence, and workflow control.

## Automatic internal strategy

For a fresh empty model, the assistant can generate a complete conceptual instance document and
compile it deterministically into model commands. For an existing model, selected-element edit,
resumed workflow, or destructive request, it uses a bounded inspect/contract action loop. Questions
can finish without changing the model.

Strategy selection uses a strict LLM enum constrained by structural facts such as whether the model
is empty, whether elements are selected, and whether durable or destructive state is present. It
does not route from request keywords.

## Durable turn lifecycle

Message submission returns `202 Accepted` with a `turnId`, model ID/revision, deadline, and event
cursor. Follow the turn through authenticated SSE or the polling status endpoint.

A turn may finish as:

- `SUCCEEDED` — answer completed or all intended validated work committed;
- `PARTIAL` — a valid checkpoint was saved and more work remains;
- `NEEDS_INPUT` or `NEEDS_CONFIRMATION`;
- `CONFLICTED`, `CANCELLED`, `TIMED_OUT`, or `FAILED`.

Reload the model after a checkpoint event. Use the turn control endpoints to continue partial work,
confirm destruction, cancel, rebase safe revision drift, roll back a checkpoint, or undo a turn.
The current interface has no approve/reject proposal stage.

## Source-backed CIM generation

The backend stores attachments and splits them into bounded source units with stable IDs. Created or
inferred model elements record provenance as either:

- source-grounded, with an exact source-unit ID; or
- inferred, with an explicit assumption.

A source-backed turn cannot report successful completion while relevant units remain unaccounted.
`coveragePercent=100` is a source-accounting result, not a claim of semantic correctness.

## Safety and validation

All changes are first applied to a private working copy. EClasses, attributes, enum literals,
containment, references, IDs, required features, and revision preconditions are checked against
backend-owned Ecore contracts. A failed candidate does not partially mutate the saved model.

Assistant-generated changes are gated only by structural Ecore/EMF conformance. The chatbot does
not run EVL during generation, repair, apply, or commit. When semantic EVL feedback is required, a
user must run the normal model-validation workflow separately after a checkpoint.

Destructive work requires explicit confirmation and current-model preconditions. Successful model
turns save checkpoints and inverse patches when undo is possible.

## Realtime behavior

The event stream contains factual lifecycle, action, validation, checkpoint, coverage, and failure
events. It does not stream private reasoning or provider token text, and it is not a WebSocket.
Events are durable and can be replayed after reconnecting.

## Current limitations

- Provider latency and structured-output truncation can make large generation unreliable.
- Conceptual generation is currently bounded to one complete response rather than a persisted
  multi-response instance ledger.
- Existing-model changes intentionally use the inspect/contract path because conceptual update
  reliability has not met the live acceptance threshold.
- The adaptive `ANSWER` intention currently enters the ordinary action loop rather than an enforced
  read-only capability set; non-mutation is prompt-directed in that branch.
- Structural validity does not guarantee that a model is useful, complete, or semantically valid;
  review the generated model and run explicit validation where appropriate.

See [REST API](../reference/rest-api.md) and
[Durable assistant realtime API](../reference/realtime-api.md) for integration details.
