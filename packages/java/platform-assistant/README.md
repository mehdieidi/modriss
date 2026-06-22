# platform-assistant

AI modeling assistant for the Modless platform. Owns the guarded-apply workflow,
semantic patch pipeline, turn planning, and session orchestration in
`platform.assistant.*` packages. Spring Boot adapters (providers, JDBC, WebSocket)
live in `apps/backend`.

## Public entry points

- `platform.assistant.application.AssistantOrchestrator` — main assistant workflow
- `platform.assistant.patch.AssistantPatchCompiler` — semantic patch compilation
- `platform.assistant.provider.AssistantModelProvider` — provider-neutral LLM boundary
- `platform.assistant.spi.*` — ports implemented by the backend delivery layer
