# Assistant Storage ER Diagram

This is the durable assistant schema from `V14__assistant_baseline.sql` plus the later assistant
migrations through V18.

```mermaid
erDiagram
    USERS {
        text id PK
    }
    PROJECTS {
        text id PK
    }
    ASSISTANT_THREADS {
        text id PK
        text user_id FK
        text project_id FK
        text level
        text active_model_id
        bigint active_revision
        text title
        timestamptz created_at
        timestamptz updated_at
    }
    ASSISTANT_TURNS {
        text id PK
        text thread_id FK
        text user_id FK
        text project_id FK
        text level
        text model_id
        bigint expected_revision
        text idempotency_key
        text state
        timestamptz accepted_at
        timestamptz deadline_at
        boolean cancellation_requested
        bigint revision
        integer checkpoint_count
        integer saved_element_count
        integer coverage_percent
        text remaining_work
        text final_message
    }
    ASSISTANT_TURN_EVENTS {
        bigserial id PK
        text turn_id FK
        bigint sequence
        text type
        timestamptz occurred_at
        jsonb payload
    }
    ASSISTANT_CHECKPOINTS {
        bigserial id PK
        text turn_id FK
        text model_id
        bigint revision
        jsonb inverse_patch
        timestamptz created_at
    }
    ASSISTANT_SOURCE_UNITS {
        text id PK
        text turn_id FK
        integer ordinal
        integer start_offset
        integer end_offset
        text content
        text status
        text reason
    }
    ASSISTANT_ELEMENT_PROVENANCE {
        bigserial id PK
        text turn_id FK
        text element_id
        text source_unit_id FK
        text kind
        text assumption
    }
    ASSISTANT_PROVIDER_CALLS {
        bigserial id PK
        text turn_id FK
        text provider
        text model
        timestamptz started_at
        bigint latency_ms
        bigint prompt_tokens
        bigint completion_tokens
        text finish_reason
        text error
    }
    ASSISTANT_MESSAGES {
        text id PK
        text thread_id FK
        text role
        text content
        jsonb metadata
        timestamptz created_at
    }
    SPRING_AI_CHAT_MEMORY {
        text conversation_id
        text content
        varchar type
        timestamp timestamp
    }
    ASSISTANT_THREAD_SUMMARIES {
        text thread_id PK,FK
        text summary
        text message_id FK
        timestamptz updated_at
    }
    ASSISTANT_ACTION_AUDITS {
        text id PK
        text turn_id FK
        text project_id FK
        text actor_id FK
        text action
        jsonb details
        timestamptz created_at
    }
    ASSISTANT_RATE_LIMITS {
        text key PK
        timestamptz window_start
        integer count
    }

    USERS ||--o{ ASSISTANT_THREADS : owns
    PROJECTS ||--o{ ASSISTANT_THREADS : scopes
    ASSISTANT_THREADS ||--o{ ASSISTANT_MESSAGES : records
    ASSISTANT_THREADS ||--o{ ASSISTANT_TURNS : accepts
    ASSISTANT_THREADS ||--o| ASSISTANT_THREAD_SUMMARIES : summarizes
    ASSISTANT_MESSAGES o|--o| ASSISTANT_THREAD_SUMMARIES : summary_through
    ASSISTANT_TURNS ||--o{ ASSISTANT_TURN_EVENTS : emits
    ASSISTANT_TURNS ||--o{ ASSISTANT_CHECKPOINTS : checkpoints
    ASSISTANT_TURNS ||--o{ ASSISTANT_SOURCE_UNITS : splits
    ASSISTANT_TURNS ||--o{ ASSISTANT_ELEMENT_PROVENANCE : records
    ASSISTANT_SOURCE_UNITS o|--o{ ASSISTANT_ELEMENT_PROVENANCE : grounds
    ASSISTANT_TURNS ||--o{ ASSISTANT_PROVIDER_CALLS : calls
    ASSISTANT_TURNS ||--o{ ASSISTANT_ACTION_AUDITS : audits
    PROJECTS ||--o{ ASSISTANT_ACTION_AUDITS : scopes
    USERS ||--o{ ASSISTANT_ACTION_AUDITS : acts
```

`SPRING_AI_CHAT_MEMORY` and `ASSISTANT_RATE_LIMITS` have logical keys but intentionally no foreign
keys to the application tables.
