# ADR 0005: GLSP/Sprotty Diagram Editor v2

## Status

Accepted

## Context

The Modless IDE originally rendered CIM/PIM/PSM diagrams with AntV G6 and JSON-owned UI metadata.
Thesis and production goals require:

- A formally specified concrete visual syntax derived from Ecore
- A standards-based diagram protocol (Eclipse GLSP) with SVG rendering (Sprotty)
- UI shell preservation and safe coexistence with the existing G6 editor

## Decision

1. Introduce **CVS v2** notation files under `mde/notation/` validated and merged by
   `ModelingConfigService`.
2. Add a **Node GLSP sidecar** (`packages/js/glsp-server`) that loads models from Spring REST,
   maps CVS + graph state to Sprotty GModel, and persists JSON patches.
3. Add a **scoped Vite bundle** (`packages/js/glsp-client`) output to
   `apps/frontend/vendor/glsp/` with Modless-themed node/edge views.
4. Introduce a **renderer adapter** in the frontend; `canvas.js` delegates to G6 or GLSP based on
   `diagramEditor.renderer` in platform config.
5. Keep Spring as the **source of truth** for models and metamodel config; the sidecar owns
   diagram-session operations only.

## Consequences

### Positive

- Clear separation of abstract vs concrete syntax
- Renderer switch without data migration
- GLSP ecosystem alignment for future PIM/PSM phases

### Negative

- Additional Node service in deployment (`glsp-server` on port 8081)
- First npm workspace bundler (scoped to GLSP client only)
- Phase 1 GLSP feature parity trails G6 for advanced overlays (bounded context boxes, impact)

### Mitigation

- Default renderer remains `antv-g6`
- GLSP sidecar reuses the same modeling config and patch semantics as G6
- Phased delivery: CIM first, then PIM, then PSM
