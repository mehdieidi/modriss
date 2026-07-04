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
`src/main/resources/db/assistant-migration/` (V2–V4, V6–V9). MDE job metadata extension `V5` lives in
`platform-storage-postgres`.

## Eval and quality gates

Stub eval matrix (CI-safe):

```bash
mvn -pl packages/java/platform-assistant test -Dtest=AssistantEvalRunnerTest
```

Live eval with quality gate assertions (requires `.env` provider credentials):

```powershell
# from repository root
$env:MODLESS_RUN_LIVE_ASSISTANT_EVAL = "true"
$env:MODLESS_WRITE_EVAL_REPORT = "true"
mvn -pl packages/java/platform-assistant test -Dtest=AssistantLiveEvalTest#liveGatePromptsPassQualityGatesWithinTurnBudget
```

Or use `scripts/run-assistant-live-eval.ps1` (bounded 6-prompt gate set; suite budget 15 min, per-turn 5 min).

The full 46-prompt matrix (`assistant-eval-prompts.json`) is for baseline recording only and is disabled in CI by default.

Gate report output: `docs/internal/ai/live-eval-gate-report.md`.

Docker compose smoke (backend readiness + modeling config):

```powershell
./scripts/assistant-compose-smoke.ps1
```

Orchestrator resilience integration tests:

```bash
mvn -pl packages/java/platform-assistant test -Dtest=AssistantOrchestratorResilienceIntegrationTest
```

Stub resilience classification evals:

```bash
mvn -pl packages/java/platform-assistant test -Dtest=AssistantResilienceEvalTest
```

Source-understanding pipeline integration:

```bash
mvn -pl packages/java/platform-assistant test -Dtest=SourceUnderstandingPipelineIntegrationTest
```

Latency JSON is emitted by live eval tests when `MODLESS_WRITE_EVAL_REPORT=true` (see `LatencyReport.toJson()` in gate report writer output).

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
