<!-- Process documentation is maintained by hand. -->

# Operations and Maintenance

Operations and Maintenance keeps the accepted service running and manages incidents, changes and improvements. It starts with the first operational acceptance at G7. Development of later versions can continue at the same time.

The [process overview](../guides/full-lifecycle-method.md#two-processes-across-the-product-lifecycle) shows the maintenance sequence: Operate & Observe, Register & Classify Work, and Manage Flow (Kanban). Classified changes feed back into active product development.

The inputs are the running version, handover package, service objectives, monitoring data, incident and release records, and change requests. The process produces service and cost evidence, work items, incident records, change impact assessments and released improvements.

## Observe the service and prepare work

The service owner works with security, quality and cost staff to review service health, failures, usage, cost and provider changes. A gap against a product or service objective should lead to a decision or an identified work item.

Record each item's source, affected service, impact, owner, evidence and expected outcome. For maintenance work, identify whether its purpose is corrective, preventive, adaptive, additive or perfective. Record urgency separately, for example urgent, fixed-date, standard or risk reduction. Also record whether an emergency recovery action is still temporary.

Before an item is ready to start, clarify its expected result, dependencies and likely delivery route.

## Manage work with a Kanban board

Work moves through Requested, Ready, In progress, Verification and Done. The team pulls an item when there is capacity to do it. The board policy defines start and finish points, work-in-progress limits, service classes, expected completion time and emergency handling.

A blocked item stays in its current state and counts toward the work-in-progress limit. Ready is a queue before work starts.

The delivery lead and service owner review ready work, aging items, blockers and emergencies. They make capacity changes visible so that operational demand and planned development can be considered together.

## Respond to incidents

The service owner assesses the impact, stabilizes the service and directs recovery. Record the incident timeline, decisions, temporary actions and outcome.

Restoring service does not remove the need for a permanent correction. Keep underlying causes available for quality, security or architecture review. An emergency change remains open until it is removed or reconciled with the source that should maintain it.

## Choose the route for each item

An item can be resolved by an operational action, follow the necessary software change and release activities, or join a planned release backlog.

The delivery lead and source owner use trace links to find where a change should begin:

| Change                                         | Starting point                               |
| ---------------------------------------------- | -------------------------------------------- |
| Business concept, rule or requirement          | CIM domain model and requirements.           |
| Service boundary, contract or failure behavior | PIM architecture.                            |
| Provider resource or modeled configuration     | PSM cloud design.                            |
| Generator defect                               | Generator or templates.                      |
| Application logic defect                       | Application code and tests.                  |
| Build or deployment problem                    | Release pipeline and its configuration.      |
| Routine operational action                     | The relevant runbook and operational record. |

After changing the source, regenerate affected outputs where needed, test them and follow release and handover activities. A permanent change to a modeled setting, such as an alert threshold, follows the corresponding model and release route.

## Verify the result and improve the service

Close an item only after checking its outcome and resolving temporary changes. If the outcome is unacceptable, reassess the action. A place in a future release backlog alone is insufficient to close it.

Review recurring incidents, blocked work, waiting time and cost deviations. Improvements may affect the product, runbooks, capacity, tests or method profile. Give each action an owner and an expected result.

The process continues while a release is live. During [retirement](retirement.md), operations maintains the necessary service coverage and provides shutdown evidence for G8. The process ends only with safe shutdown, no live release and accepted joint closure.
