# Assistant agent-pattern assessment

```mermaid
flowchart TB
    Worker["Durable worker"] --> Router{"LLM closed strategy"}
    Router -->|fresh empty model| Chain["Prompt chain<br/>obligations -> types -> blueprint -> slices"]
    Chain --> Reviewer["Independent obligation evaluator"]
    Reviewer --> Compiler["Deterministic Ecore compiler"]
    Router -->|existing/resumed/destructive| Agent["Inspect/contract action loop"]
    Router -->|answer| ReadOnly["Read-only explanation"]
    Compiler --> Workspace["Private workspace"]
    Agent --> Workspace
    Workspace --> Structural["Structural-only validation"]
    Structural --> Commit["Atomic checkpoint"]
```

The assistant combines routing, prompt chaining, checked tool use, an independent semantic
evaluator, deterministic structural enforcement, and durable human controls. It remains one
assistant runtime; semantic decisions are LLM-owned and mechanisms are backend-owned.
