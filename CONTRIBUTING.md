# Contributing to MODRISS

Thank you for helping improve MODRISS. This file is the short entry point; detailed guides
live in the public documentation site.

## Before You Start

1. Read the [change guide](docs/public-docs/docs/contributing/change-guide.md), language and
   persistence changes must stay synchronized across metamodels, validation, UI, API, and tests.
2. Copy [`.env.example`](.env.example) to `.env` for local overrides (never commit `.env`).
3. Install tooling hooks:

   ```bash
   pip install pre-commit
   pre-commit install
   ```

## Development Workflow

```bash
# Run the checks directly
python scripts/format.py          # apply formatting
python scripts/format.py --check  # verify formatting
python scripts/lint.py            # all linters
python scripts/check-flyway-migration-versions.py --changed-only
python -m unittest discover -s scripts/tests
node methodology/process/tools/validate-coverage.mjs
mvn test                          # Java + integration tests
docker compose up --build         # full stack smoke test
```

Run the checks above before opening a PR; apply formatting only when needed.

`scripts/format.py` delegates `.emf` files to `scripts/format-emf.py`, including in
`--check` mode. Its generic whitespace normalizer skips `.emf` files so it preserves
the metamodel formatter's layout and literal/comment contents. For metamodels only,
run `python scripts/format-emf.py`; see the
[Emfatic formatter guide](scripts/README-emf-formatting.md).

## Pull Requests

- Keep PRs focused; link related issues when applicable.
- Use the [pull request template](.github/pull_request_template.md).
- Update OpenAPI (`docs/api/openapi/openapi.yaml`) when REST endpoints change.
- Add a Flyway migration when the PostgreSQL schema changes. Use
  `python scripts/flyway-next-migration.py "<description>"` instead of choosing the version
  manually, because Flyway versions are global across all backend migration locations.
- Update sample models under `methodology/modeling-framework/samples/` when validation rules affect them.
- Run the development checks above before opening a PR.

## Architecture Decisions

Significant design choices are documented in `docs/diagrams/`, `docs/internal/`, and the public
MkDocs site. Update the relevant architecture and guide pages when changing core technology,
layering, or security posture.

## Documentation

- Public docs: `docs/public-docs/` (MkDocs)
- Engineering deep-dives: `docs/internal/` (storage, AI, artifacts, MDE impact guides)
- Architecture diagrams: `docs/diagrams/`
- API reference: `docs/api/openapi/openapi.yaml`
- Deployment and observability: `deploy/`, `infra/`
- Tooling configuration: `config/`

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE).
