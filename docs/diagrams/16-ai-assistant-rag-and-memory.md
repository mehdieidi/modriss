# AI Assistant Contracts, Source Units, and Durable Memory

## Metamodel Contract Use

```mermaid
flowchart TD
    ecore["CIM/PIM/PSM combined Ecore"]
    extractor["EcoreContractExtractor"]
    index["MetamodelKnowledgeIndex"]
    schema["AssistantMetamodelSchemaService"]
    tools["AgentModelTools"]
    workspace["ModelWorkspace"]

    ecore --> extractor --> index --> schema --> tools
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
    participant A as AgentTurnLoop

    Client->>C: Upload .md/.txt/.json attachment
    C->>U: store assistant attachment
    Client->>C: Submit message with attachmentIds
    C->>T: create durable turn with source text
    W->>S: split source text into bounded units and aliases
    W->>T: persist assistant_source_units
    alt large source map
        W->>A: ask for plan_source_model blueprint
        A-->>W: ordered modeling slices
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
    limits
```

Source coverage is a provenance/accounting signal. It says whether tracked source units were
modeled, inferred, or intentionally left as remaining work. Semantic EVL validation is still checked
through the model validation endpoints after a checkpoint.
