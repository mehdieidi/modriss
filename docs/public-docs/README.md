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

This package summarizes the current implementation and points to canonical repository assets where
appropriate:

- Backend controllers and configuration define runtime API behavior.
- Emfatic, Ecore, EVL, ETL, EOL, EGX, and EGL files under `mde/` define the formal MDE behavior.
- Flyway migrations define persisted PostgreSQL structure.
- `docs/api/openapi/openapi.yaml` is the checked-in API contract.
- `docs/diagrams/` contains the complete Mermaid architecture diagram package.

When behavior changes, update this package together with the affected source-of-truth files.
