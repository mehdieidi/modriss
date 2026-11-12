<!-- Process documentation is maintained by hand. -->

# Phase 0: Inception, tailoring and organization

Phase 0 turns a problem, opportunity or required change into an agreed basis for development. It starts with a named sponsor and enough context to examine what the product should achieve.

The inputs include stakeholder expectations, existing knowledge of the organization and known constraints. These may initially come from conversations and existing systems rather than formal documents.

## Define the product

The product owner, domain expert and service owner describe the problem, expected outcomes, stakeholders and product boundary. They record success measures, exclusions, constraints and assumptions about the first release in a product and system charter.

The requirements engineer helps select an initial capability small enough to review. This gives feasibility work a concrete scope.

## Assess feasibility, serverless suitability and cost

The solution architect compares serverless, hybrid and non-serverless options. The assessment considers workload, latency, state, external connections, regulation, team skills and the ability to leave a provider. It also checks whether the proposed design can be expressed and generated with the selected MODRISS modeling package. A technically suitable architecture may still need modeling or generator support.

For a hybrid design, identify the separately implemented components, interface contracts, data owners and testing responsibilities. Record the alternatives, reasons for the recommendation, assumptions and remaining risks. An important uncertainty should lead to a bounded experiment with an owner and deadline. The [suitability guide](../guides/serverless-suitability.md) provides a practical checklist.

The cost analyst estimates representative usage scenarios, including compute, data, transfer, monitoring and dependent services. The estimate records its assumptions, range and budget limits.

At G0, the sponsor reviews value, feasibility, cost and risk. The decision can be to proceed, investigate further, change direction or stop.

## Tailor the method

The method engineer adapts the work to product criticality, regulation, uncertainty, architectural novelty, team size and distribution, and delivery constraints.

The method profile records:

- Its version and the project conditions it addresses.
- Selected activities, any changes or combinations, and the reasons for them.
- Responsibilities, review authority and required acceptance evidence.
- Conditions that should trigger another review of the method.

Combining or omitting an activity requires an alternative control for the responsibility it covered. A small team may combine roles. A sensitive product may need deeper tests and more independent review. Clear ownership, identifiable versions, change control, operational acceptance and approved closure remain necessary in either case.

## Organize the team

The delivery lead assigns ownership of model and code areas, decision authority and integration responsibilities. Teams may be organized around capabilities, services, platform work or lifecycle responsibilities.

Teams that depend on one another agree on data and interface contracts and compatible versions. A shared view of dependencies, integration reviews and a way to raise blockers keep parallel work coordinated. Team assignments can change as the product develops.

## Establish quality, security and operational expectations

The quality and security engineers define the checks, controls and risk acceptance limits. The service owner establishes environments, service objectives, monitoring, support, recovery and the release approach.

The delivery lead and service owner also agree how planned development and unexpected service work will share capacity. The process reviewer records what must be known before work starts and what evidence is needed to accept its result.

## Review readiness at G1

The product owner and delivery lead, with the method engineer, check that the method, responsibilities, controls and first increment are ready. The main outputs are the charter, feasibility assessment, cost model, risk register, method profile, team assignments and initial increment and release plan.

After G1 acceptance, [active product development](../guides/end-to-end-modeling-methodology.md) begins. The method profile remains open to revision when project conditions change.
