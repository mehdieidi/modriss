# REST API

The Spring Boot backend exposes the frontend API under `/api`.

Interactive docs:

- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`
- Swagger-style UI: `http://127.0.0.1:8080/swagger-ui.html`

Implemented areas:

- Auth: register, login, logout, current user, display-name update
- Projects: create, list, update, delete, members, invites, revoke access
- Models: CIM/PIM/PSM CRUD, JSON import/export, validation response shape
- Transformations: CIM to PIM, PIM to PSM, PSM to artifact
- Artifacts: list, load, read file, save file, ZIP download
- Modeling: palette/config metadata, layout, health

Future areas currently return `501`: chatbot, GitHub, impact analysis, admin workspace editing, and
collaboration.
