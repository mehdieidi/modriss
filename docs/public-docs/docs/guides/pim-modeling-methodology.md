<!-- Process documentation is maintained by hand. -->

# Serverless architecture with PIM

The Platform-Independent Model (PIM) describes the serverless architecture without choosing a cloud provider. The solution architect develops it from the accepted CIM, requirements, constraints and transformation contract.

## Create and review the architecture draft

Run the CIM-to-PIM transformation from an identified CIM version. Review the report, mappings, assumptions, trace links and decisions left open by the transformation.

When a PIM already exists, compare the new output with earlier architecture work. Resolve conflicts before accepting the result. The transformation draft provides a starting point for architecture decisions.

## Define services, contracts and data

Set service boundaries and assign model elements to the services responsible for them. Record dependencies and the reasons for architecture decisions.

Define request, response and event structures. Describe persistent data, access patterns and indexes. Review ownership and contracts with the teams that produce and consume the data.

## Design functions and interfaces

Define functions, their inputs and outputs, triggers and execution conditions. Connect access interfaces and routes to the functions they invoke. Specify the contracts and errors that consumers need to handle.

## Design integration and workflows

Describe channels, event routes, communication flows and workflows. Decide how timeouts, retries, duplicate requests and compensation should behave. These decisions must preserve the intended business outcome when a dependency fails or a message arrives again.

Review integrations with the teams responsible for external systems. Keep unresolved dependencies and assumptions visible.

## Complete security, policies and configuration

Specify identities, permissions, architecture policies, external systems and environments. The security engineer, quality engineer and service owner review the decisions relevant to their responsibilities.

Examine failure behavior and cost assumptions along with the normal execution path. Record decisions that affect the next cloud-specific design.

## Review the architecture at G3

The solution architect reviews contracts, data, security, failure behavior and whether the architecture can map to the chosen cloud platform, with the process reviewer and relevant specialists.

The outputs are the completed PIM, transformation report, architecture decisions, trace links and supporting threat, failure and cost analysis. G3 accepts the architecture for the selected scope or returns it for rework.

A missing business concept or rule returns to CIM. An accepted PIM continues to [PSM cloud design](psm-modeling-methodology.md).
