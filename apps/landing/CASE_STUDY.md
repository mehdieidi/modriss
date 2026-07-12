# Signal Desk Landing Demo

Signal Desk is a deliberately small support-ticket system used by the landing page to explain the
complete Varka workflow without hiding it behind a large sample model.

## CIM

- `Customer` actor issues the user-initiated `SubmitTicket` command.
- `SubmitTicket` targets `TicketAggregate` and produces `TicketSubmitted`.
- `TicketAggregate` is rooted at the `SupportTicket` domain entity.
- `GetTicket` reads `SupportTicket`.
- `Support Intake` owns the business capability.

## CIM to PIM

The landing demo follows the repository's ETL mappings:

- `Command2Function` and `UserInitiatedCommand2ApiRoute` create the command handler and API route.
- `Query2Function` and `Query2ApiRoute` create the query handler and route.
- `Entity2Schema` creates the ticket contract.
- `Aggregate2DataStore` creates the persistent ticket store.
- `BusinessEvent2EventType` creates the versioned `TicketSubmitted` event contract.
- Capability-based boundaries produce the `Support Intake Service`.

The AI modeling assistant then applies the prompt:

> Notify the support team whenever a ticket is submitted.

It adds a notification function, topic, subscription, and pub/sub flow while preserving PIM
conformance.

## PIM to AWS PSM

- `Function2AwsLambdaFunction` realizes both handlers as Lambda resources.
- `Api2HttpApi` realizes the provider-independent API.
- `DataStore2DynamoDbTable` realizes the ticket store.
- `Topic2SnsTopic` realizes the AI-added notification topic.
- Relationship-resolution helpers generate API integration, SNS subscription, IAM, logging, and
  observability resources.

The manual refinement increases `SubmitTicketFunction.memorySizeMb` from `256` to `512` and enables
active tracing.

## AWS PSM to Artifacts

The EGL/EGX generation sequence shown in the landing page is based on
`mde/generation/awspsm-to-artifacts` and includes:

- AWS SAM templates and configuration;
- Go Lambda handlers and shared runtime code;
- OpenAPI and JSON Schema contracts;
- tests, scripts, documentation, and trace reports;
- protected regions that preserve developer-owned business logic across regeneration.
