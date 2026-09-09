# PIM enumerations

Enumerations in this module are the PIM vocabulary for architecture style, execution, contracts, data access, and support decisions.

Source: `mde/metamodels/pim/pim-types.emf`.

## Enumeration values

These are closed vocabularies: use exactly one of the listed literals, preserving capitalization. The generator and EVL rules may assign different consequences to different literals.

### `ArchitectureStyle`

| Literal                            | Practical meaning                                                   |
| ---------------------------------- | ------------------------------------------------------------------- |
| `EVENT_DRIVEN_SERVERLESS`          | `event_driven_serverless` as a controlled modeling choice.          |
| `API_FIRST_SERVERLESS`             | `api_first_serverless` as a controlled modeling choice.             |
| `WORKFLOW_ORCHESTRATED_SERVERLESS` | `workflow_orchestrated_serverless` as a controlled modeling choice. |
| `HYBRID_SERVERLESS`                | `hybrid_serverless` as a controlled modeling choice.                |

### `BoundaryType`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `CAPABILITY_BASED`      | `capability_based` as a controlled modeling choice.      |
| `BOUNDED_CONTEXT_BASED` | `bounded_context_based` as a controlled modeling choice. |
| `PROCESS_BASED`         | `process_based` as a controlled modeling choice.         |
| `DATA_OWNERSHIP_BASED`  | `data_ownership_based` as a controlled modeling choice.  |
| `TEAM_OWNERSHIP_BASED`  | `team_ownership_based` as a controlled modeling choice.  |

### `DeploymentUnitType`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `APPLICATION`           | `application` as a controlled modeling choice.           |
| `SERVICE`               | `service` as a controlled modeling choice.               |
| `SHARED_INFRASTRUCTURE` | `shared_infrastructure` as a controlled modeling choice. |
| `LIBRARY`               | `library` as a controlled modeling choice.               |
| `EDGE_INTERFACE`        | `edge_interface` as a controlled modeling choice.        |
| `OBSERVABILITY`         | `observability` as a controlled modeling choice.         |

### `OwnershipKind`

| Literal       | Practical meaning                              |
| ------------- | ---------------------------------------------- |
| `OWNS`        | `owns` as a controlled modeling choice.        |
| `USES_SHARED` | `uses_shared` as a controlled modeling choice. |
| `DEPENDS_ON`  | `depends_on` as a controlled modeling choice.  |
| `EXPOSES`     | `exposes` as a controlled modeling choice.     |

### `FunctionKind`

| Literal              | Practical meaning                                     |
| -------------------- | ----------------------------------------------------- |
| `COMMAND_HANDLER`    | `command_handler` as a controlled modeling choice.    |
| `QUERY_HANDLER`      | `query_handler` as a controlled modeling choice.      |
| `EVENT_HANDLER`      | `event_handler` as a controlled modeling choice.      |
| `POLICY_HANDLER`     | `policy_handler` as a controlled modeling choice.     |
| `ORCHESTRATION_TASK` | `orchestration_task` as a controlled modeling choice. |
| `EXTERNAL_ADAPTER`   | `external_adapter` as a controlled modeling choice.   |
| `SCHEDULED_TASK`     | `scheduled_task` as a controlled modeling choice.     |
| `STREAM_PROCESSOR`   | `stream_processor` as a controlled modeling choice.   |
| `MAINTENANCE_TASK`   | `maintenance_task` as a controlled modeling choice.   |

### `ComputeProfile`

| Literal             | Practical meaning                                    |
| ------------------- | ---------------------------------------------------- |
| `IO_BOUND`          | `io_bound` as a controlled modeling choice.          |
| `CPU_BOUND`         | `cpu_bound` as a controlled modeling choice.         |
| `MEMORY_INTENSIVE`  | `memory_intensive` as a controlled modeling choice.  |
| `LATENCY_SENSITIVE` | `latency_sensitive` as a controlled modeling choice. |
| `BATCH_ORIENTED`    | `batch_oriented` as a controlled modeling choice.    |
| `LIGHTWEIGHT`       | `lightweight` as a controlled modeling choice.       |
| `UNKNOWN`           | `unknown` as a controlled modeling choice.           |

