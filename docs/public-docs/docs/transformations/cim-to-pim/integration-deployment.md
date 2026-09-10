# CIM → PIM: Integration Deployment

This module performs the connective work that individual source-to-target rules cannot complete alone. It builds request/response and event flows, connects topics and external systems, derives access and protection policies, creates operational defaults, attaches service membership, and closes readiness evidence. It is the place where the PIM model becomes an architecture rather than a set of isolated generated objects.

Source module: `mde/transformations/cim-to-pim/integration-deployment.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                                | Role                                                                    | Source                                                           |
| ---------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `attachOwnedElements`                    | Adds or records attach owned elements.                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:9`    |
| `addOwnedOnce`                           | Adds or records add owned once.                                         | `mde/transformations/cim-to-pim/integration-deployment.etl:52`   |
| `cacheOwnedKey`                          | Computes cache owned key.                                               | `mde/transformations/cim-to-pim/integration-deployment.etl:57`   |
| `createRequestResponseFlows`             | Creates create request response flows.                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:63`   |
| `createEventChannelsAndFlows`            | Creates create event channels and flows.                                | `mde/transformations/cim-to-pim/integration-deployment.etl:86`   |
| `createTopicSubscriptions`               | Creates create topic subscriptions.                                     | `mde/transformations/cim-to-pim/integration-deployment.etl:271`  |
| `createPolicyRoutingRules`               | Creates create policy routing rules.                                    | `mde/transformations/cim-to-pim/integration-deployment.etl:310`  |
| `createExternalIntegrationFlows`         | Creates create external integration flows.                              | `mde/transformations/cim-to-pim/integration-deployment.etl:336`  |
| `createDefaultRetentionPolicy`           | Creates create default retention policy.                                | `mde/transformations/cim-to-pim/integration-deployment.etl:365`  |
| `createDefaultBackupPolicy`              | Creates create default backup policy.                                   | `mde/transformations/cim-to-pim/integration-deployment.etl:387`  |
| `ensureStoreOperationalPolicies`         | Creates ensure store operational policies.                              | `mde/transformations/cim-to-pim/integration-deployment.etl:410`  |
| `ensureIndexCandidatesForAccessPatterns` | Creates ensure index candidates for access patterns.                    | `mde/transformations/cim-to-pim/integration-deployment.etl:428`  |
| `ensureFunctionContractFields`           | Creates ensure function contract fields.                                | `mde/transformations/cim-to-pim/integration-deployment.etl:456`  |
| `ensureApiContracts`                     | Creates ensure api contracts.                                           | `mde/transformations/cim-to-pim/integration-deployment.etl:471`  |
| `ensureChannelConnectivityIntent`        | Creates ensure channel connectivity intent.                             | `mde/transformations/cim-to-pim/integration-deployment.etl:497`  |
| `ensureFlowOperationalPolicies`          | Creates ensure flow operational policies.                               | `mde/transformations/cim-to-pim/integration-deployment.etl:523`  |
| `ensureReadinessFindingAffectedElements` | Creates ensure readiness finding affected elements.                     | `mde/transformations/cim-to-pim/integration-deployment.etl:543`  |
| `firstOwnedFunction`                     | Computes first owned function.                                          | `mde/transformations/cim-to-pim/integration-deployment.etl:550`  |
| `createCapabilityDependencyArtifacts`    | Creates create capability dependency artifacts.                         | `mde/transformations/cim-to-pim/integration-deployment.etl:558`  |
| `containsProviderSpecificTerm`           | Returns whether the receiver contains provider specific term.           | `mde/transformations/cim-to-pim/integration-deployment.etl:678`  |
| `containsProviderSpecificTermIn`         | Returns whether the receiver contains provider specific term in.        | `mde/transformations/cim-to-pim/integration-deployment.etl:683`  |
| `createDataAccesses`                     | Creates create data accesses.                                           | `mde/transformations/cim-to-pim/integration-deployment.etl:688`  |
| `schemaContainsItem`                     | Computes schema contains item.                                          | `mde/transformations/cim-to-pim/integration-deployment.etl:801`  |
| `addIndexed`                             | Adds or records add indexed.                                            | `mde/transformations/cim-to-pim/integration-deployment.etl:806`  |
| `indexedValues`                          | Computes indexed values.                                                | `mde/transformations/cim-to-pim/integration-deployment.etl:821`  |
| `attachDataProtectionPolicies`           | Adds or records attach data protection policies.                        | `mde/transformations/cim-to-pim/integration-deployment.etl:830`  |
| `attachDataProtectionPolicyToItem`       | Adds or records attach data protection policy to item.                  | `mde/transformations/cim-to-pim/integration-deployment.etl:883`  |
| `addEventTypesToChannel`                 | Adds or records add event types to channel.                             | `mde/transformations/cim-to-pim/integration-deployment.etl:902`  |
| `copySeedEventTypesToChannel`            | Computes copy seed event types to channel.                              | `mde/transformations/cim-to-pim/integration-deployment.etl:911`  |
| `ensureDeadLetterQueue`                  | Creates ensure dead letter queue.                                       | `mde/transformations/cim-to-pim/integration-deployment.etl:937`  |
| `ensureEventTypeForChannel`              | Creates ensure event type for channel.                                  | `mde/transformations/cim-to-pim/integration-deployment.etl:974`  |
| `ensureApiAuthentication`                | Creates ensure api authentication.                                      | `mde/transformations/cim-to-pim/integration-deployment.etl:1010` |
| `ensureGeneratedOperationalPolicies`     | Creates ensure generated operational policies.                          | `mde/transformations/cim-to-pim/integration-deployment.etl:1034` |
| `createHotspotManualDecision`            | Creates create hotspot manual decision.                                 | `mde/transformations/cim-to-pim/integration-deployment.etl:1141` |
| `resolveGeneratedActionReferences`       | Restores references whose generated target is produced by a later rule. | `mde/transformations/cim-to-pim/integration-deployment.etl:1162` |
| `addServiceMembership`                   | Adds or records add service membership.                                 | `mde/transformations/cim-to-pim/integration-deployment.etl:1293` |
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

Read the complete ETL rule at `mde/transformations/cim-to-pim/integration-deployment.etl:1091`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

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

Read the complete ETL rule at `mde/transformations/cim-to-pim/integration-deployment.etl:1116`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
