# platform-transformation

MDE transformation pipeline and asynchronous job orchestration. Owns `MdeJob*` domain records,
`TransformationService` for synchronous CIM→PIM→PSM and artifact generation, and
`MdeJobService` for async validation and transformation jobs with idempotency and polling.