### `ExecutionModel`

| Literal            | Practical meaning                                   |
| ------------------ | --------------------------------------------------- |
| `REQUEST_RESPONSE` | `request_response` as a controlled modeling choice. |
| `ASYNC_EVENT`      | `async_event` as a controlled modeling choice.      |
| `BATCH_EVENT`      | `batch_event` as a controlled modeling choice.      |
| `STREAM_EVENT`     | `stream_event` as a controlled modeling choice.     |
| `SCHEDULED`        | `scheduled` as a controlled modeling choice.        |
| `WORKFLOW_TASK`    | `workflow_task` as a controlled modeling choice.    |

### `ApiStyle`

| Literal                  | Practical meaning                                         |
| ------------------------ | --------------------------------------------------------- |
| `RESOURCE_ORIENTED_HTTP` | `resource_oriented_http` as a controlled modeling choice. |
| `RPC_HTTP`               | `rpc_http` as a controlled modeling choice.               |
| `GRAPHQL`                | `graphql` as a controlled modeling choice.                |
| `EVENT_API`              | `event_api` as a controlled modeling choice.              |
| `WEBHOOK`                | `webhook` as a controlled modeling choice.                |

### `HttpMethod`

| Literal   | Practical meaning                          |
| --------- | ------------------------------------------ |
| `GET`     | `get` as a controlled modeling choice.     |
| `POST`    | `post` as a controlled modeling choice.    |
| `PUT`     | `put` as a controlled modeling choice.     |
| `PATCH`   | `patch` as a controlled modeling choice.   |
| `DELETE`  | `delete` as a controlled modeling choice.  |
| `OPTIONS` | `options` as a controlled modeling choice. |
| `HEAD`    | `head` as a controlled modeling choice.    |
| `ANY`     | `any` as a controlled modeling choice.     |

### `InvocationMode`

| Literal        | Practical meaning                               |
| -------------- | ----------------------------------------------- |
| `SYNCHRONOUS`  | `synchronous` as a controlled modeling choice.  |
| `ASYNCHRONOUS` | `asynchronous` as a controlled modeling choice. |
| `POLLED`       | `polled` as a controlled modeling choice.       |
| `SCHEDULED`    | `scheduled` as a controlled modeling choice.    |
| `ORCHESTRATED` | `orchestrated` as a controlled modeling choice. |

### `ChannelKind`

| Literal     | Practical meaning                            |
| ----------- | -------------------------------------------- |
| `QUEUE`     | `queue` as a controlled modeling choice.     |
| `TOPIC`     | `topic` as a controlled modeling choice.     |
| `EVENT_BUS` | `event_bus` as a controlled modeling choice. |
| `STREAM`    | `stream` as a controlled modeling choice.    |
| `WEBHOOK`   | `webhook` as a controlled modeling choice.   |

### `DeliverySemantics`

| Literal                             | Practical meaning                                                    |
| ----------------------------------- | -------------------------------------------------------------------- |
| `AT_MOST_ONCE`                      | `at_most_once` as a controlled modeling choice.                      |
| `AT_LEAST_ONCE`                     | `at_least_once` as a controlled modeling choice.                     |
| `EFFECTIVELY_ONCE_WITH_IDEMPOTENCY` | `effectively_once_with_idempotency` as a controlled modeling choice. |
| `EXACTLY_ONCE_REQUIRED`             | `exactly_once_required` as a controlled modeling choice.             |

### `OrderingRequirement`

| Literal   | Practical meaning                          |
| --------- | ------------------------------------------ |
| `NONE`    | `none` as a controlled modeling choice.    |
| `PER_KEY` | `per_key` as a controlled modeling choice. |
| `GLOBAL`  | `global` as a controlled modeling choice.  |

### `StoreKind`

