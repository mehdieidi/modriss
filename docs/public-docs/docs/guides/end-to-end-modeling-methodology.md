<!-- Process documentation is maintained by hand. -->

# Building a product increment

An increment is a limited part of a capability that the team can follow from business need to tested implementation. This work repeats inside Phase 1, the active product phase. Existing models, code and operational findings become inputs to later increments.

## Plan the increment

The product owner works with requirements, architecture, delivery and operations staff to choose the scope. Record the intended outcome, acceptance criteria, responsible team, dependencies, risks and expected evidence.

The scope should produce a result that people can assess, such as submitting and tracking a request. Independent work may proceed in parallel when ownership, contracts and the versions used by each team are clear. Unresolved assumptions must remain visible to teams that depend on them.

## Model, transform and complete the software

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/process/increment.svg" aria-label="Open the product increment diagram">
    <img src="../../assets/diagrams/process/increment.svg" alt="Increment planning leads through CIM, PIM and PSM reviews at G2, G3 and G4, then generation, implementation and testing, and increment acceptance at G5." />
  </a>
  <figcaption>One product increment. Model transformations produce drafts for review. Dashed arrows show rework and the next increment.</figcaption>
</figure>

| Activity                                                                                | Main responsibility                                                | Result                                           |
| --------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------ |
| [Understand and model the domain](cim-modeling-methodology.md)                          | Business modeler, with the domain expert and requirements engineer | A CIM and requirements accepted at G2.           |
| [Design the serverless architecture](pim-modeling-methodology.md)                       | Solution architect                                                 | A completed PIM accepted at G3.                  |
| [Define the cloud resources](psm-modeling-methodology.md)                               | Cloud platform engineer                                            | A PSM accepted for generation at G4.             |
| [Generate, complete and test the software](../process-reference/artifact/activities.md) | Cloud platform engineer, application engineer and quality engineer | Code and evidence for the G5 increment decision. |

At each transformation, record the source version, previous target baseline, working target, transformation rule version, report, trace links and open decisions. Review the new draft against previous work at the target level and resolve conflicts before accepting it.

Changes start at the level responsible for their meaning. A missing domain concept returns to CIM. A cloud limitation that affects the architecture returns to PIM. A generator defect goes to the generator owner. Update the affected outputs and repeat the relevant reviews.

## Accept or rework the increment

At G5, the product owner reviews the acceptance criteria and current evidence with quality, security and service responsibilities represented. The decision is to accept, rework or defer the increment. The evidence covers build and test results, security, cost, operational readiness and remaining manual actions.

Acceptance makes the increment available for selection in a release. The [release review](../process-reference/artifact/activities.md#prepare-a-release) later considers the exact combination of increments and versions that will be deployed.

The method engineer and team review waiting time, rework, dependencies and difficulties with the method. Any improvement has an owner and a review date. If no method change is needed, record why the current approach remains appropriate.
