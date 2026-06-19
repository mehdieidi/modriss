# platform-model

Model workspace lifecycle for CIM, PIM, and PSM. Owns model records, indexes, staging tokens,
and optimistic-concurrency locks in `platform.model.domain`. Application services:
`ModelService` (CRUD facade), `ModelValidationService` (EVL and JSON checks),
`ModelImportExportService` (import/export and XMI sidecars), `ModelLockService`, and
`StoredViewLayoutService` (applies layout to persisted view JSON via `ModelService`).
