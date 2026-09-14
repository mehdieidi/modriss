# MDE Change Impact and Synchronization Guide

## Purpose

This document is the repository-specific playbook for changing MODRISS without leaving the
metamodels, transformations, validation, generated artifacts, backend, frontend, persistence, AI,
samples, tests, and documentation out of sync.

The central rule is:

> Treat every language change as a contract change. Update every producer, consumer, persisted
> representation, visual representation, and verification fixture affected by that contract.

Not every change requires editing every layer. Use the impact classification and checklists below
to determine what is required.

## Repository Contract Map

| Concern                            | Primary source of truth                                                                              | Important consumers and derived assets                                                                                           |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Shared abstract syntax             | `mde/metamodels/shared/kernel.emf`                                                                   | `kernel.ecore`, all three combined Ecore files, EVL, ETL, EGX/EGL, UI metadata, JSON/XMI bridge, assistant contracts, samples    |
| CIM abstract syntax                | `mde/metamodels/cim/*.emf`                                                                           | `cim-combined.ecore`, CIM EVL, CIM-to-PIM ETL, CIM UI metadata, model import/export, assistant, samples/tests                    |
| PIM abstract syntax                | `mde/metamodels/pim/*.emf`                                                                           | `pim-combined.ecore`, PIM EVL, both ETL profiles, PIM UI metadata, model import/export, assistant, samples/tests                 |
| AWS PSM abstract syntax            | `mde/metamodels/psm/*.emf`                                                                           | `psm-combined.ecore`, PSM EVL, PIM-to-PSM ETL, EGX/EGL, PSM UI metadata, model import/export, samples/tests                      |
| Runtime metamodel                  | `mde/metamodels/{cim,pim,psm}/*-combined.ecore`                                                      | Java EMF loading, validation, transformation, generation, UI structural metadata, metamodel hash/version                         |
| Semantic validation                | `mde/validation/{cim,pim,psm}/`                                                                      | `ModelService`, EVL CLI, validation endpoint tests                                                                               |
| CIM-to-PIM semantics               | `mde/transformations/cim-to-pim/`                                                                    | ETL runner/CLI, `TransformationService`, generated PIM, tests/docs                                                               |
| PIM-to-AWS-PSM semantics           | `mde/transformations/pim-to-awspsm/`                                                                 | ETL runner/CLI, `TransformationService`, generated PSM, tests/docs                                                               |
| PSM-to-artifact semantics          | `mde/generation/awspsm-to-artifacts/`                                                                | M2T runner/CLI, artifact service, generated projects, tests/docs                                                                 |
| Editor structure and visual syntax | `packages/java/platform-modeling/src/main/resources/modeling/*-ui-metadata.json` plus combined Ecore | `ModelingConfigService`, `/api/modeling/config`, frontend canvas, palette, views, relationship presentation                      |
| JSON/XMI semantic bridge           | `packages/java/platform-modeling/.../XmiModelImportService.java`                                     | Model create/update/import/export, transformation handoff, graph reconstruction                                                  |
| Frontend modeling behavior         | `apps/frontend/js/` and `apps/frontend/css/`                                                         | Canvas, graph editor, workbenches, attribute editor, views, layout, methodology checks                                           |
| Persistence                        | PostgreSQL migrations and platform storage/application records                                       | Stored JSON/XMI, metamodel hash/version, jobs, synchronization baselines/sessions, assistant turns/events/checkpoints/provenance |
| AI modeling behavior               | `apps/backend/.../assistant/` and `packages/java/platform-assistant/...`                             | Ecore-derived contracts, agent tools, durable turns, checkpoints, source provenance                                              |
| Public API contract                | controllers plus `docs/api/openapi/openapi.yaml`                                                     | Frontend, external clients, API docs                                                                                             |
| Regression fixtures                | `mde/samples/*.xmi`, Java tests, case-study samples                                                  | All MDE pipeline regression tests                                                                                                |
| Architecture documentation         | `README.md`, `docs/internal/project-description.md`, `docs/`, especially `docs/diagrams/`            | Developers, thesis material, operations                                                                                          |

## How Runtime Metadata Is Built

The editor configuration is not solely hand-written JSON and is not solely generated from Ecore.
`ModelingConfigService` merges both:

- The combined Ecore files provide EClasses, inheritance, attributes, enum options, references,
  multiplicities, containment rules, and derived semantic-reference rules.
- `cim-ui-metadata.json`, `pim-ui-metadata.json`, and `psm-ui-metadata.json` provide visual and
  editor-owned metadata such as labels, icons, colors, categories, notation, relationship visual
  rules, views, root templates, and palette scoping.
- `/api/modeling/config` exposes the merged result.
- `apps/frontend/js/modeling-config-data.js` normalizes and queries that result.

