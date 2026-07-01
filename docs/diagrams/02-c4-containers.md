# C4 Level 2: Containers

```mermaid
flowchart TB
    user["Person: Modeler"]

    subgraph modless["Modless System"]
        landing["Container: Landing Site<br/>Static HTML/CSS/JS<br/>Product story and case study"]
        frontend["Container: Modeling Frontend<br/>Plain HTML/CSS/JavaScript<br/>AntV G6 or GLSP/Sprotty renderer"]
        glsp["Container: GLSP Diagram Server<br/>Node.js + Eclipse GLSP<br/>Diagram editing sidecar"]
        backend["Container: Spring Boot Backend<br/>Java 17 + Spring MVC/WebSocket/JDBC/AI<br/>API, orchestration, validation, transformation"]
        postgres[("Container: PostgreSQL + pgvector<br/>Application records, assistant memory, embeddings")]
        mde["Container: MDE Repository Assets<br/>Emfatic/Ecore, EVL, ETL, EGX/EGL<br/>Formal languages and executable rules"]
        cli["Container: MDE CLI Tools<br/>Picocli shaded JARs<br/>Metamodel compile, validate, transform, generate"]
    end

    provider["External AI Provider"]
    proxy["Optional HTTP/SOCKS Proxy"]

    user -->|"HTTPS/static content"| landing
    user -->|"Browser interaction"| frontend
    frontend -->|"REST JSON, multipart, downloads"| backend
    frontend <-->|"WebSocket when renderer is glsp-sprotty"| glsp
    glsp -->|"Load model JSON and CVS config"| backend
    frontend <-->|"WebSocket or SSE assistant events"| backend
    backend -->|"Spring JDBC"| postgres
    backend -->|"Reads executable MDE assets"| mde
    cli -->|"Reads executable MDE assets"| mde
    backend -->|"Spring AI calls"| proxy
    proxy --> provider
```

## Container Protocols

```mermaid
flowchart LR
    browser["Browser frontend"]
    glsp["GLSP diagram server"]
    mvc["Spring MVC controllers"]
    ws["WebSocket handler"]
    sse["SSE emitter hub"]
    services["Application and assistant services"]
    jdbc["JDBC / PlatformStore adapter"]
    db[("PostgreSQL / pgvector")]
    files["MDE files and temporary workspaces"]
    epsilon["Epsilon and EMF runtimes"]

    browser -->|"REST: JSON, multipart, ZIP/text downloads"| mvc
    browser <-->|"WS /ws/chatbot/sessions/{id}"| ws
    browser <--|"SSE /api/chatbot/sessions/{id}/events"| sse
    browser <-->|"WS diagram editing when renderer is glsp-sprotty"| glsp
    glsp -->|"Load model JSON and CVS config"| mvc
    mvc --> services
    ws --> sse
    services --> jdbc --> db
    services --> epsilon
    epsilon --> files
```
