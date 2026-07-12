# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Pre-commit and pre-push hooks for format, lint, and test enforcement
- `.env.example` environment template with documented variables
- Root `CONTRIBUTING.md` and `SECURITY.md` entry points
- `scripts/verify.py` one-command verification pipeline
- Testcontainers-backed PostgreSQL integration tests
- OpenAPI contract checks and REST smoke tests for critical API flows
- Prometheus metrics endpoint and Grafana dashboard template
- OpenTelemetry tracing support (opt-in via environment variables)
- PostgreSQL backup/restore scripts and Flyway migration verification tooling
- Alerting runbooks for production operations
- GitHub PR and issue templates plus CI workflows

## [0.0.1-SNAPSHOT] - 2026-03-19

### Added

- Initial Varka platform: CIM/PIM/PSM modeling, MDE toolchain, Spring Boot backend, PostgreSQL storage, and browser frontend

[Unreleased]: https://github.com/mehdieidi/varka/compare/v0.0.1-SNAPSHOT...HEAD
[0.0.1-SNAPSHOT]: https://github.com/mehdieidi/varka/releases/tag/v0.0.1-SNAPSHOT
