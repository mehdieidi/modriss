# CIM → PIM: Behavior Contracts

Behavior refinement is where a command, query, event, or error acquires an executable PIM boundary. Commands and queries become functions and, when appropriate, API routes; events acquire versioned event types and schemas; business errors become reusable error schemas. The rules also carry authorization, idempotency, correlation, error, and access-pattern intent rather than reducing business behavior to names.

Source module: `mde/transformations/cim-to-pim/behavior-contracts.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation      | Role                    | Source                                                     |
| -------------- | ----------------------- | ---------------------------------------------------------- |
| `commandPath`  | Computes command path.  | `mde/transformations/cim-to-pim/behavior-contracts.etl:9`  |
| `queryPath`    | Computes query path.    | `mde/transformations/cim-to-pim/behavior-contracts.etl:36` |
| `eventSubject` | Computes event subject. | `mde/transformations/cim-to-pim/behavior-contracts.etl:48` |

---

## `BusinessError2ErrorSchema`

**Source:** `e` to `CIMBEHAVIOR!BusinessError`  
**Target:** `s` to `CONTRACTS!Schema`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:57`

### Why this rule exists

Business errors become a stable PIM schema with code, human message, and recoverability. Those fields are deliberately copied into a contract-level object because API mappings, retries, tests, and generated clients need the distinction between business rejection and infrastructure failure.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`CONTRACTS!Schema`): Generated schema (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.schemaKind`, `s.semanticVersion`, `s.compatibility`, `s.additionalPropertiesAllowed`.
Trace identifiers emitted here: `TR-130`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the business error is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:57`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `BusinessEvent2EventType`

**Source:** `e` to `CIMBEHAVIOR!BusinessEvent`  
**Target:** `et` to `CONTRACTS!EventType`, `schema` to `CONTRACTS!Schema`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:76`

### Why this rule exists

An event becomes both a versioned event type and its payload schema. The rule carries past-tense business meaning, subject/order keys, visibility, audit/replay intent, and privacy classification so downstream consumers receive a fact with an explicit compatibility boundary.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `et` (`CONTRACTS!EventType`): Generated event type (et).
- `schema` (`CONTRACTS!Schema`): Generated schema (schema).

### Important behavior encoded in the rule

The rule directly assigns: `schema.id`, `schema.name`, `schema.schemaKind`, `schema.semanticVersion`, `schema.compatibility`, `schema.additionalPropertiesAllowed`, `schema.generatedFromCimInformation`, `et.id`, `et.name`, `et.semanticName`, `et.version`, `et.sourceDomain`, `et.subjectExpression`, `et.orderingKey`, `et.externalEvent`, `et.auditEvent` ….
Trace identifiers emitted here: `TR-090`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the business event is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:76`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Command2Function`

**Source:** `cmd` to `CIMBEHAVIOR!Command`  
**Target:** `fn` to `COMPUTE!Function`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:114`

### Why this rule exists

A command is refined into a command-handler function with a request/response contract, emitted outcomes, errors, authorization context, and idempotency policy. The rule turns an intended business state change into an executable unit without prematurely choosing Lambda or another provider.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `fn` (`COMPUTE!Function`): Generated function (fn).
- Secondary objects created in the rule body: `CONTRACTS!FunctionContract`.

### Important behavior encoded in the rule

The rule directly assigns: `fn.id`, `fn.name`, `fn.functionKind`, `fn.responsibility`, `fn.handlerResponsibility`, `fn.sourceNameSuggestion`, `fn.publicEntryPoint`, `fn.writesState`, `fn.readsState`, `fn.publishesEvents`, `fn.requiresNetworkAccess`, `fn.requiresFileSystem`, `fn.requiresLargeTemporaryStorage`, `fn.requiresIdempotency`, `fn.businessOperationRef`, `fn.computeProfile` ….
Trace identifiers emitted here: `TR-070`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the command is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:114`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `UserInitiatedCommand2ApiRoute`

**Source:** `cmd` to `CIMBEHAVIOR!Command`  
**Target:** `route` to `API!ApiRoute`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:178`

### Why this rule exists

Only commands that need an immediate or user-facing entry point become API routes. This rule derives method/path, auth, status code, schemas, triggers, and error mappings, while creating a manual decision when authorization intent is still too vague to trust.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : cmd.userInitiated or cmd.interactionExpectation.asString() = "IMMEDIATE_RESPONSE_EXPECTED"
```

### What it creates

- `route` (`API!ApiRoute`): Generated api route (route).
- Secondary objects created in the rule body: `SECURITY!AuthorizationPolicy`, `COMPUTE!Trigger`, `API!ErrorMapping`.

### Important behavior encoded in the rule

The rule directly assigns: `route.id`, `route.name`, `route.method`, `route.pathTemplate`, `route.operationId`, `route.publicRoute`, `route.authRequired`, `route.expectedSuccessStatus`, `route.descriptionForConsumers`, `route.requestValidationRequired`, `route.responseValidationRequired`, `route.requestSchema`, `route.responseSchema`, `route.functionIntegration`, `authz.id`, `authz.name` ….
Trace identifiers emitted here: `TR-070`, `TR-130`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-070`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-070` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the command actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:178`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Query2Function`

**Source:** `q` to `CIMBEHAVIOR!Query`  
**Target:** `fn` to `COMPUTE!Function`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:270`

### Why this rule exists

A query becomes a read-oriented function with no state mutation or event publication by default. Its contract reflects input/output validation and personal-data authorization, and its compute profile distinguishes ordinary reads from search, reporting, or analytics work.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `fn` (`COMPUTE!Function`): Generated function (fn).
- Secondary objects created in the rule body: `CONTRACTS!FunctionContract`.

### Important behavior encoded in the rule

The rule directly assigns: `fn.id`, `fn.name`, `fn.functionKind`, `fn.responsibility`, `fn.handlerResponsibility`, `fn.sourceNameSuggestion`, `fn.publicEntryPoint`, `fn.writesState`, `fn.readsState`, `fn.publishesEvents`, `fn.requiresIdempotency`, `fn.executionModel`, `fn.computeProfile`, `fn.stateless`, `contract.id`, `contract.name` ….
Trace identifiers emitted here: `TR-080`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the query is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:270`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Query2ApiRoute`

**Source:** `q` to `CIMBEHAVIOR!Query`  
**Target:** `route` to `API!ApiRoute`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:312`

### Why this rule exists

Queries are exposed as GET routes so the business read model has a stable consumer boundary. The rule derives lookup versus collection paths, pagination, authorization, schemas, and triggers, preserving the difference between reading sensitive data and issuing a command.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `route` (`API!ApiRoute`): Generated api route (route).
- Secondary objects created in the rule body: `SECURITY!AuthorizationPolicy`, `COMPUTE!Trigger`.

### Important behavior encoded in the rule

The rule directly assigns: `route.id`, `route.name`, `route.method`, `route.pathTemplate`, `route.operationId`, `route.publicRoute`, `route.authRequired`, `route.descriptionForConsumers`, `route.expectedSuccessStatus`, `route.paginationStyle`, `route.requestValidationRequired`, `route.responseValidationRequired`, `route.requestSchema`, `route.responseSchema`, `route.functionIntegration`, `authz.id` ….
Trace identifiers emitted here: `TR-080`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-080`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-080` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the query actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:312`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Query2AccessPattern`

**Source:** `q` to `CIMBEHAVIOR!Query`  
**Target:** `ap` to `DATA!AccessPattern`  
**Source location:** `mde/transformations/cim-to-pim/behavior-contracts.etl:381`

### Why this rule exists

A query that reads domain data becomes a named PIM access pattern. It records keys, filters, projection, cardinality, frequency, and consistency needs. The information a store design needs but a query name alone cannot provide. High-cardinality or read-model cases can also seed a dedicated read store.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : q.reads.notEmpty()
```

### What it creates

- `ap` (`DATA!AccessPattern`): Generated access pattern (ap).
- Secondary objects created in the rule body: `DATA!DataStore`, `DATA!DataModel`.

### Important behavior encoded in the rule

The rule directly assigns: `ap.id`, `ap.name`, `ap.patternName`, `ap.queryBy`, `ap.sortBy`, `ap.filterBy`, `ap.projection`, `ap.highCardinality`, `ap.highFrequency`, `ap.stronglyConsistentReadRequired`, `ap.transactionalWriteRequired`, `ap.expectedItemsReturned`, `readStore.id`, `readStore.name`, `readStore.persistent`, `readStore.encrypted` ….
Trace identifiers emitted here: `TR-080`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-080`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-080` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the query actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/behavior-contracts.etl:381`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
