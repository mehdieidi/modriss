<!-- Process documentation is maintained by hand. -->

# Serverless suitability

During Phase 0, the solution architect compares serverless, hybrid and non-serverless options for the proposed product. The assessment uses the product charter, requirements, expected workload and known constraints. Its findings support the sponsor's G0 decision.

Assess architectural suitability separately from MODRISS support. A design can be a reasonable serverless choice while still needing a metamodel, transformation or generator that the selected provider package does not supply.

## Examine requirements and alternatives

Use available specifications, measurements and stakeholder knowledge. Keep confirmed requirements, assumptions and missing information separate. Check current provider capabilities, quotas and prices for the proposed deployment.

The following questions help organize the assessment. The identifiers also appear in the modeling assistant's suitability guidance.

| ID  | Area                      | Questions to resolve                                                                                    |
| --- | ------------------------- | ------------------------------------------------------------------------------------------------------- |
| C01 | Workload                  | What triggers work? How much traffic, concurrency, idle time and growth should the design handle?       |
| C02 | Execution                 | Can the selected services meet task duration, memory, runtime and other resource needs?                 |
| C03 | Latency                   | Can measured response times meet the target during startup, bursts and failures?                        |
| C04 | State and data            | Where does state persist? What are the data volume, locality, consistency and connection requirements?  |
| C05 | Reliability               | How will the design handle duplicate delivery, ordering, retries and partial failure?                   |
| C06 | Integration and workflows | Are dependencies, communication, timeouts, retries and compensation practical for this design?          |
| C07 | Cost                      | How do compute, data, transfer, monitoring, dependent services and staff effort compare across options? |
| C08 | Security and governance   | Can the deployment satisfy permissions, privacy, residency, audit and retention obligations?            |
| C09 | External connections      | Can required systems be reached with suitable contracts, authentication, reliability and ownership?     |
| C10 | Delivery and operations   | Does the team have the skills and tools to test, deploy, monitor, recover and support the service?      |
| C11 | Provider dependence       | Are the proposed dependencies acceptable? What would migration or exit require?                         |
| C12 | MODRISS support           | Which parts can the selected modeling and generation package express? What remains external or manual?  |

A mandatory requirement that an option cannot meet needs an acceptable alternative. Benefits elsewhere do not resolve that conflict. For an important uncertainty, agree on an experiment or question with an owner and decision deadline.

## Record the recommendation

For each area, record whether the evidence supports the option, support depends on a condition, a requirement conflicts with it, or information is missing. The assessment record can use COMPATIBLE, CONDITIONAL, INCOMPATIBLE and UNKNOWN for these findings.

The recommendation can be SUITABLE, HYBRID, UNSUITABLE or INSUFFICIENT_INFORMATION. Explain the alternatives, reasons, evidence, assumptions, unresolved questions and remaining risks. Confidence should reflect what is known.

For a hybrid option, identify the separately delivered components, interface contracts, data owners and testing responsibilities. Record what MODRISS will generate and what the team must deliver separately.

The cost analyst supplies usage scenarios, an estimate range and budget limits. The sponsor then reviews value, feasibility, cost and risk at G0 and decides whether to continue, investigate further, change direction or stop.

## Use assistant advice as input

The modeling assistant can help assess a specification and ask about missing information. Include mandatory constraints and available workload evidence, then review its recommendation with the responsible people. An assessment does not change the model or replace the project decision.
