# PIM enumerations

Enumerations give recurring architectural decisions a stable vocabulary across editing, EVL validation, ETL transformation, and documentation. Their literals are semantic choices rather than provider product names. For example, `QUEUE` describes competing-consumer delivery without naming SQS, and `KEY_VALUE` states a storage model without selecting DynamoDB.

Choose a literal for the meaning it records at PIM level. A later transformation may map several literals to the same provider resource, reject a combination, or retain it as metadata. `UNKNOWN` and `UNDECIDED` preserve genuine open questions; they should not be used as convenient substitutes for analysis that has already been completed.

Source: `mde/metamodels/pim/pim-types.emf`.

## Enumeration values

These are closed vocabularies: use exactly one of the listed literals, preserving capitalization. The generator and EVL rules may assign different consequences to different literals.

### `ArchitectureStyle`

| Literal                            | Practical meaning                                          |
| ---------------------------------- | ---------------------------------------------------------- |
| `EVENT_DRIVEN_SERVERLESS`          | Events dominate communication.                             |
| `API_FIRST_SERVERLESS`             | Published APIs shape the architecture.                     |
| `WORKFLOW_ORCHESTRATED_SERVERLESS` | Workflows coordinate the principal behavior.               |
| `HYBRID_SERVERLESS`                | API, event, and workflow styles are deliberately combined. |

### `BoundaryType`

| Literal                 | Practical meaning                     |
| ----------------------- | ------------------------------------- |
| `CAPABILITY_BASED`      | Follows a business capability.        |
| `BOUNDED_CONTEXT_BASED` | Follows a bounded domain context.     |
| `PROCESS_BASED`         | Follows a business process.           |
| `DATA_OWNERSHIP_BASED`  | Follows authoritative data ownership. |
| `TEAM_OWNERSHIP_BASED`  | Follows operational team ownership.   |

### `DeploymentUnitType`

| Literal                 | Practical meaning                               |
| ----------------------- | ----------------------------------------------- |
| `APPLICATION`           | Top-level application assembly.                 |
| `SERVICE`               | Independently released service.                 |
| `SHARED_INFRASTRUCTURE` | Infrastructure shared across services.          |
| `LIBRARY`               | Reusable code package, not an operated service. |
| `EDGE_INTERFACE`        | Externally exposed edge resources.              |
| `OBSERVABILITY`         | Telemetry and operational-visibility resources. |

### `OwnershipKind`

| Literal       | Practical meaning                                 |
| ------------- | ------------------------------------------------- |
| `OWNS`        | The service owns the element lifecycle.           |
| `USES_SHARED` | The service consumes a shared element.            |
| `DEPENDS_ON`  | The service requires an externally owned element. |
| `EXPOSES`     | The service publishes the element as a boundary.  |

### `FunctionKind`

| Literal              | Practical meaning                            |
| -------------------- | -------------------------------------------- |
| `COMMAND_HANDLER`    | Handles a state-changing command.            |
| `QUERY_HANDLER`      | Answers a query without command semantics.   |
| `EVENT_HANDLER`      | Reacts to an event that has occurred.        |
| `POLICY_HANDLER`     | Evaluates or enforces a policy.              |
| `ORCHESTRATION_TASK` | Performs one workflow-controlled task.       |
| `EXTERNAL_ADAPTER`   | Implements a boundary to an external system. |
| `SCHEDULED_TASK`     | Runs from a time-based trigger.              |
| `STREAM_PROCESSOR`   | Processes a continuing record stream.        |
| `MAINTENANCE_TASK`   | Performs operational housekeeping.           |

### `ComputeProfile`

| Literal             | Practical meaning                             |
| ------------------- | --------------------------------------------- |
| `IO_BOUND`          | Dominated by network or storage waits.        |
| `CPU_BOUND`         | Dominated by processor demand.                |
| `MEMORY_INTENSIVE`  | Requires a comparatively large working set.   |
| `LATENCY_SENSITIVE` | Constrained primarily by response latency.    |
| `BATCH_ORIENTED`    | Optimized for grouped records and throughput. |
| `LIGHTWEIGHT`       | Expected to use modest runtime resources.     |
| `UNKNOWN`           | Workload characteristics remain undetermined. |

### `ExecutionModel`

| Literal            | Practical meaning                                |
| ------------------ | ------------------------------------------------ |
| `REQUEST_RESPONSE` | The caller waits for a direct result.            |
| `ASYNC_EVENT`      | An event starts work without a waiting caller.   |
| `BATCH_EVENT`      | One invocation handles a collected batch.        |
| `STREAM_EVENT`     | Invocations consume stream records.              |
| `SCHEDULED`        | Time initiates execution.                        |
| `WORKFLOW_TASK`    | A workflow controls invocation and continuation. |

