CREATE TABLE admin_roles
(
    user_id    text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       text        NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER')),
    granted_by text        REFERENCES users (id) ON DELETE SET NULL,
    granted_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, role)
);
CREATE INDEX admin_roles_role_idx ON admin_roles (role);

CREATE TABLE disabled_users
(
    user_id     text        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    disabled_by text        REFERENCES users (id) ON DELETE SET NULL,
    reason      text        NOT NULL DEFAULT '',
    disabled_at timestamptz NOT NULL
);

CREATE TABLE admin_audit_events
(
    id          text        PRIMARY KEY,
    actor_id    text        REFERENCES users (id) ON DELETE SET NULL,
    action      text        NOT NULL,
    target_type text        NOT NULL,
    target_id   text        NOT NULL,
    reason      text        NOT NULL DEFAULT '',
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    request_id  text,
    created_at  timestamptz NOT NULL
);
CREATE INDEX admin_audit_events_created_idx ON admin_audit_events (created_at DESC);
CREATE INDEX admin_audit_events_target_idx ON admin_audit_events (target_type, target_id, created_at DESC);
CREATE INDEX admin_audit_events_actor_idx ON admin_audit_events (actor_id, created_at DESC);
