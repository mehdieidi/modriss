# AWS Test workspace verification

Verified on 2026-10-31 against the local PostgreSQL/backend/frontend stack. The relational and
Cognito evaluations use Floci 2.2.0; earlier results are retained below as history.

## Automatic relational datastore follow-up

PIM `RELATIONAL` datastores now transform to typed Aurora PostgreSQL resources rather than
unresolved native resources. The AWS PSM has editable clusters, writer instances, subnet groups,
tables, columns and indexes. The transformation supplies generated Secrets Manager credentials,
typed SQL columns, identifier-based primary keys, modeled indexes, Lambda connection variables
and Data API/secret permissions. Cross-stack references produce exports/imports and deployment
ordering. Key-value/document/cache stores retain their DynamoDB mapping.

Artifact generation supplies CloudFormation, a SQL schema and manifest, a transactional schema
initializer, and a Go Data API client with parameterized statements and transaction callbacks.
Deployment initializes the real database after provisioning. An unchanged schema is retained;
a changed initial-schema fingerprint requires a reviewed migration rather than dropping data.
Missing schema identifiers/fields, CDC/outbox behavior, SQL user privileges, and business queries
remain explicit implementation decisions where the PIM does not supply enough information.
The assistant's structural-only validation boundary is unchanged; the added relational EVL rules
belong to explicit user model validation.

The actual climate-relief PIM was cloned, transformed and regenerated through the backend,
producing 355 artifact files with no unresolved native-datastore references. No CIM/PIM workaround
was needed. Existing PSMs and artifact snapshots require **PIM → AWS PSM → artifacts** regeneration
to receive this mapping. The original user project and failed environment were not edited/reset.

The initial full climate deployment exposed a Floci 2.2 CloudFormation limitation: published Step
Functions versions/aliases cannot be provisioned. The Test worker now applies an explicit Floci
Test profile to its in-memory copy of selected SAM templates. It removes `AutoPublishAlias`, alias
event-target settings, and alias deployment preferences, preserves each workflow definition, and
records affected resources in the deployment result and timeline. The saved artifacts and modeled
publication settings remain unchanged.

The latest relational climate artifact passed profile preflight with no template or emulator
compatibility diagnostics. Its generated build passed, both stacks reached `CREATE_COMPLETE` on a
fresh Floci 2.2.0 instance, all 354 resources were discovered, and the generated relational schema
initialized. The profile recorded three workflow aliases omitted from that deployment. This
verifies build and infrastructure deployment; it does not verify the unfinished climate business
handlers or workflow decision paths. Explicit Step Functions version/alias resources and configured
Secrets Manager rotation schedules still fail preflight because their behavior cannot be safely
adapted. Incomplete rotation schedules without a rotation Lambda remain omitted while the existing
`SECRET_ROTATION_LAMBDA_REQUIRED` decision stays visible.

To evaluate the relational path independently, a second isolated project retained the actual
transformed climate database, its 24-column schema, credentials, one function and its supporting
resources. Its eight modeled resources generated 71 files. Unrelated API/event/workflow resources
were excluded from this focused fixture. Its test-only completed handler was applied through the
artifact editing API and redeployed through the normal Test pipeline.

All 11 real relational checks passed:

1. PostgreSQL engine/database identity returned by actual SQL.
2. The generated table and all 24 typed columns exist.
3. Parameterized insertion and persisted reads.
4. Duplicate primary-key rejection by the database.
5. Required-field rejection by the database.
6. Real transaction rollback leaves no row.
7. Real transaction commit retains the row.
8. Code redeployment retains schema/data.
9. The generated Go client executes SQL inside the real Lambda runtime; a unique source revision
   proves the refined code was actually deployed.
10. The generated Go transaction helper rolls back a failed callback.
11. The generated Go transaction helper commits a successful callback.

The Test workspace exposes SQL execution, table/column browsing, transaction controls, result
grids, and bounded before/after database observations. Real SQL and Lambda evidence is retained
in the isolated evaluation project. Full climate business handlers, external endpoints, role and
ownership checks, and workflow decisions still require implementation and evaluation.

Focused verification also passed 27 modeling configuration tests, four ETL regression tests,
five PSM semantic tests (including positive/negative relational constraints), six
syntax/relational-generation tests, 40 worker/build-script tests and 114 frontend tests. The broader
representative generation/coverage tests passed earlier in the same implementation run.
No linters or formatters were run.

