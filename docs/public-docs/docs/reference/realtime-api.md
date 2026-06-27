# Realtime Assistant API

Assistant commands use REST. Realtime transports publish receive-only progress and result events.

## Session and Message Flow

1. Create a session with `POST /api/chatbot/sessions`.
2. Open SSE or WebSocket for the returned `sessionId`.
3. Submit messages with `POST /api/chatbot/sessions/{sessionId}/messages`.
4. Optionally upload text attachments with `POST /api/chatbot/sessions/{sessionId}/attachments`.
5. Handle proposals or choices through the relevant REST endpoints.
6. Clear the session with `DELETE /api/chatbot/sessions/{sessionId}`.

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

## Proposal REST Routes

| Method | Path                                                               |
| ------ | ------------------------------------------------------------------ |
| `GET`  | `/api/chatbot/conversations`                                       |
| `GET`  | `/api/chatbot/sessions/{sessionId}/thread`                         |
| `POST` | `/api/chatbot/sessions/{sessionId}/attachments`                    |
| `POST` | `/api/chatbot/catalogs/reindex`                                    |
| `GET`  | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}`         |
| `POST` | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/approve` |
| `POST` | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/reject`  |
| `POST` | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo`    |
| `POST` | `/api/chatbot/sessions/{sessionId}/choices`                        |

## Transport Notes

- Realtime events are scoped by assistant session ID.
- Authentication and project access are enforced on command endpoints.
- Consumers should tolerate duplicate status updates and reconnect after interruption.
- Persisted proposal status is authoritative after reconnect.
