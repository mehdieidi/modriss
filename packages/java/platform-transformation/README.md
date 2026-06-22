# platform-transformation

MDE transformation pipeline and asynchronous job orchestration.

- **`TransformationService`** — runs CIM→PIM, PIM→PSM, and PSM→artifact generation (used by job
  workers and tests).
- **`MdeJobService`** — submits transformations and stored-model validation as async jobs with
  idempotency keys, polling, cancellation, and diagnostics (`MdeJob*` domain records).

REST clients call `TransformationController`, which submits through `MdeJobService` and returns
`202 Accepted` with a job id.
