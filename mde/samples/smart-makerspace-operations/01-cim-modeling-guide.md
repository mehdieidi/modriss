# CIM Modeling Guide: Smart Makerspace Operations

## Project scenario

Northstar Community Makerspace shares hazardous fabrication equipment among members. Today,
training evidence, reservations, machine access, controller telemetry, and maintenance records are
fragmented. The project creates one demonstrable platform where a member can become certified,
find and reserve equipment, start a machine only when every safety rule passes, and see session
history. A controller simulator can publish session and incident events. A critical incident must
deny further access immediately, create a technician work item, and keep the machine locked until a
reviewed repair checklist is complete.

This makes a strong exhibition project because a workshop can show a normal member journey and a
safety failure journey in real time without connecting actual hazardous equipment.

## Modeler simulation

Use the CIM palette and the stable IDs below. A connector named in capitals sets the corresponding
metamodel reference. Capability dependencies and domain/process/trace connections are semantic edge
objects; edit the edge attributes after drawing them.

### 1. Establish intent

1. Create `CIMModel` `makerspace-root`; set domain, scope, organization, date, language, and
   `APPROVED` lifecycle as recorded in [`smart-makerspace.cim.xmi`](./smart-makerspace.cim.xmi).
2. Create goals `goal-safe`, `goal-utilization`, and `goal-recovery`. Fill success criterion,
   business value, failure consequence, and priority.
3. Create KPIs `kpi-unsafe`, `kpi-utilization`, and `kpi-lockout`; set metric definition, operator,
   target, unit, frequency, source, and threshold. Connect each KPI to its goal with `MEASURES`.
4. Create stakeholders `stakeholder-safety`, `stakeholder-ops`, and `stakeholder-technician` and
   connect each to the goal it owns.

The outcomes are zero unauthorized starts, at least 75 percent bookable utilization, and critical
lockout confirmation within 60 seconds.

### 2. Model people and system boundaries

1. Create human actors `actor-member`, `actor-instructor`, and `actor-technician` with type, trust,
   organizational boundary, authentication, authorization, and interaction expectations.
2. Create roles `role-member`, `role-instructor`, and `role-technician`. Connect them with
   `PLAYS_ROLES`; mark assessor and maintainer privileged and state their business permissions.
3. Create `ExternalSystem` nodes `ext-controller` and `ext-notification`. Fill owner, purpose, trust
   rationale, SLA, data/event flags, and exchanged information.
4. Connect controller-produced session/incident events and controller-consumed authorization and
   lockout events. Connect the notification system to reservation and lockout events.

### 3. Draw capability and language boundaries

1. Create capabilities `cap-membership`, `cap-reservation`, and `cap-maintenance`; set owner,
   responsibility, maturity, criticality, supported goals, behavior, managed entities, and process
   ownership.
2. Draw `CapabilityDependency` from reservation to qualification (`capdep-access-cert`) and from
   reservation to maintenance (`capdep-access-maint`); both are critical paths.
3. Create bounded contexts `bc-qualification`, `bc-operations`, and `bc-maintenance`. Assign their
   capability, entities, commands, queries, events, language, and ownership boundary.
4. Add glossary terms `term-cert`, `term-session`, and `term-lockout` with definition, forbidden
   synonyms, example, and context.

### 4. Define and govern information

Create classifications `class-public`, `class-personal`, and `class-safety`. Set identifiability,
encryption, masking, minimization, consent, audit, deletion, regulatory category, and rationale.

Create and classify these information items:

| Concern        | Items                                                     |
| -------------- | --------------------------------------------------------- |
| Member         | `info-member-id`, `info-contact`                          |
| Equipment      | `info-equipment-id`, `info-equipment-class`               |
| Qualification  | `info-cert-id`, `info-cert-expiry`                        |
| Reservation    | `info-reservation-id`, `info-start-time`, `info-end-time` |
| Access/session | `info-session-id`, `info-access-decision`                 |
| Recovery       | `info-incident-id`, `info-signal`, `info-resolution`      |

Fill business name, primitive type, required flag, examples/allowed values, validation, source of
truth, sharing, audit/search/report/retention flags. Connect every personal or safety item to
`priv-member`; connect safety evidence to `comp-safety-evidence`.

