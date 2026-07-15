# System Architecture

```mermaid
flowchart TB
    User["Modeler / Developer"]
    Operator["Operator / Admin"]
    Caddy["Caddy edge<br/>reverse proxy, access logs, metrics"]
    Landing["Landing site"]
    Frontend["Modeling frontend<br/>HTML, CSS, JavaScript<br/>AntV G6"]
    Admin["Admin app<br/>React, TypeScript"]
    Backend["Spring Boot backend<br/>API, services, MDE orchestration, assistant"]
    DB[("PostgreSQL")]
    MDE["MDE assets<br/>Emfatic, Ecore, EVL, ETL, EGX/EGL"]
    Observability["Prometheus, Grafana, Loki, Promtail<br/>metrics, dashboards, logs"]
    LocalStack["LocalStack<br/>AWS emulator for generated projects"]
    AI["External AI provider"]
    CLIs["MDE CLI tools"]

    User --> Caddy
    Operator --> Caddy
    Caddy --> Landing
    Caddy --> Frontend
    Caddy --> Admin
    Caddy --> Backend
    Caddy --> Observability
    Frontend -->|"REST, downloads, authenticated SSE"| Backend
    Admin -->|"admin REST APIs"| Backend
    Backend --> DB
    Backend --> MDE
    Backend --> LocalStack
    Backend --> AI
    Observability --> Backend
    Observability --> Caddy
    Observability --> DB
    CLIs --> MDE
```

## Architectural Style

The backend is a modular monolith. Spring MVC controllers expose public transports, application
services own use cases, `PlatformStore` defines the persistence boundary, and the PostgreSQL module
implements that boundary. Modeling and MDE runners remain reusable Java modules.

The frontend is a static browser application made of ES modules. It consumes backend-derived
modeling configuration rather than maintaining a second independent metamodel.

## Runtime Boundaries

- Static browser assets can be hosted independently from the backend.
- The backend requires PostgreSQL and filesystem access to the `mde/` assets.
- The admin app is a separate browser app for operational control-plane workflows.
- Prometheus, Grafana, Loki, Promtail, Caddy logs, and Dozzle provide the local DevOps/SRE surface.
- AI provider calls are optional and isolated from normal platform traffic.
- LocalStack is used to test generated AWS projects, not to run Varka itself.
