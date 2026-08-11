# Testing

## One-command verification

```powershell
python scripts/verify.py
```

This runs format check, all linters, and `mvn test`. Pre-commit runs format check only on `git commit`.

## Repository Test Ladder

Run focused checks first, then broader regression tests.

```powershell
# Metamodel compiler and modeling configuration
mvn -q -pl tools/mde-cli -am test
mvn -q -pl packages/java/platform-modeling -am test

# Validation
mvn -q -pl packages/java/mde-evl-validator -am test
mvn -q -pl tools/mde-evl-cli -am test

# Transformations
mvn -q -pl packages/java/mde-etl-runner -am test
mvn -q -pl tools/mde-etl-cli -am test
mvn -q -pl packages/java/platform-transformation -am test
mvn -q -pl packages/java/platform-model -am test

# Generation
mvn -q -pl packages/java/mde-m2t-runner -am test
mvn -q -pl tools/mde-m2t-cli -am test

# Backend, storage, and complete regression
mvn -q -pl apps/backend -am test
mvn -q -pl packages/java/platform-assistant -am test
mvn -q -pl packages/java/platform-storage-postgres -am test
mvn test

# JavaScript frontend checks
npm run test:frontend
```

Use the `onnx-embeddings` Maven profile only when testing the native ONNX embedding path.

## LLM Provider Tests

Provider-dependent tests must be tagged with JUnit `@Tag("llm-provider")`. The default Maven test
run excludes that tag, so this command stays local and deterministic:

```powershell
mvn clean test
```

Run live provider tests explicitly when a compatible provider is configured:

```powershell
mvn test -Pllm-provider-tests -Dgroups=llm-provider
```

Live provider tests use the Arvan OpenAI-compatible configuration in the ignored `.env`:
`VARKA_AI_PROVIDER=openai`, the Arvan `OPENAI_COMPATIBLE_BASE_URL` and key,
`VARKA_AI_MODEL=DeepSeek-V4-Flash`, JSON schema protocol, native tools disabled, and unified mode.
Use `VARKA_AI_TEST_MODEL` only for an intentional evaluation override.

## PostgreSQL integration tests

Storage and backend integration tests use **Testcontainers** with the `pgvector/pgvector:pg16`
image. Docker must be running locally and in CI — no manual PostgreSQL setup is required.

Additional coverage:

- `PostgresPlatformStoreIntegrationTest` — storage CRUD and blob operations
- `FlywayMigrationVerificationTest` — migrations from empty schema
- `OpenApiContractTest` — Spring MVC handlers vs `docs/api/openapi/openapi.yaml`
- `ApiSmokeContractTest` — auth, project, and model lifecycle smoke tests

## Formatting Verification

```powershell
python scripts/format.py --check
```

## Manual End-to-End Smoke Test

1. Start PostgreSQL and backend.
2. Load the frontend and modeling configuration.
3. Register, create a project, and create or import a CIM.
4. Save, reload, export, import, and validate the model.
5. Run CIM-to-PIM, PIM-to-PSM, and PSM-to-artifact.
6. Inspect transformed models and generated files.
7. Exercise assistant explanation, durable turn events, checkpoint, confirmation, continue, cancel,
   and undo flows.
8. Download the artifact ZIP and run its generated validation and tests.
9. Deploy the generated project to LocalStack and verify runtime effects.

## Assistant live evaluation

For the required unified chatbot fixtures, rebuild the Docker Compose backend, verify health, and
use the durable live-eval gate:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-assistant-live-eval.ps1 `
  -FixtureId create-cim-library `
  -ReportPath target\unified-create-cim-library.md
```

Run the same command for `cim-feature-evolution`, `source-to-cim-pantry`, and
`create-pim-serverless`. The script registers a temporary user, creates project/model state,
uploads fixture source when required, submits durable turns, follows checkpoints/terminal state,
fetches the model, and records calls, tokens, latency, coverage, preservation, structure, and
validation evidence.

Do not reduce acceptance to provider connectivity or one stochastic success. Preserve every failed
and successful report. Current unified evidence passes feature evolution, source-backed pantry, and
serverless PIM; the final library rerun failed on length-limited output. The assistant gates output
with structural Ecore/EMF validation only; run EVL separately only when a user-oriented test
explicitly needs semantic review feedback.

## What to Test After Language Changes

Verify formal structure, UI metadata coverage, JSON/XMI round trips, EVL results, incoming and
outgoing transformations, generated artifacts, assistant metamodel contracts and durable turn
patches, existing stored model compatibility, and public documentation.
