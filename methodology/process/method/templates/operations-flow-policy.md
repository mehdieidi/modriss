# Kanban Service-Delivery Policy and Board (WP-31)

## Context

- Product/service:
- Accepted operating baseline:
- Service Owner:
- Delivery Lead:
- Support coverage and hand-off model:
- Effective method-profile version:
- Review date and owner:

## Definition of Workflow and board design

| Board state | Entry rule                                                                                    | Exit rule                                                              | WIP control                     | Blocked-work rule                           |
| ----------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- | ------------------------------- | ------------------------------------------- |
| Requested   | source, impact, affected service and evidence recorded                                        | owner, expected outcome, class of service and likely disposition known | outside started-to-finished WIP | surface unowned or aging requests           |
| Ready       | Ready policy satisfied and item selected at replenishment                                     | downstream capacity exists and item is pulled                          | selection limit:                | reorder only under explicit policy          |
| In Progress | accountable team pulls the item; this is the **started point**                                | resolution or change evidence is ready for verification                | limit:                          | swarm, unblock, split or escalate           |
| Verify      | required restoration, test, trace and release evidence available                              | Done policy met or explicit rework decision made                       | limit:                          | preserve failed evidence and return visibly |
| Done        | resolution, evidence, reconciliation and disposition accepted; this is the **finished point** | not applicable                                                         | not applicable                  | reopened work creates a linked item         |

The board must show WIP controls on every state or group of states between the
started and finished points. The SLE is stated as an elapsed-time/probability
forecast from In Progress to Done and is recalculated from historical cycle
time when enough data exists.

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

Future incidents are not invented or scheduled as tasks. Capacity for service
work is established explicitly and reviewed using arrival and flow evidence.
Unused service capacity may support planned work, but an expedite item displaces
active work only under the visible expedite policy.

## Disposition and lifecycle bridge

For each WP-30 Service Work Item select one disposition:

- operations-only/runbook resolution with retained evidence;
- bounded maintenance change through the earliest affected CIM, PIM, PSM,
  generator, code, or release activity; or
- explicit commitment to a planned release backlog.

Any temporary emergency modification identifies the permanent corrective or
reconciliation item and remains open until removed or incorporated into the
authoritative source.

## Measures and improvement

Review WIP, throughput, cycle time, work-item age, SLE attainment, arrival rate,
blocked time, expedite frequency, capacity allocation, planned-work disruption,
escaped defects, and reconciliation age. Use these measures for system
improvement, never individual ranking.

## Cadences

| Cadence                 | Purpose                                                                    | Default participants                                       | Decision/evidence                                   |
| ----------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------------- | --------------------------------------------------- |
| Replenishment           | select eligible Requested items into Ready within capacity                 | Service Owner, Delivery Lead, delivery representatives     | ordered Ready set and explicit deferrals            |
| Daily flow review       | inspect aging, blockers, WIP and expedite work; coordinate flow            | people delivering service work                             | unblock, swarm, split, or escalate decisions        |
| Service-delivery review | compare SLE and flow outcomes with demand and customer/service outcomes    | Service Owner, Delivery Lead, Quality and Operations       | Definition-of-Workflow improvement experiment       |
| Operations/risk review  | inspect cross-service capacity, recurring risk, cost and provider exposure | product, service, platform, security and governance owners | capacity, policy, planned-release, or risk decision |

These are feedback cadences, not additional lifecycle phases. Their frequency is
tailored in the Method Profile.

## Method-fragment basis

The board and policies operationalize the Kanban Guide's Definition of Workflow,
WIP control, SLE, active management, and four flow metrics. Disciplined Agile
contributes contextual selection of flow rather than iterations for this service
work, visualization across the value stream, and guided improvement of the way
of working. This fragment is also consistent with empirical software-engineering
evidence that Kanban has been applied to both development and maintenance while
requiring contextual tailoring:

- [The Kanban Guide (2025)](https://kanbanguides.org/the-kanban-guide/)
- [PMI Disciplined Agile: Starting with Iterations or Flow](https://www.pmi.org/disciplined-agile/starting-with-iterations-or-flow)
- [PMI Disciplined Agile: Designing the Kanban Board](https://www.pmi.org/disciplined-agile/designing-the-kanban-board)
- [PMI Disciplined Agile Lean (Kanban-Based) Lifecycle](https://www.pmi.org/-/media/pmi/microsites/disciplined-agile/posters/life-cycle-posters-11x17_lean.pdf)
- [Ahmad et al. (2018), _Kanban in software engineering: a systematic mapping study_](https://doi.org/10.1016/j.jss.2017.11.045)
- [ISO/IEC/IEEE 14764:2022, software maintenance](https://www.iso.org/standard/80710.html)