During resumed browser verification the emulator had restarted, and execution correctly required
redeployment. Three restoration builds encountered transient Go registry/checksum `unexpected EOF`
errors. Generated build scripts now retry dependency resolution up to three times in the same
workspace, retaining downloaded modules. Tests/build failures remain failures and are not retried.
Three executable shell checks cover transient recovery, exhausted retries, and failing Go tests.
The restoration deployment subsequently succeeded. The live browser check passed schema browsing,
table selection, SQL execution, schema/result grids, desktop/mobile layouts and zero page errors.
The SQL result confirmed all five existing evaluation rows survived the emulator restart and
redeployment. The browser check exposed and verified a fix for long resource names overflowing
the mobile inspector. Desktop and both mobile screenshots were also inspected visually.

Repeat the relational integration/browser checks against an isolated generated project using
`TEST_RELATIONAL_SESSION` pointing to JSON containing `projectId`, `artifactId`, `psmId` and
`deploymentJobId`. `TEST_AUTH_SESSION` supplies the authenticated evaluation session. Set
`TEST_RELATIONAL_REFINE_HANDLER=1` only when intentionally replacing the selected evaluation
function with the test fixture; the live harness retains evaluation rows for inspection.

Local ignored evidence includes:

- `target/relational-slice-live-tests.log`
- `target/test-environment-relational.json`
- `target/relational-final-backend-build-tests.log`
- `target/relational-final-generation-tests.log`
- `target/relational-semantic-tests.log`
- `target/relational-worker-tests.log`
- `target/relational-all-frontend-tests.log`
- `target/relational-build-generation-tests.log`
- `target/relational-restoration-deployment.json`
- `target/floci-workflow-compatibility-preflight.json`
- `target/relational-ui-live-tests.log`
- `target/test-environment-relational-ui.json`
- `target/test-environment-relational-desktop.png`
- `target/test-environment-relational-mobile-inspector.png`
- `target/test-environment-relational-mobile.png`

## Earlier Cognito and climate-relief follow-up

The reported `Invalid API id specified` failure exposed invalid generated REST authorizer
and route definitions. The fixes cover concrete authorizer properties, executable OpenAPI
security definitions, REST resource ownership, Lambda integration URIs, Cognito pool exports
and stack ordering, and valid Cognito deletion-protection values. PIM identity-provider
references now select Cognito before role/scope heuristics. Native JWT authorizers remain
limited to HTTP APIs. Role and case-ownership requirements remain explicit implementation
decisions; accepting a signed identity does not implement those business checks.

Additional fixes discovered through actual execution include:

- Unspecified Lambda reserved concurrency is omitted. The former default of zero disabled
  generated functions and produced throttling errors. An explicitly configured zero is retained.
- GET/HEAD/DELETE object inputs become query/path parameters rather than discarded GET bodies.
- Imported REST request models receive their real schemas through API Gateway model APIs,
  including local schema references; Floci's empty imported schemas no longer bypass validation.
- SNS HTTP subscriptions require actual endpoint URLs and belong to their topic's stack.
- Unresolved workflow routing remains an explicit refinement failure in valid deployable ASL.
- Preflight reports all detected template problems before build/provisioning. Failed stacks
  cannot use the unchanged-template optimization, and `ROLLBACK_COMPLETE` is diagnosed before
  rebuilding. Floci 2.2 ignores `OnFailure=DO_NOTHING`, so automatic stack recovery is not claimed.
- Dedicated environments now default to Floci 2.2.0, which supports the evaluated Cognito path.
  An actual 2.0.1-to-2.2 upgrade retained fixture DynamoDB records and created a volume backup.

Latest focused checks passed: 113 frontend tests, 12 backend service/lifecycle tests,
29 worker tests, four ETL regression tests, three representative/broad/binding generation
tests, and eight additional syntax/generation checks. The final ETL concurrency regression
also passed. Compose configuration, migration-version validation, and `git diff --check` passed.
No linters or formatters were run.

The representative completed sample was exercised on 2.2.0 through APIs, functions, workflow
success/failure and history, data, messaging, logs, scripts, isolation, evidence, and desktop/mobile
UI. The combined rerun stopped at an outdated hardcoded redeployment marker; the marker was
corrected, and the separate redeployment check then passed with updated code, stable resource IDs,
and retained data. This is not represented as a successful exit of that earlier combined run.

Before the relational follow-up above, the climate-relief PIM was cloned into isolated evaluation projects, transformed, and regenerated
through the production backend, producing 351 artifact files. The original project and its
failed environment were not reset or edited. That earlier regenerated project was blocked
by 11 references to an unresolved native datastore. It also needs real external URLs, business
handlers, role/ownership enforcement, and workflow decisions. No complete climate business
acceptance or deployment is claimed.

To verify its generated API boundary independently, an isolated fixture retained the actual two
REST APIs, four Cognito pools, 20 generated functions, and their supporting infrastructure:
172 resources across two stacks. Unrelated unfinished business resources were excluded from this
focused fixture. The initially deployed fixture still had the former zero-concurrency reservation;
those reservations were removed through Lambda's management API after diagnosing the throttling.
Freshly regenerated templates were separately checked to omit the unspecified reservation.
Both APIs passed seven real request scenarios each:

