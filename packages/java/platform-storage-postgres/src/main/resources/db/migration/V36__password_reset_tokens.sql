CREATE TABLE password_reset_tokens
(
    id          text PRIMARY KEY,
    user_id     text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    secret_hash text        NOT NULL,
    expires_at  timestamptz NOT NULL
);

CREATE INDEX password_reset_tokens_user_id_idx ON password_reset_tokens (user_id);
