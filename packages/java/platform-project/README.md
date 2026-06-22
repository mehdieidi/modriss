# platform-project

Project workspace and membership management. Owns `ProjectRecord`, `ProjectMember`, and
`MemberRole` in `platform.project.domain`. `ProjectService` in `platform.project.application`
handles project CRUD, membership, invites, and role checks. Project ZIP export is implemented in
`platform-export` (`ProjectArchiveService`).