Consequences:

- Adding an EClass or feature to Ecore can make it structurally visible to the UI automatically.
- It will receive generic visual defaults unless a matching JSON visual rule or explicit element
  override exists.
- A structurally available element is not necessarily usable in every view or specialized
  workbench.
- Visual-only changes usually do not require metamodel, ETL, EVL, database, or AI changes.

## Impact Classification

Classify the intended change before editing files.

### At-a-glance review matrix

`Required` means the layer must change. `Review` means inspect it and change it only when the
affected concept is encoded or consumed there.

| Change                                             | Java/backend            | JSON/UI metadata              | Frontend               | Docs/API        | PostgreSQL       | Metamodel/schema     | AI                      | MDE scripts/tests |
| -------------------------------------------------- | ----------------------- | ----------------------------- | ---------------------- | --------------- | ---------------- | -------------------- | ----------------------- | ----------------- |
| Label, icon, color, category, palette grouping     | Review                  | Required                      | Review                 | Review          | No               | No                   | No                      | UI tests          |
| View or relationship visual style                  | Review                  | Required                      | Review                 | Review          | No               | Review semantics     | No                      | UI/layout tests   |
| Add/change EClass, attribute, reference, enum      | Review                  | Review                        | Review                 | Review          | Usually no       | Required             | Review                  | Required review   |
| Rename/remove model concept                        | Required review         | Required review               | Required review        | Required review | Migration review | Required             | Required review         | Required review   |
| EVL rule change                                    | Review                  | Review minimum-valid defaults | Review issue UX        | Review          | No               | Review               | Required refresh/review | Required          |
| ETL rule change                                    | Review orchestration    | Review target UI              | Review target behavior | Review          | No               | Review source/target | Review                  | Required          |
| EGX/EGL generation change                          | Review artifact service | No                            | Review artifact UX     | Review          | Usually no       | Review PSM           | Review                  | Required          |
| Add/rename model level or API/model envelope field | Required                | Required                      | Required               | Required        | Required         | Required             | Required                | Required review   |

### A. Visual-only change

Examples: label, color, icon, category, notation shape, visible lines, edge style, palette grouping,
or view membership.

Usually change:

- `packages/java/platform-modeling/src/main/resources/modeling/*-ui-metadata.json`
- Frontend CSS/JS only when introducing a new rendering behavior or shape
- UI metadata and frontend tests
- Visual/editor documentation

Usually do not change:

- Emfatic/Ecore
- EVL/ETL/EGX/EGL
- PostgreSQL schema
- AI catalog

### B. Semantic rule change

Examples: a model combination becomes invalid, a readiness rule changes, or a transformation
mapping changes without changing the metamodel shape.

Change the relevant EVL, ETL, EOL, EGX, EGL, helpers, tests, samples, and explanatory docs.
The abstract syntax and database usually remain unchanged.

### C. Abstract syntax change

Examples: add/rename/remove an EClass, attribute, reference, enum, literal, inheritance relation,
root containment, multiplicity, or namespace.

This is the broadest common change. Review every layer in this document.

### D. Representation or platform-contract change

Examples: rename `diagram`, `graph`, `elements`, `relationships`, `eClass`, model levels, root
classes, API fields, stored record fields, or assistant patch operations.

These changes affect cross-layer protocols and may require Java, frontend, API docs, database
migrations, stored-data migration, and compatibility handling even when the DSML itself is
unchanged.

## Metamodel Change Impact

### Always required for an Emfatic metamodel change

1. Edit the authoritative `.emf` module under `mde/metamodels/`.
2. Regenerate the affected `.ecore` output.
3. Regenerate the affected level's `*-combined.ecore`.
4. Run structural loading and model import/export tests.
5. Update affected sample XMI.
6. Review EVL, ETL, EGX/EGL, UI metadata, AI behavior, docs, and existing stored models.

The runtime Java services load the combined Ecore files, not the `.emf` files. Editing Emfatic
without regenerating combined Ecore changes documentation/source but does not change runtime
behavior.

### Shared kernel changes

`kernel.emf` is imported by CIM, PIM, and PSM. After changing it:

- Regenerate `mde/metamodels/shared/kernel.ecore`.
- Regenerate all three combined Ecore files.
- Review all three validation profiles.
- Review both ETL profiles and PSM generation.
- Review all three UI metadata files.
- Update all three samples and cross-level regression tests.

### Add an EClass

Review and update:

- The owning `.emf` module.
- Root or parent containment, if users or transformations must be able to persist instances.
- The combined Ecore.
- UI metadata: visual rule/override, `creatable`, `supportOnly`, `containedOnly`, category,
  notation, and relevant view palettes.
