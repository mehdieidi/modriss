# Project Description

This project is being developed as part of an academic thesis. It is an AI-assisted, model-driven,
low-code platform for serverless software development (software systems that follow the serverless
computing paradigm and serverless architectural style).

The platform enables users to model, transform, generate, and refine serverless applications
through formal modeling languages, automated model transformations, code generation, and AI-assisted
modeling support. The final goal is to produce deployable AWS serverless artifacts while maintaining
academic rigor, technical quality, and production-readiness.

## Model-Driven Engineering Approach

The project follows a model-driven engineering approach based on three modeling levels:

1. Computation-Independent Model (CIM)
2. Platform-Independent Model (PIM)
3. Platform-Specific Model (PSM)

Each modeling level is defined using a formal Domain-Specific Modeling Language (DSML). These DSMLs
are specified through Ecore metamodels, written in the Emfatic language.

### CIM Level

The CIM level represents computation-independent structural and behavioral models. It captures
concepts such as system requirements, domain concepts, business logic, and high-level behavioral
aspects without committing to a software-based solution.

### PIM Level

The PIM level represents platform-independent structural and behavioral models of a serverless
architecture. It abstracts serverless concepts independently of a specific cloud provider while
preserving the architectural and behavioral characteristics required for serverless software
systems.

### PSM Level

The PSM level represents platform-specific structural and behavioral models for AWS serverless
architecture. It refines the platform-independent serverless model into AWS-specific serverless
components and structures.

## Model Transformations and Code Generation

The platform defines formal model-to-model transformations and model-to-text transformations.

### Model-to-Model Transformations

Formal model-to-model transformations are defined for:

- CIM to PIM
- PIM to PSM

These transformations are semi-automated. The transformation process automatically generates the
target model, while still allowing the developer to review, refine, and complete missing or
ambiguous parts. This ensures both automation and developer control.

The model transformations are implemented using the Eclipse Epsilon Transformation Language (ETL).

### Model-to-Text Transformations

Formal model-to-text transformations are defined to generate final deployable artifacts from the PSM
level. These generated artifacts target AWS serverless ecosystem and may include configuration
files, infrastructure definitions (IaC), source code, and other required project files.

The generated artifacts are intended to be reviewed, refined, and deployed by the developer.

The code generation process is implemented using the Eclipse Epsilon Generation Language (EGL) and
EGL Coordination Language (EGX).

### Constraint Validation

Model validation constraints are formally defined to ensure that models conform to the
corresponding metamodels and predefined static constraint rules.

Constraint validation is implemented using the Eclipse Epsilon Validation Language (EVL).

## Backend Architecture

The backend is implemented using Java, Maven, and Spring Boot. It exposes APIs that allow the
frontend to interact with the platform.

The backend is responsible for typical server-side operations such as project management,
persistence, authentication-related workflows, and coordination of user actions. In addition, it
handles model-driven engineering operations by executing and orchestrating Eclipse Epsilon scripts
for model validation, model transformation, and code generation.

The backend follows a monolithic but modular architecture. It is designed to remain maintainable,
extensible, and modifiable.

## AI-Assisted Modeling

The platform includes an AI modeling assistant integrated into the frontend editor and implemented
in backend.

The assistant is presented as a chatbot icon on the modeling canvas. When the user clicks the icon,
a chatbot window appears inside the editor. Through this interface, the user can interact with the
assistant using natural language.

The AI assistant is designed to understand the current state of the model being edited. It is also
aware of the formal metamodel definitions and validation constraints. Based on user prompts, the
assistant can create, modify, and refine model elements while ensuring that its outputs conform to
the defined metamodels and EVL constraints.

Instead of manually creating every element, the user can describe the intended model changes in
natural language, and the assistant can generate or update the model accordingly.

The assistant’s changes are reflected visually on the frontend canvas in real time using
WebSocket-based communication. The chat interaction itself is also designed to operate in real time.

The AI assistant is implemented using Spring AI.

## Frontend Architecture

The frontend is web-based. The current implementation plan is to use plain HTML, CSS, and
JavaScript.

Users can log in to the platform, create projects, and enter the modeling editor. The editor
provides an infinite modeling canvas where users can create and manipulate models visually.

The editor includes a modeling palette containing the elements available for the current modeling
level. Users can drag and drop elements onto the canvas and create relationships between them by
drawing legal connections.

The editor is organized into three modeling tabs:

- CIM
- PIM
- PSM

Each tab has its own modeling palette according to the formal metamodel defined for that level.

A generation action is available in the editor. When the user clicks the generate button, the
platform performs the appropriate transformation depending on the active tab:

- From CIM, it generates the corresponding PIM model.
- From PIM, it generates the corresponding PSM model.
- From PSM, it generates the final deployable AWS serverless artifacts.

The platform also includes an artifacts view. This view is designed to resemble an IDE, similar to
Visual Studio Code. It allows the user to browse generated project files and folders, open and edit
files, save changes, and export the complete generated project as a ZIP file for deployment.

## Planned Future Features

Additional features may be added in later stages of the project, including:

- Impact analysis
- Reverse engineering
- Real-time collaboration
- Deployment automation
- Versioning and change tracking
- Other cloud providers

## Academic and Production Goals

This project is part of an academic thesis and is intended to support future academic publications.
Therefore, the platform is designed with academic rigor, formal modeling foundations, and clear
engineering methodology.

At the same time, the project is intended to be released as a production-ready system. As a result,
the implementation must follow high-quality software engineering practices, ensuring
maintainability and extensibility.

The overall goal is to provide a novel, rigorous, and practical platform that combines model-driven
engineering, low-code development, serverless architecture, and AI-assisted modeling into a unified
environment for developing applications that follow the serverless paradigm.