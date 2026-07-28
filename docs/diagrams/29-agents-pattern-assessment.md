# Varka AI Assistant: Anthropic Pattern Assessment

This diagram classifies the implementation using Anthropic's [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents). The teal path is implemented; grey items are intentional runtime exclusions.

```mermaid
flowchart TB
    user["User request<br/>selected elements / source attachment"]
    durable["Deterministic durable-turn workflow<br/>accept -> queue -> claim -> persist -> SSE"]
    subgraph augmented["Implemented: augmented LLM + bounded single agent"]
        context["Augmentations<br/>model inventory, memory<br/>retrieval, source units / source map"]
        agent["AgentTurnLoop<br/>step / call / time budgets"]
        action{"Strict AgentAction<br/>or native tool call"}
        plan["plan_source_model"]
        inspect["inspect_model"]
        types["describe_types"]
        terminal["answer_user / ask_user"]
        commit["commit_model_batch"]
        tools["AgentModelTools + ModelWorkspace<br/>Ecore checks / evidence / deletion confirmation"]
        validation{"Structural validation<br/>and revision guard"}
        persist["Atomic commit<br/>checkpoint + undo + provenance + coverage"]
    end
    user --> durable --> context --> agent --> action
    action --> plan --> agent
    action --> inspect --> agent
    action --> types --> agent
    action --> terminal --> durable
    action --> commit --> tools --> validation
    validation -- valid --> persist --> durable
    validation -- feedback --> agent
    subgraph excluded["Not implemented as runtime LLM patterns (intentional)"]
        route["Routing to specialist agents"]
        parallel["Parallelization / voting"]
        workers["Orchestrator-workers"]
        evaluator["Separate evaluator-optimizer LLM loop"]
    end
    agent -. "one responder role; ADR-004" .-> route
    agent -. "no parallel provider calls" .-> parallel
    agent -. "no delegated worker models" .-> workers
    validation -. "deterministic feedback" .-> evaluator
    classDef implemented fill:#d9f7ef,stroke:#087f5b,color:#063d2c;
    classDef boundary fill:#e7f0ff,stroke:#4263eb,color:#1c2a5e;
    classDef excluded fill:#f1f3f5,stroke:#868e96,color:#495057,stroke-dasharray: 5 5;
    class user,durable boundary;
    class context,agent,action,plan,inspect,types,terminal,commit,tools,validation,persist implemented;
    class route,parallel,workers,evaluator excluded;
```

The outer lane is a deterministic workflow. The inner lane is an agent because the LLM chooses an allowed action after seeing tool-produced ground truth. See [the detailed assessment](../internal/ai/agents-pattern-assessment.md) and [the current workflow status](../internal/ai/current-llm-workflow.md).
