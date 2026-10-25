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
- `methodology/process/method/diagrams/` contains the lifecycle and delivery-engine SVG sources.

The modeling-path, lifecycle, and delivery-engine SVGs are copied into
`docs/public-docs/docs/assets/diagrams/` for the public site. Refresh those copies when a source
diagram changes. When implementation behavior changes, update this package together with the
affected source-of-truth files.

The complete process reference and its diagrams are generated together. From the repository root,
run `node methodology/process/tools/generate-public-process-reference.mjs`. Diagram sources and
review guidance are in `methodology/process/method/diagrams/process-reference/README.md`; the public
SVG copies use the existing documentation theme and full-size image viewer.

The public [serverless suitability guide](docs/guides/serverless-suitability.md) and the assistant's
[packaged research criteria](../../packages/java/platform-modeling-assistant/src/main/resources/assistant/serverless-suitability.md)
share criterion identifiers C01 to C12 and source identifiers A1 to A4, I1 to I5, and M1 to M2.
Update both when the criteria, evidence or assessment procedure changes.
