# Concrete Visual Syntax v2 (CVS)

## Purpose

CVS v2 is the formally specified concrete visual syntax layer for MODRISS. It separates
abstract syntax (Emfatic / combined Ecore) from diagram notation, viewpoints, and canvas
interaction policy. The Spring backend validates CVS documents against Ecore and exposes the
merged result through `GET /api/modeling/config`.

## Artifacts

| Artifact                                       | Role                                 |
| ---------------------------------------------- | ------------------------------------ |
| `mde/notation/cvs-v2.schema.json`              | JSON Schema for CVS documents        |
| `mde/notation/cim.cvs.json`                    | CIM concrete visual syntax (phase 1) |
| `mde/notation/pim.cvs.json`                    | PIM CVS (phase 2)                    |
| `mde/notation/psm.cvs.json`                    | PSM CVS (phase 3)                    |
| `tools/notation-migrate/migrate-ui-to-cvs.mjs` | Migrates legacy `*-ui-metadata.json` |

Legacy `*-ui-metadata.json` files remain on the classpath as fallback when a CVS file is absent.

## Document structure

Each CVS document declares:

- `metamodelRef`: level key, Ecore path, namespace URI
- `elements`, explicit per-type visual definitions and visible fields
- `elementVisualDefaults` and `elementVisualRules`, default and matched visual metadata
- `referenceMappings` and `relationshipMappings`, semantic edges and relationship-object EClasses
- `views`, question-focused palettes and canvas element filters
- `containers`, containment palettes, canvas scopes, and relationship kinds
- `canvasPolicy`, palette roles, container focus, and semantic zoom thresholds
- `rootTemplate` and optional `starterTemplate`, initial model content

Admin rejects retired top-level fields (`primitives`, `notationPrimitives`, `elementOverrides`,
`viewpoints`, `universalSyntax`, `kernelSyntax`, and `kernelNotation`). Their design guidance belongs
in documentation; executable visual metadata belongs in the current CVS fields above.

At runtime `CvsV2Loader` converts CVS into the UI metadata shape consumed by
`ModelingConfigService`, then derives per-type `elementMappings` after Ecore merge.

## Coverage proof

Completeness is enforced in two places:

1. **Load time**, CVS must declare `cvsVersion: 2`, per-type elements, views,
   containers, and canvas policy.
2. **Merge time**, every concrete EClass receives `visualRole`, notation geometry, and card
   fields; `syntaxCoverage` reports uncovered view types and reference fields.

## Phased delivery

| Phase  | Scope                                                                                   | Status   |
| ------ | --------------------------------------------------------------------------------------- | -------- |
| 1: CIM | `cim.cvs.json`, bounded-context overlays, validation decoration, full canvas operations | Complete |
| 2: PIM | `pim.cvs.json`, service containers, workflow notation, ELK auto-layout                  | Complete |
| 3: PSM | `psm.cvs.json`, AWS resource cards, shortcut integration edges, impact overlay          | Complete |

Generate or refresh level CVS files:

```bash
npm run migrate:all -w @modriss/notation-migrate
```

## Editor binding

- The AntV G6 canvas consumes merged `elements`, `notation`, `relationshipRules`, and
  `elementMappings`.
- Global configured views with a non-empty `palette` use that palette as their visible canvas
  surface. Wider `elementTypes` remain available for metadata, dashboards, inspectors, and
  relationship semantics; they do not by themselves add non-palette node types to the canvas.
- The PSM CVS sets `workbench.defaultViewDefinitionId` to `psm-workflow-asl`, so the PSM tab opens
  on the Step Functions / ASL view.
- Container focus uses backend-derived containment palettes and relationship rules. When users draw
  to a container, the editor offers only legal contained targets, opens the container focus canvas
  for the selected internal target, and records an outer visual-only summary edge for zoomed-out
  readability.
- Dashboard layout semantics are stored on CVS views through `semanticDashboardColumns`; the
  backend layout service consumes those columns from stored view JSON instead of owning DSML class
  groups.

The diagram editor renderer is configured in `platform-config.json`:

```json
"diagramEditor": {
  "renderer": "antv-g6"
}
```
