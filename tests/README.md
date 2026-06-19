# Cross-cutting tests

Placeholder directories for repository-level tests that span multiple apps or packages.
Module-specific tests live next to their source (for example under `apps/backend/src/test`).

| Directory      | Intended use                         |
| -------------- | ------------------------------------ |
| `api/`         | HTTP API contract and smoke tests    |
| `e2e/`         | End-to-end workflows across services |
| `frontend/`    | Browser-level frontend checks        |
| `integration/` | Multi-module integration scenarios   |
| `mde/`         | MDE pipeline regression suites       |
| `unit/`        | Shared unit-test utilities           |
| `websocket/`   | Real-time API behavior               |
