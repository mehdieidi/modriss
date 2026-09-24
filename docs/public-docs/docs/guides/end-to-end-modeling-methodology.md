# Capability-Increment Process

This guide describes the model-driven delivery part of the MODRISS development process. It follows one capability increment from scope definition through readiness review. The [full-lifecycle development process](full-lifecycle-method.md) also covers initiation, tailoring, release, operations, change management, and retirement.

The maintained process definition is [`mde/process/definitions/end-to-end.json`](https://github.com/mehdieidi/modriss/blob/main/mde/process/definitions/end-to-end.json). It defines an eight-step engine for a capability slice. The same engine may run several times before the team assembles a release.

## The increment engine

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-model-driven-engine.svg" aria-label="Open the full-size capability-increment process diagram">
    <img src="../../assets/diagrams/modriss-model-driven-engine.svg" alt="Eight steps from increment framing through CIM, PIM, AWS PSM, generation, readiness review, and an accept, defer, or rework decision." />
  </a>
  <figcaption>The repeatable engine for a single capability increment.</figcaption>
</figure>

| Step                        | Work                                                                                                  | Main result                              |
| --------------------------- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| 1. Increment framing        | Define the capability slice, its acceptance signals, and the evidence needed for review.              | Agreed scope and increment record        |
| 2. CIM engine               | Model business intent, domain structure, behavior, processes, policies, and readiness.                | Reviewed CIM revision                    |
| 3. CIM to PIM               | Transform the accepted CIM and examine generated elements, traces, assumptions, and manual decisions. | PIM draft with trace links and decisions |
| 4. PIM engine               | Refine service boundaries, contracts, data, compute, integrations, security, and platform readiness.  | Reviewed PIM revision                    |
| 5. PIM to AWS PSM           | Transform the PIM and review AWS mappings, relationships, and unresolved platform assumptions.        | AWS PSM draft                            |
| 6. PSM engine               | Refine AWS resources, policies, deployment structure, and integration views.                          | Reviewed AWS PSM revision                |
| 7. M2T generation           | Generate a reproducible AWS project from the reviewed PSM.                                            | Generated artifact baseline              |
| 8. Readiness and acceptance | Review the models, generated files, verification evidence, findings, and open decisions.              | Accept, defer, or rework decision        |

The level-specific guides describe the [CIM process](cim-modeling-methodology.md), [PIM process](pim-modeling-methodology.md), and [AWS PSM process](psm-modeling-methodology.md). The [transformation reference](../transformations/index.md) and [generation reference](../generation/index.md) describe the corresponding modeling-framework components.

## Review and feedback

Each transformation starts from a saved source revision. The generated target needs review because it can contain assumptions and decisions that depend on the system. Teams inspect its traces, transformation report, unresolved manual actions, and relationships before refining it.

When a downstream finding exposes a missing concept, the team updates the earliest model that owns it and repeats the affected transformations. PIM refinement can return a discovery to CIM. AWS constraints can lead to a PIM change. Artifact validation can require a PSM or generator correction. The team records the cause, affected revisions, and resulting evidence.

The engine loops after an accepted increment when more capability slices are needed. The team can also leave the engine and assemble a release. A release remains in operation while the product is active. Roadmap demand or operational evidence can start another release cycle, which returns to model-driven delivery. Retirement requires a separate authorized decision.

## Roles and coordination

Roles identify responsibilities. They do not require a separate person for every responsibility. The Product Owner sets outcomes and scope. Business Modelers, Requirements Engineers, and Domain Experts establish and review business intent. The Solution Architect leads PIM decisions, while the Cloud Platform Engineer leads AWS PSM, generation, and platform automation. Quality and Security Engineers shape verification and risk evidence. The Release Engineer and Service Owner handle promotion and operations. The Delivery Lead coordinates dependencies and flow; the Process Reviewer reviews evidence and gates; the Method Engineer tailors the process.

For each increment, record the responsible teams, model and artifact revisions, transformation profiles and reports, trace coverage, assumptions, manual decisions, findings, dependencies, and acceptance decision. Teams may work concurrently when ownership and interfaces are clear. A downstream step must not treat an unresolved assumption as approved simply because a transformation produced a target model.

## Validation in the process

EVL semantic gates belong to explicit user/model validation workflows. The relevant level-specific guide describes when to perform them. Structural Ecore/EMF conformance is a separate check. Chatbot assistant apply, repair, and commit paths use only `ModelService.validateStructural(...)` for generated model output; they do not call EVL or stored semantic-validation endpoints.

## Process milestones

The full process tracks decisions across the lifecycle:

| Milestone                                    | Decision recorded                                                           |
| -------------------------------------------- | --------------------------------------------------------------------------- |
| Process tailoring and team topology approved | The method profile, ownership, and coordination approach are accepted.      |
| Vertical increment accepted                  | The child-level gates and artifact evidence support the increment decision. |
| Release promoted and handed over             | Promotion and operational handover are complete.                            |
| Operational learning reviewed                | Service outcomes and process evidence have been inspected.                  |
| Lifecycle retired and closed                 | Retirement evidence and required knowledge have been retained.              |

These milestones are part of a process run. Task completion by itself is not acceptance evidence. See the [full-lifecycle development process](full-lifecycle-method.md) for run state, tailoring, release control, and operational change.