| Literal       | Practical meaning                              |
| ------------- | ---------------------------------------------- |
| `KEY_VALUE`   | `key_value` as a controlled modeling choice.   |
| `DOCUMENT`    | `document` as a controlled modeling choice.    |
| `RELATIONAL`  | `relational` as a controlled modeling choice.  |
| `GRAPH`       | `graph` as a controlled modeling choice.       |
| `SEARCH`      | `search` as a controlled modeling choice.      |
| `TIME_SERIES` | `time_series` as a controlled modeling choice. |
| `CACHE`       | `cache` as a controlled modeling choice.       |
| `OBJECT`      | `object` as a controlled modeling choice.      |

### `ConsistencyNeed`

| Literal            | Practical meaning                                   |
| ------------------ | --------------------------------------------------- |
| `EVENTUAL`         | `eventual` as a controlled modeling choice.         |
| `STRONG`           | `strong` as a controlled modeling choice.           |
| `TRANSACTIONAL`    | `transactional` as a controlled modeling choice.    |
| `READ_YOUR_WRITES` | `read_your_writes` as a controlled modeling choice. |

### `WorkflowKind`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `ORCHESTRATION`         | `orchestration` as a controlled modeling choice.         |
| `LONG_RUNNING_PROCESS`  | `long_running_process` as a controlled modeling choice.  |
| `SAGA`                  | `saga` as a controlled modeling choice.                  |
| `HUMAN_APPROVAL`        | `human_approval` as a controlled modeling choice.        |
| `BATCH_COORDINATION`    | `batch_coordination` as a controlled modeling choice.    |
| `EVENT_ROUTING_PROCESS` | `event_routing_process` as a controlled modeling choice. |

### `IdentityKind`

| Literal                      | Practical meaning                                             |
| ---------------------------- | ------------------------------------------------------------- |
| `USER_DIRECTORY`             | `user_directory` as a controlled modeling choice.             |
| `FEDERATED_IDENTITY`         | `federated_identity` as a controlled modeling choice.         |
| `MACHINE_CLIENT`             | `machine_client` as a controlled modeling choice.             |
| `API_KEY_CLIENT`             | `api_key_client` as a controlled modeling choice.             |
| `SERVICE_PRINCIPAL`          | `service_principal` as a controlled modeling choice.          |
| `EXTERNAL_IDENTITY_PROVIDER` | `external_identity_provider` as a controlled modeling choice. |

### `PrincipalKind`

| Literal           | Practical meaning                                  |
| ----------------- | -------------------------------------------------- |
| `HUMAN_USER`      | `human_user` as a controlled modeling choice.      |
| `GROUP`           | `group` as a controlled modeling choice.           |
| `ROLE`            | `role` as a controlled modeling choice.            |
| `SERVICE`         | `service` as a controlled modeling choice.         |
| `EXTERNAL_SYSTEM` | `external_system` as a controlled modeling choice. |
| `ANONYMOUS`       | `anonymous` as a controlled modeling choice.       |

### `PermissionEffect`

| Literal | Practical meaning                        |
| ------- | ---------------------------------------- |
| `ALLOW` | `allow` as a controlled modeling choice. |
| `DENY`  | `deny` as a controlled modeling choice.  |

### `PermissionActionKind`

| Literal       | Practical meaning                              |
| ------------- | ---------------------------------------------- |
| `INVOKE`      | `invoke` as a controlled modeling choice.      |
| `READ`        | `read` as a controlled modeling choice.        |
| `WRITE`       | `write` as a controlled modeling choice.       |
| `DELETE`      | `delete` as a controlled modeling choice.      |
| `LIST`        | `list` as a controlled modeling choice.        |
| `PUBLISH`     | `publish` as a controlled modeling choice.     |
| `SUBSCRIBE`   | `subscribe` as a controlled modeling choice.   |
| `CONSUME`     | `consume` as a controlled modeling choice.     |
| `ENCRYPT`     | `encrypt` as a controlled modeling choice.     |
| `DECRYPT`     | `decrypt` as a controlled modeling choice.     |
| `READ_SECRET` | `read_secret` as a controlled modeling choice. |
| `MANAGE`      | `manage` as a controlled modeling choice.      |

### `LeastPrivilegeStatus`

