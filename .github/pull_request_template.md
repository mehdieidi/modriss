## Summary

<!-- What changed and why? Link issues with "Fixes #123" when applicable. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update
- [ ] Refactoring / tooling

## Checklist

- [ ] `python scripts/verify.py` passes locally
- [ ] OpenAPI updated (`docs/api/openapi/openapi.yaml`) if REST endpoints changed
- [ ] Flyway migration added if PostgreSQL schema changed
- [ ] Sample models under `mde/samples/` updated if validation rules changed
- [ ] Public docs / diagrams updated when behavior or architecture changed
- [ ] Architecture or guide docs updated for significant design decisions (`docs/diagrams/`, `docs/public-docs/`, `docs/internal/`)

## Test plan

<!-- How did you verify this change? -->

- [ ] Unit / integration tests
- [ ] Manual UI smoke test
- [ ] Docker Compose full-stack test
