<p align="center">
  <img src="apps/frontend/assets/icons/logo/modriss-favicon.svg" alt="MODRISS" width="96">
</p>

# MODRISS

MODRISS is an ongoing MSc thesis research project at the Methodology Engineering Laboratory,
Department of Computer Engineering, Sharif University of Technology. It studies how serverless
software development can be supported by an explicit development process, formal modeling
languages, model transformations, and code generation.

This repository contains the proposed methodology and its web-based research platform. The current
platform-specific target is AWS serverless architecture.

## Research context

Serverless computing reduces direct server-management work while creating concerns across
architecture, configuration, integration, operations, and deployment. MODRISS investigates a
model-driven methodology that connects domain understanding, provider-independent serverless
architecture, AWS-specific design, and generated implementation artifacts in a traceable path.

The methodology has two connected parts:

- **Development process**: lifecycle phases, roles, tasks, work products, decision gates,
  iteration, risk management, and umbrella activities for serverless projects.
- **Modeling framework**: DSMLs, metamodels, semantics, constraints, concrete syntax,
  transformations, code generation, and platform support.

## Contributions and capabilities

| Area                 | MODRISS contribution                                                                                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Development process  | An iterative and incremental process definition links modeling work with lifecycle activities, roles, tasks, artifacts, review points, and supporting practices.                     |
| Modeling framework   | Formal DSMLs describe the problem domain, platform-independent serverless architecture, and AWS-specific serverless design.                                                          |
| Language engineering | Ecore metamodels specify abstract syntax. EVL rules express well-formedness and semantic validation. Browser editors provide concrete notation.                                      |
| Model refinement     | ETL transformations support semi-automated CIM-to-PIM and PIM-to-AWS-PSM refinement. Modelers review and complete decisions that cannot be resolved automatically.                   |
| Artifact generation  | EGX and EGL templates generate a reviewable AWS project baseline, including infrastructure, Go handlers, contracts, tests, workflows, scripts, documentation, and trace information. |
| AI-assisted modeling | A conversational assistant supports selected CIM and PIM modeling activities using the live metamodel as its structural contract.                                                    |

## Modeling path

```text
CIM ── ETL refinement ──▶ PIM ── ETL refinement ──▶ AWS PSM ── EGX/EGL generation ──▶ AWS project baseline
 │                          │                         │                                      │
 business intent             serverless architecture   AWS resource design                    review, completion, test, deploy
```

- **CIM, Computation-Independent Model**: domain concepts, actors, capabilities, events,
  processes, policies, governance, and requirements without a software or cloud commitment.
- **PIM, Platform-Independent Model**: serverless services, functions, contracts, data, events,
  workflows, integrations, security, policies, and deployment concerns.
- **AWS PSM, Platform-Specific Model**: AWS resources and configuration for compute, APIs,
  storage, messaging, identity, networking, and observability.

Transformations support systematic refinement. Generated models and artifacts remain subject to
human review and project-specific decisions.

## Run the platform

### Prerequisites

- Docker with Compose support
- A modern browser
- Several gigabytes of available memory and disk space

Java 17+, Maven 3.9+, Python 3, and Node.js are required only for source development. AWS CLI,
SAM CLI, and Go are needed when verifying or deploying generated projects.

From the repository root:

```bash
./scripts/dev.sh
```

| Service                        | Address                                 |
| ------------------------------ | --------------------------------------- |
| Modeling workbench             | <http://127.0.0.1:8082>                 |
| Backend health                 | <http://127.0.0.1:8080/api/health>      |
| API explorer                   | <http://127.0.0.1:8080/swagger-ui.html> |
| Research landing page          | <http://127.0.0.1:8083>                 |
| Container logs                 | <http://127.0.0.1:9999>                 |
| AWS emulator, Floci by default | <http://127.0.0.1:4566>                 |

For a first workflow, register a user, create a project, and open the CIM workspace. Create a
model or import [`mde/samples/cim.xmi`](mde/samples/cim.xmi), validate it, then generate and review
the PIM and AWS PSM models. From AWS PSM, select **Generate Artifacts** and inspect or download the
generated project. The [climate-relief sample](mde/samples/climate-relief-grants/) offers a broader
end-to-end case study.

See the [quickstart](docs/public-docs/docs/getting-started/quickstart.md) and
[local-development guide](docs/public-docs/docs/getting-started/local-development.md).

