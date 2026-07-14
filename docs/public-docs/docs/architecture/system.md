# System Architecture

```mermaid
flowchart TB
    User["Modeler / Developer"]
    Landing["Landing site"]
    Frontend["Modeling frontend<br/>HTML, CSS, JavaScript<br/>AntV G6"]
    Backend["Spring Boot backend<br/>API, services, MDE orchestration, assistant"]
    DB[("PostgreSQL")]
    MDE["MDE assets<br/>Emfatic, Ecore, EVL, ETL, EGX/EGL"]
    AI["External AI provider"]
    CLIs["MDE CLI tools"]

    User --> Landing
    User --> Frontend
    Frontend -->|"REST, downloads, authenticated SSE"| Backend
    Backend --> DB
    Backend --> MDE
    Backend --> AI
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
- AI provider calls are optional and isolated from normal platform traffic.
- LocalStack is used to test generated AWS projects, not to run Varka itself.
