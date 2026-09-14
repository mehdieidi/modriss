# Floci AWS emulator

MODRISS can run generated AWS artifacts against Floci or LocalStack without changing the generated
CloudFormation, SAM, OpenAPI, or AWS SDK code. Floci is the default because it starts quickly and
uses the same single edge endpoint (`4566`) used by the generated tests.

## Select the provider

Copy `.env.example` to `.env` and choose one provider:

```dotenv
AWS_EMULATOR=floci
COMPOSE_PROFILES=${AWS_EMULATOR}
AWS_EMULATOR_ENDPOINT_URL=http://127.0.0.1:4566
AWS_DEFAULT_REGION=us-east-1
```

Use `AWS_EMULATOR=localstack` when a compatibility run requires LocalStack. The provider-specific
variables remain available; switching does not remove or migrate either emulator's data.

## Start and inspect

```powershell
powershell -ExecutionPolicy Bypass -File scripts/aws-emulator.ps1 start
powershell -ExecutionPolicy Bypass -File scripts/aws-emulator.ps1 status
```

```bash
./scripts/aws-emulator.sh start
./scripts/aws-emulator.sh status
```

The helper stops the opposite profile, recreates the selected container, and waits for its health
endpoint. Floci is reachable at `http://127.0.0.1:4566`; Caddy also exposes
`http://floci.localhost:8088` when the main Compose stack is running.

## Run generated projects

Generated projects contain `scripts/emulator-env.sh` and a `make emulator-env` target. Source the
helper before AWS CLI, SAM, or custom SDK commands:

```bash
source scripts/emulator-env.sh
aws sts get-caller-identity
sam deploy --stack-name case-study --no-confirm-changeset --no-fail-on-empty-changeset
make test
```

The helper sets dummy local credentials, region, `AWS_ENDPOINT_URL`, and metadata-service disable
flags. It accepts `AWS_EMULATOR=floci|localstack` and an optional
`AWS_EMULATOR_ENDPOINT_URL` override. Generated Go handlers return JSON string bodies compatible
with Lambda proxy and Function URL responses.

## State and compatibility

The Compose profile uses `FLOCI_STORAGE_MODE=memory` by default, so recreating the container gives a
clean evaluation run. Set `persistent` (and keep the `modriss-floci-data` volume) when state must
survive restarts. Remove that named volume only when intentionally discarding emulator state.

Set `FLOCI_CFN_ALLOW_STUB_UNSUPPORTED_RESOURCE_TYPES=false` for strict thesis evidence: an
unsupported CloudFormation type fails the stack instead of silently becoming a synthetic resource.
Floci provisions Lambda code in Docker, so the Compose service mounts the Docker socket. Keep this
mount limited to local development and never expose the emulator publicly.

Floci and LocalStack implement the same AWS-compatible API surface for the services used by the
generated tests (Lambda, API Gateway, S3, SQS, SNS, DynamoDB, IAM, CloudFormation, and
Step Functions). Always run a final deployment in a real AWS account before production claims.
