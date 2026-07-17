CREATE TABLE guest_accounts
(
    user_id      text PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    prompt_limit integer     NOT NULL CHECK (prompt_limit > 0),
    prompts_used integer     NOT NULL DEFAULT 0 CHECK (prompts_used >= 0),
    created_at   timestamptz NOT NULL
);

CREATE INDEX guest_accounts_created_at_idx ON guest_accounts (created_at DESC);
