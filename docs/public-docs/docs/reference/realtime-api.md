# Durable assistant turns and realtime events

`POST /api/chatbot/sessions/{sessionId}/messages` persists a turn and returns `202` immediately.
The response carries `turnId`, deadline, model ID, revision, and event cursor. Use
`GET /api/chatbot/turns/{turnId}` for durable status, `POST /cancel`, `/continue`, `/confirm`,
`/undo`, `/rebase`, `/checkpoints/{checkpointId}/rollback`, and `/feedback` for turn actions.

Realtime uses authenticated fetch-based SSE only:

```http
GET /api/chatbot/turns/{turnId}/events
X-Auth-Token: <session token>
Last-Event-ID: <optional cursor>
Accept: text/event-stream
```

Every event contains an event ID, turn ID, sequence, type, timestamp, and safe payload. Common event
types include `turn.accepted`, `turn.stage`, `tool.started`, `tool.completed`,
`turn.workflow.routed`, `turn.plan.ready`, `model.slice`, `model.checkpoint`,
`model.checkpoint.committed`, `turn.validation.completed`, `turn.needs_input`,
`turn.needs_confirmation`, `turn.completed`,
`turn.failed`, `turn.cancelled`, `turn.feedback`, and source-coverage progress payloads. Clients
must poll turn status when SSE disconnects and must tolerate replayed events.

Status payloads may include `coveragePercent`, unresolved source units or obligations,
provider-call/token counters, repair attempts, workflow phase, work items, and `remainingWork`.
Reload the model after each `model.checkpoint` event. Run explicit model validation separately when
EVL feedback is required; assistant checkpoints use structural validation only.

Provider token text streaming and WebSocket access are intentionally not supported. The UI exposes
only factual stage/checkpoint/validation/coverage progress, never private reasoning.

Internal workflow events can identify routing and durable work-item progress, but they are
observability data. Clients cannot select or override the assistant's internal conceptual/agent
strategy through SSE or REST.

Conceptual work items and obligation-review evidence remain private until a complete atomic
checkpoint is committed. SSE never exposes provider reasoning content.