| Literal                     | Practical meaning                                            |
| --------------------------- | ------------------------------------------------------------ |
| `UNREVIEWED`                | `unreviewed` as a controlled modeling choice.                |
| `GENERATED_BROAD`           | `generated_broad` as a controlled modeling choice.           |
| `REVIEW_REQUIRED`           | `review_required` as a controlled modeling choice.           |
| `CONFIRMED_LEAST_PRIVILEGE` | `confirmed_least_privilege` as a controlled modeling choice. |

### `SecretKind`

| Literal             | Practical meaning                                    |
| ------------------- | ---------------------------------------------------- |
| `API_KEY`           | `api_key` as a controlled modeling choice.           |
| `PASSWORD`          | `password` as a controlled modeling choice.          |
| `TOKEN`             | `token` as a controlled modeling choice.             |
| `CERTIFICATE`       | `certificate` as a controlled modeling choice.       |
| `CONNECTION_STRING` | `connection_string` as a controlled modeling choice. |
| `PRIVATE_KEY`       | `private_key` as a controlled modeling choice.       |
| `OTHER`             | `other` as a controlled modeling choice.             |

### `ConfigScope`

| Literal           | Practical meaning                                  |
| ----------------- | -------------------------------------------------- |
| `APPLICATION`     | `application` as a controlled modeling choice.     |
| `SERVICE`         | `service` as a controlled modeling choice.         |
| `FUNCTION`        | `function` as a controlled modeling choice.        |
| `ENVIRONMENT`     | `environment` as a controlled modeling choice.     |
| `DEPLOYMENT_UNIT` | `deployment_unit` as a controlled modeling choice. |

### `SchemaKind`

| Literal         | Practical meaning                                |
| --------------- | ------------------------------------------------ |
| `REQUEST`       | `request` as a controlled modeling choice.       |
| `RESPONSE`      | `response` as a controlled modeling choice.      |
| `ERROR`         | `error` as a controlled modeling choice.         |
| `EVENT`         | `event` as a controlled modeling choice.         |
| `MESSAGE`       | `message` as a controlled modeling choice.       |
| `ENTITY`        | `entity` as a controlled modeling choice.        |
| `VALUE_OBJECT`  | `value_object` as a controlled modeling choice.  |
| `READ_MODEL`    | `read_model` as a controlled modeling choice.    |
| `CONFIGURATION` | `configuration` as a controlled modeling choice. |

### `SchemaCompatibility`

| Literal    | Practical meaning                           |
| ---------- | ------------------------------------------- |
| `NONE`     | `none` as a controlled modeling choice.     |
| `BACKWARD` | `backward` as a controlled modeling choice. |
| `FORWARD`  | `forward` as a controlled modeling choice.  |
| `FULL`     | `full` as a controlled modeling choice.     |

### `FieldType`

| Literal    | Practical meaning                           |
| ---------- | ------------------------------------------- |
| `STRING`   | `string` as a controlled modeling choice.   |
| `INTEGER`  | `integer` as a controlled modeling choice.  |
| `NUMBER`   | `number` as a controlled modeling choice.   |
| `BOOLEAN`  | `boolean` as a controlled modeling choice.  |
| `DATE`     | `date` as a controlled modeling choice.     |
| `TIME`     | `time` as a controlled modeling choice.     |
| `DATETIME` | `datetime` as a controlled modeling choice. |
| `OBJECT`   | `object` as a controlled modeling choice.   |
| `ARRAY`    | `array` as a controlled modeling choice.    |
| `ENUM`     | `enum` as a controlled modeling choice.     |
| `BINARY`   | `binary` as a controlled modeling choice.   |
| `MONEY`    | `money` as a controlled modeling choice.    |
| `UUID`     | `uuid` as a controlled modeling choice.     |
| `EMAIL`    | `email` as a controlled modeling choice.    |
| `URI`      | `uri` as a controlled modeling choice.      |
| `MAP`      | `map` as a controlled modeling choice.      |
| `UNION`    | `union` as a controlled modeling choice.    |

### `EnvironmentClass`

