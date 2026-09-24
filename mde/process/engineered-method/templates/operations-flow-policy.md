# Operations Flow Policy and Board (WP-31)

## Context

- Product/service:
- Accepted operating baseline:
- Service Owner:
- Delivery Lead:
- Support coverage and hand-off model:
- Effective method-profile version:
- Review date and owner:

## Definition of Workflow

| State  | Entry rule                                    | Exit rule                                    | WIP limit      | Blocked-work rule                   |
| ------ | --------------------------------------------- | -------------------------------------------- | -------------- | ----------------------------------- |
| Intake | evidence/source recorded                      | triage owner assigned                        | not applicable | surface unowned items               |
| Triage | impact and affected service known             | type, service class, SLE and disposition set |                | escalate aging unclassified demand  |
| Ready  | minimum evidence and owner available          | capacity exists and item is pulled           |                | reorder only under explicit policy  |
| Active | item pulled by accountable team               | resolution is ready for verification         |                | swarm, unblock or escalate          |
| Verify | expected evidence is available                | done criteria or rework decision met         |                | preserve failed evidence            |
| Done   | resolution, evidence and disposition accepted | not applicable                               | not applicable | reopened work creates a linked item |

## Classification policy

Maintenance purpose records _why_ work exists: corrective, preventive,
adaptive, additive, or perfective. Record separately whether an emergency
requires a temporary restoration modification pending permanent correction.
Class of service records _how_ work flows:

| Class of service | Selection policy                                                    | Service-level expectation | Preemption rule                                                        |
| ---------------- | ------------------------------------------------------------------- | ------------------------- | ---------------------------------------------------------------------- |
| Expedite         | severe current impact requiring immediate action                    |                           | one active expedite by default; Incident Commander controls exceptions |
| Fixed-date       | material loss if a known deadline is missed                         |                           | does not bypass WIP without documented policy                          |
| Standard         | ordinary operational or maintenance demand                          |                           | pull in order set by explicit policy                                   |
| Risk-reduction   | reduces future reliability, security, cost or obsolescence exposure |                           | reserve/replenish according to risk appetite                           |

## Capacity, pull, and replenishment

- Planned-delivery/operational capacity policy:
- Who may change allocation and on what evidence:
- Replenishment cadence and participants:
- Daily flow review:
- Service-delivery review cadence:
- Starvation and overload signals:

Unknown future incidents are not scheduled as tasks. Available operational
capacity may be used by planned work under this policy, but new demand preempts
only through the selected service-class rule.

## Disposition and lifecycle bridge

For each WP-30 Operational Work Item select one disposition:

- operations-only/runbook resolution with retained evidence;
- bounded maintenance change through the earliest affected CIM, PIM, PSM,
  generator, code, or release activity; or
- explicit commitment to a planned release backlog.

Any temporary emergency modification identifies the permanent corrective or
reconciliation item and remains open until removed or incorporated into the
authoritative source.

## Measures and improvement

Review WIP, throughput, cycle time, work-item age, SLE attainment, arrival rate,
blocked time, expedite/preemption frequency, capacity allocation, planned-work
disruption, escaped defects, and reconciliation age. Use these measures for
system improvement, never individual ranking.
