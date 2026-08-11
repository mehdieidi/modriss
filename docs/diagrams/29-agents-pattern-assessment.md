# Varka AI Assistant: Anthropic Pattern Assessment

This diagram classifies the current unified implementation using Anthropic's [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents). The teal paths are implemented; grey items are intentional runtime exclusions.

```mermaid
flowchart TB
    user["User request<br/>selected elements / source attachment"]
    durable["Deterministic durable-turn workflow<br/>accept -> queue -> claim -> persist -> SSE"]
    strategy{"Strict strategy enum<br/>conceptual / inspect / answer"}
    subgraph augmented["Implemented internal workflows"]
        context["Augmentations<br/>model inventory, memory<br/>retrieval, source units / source map"]
        conceptual["Prompt chain<br/>type selection -> complete conceptual IR"]
        compiler["Deterministic Ecore compiler<br/>complete-response repair"]
        agent["Inspect/contract agent loop<br/>step / call / time budgets"]
        action{"Strict JSON AgentAction<br/>Arvan production protocol"}
        plan["plan_model_edit"]
        inspect["inspect_model"]
        types["describe_types"]
        terminal["answer_user / ask_user"]
        commit["commit_model_batch"]
        tools["AgentModelTools + ModelWorkspace<br/>Ecore checks / evidence / deletion confirmation"]
        validation{"Structural validation<br/>and revision guard"}
        persist["Atomic commit<br/>checkpoint + undo + provenance + coverage"]
    end
    user --> durable --> context --> strategy
    strategy --> conceptual --> compiler --> tools
    strategy --> agent --> action
    strategy -->|ANSWER intention| agent
    action --> plan --> agent
    action --> inspect --> agent
    action --> types --> agent
    action --> terminal --> durable
    action --> commit --> tools --> validation
    validation -- valid --> persist --> durable
    validation -- feedback --> agent
    subgraph excluded["Not implemented as runtime LLM patterns (intentional)"]
        route["Routing to independent specialist agents"]
        parallel["Parallelization / voting"]
        workers["Orchestrator-workers"]
        evaluator["Separate evaluator-optimizer LLM loop"]
    end
    strategy -. "workflow routing, not delegation" .-> route
    agent -. "no parallel provider calls" .-> parallel
    agent -. "no delegated worker models" .-> workers
    validation -. "deterministic feedback" .-> evaluator
    classDef implemented fill:#d9f7ef,stroke:#087f5b,color:#063d2c;
    classDef boundary fill:#e7f0ff,stroke:#4263eb,color:#1c2a5e;
    classDef excluded fill:#f1f3f5,stroke:#868e96,color:#495057,stroke-dasharray: 5 5;
    class user,durable boundary;
    class context,strategy,conceptual,compiler,agent,action,plan,inspect,types,terminal,commit,tools,validation,persist implemented;
    class route,parallel,workers,evaluator excluded;
```

The outer lane is a deterministic workflow. Its semantic router chooses between a conceptual prompt
chain, an action-selecting agent loop, and an answer. There is one provider model and no delegated
specialist agents. See [the detailed assessment](../internal/ai/agents-pattern-assessment.md) and
[the current workflow status](../internal/ai/current-llm-workflow.md).
