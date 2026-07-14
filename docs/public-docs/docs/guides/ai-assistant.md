# AI modeling assistant

Varka uses one durable, coding-agent-style modeling runtime. A user message is accepted as an
assistant turn, processed by `AgentTurnLoop`, and applied only through backend-validated model
tools over an in-memory `ModelWorkspace`.

The agent can inspect and edit the working model with tools such as `describe_types`,
`read_model`, `search_model`, `create_elements`, `update_elements`, `delete_elements`,
`connect_elements`, `validate_model`, and `plan_work`. Invalid types, attributes, references,
containments, and enum values are rejected by the live metamodel contract before a mutation is
committed.

Assistant message submission returns `202 Accepted` with a `turnId`, model ID, revision, deadline,
and event cursor. Clients then use durable status and event endpoints to follow progress, replay
events after reconnects, cancel work, continue partial work, confirm destructive batches, or undo a
saved checkpoint.

The realtime API is authenticated fetch-based SSE over durable turn events. It publishes factual
stage, tool, checkpoint, needs-input, needs-confirmation, completion, failure, and cancellation
events. It does not expose provider token streaming, private reasoning, WebSocket access, or an
approve/reject proposal workflow.
