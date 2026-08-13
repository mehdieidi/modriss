# Frontend Architecture

The frontend is a static ES-module application under `apps/frontend`. It uses AntV G6 for diagram
editing and communicates with the backend through REST and authenticated fetch-based SSE.

## Diagram editor

```text
IDE shell (palette, inspector, views)
        │
        ▼
renderer-adapter.js  ──config──►  diagramEditor.renderer (antv-g6)
        │
        └─ antv-g6 ──► graph-editor/g6-*
```

At startup the frontend loads `GET /api/modeling/config`, including `diagramEditor.renderer`.
`canvas.js` calls `ensureCanvas()` which mounts `#g6EditorHost`.

## Main Areas

- Authentication and project management
- CIM, PIM, and PSM workbenches; the chatbot is enabled only for CIM and PIM
- Metadata-driven palette and attribute panels
- Graph editing, relationship creation, layout, views, and focus
- Validation and generation progress
- One assistant chat with automatic backend strategy selection, durable turn status, checkpoint,
  confirmation, continue, cancel, rebase, rollback, and undo controls
- Factual progress for obligation planning, type selection, blueprinting, private slices, review,
  structural validation, partial/failure states, and checkpoint publication
- Artifact explorer and file editor
- Change impact analysis
- Planned admin workspace surfaces

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
window.VARKA_BACKEND_BASE_URL = "http://127.0.0.1:8080";
```

Configure the deployed frontend and backend origins together, including the CORS origins needed for
REST and SSE requests.
