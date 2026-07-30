# AWS PSM Code Generator E2E

Validation boundary: generated model output is gated by structural Ecore/EMF conformance only. This
E2E test does not invoke EVL semantic validation.

## Fixture

The E2E source model is:

`packages/java/mde-m2t-runner/src/test/resources/awspsm/e2e/localstack-serverless-system.awspsm.xmi`

It models a small serverless runtime stack:

- Lambda function and IAM execution role/policy.
- Lambda function URL and API Gateway HTTP API route targeting the Lambda function.
- CloudWatch log group.
- DynamoDB table keyed by `pk`.
- SQS queue and SNS topic.
- S3 bucket.
- SSM parameter and Secrets Manager secret.
- EventBridge bus and rule targeting the queue.
- Step Functions workflow with generated ASL.

## Test

`EpsilonEgxGeneratorTest.deploysGeneratedAwsArtifactsToLocalStackAndExecutesThem` generates the
project from the checked-in fixture, parses generated JSON/YAML, builds the generated Go Lambda
bootstrap, uploads generated package artifacts, deploys the generated SAM/CloudFormation template to
LocalStack, and executes live service assertions.

Protected-region business logic remains explicit: the generated Lambda is invoked as a generated
bootstrap and returns the generated error response shape unless developer-owned business logic is
merged into the protected region.

## Live Evidence

The test verifies these generated artifacts against LocalStack:

- `template.yaml` deploys Lambda, IAM, logs, DynamoDB, SQS, SNS, S3, SSM, Secrets Manager, and
  EventBridge resources.
- The generated Lambda function URL is looked up through `lambda get-function-url-config`, invoked
  over HTTP, and verified to return the generated handler response.
- The generated API Gateway HTTP API is looked up through `apigatewayv2 get-apis`, invoked over
  HTTP at `/runtime`, and verified to return the generated handler response.
- `asl/varka-localstack-workflow.asl.json` parses and is executed through Step Functions.
- `src/functions/varka-localstack-handler/handler.go` compiles to the Lambda `bootstrap`, is
  accepted by LocalStack Lambda, reaches `function-active-v2`, and responds to invocation.
- Generated Go tests run with `GENERATED_LAMBDA_FUNCTION_NAME` pointed at the deployed function.

Runtime assertions perform real service calls:

- SQS `send-message` and `receive-message`.
- DynamoDB `put-item` and `get-item`.
- S3 object upload and list.
- SSM `get-parameter`.
- Secrets Manager `get-secret-value`.
- SNS `publish`.
- EventBridge `put-events`.
- Step Functions `start-execution`.
- Lambda function URL HTTP POST.
- API Gateway HTTP API POST `/runtime`.

## LocalStack Mode

The test first reuses a running LocalStack container only when its health endpoint responds. If the
compose container is unavailable or restarting, it checks `.env` for `LOCALSTACK_AUTH_TOKEN` and
starts an isolated `localstack/localstack:latest` container with the token on a disposable Docker
network. If no token is available, it starts the pinned community image
`localstack/localstack:3.8.1`.

On the current verified run, the compose LocalStack container was not reused because it was
restarting with host proxy configuration. The test-owned `localstack/localstack:latest` container
activated with the `.env` LocalStack token and executed the live assertions.

When LocalStack does not materialize the generated `AWS::Serverless::StateMachine` from the SAM
template, the test keeps the generated SAM state machine assertion in place, then creates the
workflow directly from the generated ASL artifact and executes it. This documents a
CloudFormation/SAM emulator coverage gap while still giving the ASL generator live execution
coverage.
