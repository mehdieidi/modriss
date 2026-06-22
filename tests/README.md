# Cross-cutting tests

Repository-level tests that are not tied to a single Maven module. Most automated coverage lives
next to source (`apps/backend/src/test`, `packages/java/*/src/test`, and so on).

## Current contents

| Path                                                             | What it covers                                      |
| ---------------------------------------------------------------- | --------------------------------------------------- |
| [`frontend/graph-store.test.mjs`](frontend/graph-store.test.mjs) | Frontend graph-store unit checks (Node test runner) |

## Planned layout

These directories are reserved for future cross-module suites:

| Directory      | Intended use                                              |
| -------------- | --------------------------------------------------------- |
| `api/`         | HTTP contract and smoke tests beyond backend module tests |
| `e2e/`         | End-to-end workflows across services                      |
| `integration/` | Multi-module integration scenarios                        |
| `mde/`         | MDE pipeline regression suites                            |
| `websocket/`   | Realtime assistant transport behavior                     |

Run the full Java regression suite from the repository root with `mvn test` or
`python scripts/verify.py`.
