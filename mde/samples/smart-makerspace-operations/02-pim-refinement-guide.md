# PIM Refinement Guide: Smart Makerspace Operations

## Generated baseline

The CIM-to-PIM ETL created 3 services, 12 functions, 2 workflows, 6 stores, 3 APIs, 45 channels,
29 flows, 18 event types, 52 schemas, identity/security elements, deployments, configuration, and
readiness items. Preserve the draft and refine a copy; never erase source traces.

The exact repeatable edit is implemented by
[`scripts/refine-pim.ps1`](./scripts/refine-pim.ps1). The following is the modeler's activity behind
that script.

## Refinement simulation

1. **Confirm boundaries.** Keep Safety Qualification, Equipment Operations, and Maintenance
   Recovery as bounded-context services. Assign Safety Platform, Workshop Experience, and Equipment
   Reliability owner teams. Review every service membership and cross-service flow.
2. **Choose implementation posture.** Replace the default `CUSTOM/NONE/TBD` profile with Go 1.24,
   Go modules, typed contracts/runtime validation, approved source layout, testing, lint, format,
   build, and dependency policy. Choose `HYBRID_SERVERLESS` because APIs, events, schedules, and
   long-running workflows coexist.
3. **Refine APIs.** Give service APIs `/qualification/v1`, `/operations/v1`, and `/maintenance/v1`
   base paths and version `1.0.0`. Require authentication, OpenAPI, request/response validation, and
   exactly one handler/workflow integration per route.
4. **Complete contracts.** Review all generated schemas. Add typed SafetySignal fields
   (`signalType`, measured value, unit, observed time) and MaintenanceResolution fields (diagnosis,
   repair, independent check, verification time). Add signal enum literals and protect safety data.
5. **Select storage semantics.** Use document/read-your-writes for qualification, transactional
   relational semantics for operations and incidents, search for availability, and document read
   models for member reservations and maintenance cases. Set encryption, recovery, volume, rate,
   access patterns, indexes, and projection replay rules.
6. **Budget compute.** Review every function's kind, contract, state access, events, and adapter
   calls. Set IO-bound defaults, 250 ms average, 1.5 second P95, peak concurrency 200, and mark
   AuthorizeMachineStart cold-start sensitive. Attach timeout, idempotency, resilience,
   observability, and security policies.
7. **Resolve event topology.** Use per-equipment ordering and effective-once-with-idempotency for
   safety-critical channels. Add missing bus routing rules, synchronize channel/event producers and
   consumers, and represent workflow delivery through one generated flow/trigger rather than a
   contradictory duplicate reference.
8. **Complete integrations.** Select MQTT over TLS with registered device credentials for the
   controller network. Select HTTPS REST with rotated token credentials for notification delivery.
   Use configuration references for endpoints and fail closed when controller state is uncertain.
9. **Implement least privilege.** Add provider-independent Permission children: members invoke only
   own reservation/session API operations; assessors record certification for assigned equipment
   classes; maintainers manage assigned recovery cases. Attach concrete target resources and
   ownership/assignment conditions.
10. **Finish workflows.** Keep stateful week-long maximum execution. Define controller callback,
    technician assignment, four-hour acknowledgement, required resolution evidence, Safety Lead
    escalation, error selectors, retry, timeout, and physical lockout procedure.
11. **Resolve readiness.** Give every generated manual decision a decision, owner, review notes, and
    traceable affected elements. Mitigate controller connectivity and clock drift. Mark the PIM
    `DEPLOYMENT_READY`, but not production ready because implementation evidence does not yet exist.

Create and validate the refined model:

```powershell
& mde/samples/smart-makerspace-operations/scripts/refine-pim.ps1
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar pim `
  --repo-root . `
  --model mde/samples/smart-makerspace-operations/smart-makerspace.refined.pim.xmi `
  --fail-on-mandatory-violations --fail-on-optional-violations
```

The refined PIM reports **0 mandatory and 0 optional violations**. Generate the draft PSM with:

```powershell
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar pim-to-awspsm `
  --repo-root . `
  --source-model mde/samples/smart-makerspace-operations/smart-makerspace.refined.pim.xmi `
  --target-model mde/samples/smart-makerspace-operations/smart-makerspace.draft.awspsm.xmi `
  --overwrite
```
