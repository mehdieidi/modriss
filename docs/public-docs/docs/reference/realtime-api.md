# Durable assistant turns and realtime events

`POST /api/chatbot/sessions/{sessionId}/messages` persists a turn and returns `202` immediately.
The response carries `turnId`, deadline, model ID, revision, and event cursor. Use
`GET /api/chatbot/turns/{turnId}` for durable status, `POST /cancel`, `/continue`, and `/undo` for
turn actions.

Realtime uses authenticated fetch-based SSE only:

```http
GET /api/chatbot/turns/{turnId}/events
X-Auth-Token: <session token>
Last-Event-ID: <optional cursor>
Accept: text/event-stream
```

Every event contains an event ID, turn ID, sequence, type, timestamp, and safe payload. Supported
event types are `turn.accepted`, `turn.stage`, `tool.started`, `tool.completed`,
`model.checkpoint`, `turn.needs_input`, and `turn.completed`. Clients must poll turn status when
SSE disconnects and must tolerate replayed events.

Provider token text streaming and WebSocket access are intentionally not supported. The UI exposes
only factual stage/checkpoint/validation/coverage progress, never private reasoning.
