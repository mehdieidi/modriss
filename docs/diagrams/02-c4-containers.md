# C4 Level 2: Containers

```mermaid
flowchart TB
    user["Person: Modeler"]

    subgraph modriss["MODRISS System"]
        caddy["Container: Caddy Edge<br/>Reverse proxy, access logs, local host routing"]
        landing["Container: Landing Site<br/>Static HTML/CSS/JS<br/>Product story and case study"]
        frontend["Container: Modeling Frontend<br/>Plain HTML/CSS/JavaScript<br/>AntV G6 renderer"]
        admin["Container: Admin Frontend<br/>React + TypeScript<br/>Operational control plane"]
        backend["Container: Spring Boot Backend<br/>Java 17 + Spring MVC/JDBC/AI<br/>API, orchestration, validation, transformation"]
        postgres[("Container: PostgreSQL<br/>Application records, assistant memory, durable turns")]
        prometheus["Container: Prometheus<br/>Metrics scrape and alert rules"]
        grafana["Container: Grafana<br/>Metrics and log dashboards"]
        loki["Container: Loki<br/>Log store"]
        promtail["Container: Promtail<br/>Backend, Caddy, Docker log shipping"]
        dozzle["Container: Dozzle<br/>Container log inspection"]
        localstack["Container: LocalStack<br/>AWS emulator for generated projects"]
        mde["Container: MDE Repository Assets<br/>Emfatic/Ecore, EVL, ETL, EGX/EGL<br/>Formal languages and executable rules"]
        cli["Container: MDE CLI Tools<br/>Picocli shaded JARs<br/>Metamodel compile, validate, transform, generate"]
    end

    provider["Arvan OpenAI-compatible AI<br/>Gemma-4-31B-IT"]
    proxy["Optional HTTP/SOCKS Proxy"]

    user -->|"HTTP/HTTPS entrypoints"| caddy
    caddy -->|"app route"| frontend
    caddy -->|"landing route"| landing
    caddy -->|"admin route"| admin
    caddy -->|"API, Actuator, Swagger routes"| backend
    caddy -->|"operator routes"| grafana
    caddy -->|"local log route"| dozzle
    user -->|"Direct local ports when debugging"| frontend
    user -->|"Direct local ports when debugging"| landing
    user -->|"Admin operations"| admin
    frontend -->|"REST JSON, multipart, downloads"| backend
    frontend <-->|"Authenticated SSE assistant events"| backend
    admin -->|"Admin REST calls and telemetry"| backend
    backend -->|"Spring JDBC"| postgres
    backend -->|"Reads executable MDE assets"| mde
    backend -->|"Generated project deployment tests"| localstack
    prometheus -->|"Scrapes Actuator metrics"| backend
    prometheus -->|"Scrapes Caddy, Postgres, host, container metrics"| caddy
    grafana -->|"PromQL"| prometheus
    grafana -->|"LogQL"| loki
    promtail -->|"Push logs"| loki
    promtail -->|"Reads file and Docker logs"| backend
    promtail -->|"Reads access logs"| caddy
    dozzle -->|"Reads Docker socket logs"| backend
    cli -->|"Reads executable MDE assets"| mde
    backend -->|"Structured JSON calls<br/>temperature 0, thinking disabled"| proxy
    proxy --> provider
```

## Container Protocols

```mermaid
flowchart LR
    browser["Browser frontend"]
    mvc["Spring MVC controllers"]
    sse["SSE emitter hub"]
    services["Application and assistant services"]
    jdbc["JDBC / PlatformStore adapter"]
    db[("PostgreSQL")]
    files["MDE files and temporary workspaces"]
    epsilon["Epsilon and EMF runtimes"]

    browser -->|"REST: JSON, multipart, ZIP/text downloads"| mvc
    browser <--|"SSE /api/chatbot/turns/{turnId}/events"| sse
    mvc --> services
    services --> jdbc --> db
    services --> epsilon
    epsilon --> files
```