| Literal   | Practical meaning                          |
| --------- | ------------------------------------------ |
| `LOCAL`   | `local` as a controlled modeling choice.   |
| `DEV`     | `dev` as a controlled modeling choice.     |
| `TEST`    | `test` as a controlled modeling choice.    |
| `STAGING` | `staging` as a controlled modeling choice. |
| `PROD`    | `prod` as a controlled modeling choice.    |
| `SANDBOX` | `sandbox` as a controlled modeling choice. |

### `RuntimeLanguage`

| Literal      | Practical meaning                             |
| ------------ | --------------------------------------------- |
| `TYPESCRIPT` | `typescript` as a controlled modeling choice. |
| `JAVASCRIPT` | `javascript` as a controlled modeling choice. |
| `PYTHON`     | `python` as a controlled modeling choice.     |
| `JAVA`       | `java` as a controlled modeling choice.       |
| `CSHARP`     | `csharp` as a controlled modeling choice.     |
| `GO`         | `go` as a controlled modeling choice.         |
| `RUST`       | `rust` as a controlled modeling choice.       |
| `CUSTOM`     | `custom` as a controlled modeling choice.     |

### `PackageManager`

| Literal  | Practical meaning                         |
| -------- | ----------------------------------------- |
| `NPM`    | `npm` as a controlled modeling choice.    |
| `PNPM`   | `pnpm` as a controlled modeling choice.   |
| `YARN`   | `yarn` as a controlled modeling choice.   |
| `PIP`    | `pip` as a controlled modeling choice.    |
| `POETRY` | `poetry` as a controlled modeling choice. |
| `MAVEN`  | `maven` as a controlled modeling choice.  |
| `GRADLE` | `gradle` as a controlled modeling choice. |
| `DOTNET` | `dotnet` as a controlled modeling choice. |
| `GO_MOD` | `go_mod` as a controlled modeling choice. |
| `CARGO`  | `cargo` as a controlled modeling choice.  |
| `NONE`   | `none` as a controlled modeling choice.   |

### `Decision`

| Literal        | Practical meaning                               |
| -------------- | ----------------------------------------------- |
| `UNDECIDED`    | `undecided` as a controlled modeling choice.    |
| `REQUIRED`     | `required` as a controlled modeling choice.     |
| `NOT_REQUIRED` | `not_required` as a controlled modeling choice. |

### `DataAccessMode`

| Literal      | Practical meaning                             |
| ------------ | --------------------------------------------- |
| `READ`       | `read` as a controlled modeling choice.       |
| `WRITE`      | `write` as a controlled modeling choice.      |
| `READ_WRITE` | `read_write` as a controlled modeling choice. |
| `APPEND`     | `append` as a controlled modeling choice.     |
| `DELETE`     | `delete` as a controlled modeling choice.     |

### `AccessPatternKind`

| Literal            | Practical meaning                                   |
| ------------------ | --------------------------------------------------- |
| `GET_BY_ID`        | `get_by_id` as a controlled modeling choice.        |
| `QUERY_BY_KEY`     | `query_by_key` as a controlled modeling choice.     |
| `SCAN_FILTERED`    | `scan_filtered` as a controlled modeling choice.    |
| `SEARCH_TEXT`      | `search_text` as a controlled modeling choice.      |
| `AGGREGATE_REPORT` | `aggregate_report` as a controlled modeling choice. |
| `WRITE_COMMAND`    | `write_command` as a controlled modeling choice.    |
| `APPEND_EVENT`     | `append_event` as a controlled modeling choice.     |

### `SupportLevel`

| Literal                      | Practical meaning                                             |
| ---------------------------- | ------------------------------------------------------------- |
| `SUPPORTED_FIRST_CLASS`      | `supported_first_class` as a controlled modeling choice.      |
| `SUPPORTED_WITH_ASSUMPTIONS` | `supported_with_assumptions` as a controlled modeling choice. |
| `REQUIRES_MANUAL_DECISION`   | `requires_manual_decision` as a controlled modeling choice.   |
| `METADATA_ONLY`              | `metadata_only` as a controlled modeling choice.              |
| `UNSUPPORTED`                | `unsupported` as a controlled modeling choice.                |
