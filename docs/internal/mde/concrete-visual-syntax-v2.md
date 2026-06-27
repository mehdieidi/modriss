# Concrete Visual Syntax v2 (CVS)

## Purpose

CVS v2 is the formally specified concrete visual syntax layer for Modless. It separates
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

- `metamodelRef` — level key, Ecore path, namespace URI
- `primitives` — reusable visual primitives with `sprottyShape` and geometry
- `elementVisualRules` / `elementOverrides` — type and package visual rules
- `referenceMappings` — EReference to semantic edge kind
- `relationshipMappings` — relationship-object EClasses
- `viewpoints` — workbench views (palette, layout hints, legal edge kinds)
- `canvasPolicy` — palette roles, container focus, semantic zoom thresholds
- `semanticDashboardColumns` — optional viewpoint-owned dashboard layout columns

At runtime `CvsV2Loader` converts CVS into the UI metadata shape consumed by
`ModelingConfigService`, then derives per-type `elementMappings` after Ecore merge.

## Coverage proof

Completeness is enforced in two places:

1. **Load time** — CVS must declare `cvsVersion: 2`, viewpoints, and canvas policy.
2. **Merge time** — every concrete EClass receives `visualRole`, notation geometry, and card
   fields; `syntaxCoverage` reports uncovered view types and reference fields.

## Phased delivery

| Phase   | Scope                                                                                   | Status   |
| ------- | --------------------------------------------------------------------------------------- | -------- |
| 1 — CIM | `cim.cvs.json`, bounded-context overlays, validation decoration, full canvas operations | Complete |
| 2 — PIM | `pim.cvs.json`, service containers, workflow notation, ELK auto-layout                  | Complete |
| 3 — PSM | `psm.cvs.json`, AWS resource cards, shortcut integration edges, impact overlay          | Complete |

Generate or refresh level CVS files:

```bash
npm run migrate:all -w @modless/notation-migrate
```

## Editor binding

- **G6 v1** continues to consume merged `elements`, `notation`, and `relationshipRules`.
- **GLSP v2** consumes `elementMappings`, `cvsPrimitives`, and viewpoints via the Node sidecar.
- Container focus uses backend-derived containment palettes and relationship rules. When users draw
  to a container, the editor offers only legal contained targets, opens the container focus canvas
  for the selected internal target, and records an outer visual-only summary edge for zoomed-out
  readability.
- Dashboard layout semantics are stored on CVS viewpoints through `semanticDashboardColumns`; the
  backend layout service consumes those columns from stored view JSON instead of owning DSML class
  groups.

Switch renderers with `diagramEditor.renderer` in `platform-config.json`:

```json
"diagramEditor": {
  "renderer": "glsp-sprotty",
  "glspServerUrl": "ws://127.0.0.1:8081/modless",
  "allowedRenderers": ["antv-g6", "glsp-sprotty"]
}
```
