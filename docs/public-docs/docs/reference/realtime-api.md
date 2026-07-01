# Realtime Assistant API

Assistant commands use REST. Realtime transports publish receive-only progress and result events.

## Session and Message Flow

1. Create or resume a session with `POST /api/chatbot/sessions`.
2. Open SSE or WebSocket for the returned `sessionId`.
3. Submit messages with `POST /api/chatbot/sessions/{sessionId}/messages`.
4. Optionally upload text attachments with `POST /api/chatbot/sessions/{sessionId}/attachments`.
5. Answer structured clarifications with `POST /api/chatbot/sessions/{sessionId}/choices`.
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

Allowed WebSocket origins come from `modless.allowed-origins`.

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

| Type                      | When                    | Payload highlights                         |
| ------------------------- | ----------------------- | ------------------------------------------ |
| `assistant.ready`         | Connection established  | `{ "sessionId": "..." }`                   |
| `assistant.progress`      | Turn stages             | `{ "stage", "message" }`                   |
| `assistant.model.preview` | During compile/validate | Preview model fragment and operation index |
| `chat.assistant`          | Turn complete           | Same shape as `MessageResponse`            |
| `model.updated`           | After apply or undo     | `{ "modelId", "revision", "proposalId"? }` |

Common `assistant.progress` stages include `READING_MODEL`, `PLANNING`, `PLANNING_SUBSET`,
`QUERYING_METAMODEL`, `PREVIEWING_PATCH`, `VALIDATING`, `COMPLETING`, `APPLYING`, and
`ANALYZING_SOURCE`.

## Proposal REST Routes

| Method | Path                                                            |
| ------ | --------------------------------------------------------------- |
| `GET`  | `/api/chatbot/conversations`                                    |
| `GET`  | `/api/chatbot/sessions/{sessionId}/thread`                      |
| `POST` | `/api/chatbot/sessions/{sessionId}/attachments`                 |
| `POST` | `/api/chatbot/catalogs/reindex`                                 |
| `GET`  | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}`      |
| `POST` | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo` |
| `POST` | `/api/chatbot/sessions/{sessionId}/choices`                     |

## Transport Notes

- Realtime events are scoped by assistant session ID.
- Authentication and project access are enforced on command endpoints. SSE and WebSocket streams do
  not require `X-Auth-Token`; treat session IDs as capability tokens in shared environments.
- Consumers should tolerate duplicate status updates and reconnect after interruption.
- Persisted proposal status (`APPLIED`, `UNDONE`) is authoritative after reconnect.
