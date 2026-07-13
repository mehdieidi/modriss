# ADR-003: Explicit agent tool loop

The backend owns the request/tool/validation loop. Providers return one strict `AgentAction`; no
provider-managed automatic tool loop, streamed reasoning, or post-content retry is permitted.