| Scenario                             | Actual result                       |
| ------------------------------------ | ----------------------------------- |
| Missing bearer token                 | 401                                 |
| Malformed token                      | 401                                 |
| Real token with a tampered signature | 401                                 |
| Missing required query values        | 400                                 |
| Valid GET to an unrefined handler    | 500 with explicit `NOT_IMPLEMENTED` |
| Invalid POST body                    | 400                                 |
| Valid POST to an unrefined handler   | 500 with explicit `NOT_IMPLEMENTED` |

After updating only the two GET handlers with the completed
`test-environment-cognito-handler.go` evaluation fixture, both APIs returned **200** using real
Cognito-issued JWTs. The handlers received the original bearer header and verified Cognito
subject. Missing tokens continued to return 401. These handlers demonstrate the completed
authentication/integration path and do not implement climate-relief business rules.

`apps/frontend/tests/test-environment-cognito.live.mjs` repeats these checks against an existing
isolated generated deployment. Supply `TEST_COGNITO_DEPLOYMENT` (the worker's deployment result),
`TEST_COGNITO_EXAMPLES` (GET/query and POST/body examples keyed by API logical ID), and
`TEST_COGNITO_NETWORK`. Set `TEST_COGNITO_REFINED_GET=1` after deploying the completed GET fixture.
It creates evaluation identities only in that selected emulator and excludes tokens from its report.
The final repeatable run exited successfully with all 14 scenarios passing in refined-GET mode:
200 for the completed GET handlers, 500/`NOT_IMPLEMENTED` for the untouched POST scaffolds,
and the expected 401/400 rejections before integration.

Local ignored follow-up evidence includes:

- `target/api-cognito-resumed-frontend-tests.log`
- `target/api-cognito-resumed-backend-tests.log`
- `target/api-cognito-worker-tests.log`
- `target/api-query-final-tests.log`
- `target/api-cognito-final-coverage-tests.log`
- `target/api-concurrency-etl-tests.log`
- `target/api-cognito-redeploy-tests.log`
- `target/climate-current-preflight-diagnostics.json`
- `target/generated-cognito-api-deployment.json`
- `target/generated-cognito-authentication.json`
- `target/generated-cognito-refined-requests.json`
- `target/api-cognito-final-live-tests.log`
- `target/test-environment-cognito.json`
- `target/test-environment-preflight-desktop.png`
- `target/test-environment-preflight-mobile.png`

## Original workspace verification (Floci 2.0.1)

The workbench now continues from saved, developer-refined AWS artifacts into real backend
emulation. Deployment captures immutable source files and a SHA-256 fingerprint. Executions
retain their inputs, resource identities, actual observations, errors, responses, and timestamps.
The Test tab and deployment control disappear when an uploaded cloud provider is selected.

## Checks completed

| Check                                                                                                       | Result                                                  |
| ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| Frontend Node regression tests                                                                              | 113 passed                                              |
| Backend access, source archive, fingerprint, dedicated environment lifecycle, and API contract tests        | 15 passed (14 focused checks plus the OpenAPI contract) |
| Disposable worker endpoint/path isolation, packaging, authorization, route discovery, and code update tests | 13 passed                                               |
| Flyway global version check                                                                                 | Passed; migration created with the repository helper    |
| Backend Maven package                                                                                       | Passed                                                  |
| Standard Compose runner image build                                                                         | Passed                                                  |
| Compose configuration validation                                                                            | Passed                                                  |
| Persisted execution history cursor                                                                          | Passed against actual saved executions                  |

Linters and formatters were not run, following AGENTS.md.

## Live sample execution

The integration test imports the HTTP architecture PSM sample, generates an actual 89-file
artifact through the production pipeline, and imports a developer-refined Go/SAM project using
the source ZIP endpoint. The refined project provisions 28 resources on Floci, including a real
REST TOKEN Lambda authorizer.

The successful checks include:

- Lambda invocation, real runtime errors, and captured container stdout/stderr.
- REST and HTTP API requests with actual Lambda responses and DynamoDB writes.
- Standard bearer-token requests through REST and HTTP APIs, exact header preservation,
  unauthenticated requests without added credentials, denied application access, and a real REST
  TOKEN Lambda authorizer. The browser also executes a bearer-authenticated request.
- Two independently deployed projects with separate Floci containers and Docker networks,
  empty storage in the second project, authentication working in both, and rejection of an API
  identifier belonging to the other instance. Deleting the second project cleans its environment.
- Successful and deliberately failing Step Functions executions, execution listings, and state
  input/output observations.
- DynamoDB writes and reads, S3 writes/reads/listings, and SQS send/inspection.
- SNS and EventBridge delivery through SQS into a Lambda consumer, verified by `CONSUMED`
  records written to DynamoDB.
- Advanced AWS SDK requests, a source script, denied cross-project reads, source snapshot
  stability after artifact edits, and downloadable execution evidence.
- Browser navigation, actual execution from the UI, AWS-only control visibility with an uploaded
  Azure package, workflow keyboard inspection, history, stale-source notices, mobile layout,
  and light/dark rendering. No browser page errors were observed.
- Test entry controls appear only in AWS Artifacts, including the mobile level selector. Deploy
  sits in Advance beside Download with a distinct violet background; desktop and mobile browser
  checks verify visibility, placement, color difference, and navigation into deployment.
- Repeated deployment with unchanged infrastructure, code-only Lambda updates, execution of the
  revised code, stable resource identities, and retained DynamoDB data.
- Backend restart retained the same dedicated emulator and successful bearer-authenticated
  execution. Emulator restart blocked stale requests with HTTP 409, retained earlier evidence,
  and recovered through redeployment with existing DynamoDB records intact.
- REST method discovery worked around Floci's omitted embedded method metadata using actual
  GetMethod calls. The final browser check verified request JSON, including its bearer header,
  remained available after refresh and execution.

A separate check also deployed the **unmodified generated HTTP baseline**: all 89 generated
files built with their native Go dependencies and tests, 23 resources were provisioned, and a
generated Lambda executed successfully. This establishes scaffold execution, rather than
business-requirement acceptance.

## Reproduce

With Floci and the platform running and the worker image built:

```bash
node apps/frontend/tests/test-environment.live.mjs
```

The test exercises backend execution, browser behavior, and redeployment. Set
`TEST_KEEP_PROJECT=1` to retain its project and write an ignored local session file for the
separate `test-environment-ui.live.mjs` and `test-environment-redeploy.live.mjs` checks.
The explicit lifecycle check restarts the local backend and the retained fixture's emulator:

```bash
node apps/frontend/tests/test-environment-lifecycle.live.mjs
```

The verification run produced these ignored local artifacts:

- `target/test-environment-live-final.log`
- `target/test-environment-evidence.json`
- `target/test-environment-redeployment.json`
- `target/test-environment-native-baseline.json`
- `target/test-environment-desktop.png`
- `target/test-environment-workflow.png`
- `target/test-environment-dark.png`
- `target/test-environment-mobile.png`
- `target/test-environment-authentication.json`
- `target/test-environment-isolation.json`
- `target/test-environment-bearer.png`
- `target/test-environment-lifecycle.json`
- `target/test-environment-dedicated-live.log`
- `target/test-environment-dedicated-lifecycle.log`
- `target/test-environment-dedicated-ui.log`
- `target/test-environment-artifact-controls.png`
- `target/test-environment-artifact-controls-mobile.png`

## Interpretation and limits

The observed Lambda and workflow failures were intentional negative scenarios; the tests checked
their actual diagnostics. No simulated success responses or mocked browser API responses were
used in the live checks.

The shared-account adapter has been replaced with a dedicated Floci instance, private network,
and persistent volume per project. The instance's default account selects the project; the API
adapter preserves application `Authorization` values unchanged and does not add credentials to
unauthenticated requests. AWS management calls remain signed separately, and API IAM signing
is available explicitly with `auth: "aws-iam"`. Legacy shared-emulator deployments require
redeployment; their immutable source snapshots and evidence remain readable.

Floci 2.0.1 returns 403 for the fixture's missing-token REST TOKEN-authorizer case because it
invokes the authorizer with no token and uses its Deny policy. AWS normally rejects the missing
identity earlier with 401. The workspace records that distinction without rewriting statuses.
The application-level REST/HTTP checks independently verify 200, 401, and 403 outcomes and exact
header preservation for valid, missing, invalid, expired, and forbidden opaque token inputs.
These checks do not claim cryptographic JWT verification. Other emulator limitations, bounded
capture, source ZIP constraints, runtime tools, and setup are documented in the
[Test workspace guide](../public-docs/docs/guides/floci-emulator.md).

The existing OrderPulse XMI was also inspected, but its import was rejected by the current
metamodel with an SNS subscription reference error. It was not used for execution claims and was
not changed in this feature. Both execution samples above use the current HTTP architecture sample.

These checks validate the Test workspace and representative emulated execution paths. They do
not establish acceptance of a future case study's business requirements or equivalence to all
real AWS behavior. Case-study scenarios still need their own expected outcomes and retained
evidence.
