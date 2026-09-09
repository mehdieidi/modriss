# Unified AI modeling assistant architecture

```mermaid
flowchart TB
    UI["Frontend chatbot<br/>CIM/PIM only"]
    API["ChatbotController<br/>REST + authenticated SSE"]
    Store[("AssistantTurnStore<br/>turns, events, workflows, work items,<br/>checkpoints, provenance, provider audit")]
    Worker["DurableAssistantTurnWorker<br/>lease, deadline, cancellation, resume"]
    Loop["AgentTurnLoop ADAPTIVE"]
    Strategy{"LLM closed strategy"}

    subgraph CG["Conceptual generation and additive evolution"]
        Obligations["LLM obligation ledger<br/>mandatory/optional + candidate EClasses"]
        Types["LLM exact-EClass selection<br/>combined closure/capacity checks"]
        Blueprint["LLM stable-ID blueprint<br/>containment, references, obligations, slices"]
        Slices["Private durable slice generation<br/>2 objects, fallback to 1 on truncation"]
        Review["Optional LLM obligation review<br/>enabled by configuration"]
        Compiler["Deterministic Ecore compiler<br/>ModelCommandBatch"]
    end

    subgraph IA["Surgical/selected/resumed/destructive path"]
        Actions["plan_model_edit / inspect_model / describe_types<br/>commit_model_batch / answer_user / ask_user"]
        Tools["AgentModelTools + exact Ecore contracts"]
    end

    ReadOnly["EXPLAIN_MODEL<br/>read-only answer"]
    Provider["OpenAiCompatibleAssistantModelProvider<br/>Arvan Gemma-4-31B-IT<br/>JSON content, temperature 0"]
    Guard["Prompt guard + hardening<br/>budgets, timeout, retry, circuit breaker, audit"]
    Workspace["Private ModelWorkspace"]
    Structural["ModelService.validateStructural(...)<br/>structural Ecore/EMF only"]
    Commit["Expected-revision atomic checkpoint<br/>inverse patch + validation summary"]
    Model[("Persisted model revision")]

    UI --> API --> Store
    Worker --> Store
    Worker --> Loop --> Strategy
    Strategy -->|CONCEPTUAL_GENERATION| Obligations --> Types --> Blueprint --> Slices
    Slices -->|review enabled| Review --> Compiler
    Slices -->|review disabled| Compiler
    Strategy -->|INSPECT_AGENT| Actions --> Tools
    Strategy -->|ANSWER| ReadOnly
    Obligations --> Guard --> Provider
    Types --> Guard
    Blueprint --> Guard
    Slices --> Guard
    Review --> Guard
    Actions --> Guard
    Compiler --> Workspace
    Tools --> Workspace
    Workspace --> Structural --> Commit --> Model
    Commit --> Store
```

The product exposes one chatbot, not separate conceptual and agent assistants. Deterministic state
restricts legal strategies, but the LLM owns intent interpretation, obligation mapping, EClass
selection, objects, relationships, concrete subtype choice, and satisfaction review.

For an existing non-destructive model, the LLM may select either conceptual evolution or the
inspect/contract loop. Selected-element, resumed, and destructive work remains inspect-only, and
destructive mutations additionally require confirmation.

The blueprint schema is capped at 18 objects/types and effective capacity is lower when required
closure or provider-call reserves consume budget. Conceptual objects remain private until all
mandatory obligations are proven, deterministic compilation succeeds, and structural validation
passes.

Assistant paths never invoke EVL or full semantic validation. PSM assistant sessions are rejected;
PSM transformation and validation remain separate platform workflows.
