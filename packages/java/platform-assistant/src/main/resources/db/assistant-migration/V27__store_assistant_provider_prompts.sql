-- Add migration SQL here.
ALTER TABLE assistant_provider_calls
    ADD COLUMN IF NOT EXISTS system_prompt text,
    ADD COLUMN IF NOT EXISTS user_prompt text;
