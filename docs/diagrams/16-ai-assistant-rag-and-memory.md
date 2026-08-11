# AI Assistant Contracts, Source Units, and Durable Memory

## Metamodel Contract Use

```mermaid
flowchart TD
    ecore["CIM/PIM/PSM combined Ecore"]
    extractor["EcoreContractExtractor"]
    index["MetamodelKnowledgeIndex"]
    knowledge["MetamodelKnowledgeService"]
    guide["MetamodelGuideGenerator"]
    contracts["TypeContractService"]
    conceptual["ConceptualInstanceModelWorkflow"]
    schema["AssistantMetamodelSchemaService"]
    tools["AgentModelTools"]
    workspace["ModelWorkspace"]

    ecore --> extractor --> index
    index --> knowledge --> guide --> conceptual
    knowledge --> contracts --> conceptual
    index --> schema --> tools
    tools --> workspace
    schema -->|"types, attributes, references, containments, enum values"| tools
```

## Source-Backed Turn Provenance

```mermaid
sequenceDiagram
    actor Client
    participant C as ChatbotController
    participant U as UploadService
    participant W as DurableAssistantTurnWorker
    participant S as SourceUnitSplitter
    participant T as AssistantTurnStore
    participant A as AgentTurnLoop / Conceptual Workflow

    Client->>C: Upload .md/.txt/.json attachment
    C->>U: store assistant attachment
    Client->>C: Submit message with attachmentIds
    C->>T: create durable turn with source text
    W->>S: split source text into bounded units and aliases
    W->>T: persist assistant_source_units
    alt durable inspect/contract source workflow
        W->>A: source units + persisted plan/work-item context
        A-->>W: modeling plan and atomic source-grounded slice
    else bounded conceptual source workflow
        W->>A: selected source units + exact Ecore contracts
        A-->>W: one complete conceptual document with evidence
    end
    W->>A: run turn with source units, contracts, and model tools
    A-->>W: committed elements with source-grounded/inferred labels
    W->>T: persist assistant_element_provenance and coveragePercent
```

## Durable Memory Layout

```mermaid
flowchart LR
    thread["assistant_threads<br/>user/project/level scope"]
    messages["assistant_messages<br/>durable audit history"]
    spring["SPRING_AI_CHAT_MEMORY<br/>recent chat window"]
    summaries["assistant_thread_summaries<br/>rolling summary"]
    turns["assistant_turns<br/>state, idempotency, deadlines, counters"]
    events["assistant_turn_events<br/>replay cursor and payload"]
    checkpoints["assistant_checkpoints<br/>model revision and inverse patch"]
    sources["assistant_source_units"]
    provenance["assistant_element_provenance"]
    calls["assistant_provider_calls"]
    audits["assistant_action_audits"]
    workflows["assistant_workflows + assistant_work_items<br/>durable strategy/plan progress"]
    facts["assistant_source_facts + assistant_source_blueprints<br/>context cache"]
    validation["assistant_validation_attempts"]
    limits["assistant_rate_limits<br/>schema reserved"]

    thread --> messages
    thread --> spring
    thread --> summaries
    thread --> turns
    turns --> events
    turns --> checkpoints
    turns --> sources --> provenance
    turns --> calls
    turns --> audits
    turns --> workflows
    turns --> facts
    turns --> validation
    limits
```

Source coverage is a provenance/accounting signal. It says whether tracked source units were
modeled, inferred, or intentionally left as remaining work. Assistant checkpoints do not execute EVL
or require semantic EVL validity; model validation endpoints remain available for explicit
post-checkpoint review.

The inspect/contract path can persist source plans and work items across continuations. The current
conceptual path is bounded to one complete response and does not yet persist a cross-response
instance ledger.
