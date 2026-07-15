# Security, Observability, and Failure Flows

## Authentication and Authorization Boundary

```mermaid
flowchart TD
    request["Incoming protected request"]
    token["X-Auth-Token"]
    session["auth_sessions row"]
    user["users row"]
    project["Project access check"]
    role{"Operation requires editor/owner?"}
    allowed["Proceed to service action"]
    denied["401/403 ApiErrorResponse"]

    request --> token --> session --> user --> project --> role
    role -- read/member access ok --> allowed
    role -- editor/owner required and present --> allowed
    role -- missing or expired token --> denied
    role -- insufficient role --> denied
```

## Optimistic Concurrency

```mermaid
sequenceDiagram
    actor Client
    participant API
    participant Service as ModelService / TransformationService
    participant DB as PostgreSQL

    Client->>API: Mutation with expectedRevision
    API->>Service: Execute mutation
    Service->>DB: Load current revision
    alt expectedRevision equals current
        Service->>DB: Persist next revision
        Service-->>API: Success
        API-->>Client: Updated summary/response
    else stale revision
        Service-->>API: PlatformException 409
        API-->>Client: Refresh required
    end
```

## Request Logging and Error Response

```mermaid
flowchart LR
    req["HTTP request"]
    filter["RequestLoggingFilter<br/>assign requestId MDC"]
    controller["Controller/service"]
    exception{"Exception?"}
    handler["GlobalExceptionHandler"]
    response["ApiErrorResponse<br/>message, status, timestamp, issues"]
    logs["Structured console/file log<br/>requestId"]

    req --> filter --> controller --> exception
    exception -- no --> ok["Normal response"]
    exception -- yes --> handler --> response
    filter --> logs
    handler --> logs
```

## MDE Failure Handling

```mermaid
flowchart TD
    run["Run EVL / ETL / EGX"]
    phase{"Failure phase"}
    validation["Request validation"]
    parse["Parse diagnostics"]
    load["Model/metamodel loading"]
    exec["Epsilon runtime execution"]
    store["Target model storing"]
    timeout["Watchdog timeout"]
    report["Structured report with phase, reason, fix, source location, captured output"]
    platform["PlatformException to API or test"]

    run --> phase
    phase --> validation --> report
    phase --> parse --> report
    phase --> load --> report
    phase --> exec --> report
    phase --> store --> report
    phase --> timeout --> report
    report --> platform
```

## Assistant Failure and Guardrails

```mermaid
flowchart TD
    prompt["User prompt"]
    redact["Redact secrets and bearer tokens"]
    inject["Mark likely prompt-injection text"]
    rate["Rate limit by user"]
    proxy["Validate optional AI proxy"]
    provider["Provider call with retry/circuit"]
    action["Agent tool action returned"]
    ground["Check type, feature, containment, and target IDs"]
    preview["Update workspace and validate"]
    decision{"Mandatory validation passes?"}
    save["Commit checkpointed model revision"]
    block["Reject with 422/409 and audit"]

    prompt --> redact --> inject --> rate --> proxy --> provider --> action --> ground --> preview --> decision
    decision -- yes --> save
    decision -- no --> block
```

## Data Protection Hotspots

```mermaid
flowchart TB
    secrets["Passwords and API keys"]
    passwords["Password hashes salted in AuthService"]
    env["AI keys only via environment/config"]
    assistant["Assistant prompt guard redacts secrets from outbound prompts"]
    artifacts["Artifact path validation rejects traversal and absolute paths"]
    uploads["Model import enforces max request size and bounded stream reads"]
    generated["Generated artifacts enforce file count, file size, and total size budgets"]

    secrets --> passwords
    secrets --> env
    secrets --> assistant
    artifacts --> generated
    uploads --> generated
```

## Runtime Health

```mermaid
flowchart LR
    compose["Docker Compose"]
    readiness["/actuator/health/readiness"]
    db["DataSource health"]
    ai["AiHealthIndicator"]
    proxy["ProxyAvailability"]
    result["Container healthy/unhealthy"]

    compose --> readiness
    readiness --> db
    readiness --> ai --> proxy
    readiness --> result
```

## Observability Pipeline

```mermaid
flowchart TB
    browser["Frontend, admin, and landing browser telemetry"]
    backend["Spring Boot backend<br/>Actuator, request logs, app events"]
    caddy["Caddy edge<br/>access logs and metrics"]
    postgres["PostgreSQL"]
    docker["Docker containers and host"]

    prometheus["Prometheus<br/>scrape and alert rules"]
    loki["Loki<br/>log storage"]
    promtail["Promtail<br/>file and Docker log collector"]
    grafana["Grafana<br/>dashboards and exploration"]
    dozzle["Dozzle<br/>local live logs"]
    pgexp["postgres-exporter"]
    nodeexp["node-exporter"]
    cadvisor["cAdvisor"]

    browser -->|"POST /api/telemetry/frontend"| backend
    backend -->|"metrics /actuator/prometheus"| prometheus
    caddy -->|"metrics :2019/metrics"| prometheus
    postgres --> pgexp --> prometheus
    docker --> nodeexp --> prometheus
    docker --> cadvisor --> prometheus

    backend -->|"backend.log volume"| promtail
    caddy -->|"access.log volume"| promtail
    docker -->|"container JSON logs"| promtail
    promtail --> loki
    docker --> dozzle

    grafana --> prometheus
    grafana --> loki
```
