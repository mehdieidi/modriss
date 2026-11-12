<!-- Process documentation is maintained by hand. -->

# Ongoing activities

These activities continue across the lifecycle. Their depth depends on project conditions. Teams can keep the records in models, repositories or tracking tools without creating a separate document for every item.

## Coordinate work

The product owner and delivery lead maintain priorities, increment plans, release expectations and dependencies. Local coordination, reviews across teams and increment and release reviews help resolve blockers and contract disagreements.

Parallel work requires clear ownership and an agreed integration path. Planned development and operational demand share capacity through visible decisions.

## Manage risk, security and cost

Record each risk's cause, impact, owner, response and review date. Follow security controls from domain analysis through deployment and data retention.

Update cost assumptions with actual usage. Product and architecture decisions should use the current cost model, including deviations from the budget.

## Maintain quality and decision records

Agree on starting conditions, completion conditions, tests and acceptance evidence before the work. Keep each review connected to its scope, versions, authority and reasons. The [decision gates](gates.md) describe the main review points.

Changes to relevant dependencies or expired exceptions reopen affected decisions. Completing the activity list alone does not establish acceptance.

## Control versions and trace changes

Maintain links between requirements, models, transformations, generated files, application code, tests and releases. Use these links to identify the impact of a change and the people responsible for affected work.

Keep accepted versions and superseded records identifiable by version and origin. Reconcile generated output with existing refinements before accepting it.

## Review and adapt the method

The method engineer and process reviewer examine progress and difficulties. Useful development measures include increment lead time, rework, trace coverage, dependency age and defects found after release.

Operations also reviews work in progress, throughput, cycle time and the age of open items. An improvement decision should name a concrete problem, an owner and a date for assessing the result.

## Separate model checks from software tests

Structural conformance checks the model against its Ecore/EMF metamodel. Semantic validation checks modeled rules through an explicit user workflow. Runtime tests examine the behavior of the implemented software.

The language-model assistant can help create and change CIM and PIM models. Applying, repairing and committing its output depend only on structural conformance. Semantic validation is a separate user activity and is not called by those assistant paths. Its results can later support process reviews.

## Reuse process activities

A task definition describes the purpose and way of doing work. Each use of that task has its own responsible person, input and output versions, and review result. Dependencies specify what must start or finish before other work can proceed.

This distinction between reusable method content and its use in a process follows the central idea of SPEM, the Software and Systems Process Engineering Metamodel. MODRISS does not claim full conformance with every SPEM requirement.
