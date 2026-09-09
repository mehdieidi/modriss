# CIM DSML

The Computation-Independent Model captures business intent, domain language, information, behavior, process, and governance before implementation decisions.

## How to use this reference

Start with the root page, then follow the module that owns the class you are modeling. Each class section includes declared attributes, accepted values or examples, and relationships. Attributes inherited from the shared kernel are documented once and apply to every subtype.

## Module map

| Module                   | Use it for                                                                                                                                                          | Reference                                                                 |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `cim-root.emf`           | The root is the container for business intent and the traceability/readiness objects that make a CIM increment reviewable.                                          | [CIM model root](cim-root.md)                                             |
| `cim-organization.emf`   | These concepts establish who wants the system, what outcomes matter, what the organization can do, and where language or ownership boundaries lie.                  | [Organization, requirements, and bounded contexts](cim-organization.md)   |
| `cim-domain-data.emf`    | Use this module to describe business concepts and information before choosing persistence or implementation technology.                                             | [Domain concepts and information](cim-domain-data.md)                     |
| `cim-behavior.emf`       | This module gives the domain an event-storming and CQRS vocabulary: state-changing commands, reads, events, errors, and conditions.                                 | [Commands, queries, events, and business errors](cim-behavior.md)         |
| `cim-process-policy.emf` | These classes express end-to-end business processes, branching decisions, policies, waits, human work, and exception handling.                                      | [Processes, policies, and decisions](cim-process-policy.md)               |
| `cim-governance.emf`     | Governance elements turn quality, privacy, security, and regulatory expectations into model facts that can constrain later design.                                  | [Quality, security, privacy, and compliance](cim-governance.md)           |
| `cim-transformation.emf` | Transformation metadata records uncertainty and decisions at the business boundary so generated PIM elements remain explainable.                                    | [Transformation assumptions and readiness signals](cim-transformation.md) |
| `cim-types.emf`          | Enumerations in this module are the controlled vocabulary for CIM attributes. Prefer these values over free-form strings wherever an attribute is typed by an enum. | [CIM enumerations](cim-types.md)                                          |

## Model-level guidance

- Treat the Emfatic declarations as the abstract-syntax authority. EVL adds semantic constraints; it does not introduce attributes that are absent from the metamodel.
- Use containment (`val`) for objects owned by the containing element and references (`ref`) for shared or cross-cutting concepts.
- Keep provider-neutral intent in CIM/PIM. Put AWS names, ARNs, SAM/CloudFormation properties, and service-specific operational decisions in AWS PSM.
- Preserve traceability and rationale when refining or transforming a model. They are part of the engineering record, not merely editor decoration.

## Additional resources

- [Domain-Driven Design](https://domainlanguage.com/ddd/)
- [EventStorming](https://www.eventstorming.com/)
- [CQRS pattern](https://martinfowler.com/bliki/CQRS.html)
- [Eclipse Epsilon EVL](https://eclipse.dev/epsilon/doc/evl/)
