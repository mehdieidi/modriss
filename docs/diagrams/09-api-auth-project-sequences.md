# Auth and Project API Sequences

All protected routes first resolve `X-Auth-Token` through `AuthSupport` or `AuthService`.

## POST `/api/auth/register`

```mermaid
sequenceDiagram
    actor Client
    participant C as AuthController
    participant S as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: email, password, displayName
    C->>S: register(...)
    S->>DB: Check normalized email uniqueness
    S->>S: Generate salt and password hash
    S->>DB: Insert user and auth session
    S-->>C: AuthResult(token, user)
    C-->>Client: AuthResponse
```

## POST `/api/auth/login`

```mermaid
sequenceDiagram
    actor Client
    participant C as AuthController
    participant S as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: email, password
    C->>S: login(...)
    S->>DB: Load user by normalized email
    S->>S: Verify salted password hash
    S->>DB: Insert new auth session
    S-->>C: AuthResult(token, user)
    C-->>Client: AuthResponse
```

## GET `/api/auth/me`

```mermaid
sequenceDiagram
    actor Client
    participant C as AuthController
    participant S as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: X-Auth-Token
    C->>S: requireUser(token)
    S->>DB: Load session and user
    S->>S: Reject expired session
    S-->>C: UserRecord
    C-->>Client: UserDto
```

## PUT `/api/auth/me`

```mermaid
sequenceDiagram
    actor Client
    participant C as AuthController
    participant S as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + displayName
    C->>S: updateDisplayName(token, displayName)
    S->>DB: Load session and user
    S->>DB: Update user and membership snapshots
    S-->>C: Updated UserRecord
    C-->>Client: UserDto
```

## POST `/api/auth/logout`

```mermaid
sequenceDiagram
    actor Client
    participant C as AuthController
    participant S as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: X-Auth-Token
    C->>S: logout(token)
    S->>DB: Delete auth session
    C-->>Client: 204 / empty response
```

## GET `/api/projects`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant A as AuthSupport
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: X-Auth-Token
    C->>A: user(token)
    A-->>C: UserRecord
    C->>P: list(user)
    P->>DB: List owned/member projects
    P-->>C: Accessible ProjectRecords
    C-->>Client: ProjectRecord[]
```

## POST `/api/projects`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant A as AuthSupport
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + name + description
    C->>A: user(token)
    C->>P: create(owner, name, description)
    P->>DB: Insert project
    P->>DB: Insert OWNER membership
    P-->>C: ProjectRecord
    C-->>Client: ProjectRecord
```

## GET `/api/projects/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project id
    C->>P: get(auth.user(token), id)
    P->>DB: Load project and members
    P->>P: Verify owner or member access
    P-->>C: ProjectRecord
    C-->>Client: ProjectRecord
```

## GET `/api/projects/{id}/download`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project id
    C->>P: get(user, id)
    C->>P: zip(user, id)
    P->>DB: Read project, models, artifacts, artifact files
    P->>P: Build bounded ZIP archive
    P-->>C: ZIP bytes
    C-->>Client: application/zip attachment
```

## PUT `/api/projects/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project fields + activeModelIds
    C->>P: update(user, id, ...)
    P->>DB: Load project
    P->>P: Require editor permission
    P->>DB: Update project and active model selections
    P-->>C: Updated ProjectRecord
    C-->>Client: ProjectRecord
```

## DELETE `/api/projects/{id}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project id
    C->>P: delete(user, id)
    P->>DB: Load project
    P->>P: Require owner
    P->>DB: Delete project; cascades dependent rows
    C-->>Client: Empty response
```

## GET `/api/projects/{id}/members`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project id
    C->>P: members(user, id)
    P->>DB: Load project and memberships
    P->>P: Verify project access
    P-->>C: ProjectMember[]
    C-->>Client: ProjectMember[]
```

## POST `/api/projects/{id}/invite`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant Auth as AuthService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + email + role
    C->>P: invite(user, projectId, email, role)
    P->>DB: Load project and inviter membership
    P->>P: Require owner
    P->>Auth: findByEmail(email)
    P->>DB: Upsert project membership snapshot
    P-->>C: ProjectMember
    C-->>Client: ProjectMember
```

## DELETE `/api/projects/{id}/members/{userId}`

```mermaid
sequenceDiagram
    actor Client
    participant C as ProjectController
    participant P as ProjectService
    participant DB as PlatformStore / PostgreSQL
    Client->>C: token + project id + member id
    C->>P: revoke(requester, projectId, userId)
    P->>DB: Load project and requester membership
    P->>P: Require owner and reject owner removal
    P->>DB: Delete membership
    C-->>Client: Empty response
```
