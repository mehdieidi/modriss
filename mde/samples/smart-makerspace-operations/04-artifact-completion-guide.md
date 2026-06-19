# Artifact Completion Guide: Smart Makerspace Operations

## What generation provides

[`generated-project`](./generated-project/) contains three SAM templates, OpenAPI contracts, two ASL
workflows, 12 Go Lambda handler shells, shared validation/logging/metrics/tracing/idempotency/data and
event helpers, schemas, event fixtures, environment files, CI workflows, deployment scripts,
runbooks, traceability/security reports, and generated unit/integration/contract/event/workflow/e2e
test shells.

Generation does not invent business logic or real credentials. The generated handlers intentionally
return `NOT_IMPLEMENTED` inside protected regions. Complete work in those regions or developer-owned
packages so regeneration preserves it. Reconcile every entry in
[`generated/reports/manual-actions.md`](./generated-project/generated/reports/manual-actions.md).

## Implementation sequence

### 1. Establish domain and port packages

Create developer-owned Go packages under `internal/`:

- `domain`: Member, Certification, Equipment, Reservation, Session, Incident, state enums, typed
  identifiers, UTC time intervals, SafetySignal, MaintenanceResolution, and domain errors.
- `application`: command/query services and policies with no AWS imports.
- `ports`: repositories, event publisher, clock, idempotency store, identity context, controller,
  notification, technician-task, and transaction interfaces.
- `adapters/aws`: DynamoDB repositories/projections, relational repositories, search projection,
  EventBridge/SNS/SQS publishing, Step Functions callback, Secrets Manager, and Cognito claims.
- `adapters/simulator`: in-memory stores, fake clock, controller simulator, and notification capture
  for workshops and tests.

Keep AWS request/response conversion in handler/adapters; domain services consume typed values.

### 2. Implement the five commands

1. **RecordEquipmentCertification:** authenticate assessor, verify assigned equipment class, validate
   expiry, use assessment ID idempotency, transactionally append certification, publish
   MemberCertified, and audit actor/reason/time.
2. **ReserveEquipment:** authenticate member, load current certification and machine state, reject
   overlap/lockout, transactionally reserve with conditional conflict protection, publish
   ReservationConfirmed, and update projections.
3. **AuthorizeMachineStart:** authenticate both member and registered controller, enforce matching
   machine/member/reservation, current certification and time window, deny on any unavailable or
   uncertain dependency, create one session by idempotency key, return within five seconds, publish
   authorization/session events, and audit every allow/deny reason.
4. **EndMachineSession:** authenticate member/controller, close only the active matching session,
   make duplicate ends harmless, publish MachineSessionEnded, and release availability.
5. **ResolveSafetyIncident:** require maintainer assignment, diagnosis, repair action, independent
   check, and timestamp; transactionally close incident/remove lockout, publish
   EquipmentRestoredToService, and retain immutable evidence.

### 3. Implement queries and projections

- Find availability by equipment class/date using the search projection; exclude locked,
  maintenance, and overlapping intervals.
- List reservations using authenticated member ID from claims, never a trusted request-body ID.
- List open maintenance cases by state/equipment and sort by severity then detection time.
- Consume domain events idempotently to rebuild all read models. Store projection checkpoints and
  provide a replay command/runbook.

### 4. Implement event policies and workflows

- Certified Access Policy shares the same pure domain authorization function used by the start
  handler; test it as a decision table.
- Critical Incident Lockout consumes incident events, conditionally locks equipment, revokes active
  access, publishes MachineLocked, requests controller lockout, and starts recovery exactly once.
- Reservation No Show uses a scheduled check at start plus 15 minutes and releases only a still-
  confirmed reservation with no session.
- Recovery workflow waits at most 60 seconds for controller confirmation, then pages the duty
  technician for physical disconnect/tag lockout. Use task-token callbacks for diagnosis and
  independent check. Never restore automatically after timeout.
- Reservation workflow handles wait, access attempt, no-show, completion, cancellation, and incident
  interruption with explicit retries/catches and deterministic execution names.

### 5. Build the workshop simulator and UI

Add a small web application with member and staff views:

- member login, certification badges, equipment availability calendar, reservation form, session
  state, and own history;
- instructor certification form with equipment class and expiry;
- technician incident queue, signal evidence, controller status, checklist, independent approval,
  and restore action;
- operations dashboard for utilization, denied starts, lockout latency, open incidents, and DLQs.

Add a controller simulator that registers a demo machine, requests start/end, publishes telemetry,
injects emergency-stop/thermal/interlock faults, acknowledges lockout, and can simulate offline mode.
The simulator must use a distinct device credential and cannot call staff APIs.

### 6. Finish security and privacy

1. Configure separate member, staff, and device authentication; enforce issuer, audience, expiry,
   equipment assignment, role, and resource ownership in code and gateway policy.
2. Replace broad generated statements with exact ARNs/actions and run IAM Access Analyzer plus the
   generated security tests. Keep permission boundaries.
3. Onboard real external credentials out of band; implement/test rotation functions and alarms.
4. Encrypt data, queues, topics, logs, backups, and secrets; test denied decrypt/read paths.
5. Mask member/contact/signal values in logs. Never log tokens, raw notification destinations,
   controller secrets, or free-form maintenance evidence.
6. Implement retention/deletion/legal-hold jobs and member export/rectification workflows.

### 7. Turn generated tests into evidence

Replace placeholders with assertions for:

- all command success, rejection, duplicate, concurrency, stale-certification, overlap, lockout, and
  cross-member authorization cases;
- access decision p99 under five seconds at 200 concurrent requests;
- incident lockout under 60 seconds, controller timeout, physical escalation, repair rejection, and
  safe restoration;
- schema backward compatibility, event envelopes, ordering, replay, poison messages, retries, and
  DLQ redrive;
- transaction conflicts, projection rebuild, backup restore, secret rotation, least privilege,
  account/region isolation, and rollback;
- end-to-end normal journey and injected emergency-stop journey using the simulator.

Use local/in-memory adapters for fast tests and deployed dev-stack tests for integration behavior.

### 8. Validate, deploy, and rehearse

From `generated-project` run the generated build, contract, test, and template scripts. Install Go,
AWS SAM CLI, AWS CLI, cfn-lint, and the configured linters in CI. Validate all three templates and
OpenAPI/ASL/JSON schemas, then deploy dev, run migrations/seed demo data, execute integration/e2e
tests, and promote the same packaged artifacts to test and prod through change-set approval.

Before calling the project deployment ready:

1. Close all 66 generated manual-action records with links to code, configuration, tests, or an
   accepted residual-risk record.
2. Run the physical power-isolation drill with Safety Lead and duty technician; record timing and
   evidence without connecting hazardous equipment during early workshops.
3. Exercise backup restore, projection replay, DLQ redrive, secret rotation, controller outage,
   regional/account access controls, rollback, and incident communications.
4. Confirm dashboards, alarms, owners, escalation targets, runbooks, on-call coverage, SLO/error
   budget, cost budget, and log/data retention.
5. Produce signed security, privacy, safety, operations, and business acceptance evidence and update
   readiness from `DEPLOYMENT_READY` to `PRODUCTION_READY` only then.

The recommended exhibition script is: certify a member, reserve the laser cutter, authorize and end
a normal session, inject a thermal-limit incident, observe immediate denial/lockout and technician
workflow, complete independent repair verification, restore equipment, and show the trace from
business goal through model elements, AWS resources, logs, metrics, and generated artifacts.