- EVL constraints and helper operations.
- Incoming ETL rules that produce it and outgoing ETL/EGX logic that consumes it.
- Frontend specialized workbench/model utility code if the element needs more than generic canvas
  and attribute-panel behavior.
- AI starter/deterministic behavior if the assistant should create it without relying only on
  retrieved catalog context.
- Samples, tests, and diagrams.

An EClass that is not reachable through containment can be visible in metadata but cannot be
reliably serialized as part of the model.

### Add or change an attribute

Review and update:

- Type, multiplicity, default, and enum literals in Emfatic/Ecore.
- EVL checks that require, compare, or derive it.
- ETL assignments and EOL builders/mappings.
- EGL templates and generation helper operations.
- UI notation `lineFields`, labels, explicit element overrides, and specialized attribute editors.
- Root templates and AI starter models when the field is required.
- Assistant deterministic patches that set the old field or literal.
- Samples and tests.

The generic UI attribute metadata is derived from Ecore. A new ordinary attribute therefore does
not normally require Java DTO or PostgreSQL column changes.

### Add or change a reference or containment

Review and update:

- Reference target, multiplicity, containment, opposite, and derived/read-only properties.
- Root containment and serialization reachability.
- EVL ownership, endpoint, multiplicity, cycle, and consistency rules.
- ETL resolution/builders and trace creation.
- EGX/EGL traversal assumptions.
- UI relationship rules, relationship labels/visual rules, view membership, and shortcut
  connectors.
- `XmiModelImportService` graph reconstruction when the reference needs a special user-facing kind,
  must be hidden as support data, or represents a special relationship object.
- CIM/PIM workbench and model utility containment/reference maps.
- Samples and import/export round-trip tests.

### Rename or remove an EClass, feature, enum, or literal

Treat this as a breaking migration, not a local refactor.

Search the entire repository for the old identifier and update:

- Emfatic imports, types, inheritance, and references.
- EVL contexts, checks, guards, messages, and helpers.
- ETL rules, guards, assignments, builders, resolution, trace/readiness logic, and aliases.
- EGX guards and EGL/EOL property accesses.
- UI metadata element overrides, visual selectors, view palettes, relationship rules, root
  templates, and notation fields.
- `XmiModelImportService` hard-coded root names, support objects/references, relationship class
  kinds, feature-to-kind mappings, and PSM view mappings where relevant.
- Frontend workbenches, model utilities, methodology validation, view materialization, graph
  mapping, rendering, and filters.
- AI starter models, deterministic patches, prompt examples, and tests.
- XMI samples and persisted JSON/XMI migration logic.
- Docs and diagrams.

Existing stored records retain the old `model_json` and `source_xmi`. The application detects a
combined-Ecore hash mismatch and reports `NEEDS_MIGRATION`, but it does not automatically rewrite
old model content. A breaking rename/removal therefore needs an explicit migration strategy.

### Change a root EClass or model level

This is a platform-contract change. In addition to the full metamodel review, update:

- `ModelLevel` and every level switch if adding/renaming a level.
- `MdeRuntimePaths`.
- `XmiModelImportService.expectedRootEClass`.
- UI metadata `rootTemplate`.
- `modeling-config-data.js` root fallbacks and level lists.
- Controllers and route regexes.
- Transformation service operation routing.
- Database `CHECK` constraints through a new Flyway migration.
- OpenAPI and REST/SSE docs.
- Assistant thread/model-level storage and starter models.

## Validation Change Impact

Validation entry modules are:

- `mde/validation/cim/cim-semantic-validation.evl`
- `mde/validation/pim/pim-semantic-validation.evl`
- `mde/validation/psm/psm-semantic-validation.evl`

They import rules and EOL helpers beneath their level directories.

When changing validation logic:

1. Update the relevant EVL rule and shared/level EOL helper.
2. Ensure the entry EVL imports any new rule module.
3. Keep context model aliases compatible with `ModelService.validationModelName`,
   `ModelService.validationModelAliases`, and the EVL CLI profile commands.
4. Update transformations when generated target models must satisfy the changed rule.
5. Update root templates, optional assistant review examples, and samples when the new rule affects
   models expected to pass explicit EVL validation.
6. Update generation preconditions if PSM generation relies on the same invariant.
7. Add positive and negative validation tests.
8. Restart the backend if metamodel sources changed so Ecore-derived assistant contracts are fresh.
   EVL-only changes affect workbench validation and explicit model-validation outcomes, but do not
   gate assistant tool validation.

Use stable rule identifiers in messages. If a rule identifier or message meaning changes, update
tests, docs, frontend issue handling, and assistant expectations that refer to it.

## Transformation Change Impact

### CIM to PIM

Entry point: `mde/transformations/cim-to-pim/cim-to-pim.etl`

