# AWS PSM Refinement Guide: Smart Makerspace Operations

## Generated baseline

The PIM-to-PSM ETL produced three deployable stacks with 12 Lambda functions, 2 Step Functions state
machines, 3 DynamoDB tables, 54 queues, 6 topics, EventBridge buses/rules/schedules, Cognito pools,
secrets, KMS keys, IAM roles, logs, alarms, and dashboards. It correctly left provider decisions as
blocking readiness findings.

The repeatable edit is [`scripts/refine-psm.ps1`](./scripts/refine-psm.ps1). This guide explains the
modeler's platform decisions.

## Refinement simulation

1. **Accounts and region.** Use separate dev/test/prod accounts, approved `eu-west-1` residency,
   stage-specific artifact buckets and deployment roles, production approval, confirmed change
   sets, and a documented recovery plan. Use stable makerspace names and mandatory ownership,
   environment, service, source, data-classification, and cost tags.
2. **Stacks.** Keep one SAM stack per service deployment unit. Enable SAM and cfn-lint validation,
   individual packaging, named-IAM capability, retained stateful resources, and semantic release
   ordering for contracts/events.
3. **APIs.** Add validated REST routes and Lambda proxy integrations to each generated API. Scope
   Lambda permissions to the production account and execute-API source ARN. Enable access logs,
   metrics, tracing, request models, authentication, throttling, and failure-safe timeouts.
4. **Storage.** Retain generated DynamoDB resources for document projections. Bind transactional
   operations and incident stores to encrypted retained `AWS::RDS::DBCluster` native resources.
   Bind availability search to an encrypted `AWS::OpenSearchServerless::Collection`. Resolve keys,
   point-in-time recovery, backup, masking, residency, deletion protection, and rebuild procedure.
5. **Messaging.** Assign encrypted service dead-letter queues with 14-day retention to asynchronous
   handlers/subscriptions. Review visibility timeout, retry count, per-equipment ordering,
   deduplication, EventBridge target role, event pattern, archive/replay, and alarms.
6. **Compute.** Confirm Go runtime, architecture, package, code URI/handler, memory, timeout,
   concurrency, tracing, structured logging, log retention, KMS, environment variables, aliases,
   and source-scoped invocation permissions. Critical access remains fail closed.
7. **Workflow.** Complete ASL resource bindings, retries/catches, task-token callback for technician
   work, controller confirmation timeout, emergency-isolation escalation, execution logging,
   tracing, role permissions, and terminal recovery states.
8. **Identity and IAM.** Apply the organization workload permission boundary to every production
   role. Replace provider-independent permissions with explicit API, function, table, cluster,
   search, queue/topic, state-machine, secret, log, and KMS actions against concrete ARNs. Reject
   wildcard actions/resources unless an approved boundary and condition make them unavoidable.
9. **Secrets.** Generate only bootstrap values. Onboard real controller and notification credentials
   after deployment through the approved process, add dedicated rotation functions, test rollback,
   and prohibit values from model, templates, environment JSON, logs, and source control.
10. **Observability.** Keep 90-day logs, correlation IDs, traces, dashboards, queue/DLQ alarms, access
    latency, denied access, incident-to-lockout latency, unresolved-case age, and restoration
    metrics. Route critical alarms to the duty technician and Safety Lead.
11. **Readiness.** Resolve each generated finding with a modeled choice or transfer it explicitly to
    artifact implementation evidence. Final PSM is `DEPLOYMENT_READY`, not production ready.

Create and validate the final PSM:

```powershell
& mde/samples/smart-makerspace-operations/scripts/refine-psm.ps1
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar psm `
  --repo-root . `
  --model mde/samples/smart-makerspace-operations/smart-makerspace.final.awspsm.xmi `
  --fail-on-mandatory-violations --fail-on-optional-violations
```

The final PSM reports **0 mandatory and 0 optional violations**. Generate artifacts with:

```powershell
java -jar tools/mde-m2t-cli/target/mde-m2t-cli-0.0.1-SNAPSHOT.jar aws-psm-to-artifacts `
  --repo-root . `
  --source-model mde/samples/smart-makerspace-operations/smart-makerspace.final.awspsm.xmi `
  --output-dir mde/samples/smart-makerspace-operations/generated-project
```

The real generator produced 279 files for this case study.
