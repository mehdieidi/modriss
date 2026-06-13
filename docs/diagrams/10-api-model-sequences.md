# Model API Sequences

Each `{level}` route applies independently to `cim`, `pim`, and `psm`.

## GET `/api/{level}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + level + optional projectId
    C->>M: listSummaries(user, level, projectId)
    alt projectId is blank
        M-->>C: Empty list
    else project supplied
        M->>P: Verify project access
        M->>DB: List models for project and level
        M-->>C: Sorted ModelSummary[]
    end
    C-->>Client: ModelSummary[]
```

## POST `/api/{level}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant Meta as MetamodelResolver
    participant XMI as XmiModelImportService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + projectId + name + model
    C->>M: create(user, level, ...)
    M->>M: Verify editor; normalize model
    M->>XMI: Produce canonical XMI sidecar
    M->>Meta: Resolve version and metamodel hash
    M->>DB: Persist model, XMI, and global index
    M-->>C: ModelRecord revision 1
    C-->>Client: ModelSummary
```

## GET `/api/{level}/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + level + model id
    C->>M: get(user, level, id)
    M->>DB: Resolve global model index and model
    M->>P: Verify project access
    M->>M: Repair missing metamodel metadata if needed
    M-->>C: Client-safe ModelRecord
    C-->>Client: ModelRecord
```

## PUT `/api/{level}/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant Lock as ModelLockService
    participant M as ModelService
    participant XMI as XmiModelImportService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: replacement model + expectedRevision
    C->>C: Require expectedRevision
    C->>M: update(user, level, id, ...)
    M->>Lock: Acquire model lock
    M->>DB: Load current model
    M->>M: Check revision and editor permission
    M->>XMI: Resolve/canonicalize source XMI
    M->>DB: Persist model, sidecar, index; increment revision
    M-->>C: Updated ModelRecord
    C-->>Client: ModelSummary
```

## PATCH `/api/{level}/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant Lock as ModelLockService
    participant M as ModelService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: name + add/replace/remove operations + expectedRevision
    C->>C: Require expectedRevision
    C->>M: patch(user, level, id, ...)
    M->>Lock: Acquire model lock
    M->>DB: Load current model
    M->>M: Verify revision and editor permission
    loop Each JSON Pointer operation
        M->>M: Validate path and apply add/replace/remove
    end
    M->>M: Normalize and canonicalize sidecar
    M->>DB: Persist model and increment revision
    M-->>C: Updated ModelRecord
    C-->>Client: ModelSummary
```

## DELETE `/api/{level}/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant Lock as ModelLockService
    participant M as ModelService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + level + model id
    C->>M: delete(user, level, id)
    M->>Lock: Acquire model lock
    M->>DB: Load model and verify editor
    M->>DB: Delete model, source XMI, and global index
    C-->>Client: Empty response
```

## POST `/api/{level}/validate`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant XMI as XmiModelImportService
    participant EVL as EpsilonEvlValidator
    Client->>C: token + ad hoc model JSON
    C->>C: Authenticate
    C->>M: validate(level, model)
    M->>XMI: Export in-memory EMF resource
    M->>EVL: Execute level EVL entry profile
    EVL-->>M: Structured violations and diagnostics
    M->>M: Add level-specific JSON checks
    M-->>C: ValidationResult
    C-->>Client: ValidationResult
```

## POST `/api/{level}/{id}/validate`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant EVL as EpsilonEvlValidator
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + level + model id
    C->>M: validate(user, level, id)
    M->>DB: Load model and source XMI sidecar
    alt cached validation exists
        M-->>C: Cached ValidationResult
    else source XMI exists
        M->>EVL: Validate XMI
        opt recoverable stale-sidecar issue
            M->>M: Regenerate XMI from JSON and revalidate
            M->>DB: Attach repaired XMI
        end
    else legacy JSON-only record
        M->>M: Convert JSON in memory and validate
    end
    M-->>C: ValidationResult
    C-->>Client: ValidationResult
```

## POST `/api/{level}/export`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant XMI as XmiModelImportService
    Client->>C: token + name + ad hoc model + format
    C->>C: Authenticate
    C->>M: exportModel(level, model, format)
    alt format is json
        M->>M: Pretty-print JSON
    else format is xmi
        M->>XMI: Hydrate references and export XMI
    end
    M-->>C: Export bytes
    C-->>Client: JSON/XML attachment
```

## POST `/api/{level}/{id}/export`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant DB as PlatformStore / PostgreSQL
    participant XMI as XmiModelImportService
    Client->>C: token + model id + format
    C->>M: exportModel(user, level, id, format)
    M->>DB: Load authorized model
    alt XMI requested and sidecar exists
        DB-->>M: Stored canonical XMI
    else JSON or missing sidecar
        M->>XMI: Export from model JSON when needed
    end
    M-->>C: Export bytes
    C-->>Client: JSON/XML attachment
```

## POST `/api/{level}/import`

```mermaid
sequenceDiagram
    actor Client
    participant C as ModelController
    participant M as ModelService
    participant XMI as XmiModelImportService
    participant P as ProjectService
    Client->>C: multipart file + format + projectId
    C->>C: Enforce upload size
    C->>M: importModelFromStream(...)
    M->>P: Verify project editor access
    M->>M: Bounded stream read
    alt JSON
        M->>M: Parse, normalize, and validate JSON
    else XMI
        M->>XMI: Import XMI to model JSON
        M->>M: Attach encoded source-XMI hint
    end
    M-->>C: ImportResult(name, modelJson, issues)
    C-->>Client: ImportResult
```
