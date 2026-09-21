# MODRISS Public Documentation

This directory is the source package for the public MODRISS documentation website. It is organized
as a self-contained MkDocs project so it can be previewed locally or deployed by any static-site
pipeline that supports MkDocs.

## Preview Locally

From this directory:

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

## Documentation Sources

The pages here describe the running implementation and link to formal sources where readers need
more detail. Paths in this list are relative to the repository root:

- Backend controllers and configuration define runtime API behavior.
- The Emfatic, Ecore, EVL, ETL, EOL, EGX, and EGL files under `mde/` define the formal modeling behavior.
- Flyway migrations define the persisted PostgreSQL structure.
- `docs/api/openapi/openapi.yaml` is the checked-in API contract.
- `docs/diagrams/` contains architecture diagrams and the source for the modeling-path SVG.
- `mde/process/engineered-method/diagrams/` contains the lifecycle and delivery-engine SVG sources.

The modeling-path, lifecycle, and delivery-engine SVGs are copied into
`docs/public-docs/docs/assets/diagrams/` for the public site. Refresh those copies when a source
diagram changes. When implementation behavior changes, update this package together with the
affected source-of-truth files.
