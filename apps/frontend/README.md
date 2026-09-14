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
css/             stylesheets
vendor/antv/     vendored G6 build
vendor/monaco/   vendored Monaco Editor build
index.html       shell page
```

Modeling palettes and notation come from `GET /api/modeling/config` (backend merges Ecore structure
with UI metadata from `platform-modeling`). The diagram editor uses the AntV G6 canvas renderer.

## Docs

- [Frontend architecture](../../docs/public-docs/docs/architecture/frontend.md)
- [Modeling workflow](../../docs/public-docs/docs/guides/modeling-workflow.md)
