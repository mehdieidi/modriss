# WebSocket API

The backend exposes one receive-only WebSocket transport for the modeling assistant. Client actions
(messages, approvals, choices) use REST; the WebSocket publishes progress and result events.

## Connection

```text
ws://127.0.0.1:8080/ws/chatbot/sessions/{sessionId}
```

Create a session first with `POST /api/chatbot/sessions`, then open the WebSocket for the returned
`sessionId`. Allowed browser origins come from `modless.allowed-origins`.

## Behavior

- The server sends `assistant.ready` after the connection is established.
- Incoming client text frames are ignored; submit user actions through REST.
- Events are scoped to the assistant session ID.
- Consumers should tolerate duplicate status updates and reconnect after interruption.
- Persisted proposal status is authoritative after reconnect.

## SSE Alternative

When WebSocket connectivity is blocked, use the SSE stream instead:

```http
GET /api/chatbot/sessions/{sessionId}/events
Accept: text/event-stream
```

## Full Reference

Assistant REST routes, proposal lifecycle, and transport notes:
[docs/public-docs/docs/reference/realtime-api.md](../public-docs/docs/reference/realtime-api.md).

Sequence diagrams: [docs/diagrams/13-api-assistant-sequences.md](../diagrams/13-api-assistant-sequences.md).
