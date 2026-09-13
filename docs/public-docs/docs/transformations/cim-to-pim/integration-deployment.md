# CIM → PIM: Integration Deployment

This module performs the connective work that individual source-to-target rules cannot complete alone. It builds request/response and event flows, connects topics and external systems, derives access and protection policies, creates operational defaults, attaches service membership, and closes readiness evidence. It is the place where the PIM model becomes an architecture rather than a set of isolated generated objects.

Source module: `mde/transformations/cim-to-pim/integration-deployment.etl`.

## Reading this page

A transformation rule determines whether a source element contributes to the target model and how it is mapped. Use the guard to understand routing and the target table to see the model-level result. The behavior section records important semantic side effects. Trace and manual-decision information identifies work for review and later phases.

---

## Supporting ETL operations

Supporting operations also shape the transformation. They derive defaults, create secondary resources, cache correspondences, and resolve relationships after the main rule runs.

| Operation                                | Role                                                                    | Source                                                           |
| ---------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `attachOwnedElements`                    | Adds or records attach owned elements.                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:9`    |
| `addOwnedOnce`                           | Adds or records owned once.                                             | `mde/transformations/cim-to-pim/integration-deployment.etl:52`   |
| `cacheOwnedKey`                          | Computes cache owned key.                                               | `mde/transformations/cim-to-pim/integration-deployment.etl:57`   |
| `createRequestResponseFlows`             | Creates request response flows.                                         | `mde/transformations/cim-to-pim/integration-deployment.etl:63`   |
| `createEventChannelsAndFlows`            | Creates event channels and flows.                                       | `mde/transformations/cim-to-pim/integration-deployment.etl:86`   |
| `createTopicSubscriptions`               | Creates topic subscriptions.                                            | `mde/transformations/cim-to-pim/integration-deployment.etl:271`  |
| `createPolicyRoutingRules`               | Creates policy routing rules.                                           | `mde/transformations/cim-to-pim/integration-deployment.etl:310`  |
| `createExternalIntegrationFlows`         | Creates external integration flows.                                     | `mde/transformations/cim-to-pim/integration-deployment.etl:336`  |
| `createDefaultRetentionPolicy`           | Creates a default retention policy.                                     | `mde/transformations/cim-to-pim/integration-deployment.etl:365`  |
| `createDefaultBackupPolicy`              | Creates a default backup policy.                                        | `mde/transformations/cim-to-pim/integration-deployment.etl:387`  |
| `ensureStoreOperationalPolicies`         | Ensures store operational policies.                                     | `mde/transformations/cim-to-pim/integration-deployment.etl:410`  |
| `ensureIndexCandidatesForAccessPatterns` | Ensures index candidates for access patterns.                           | `mde/transformations/cim-to-pim/integration-deployment.etl:428`  |
| `ensureFunctionContractFields`           | Ensures function contract fields.                                       | `mde/transformations/cim-to-pim/integration-deployment.etl:456`  |
| `ensureApiContracts`                     | Ensures api contracts.                                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:471`  |
| `ensureChannelConnectivityIntent`        | Ensures channel connectivity intent.                                    | `mde/transformations/cim-to-pim/integration-deployment.etl:497`  |
| `ensureFlowOperationalPolicies`          | Ensures flow operational policies.                                      | `mde/transformations/cim-to-pim/integration-deployment.etl:523`  |
| `ensureReadinessFindingAffectedElements` | Ensures readiness finding affected elements.                            | `mde/transformations/cim-to-pim/integration-deployment.etl:543`  |
| `firstOwnedFunction`                     | Computes first owned function.                                          | `mde/transformations/cim-to-pim/integration-deployment.etl:550`  |
| `createCapabilityDependencyArtifacts`    | Creates capability dependency artifacts.                                | `mde/transformations/cim-to-pim/integration-deployment.etl:558`  |
| `containsProviderSpecificTerm`           | Returns whether the receiver contains provider specific term.           | `mde/transformations/cim-to-pim/integration-deployment.etl:678`  |
| `containsProviderSpecificTermIn`         | Returns whether the receiver contains provider specific term in.        | `mde/transformations/cim-to-pim/integration-deployment.etl:683`  |
| `createDataAccesses`                     | Creates data accesses.                                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:688`  |
| `schemaContainsItem`                     | Computes schema contains item.                                          | `mde/transformations/cim-to-pim/integration-deployment.etl:801`  |
| `addIndexed`                             | Adds or records indexed.                                                | `mde/transformations/cim-to-pim/integration-deployment.etl:806`  |
| `indexedValues`                          | Computes indexed values.                                                | `mde/transformations/cim-to-pim/integration-deployment.etl:821`  |
| `attachDataProtectionPolicies`           | Adds or records attach data protection policies.                        | `mde/transformations/cim-to-pim/integration-deployment.etl:830`  |
| `attachDataProtectionPolicyToItem`       | Adds or records attach data protection policy to item.                  | `mde/transformations/cim-to-pim/integration-deployment.etl:883`  |
| `addEventTypesToChannel`                 | Adds or records event types to channel.                                 | `mde/transformations/cim-to-pim/integration-deployment.etl:902`  |
| `copySeedEventTypesToChannel`            | Computes copy seed event types to channel.                              | `mde/transformations/cim-to-pim/integration-deployment.etl:911`  |
| `ensureDeadLetterQueue`                  | Ensures dead letter queue.                                              | `mde/transformations/cim-to-pim/integration-deployment.etl:937`  |
| `ensureEventTypeForChannel`              | Ensures event type for channel.                                         | `mde/transformations/cim-to-pim/integration-deployment.etl:974`  |
| `ensureApiAuthentication`                | Ensures api authentication.                                             | `mde/transformations/cim-to-pim/integration-deployment.etl:1010` |
| `ensureGeneratedOperationalPolicies`     | Ensures generated operational policies.                                 | `mde/transformations/cim-to-pim/integration-deployment.etl:1034` |
| `createHotspotManualDecision`            | Creates hotspot manual decision.                                        | `mde/transformations/cim-to-pim/integration-deployment.etl:1141` |
| `resolveGeneratedActionReferences`       | Restores references whose generated target is produced by a later rule. | `mde/transformations/cim-to-pim/integration-deployment.etl:1162` |
| `addServiceMembership`                   | Adds or records service membership.                                     | `mde/transformations/cim-to-pim/integration-deployment.etl:1293` |
| `generateServiceMemberships`             | Computes generate service memberships.                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:1313` |

