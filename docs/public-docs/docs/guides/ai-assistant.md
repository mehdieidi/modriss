# AI modeling assistant

Varka uses one coding-agent-style modeling runtime. A turn runs through the streaming
`AgentTurnLoop`, which receives deterministic Ecore contracts and invokes validated tools over an
in-memory `ModelWorkspace`.

The available tools inspect and edit the working model with `describe_types`, `read_model`,
`search_model`, `create_elements`, `update_elements`, `delete_elements`, `connect_elements`,
`validate_model`, and `plan_work`. Invalid types, attributes, references, containments, and enum
values are rejected by the live metamodel contract before a mutation is applied.

After the turn, the workspace is structurally validated and atomically applied with a revision
lock. The inverse patch is stored as a proposal so users can undo an applied turn. Conversation
history and JDBC chat memory are retained. Large source documents use bounded parallel section
workers before the main agent merges candidates into its workspace.

The realtime API publishes streamed assistant text, plans, tool activity, worker progress, model
deltas, completion/failure, cancellation, and model updates. The assistant has no separate intent
planner, read-only mode, structured-choice workflow, retrieval catalog, embedding store, or
provider-facing JSON mutation protocol.
