# C4 Level 2: Containers

```mermaid
flowchart TB
    user["Person: Modeler"]

    subgraph varka["Varka System"]
        landing["Container: Landing Site<br/>Static HTML/CSS/JS<br/>Product story and case study"]
        frontend["Container: Modeling Frontend<br/>Plain HTML/CSS/JavaScript<br/>AntV G6 renderer"]
        backend["Container: Spring Boot Backend<br/>Java 17 + Spring MVC/JDBC/AI<br/>API, orchestration, validation, transformation"]
        postgres[("Container: PostgreSQL<br/>Application records, assistant memory, durable turns")]
        mde["Container: MDE Repository Assets<br/>Emfatic/Ecore, EVL, ETL, EGX/EGL<br/>Formal languages and executable rules"]
        cli["Container: MDE CLI Tools<br/>Picocli shaded JARs<br/>Metamodel compile, validate, transform, generate"]
    end

    provider["External AI Provider"]
    proxy["Optional HTTP/SOCKS Proxy"]

    user -->|"HTTPS/static content"| landing
    user -->|"Browser interaction"| frontend
    frontend -->|"REST JSON, multipart, downloads"| backend
    frontend <-->|"Authenticated SSE assistant events"| backend
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
    mvc["Spring MVC controllers"]
    sse["SSE emitter hub"]
    services["Application and assistant services"]
    jdbc["JDBC / PlatformStore adapter"]
    db[("PostgreSQL")]
    files["MDE files and temporary workspaces"]
    epsilon["Epsilon and EMF runtimes"]

    browser -->|"REST: JSON, multipart, ZIP/text downloads"| mvc
    browser <--|"SSE /api/chatbot/turns/{turnId}/events"| sse
    mvc --> services
    services --> jdbc --> db
    services --> epsilon
    epsilon --> files
```
