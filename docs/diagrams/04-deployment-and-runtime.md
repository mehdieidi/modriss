# Deployment and Runtime Architecture

## Docker Compose Deployment

```mermaid
flowchart TB
    host["Developer / deployment host"]
    browser["Browser"]

    subgraph compose["Docker Compose project: modriss"]
        caddy["caddy<br/>edge proxy<br/>port 8088"]
        frontend["frontend<br/>python:3.13-alpine static server<br/>port 8082"]
        admin["admin<br/>nginx static React app<br/>port 8084"]
        landing["landing<br/>nginx static Vite app<br/>port 8083"]
        backend["backend<br/>Spring Boot image<br/>port 8080"]
        postgres["postgres<br/>pgvector/pgvector:pg16<br/>port 5432"]
        localstack["localstack<br/>AWS simulator<br/>port 4566"]
        dozzle["dozzle<br/>container logs<br/>port 9999"]
        prometheus["prometheus<br/>metrics and alerts<br/>port 9090"]
        grafana["grafana<br/>dashboards<br/>port 3000"]
        loki["loki<br/>log store<br/>port 3100"]
        promtail["promtail<br/>log shipper"]
        pgexp["postgres-exporter"]
        nodeexp["node-exporter"]
        cadvisor["cAdvisor"]
        dbvolume[("modriss-postgres-data")]
        uploadvolume[("modriss-backend-uploads")]
        logvolume[("modriss-backend-logs / modriss-caddy-logs")]
    end

    proxy["Host AI proxy<br/>host.docker.internal:2081 by default"]
    provider["Arvan OpenAI-compatible<br/>Gemma-4-31B-IT"]

    host -->|"docker compose up --build"| compose
    browser -->|"HTTP :8088 / *.localhost:8088"| caddy
    caddy -->|"editor.localhost"| frontend
    caddy -->|"admin.localhost"| admin
    caddy -->|"localhost"| landing
    caddy -->|"api routes"| backend
    caddy -->|"grafana.localhost / logs.localhost"| grafana
    caddy --> dozzle
    browser -->|"HTTP :8082"| frontend
    browser -->|"HTTP :8083"| landing
    browser -->|"HTTP :8084"| admin
    browser -->|"REST and SSE :8080"| backend
    admin -->|"Admin REST"| backend
    backend -->|"JDBC; starts after DB healthcheck"| postgres
    postgres --> dbvolume
    backend --> uploadvolume
    backend --> logvolume
    caddy --> logvolume
    backend -->|"Generated AWS project tests"| localstack
    prometheus -->|"scrape"| backend
    prometheus -->|"scrape"| caddy
    prometheus --> pgexp --> postgres
    prometheus --> nodeexp
    prometheus --> cadvisor
    promtail -->|"ship logs"| loki
    promtail --> logvolume
    grafana --> prometheus
    grafana --> loki
    backend -->|"Optional proxied AI traffic"| proxy --> provider
```

## Backend Startup

```mermaid
sequenceDiagram
    participant Boot as Spring Boot
    participant Flyway
    participant DB as PostgreSQL
    participant Core as CoreServicesConfig
    participant Meta as FileMetamodelResolver
    participant Contracts as AssistantMetamodelSchemaService
    participant Health as Actuator

    Boot->>Flyway: Apply classpath db/migration
    Flyway->>DB: Platform migrations V1, V5, V9, V19-V23, V32
    Flyway->>DB: Assistant migrations V14-V18 and V24-V31
    Boot->>Core: Construct service graph
    Core->>Meta: Resolve CIM, PIM, PSM metamodel descriptors
    Meta-->>Core: EPackages, versions, SHA-256 hashes
    Boot->>Contracts: Build Ecore-derived assistant contracts on demand
    Boot->>Health: Expose readiness and liveness
```
