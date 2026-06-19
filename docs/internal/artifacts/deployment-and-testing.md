# Generated AWS Artifact Deployment and Testing

This process verifies the final downloadable project produced by the complete
CIM -> PIM -> AWS PSM -> artifacts pipeline.

## 1. Start Modless and LocalStack

LocalStack starts after the frontend and healthy backend. No `SERVICES` allowlist
is set, so every available LocalStack service can start on demand.

```powershell
$env:LOCALSTACK_AUTH_TOKEN='<your-localstack-token>'
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:4566/_localstack/health
```

The default LocalStack state is intentionally non-persistent so every deployment
test starts clean. Set `$env:LOCALSTACK_PERSISTENCE='1'` before `docker compose
up` when retained state is required. Large CloudFormation deployments containing
DynamoDB streams can expose a LocalStack persistence race, so use clean state
for release evidence.

The Compose defaults use the host HTTP proxy at
`http://host.docker.internal:2081`. Override `LOCALSTACK_HTTP_PROXY`,
`LOCALSTACK_HTTPS_PROXY`, or `LOCALSTACK_NO_PROXY` when needed.

## 2. Generate and Review

In the UI:

1. Import the CIM XMI.
2. Select **Generate PIM**, then **Generate PSM**.
3. Select **Generate Artifacts** and download the ZIP.
4. Extract the ZIP and run the remaining commands from its root.

Before deployment, review:

- `generated/reports/manual-actions.md`
- `generated/reports/security-review.md`
- `generated/reports/generation-report.md`
- every protected region containing application-specific business logic

Manual actions are intentional design decisions, not deployment-ready values.
In particular, replace generated external-secret placeholder values before a
production deployment.

## 3. Validate and Test the Download

Prerequisites are Docker, AWS CLI, AWS SAM CLI, Go 1.24, and `make`.

```powershell
sam validate --lint --template-file .\template-<deployment-unit>.yaml
docker run --rm -v modless-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  -e GOPROXY=https://proxy.golang.org,direct -e GOSUMDB=off `
  golang:1.24-bookworm bash scripts/test.sh
docker run --rm -v modless-go-cache:/go/pkg/mod -v "${PWD}:/src" -w /src `
  -e GOPROXY=https://proxy.golang.org,direct -e GOSUMDB=off `
  golang:1.24-bookworm bash scripts/build.sh
```

Validate every ASL definition:

```powershell
Get-ChildItem .\asl\*.json | ForEach-Object {
  aws --endpoint-url http://localhost:4566 stepfunctions `
    validate-state-machine-definition --definition file://$($_.FullName)
}
```

## 4. Deploy to LocalStack

```powershell
$env:AWS_ACCESS_KEY_ID='test'
$env:AWS_SECRET_ACCESS_KEY='test'
$env:AWS_DEFAULT_REGION='us-east-1'
$env:AWS_ENDPOINT_URL='http://localhost:4566'
$env:NO_PROXY='localhost,127.0.0.1'

aws --endpoint-url http://localhost:4566 s3 mb s3://modless-sam-artifacts
sam build --template-file .\template-<deployment-unit>.yaml
sam package --template-file .\.aws-sam\build\template.yaml `
  --s3-bucket modless-sam-artifacts --output-template-file packaged.yaml
sam deploy --template-file packaged.yaml --stack-name <stack-name> `
  --region us-east-1 --capabilities CAPABILITY_IAM `
  --s3-bucket modless-sam-artifacts --no-confirm-changeset
```

Deploy every generated deployment-unit template as a separate stack. Success
means every stack reaches `CREATE_COMPLETE`:

```powershell
aws --endpoint-url http://localhost:4566 cloudformation describe-stacks `
  --query "Stacks[].{Name:StackName,Status:StackStatus}" --output table
```

## 5. Runtime Verification

Use deployed names returned by the list commands rather than assuming names:

```powershell
aws --endpoint-url http://localhost:4566 lambda list-functions
aws --endpoint-url http://localhost:4566 stepfunctions list-state-machines
aws --endpoint-url http://localhost:4566 dynamodb list-tables
aws --endpoint-url http://localhost:4566 sqs list-queues
aws --endpoint-url http://localhost:4566 sns list-topics
aws --endpoint-url http://localhost:4566 apigateway get-rest-apis
```

Concrete release checks:

```powershell
# Lambda executes the packaged generated binary.
aws --endpoint-url http://localhost:4566 lambda invoke `
  --function-name <function-name> --payload fileb://events/samples/<event>.json response.json

# Workflow starts and reaches the expected terminal state.
$run = aws --endpoint-url http://localhost:4566 stepfunctions start-execution `
  --state-machine-arn <arn> --input '{}' | ConvertFrom-Json
aws --endpoint-url http://localhost:4566 stepfunctions describe-execution `
  --execution-arn $run.executionArn

# Messaging resources accept traffic.
aws --endpoint-url http://localhost:4566 sqs send-message `
  --queue-url <queue-url> --message-body '{"test":true}'
aws --endpoint-url http://localhost:4566 sns publish `
  --topic-arn <topic-arn> --message '{"test":true}'

# DynamoDB accepts and returns a record using the modeled key.
aws --endpoint-url http://localhost:4566 dynamodb put-item `
  --table-name <table> --item file://events/samples/<item>.json
aws --endpoint-url http://localhost:4566 dynamodb scan --table-name <table>
```

Call each API route using its deployed API ID and stage, and verify both a valid
request and a deliberately invalid request. Also inspect CloudWatch logs and
CloudFormation stack events after every test.

Before protected-region business logic is implemented, a generated Lambda
deliberately returns `NOT_IMPLEMENTED`; a workflow may consequently stop at a
Choice state because no business decision value was produced. That proves
deployment and invocation, but a successful business-path test requires the
protected logic and representative input.

## 6. Real AWS Promotion

For AWS, unset `AWS_ENDPOINT_URL`, use a dedicated non-production account, and
replace every required manual action, external credential, owner, and protected
region. Run the same validation, tests, build, package, deploy, and runtime
checks. Promote only the exact reviewed artifact and packaged template.

## Audience Demonstration Checklist

Show the imported CIM, generated PIM and single-root PSM, downloaded ZIP,
passing generated tests, passing SAM/ASL validation, all stacks at
`CREATE_COMPLETE`, one Lambda invocation, one completed workflow, one API call,
and observable DynamoDB/SQS/SNS effects.

LocalStack currently deploys `AWS::StepFunctions::StateMachineVersion`,
`AWS::StepFunctions::StateMachineAlias`, and `AWS::CloudWatch::Dashboard` as
fallback resources. Treat those as simulator limitations and verify them again
in a real AWS non-production account.
