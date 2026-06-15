# Modeling Workflow

## Create a Project

Projects are the access-control and organization boundary for models and artifacts. A project has an
owner and may include editors and viewers.

- Owners can update and delete the project and manage membership.
- Editors can update project-owned models and artifacts.
- Viewers have read-only access.

## Create or Import a Model

Create a model from a level workspace or import JSON/XMI. The current upload limit defaults to
20 MiB. XMI imports are staged while they are converted and validated.

Models are versioned using a numeric revision. Every update or patch must include the expected
revision. A stale revision returns HTTP `409` instead of overwriting newer work.

## Edit Visually

The editor exposes:

- A palette scoped by level and viewpoint
- An infinite graph canvas
- Legal relationship handles derived from metamodel references
- Typed attribute and containment inspectors
- Level-specific workbenches
- Focus, filtering, semantic views, layout, undo, and save controls

Openable containers provide focused semantic subgraphs. The layout API can calculate node positions
and routed edges; persisted view layout supports stable revisits.

## Validate Frequently

Validation should happen before each transformation and after meaningful manual refinement.
Validation results identify severity, constraint, issue class, guidance, and affected element.

## Generate the Next Level

- From CIM, generate PIM.
- From PIM, generate AWS PSM.
- From PSM, generate the AWS project artifact.

Transformations use the saved source model and expected revision. Review generated models before
continuing because transformations intentionally leave space for developer decisions and
refinement.

## Export and Share

Models can be exported as JSON or XMI. Projects and artifacts can be downloaded as ZIP files.
Generated artifact files can also be opened and edited in the browser artifact explorer.
