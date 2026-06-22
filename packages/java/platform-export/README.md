# platform-export

Project export and archive packaging. `ProjectArchiveService` in `platform.export.application`
builds ZIP archives of a project's models, artifacts, and related metadata. Used by
`ProjectController` (`GET /api/projects/{id}/download`).
