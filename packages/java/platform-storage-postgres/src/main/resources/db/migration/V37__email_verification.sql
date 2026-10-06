ALTER TABLE users
    ADD COLUMN email_verified boolean NOT NULL DEFAULT true;

CREATE TABLE email_verification_tokens
(
    id          text PRIMARY KEY,
    user_id     text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    secret_hash text        NOT NULL,
    expires_at  timestamptz NOT NULL
);

CREATE INDEX email_verification_tokens_user_id_idx ON email_verification_tokens (user_id);
