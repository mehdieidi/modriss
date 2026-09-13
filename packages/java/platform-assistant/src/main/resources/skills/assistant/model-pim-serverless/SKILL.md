---
name: model-pim-serverless
description: Design and evolve provider-neutral serverless architectures in the PIM DSML. Use for PIM requests involving APIs, functions, events, queues, topics, workflows, schedules, data stores, object storage, identity, policies, contracts, configuration, deployment, or observability.
---

# Model PIM Serverless

Model requested behavior end to end, not as disconnected infrastructure labels.

For each feature, trace an execution path through the applicable concepts:

`entry or trigger -> compute or workflow -> integration -> data/external effect -> outcome`

Use only the exact subset supported by retrieved contracts. Typical concrete PIM candidates include `ServerlessService`, `Api`, `ApiRoute`, `Function`, `Trigger`, `Workflow`, `Queue`, `Topic`, `EventBus`, `Schedule`, `DataStore`, `ObjectStore`, `ExternalEndpoint`, `ExternalAdapter`, `Schema`, `EventType`, and architecture policies. These names are discovery hints, not permission to create them without exact contracts.

- Reuse one service boundary for closely related features unless the user requests separate ownership or deployment.
- Represent synchronous calls, event publication, subscriptions, routing, and orchestration with their exact DSML flows/references; do not encode architecture only in descriptions.
- Record every intended non-containment edge with its exact Ecore feature and target in the conceptual relationship plan so generation cannot silently omit or substitute it. This applies equally to invocation, reads/writes, publication/subscription, integration, routing, policy attachment, traceability, and other retrieved PIM references.
- A workflow with more than one step must contain explicit `WorkflowTransition` instances (when exposed by the retrieved contracts), each with legal `source` and `target` step references. Build a continuous start-to-outcome path and connect every modeled branch; shared containment, step order, or visual proximity is not control flow.
- Give each `TaskStep` a legal behavioral target such as `invokesFunction`, `invokesAdapter`, or another exact target supported by its contract. A named but behaviorless task is not useful workflow evidence.
- Add schemas/contracts when payload shape is part of the request. Add resilience, idempotency, timeout, dead-letter, security, and observability concepts when demanded by requirements or needed to make the requested architecture explicit.
- Keep the PIM provider-neutral. Do not introduce AWS, Azure, GCP, vendor product names, regions, accounts, or provider-specific resources unless those are merely quoted user context and a provider-neutral PIM concept is used.
- Choose required contracts broadly enough to cover all features, then let exact containment closure reveal required owners and detail objects.
