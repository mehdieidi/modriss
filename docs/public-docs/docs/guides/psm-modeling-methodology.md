<!-- Process documentation is maintained by hand. -->

# Cloud design with PSM

The Platform-Specific Model (PSM) describes the resources and configuration needed on the selected cloud platform. The cloud platform engineer develops it from the accepted PIM and its requirements, constraints and architecture decisions.

MODRISS includes an AWS modeling and generation path. Another provider needs its own metamodel, transformations and generator. The supported outputs depend on that provider package.

## Create and review the cloud design draft

Transform an identified PIM version using the provider's mapping package. Review the resources, relationships, assumptions, trace links and open decisions in the result.

For an existing PSM, compare the new draft with previous cloud design work and resolve conflicts. Keep the source, target and rule versions with the transformation report.

## Establish deployment and security settings

Define accounts, environments, naming, deployment units and shared configuration. Review parameters, permissions, keys and references to secrets with the security engineer.

The model should make clear how resources are grouped for deployment and which settings vary by environment.

## Complete networking, identity, data and messaging

Specify network resources and access, user and service identities, data stores, queues and notifications. Check that their relationships implement the architecture's contracts and data ownership.

Review data protection and recovery policies alongside storage configuration. Revisit the architecture if a provider restriction changes the design assumptions.

## Complete compute, interfaces, events and workflows

Connect executable resources to their triggers, messages, events and interfaces. Complete workflow configuration and check the relationships between resources.

Configure logs, metrics and alerts according to operational needs. The service owner helps review whether the proposed monitoring and recovery support is sufficient.

## Review generation readiness at G4

The cloud platform engineer reviews resource relationships, quotas, security, cost and remaining decisions. The result includes the completed PSM, mapping decisions, transformation report, trace links and readiness record.

G4 accepts a specific PSM version for code and infrastructure generation. It does not authorize a production release. A limitation that requires an architecture change returns to PIM; affected downstream work is then updated and reviewed.

After acceptance, continue with [generation, implementation and release](../process-reference/artifact/activities.md).