### `ApiStyle`

| Literal                  | Practical meaning                                   |
| ------------------------ | --------------------------------------------------- |
| `RESOURCE_ORIENTED_HTTP` | HTTP resources and methods define operations.       |
| `RPC_HTTP`               | HTTP transports named procedure calls.              |
| `GRAPHQL`                | GraphQL schema and operations define access.        |
| `EVENT_API`              | Publication and consumption form the API contract.  |
| `WEBHOOK`                | HTTP callbacks are delivered to consumer endpoints. |

### `HttpMethod`

| Literal   | Practical meaning                         |
| --------- | ----------------------------------------- |
| `GET`     | Retrieve a representation.                |
| `POST`    | Submit data for processing or creation.   |
| `PUT`     | Create or replace the addressed resource. |
| `PATCH`   | Apply a partial resource change.          |
| `DELETE`  | Remove the addressed resource.            |
| `OPTIONS` | Describe communication options.           |
| `HEAD`    | Retrieve headers without a response body. |
| `ANY`     | Accept any method at this route.          |

### `InvocationMode`

| Literal        | Practical meaning                      |
| -------------- | -------------------------------------- |
| `SYNCHRONOUS`  | The invoker waits for completion.      |
| `ASYNCHRONOUS` | The invoker hands work off.            |
| `POLLED`       | A consumer polls for available work.   |
| `SCHEDULED`    | A time expression initiates work.      |
| `ORCHESTRATED` | A workflow coordinates the invocation. |

### `ChannelKind`

| Literal     | Practical meaning                            |
| ----------- | -------------------------------------------- |
| `QUEUE`     | Competing consumers receive queued messages. |
| `TOPIC`     | Subscriptions receive topic publications.    |
| `EVENT_BUS` | Rules route events from a shared bus.        |
| `STREAM`    | Consumers read an ordered retained sequence. |
| `WEBHOOK`   | Delivery occurs through an HTTP callback.    |

### `DeliverySemantics`

| Literal                             | Practical meaning                                                |
| ----------------------------------- | ---------------------------------------------------------------- |
| `AT_MOST_ONCE`                      | Loss is possible, redelivery is avoided.                         |
| `AT_LEAST_ONCE`                     | Redelivery is possible to prevent loss.                          |
| `EFFECTIVELY_ONCE_WITH_IDEMPOTENCY` | Idempotent handling turns duplicates into one effective outcome. |
| `EXACTLY_ONCE_REQUIRED`             | One observable processing outcome is a strict requirement.       |

### `OrderingRequirement`

| Literal   | Practical meaning                      |
| --------- | -------------------------------------- |
| `NONE`    | No delivery order is assumed.          |
| `PER_KEY` | Order is preserved within each key.    |
| `GLOBAL`  | All deliveries share one global order. |

### `StoreKind`

| Literal       | Practical meaning                                    |
| ------------- | ---------------------------------------------------- |
| `KEY_VALUE`   | Key lookup is the primary access form.               |
| `DOCUMENT`    | Semi-structured documents are the stored unit.       |
| `RELATIONAL`  | Relations and keys organize data.                    |
| `GRAPH`       | Vertices and edges organize data.                    |
| `SEARCH`      | Indexed retrieval supports search semantics.         |
| `TIME_SERIES` | Time-indexed observations form the data.             |
| `CACHE`       | Temporary low-latency values support another source. |
| `OBJECT`      | Opaque objects are addressed by keys.                |

### `ConsistencyNeed`

| Literal            | Practical meaning                                                 |
| ------------------ | ----------------------------------------------------------------- |
| `EVENTUAL`         | Replicas or projections may converge later.                       |
| `STRONG`           | Successful writes must be immediately visible under the boundary. |
| `TRANSACTIONAL`    | Grouped changes commit or fail atomically.                        |
| `READ_YOUR_WRITES` | A caller must observe its own successful writes.                  |

### `WorkflowKind`

| Literal                 | Practical meaning                                       |
| ----------------------- | ------------------------------------------------------- |
| `ORCHESTRATION`         | A coordinator sequences tasks.                          |
| `LONG_RUNNING_PROCESS`  | Execution may persist across long waits.                |
| `SAGA`                  | Steps and compensations form a distributed transaction. |
| `HUMAN_APPROVAL`        | A person must approve or complete work.                 |
| `BATCH_COORDINATION`    | The workflow coordinates work over a collection.        |
| `EVENT_ROUTING_PROCESS` | Events and conditions drive process routing.            |

### `IdentityKind`

