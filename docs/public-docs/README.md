# MODRISS Public Documentation

This directory is the source package for the public MODRISS documentation website. It is organized
as a self-contained MkDocs project and built into a static site for the Docker Compose deployment.

## Preview Locally

The normal local stack serves the docs through Caddy at `http://docs.localhost:8088`. From the
repository root, start it with:

```bash
docker compose up -d --build
```

For a standalone MkDocs preview, from this directory:

```bash
python -m pip install -r requirements.txt
mkdocs serve
```

Open `http://127.0.0.1:8000`.

## Build

```bash
mkdocs build --strict
```

The generated site is written to `site/`.

Production serves the same generated site at `https://docs.modriss.site` through Caddy. The
production deployment script builds and refreshes the docs container and checks the public URL.

## Documentation Sources

The pages here describe the running implementation and link to formal sources where readers need
more detail. Paths in this list are relative to the repository root:

- Backend controllers and configuration define runtime API behavior.
- The Emfatic, Ecore, EVL, ETL, EOL, EGX, and EGL files under `methodology/` define the formal modeling behavior.
- Flyway migrations define the persisted PostgreSQL structure.
- `docs/api/openapi/openapi.yaml` is the checked-in API contract.
- `docs/diagrams/` contains architecture diagrams and the source for the modeling-path SVG.
- `docs/thesis/modriss-process-chapter.deljouyi-style.fa.md` defines the public development-process content.
- `methodology/process/method/diagrams/process.svg` and `increment.svg` are the current process diagrams used in the public guides.

The modeling-path SVG is copied into `docs/public-docs/docs/assets/diagrams/`. The process overview
and increment guide use `process.svg` and `increment.svg` from
`methodology/process/method/diagrams/`, copied without modification to
`docs/public-docs/docs/assets/diagrams/process/`. Refresh both copies when their sources change.
The supplied diagrams define their own background and adaptive foreground colors for theme support.
The landing page redraws the process as native React/SVG in
`apps/landing/src/ProcessDiagram.tsx`, using the graph and independently authored layout in
`apps/landing/src/process-diagram-model.ts`. The source SVG is a content reference, not a runtime
asset. Keep the 34 activities, nine gates, nested groups and 54 directed relationships aligned
when the process changes. `process-diagram-layout.ts` automatically reflows the complete diagram
for its available width. Phone layouts stack the groups vertically and reroute their connections;
the figure has no viewer controls or internal scrolling.
Phase numbering follows the diagrams: 1. Inception, 2. Active Product Development, 3. Retirement.

The public process pages are maintained by hand from the thesis chapter. Keep the explanation
focused on phases, activities, responsibilities, inputs, outputs and reviews. The legacy
`generate-methodology-guides.mjs` and `generate-public-process-reference.mjs` commands recognize
the maintenance marker in these pages and skip them, preserving the edited prose and navigation.
Do not restore the generated task catalogs or their repeated reference lists in the public guides.
The process redirect hook preserves the former catalog URLs without adding them to navigation or search.
It also marks process-page content for scoped reading and table styles in `stylesheets/process.css`.

The public [serverless suitability guide](docs/guides/serverless-suitability.md) summarizes criteria
C01 to C12. The assistant's [packaged research criteria](../../packages/java/platform-modeling-assistant/src/main/resources/assistant/serverless-suitability.md)
retain the detailed research and provider references. Keep criterion meanings consistent when
changing either version.
