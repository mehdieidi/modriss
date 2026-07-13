-- Durable turns and checkpoints replace proposal state. Existing assistant-only proposal history
-- is intentionally discarded; projects and ordinary model revisions are untouched.
ALTER TABLE assistant_action_audits DROP COLUMN IF EXISTS proposal_id;
DROP TABLE IF EXISTS assistant_proposals;
