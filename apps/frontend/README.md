# Modless Frontend

Static browser modeling application: CIM/PIM/PSM workbenches, artifact explorer, and assistant UI.
No bundler — ES modules, AntV G6 for the graph canvas, Monaco Editor from CDN.

## Run locally

With the backend on port 8080:

```bash
python -m http.server 8082 --directory apps/frontend
```

Open <http://127.0.0.1:8082>. Backend origin:
[`backend-config.js`](backend-config.js) (`window.MODLESS_BACKEND_BASE_URL`).

## Layout

```text
js/           application modules (auth, canvas, chat, model-ops, …)
css/          stylesheets
vendor/antv/  vendored G6 build
index.html    shell page
```

Modeling palettes and notation come from `GET /api/modeling/config` (backend merges Ecore structure
with UI metadata from `platform-modeling`).

## Docs

- [Frontend architecture](../../docs/public-docs/docs/architecture/frontend.md)
- [Modeling workflow](../../docs/public-docs/docs/guides/modeling-workflow.md)
