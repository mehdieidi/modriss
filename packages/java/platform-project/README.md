# platform-project

Project workspace and membership management. Owns `ProjectRecord` and `ProjectMember` in
`platform.project.domain`. `ProjectService` in `platform.project.application` handles project CRUD,
membership, invites, custom role labels, and owner checks. Project ZIP export is implemented in
`platform-export` (`ProjectArchiveService`).
