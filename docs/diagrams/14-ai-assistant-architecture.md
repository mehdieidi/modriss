# Unified AI modeling assistant architecture

## Components and shared durable boundary

```mermaid
flowchart TB
    ui["Frontend chat UI<br/>one assistant, no strategy selector"]
    api["ChatbotController<br/>REST + authenticated SSE"]
    worker["DurableAssistantTurnWorker<br/>lease, source context, durable routing"]
    facade["AgenticAssistantFacade<br/>AgenticTurnService"]
    loop["AgentTurnLoop"]
    strategy{"Strict LLM strategy<br/>CONCEPTUAL_GENERATION<br/>INSPECT_AGENT<br/>ANSWER"}

    subgraph conceptual["Conceptual generation"]
        select["LLM EClass selection<br/>maximum 8 exact names"]
        guide["MetamodelGuideGenerator + TypeContractService<br/>Ecore construction/required closure"]
        ir["Complete paper-style conceptual JSON<br/>temporary/persisted instance IDs"]
        compiler["Conceptual Ecore compiler<br/>attributes / compositions / references / evidence"]
        repair["Bounded complete-document repair"]
    end

    subgraph agent["Inspect/contract action loop"]
        codec["AgentActionCodec"]
        actions["plan_model_edit / inspect_model / describe_types<br/>commit_model_batch / answer_user / ask_user"]
        skills["Workflow-selected assistant skills"]
        tools["AgentModelTools + ModelCommandCompiler"]
    end

    workspace["Private ModelWorkspace"]
    structural["ModelService.validateStructural<br/>structural Ecore/EMF only"]
    commit["Expected-revision atomic commit<br/>checkpoint + inverse patch"]
    turns[("AssistantTurnStore<br/>turns, events, workflows, source, provenance, calls")]
    memory[("Session + JDBC chat memory")]
    model[("Persisted model revisions")]
    provider["OpenAiCompatibleAssistantModelProvider<br/>Arvan / DeepSeek-V4-Flash<br/>JSON content, temperature 0, thinking disabled"]
    guard["PromptGuard + AssistantHardeningService<br/>redaction, timeout, budget, retry, circuit breaker"]

    ui --> api --> turns
    worker --> turns
    worker --> facade --> loop --> strategy
    strategy -->|empty-model mutation| select --> guide --> ir --> compiler
    compiler -->|diagnostic| repair --> ir
    compiler --> workspace
    strategy -->|existing/selected/resumed/destructive mutation| codec --> actions
    skills --> actions --> tools --> workspace
    strategy -->|ANSWER intention; AUTO loop| actions
    loop --> guard --> provider
    select --> guard
    ir --> guard
    workspace --> structural --> commit --> model
    commit --> turns
    facade --> memory
```

The same provider adapter and durable lifecycle serve every strategy. Conceptual generation is not
a separate chatbot or provider. The production router restricts conceptual mutation to fresh empty
models; existing-model, selected-element, resumed, and destructive mutations use the agent path.

## Strategy decision

```mermaid
flowchart TD
    fresh([Fresh unified turn])
    durable{"Persisted plan/checkpoint/<br/>source analysis?"}
    selected{"Selected elements or<br/>confirmed destruction?"}
    empty{"Model structurally empty?"}
    llmEmpty["LLM strict enum<br/>CONCEPTUAL_GENERATION or ANSWER"]
    llmExisting["LLM strict enum<br/>INSPECT_AGENT or ANSWER"]
    conceptual["Conceptual workflow"]
    inspect["Inspect/contract workflow"]
    answer["ANSWER intention<br/>ordinary AUTO action loop"]

    fresh --> durable
    durable -- yes --> inspect
    durable -- no --> selected
    selected -- yes --> inspect
    selected -- no --> empty
    empty -- yes --> llmEmpty
    empty -- no --> llmExisting
    llmEmpty -->|conceptual| conceptual
    llmEmpty -->|ANSWER| answer --> inspect
    llmExisting -->|inspect| inspect
    llmExisting -->|ANSWER| answer
```

No request keyword or fixture phrase participates in routing.

`ANSWER` is not yet an enforced read-only branch. It enters the ordinary AUTO action loop, whose
prompt should select `answer_user` but whose mutation actions remain available.

## Validation boundary

```mermaid
flowchart LR
    output["LLM semantic output"]
    compile["Exact Ecore checks and deterministic compilation"]
    private["Private working model"]
    structural{"Structural Ecore/EMF valid?"}
    diagnostic["Repair diagnostic; persisted model unchanged"]
    atomic["Revision-checked atomic commit"]
    evl["Explicit user validation endpoint / EVL"]

    output --> compile --> private --> structural
    structural -- no --> diagnostic
    structural -- yes --> atomic
    atomic -. "separate, user initiated" .-> evl
```

EVL, stored semantic validation, and full `ModelService.validate(...)` are outside assistant
generation, repair, apply, and commit.