Main modules:

- `root-scaffolding.etl`
- `boundaries-security.etl`
- `domain-data.etl`
- `behavior-contracts.etl`
- `process-policy.etl`
- `integration-deployment.etl`
- `lib/*.eol`

When a CIM-to-PIM rule changes:

- Update the owning ETL module and shared builders/mapping/resolution/trace-readiness helpers.
- Update the entry module imports when adding a module.
- Confirm source and target aliases in `CimToPimDefaults` and CLI docs if packages/aliases changed.
- Ensure the generated PIM satisfies PIM structural and EVL constraints.
- Update traceability/readiness expectations.
- Update PIM-to-PSM if the shape or meaning of generated PIM changed.
- Update samples and `CimToPimEtlRegressionTest`.
- Update `TransformationServiceTest` and end-to-end documentation when observable behavior changes.

### PIM to AWS PSM

Entry point: `mde/transformations/pim-to-awspsm/pim-to-awspsm.etl`

Main modules:

- `root-stage-stack.etl`
- `compute-api.etl`
- `data-messaging-events.etl`
- `workflow-security-config.etl`
- `contracts-external-policy.etl`
- `lib/*.eol`

When a PIM-to-PSM rule changes:

- Update the owning ETL module and AWS builders/readiness/trace helpers.
- Confirm aliases in `PimToAwsPsmDefaults`; `KERNEL` intentionally belongs only to the target side.
- Ensure generated PSM satisfies PSM structural and EVL constraints.
- Update EGX/EGL when the generated PSM structure or semantics changed.
- Update PSM views and relationship visualization for newly generated relationships/resources.
- Update samples, `PimToAwsPsmEtlRegressionTest`, and transformation service tests.

### General transformation rule checklist

For any ETL/EOL logic change, ask:

- Did the source selector/guard change?
- Did target cardinality change?
- Did an output move to a different containment?
- Did naming or stable ID logic change?
- Did reference resolution change?
- Did trace/readiness generation change?
- Can downstream validation and generation still consume the output?
- Are target model imports and frontend graph reconstruction still correct?
- Do tests prove both transformed content and downstream usability?

## Code Generation Change Impact

Entry point: `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`

Supporting assets:

- `lib/*.eol` for naming, paths, values, IAM, CloudFormation/SAM, contracts, trace, validation, and
  protected regions.
- `templates/**/*.egl` for infrastructure, Lambda code, contracts, tests, docs, scripts, and CI.

When generation logic changes:

- Update EGX rules when generation selection, target path, or template coordination changes.
- Update EGL templates when emitted content changes.
- Update EOL helpers when naming, paths, value conversion, contracts, IAM, traceability, or
  protected-region behavior changes.
- Update PSM EVL when a newly required generator precondition should be validated earlier.
- Update PIM-to-PSM when generated PSM must now supply new information.
- Update artifact documentation and expected generated file lists.
- Add syntax/content assertions to `AwsPsmArtifactGenerationSyntaxTest` and
  `EpsilonEgxGeneratorTest`.
- Run generation from `mde/samples/psm.xmi` and inspect the deployable outputs as well as the EGX parse
  success.

Changes to generated artifact content do not require PostgreSQL migrations because artifact files
are stored generically by path/content. A migration is needed only if the artifact storage record
contract itself changes.

## UI Metadata and Concrete Visual Syntax

### Primary files

- `packages/java/platform-modeling/src/main/resources/modeling/cim-ui-metadata.json`
- `packages/java/platform-modeling/src/main/resources/modeling/pim-ui-metadata.json`
- `packages/java/platform-modeling/src/main/resources/modeling/psm-ui-metadata.json`

Important sections include:

- `elementVisualDefaults`
- `elementVisualRules`
- `elements`
- `relationshipKinds`
- `relationshipKindLabels`
- `relationshipVisualRules`
- `relationshipRules`
- `shortcutConnectorRules`
- `viewDefinitions`
- `rootTemplate`
- `universalSyntax`
- `kernelSyntax`
- `complexityManagement`

### Change elements, names, icons, colors, groupings, or palette

Update the level UI metadata:

- `label` or `displayName` for the shown name.
- `description` for palette/tooltips.
- `icon` for the CSS mask source.
- `color` for element accents.
- `category` for palette grouping.
- `creatable`, `supportOnly`, `containedOnly`, and `relationshipElement` for editor availability.
- `elementVisualRules` for package/type/supertype-based defaults.
- Explicit `elements` overrides for exceptions.
- `viewDefinitions[*].palette` to scope tools to views.

