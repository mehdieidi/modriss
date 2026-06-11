CREATE TABLE users
(
    id            text PRIMARY KEY,
    email         text        NOT NULL UNIQUE,
    display_name  text        NOT NULL,
    password_hash text        NOT NULL,
    salt          text        NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);

CREATE TABLE auth_sessions
(
    token      text PRIMARY KEY,
    user_id    text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX auth_sessions_expires_at_idx ON auth_sessions (expires_at);

CREATE TABLE projects
(
    id            text PRIMARY KEY,
    name          text        NOT NULL,
    description   text        NOT NULL DEFAULT '',
    owner_user_id text        NOT NULL REFERENCES users (id),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
CREATE INDEX projects_owner_user_id_idx ON projects (owner_user_id);

CREATE TABLE project_members
(
    project_id            text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id               text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email_snapshot        text        NOT NULL,
    display_name_snapshot text        NOT NULL,
    role                  text        NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    added_at              timestamptz NOT NULL,
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX project_members_user_id_idx ON project_members (user_id);

CREATE TABLE project_active_models
(
    project_id text NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    model_key  text NOT NULL,
    model_id   text NOT NULL,
    PRIMARY KEY (project_id, model_key)
);

CREATE TABLE models
(
    id                text PRIMARY KEY,
    project_id        text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    level             text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    name              text        NOT NULL,
    model_json        jsonb       NOT NULL,
    metamodel_version text,
    metamodel_hash    text,
    revision          bigint      NOT NULL CHECK (revision >= 1),
    source_xmi        bytea,
    source_xmi_hash   text,
    migration_state   text,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    UNIQUE (project_id, level, id)
);
CREATE INDEX models_project_level_updated_idx ON models (project_id, level, updated_at DESC);

CREATE TABLE staged_imports
(
    token      text PRIMARY KEY,
    user_id    text REFERENCES users (id) ON DELETE CASCADE,
    project_id text REFERENCES projects (id) ON DELETE CASCADE,
    level      text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    size_bytes bigint      NOT NULL CHECK (size_bytes >= 0),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE TABLE staged_import_payloads
(
    token   text PRIMARY KEY REFERENCES staged_imports (token) ON DELETE CASCADE,
    payload bytea NOT NULL
);
CREATE INDEX staged_imports_expires_at_idx ON staged_imports (expires_at);

CREATE TABLE artifacts
(
    id         text PRIMARY KEY,
    project_id text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       text        NOT NULL,
    model_json jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX artifacts_project_updated_idx ON artifacts (project_id, updated_at DESC);

CREATE TABLE artifact_files
(
    artifact_id text NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
    path        text NOT NULL,
    content     text NOT NULL,
    PRIMARY KEY (artifact_id, path)
);

CREATE TABLE mde_jobs
(
    id                 text PRIMARY KEY,
    project_id         text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id            text        NOT NULL REFERENCES users (id),
    source_model_id    text        NOT NULL,
    source_level       text        NOT NULL CHECK (source_level IN ('CIM', 'PIM', 'PSM')),
    source_revision    bigint      NOT NULL,
    source_model_hash  text        NOT NULL,
    operation          text        NOT NULL,
    status             text        NOT NULL,
    progress_percent   integer     NOT NULL CHECK (progress_percent BETWEEN 0 AND 100),
    result_model_id    text,
    result_artifact_id text,
    created_at         timestamptz NOT NULL,
    started_at         timestamptz,
    finished_at        timestamptz
);
CREATE INDEX mde_jobs_project_created_idx ON mde_jobs (project_id, created_at DESC);
CREATE INDEX mde_jobs_status_idx ON mde_jobs (status);

CREATE TABLE mde_job_diagnostics
(
    job_id     text    NOT NULL REFERENCES mde_jobs (id) ON DELETE CASCADE,
    position   integer NOT NULL,
    diagnostic text    NOT NULL,
    PRIMARY KEY (job_id, position)
);
