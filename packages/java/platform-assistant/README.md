# Platform assistant

The assistant package contains the bounded modeling-agent runtime used by the chatbot:

`DurableAssistantTurnWorker -> AgenticTurnService -> AgentTurnLoop -> AgentModelTools -> ModelWorkspace`

The agent receives deterministic Ecore contracts, uses validated tools for model inspection and
editing, streams activity through the realtime publisher, validates the working copy, and applies
an atomic revision-locked patch.

Provider-visible work is normalized into a small action protocol:

- `plan_source_model`
- `inspect_model`
- `describe_types`
- `commit_model_batch`
- `answer_user`
- `ask_user`

OpenAI-compatible native tool calls may expose the mutation action as `apply_draft_patch`; the
provider adapter maps it back to `commit_model_batch`.

Source-backed turns store uploaded text, split it into bounded source units, validate evidence IDs,
record source-grounded or inferred provenance, and compute source coverage. Model element IDs are
backend-generated UUIDs; provider `clientRef` values are temporary same-batch references only.

Current local live status is documented in
`../../../docs/internal/ai/current-llm-workflow.md`. As of 2026-08-10, assistant-generated model
changes are accepted only after structural Ecore/EMF conformance checks. The assistant apply path
does not execute EVL or require semantic validation to pass; EVL remains available through explicit
model validation endpoints outside the chatbot workflow.

Assistant database migrations live under `src/main/resources/db/assistant-migration/` and begin with
the squashed `V1__assistant_baseline.sql`. The baseline retains conversation, chat-memory,
proposal/undo, audit, and rate-limit tables only. Existing databases must be recreated or
intentionally baselined/repaired before using the squashed migration set.
