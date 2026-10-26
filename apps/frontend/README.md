# MODRISS Frontend

Static browser modeling application: CIM/PIM/PSM workbenches, artifact explorer, impact analysis,
and assistant UI. No bundler, ES modules with vendored browser libraries.

## Run locally

With the backend on port 8080:

```bash
python -m http.server 8082 --directory apps/frontend
```

Open <http://127.0.0.1:8082>. Backend origin:
[`backend-config.js`](backend-config.js) (`window.MODRISS_BACKEND_BASE_URL`).

## Layout

```text
js/              application modules (auth, canvas, chat, model-ops, ...)
tests/           Node.js tests for frontend modules
css/             stylesheets
vendor/antv/     vendored G6 build
vendor/monaco/   vendored Monaco Editor build
index.html       shell page
```

Modeling palettes and notation come from `GET /api/modeling/config` (backend merges Ecore structure
with UI metadata from `platform-modeling-infrastructure`). The diagram editor uses the AntV G6 canvas renderer.

## First-use tutorial

The first-use editor tutorial starts after authentication and project-list loading. It introduces
the workspace, then guides a user-created CIM goal/requirement exercise in the Goals and
Requirements view. It never creates, replaces, validates, or generates model content automatically.
Users can minimize the tips, go back, or permanently dismiss them with Finish tutorial.

Progress is stored in browser local storage under `modriss.editorTutorial:user:<user-id>` for
registered users and `modriss.editorTutorial:guest` for guests. Dismissal applies in that browser;
it does not sync across devices and is lost if site storage is cleared. Interrupted tutorials
resume, and missing unsaved exercise elements return the user to the appropriate creation step.

Run unit tests with `npm run test:frontend`. The explicit browser check is
`node apps/frontend/tests/editor-tutorial.browser.mjs` (uses the landing app's Playwright
installation and a local editor-shell harness with model state supplied at the test boundary).

The impact analysis integration check is `node apps/frontend/tests/impact.live.mjs`.
It requires a backend built from the current source and PostgreSQL. Set `IMPACT_BACKEND_URL`
to override `http://127.0.0.1:8080`. The check imports the Climate Relief Grants CIM into an
isolated guest project, runs real CIM-to-PIM, PIM-to-PSM, and artifact generation, verifies the
recorded trace edges and exact file contributors, and exercises the editor in Chromium.
It covers filtering, trace rules, pagination, file opening, cross-level navigation, mobile
width, request races, closing during loading, and failure/retry. Screenshots and measured
API timing go to `apps/backend/target/impact-verification`; the temporary project is deleted.

## Docs

- [Frontend architecture](../../docs/public-docs/docs/architecture/frontend.md)
- [Modeling workflow](../../docs/public-docs/docs/guides/modeling-workflow.md)
