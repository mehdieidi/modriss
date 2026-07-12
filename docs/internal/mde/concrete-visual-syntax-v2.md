# Concrete Visual Syntax v2 (CVS)

## Purpose

CVS v2 is the formally specified concrete visual syntax layer for Varka. It separates
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
- `primitives` — reusable visual primitives with geometry
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
npm run migrate:all -w @varka/notation-migrate
```

## Editor binding

- The AntV G6 canvas consumes merged `elements`, `notation`, `relationshipRules`, and
  `elementMappings`.
- Container focus uses backend-derived containment palettes and relationship rules. When users draw
  to a container, the editor offers only legal contained targets, opens the container focus canvas
  for the selected internal target, and records an outer visual-only summary edge for zoomed-out
  readability.
- Dashboard layout semantics are stored on CVS viewpoints through `semanticDashboardColumns`; the
  backend layout service consumes those columns from stored view JSON instead of owning DSML class
  groups.

The diagram editor renderer is configured in `platform-config.json`:

```json
"diagramEditor": {
  "renderer": "antv-g6"
}
```
