<!-- Process documentation is maintained by hand. -->

# Domain modeling with CIM

The Computation-Independent Model (CIM) describes the business problem before the team chooses implementation technology. The business modeler develops it with the domain expert and requirements engineer.

The work starts from the increment scope and acceptance criteria, product charter, scenarios, domain terms and existing rules. Later increments also use the previous CIM version.

## Establish the scope and context

Connect the increment's business goals to acceptance measures. Create the model root for the first increment and review it as the product changes.

Identify participants, roles, external systems and capabilities. Record capability dependencies and agree on a shared glossary so the team uses domain terms consistently.

## Explore structure and behavior

Describe domain concepts, information and relationships together with the behavior that uses them. Identify operations that change state, queries and domain events.

Structure and behavior need to be reviewed together. A new rule may change a relationship, and a scenario may reveal missing information. Return to these descriptions as the team's understanding improves.

## Define boundaries, processes and rules

Group concepts that must remain consistent within aggregate boundaries. Describe business processes, decisions and policies. Use bounded contexts where different parts of the domain need separate meanings or ownership.

These boundaries should follow the domain knowledge available for the increment. The domain expert checks that the modeler's interpretation matches the intended business behavior.

## Complete requirements and transformation decisions

The requirements engineer checks that the model covers the agreed requirements and acceptance criteria. Complete governance constraints and trace links from goals and requirements to model elements.

Record the transformation contract: the accepted input scope, expected mapping and output, and decisions that require human review. Review open findings and run explicit model validation as part of the user's modeling workflow.

## Review the domain at G2

The product owner and domain expert decide whether the CIM correctly describes the concepts, rules and needs of the selected scope. The outputs include the reviewed CIM, requirements and constraints, transformation contract, trace links and review record.

If the domain is incomplete, revise it before relying on the affected parts in the architecture. Once accepted, the CIM is the input to [PIM architecture work](pim-modeling-methodology.md).

Structural conformance checks whether a model follows its metamodel. Semantic validation checks modeled rules through an explicit user action. The modeling assistant's apply, repair and commit paths use structural conformance only; semantic validation remains a separate activity.
