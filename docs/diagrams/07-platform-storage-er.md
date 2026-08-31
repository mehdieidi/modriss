# Platform Storage ER Diagram

This is the complete platform schema from Flyway migrations V1, V5, V9, and V32 (with later
platform migrations adding unrelated operational tables/columns).

```mermaid
erDiagram
    USERS {
        text id PK
        text email UK
        text display_name
        text password_hash
        text salt
        timestamptz created_at
        timestamptz updated_at
    }
    AUTH_SESSIONS {
        text token PK
        text user_id FK
        timestamptz created_at
        timestamptz expires_at
    }
    PROJECTS {
        text id PK
        text name
        text description
        text owner_user_id FK
        timestamptz created_at
        timestamptz updated_at
    }
    PROJECT_MEMBERS {
        text project_id PK,FK
        text user_id PK,FK
        text email_snapshot
        text display_name_snapshot
        text role
        timestamptz added_at
    }
    PROJECT_ACTIVE_MODELS {
        text project_id PK,FK
        text model_key PK
        text model_id
    }
    MODELS {
        text id PK
        text project_id FK
        text level
        text name
        jsonb model_json
        text metamodel_version
        text metamodel_hash
        bigint revision
        bytea source_xmi
        text source_xmi_hash
        text migration_state
        timestamptz created_at
        timestamptz updated_at
    }
    STAGED_IMPORTS {
        text token PK
        text user_id FK
        text project_id FK
        text level
        bigint size_bytes
        timestamptz created_at
        timestamptz expires_at
    }
    STAGED_IMPORT_PAYLOADS {
        text token PK,FK
        bytea payload
    }
    ARTIFACTS {
        text id PK
        text project_id FK
        text name
        jsonb model_json
        timestamptz created_at
        timestamptz updated_at
    }
    ARTIFACT_FILES {
        text artifact_id PK,FK
        text path PK
        text content
    }
    MDE_JOBS {
        text id PK
        text project_id FK
        text user_id FK
        text source_model_id
        text source_level
        bigint source_revision
        text source_model_hash
        text operation
        text status
        integer progress_percent
        text result_model_id
        text result_artifact_id
        timestamptz created_at
        timestamptz started_at
        timestamptz finished_at
    }
    MDE_JOB_DIAGNOSTICS {
        text job_id PK,FK
        integer position PK
        text diagnostic
    }
    MODEL_SYNCHRONIZATION_RECORDS {
        text project_id PK,FK
        text record_kind PK
        text record_id PK
        jsonb payload
        timestamptz updated_at
    }

    USERS ||--o{ AUTH_SESSIONS : authenticates
    USERS ||--o{ PROJECTS : owns
    USERS ||--o{ PROJECT_MEMBERS : participates
    PROJECTS ||--o{ PROJECT_MEMBERS : grants
    PROJECTS ||--o{ PROJECT_ACTIVE_MODELS : selects
    PROJECTS ||--o{ MODELS : contains
    USERS ||--o{ STAGED_IMPORTS : uploads
    PROJECTS ||--o{ STAGED_IMPORTS : receives
    STAGED_IMPORTS ||--|| STAGED_IMPORT_PAYLOADS : stores
    PROJECTS ||--o{ ARTIFACTS : contains
    ARTIFACTS ||--o{ ARTIFACT_FILES : expands_to
    PROJECTS ||--o{ MDE_JOBS : tracks
    USERS ||--o{ MDE_JOBS : submits
    MDE_JOBS ||--o{ MDE_JOB_DIAGNOSTICS : reports
    PROJECTS ||--o{ MODEL_SYNCHRONIZATION_RECORDS : owns
```

## Logical PlatformStore Mapping

```mermaid
flowchart LR
    services["Application services"]
    paths["Logical path keys<br/>projects/... models/... artifacts/... jobs/..."]
    store["PlatformStore port"]
    adapter["PostgresPlatformStore<br/>Maps record type/path to SQL"]
    tx["TransactionTemplate"]
    tables[("Platform tables incl. V32 synchronization records")]

    services --> paths --> store --> adapter
    adapter --> tx --> tables
```
