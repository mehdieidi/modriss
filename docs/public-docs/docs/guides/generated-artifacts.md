# Generated AWS Projects

An AWS PSM can generate a complete reviewable project rather than a single infrastructure file.
The exact files depend on modeled resources and contracts.

## Review Before Deployment

Inspect at least:

- `generated/reports/manual-actions.md`
- `generated/reports/security-review.md`
- `generated/reports/generation-report.md`
- Protected regions containing application-specific business logic
- External-secret placeholders, owners, environment values, and IAM rationale

Manual actions are intentional. They identify decisions that should not be guessed by a generator.

## Validate and Test

Generated projects may require Docker, AWS CLI, AWS SAM CLI, Go 1.24, and `make`.

```powershell
sam validate --lint --template-file .\template-<deployment-unit>.yaml
docker run --rm -v varka-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  golang:1.24-bookworm bash scripts/test.sh
docker run --rm -v varka-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  golang:1.24-bookworm bash scripts/build.sh
```

Validate every generated Step Functions ASL definition before deployment.

## Deploy to the local AWS emulator

Start the Varka stack with Floci (or select LocalStack), configure test AWS credentials, create an artifact bucket,
then run SAM build, package, and deploy for each generated deployment-unit template.

Success criteria include:

- Every stack reaches `CREATE_COMPLETE`.
- Lambda packages execute.
- Workflows start and reach the expected state.
- APIs accept valid requests and reject invalid requests.
- DynamoDB, SQS, SNS, and EventBridge interactions behave as modeled.
- Logs and stack events are inspectable.

Generated handlers may intentionally return `NOT_IMPLEMENTED` until protected business logic is
completed. That proves packaging and invocation, not business-path correctness.

## Promote to AWS

Use a dedicated non-production AWS account first. Remove any emulator endpoint override, replace
all placeholders and manual actions, review security and IAM, and promote only the exact tested and
reviewed artifact.
