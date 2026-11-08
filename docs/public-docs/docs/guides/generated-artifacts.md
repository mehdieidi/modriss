# Generated AWS Projects

The built-in AWS PSM generator produces a reviewable project containing infrastructure,
application files, and supporting artifacts. The exact files depend on modeled resources
and contracts.

In the Artifacts tab, right-click the project root or a folder to create a file or folder there.
Right-click a user-added file or folder to rename it; generator-owned outputs keep their required
paths for later regeneration. Use **Import ZIP** to replace the saved artifact with a locally
refined project. The archive must retain every current generator-owned output and the generation
trace file. The saved generator trace manifest is kept, so a ZIP from a different generation can
be imported even when its source model hash, element IDs, or checksums differ. Importing sources
does not update the AWS model; review infrastructure changes against that model before regenerating.
Added files and folders are accepted at the project root or beneath an existing generated
or user-owned folder. Those user-owned files and folders remain in the saved artifact and are
carried into later generations. Build output and binary assets should stay out of the archive.

This guide describes the native AWS output. [Custom cloud platforms](custom-cloud-platforms.md)
use their own EGX/EGL scripts and templates to define the generated project. Their files and
deployment requirements depend on the uploaded package. The Artifacts tab lets users switch
between outputs from different providers derived from the same CIM and PIM.

## Review Before Deployment

Inspect at least:

- `generated/reports/manual-actions.md`
- `generated/reports/security-review.md`
- `generated/reports/generation-report.md`
- Protected regions containing application-specific business logic
- External-secret placeholders, owners, environment values, and IAM rationale

Manual actions are intentional. They identify decisions that should not be guessed by a generator.

## Generated layout

The generated file tree carries the ownership modeled in the PSM:

- `services/<service>/` contains each service's Lambda entry points, API and event contracts,
  workflows, tests, create-once application use-case stubs, and `internal/domain` and adapter notes.
- `platform/runtime/` contains cross-cutting runtime support and low-level AWS SDK helpers.
- `infrastructure/units/<deployment-unit>/template.yaml` contains a PIM-generated deployment
  unit; stage files and SAM defaults live under `infrastructure/` as well.
- `contracts/public/` contains system-wide contract catalogs, while project-wide contract and E2E
  tests remain under `tests/`.

The PIM-to-AWS transformation retains `ServerlessService` as a first-class AWS PSM element and
links owned AWS resources to it. A resource without modeled service ownership uses the `application`
directory. SAM stacks remain owned by deployment units, independently of service ownership. Explicit
template paths in a hand-authored PSM remain authoritative.

Service-layer notes and application use-case files use `CREATE_IF_ABSENT` ownership: generation
creates each file once and keeps later developer edits on regeneration. Generated Lambda adapters,
templates, contracts, and runtime artifacts remain generator-owned.

## Validate and Test

Generated projects may require Docker, AWS CLI, AWS SAM CLI, Go 1.24, and `make`.

```powershell
sam validate --lint --template-file .\infrastructure\units\<deployment-unit>\template.yaml
docker run --rm -v modriss-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  golang:1.24-bookworm bash scripts/test.sh
docker run --rm -v modriss-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  golang:1.24-bookworm bash scripts/build.sh
```

Validate every generated Step Functions ASL definition before deployment.

## Deploy to the local AWS emulator

Start the MODRISS stack with Floci (or select LocalStack), configure test AWS credentials, create an artifact bucket,
then run `scripts/build.sh`, `scripts/package.sh`, and `scripts/deploy.sh` for each generated deployment unit. The scripts read templates from `infrastructure/units/`, stage files from `infrastructure/environments/`, and SAM defaults from `infrastructure/samconfig.toml`.

Success criteria include:

- Every stack reaches `CREATE_COMPLETE`.
- Lambda packages execute.
- Workflows start and reach the expected state.
- APIs accept valid requests and reject invalid requests.
- DynamoDB, SQS, SNS, and EventBridge interactions behave as modeled.
- Logs and stack events are inspectable.

Generated handlers delegate to service-owned application functions. The generated application stub
returns `NOT_IMPLEMENTED` until its use case is implemented. That proves packaging and invocation,
not business-path correctness.

## Promote to AWS

Use a dedicated non-production AWS account first. Remove any emulator endpoint override, replace
all placeholders and manual actions, review security and IAM, and promote only the exact tested and
reviewed artifact.