The frontend passes `icon` values to CSS mask URLs. For custom icons, add the SVG under
`apps/frontend/assets/icons/` and use a resolvable asset path such as
`/assets/icons/example.svg`. Existing non-path symbolic values fall back or fail to resolve in
parts of the frontend, so verify the rendered palette and node explicitly.

### Change relationship tools or appearance

Update:

- Ecore references or relationship EClasses if semantics change.
- `relationshipRules` for legal source/target/kind combinations.
- `relationshipKindLabels` for displayed names.
- `relationshipVisualRules` for line style, markers, classes, and variants.
- `shortcutConnectorRules` for convenient PSM connectors.
- `viewDefinitions` for visibility.
- `XmiModelImportService` feature/class-to-kind maps when imported XMI must reconstruct a special
  relationship kind.
- Frontend graph/editor code only when introducing behavior not expressible through metadata.

### Change views

Update `viewDefinitions` for:

- View identifier, label, viewpoint, and view type.
- Included element and relationship types.
- View-specific palette.
- Any configured filtering or complexity-management metadata.

Also review:

- `apps/frontend/js/graph-store.js`
- `apps/frontend/js/view-materializer.js`
- `apps/frontend/js/view-explorer.js`
- `apps/frontend/js/canvas.js`
- `apps/frontend/js/layout-engine.js`
- `StoredViewLayoutService` and layout tests when geometry or persisted-view assumptions change

### Introduce a new notation shape or rendering behavior

Metadata can select a notation shape, but JavaScript/CSS must know how to render any genuinely new
shape or interaction. Review:

- `apps/frontend/js/canvas.js`
- `apps/frontend/js/graph-editor/g6-mapper.js`
- `apps/frontend/js/graph-editor/g6-style.js`
- `apps/frontend/css/canvas.css`
- `apps/frontend/css/sidebar.css`
- `apps/frontend/css/variables.css`

### Specialized frontend behavior

The generic editor is metadata-driven, but specialized workbenches and utilities contain explicit
type/feature knowledge. Review these after structural or semantic UI changes:

- `apps/frontend/js/cim-workbench.js`
- `apps/frontend/js/cim-model-utils.js`
- `apps/frontend/js/pim-workbench.js`
- `apps/frontend/js/pim-model-utils.js`
- `apps/frontend/js/psm-workbench.js`
- `apps/frontend/js/attr-panel.js`
- `apps/frontend/js/methodology-validation.js`
- `apps/frontend/js/model-ops.js`
- `apps/frontend/js/model-patch.js`
- `apps/frontend/js/graph-store.js`

## Concrete Syntax of CIM, PIM, and PSM

In this repository, concrete syntax has two forms:

1. **Serialized concrete syntax:** EMF/XMI plus the platform JSON representation.
2. **Visual concrete syntax:** palette tools, node notation, relationship notation, views, and
   interaction behavior.

### To change serialized concrete syntax

Review:

- Emfatic/Ecore names, namespace URIs, containment, opposites, multiplicities, and defaults.
- `XmiModelImportService` JSON/XMI conversion and graph reconstruction.
- Existing XMI samples and stored XMI.
- Stored `model_json`, migration state, and explicit migration tooling/logic.
- API examples and assistant patch payloads.

### To change visual concrete syntax

Review:

- Level UI metadata JSON.
- `ModelingConfigService` only if the metadata schema or Ecore-to-UI derivation changes.
- Frontend modeling-config normalization if adding metadata fields.
- Canvas/G6/CSS if adding new notation behavior.
- View and layout services if the new notation changes visibility or geometry.
- Visual tests, docs, and screenshots/diagrams.

Changing visual concrete syntax alone should not change formal model semantics. Changing Ecore,
references, or validation changes the language, not merely its notation.

## Java and Backend Change Hotspots

Review these when a change cannot remain purely declarative:

| File/area                                                                                 | Why it may need changes                                                                              |
| ----------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `packages/java/platform-modeling/.../MdeRuntimePaths.java`                                | Fixed entry module and combined-Ecore paths                                                          |
| `packages/java/platform-modeling/.../FileMetamodelResolver.java`                          | Combined-Ecore loading, namespace version, hash caching                                              |
| `packages/java/platform-modeling/.../ModelingConfigService.java`                          | Ecore-to-editor metadata derivation and metadata schema                                              |
| `packages/java/platform-modeling/.../XmiModelImportService.java`                          | Root names, JSON/XMI mapping, graph relationships, support objects                                   |
| `packages/java/platform-model/.../ModelService.java`                                      | Validation aliases, required-feature validation, migration state, persistence normalization          |
| `packages/java/platform-transformation/.../TransformationService.java`                    | End-to-end operation orchestration and generated model import                                        |
| `packages/java/platform-transformation/.../TransformationSynchronizationCoordinator.java` | Base/Working/NewGenerated lifecycle, conflict sessions, stale checks, and atomic commit              |
| `packages/java/platform-transformation/.../ModelSynchronizationService.java`              | Standalone EMF Compare matching, policy filtering, merge, and conflict descriptions                  |
| `CimToPimDefaults.java`                                                                   | CIM/PIM paths and Epsilon aliases                                                                    |
| `PimToAwsPsmDefaults.java`                                                                | PIM/PSM paths and Epsilon aliases                                                                    |
| `AwsPsmToArtifactsDefaults.java`                                                          | PSM paths and Epsilon aliases                                                                        |
| `packages/java/platform-model/.../StoredViewLayoutService.java`                           | Persisted view and level-specific layout assumptions (lives in model to avoid modeling↔model cycle) |
| Backend controllers                                                                       | Routes and public API contract                                                                       |

