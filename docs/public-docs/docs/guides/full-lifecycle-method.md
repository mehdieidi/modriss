<!-- Process documentation is maintained by hand. -->

# Development process

MODRISS is a research methodology for model-driven serverless software development. It combines a development process with a modeling framework. The framework provides the modeling languages, transformations and code generation tools. The process explains when to use them, who does the work and how the team reviews the result.

The work starts with a product idea and continues through development, release, operation and retirement. Teams adapt the depth of each activity to their project's risks, constraints and available skills.

## Two processes across the product lifecycle

Development and Delivery has three phases. Each phase occurs once in the product's life. Within the active product phase, the team repeats development and release activities as the product grows.

Operations and Maintenance starts when the first release receives operational acceptance at G7. It runs alongside later development and continues through retirement until the service has shut down safely and both processes have met the G8 closure conditions.

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/process/lifecycle.svg" aria-label="Open the product lifecycle diagram">
    <img src="../../assets/diagrams/process/lifecycle.svg" alt="Three sequential development phases, with repeated increments and releases inside Phase 1. Operations starts at G7 and contributes to shared closure at G8." />
  </a>
  <figcaption>Development and Delivery and the concurrent Operations and Maintenance process. Dashed arrows show feedback and repeated work.</figcaption>
</figure>

| Phase                                       | Purpose                                                                             | Main result                                                       |
| ------------------------------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 0. Inception, tailoring and organization    | Define the product, compare implementation options and prepare the method and team. | An agreed starting point for the first increment, accepted at G1. |
| 1. Active product development and evolution | Build, review and release useful increments of the product.                         | Accepted increments and releases with operational support.        |
| 2. Retirement, migration and closure        | End or replace the product and settle its remaining obligations.                    | Safe shutdown and joint closure at G8.                            |

## From a business need to working software

During Phase 1, the team selects a small part of a capability with a result that people can review. For example, an increment might let a user submit and track a request.

The team describes the increment at three modeling levels:

- The Computation-Independent Model (CIM) records business goals, concepts, rules and behavior.
- The Platform-Independent Model (PIM) describes the serverless architecture without choosing a cloud provider.
- The Platform-Specific Model (PSM) defines resources and configuration for the chosen provider.

Transformations create drafts of the next model. The person responsible for that level reviews the mappings, resolves open decisions and completes the design. Code generation then creates a project that engineers complete and test.

An accepted increment can join other accepted increments in a release. Increment acceptance at G5, release authorization at G6 and operational acceptance at G7 are separate decisions. Each reviews a different result and its supporting evidence.

## Feedback from the running service

Operations staff monitor service health, incidents, usage and cost. Some work ends after an operational action, such as following a recovery procedure. Other work requires a model or code change, or joins a planned release.

Changes return to the source responsible for the decision. A business rule belongs in CIM, an architecture decision in PIM and a cloud resource setting in PSM. The team updates and checks the affected outputs before releasing the change. Temporary emergency changes remain open until they are removed or reconciled with the maintained source.

## Responsibilities and reviews

Role names describe responsibilities. A small team can assign several roles to one person, while a larger team can share a role. The project method profile records assignments, review authority and the evidence needed to accept work.

A decision gate reviews a defined scope and specific versions of its inputs and outputs. It can accept the work, request rework, defer it or stop it. Completing a checklist does not replace that decision.

Risk management, quality, security, cost control, configuration management and team coordination continue throughout the lifecycle. Records can live in models, repositories or tracking tools. Each record does not need its own document.

## Follow the process

Start with [project inception](../process-reference/development-delivery.md) and the [serverless suitability assessment](serverless-suitability.md). During active development, follow the [increment workflow](end-to-end-modeling-methodology.md) and [implementation and release activities](../process-reference/artifact/activities.md).

The [operations](../process-reference/operations-maintenance.md) and [retirement](../process-reference/retirement.md) pages cover the running service and the end of its life. Shared guidance explains [roles](../process-reference/roles.md), [decision gates](../process-reference/gates.md) and [ongoing activities](../process-reference/ongoing-activities.md).