---

## `Risk2ReadinessFinding`

**Source:** `r` to `CIMTRANSFORM!Risk`  
**Target:** `f` to `KERNEL!ReadinessFinding`  
**Source location:** `mde/transformations/cim-to-pim/integration-deployment.etl:1091`

### Why this rule exists

A CIM risk becomes a PIM readiness finding with severity, affected elements, owner/evidence expectations, and remediation. This does not magically resolve the risk; it makes the unresolved decision visible at the transformation boundary.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `f` (`KERNEL!ReadinessFinding`): Generated readiness finding (f).

### Important behavior encoded in the rule

The rule directly assigns: `f.id`, `f.name`, `f.findingType`, `f.severity`, `f.ruleId`, `f.message`, `f.recommendation`, `f.blocking`.
Trace identifiers emitted here: `TR-160`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the risk is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/integration-deployment.etl:1091`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `Assumption2ReadinessFinding`

**Source:** `a` to `CIMTRANSFORM!Assumption`  
**Target:** `f` to `KERNEL!ReadinessFinding`  
**Source location:** `mde/transformations/cim-to-pim/integration-deployment.etl:1116`

### Why this rule exists

Assumptions become readiness findings because transformations often rely on them without being able to prove them. Recording the statement, validation approach, and acceptance decision prevents an implicit premise from silently hardening into infrastructure.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `f` (`KERNEL!ReadinessFinding`): Generated readiness finding (f).

### Important behavior encoded in the rule

The rule directly assigns: `f.id`, `f.name`, `f.findingType`, `f.severity`, `f.ruleId`, `f.message`, `f.recommendation`, `f.blocking`.
Trace identifiers emitted here: `TR-160`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the assumption is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/integration-deployment.etl:1116`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---
