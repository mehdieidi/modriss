# Transformation, Job, and Artifact API Sequences

## POST `/api/transformations/cim-to-pim`

```mermaid
sequenceDiagram
    actor Client
    participant C as TransformationController
    participant T as TransformationService
    participant M as ModelService
    participant ETL as EpsilonEtlExecutor
    participant DB as PlatformStore / PostgreSQL
    Client->>C: sourceModelId + expectedRevision
    C->>T: cimToPim(user, sourceModelId, revision)
    T->>M: Load locked CIM source and verify revision
    T->>ETL: Execute CimToPimDefaults in temp workspace
    ETL-->>T: Generated PIM XMI + report
    T->>T: Synchronize fresh PIM with downstream Working/Base
    T->>DB: Persist Working + raw Base, or pending conflict session
    T-->>C: MdeJobRecord (async worker result)
    C-->>Client: 202 JobResponse + Location header
```

## POST `/api/transformations/pim-to-psm`

```mermaid
sequenceDiagram
    actor Client
    participant C as TransformationController
    participant T as TransformationService
    participant M as ModelService
    participant ETL as EpsilonEtlExecutor
    participant DB as PlatformStore / PostgreSQL
    Client->>C: sourceModelId + expectedRevision
    C->>T: pimToPsm(user, sourceModelId, revision)
    T->>M: Load locked PIM source and verify revision
    T->>ETL: Execute PimToAwsPsmDefaults in temp workspace
    ETL-->>T: Generated AWS PSM XMI + report
    T->>T: Synchronize fresh PSM with downstream Working/Base
    T->>DB: Persist Working + raw Base, or pending conflict session
    T-->>C: MdeJobRecord (async worker result)
    C-->>Client: 202 JobResponse + Location header
```

## POST `/api/transformations/psm-to-artifact`

```mermaid
sequenceDiagram
    actor Client
    participant C as TransformationController
    participant T as TransformationService
    participant M as ModelService
    participant EGX as EpsilonEgxGenerator
    participant A as ArtifactService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: sourceModelId + expectedRevision
    C->>T: psmToArtifact(user, sourceModelId, revision)
    T->>M: Load locked PSM and verify revision
    T->>EGX: Generate bounded artifact workspace
    EGX-->>T: Generated files + report
    T->>T: Validate file count, size, and completeness
    T->>A: create(projectId, artifact name, files)
    A->>DB: Insert artifact and files
    C-->>Client: 202 JobResponse + Location header
```

## GET `/api/transformations/jobs/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as TransformationController
    participant J as MdeJobService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + job id
    C->>J: get(user, id)
    J->>DB: Load job through global index
    J->>P: Verify project access
    J-->>C: MdeJobRecord
    C-->>Client: MdeJobRecord
```

## POST `/api/transformations/jobs/{id}/cancel`

```mermaid
sequenceDiagram
    actor Client
    participant C as TransformationController
    participant J as MdeJobService
    participant Pool as Job Executor
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + job id
    C->>J: cancel(user, id)
    J->>J: Verify editor access
    alt already terminal
        J-->>C: Existing job
    else queued or running
        J->>Pool: Cancel Future when present
        J->>DB: Persist CANCELLED status and diagnostic
        J-->>C: Cancelled job
    end
    C-->>Client: MdeJobRecord
```

## GET `/api/artifact?projectId=...`

```mermaid
sequenceDiagram
    actor Client
    participant C as ArtifactController
    participant A as ArtifactService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + projectId
    C->>A: list(user, projectId)
    A->>P: Verify project access
    A->>DB: List artifacts ordered by update time
    A-->>C: ArtifactRecord[]
    C-->>Client: ArtifactRecord[]
```

## GET `/api/artifact/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ArtifactController
    participant A as ArtifactService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + artifact id
    C->>A: get(user, id)
    A->>DB: Load artifact and files
    A->>P: Verify project access
    A-->>C: ArtifactRecord
    C-->>Client: ArtifactRecord
```

## GET `/api/artifact/{id}/file`

```mermaid
sequenceDiagram
    actor Client
    participant C as ArtifactController
    participant A as ArtifactService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + artifact id + path
    C->>A: readFile(user, artifactId, path)
    A->>A: Normalize path; reject absolute/traversal
    A->>DB: Load authorized artifact file
    A-->>C: Text content
    C-->>Client: text/plain
```

## PUT `/api/artifact/{id}/files`

```mermaid
sequenceDiagram
    actor Client
    participant C as ArtifactController
    participant A as ArtifactService
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + path + content
    C->>A: updateFile(user, artifactId, path, content)
    A->>A: Normalize path and enforce bounds
    A->>P: Require project editor
    A->>DB: Upsert file and update artifact timestamp
    A-->>C: Updated ArtifactRecord
    C-->>Client: ArtifactRecord
```

## GET `/api/artifact/{id}/download`

```mermaid
sequenceDiagram
    actor Client
    participant C as ArtifactController
    participant A as ArtifactService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + artifact id
    C->>A: get(user, id)
    C->>A: zip(user, id)
    A->>DB: Load artifact files
    A->>A: Build ZIP with normalized paths
    A-->>C: ZIP bytes
    C-->>Client: application/zip attachment
```

## Internal Queued Job Submission

This is a major implemented service flow, although no controller currently invokes the submit
methods.

```mermaid
sequenceDiagram
    participant Caller
    participant J as MdeJobService
    participant DB as PlatformStore / PostgreSQL
    participant Pool as Bounded ThreadPoolExecutor
    participant T as TransformationService
    Caller->>J: submitCimToPim / submitPimToPsm / submitPsmToArtifact
    J->>DB: Persist QUEUED job snapshot
    J->>Pool: Submit run(jobId, user)
    Pool->>DB: Persist RUNNING
    Pool->>T: Execute selected operation with source revision
    alt success
        Pool->>DB: Persist SUCCEEDED and result id
    else failure
        Pool->>DB: Persist FAILED and diagnostic
    end
```
