# platform-assistant

Feature package for the AI modeling assistant: guarded-apply workflow, ModelDelta
planning, backend patch compilation, session orchestration, and **its own persistence**.

## Package layout

```text
platform.assistant
├── application/     orchestration and catalog facades
├── domain/          records, enums, memory types
├── patch/           patch compiler, schema service, parsers
├── planning/        clarification gate, turn-plan parsing
├── persistence/     feature-owned storage (not platform-storage-postgres)
│   ├── jdbc/        JdbcAssistantMemoryStore, JdbcAssistantCatalog, JdbcAssistantModelContextIndex
│   ├── embedding/   LocalEmbeddingService, EmbeddingSettings
│   └── memory/      SpringAiJdbcChatMemory
├── provider/        AssistantModelProvider port
├── session/         in-memory workflow session state
└── spi/             ports consumed by application code
```

Flyway migrations for assistant tables live in
`src/main/resources/db/assistant-migration/` (V2–V4 and V6–V9). MDE job metadata extension `V5` lives in
`platform-storage-postgres`.

## Delivery layer (`apps/backend`)

The backend wires JDBC beans via `AssistantPersistenceConfig` and keeps
delivery-only adapters: LLM providers, `AssistantToolService` (`@Tool`), WebSocket
hub, and `AiProperties`.

## Public entry points

- `platform.assistant.application.AssistantOrchestrator` — main assistant workflow
- `platform.assistant.delta.DeltaCompiler` — ModelDelta lowering to backend patch IR
- `platform.assistant.patch.AssistantPatchCompiler` — backend patch compilation and inverse patches
- `platform.assistant.provider.AssistantModelProvider` — provider-neutral LLM boundary
- `platform.assistant.spi.*` — ports; JDBC implementations live in `persistence.jdbc`