## Assistant and validation boundary

The LLM-based assistant is disabled by default. Enable it only after configuring a supported
provider, credentials, operating limits, and organizational policy in [`.env.example`](.env.example).
It supports CIM and PIM sessions; PSM chatbot sessions are outside the implemented scope.

Assistant-generated actions, patches, proposals, checkpoints, and model output are gated only by
structural Ecore/EMF conformance before they are applied or committed. EVL semantic validation is
deliberately outside assistant apply, repair, and commit paths. It is available through explicit
model-validation workflows. Structural conformance alone does not establish that a model is
complete, suitable, or deployable.

Read the [AI assistant guide](docs/public-docs/docs/guides/ai-assistant.md) before enabling it.

## Production and research-use notice

MODRISS is under active research and development. It includes formal languages, validation
profiles, transformation and generation tooling, persistence, observability material, and
deployment guidance. Some capabilities continue to evolve; see the
[status and roadmap](docs/public-docs/docs/contributing/status-roadmap.md).

Generated projects are engineering baselines. Before production deployment, the responsible team
must review generated code and infrastructure, complete recorded manual actions and protected
regions, test the artifact, manage secrets, establish security and operational controls, and make
deployment-specific decisions. The project documentation does not replace those responsibilities.

For operations guidance, see [deployment](docs/public-docs/docs/operations/deployment.md),
[security](docs/public-docs/docs/operations/security.md), and
[testing](docs/public-docs/docs/operations/testing.md).

## Repository map

```text
apps/           Spring Boot backend, browser modeling workbench, and research landing site
mde/            metamodels, validation rules, transformations, generation templates, methodology, and samples
packages/java/  platform modules, MDE runners, assistant, and PostgreSQL adapter
tools/          standalone CLIs for Ecore compilation, validation, transformation, and generation
docs/           public documentation, API contracts, diagrams, and engineering references
deploy/          Docker Compose stack, container definitions, and deployment scripts
infra/          observability dashboards and alert rules
```

The [repository layout reference](docs/public-docs/docs/reference/repository-layout.md) explains
the module structure and Maven dependency direction. The formal sources of truth are located in
[`mde/metamodels/`](mde/metamodels/), [`mde/validation/`](mde/validation/),
[`mde/transformations/`](mde/transformations/), and
[`mde/generation/awspsm-to-artifacts/`](mde/generation/awspsm-to-artifacts/).

## Documentation

- [Public documentation](docs/public-docs/)
- [Model-driven engineering concepts](docs/public-docs/docs/concepts/model-driven-engineering.md)
- [End-to-end modeling methodology](docs/public-docs/docs/guides/end-to-end-modeling-methodology.md)
- [REST API reference](docs/public-docs/docs/reference/rest-api.md)
- [OpenAPI contract](docs/api/openapi/openapi.yaml)
- [Generated artifact guide](docs/public-docs/docs/guides/generated-artifacts.md)
- [Architecture documentation](docs/public-docs/docs/architecture/system.md)

## Research output

Eidi, M., and Ramsin, R. (2026). _Model-Driven Approaches for Serverless Software Development:
Evaluation and Future Directions_. In the 14th International Conference on Model-Based Software and
Systems Engineering (MODELSWARD 2026), pp. 560–567.
[https://doi.org/10.5220/0014634200004058](https://doi.org/10.5220/0014634200004058)

The paper evaluates selected model-driven approaches for serverless and microservices development
with a process-centered template. It provides part of the research basis for the MODRISS
methodology.

## Project contacts

- [Mehdi Eidi, researcher homepage](https://mehdieidi.github.io/)
- [University profile](https://www.sharif.ir/en/web/me_ce/w/mehdi-eidi)
- [Methodology Engineering Laboratory, Sharif University of Technology](https://www.sharif.ir/en/web/me_ce/home)

## Contributing

Contributions are welcome where they support the research and engineering goals of MODRISS. Read
[CONTRIBUTING.md](CONTRIBUTING.md) before proposing a change. Changes to a DSML must remain aligned
across its metamodel, validation rules, transformations, visual notation, API behavior, samples,
and documentation.

## License

MODRISS is released under the [MIT License](LICENSE). Copyright © 2026 Mehdi Eidi.
