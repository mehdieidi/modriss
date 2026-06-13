# Modeling and System API Sequences

## GET `/api/modeling/config`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelingController
    participant S as ModelingConfigService
    participant R as Classpath UI metadata
    Client->>C: GET /api/modeling/config
    C->>S: config()
    S->>R: Load CIM, PIM, PSM UI metadata JSON
    S->>S: Normalize palette, relationships, views, templates
    S-->>C: Configuration map
    C-->>Client: Modeling palette and metadata
```

## POST `/api/layout`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelingController
    participant L as LayoutService
    participant ELK as Eclipse Layout Kernel
    Client->>C: LayoutRequest(nodes, edges, fixedNodeIds, options)
    C->>L: layout(request)
    L->>L: Normalize graph and choose strategy
    alt ELK can layout graph
        L->>ELK: Run layered, tree, radial, or force layout
        ELK-->>L: Node positions and edge routes
    else fallback required
        L->>L: Deterministic fallback positions and routes
    end
    L-->>C: LayoutResponse(nodes, routedEdges, warnings)
    C-->>Client: LayoutResponse
```

## POST `/api/{level}/{modelId}/views/{viewId}/layout`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelingController
    participant S as StoredViewLayoutService
    participant M as ModelService
    participant L as LayoutService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + level + modelId + viewId + force + strategy
    C->>S: layout(user, level, modelId, viewId, force, strategy)
    S->>M: Load authorized model
    S->>S: Materialize stored view nodes and relationships
    S->>L: Compute layout for persisted view graph
    L-->>S: LayoutResponse
    S->>M: Patch model view layout with expected revision
    M->>DB: Persist updated model and increment revision
    S-->>C: StoredViewLayoutResponse
    C-->>Client: Updated view, revision, counts, warnings
```

## GET `/api/health`

```mermaid
sequenceDiagram
    actor Client
    participant C as HealthController
    Client->>C: GET /api/health
    C-->>Client: status, service name, timestamp
```

## GET `/v3/api-docs`

```mermaid
sequenceDiagram
    actor Client
    participant C as OpenApiController
    participant Spec as Checked-in OpenAPI YAML / generated JSON builder
    Client->>C: GET /v3/api-docs
    C->>Spec: Build or serve OpenAPI contract
    Spec-->>C: JSON API description
    C-->>Client: application/json
```

## GET `/swagger-ui.html`

```mermaid
sequenceDiagram
    actor Browser
    participant C as OpenApiController
    Browser->>C: GET /swagger-ui.html
    C-->>Browser: HTML page loading /v3/api-docs
```

## `/api/github/**`, `/api/impact/**`, `/api/admin/**`

```mermaid
sequenceDiagram
    actor Client
    participant C as FutureFeatureController
    participant E as GlobalExceptionHandler
    Client->>C: Any method under planned feature route
    C->>E: PlatformException 501
    E-->>Client: ApiErrorResponse(feature planned)
```

## Actuator Readiness Used by Compose

```mermaid
sequenceDiagram
    participant Compose as Docker Compose healthcheck
    participant Actuator as Spring Boot Actuator
    participant DB as DataSource health
    participant AI as AiHealthIndicator
    Compose->>Actuator: GET /actuator/health/readiness
    Actuator->>DB: Check database readiness
    Actuator->>AI: Check AI configuration and optional proxy state
    Actuator-->>Compose: UP or DOWN
```
