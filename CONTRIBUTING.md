# Contributing to Varka

Thank you for helping improve Varka. This file is the short entry point; detailed guides
live in the public documentation site.

## Before You Start

1. Read the [change guide](docs/public-docs/docs/contributing/change-guide.md) — language and
   persistence changes must stay synchronized across metamodels, validation, UI, API, and tests.
2. Copy [`.env.example`](.env.example) to `.env` for local overrides (never commit `.env`).
3. Install tooling hooks:

   ```bash
   pip install pre-commit
   pre-commit install
   ```

## Development Workflow

```bash
# Full verification (format, lint, tests)
python scripts/verify.py

# Individual steps
python scripts/format.py          # apply formatting
python scripts/format.py --check  # verify formatting
python scripts/lint.py            # all linters
mvn test                          # Java + integration tests
docker compose up --build         # full stack smoke test
```

Pre-commit runs format check only. Run `python scripts/lint.py` and `python scripts/verify.py` manually
before opening a PR.

## Pull Requests

- Keep PRs focused; link related issues when applicable.
- Use the [pull request template](.github/pull_request_template.md).
- Update OpenAPI (`docs/api/openapi/openapi.yaml`) when REST endpoints change.
- Add a Flyway migration when the PostgreSQL schema changes.
- Update sample models under `mde/samples/` when validation rules affect them.
- Run `python scripts/verify.py` before opening a PR.

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

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE).
