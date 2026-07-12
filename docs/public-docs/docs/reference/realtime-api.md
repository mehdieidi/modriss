# Realtime Assistant API

Assistant commands use REST. Realtime transports publish receive-only progress and result events.

## Session and Message Flow

1. Create or resume a session with `POST /api/chatbot/sessions`.
2. Open SSE or WebSocket for the returned `sessionId`.
3. Submit messages with `POST /api/chatbot/sessions/{sessionId}/messages`.
4. Optionally upload text attachments with `POST /api/chatbot/sessions/{sessionId}/attachments`.
5. Submit follow-up messages through the normal message endpoint when the agent needs clarification.
6. Undo an applied change with `POST /api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo`.
7. Clear the session with `DELETE /api/chatbot/sessions/{sessionId}`.

Session creation accepts `projectId`, `modelType` (`cim`, `pim`, or `psm`), optional `modelName`,
`resumeSessionId`, and `forceNew`. The backend auto-resumes recent conversations for the same
project and level unless `forceNew` is set.

Message submission accepts `message`, `modelId`, `revision`, `activeView`, `selectedElementIds`,
`unsavedDraftPatch`, inline attachment fields, and `attachmentIds`.

Valid mutations are **auto-applied** before the REST response returns. There is no separate approve
endpoint.

## SSE

```http
GET /api/chatbot/sessions/{sessionId}/events
Accept: text/event-stream
```

The server keeps the emitter open for up to 30 minutes and publishes an initial
`assistant.ready` event.

## WebSocket

```text
ws://127.0.0.1:8080/ws/chatbot/sessions/{sessionId}
```

The WebSocket is receive-only. Client text messages are ignored; use REST to submit user actions.
The connection publishes `assistant.ready` after it is established.

Allowed WebSocket origins come from `varka.allowed-origins`.

## Event Envelope

Each event is a JSON object:

```json
{
  "type": "assistant.progress",
  "payload": { "stage": "VALIDATING", "message": "Checking the draft change" }
}
```

SSE sets `event: <type>` and sends the full envelope as data. WebSocket sends the envelope as text.

### Event Types

| Event type                   | Purpose                                    |
| ---------------------------- | ------------------------------------------ |
| `assistant.ready`            | Stream connected                           |
| `assistant.trace.started`    | Turn accepted with deadline                |
| `assistant.trace.step`       | User-visible planning/validation stage     |
| `assistant.progress`         | Turn stage updates (legacy alias)          |
| `assistant.tool.started`     | Agent tool invocation began                |
| `assistant.tool.completed`   | Agent tool invocation finished             |
| `assistant.plan`             | Agent-maintained work plan                 |
| `assistant.worker.started`   | Source-document worker started             |
| `assistant.worker.completed` | Source-document worker completed           |
| `model.delta`                | Validated working-copy model delta         |
| `assistant.turn.completed`   | Successful terminal turn                   |
| `assistant.turn.failed`      | Failed terminal turn                       |
| `chat.assistant`             | Completed turn payload                     |
| `model.updated`              | Model revision changed after apply or undo |

Common `assistant.progress` stages include `READING_MODEL`, `PLANNING`, `QUERYING_METAMODEL`,
`PREVIEWING_PATCH`, `VALIDATING`, `COMPLETING`, `APPLYING`, and `ANALYZING_SOURCE`.

## Proposal REST Routes

| Method | Path                                                            |
| ------ | --------------------------------------------------------------- |
| `GET`  | `/api/chatbot/conversations`                                    |
| `GET`  | `/api/chatbot/sessions/{sessionId}/thread`                      |
| `POST` | `/api/chatbot/sessions/{sessionId}/attachments`                 |
| `GET`  | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}`      |
| `POST` | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo` |

## Transport Notes

- Realtime events are scoped by assistant session ID.
- Authentication and project access are enforced on command endpoints. SSE and WebSocket streams do
  not require `X-Auth-Token`; treat session IDs as capability tokens in shared environments.
- Consumers should tolerate duplicate status updates and reconnect after interruption.
- Persisted proposal status (`APPLIED`, `UNDONE`) is authoritative after reconnect.