Avoid changing runner internals merely because a rule or metamodel changed. Runner internals should
change only when execution behavior, diagnostics, model loading, timeouts, or the reusable Java API
must change.

## PostgreSQL and Stored-Model Impact

### When no database migration is needed

No Flyway migration is normally needed for:

- Adding/changing DSML EClasses, attributes, references, or rules.
- Changing UI metadata.
- Changing ETL, EVL, EGX, EGL, or generated artifact files.
- Changing synchronization payload implementation without changing the database table shape;
  the shared `model_synchronization_records` JSONB payload is intentionally typed at the service layer.

Models are stored generically as `models.model_json` (`jsonb`) and `models.source_xmi` (`bytea`).
Artifacts are stored generically by path and content.

### When a database migration is needed

Add a new forward-only migration under
`packages/java/platform-storage-postgres/src/main/resources/db/migration/` when changing:

- Table/column/index/constraint structure.
- Allowed model levels or job operations/status values enforced by SQL checks.
- Persisted application record fields.
- Assistant persistence/retrieval schema.
- Data that must be backfilled to satisfy a new application invariant.

Do not edit an already-applied shared migration. Add the next numbered migration and update
`PostgresPlatformStore`, record types, repository interfaces, integration tests, and
`docs/internal/operations/postgres-storage.md`.

### Existing model compatibility

The combined-Ecore SHA-256 is stored with each model. When it differs from the current metamodel,
the model is marked `NEEDS_MIGRATION`. This is detection, not transformation.

For breaking metamodel changes, define one of these policies:

- Backward-compatible loader/normalizer.
- One-time JSON/XMI migration command or service.
- Database backfill for JSON plus XMI regeneration.
- Explicit rejection with a documented manual migration path.

Verify both a fresh database and an existing database containing pre-change models.

## AI Change Impact

### Ecore-derived contracts

After metamodel or methodology changes:

- Regenerate Ecore before restart so both Emfatic and runtime structure are current.
- Restart or rebuild the backend so packaged `mde/` resources and Ecore-derived contracts are
  current.
- Verify assistant tools accept new classes/features and reject removed or renamed ones.

ETL, EOL, EGX, EGL, EVL, and UI metadata are not provider-facing retrieval documents. EVL changes
still matter for workbench validation and for assistant `validate_model` outcomes.

### Manually encoded AI behavior

Review these for structural or semantic changes:

- `AgentModelTools` for model read/mutation behavior.
- `AssistantMetamodelSchemaService`, `MetamodelContractGraph`, and `EcoreContractExtractor` for
  type/feature contract extraction.
- `AgentTurnLoop` and `AgentActionCodec` when the provider action protocol changes.
- `ModelWorkspace` when workspace mutation semantics change.
- Assistant prompts/examples/docs and assistant tests.

Always validate representative assistant turns against the current metamodel. Run EVL profiles
separately only when the test is explicitly about post-checkpoint semantic review.

## API, JSON, Schema, and Documentation Impact

### JSON model shape

The model body is dynamic JSON backed by Ecore and the JSON/XMI bridge; there is no separate
repository JSON Schema for CIM/PIM/PSM model instances. Structural model changes therefore usually
do not require a JSON Schema file edit.

Update API contracts when changing:

- Request/response envelope fields.
- Model level names or routes.
- Modeling config response shape.
- Validation, transformation, artifact, or assistant payloads.
- Durable assistant REST/SSE payloads.

Relevant docs:

- `docs/api/openapi/openapi.yaml`
- `docs/api/rest-api.md`
- `docs/public-docs/docs/reference/realtime-api.md`
- `docs/internal/ai/assistant.md`
- `docs/public-docs/docs/architecture/iterative-model-transformations.md`
- `docs/model-synchronization-scenario-catalog.md`

### Generated JSON Schema

JSON Schema artifacts generated from PSM contracts are controlled by
`mde/generation/awspsm-to-artifacts/templates/contracts/*.egl` and related EOL helpers. Change those
when the generated application contract changes.

