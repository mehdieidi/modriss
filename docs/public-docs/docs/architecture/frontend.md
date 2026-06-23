# Frontend Architecture

The frontend is a static ES-module application under `apps/frontend`. It supports two diagram
renderers — AntV G6 (v1, default) and Eclipse GLSP/Sprotty (v2) — and communicates with the
backend through REST, SSE, and WebSocket.

## Dual renderer architecture

```
IDE shell (palette, inspector, views)
        │
        ▼
renderer-adapter.js  ──config──►  diagramEditor.renderer
        │                              │
        ├─ antv-g6 ──► graph-editor/g6-* (unchanged)
        └─ glsp-sprotty ──► vendor/glsp bundle + glsp-server WebSocket sidecar
```

At startup the frontend loads `GET /api/modeling/config`, including `diagramEditor` settings
(`renderer`, `glspServerUrl`, `allowedRenderers`). `canvas.js` calls `ensureCanvas()` which
mounts either `#g6EditorHost` or `#glspEditorHost`.

The GLSP sidecar (`packages/js/glsp-server`, port 8081) loads model JSON and CVS-backed config
from Spring, performs diagram operations, and persists JSON patches to the same REST endpoints as
the G6 editor.

## Main Areas

- Authentication and project management
- CIM, PIM, and PSM workbenches
- Metadata-driven palette and attribute panels
- Graph editing, relationship creation, layout, views, and focus
- Validation and generation progress
- Assistant chat and proposal controls
- Artifact explorer and file editor
- Planned impact-analysis and admin surfaces

## Metadata-Driven Modeling

At startup, the frontend loads `GET /api/modeling/config`. This response merges formal Ecore
structure with UI-owned metadata. Frontend modules normalize and query it for:

- Creatable concepts
- Attributes and enums
- Containment and legal relationship rules
- Visual roles and notation
- Palette categories
- Viewpoint definitions

This avoids hard-coding a second full copy of each language in JavaScript. Specialized workbenches
still contain level-specific behavior where a generic editor is insufficient.

## State and Persistence

The browser maintains working state, graph state, selections, view state, and undo history. Saved
models are persisted through revision-aware API calls. Generated layouts can be persisted per model
and view.

## Backend Address

The static frontend reads its backend base URL from:

```javascript
window.MODLESS_BACKEND_BASE_URL = "http://127.0.0.1:8080";
```

Configure the deployed frontend and backend origins together, including CORS and WebSocket allowed
origins.
