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
mvn -q -pl packages/java/platform-storage-postgres -am test
mvn test
```

Use the `onnx-embeddings` Maven profile only when testing the native ONNX embedding path.

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
7. Exercise assistant explanation and proposal flows.
8. Download the artifact ZIP and run its generated validation and tests.
9. Deploy the generated project to LocalStack and verify runtime effects.

## What to Test After Language Changes

Verify formal structure, UI metadata coverage, JSON/XMI round trips, EVL results, incoming and
outgoing transformations, generated artifacts, assistant retrieval and patches, existing stored
model compatibility, and public documentation.