### Architecture and thesis documentation

Update relevant files under `docs/diagrams/`, particularly:

- `17-mde-architecture.md`
- `18-mde-end-to-end-pipeline.md`
- `19-mde-validation-transformation-generation.md`
- `20-metamodel-relations.md`
- `21-frontend-architecture-and-flows.md`
- `22-major-function-flows.md`

Also update root/transformation READMEs when entry points, modules, aliases, outputs, or behavior
change.

## Step-by-Step: Change a Metamodel

1. Define the semantic intent and classify the change as additive, restrictive, rename/removal, or
   root/package-level.
2. Search the repository for every affected type, feature, enum, literal, namespace, and old name.
3. Edit the authoritative `.emf` module and its imports/root containments.
4. Build the metamodel CLI:

   ```powershell
   mvn -q -pl tools/mde-cli -am package
   ```

5. Regenerate the affected combined Ecore in place. Examples:

   ```powershell
   java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
     mde/metamodels/cim `
     --root mde/metamodels/cim/cim-root.emf `
     --output mde/metamodels/cim/cim-combined.ecore `
     --overwrite

   java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
     mde/metamodels/pim `
     --root mde/metamodels/pim/pim-root.emf `
     --output mde/metamodels/pim/pim-combined.ecore `
     --overwrite

   java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
     mde/metamodels/psm `
     --root mde/metamodels/psm/awspsm-root.emf `
     --output mde/metamodels/psm/psm-combined.ecore `
     --overwrite
   ```

6. If the shared kernel changed, regenerate `kernel.ecore` and all three combined Ecore files:

   ```powershell
   java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
     mde/metamodels/shared/kernel.emf `
     --output mde/metamodels/shared/kernel.ecore `
     --overwrite
   ```

7. Inspect the Ecore diff. Confirm namespaces, classifiers, inheritance, references, containment,
   multiplicities, opposites, defaults, and enum literals.
8. Update UI metadata and root templates so every new/changed type has intentional visual syntax
   and view/palette behavior.
9. Update the JSON/XMI bridge and specialized frontend behavior for renamed roots, special
   relationships, support objects, or custom containment behavior.
10. Update EVL and minimum-valid model assumptions.
11. Update incoming/outgoing ETL and PSM generation.
12. Update AI starter/deterministic behavior.
13. Update XMI samples and add import/export round-trip coverage.
14. Define and test compatibility/migration for existing stored models.
15. Update docs and run the verification ladder.

## Step-by-Step: Change CIM-to-PIM or PIM-to-PSM Transformation Logic

1. Define the source condition, target result, traceability, readiness, and downstream expectation.
2. Edit the owning ETL module and relevant `lib/*.eol` helper.
3. Add the module import to the entry ETL if a new module was created.
4. Update Java default aliases only if package/model aliases changed.
5. Update or add a representative source XMI fixture.
6. Run the relevant ETL regression test.
7. Validate the generated target with its structural and EVL profile.
8. Import the generated target through `ModelService` to verify JSON/XMI and graph reconstruction.
9. For CIM-to-PIM, run PIM-to-PSM against the new output.
10. For PIM-to-PSM, run PSM-to-artifacts against the new output.
11. Inspect traces, readiness findings, names/IDs, references, and target containments.
12. Update UI views, docs, and assistant behavior if the observable target shape changed.

## Step-by-Step: Change PSM-to-Artifact Code Generation

1. Define which PSM condition selects generation and which files/content must result.
2. Edit EGX coordination, EGL template, and EOL helper files as appropriate.
3. Add or change PSM EVL rules for generator preconditions.
4. Update PIM-to-PSM when new required generator inputs must be produced automatically.
5. Add a representative PSM sample or test fixture.
6. Run syntax tests and full generation.
7. Inspect generated file paths, content, trace reports, protected regions, and manual actions.
8. Run relevant generated-project validation/build/test tools when available.
9. Update artifact docs and expected-output tests.

## Step-by-Step: Change Validation

1. Decide whether the invariant is structural Ecore, mandatory EVL, optional EVL critique, or
   transformation/generation precondition.
2. Edit the relevant rule file and helper operations.
3. Import new rule files from the level entry EVL.
4. Add a failing fixture/test and a passing fixture/test.
5. Update transformations, root templates, samples, and assistant defaults so generated/default models
   meet the new rule.
6. Run EVL through both the reusable Java path and repository CLI profile.
7. Restart the backend when metamodel sources changed; verify assistant tools honor renamed types.
   Raw EVL rule text is not sent to the provider.
8. Update rule documentation and messages.

## Step-by-Step: Change Visual Syntax, Palette, or Views

1. Confirm the change is visual-only. If element/reference semantics change, follow the metamodel
   process too.
2. Edit the affected `*-ui-metadata.json`.
3. For icons, add an SVG asset and reference a resolvable `/assets/icons/...svg` path.
4. For a new notation shape or interaction, implement it in canvas/G6/CSS.
5. For a new view, update view definitions, palette scope, materialization, and layout behavior.
6. For a specialized CIM/PIM/PSM workflow, update the corresponding workbench/model utility.
7. Run `ModelingConfigServiceTest` and inspect `/api/modeling/config`.
8. Manually verify palette grouping, drag/drop, attribute editing, legal connectors, imported XMI,
   all affected views, save/reload, and auto-layout.
9. Update visual/editor documentation.

## Step-by-Step: Change AI Modeling Behavior

1. First change the formal source: metamodel and/or EVL.
2. Regenerate Ecore and restart the backend when metamodel or methodology sources changed.
3. Update assistant defaults, tool behavior, and prompts when they encode affected fields/types.
4. Update `AgentActionCodec`, `AgentModelTools`, `ModelWorkspace`, or metamodel contract extraction
   only if the action protocol, workspace semantics, or Ecore contract shape changed.
5. Validate representative assistant turns for CIM and PIM. PSM assistant sessions are currently
   out of scope; validate PSM transformations, explicit validation, and generation separately.
6. Test representative add, update, relationship, delete, confirmation, checkpoint, continue,
   cancel, and undo flows.
7. Verify assistant events, provenance, and validation summaries reflect the current metamodel and
   validation issues.
8. Verify assistant-created models can save, validate, transform, and generate downstream outputs.

## Verification Ladder

Run the narrowest relevant checks first, then broaden.

### Metamodel and UI metadata

```powershell
mvn -q -pl tools/mde-cli -am test
mvn -q -pl packages/java/platform-modeling -am test
```

### Validation

```powershell
mvn -q -pl packages/java/mde-evl-validator -am test
mvn -q -pl tools/mde-evl-cli -am test
```

### Transformations

```powershell
mvn -q -pl packages/java/mde-etl-runner -am test
mvn -q -pl tools/mde-etl-cli -am test
mvn -q -pl packages/java/platform-transformation -am test
mvn -q -pl packages/java/platform-model -am test
```

### Generation

```powershell
mvn -q -pl packages/java/mde-m2t-runner -am test
mvn -q -pl tools/mde-m2t-cli -am test
```

### Backend, AI, storage, and complete regression

```powershell
mvn -q -pl apps/backend -am test
mvn -q -pl packages/java/platform-storage-postgres -am test
mvn test
```

Also perform a manual end-to-end smoke test:

1. Start PostgreSQL and backend.
2. Open the frontend and inspect `/api/modeling/config`.
3. Create/import and save a model at the changed level.
4. Reload it and export/import XMI.
5. Validate it.
6. Run CIM-to-PIM, PIM-to-PSM, and PSM-to-artifacts as applicable.
7. Inspect transformed models in the frontend and generated artifacts.
8. Exercise the assistant on the changed concept.
9. Load an existing pre-change stored model and verify the intended migration behavior.

## Pull Request Synchronization Checklist

Copy this checklist into changes that affect the modeling languages.

```text
[ ] Change classified: visual / semantic / abstract syntax / representation contract
[ ] Authoritative Emfatic updated
[ ] Affected Ecore and combined Ecore regenerated and reviewed
[ ] Namespace/version and compatibility policy decided
[ ] Existing stored-model migration behavior defined
[ ] EVL and EOL validation helpers updated
[ ] CIM-to-PIM transformation reviewed
[ ] PIM-to-PSM transformation reviewed
[ ] PSM-to-artifact generation reviewed
[ ] UI metadata, palette, relationships, notation, and views updated
[ ] JSON/XMI bridge and specialized frontend behavior reviewed
[ ] Assistant Ecore contracts, tool behavior, and durable turn flows reviewed
[ ] PostgreSQL migration added only if persistence schema/constraints changed
[ ] API/OpenAPI/REST/SSE contracts updated if payloads/routes changed
[ ] Samples and positive/negative regression tests updated
[ ] Relevant docs and diagrams updated
[ ] End-to-end create/save/reload/XMI/validate/transform/generate/assistant smoke test passed
```

## Final Decision Rule

Before merging, identify:

- Every producer of the changed concept.
- Every consumer of the changed concept.
- Every serialized or persisted representation of the changed concept.
- Every visual/editor representation of the changed concept.
- Every automated or AI-created default containing the changed concept.
- Every test/sample/doc that asserts or explains the changed concept.

The change is synchronized only when all affected items have been updated or explicitly confirmed
as unaffected.
