# AI Modeling Assistant Sample Prompts

This prompt library shows the kinds of requests users can give the Modless AI modeling assistant.
Prompts may be used as written or adapted to the current project, model level, and selected model
elements.

## Create Complete Models

- Create a complete serverless architecture for a library management platform.
- Build a detailed serverless backend model for a vending machine network.
- Create a complete architecture for an online food delivery platform.
- Design a serverless model for a hospital appointment and patient notification system.
- Generate a complete event-driven architecture for an e-commerce marketplace.
- Create a secure serverless banking transaction platform.
- Build a complete architecture for an online learning management system.
- Design a multi-tenant SaaS project management platform.
- Create a serverless architecture for a hotel reservation system.
- Generate a complete model for an insurance claims processing platform.
- Design an IoT platform for monitoring industrial equipment.
- Create a serverless architecture for a ride-sharing backend.
- Build an event-driven order fulfillment and warehouse management system.
- Create a complete model for a digital document signing platform.
- Design a serverless architecture for a subscription billing system.
- Create a model for a real-time package tracking platform.
- Build a complete backend architecture for a social media application.
- Design a serverless platform for processing climate relief grant applications.
- Create an architecture for a smart parking management system.
- Build a model for a ticket booking and event management platform.

## Request Richer and More Connected Models

- Make this model substantially more complete and connect all related elements.
- Expand this architecture with the missing APIs, functions, data stores, events, and workflows.
- Replace this basic model with a sophisticated production-ready architecture.
- Add the missing relationships so the model clearly shows how every component interacts.
- Improve this model so there are no isolated nodes on the canvas.
- Add realistic business flows and connect their participating elements.
- Complete the model with security, observability, resilience, and operational concerns.
- Add enough model elements and relationships to represent the full domain.
- Review the current model and fill all important architectural gaps.
- Expand each service into its APIs, functions, data stores, events, policies, and dependencies.
- Make the architecture ready for transformation into an implementation model.
- Add detailed contracts, schemas, policies, and relationships to the existing model.

## Refine an Existing Model

- Add a user registration and profile management capability to this model.
- Add book reservations, overdue notifications, and fine processing to the library model.
- Add inventory replenishment and low-stock alerts to the vending machine model.
- Add refund processing to the payment workflow.
- Add an administrator API and connect it to the appropriate functions.
- Add a reporting capability using the existing event channels and data stores.
- Add a scheduled daily reconciliation process.
- Add a background function that processes expired reservations.
- Add an audit trail for all sensitive operations.
- Add support for multiple organizations and tenant isolation.
- Add a customer notification service using asynchronous messaging.
- Add an external payment provider integration.
- Add a fraud-detection step to the transaction workflow.
- Add a dead-letter queue and retry policies for failed event processing.
- Add a caching layer for frequently accessed catalog data.
- Add a search capability for the existing domain entities.
- Add data retention and backup policies to every persistent store.
- Add health monitoring and alerting to the important services.
- Split the current service into appropriate bounded serverless services.
- Merge duplicated services and update their relationships.

## Create and Connect Elements

- Add a Books API with create, update, delete, get, and search routes.
- Add a function for each route in the selected API and connect them.
- Add a data store for loans and connect every function that reads or writes it.
- Add an event type for `BookBorrowed` and connect its producers and consumers.
- Add an event channel for loan events and connect all relevant functions.
- Add a workflow for borrowing a book, including validation, inventory update, and notification.
- Add an external email provider and connect the notification function to it.
- Add an identity provider and connect it to all public APIs.
- Add authorization policies for administrator-only operations.
- Connect the selected API to the selected function.
- Connect every command handler to its owned data store.
- Connect event-producing functions to their event types and channels.
- Connect all isolated elements using valid metamodel relationships.
- Add service ownership memberships for the selected elements.
- Create schemas for the existing API requests, responses, and events.
- Connect every API route to exactly one backend function.
- Add a queue consumer function and connect its trigger, queue, and emitted events.
- Add visible relationships that reflect the semantic references already present in the model.

## Modify Attributes and Properties

- Change the domain name to Library Management.
- Set the default correlation ID name to `correlationId`.
- Change the default AWS region to `eu-west-1`.
- Rename the selected function to Process Loan Request.
- Mark the selected API as externally exposed.
- Require authentication for every public route.
- Enable encryption for all data stores and message channels.
- Mark the selected data store as the source of truth.
- Set the selected queue delivery semantics to at-least-once.
- Add a clear responsibility and rationale to the selected function.
- Add a purpose to every data-access relationship.
- Add rationales to manually maintained elements.
- Configure the selected operation as idempotent.
- Set retention requirements for audit events.
- Add descriptions for every consumer-facing route.