### 5. Build the domain

1. Create entities Member, EquipmentCertification, Equipment, Reservation, MachineSession, and
   SafetyIncident using IDs `entity-member` through `entity-incident`.
2. Set one primary identity, identity attributes, remaining attributes, owning capability,
   lifecycle description, glossary definition, and audit relevance.
3. Add active/expired certification states and available/locked/retired equipment states. Exactly
   one state is initial and at least one is terminal.
4. Draw domain relationships: member composes certifications, equipment associates reservations,
   and equipment composes incidents. Fill roles, ownership/navigation, and both multiplicities.
5. Create aggregates `agg-qualification`, `agg-operations`, and `agg-incident`. Set root, members,
   context, handled commands, emitted events, consistency expectation/rationale, conflict policy,
   and idempotency key.

### 6. Specify behavior

1. Create conditions `cond-certified`, `cond-slot`, `cond-access`, and `cond-repaired`, including
   FEEL/natural-language expressions and referenced information/concepts.
2. Create errors `err-not-certified` and `err-unavailable` with code, meaning, public message,
   recoverability, retry, and audit semantics.
3. Create commands `cmd-certify`, `cmd-reserve`, `cmd-start`, `cmd-end`, and
   `cmd-resolve-incident`. For each, fill intent, type, actor, capability, aggregate, input,
   preconditions, authorization, idempotency, expected events/errors, and nested success/failure
   outcomes.
4. Create queries `qry-availability`, `qry-my-reservations`, and `qry-maintenance`. Set type,
   freshness, confidentiality, authorization, pagination/filter/sort expectations, input, required
   output, read entities, actor, and capability.
5. Create events `evt-certified`, `evt-reservation-confirmed`, `evt-access-authorized`,
   `evt-session-started`, `evt-session-ended`, `evt-incident`, `evt-machine-locked`, and
   `evt-equipment-restored`. Fill semantic/past-tense name, meaning, time semantics, version,
   correlation/causation/ordering keys, flags, payload, affected entities, and external consumers or
   producers.

### 7. Add policies and workflows

1. Create Certified Access, Critical Incident Lockout, and Reservation No Show policies
   (`pol-access`, `pol-lockout`, `pol-no-show`). Set natural rule, FEEL expression, strength,
   severity, audit, trigger, guarded command, and emitted event.
2. Create `proc-session`. Add Start, Query, Command, Wait, Command, Event, and End steps in order;
   connect all adjacent steps with contained `ProcessTransition` edges. Add the no-show exception
   and five-second access-decision temporal constraint.
3. Create `proc-recovery`. Add Start, Policy, ExternalInteraction, HumanTask, Command, and End steps;
   connect them in order and guard restoration with `cond-repaired`. Add controller-lockout failure
   and the 60-second lockout temporal constraint.

### 8. Add requirements and readiness

Create functional requirements `req-reservation` and `req-lockout`, latency NFR
`nfr-access-latency`, security constraint `sec-machine-access`, privacy constraint `priv-member`, and
compliance constraint `comp-safety-evidence`. Give every requirement a fit criterion or Given/When/
Then criterion, goal, constrained elements, metric/target, and complete quality scenario. Keep
specialized and generic constraint scopes synchronized.

Add controller-connectivity risk `risk-controller`, clock assumption `asm-clock`, offline-isolation
hotspot `hot-offline`, and transformation profile `tp-makerspace`. Create three intent trace links
under `trace-makerspace`. Finish `ready-makerspace` with the classification check and the owned
offline-isolation decision. CIM is transformation ready, not production ready.

### 9. Validate and transform

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar cim `
  --repo-root . --model mde/samples/smart-makerspace-operations/smart-makerspace.cim.xmi `
  --fail-on-mandatory-violations --fail-on-optional-violations
```

The checked-in CIM reports **0 mandatory and 0 optional violations**. Generate the draft PIM with:

```powershell
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar cim-to-pim `
  --repo-root . --source-model mde/samples/smart-makerspace-operations/smart-makerspace.cim.xmi `
  --target-model mde/samples/smart-makerspace-operations/smart-makerspace.draft.pim.xmi --overwrite
```
