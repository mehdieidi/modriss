# Serverless suitability analysis

MODRISS applies to projects with a useful serverless implementation, either across the proposed system or within a hybrid architecture. During Phase 0, the Solution Architect assesses the specification before the team commits to modeling. The findings enter the Feasibility and Serverless Suitability Record and inform the G0 decision to pursue, explore, redirect or stop.

This assessment belongs to the [feasibility and serverless suitability task](../process-reference/development-delivery/tasks.md#assess-feasibility-and-serverless-suitability), within method fragment MF-02. The [full-lifecycle process](full-lifecycle-method.md) places it alongside economic appraisal and risk analysis. A favorable recommendation does not authorize the project or establish that its implementation is ready.

## Scope and evidence

Serverless includes managed backend services and several forms of compute. A restriction on a particular Function-as-a-Service (FaaS) product or hosting plan cannot be assumed to apply to every serverless service. Object-oriented code and microservice architectures can run on serverless platforms. Suitability depends on the requirements and deployment choices.

The twelve criteria draw on peer-reviewed studies and first-party technical documentation. The assessment procedure and MODRISS modeling boundary are engineering judgments informed by those sources. Neither the procedure's predictive accuracy nor the accuracy of LLM recommendations has been validated. Published measurements explain tradeoffs; current quotas, prices and service capabilities require a provider check.

Criteria version: 1.0. Research reviewed: 2026-10-25.

## Carrying out the assessment

Start with the project specification and Product and System Charter. Identify the components, triggering events, workload characteristics and mandatory requirements. Record the passages or measurements supporting each finding, keeping assumptions and contradictions separate from confirmed requirements.

Assess C01 to C12 for each relevant component. Use the following statuses in the assessment record:

| Status       | Meaning                                                                                              |
| ------------ | ---------------------------------------------------------------------------------------------------- |
| COMPATIBLE   | The available evidence supports the proposed design against this criterion.                          |
| CONDITIONAL  | Compatibility depends on a stated mitigation, decision or experiment.                                |
| INCOMPATIBLE | The proposed design conflicts with a requirement. Identify whether an acceptable alternative exists. |
| UNKNOWN      | Missing or contradictory information prevents a finding.                                             |

A hard blocker is a non-negotiable requirement that the proposed deployment cannot meet and for which no acceptable mitigation exists. Advantages elsewhere cannot compensate for that conflict. Missing information cannot count as evidence of compatibility. State, relational data, machine learning, regulation, steady demand and WebSockets each require examination; none alone warrants rejection.

Compare serverless, hybrid and non-serverless alternatives. For material uncertainty, define a bounded experiment or a question, with an owner and a decision deadline. Confidence should reflect the completeness of the evidence. High confidence is inappropriate while decisive questions remain unanswered.

## Assessment criteria

Source identifiers link to the [research and technical references](#research-and-technical-references). Each row identifies evidence to seek, rather than a score to add to a total.

| ID  | Dimension and signs of a fit                                                                                                                    | Constraints to examine                                                                                                                            | Evidence or experiment needed                                                                                                                                      | Basis                           |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| C01 | Workload shape: event-triggered, intermittent or bursty work that can scale independently                                                       | Steady utilization may favor reserved capacity or containers. Burst scaling also has limits.                                                      | Arrival distribution, peak concurrency, idle time and expected growth; a representative load test                                                                  | [A1](#a1), [I1](#i1)            |
| C02 | Execution and resources: tasks with bounded duration that can restart                                                                           | Indivisible long tasks, accelerators, memory needs or specific OS, process and runtime requirements may need another compute service.             | Task duration and resource needs, provider and plan quotas, and whether work can be partitioned                                                                    | [A3](#a3), [I1](#i1), [I3](#i3) |
| C03 | Latency: asynchronous work or response times supported by measurements                                                                          | Startup delays, network hops, scaling and execution variance can breach tail-latency targets or deadlines. Provisioned capacity changes the cost. | End-to-end p95/p99 latency and deadline measurements under cold starts, warm execution, bursts and failures                                                        | [A2](#a2), [I4](#i4)            |
| C04 | State and data: durable state stored outside ephemeral workers                                                                                  | Local state shared across invocations, data locality, large transfers and database connection pressure need attention.                            | State lifecycle, data volume and location, consistency requirements and connection limits; tests with realistic data                                               | [A3](#a3), [I2](#i2)            |
| C05 | Reliability and consistency: retry-safe events and defined transaction boundaries                                                               | Duplicate delivery, ordering, lost acknowledgments and atomic updates across services require explicit handling.                                  | Idempotency keys, outbox or deduplication mechanisms, compensation, reconciliation and failure tests; evidence for any exactly-once claim                          | [A3](#a3), [I2](#i2), [I5](#i5) |
| C06 | Composition: APIs, queues, schedules, fan-out and durable workflow steps                                                                        | Frequent synchronous calls, tightly coupled state and long waits inside billed compute can be costly or fragile.                                  | A dependency graph, timeouts, backpressure, orchestration choices and bounded retries                                                                              | [A4](#a4), [I2](#i2)            |
| C07 | Economics: low utilization and less server administration may reduce costs                                                                      | Sustained load, warm capacity, transfers, workflows, logging, managed services and hybrid integration can outweigh those savings.                 | A cost model using representative volumes and sensitivity analysis; comparison with containers and VMs, including staff costs                                      | [A1](#a1), [A3](#a3), [I1](#i1) |
| C08 | Security and governance: the required managed services are permitted and responsibilities are assigned                                          | Residency, isolation, auditing, retention, networking or disconnected operation may rule out a deployment plan.                                   | Applicable policy, permitted regions, services and plans, and designs for identity, secrets, deletion and audit; evidence for certification claims                 | [A3](#a3), [I4](#i4)            |
| C09 | Connectivity and external dependencies: required services are reachable through agreed interfaces                                               | Private or on-premises access, offline operation, session protocols and dependency quotas may constrain the design.                               | A connectivity test and agreed authentication, availability, latency and ownership; consider managed gateways for persistent connections                           | [I1](#i1), [I2](#i2), [I4](#i4) |
| C10 | Delivery and operations: the team can operate distributed managed components                                                                    | Testing, tracing, incident response, deployment control and cost management remain the team's responsibility.                                     | Assigned owners and relevant skills, integration tests, observability, service-level objectives (SLOs), recovery procedures and rollback plans                     | [A4](#a4), [A2](#a2)            |
| C11 | Provider dependence and evolution: the project accepts the proposed managed integrations                                                        | Portability and exit obligations may conflict with proprietary workflow or data semantics.                                                        | Provider restrictions, data export and exit plans, adapter boundaries and a budget for reimplementation                                                            | [A3](#a3), [A4](#a4)            |
| C12 | MODRISS boundary: useful functions, services, events, APIs, data and workflows can be expressed at CIM/PIM and refined through a supported path | A viable serverless design may lack the required metamodel, transformation or generator support. Runtime behavior also needs verification.        | Identify modeled and external components. Check current contracts before naming supported types, and record the implementation and verification work still needed. | [M1](#m1), [M2](#m2)            |

## Recommendation and decision record

| Outcome                  | Grounds for the recommendation                                                                                                                                                                                         |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| SUITABLE                 | A useful serverless implementation fits the requirements and MODRISS scope. Record conditions that must be met before adoption.                                                                                        |
| HYBRID                   | A useful serverless subset fits. Other components can be delivered separately and connected through explicit contracts.                                                                                                |
| UNSUITABLE               | Mandatory constraints prevent a useful MODRISS serverless subset, or the project forbids the separation it would require. A decisive blocker can justify this outcome even when unrelated requirements remain unknown. |
| INSUFFICIENT_INFORMATION | Missing or conflicting information prevents a decision. Ask specific questions and explain how the answers would affect the alternatives.                                                                              |

The Feasibility and Serverless Suitability Record should contain the assessed specification revision, findings by criterion, supporting evidence and assumptions, compared alternatives, recommendation and reasons. Record the proposed modeling boundary, unresolved conditions, experiment owners and deadlines, residual risks and review triggers. Claimed cost savings, certifications, benchmark results or probabilities need evidence of their own.

The architect reviews the findings with the relevant specialists. The project team uses the record, cost model and risk register at G0. Revisit the assessment when requirements, provider plans, prices, quotas or modeling and generation support change.

## Workload patterns and hybrid boundaries

These patterns suggest designs to investigate. Their suitability still depends on the specification.

| Candidate workload                                               | Design to investigate                                                               | Conditions to resolve                                                                                        |
| ---------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Web/mobile backends, webhooks, sporadic CRUD                     | Functions with managed API and data services                                        | Latency, transaction semantics and database connections (C03 to C05)                                         |
| IoT ingestion, file processing, scheduled jobs                   | Queues or events, bounded workers and durable storage                               | Burst limits, data size, duplicate delivery and task resources (C01 to C05)                                  |
| Multi-step approvals or order workflows                          | Durable orchestration or event choreography                                         | A long workflow can consist of short invocations. Specify compensation (C02, C05 to C06).                    |
| GPU training, continuous simulation, media engines, legacy cores | External compute or APIs with serverless ingestion, orchestration or notifications  | Separation must preserve mandatory requirements and leave a useful serverless subset (C02 to C03, C09, C12). |
| Hard real-time control or required disconnected execution        | A local or specialized core, with asynchronous external integration where permitted | Reject MODRISS if no useful serverless subset is permitted (C03, C08 to C09).                                |

In a hybrid design, MODRISS models the serverless subset. Other components are delivered separately and represented through external-system and adapter contracts. For each interface, specify call direction and protocol, data ownership, authentication, timeouts, retries and idempotency. Assign an owner and agree on SLOs, failure handling and reconciliation. Unresolved contract details become adoption conditions.

Managed databases, storage, identity services and third-party APIs can be ordinary dependencies of a serverless design. Their presence alone does not require a hybrid recommendation. Long jobs and accelerator workloads may also fit other managed compute products. Check support before including serverless containers or another product in a MODRISS generation path.

The [PIM external-systems reference](../dsml/pim/pim-external.md) describes the modeling constructs. The [hybrid integration sample](https://github.com/mehdieidi/modriss/tree/main/methodology/modeling-framework/samples/architecture-patterns/arch-hybrid) illustrates the boundary and its implementation obligations. MODRISS does not generate the internals of separately delivered services. Structural model conformance alone cannot establish correct runtime behavior.

## LLM-assisted assessment

The [modeling assistant](ai-assistant.md#serverless-suitability-analysis) can assess a specification using these criteria. Paste the description into chat and ask whether serverless and MODRISS would suit the project. Include mandatory constraints and available workload measurements. Follow-up answers can resolve missing information in the same conversation.

The response should explain its recommendation, cite relevant evidence and distinguish assumptions from requirements. With incomplete information, it can offer provisional advice and ask questions that affect the decision. The outcome labels above are guidance for the assessment record; the conversation has no required report format. Assessment leaves the model unchanged. Review the advice before requesting model creation in a separate turn.

## Research and technical references

The review of 2026-10-25 covered serverless application characteristics, FaaS development in industry, performance reviews, and provider architecture and hosting guidance. It included peer-reviewed empirical studies and surveys, together with first-party technical documentation. Unattributed blogs and marketing claims were excluded. Where publisher access was restricted, the review used the author manuscript or the IBM author abstract linked below.

This was a selective review without a formal screening or risk-of-bias protocol. Historical measurements do not establish current platform thresholds. The MODRISS sources define project scope and responsibilities; they are separate from the independent research evidence.

### Academic literature

- <a id="a1"></a> A1: Eismann et al. (2021), _Serverless Applications: Why, When, and How?_, IEEE Software 38, 32-39. [DOI](https://doi.org/10.1109/MS.2020.3023302); [author manuscript](https://arxiv.org/html/2009.08173). Describes 89 applications and their adoption characteristics. The observations do not establish a causal test of suitability.
- <a id="a2"></a> A2: Scheuner & Leitner (2020), _Function-as-a-Service performance evaluation: A multivocal literature review_, Journal of Systems and Software 170, 110708. [DOI](https://doi.org/10.1016/j.jss.2020.110708); [author manuscript](https://arxiv.org/html/2004.03276). Reviews 112 studies. Performance findings depend on the experimental setup and whether results can be reproduced.
- <a id="a3"></a> A3: Shafiei, Khonsari & Mousavi (2022), _Serverless Computing: A Survey of Opportunities, Challenges, and Applications_, ACM Computing Surveys 54(11s), 239. [DOI](https://doi.org/10.1145/3510611); [author manuscript](https://arxiv.org/html/1911.01296). Reviews application domains and technical challenges. Platform restrictions reported in the paper need checking against current services.
- <a id="a4"></a> A4: Leitner, Wittern, Spillner & Hummer (2019), _A mixed-method empirical study of Function-as-a-Service software development in industrial practice_, Journal of Systems and Software 149, 340-359. [DOI](https://doi.org/10.1016/j.jss.2018.12.013); [IBM author abstract](https://research.ibm.com/publications/a-mixed-method-empirical-study-of-function-as-a-service-software-development-in-industrial-practice). Discusses service composition and development tooling. Only the author abstract was reviewed.

### Provider documentation

- <a id="i1"></a> I1: AWS, [Fargate or Lambda decision guide](https://docs.aws.amazon.com/decision-guides/latest/decision-guides/fargate-or-lambda.html). Compute choices and hybrid designs for these services.
- <a id="i2"></a> I2: AWS, [Designing Lambda applications](https://docs.aws.amazon.com/lambda/latest/dg/concepts-application-design.html). Guidance on state, coupling, orchestration and idempotency.
- <a id="i3"></a> I3: AWS, [Lambda quotas](https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html). Check quotas for the actual account, region, invocation mode and plan.
- <a id="i4"></a> I4: Microsoft, [Azure Functions hosting options](https://learn.microsoft.com/en-us/azure/azure-functions/functions-scale). Scaling, networking, execution and cold starts vary by plan.
- <a id="i5"></a> I5: AWS, [Lambda best practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html). Retry-safe handlers and operational safeguards.

### MODRISS sources

- <a id="m1"></a> M1: The [project landing page source](https://github.com/mehdieidi/modriss/blob/main/apps/landing/src/main.tsx), [Phase 0 task definitions](../process-reference/development-delivery/tasks.md#assess-feasibility-and-serverless-suitability) and [MF-02 fragment](https://github.com/mehdieidi/modriss/blob/main/methodology/process/method/library/method-fragments.json) define methodology scope and decision responsibilities.
- <a id="m2"></a> M2: The [hybrid integration sample](https://github.com/mehdieidi/modriss/tree/main/methodology/modeling-framework/samples/architecture-patterns/arch-hybrid) describes adapters, external systems and implementation obligations. Check exact support against current metamodels, transformations and generation profiles.