## Delete and Restructure

- Delete the selected obsolete function and remove its invalid relationships.
- Remove all unused and isolated elements from the model.
- Delete the legacy API and reconnect its consumers to the replacement API.
- Remove duplicated event types and update their producers and consumers.
- Delete the unused data store after confirming nothing references it.
- Remove the selected route and its dedicated backend function.
- Replace the synchronous connection between these services with asynchronous messaging.
- Replace the shared database with service-owned data stores.
- Restructure the model around bounded contexts.
- Move the selected elements into a dedicated serverless service.

## Business and Domain Modeling

- Create a CIM for a library management organization.
- Model the actors, goals, capabilities, processes, policies, and domain concepts for a hospital.
- Add the business goal of reducing loan-processing time.
- Add librarians, members, administrators, and external notification providers as actors.
- Add a borrowing process with success and failure outcomes.
- Model the business rules for reservations, renewals, overdue loans, and fines.
- Add a policy requiring administrator approval for large refunds.
- Add domain concepts for books, copies, users, loans, reservations, and fines.
- Connect each business capability to the goals it supports.
- Add measurable success criteria to the existing business goals.
- Expand the current CIM into a complete description of the business domain.
- Identify missing actors and capabilities and add them to the model.

## Platform-Independent Architecture

- Create a complete PIM from this business description.
- Add provider-independent services for catalog, users, loans, reservations, and notifications.
- Model synchronous APIs and asynchronous events for the main business flows.
- Add provider-independent security and resilience policies.
- Add schemas and contracts for all public routes and events.
- Model a saga for the selected multi-step business transaction.
- Add idempotency to all externally initiated command handlers.
- Add data-access patterns for the existing data stores.
- Add observability configuration for critical functions and workflows.
- Add deployment units and environments without choosing a cloud provider.
- Review the PIM for transformation readiness and fix its missing elements.

## AWS Platform-Specific Architecture

- Create an AWS PSM for this serverless PIM.
- Map the selected API to Amazon API Gateway and AWS Lambda.
- Map the selected queue to Amazon SQS and configure a dead-letter queue.
- Map the selected event channel to Amazon EventBridge.
- Map the selected data store to DynamoDB with encryption and point-in-time recovery.
- Add a Step Functions workflow for the selected orchestration.
- Add CloudWatch logs, metrics, alarms, and dashboards.
- Add IAM roles and least-privilege policies for every Lambda function.
- Add development, staging, and production deployment stages.
- Add AWS Secrets Manager resources for external provider credentials.
- Configure production retention and deletion protection policies.
- Add concurrency, timeout, retry, and failure-handling configuration.
- Review this AWS PSM and add missing production-readiness resources.
- Fix the PSM so it can generate valid deployment artifacts.

## Validation and Quality

- Validate the current model and explain every issue.
- Fix all mandatory validation errors and propose the changes.
- Fix the selected validation issue.
- Add the missing elements required by the metamodel.
- Resolve all invalid references and disconnected relationships.
- Review this model for incomplete contracts and schemas.
- Find elements without responsibilities, purposes, or rationales and fix them.
- Find API routes without exactly one backend integration and correct them.
- Find event types with inconsistent producers and consumers and fix them.
- Find data stores without access patterns and add appropriate patterns.
- Find public routes without authentication or authorization and secure them.
- Check whether this model is ready for transformation.
- Check whether this model is ready for artifact generation.
- Improve the model while preserving all mandatory validation constraints.

## Explain and Analyze

- Explain the current architecture in plain language.
- Explain how a user request flows through this model.
- Explain the borrowing workflow from API request to notification.
- Explain the relationships of the selected element.
- Explain why the selected policy is needed.
- Explain all validation issues and their likely causes.
- Describe the responsibilities of every serverless service.
- Identify the most important business and technical risks in this model.
- Identify single points of failure.
- Identify security and privacy concerns.
- Identify elements that appear unused or incorrectly connected.
- Compare synchronous and asynchronous flows in the model.
- Summarize the model for a business stakeholder.
- Summarize the model for a cloud engineer.
- Describe what would be generated from this model.

## Transform and Generate

- Transform this CIM into a PIM.
- Transform this PIM into an AWS PSM.
- Explain how the selected CIM elements map into the PIM.
- Explain how the selected PIM elements map into AWS resources.
- Prepare this model for transformation and fix blocking issues.
- Prepare this AWS PSM for artifact generation.
- Generate deployment artifacts from the current AWS PSM.
- Explain which generated artifacts correspond to the selected model element.
- Review transformation traceability and identify missing mappings.
- Update the model so generated contracts include all required schemas.

