# Security Policy

## Supported Versions

| Version        | Supported |
| -------------- | --------- |
| 0.0.x-SNAPSHOT | yes       |

Tagged releases will be listed here when published.

## Reporting a Vulnerability

**Do not open public GitHub issues for security vulnerabilities.**

Report security issues privately to the maintainers:

- **Email:** [mehdieidi@proton.me](mailto:mehdieidi@proton.me) (replace with your project security contact if different)
- **Subject:** `[Modless Security]` brief description

Include:

1. Affected component (backend, frontend, MDE toolchain, generated artifacts)
2. Steps to reproduce
3. Impact assessment (data exposure, privilege escalation, etc.)
4. Suggested fix, if you have one

You should receive an acknowledgment within **5 business days**. We will coordinate
disclosure timing and credit if you wish.

## Scope

In scope:

- Modless platform runtime (Spring Boot API, PostgreSQL storage, WebSocket/SSE assistant)
- Authentication, authorization, and session handling
- Model upload, artifact export, and path traversal controls
- AI assistant guardrails and provider integration
- Docker Compose and deployment configuration shipped in this repository

Out of scope:

- Vulnerabilities in generated AWS projects (report to the deploying organization)
- Third-party services (OpenAI, Google, LocalStack) unless caused by Modless integration flaws
- Social engineering or physical attacks

## Secure Development

Platform security controls are documented in
[docs/public-docs/docs/operations/security.md](docs/public-docs/docs/operations/security.md).

Before deploying beyond local development:

- Replace default database credentials and API keys
- Restrict CORS, WebSocket origins, and exposed ports
- Enable TLS termination and secret management
- Configure backups, monitoring, and alerting (see `docs/public-docs/docs/operations/`)

## Dependency Updates

We track dependencies through Maven, npm, and Docker base images. Security patches should be
applied through the normal release process. Report supply-chain concerns using the process above.
