# MODRISS scope demonstrations

This collection supplies **48 XMI models for 16 cases**: the fifteen projects in
`.idea/scope/case-studies.md` and the additional ColdChain Sentinel analytical
scenario. Every case has a small CIM, an authored PIM refinement and an authored
AWS PSM refinement. Models represent system designs, rather than runtime records.

Dedicated samples for the architecture-pattern catalogue, topology matrix and
transaction matrix are in the [architecture-pattern collection](../architecture-patterns/README.md).

The examples follow the landing site's domain → serverless architecture → AWS
design → generated baseline path. They preserve the distinctive mechanisms from
the briefs, instead of presenting the same CRUD design under different names.

| Case                   | Demonstration                                                            | Models and walkthrough               |
| ---------------------- | ------------------------------------------------------------------------ | ------------------------------------ |
| C01 MemberLedger       | Membership ownership, version conflicts and attributable history         | [Open](memberledger/README.md)       |
| C02 ParcelDesk         | Queued dispatch, simulator replay, cancellation and returns              | [Open](parceldesk/README.md)         |
| C03 RoomSlot           | Atomic fixed-slot allocation and confirmation/expiry cutoff              | [Open](roomslot/README.md)           |
| C04 GrantDesk          | Assigned human review, clarification, evidence and overdue escalation    | [Open](grantdesk/README.md)          |
| C05 CampusSignal       | Two independent topic/queue subscribers, preferences and delivery replay | [Open](campussignal/README.md)       |
| C06 IntakeCheck        | Private versioned CSV input, object events and bounded validation        | [Open](intakecheck/README.md)        |
| C07 RenewalRun         | UTC schedule, immutable snapshots, discrepancies and checkpoints         | [Open](renewalrun/README.md)         |
| C08 StockBridge        | Explicit simulator contracts, versioned stock mapping and retry          | [Open](stockbridge/README.md)        |
| C09 FacilityWatch      | Observation sequence, sustained thresholds, maintenance and freshness    | [Open](facilitywatch/README.md)      |
| C10 QueueView          | Read models, counts, versioned snapshots and generation cutover          | [Open](queueview/README.md)          |
| C11 WorkspaceFlow      | Sequential tasks, explicit choices, wait, retries and visible failure    | [Open](workspaceflow/README.md)      |
| C12 AccessRules        | Ordered FIRST-match eligibility rules, unknown facts and policy versions | [Open](accessrules/README.md)        |
| C13 TeamDesk           | Verified organization scope, tenant keys and role boundaries             | [Open](teamdesk/README.md)           |
| C14 EvidenceRoom       | Reviewer separation, exact content identity, holds and disposition       | [Open](evidenceroom/README.md)       |
| C15 ExportQueue        | Durable queued jobs, bounded workers and cancellation/success race       | [Open](exportqueue/README.md)        |
| E01 ColdChain Sentinel | Analytical shipment observation, excursion response and evidence         | [Open](coldchain-sentinel/README.md) |

Create one fresh frontend project per case. On its CIM tab, choose **Import
Model → XMI** and select `<case>.cim.xmi`. Open the domain, requirements and
business-process views; select an element to inspect its attributes and rules.
Repeat on the PIM tab with `<case>.pim.xmi` and on the PSM tab with
`<case>.awspsm.xmi`. Importing replaces the active model, so use a separate project
when comparing cases. The frontend creates views and performs layout on import.

A useful audience walkthrough is:

1. Explain the CIM goal, actors, aggregate, business rules and acceptance criteria.
2. Show the PIM HTTP routes, operation-specific inputs, data/keys and the case's
   distinguishing queue, topic, schedule, object, policy or workflow elements.
3. Inspect the AWS resource relationships, execution roles, logs and provider
   configuration. WorkspaceFlow includes modeled ASL with ALLOW/READY branches,
   failure defaults, a five-second wait, bounded retry and catch handlers.
4. Inspect the authored refinement trace from business operations to PIM
   functions and then Lambda resources. Source IDs are stored explicitly;
   there are no cross-file EMF references to resolve during import.
5. In a separate project, transform the CIM to obtain a new PIM draft. Compare it
   with the supplied authored refinement, then transform the authored PIM to
   obtain a PSM draft. Review the generated decisions. Generate artifacts from
   the supplied AWS PSM to demonstrate the Go/infrastructure baseline.
6. Use a brief's CR1 exercise as a manual modeling change, then compare the
   resulting downstream drafts with the original sample. The supplied files
   cover the stated baseline; TeamDesk also models the two-tenant scope explicitly.

The supplied PIM/PSM files are authored refinements, **not untouched ETL output**.
Keep generated drafts separately when demonstrating refinement or reconciliation.
Stable UUIDs and the [manifest](manifest.json) make the case/level correspondence
repeatable. Each case's `requirements.json` retains its original requirement and
acceptance excerpts, allowing regeneration even when the private `.idea/scope`
briefs are unavailable.

The [verification report](verification.json) records model hashes and results for
all 48 files. The automated checks exercise:

- structural conformance of original XMI and imported JSON;
- the backend import service, persistence, source-XMI retention, export and reimport;
- resolved graph endpoints and preserved model-element identities;
- the frontend's real graph/view/layout/save modules with backend-produced JSON;
- structural conformance and XMI export of frontend-saved models;
- CIM→PIM and authored PIM→PSM ETL, plus authored PSM→Go/artifact generation.

Frontend verification runs headlessly against the actual application modules;
it does not claim a live-browser upload or pixel-level rendering check. Pipeline
checks exercise the runners without deploying generated projects. Generated Go
handlers still require developer-owned business logic, persistence, authorization,
durable publication and acceptance tests. Simulator URLs use localhost placeholders
whose reachability must be configured for a deployment. IAM resource scopes and
identity configuration require deployment review. Conditional capabilities such
as human review, policy execution, tenancy and retention remain explicit
application responsibilities. These are working **modeling and generation demos**,
not completed applications or empirical enactment evidence.

Assistant-generated models are gated **only by structural Ecore/EMF conformance
through `ModelService.validateStructural(...)`**. EVL semantic validation remains a
separate explicit user/model validation workflow outside assistant apply, repair
and commit paths. No EVL profiles are executed by this collection's verification.

To regenerate the model files from the repository root:

```powershell
python scripts/build-scope-demo-models.py
```

To repeat checks for both collections and refresh their recorded reports (Java 17+, Maven and Node.js):

```powershell
python scripts/verify-scope-demo-models.py --record
```

Verification writes intermediate imported/frontend-saved payloads to
`target/scope-demo-verification`. No formatter or linter is run.
