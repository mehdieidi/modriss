# How the process maps to SPEM

The MODRISS process documentation follows the central SPEM separation between **method content** and **process structure**. This distinction prevents reusable teaching from being confused with one occurrence of work in a lifecycle.

| Public documentation section | SPEM element                        | Meaning in MODRISS                                                   |
| ---------------------------- | ----------------------------------- | -------------------------------------------------------------------- |
| Tasks                        | TaskDefinition                      | Reusable instructions, criteria, and evidence expectations           |
| Roles                        | RoleDefinition                      | Reusable responsibility and competence                               |
| Work products                | WorkProductDefinition               | Reusable description of an input, output, model, record, or artifact |
| Guidance                     | Guidance                            | Supporting advice for performing or tailoring work                   |
| Phases and activities        | Phase and Activity                  | The lifecycle context that organizes work                            |
| Task occurrences             | TaskUse                             | Use of a TaskDefinition within an Activity                           |
| Role participation           | RoleUse and ProcessPerformer        | Use of a role in an Activity and its connection to a TaskUse         |
| Input and output bindings    | WorkProductUse and ProcessParameter | Use of a work product and its direction for a TaskUse                |
| Flow                         | WorkSequence                        | An explicit predecessor-successor relationship and its condition     |
| Gates                        | Milestone                           | A significant evidence-based decision point                          |

MODRISS uses labeled extensions for execution state, governance, metamodel coverage, iteration, change routing, and evidence-based gate decisions. These additions support the executable platform. They do not claim to be native SPEM metaclasses.

## Reading an item page

Start with **Phases and activities** to locate the current work. Open **Tasks** for the procedure and completion conditions. Use **Roles** to assign accountability and collaboration. Consult **Work products** to understand the evidence entering and leaving the task. Apply the relevant **Guidance**, then use **Gates and milestones** when an authority decision is required.

## References

These sources explain the standards and practices on which the process structure is based. They are foundations for tailoring and professional judgement, rather than substitutes for project evidence.

- [OMG Software & Systems Process Engineering Meta-Model (SPEM) 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026, software life cycle processes](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023, system life cycle processes](https://www.iso.org/standard/81702.html)
- [SWEBOK Guide, version 4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
- [FinOps Framework](https://www.finops.org/framework/)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [Brinkkemper, Method engineering](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Asadi, Esfahani, and Ramsin, Process patterns for MDA-based software development](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
