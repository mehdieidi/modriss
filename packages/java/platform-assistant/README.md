# Platform assistant

The assistant package contains one modeling-agent runtime:

`AgentTurnLoop -> AgentModelTools -> ModelWorkspace -> AgenticTurnService`

The agent receives deterministic Ecore contracts, uses validated tools for model inspection and
editing, streams activity through the realtime publisher, validates the working copy, and applies
an atomic revision-locked patch. Conversation history, chat memory, proposals, undo, cancellation,
provider hardening, and parallel source-document workers remain supported.

Assistant database migrations live under `src/main/resources/db/assistant-migration/` and begin with
the squashed `V1__assistant_baseline.sql`. The baseline retains conversation, chat-memory,
proposal/undo, audit, and rate-limit tables only. Existing databases must be recreated or
intentionally baselined/repaired before using the squashed migration set.