| Literal                      | Practical meaning                                  |
| ---------------------------- | -------------------------------------------------- |
| `USER_DIRECTORY`             | A directory manages end-user identities.           |
| `FEDERATED_IDENTITY`         | Trust is federated from another identity domain.   |
| `MACHINE_CLIENT`             | A non-human client authenticates programmatically. |
| `API_KEY_CLIENT`             | An API key identifies the client.                  |
| `SERVICE_PRINCIPAL`          | A workload acts through a service identity.        |
| `EXTERNAL_IDENTITY_PROVIDER` | Identity is issued outside the modeled boundary.   |

### `PrincipalKind`

| Literal           | Practical meaning                              |
| ----------------- | ---------------------------------------------- |
| `HUMAN_USER`      | One person is the actor.                       |
| `GROUP`           | Group membership supplies collective access.   |
| `ROLE`            | An assumable role carries permissions.         |
| `SERVICE`         | An application service is the actor.           |
| `EXTERNAL_SYSTEM` | A system outside the application is the actor. |
| `ANONYMOUS`       | No authenticated identity is present.          |

### `PermissionEffect`

| Literal | Practical meaning                                   |
| ------- | --------------------------------------------------- |
| `ALLOW` | Grant the action when conditions hold.              |
| `DENY`  | Explicitly prevent the action when conditions hold. |

### `PermissionActionKind`

| Literal       | Practical meaning                            |
| ------------- | -------------------------------------------- |
| `INVOKE`      | Call an executable target.                   |
| `READ`        | Retrieve a value or object.                  |
| `WRITE`       | Create or modify data.                       |
| `DELETE`      | Remove data.                                 |
| `LIST`        | Enumerate resources or entries.              |
| `PUBLISH`     | Place data on a publication channel.         |
| `SUBSCRIBE`   | Register for channel deliveries.             |
| `CONSUME`     | Receive and process deliveries.              |
| `ENCRYPT`     | Protect plaintext with encryption.           |
| `DECRYPT`     | Recover encrypted content.                   |
| `READ_SECRET` | Retrieve confidential configuration.         |
| `MANAGE`      | Perform administrative lifecycle operations. |

### `LeastPrivilegeStatus`

| Literal                     | Practical meaning                                                 |
| --------------------------- | ----------------------------------------------------------------- |
| `UNREVIEWED`                | No least-privilege review has occurred.                           |
| `GENERATED_BROAD`           | Transformation created a broad permission that needs narrowing.   |
| `REVIEW_REQUIRED`           | A human security review is still required.                        |
| `CONFIRMED_LEAST_PRIVILEGE` | Necessary actions and resources have been reviewed and confirmed. |

### `SecretKind`

| Literal             | Practical meaning                                            |
| ------------------- | ------------------------------------------------------------ |
| `API_KEY`           | Credential used to call an API.                              |
| `PASSWORD`          | Password for an account or service.                          |
| `TOKEN`             | Bearer or opaque access token.                               |
| `CERTIFICATE`       | Certificate used for trust or transport security.            |
| `CONNECTION_STRING` | Protected connection descriptor and authentication material. |
| `PRIVATE_KEY`       | Confidential private cryptographic key.                      |
| `OTHER`             | A secret form outside the listed categories.                 |

### `ConfigScope`

| Literal           | Practical meaning                 |
| ----------------- | --------------------------------- |
| `APPLICATION`     | Applies across the application.   |
| `SERVICE`         | Applies to one service boundary.  |
| `FUNCTION`        | Applies to one function.          |
| `ENVIRONMENT`     | Varies by deployment environment. |
| `DEPLOYMENT_UNIT` | Applies to one release unit.      |

### `SchemaKind`

| Literal         | Practical meaning                     |
| --------------- | ------------------------------------- |
| `REQUEST`       | Data accepted by a boundary.          |
| `RESPONSE`      | Successful data returned to a caller. |
| `ERROR`         | Structured failure data.              |
| `EVENT`         | Business event payload.               |
| `MESSAGE`       | Messaging payload.                    |
| `ENTITY`        | Identity-bearing domain entity.       |
| `VALUE_OBJECT`  | Identity-free domain value.           |
| `READ_MODEL`    | Query-oriented projection.            |
| `CONFIGURATION` | Structured configuration data.        |

### `SchemaCompatibility`

| Literal    | Practical meaning                                     |
| ---------- | ----------------------------------------------------- |
| `NONE`     | No evolution guarantee is promised.                   |
| `BACKWARD` | New readers accept data from the preceding schema.    |
| `FORWARD`  | Existing readers accept data from the new schema.     |
| `FULL`     | Both backward and forward compatibility are required. |

