# WebSocket API

The backend does not currently expose a WebSocket API.

Collaboration-related frontend areas are planned, but requests for future collaboration/admin
features are handled through REST placeholders under `/api/admin/**` and return `501`.

If a WebSocket transport is added later, document the connection URL, authentication handshake,
message envelopes, event names, close codes, retry behavior, and ordering guarantees here.