## Selected-Element Prompts

These prompts are especially useful after selecting one or more canvas elements.

- Explain the selected elements and their relationships.
- Connect the selected API to the selected function.
- Add a secure data store for the selected function.
- Add input and output schemas for the selected function.
- Add observability and resilience policies to the selected functions.
- Move the selected elements into a new service.
- Create a workflow using the selected functions.
- Add a queue between the selected producer and consumer.
- Replace the selected direct dependency with an event-driven interaction.
- Delete the selected obsolete elements and clean up their relationships.
- Validate only the area around the selected elements and propose fixes.
- Add missing semantic and visible relationships between the selected elements.

## Multi-Turn Conversation Examples

### Create, Review, and Approve

1. `Create a complete serverless architecture for library management.`
2. `Make it more complete and ensure all major elements are connected.`
3. `Add security, observability, backups, and failure handling.`
4. `Apply the proposal.`

### Explain, Improve, and Apply

1. `Explain the current vending machine architecture.`
2. `Identify what is incomplete or risky.`
3. `Fix those issues and propose model changes.`
4. `Yes, apply them.`

### Refine a Business Flow

1. `Explain the current book borrowing flow.`
2. `Add reservation handling when no copy is available.`
3. `Add overdue reminders and fine calculation.`
4. `Connect all new elements to the existing loan and notification services.`
5. `Approve.`

### Validate and Repair

1. `Validate this model and explain the errors.`
2. `Fix all mandatory errors.`
3. `Also fix important warnings where the intent is clear.`
4. `Apply it.`

### Selected Elements

1. Select an API and a function on the canvas.
2. `Connect the selected API route to the selected function.`
3. Select the function and a data store.
4. `Add read and write relationships with clear purposes.`
5. `Apply them.`

## Useful Follow-Up Prompts

- Do it.
- Apply it.
- Apply them.
- Yes, approve and apply the proposal.
- Go ahead.
- Proceed with the model changes.
- Make it more complete.
- Expand it further.
- Add the missing relationships.
- Connect everything appropriately.
- Fix the remaining validation issues.
- Keep the current structure but improve production readiness.
- Preserve the existing elements and add the missing capabilities.
- Undo the last applied proposal.

## Prompt-Writing Tips

- State whether you want an explanation or actual model changes.
- Ask for a **complete**, **connected**, or **production-ready** model when appropriate.
- Name important domain capabilities, actors, workflows, and external integrations.
- Mention security, observability, resilience, data ownership, and failure handling.
- Select relevant canvas elements before asking for a narrowly scoped change.
- Review proposed changes and use the approval action or an approval message such as
  `Apply the proposal`.

## Guided Modeling Task Prompts

Use these when the **Guided Modeling** panel is active. Each prompt maps to a process task ID
from `mde/methodology/process-definitions/`.

### CIM phases

- **cim.p5.information-taxonomy:** Create `DataClassification` and `InformationItem` elements for every planned entity before adding `DomainEntity` (CIM-ENTITY-001).
- **cim.p6.domain-structure:** Model `DomainEntity`, `ValueObject`, and `DomainRelationship` elements using the information taxonomy from phase 5.
- **cim.p7.behavior-surface:** Add `Command`, `Query`, and `BusinessEvent` elements for the primary use cases; link them to actors and capabilities.
- **cim.p10.bounded-context-synthesis:** Group existing capabilities, entities, commands, queries, events, and policies into `BoundedContextCandidate` elements.
- **cim.p11.requirements-governance:** Backfill `Requirement` elements with `constrains` links to modeled domain and behavior elements.
- **cim.p13.traceability-readiness:** Complete `TraceModel` links and resolve all blocking `ReadinessFinding` items before CIM→PIM transform.

### PIM phases

- **pim.p1.service-boundaries:** Define `ServerlessService` boundaries aligned to CIM bounded contexts.
- **pim.p4.compute-units:** Create `Function` and `FunctionContract` elements for each command/query handler.
- **pim.p6.integration-topology:** Model `EventChannel`, `Flow`, and routing rules for async integration.
- **pim.p11.platform-mapping-readiness:** Complete `PlatformMappingAssessment` and pass PIM EVL before PIM→PSM transform.

### PSM phases

- **psm.p2.security-baseline:** Establish `AwsSecurityBaseline`, IAM roles/policies, KMS, and Secrets Manager resources.
- **psm.p8.compute:** Deploy `AwsLambdaFunction` resources with event source mappings matching PIM functions.
- **psm.p11.integration-views-readiness:** Create `AwsRelationshipView` subtypes and close readiness before M2T generation.