### `FieldType`

| Literal    | Practical meaning                                      |
| ---------- | ------------------------------------------------------ |
| `STRING`   | Textual character sequence.                            |
| `INTEGER`  | Whole numeric value.                                   |
| `NUMBER`   | Numeric value that may be fractional.                  |
| `BOOLEAN`  | Logical true or false.                                 |
| `DATE`     | Calendar date without time.                            |
| `TIME`     | Time of day without date.                              |
| `DATETIME` | Combined date and time.                                |
| `OBJECT`   | Nested structure described by a schema.                |
| `ARRAY`    | Ordered collection of item values.                     |
| `ENUM`     | Value selected from declared literals.                 |
| `BINARY`   | Opaque binary content.                                 |
| `MONEY`    | Monetary amount with currency and precision semantics. |
| `UUID`     | Universally unique identifier.                         |
| `EMAIL`    | Email-address-shaped text.                             |
| `URI`      | Resource identifier text.                              |
| `MAP`      | Keyed collection with a modeled value type.            |
| `UNION`    | Value conforming to one of several alternatives.       |

### `EnvironmentClass`

| Literal   | Practical meaning                           |
| --------- | ------------------------------------------- |
| `LOCAL`   | Developer-local construction environment.   |
| `DEV`     | Shared development integration environment. |
| `TEST`    | Environment dedicated to verification.      |
| `STAGING` | Production-like pre-release environment.    |
| `PROD`    | Live environment for real workloads.        |
| `SANDBOX` | Isolated experimental environment.          |

### `RuntimeLanguage`

| Literal      | Practical meaning                                  |
| ------------ | -------------------------------------------------- |
| `TYPESCRIPT` | Use TypeScript for implementation.                 |
| `JAVASCRIPT` | Use JavaScript for implementation.                 |
| `PYTHON`     | Use Python for implementation.                     |
| `JAVA`       | Use Java for implementation.                       |
| `CSHARP`     | Use C# for implementation.                         |
| `GO`         | Use Go for implementation.                         |
| `RUST`       | Use Rust for implementation.                       |
| `CUSTOM`     | Use a language specified outside this enumeration. |

### `PackageManager`

| Literal  | Practical meaning               |
| -------- | ------------------------------- |
| `NPM`    | Use npm.                        |
| `PNPM`   | Use pnpm.                       |
| `YARN`   | Use Yarn.                       |
| `PIP`    | Use pip.                        |
| `POETRY` | Use Poetry.                     |
| `MAVEN`  | Use Maven.                      |
| `GRADLE` | Use Gradle.                     |
| `DOTNET` | Use the .NET project toolchain. |
| `GO_MOD` | Use Go modules.                 |
| `CARGO`  | Use Cargo.                      |
| `NONE`   | No package manager is expected. |

### `Decision`

| Literal        | Practical meaning                           |
| -------------- | ------------------------------------------- |
| `UNDECIDED`    | The decision remains open.                  |
| `REQUIRED`     | The capability is required.                 |
| `NOT_REQUIRED` | The capability was considered and excluded. |

### `DataAccessMode`

| Literal      | Practical meaning                        |
| ------------ | ---------------------------------------- |
| `READ`       | Retrieve existing data.                  |
| `WRITE`      | Create or modify data.                   |
| `READ_WRITE` | Read and modify through the same access. |
| `APPEND`     | Add immutable or sequential data.        |
| `DELETE`     | Remove data.                             |

### `AccessPatternKind`

| Literal            | Practical meaning                      |
| ------------------ | -------------------------------------- |
| `GET_BY_ID`        | Retrieve one item by stable identity.  |
| `QUERY_BY_KEY`     | Retrieve items through a lookup key.   |
| `SCAN_FILTERED`    | Inspect a broad set and filter it.     |
| `SEARCH_TEXT`      | Find items through text search.        |
| `AGGREGATE_REPORT` | Produce grouped or summarized results. |
| `WRITE_COMMAND`    | Persist a command-driven state change. |
| `APPEND_EVENT`     | Append an immutable event.             |

### `SupportLevel`

| Literal                      | Practical meaning                                        |
| ---------------------------- | -------------------------------------------------------- |
| `SUPPORTED_FIRST_CLASS`      | A dedicated target mapping handles the concept.          |
| `SUPPORTED_WITH_ASSUMPTIONS` | Mapping works under documented assumptions.              |
| `REQUIRES_MANUAL_DECISION`   | A modeler must supply a platform decision.               |
| `METADATA_ONLY`              | The concept survives as review or trace metadata only.   |
| `UNSUPPORTED`                | The current target mapping cannot implement the concept. |
