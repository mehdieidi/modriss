# Modeling Workflow

This guide describes the usual workbench flow. For the development-process activities and review points behind each modeling step, see the [full-lifecycle process](full-lifecycle-method.md) and the [capability-increment process](end-to-end-modeling-methodology.md).

## Create a project

A project groups its models and artifacts and provides the access-control boundary. The project owner can update and delete it and manage membership. Editors can update project-owned models and artifacts. Viewers have read-only access.

## Create or import a model

Create a model from a CIM, PIM, or AWS PSM workspace, or import JSON or XMI. The default upload limit is 20 MiB. XMI imports are staged while the platform converts them to its stored representation and checks the result.

Models use numeric revisions. An update or patch includes the revision it expects to change. If a newer revision has already been saved, MODRISS returns HTTP `409` so the newer work is not silently replaced.

## Edit in the workbench

The editor presents a level-specific palette and viewpoint on a graph canvas. Relationship handles follow the references allowed by the metamodel. Inspectors edit typed attributes and contained elements. Focus, filtering, semantic views, layout, undo, and save controls help with larger models. Openable containers show a focused part of the model while keeping its stored structure intact.

## Validate and review

Structural validation checks whether a model conforms to its Ecore metamodel. Explicit user/model validation workflows can run EVL semantic rules and report constraints or critiques with the affected element and guidance.

The chatbot assistant has a separate boundary. Its apply, repair, and commit paths validate generated model output through structural Ecore/EMF conformance with `ModelService.validateStructural(...)`. They do not invoke EVL.

## Transform to the next level

The pipeline moves from CIM to PIM, from PIM to AWS PSM, and from AWS PSM to a generated project. Run transformations against a saved source revision and review the resulting model before continuing. Inspect trace links, reports, assumptions, manual decisions, and readiness findings. Generated models are starting points for refinement.

See the [Capability-Increment Process](end-to-end-modeling-methodology.md) for the full increment sequence and [Iterative Model Transformations](../architecture/iterative-model-transformations.md) for synchronization during upstream changes.

## Export and share

Models can be exported as JSON or XMI. Projects and artifacts can be downloaded as ZIP files. The browser artifact explorer can open generated files for review and editing.
