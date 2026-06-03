const dsmlCards = [
  {
    tag: "CIM",
    title: "Computation-independent modeling",
    body:
        "The CIM language captures business intent before any provider or runtime decision appears. It gives the workbench concepts for requirements, goals, KPIs, stakeholders, actors, roles, capabilities, bounded contexts, glossary terms, domain data, behavior, processes, policies, governance, transformation risk, and production readiness.",
    points: [
      "Root: CIMModel with trace and readiness containers.",
      "Domain: entities, value objects, relationships, aggregates, information items, classifications.",
      "Behavior: commands, queries, business events, errors, conditions, business processes, human tasks, policies, decision tables.",
      "Governance: NFRs, security constraints, privacy constraints, compliance constraints, risks, assumptions, hotspots."
    ]
  },
  {
    tag: "PIM",
    title: "Provider-independent serverless architecture",
    body:
        "The PIM language turns clean business semantics into serverless architecture without selecting AWS resources too early. It models deployable services, functions, APIs, event channels, workflows, data stores, object stores, external adapters, policies, principals, configuration, and secrets.",
    points: [
      "Root: PIMModel with providerIndependent=true, architecture style, validation profile, trace model, and readiness.",
      "Architecture: services, deployment units, environments, implementation profile, functions, APIs, workflows, triggers, schedules, flows.",
      "Contracts: schemas, event types, function contracts, API contracts, decision models, access patterns.",
      "Control: identity providers, principals, permissions, security policy, resilience, observability, cost, retention, idempotency."
    ]
  },
  {
    tag: "AWS PSM",
    title: "AWS-specific platform model",
    body:
        "The AWS PSM language refines the provider-independent design into concrete deployment structures. It models AWS stages, SAM stacks, resources, security baseline, naming and tagging policy, relationship views, generated documents, trace links, and readiness decisions.",
    points: [
      "Compute and API: Lambda, code config, event sources, permissions, function URLs, API Gateway APIs, routes, integrations, stages, authorizers.",
      "Data and events: DynamoDB, S3, SQS, SNS, EventBridge buses, rules, schedules, pipes, API destinations.",
      "Workflow and identity: Step Functions, ASL, retry/catch/choice rules, Cognito pools, clients, domains, identity pools.",
      "Operations: IAM, KMS, Secrets Manager, SSM, CloudWatch logs, alarms, dashboards, VPC attachments, security groups."
    ]
  }
];

const transformationCards = [
  {
    meta: "CIM -> PIM",
    title: "Root scaffolding and readiness",
    body:
        "Creates the PIM root, trace model, readiness assessment, default environments, implementation profile, KPI readiness checks, and stakeholder manual decisions.",
    points: [
      "Rules include CIMModel2PIMModel, Requirement2BusinessRule, KPI2ReadinessCheck.",
      "Default implementation choices are generated as review-required, not silently final.",
      "Readiness status moves through review, transformation-ready, deployment-ready, and production-ready."
    ]
  },
  {
    meta: "CIM -> PIM",
    title: "Boundaries, security, behavior, and data",
    body:
        "Maps bounded contexts and capabilities to services, actors and roles to principals, external systems to adapters, entities and value objects to schemas, aggregates to data stores, and commands or queries to functions and routes.",
    points: [
      "Security constraints become provider-independent authentication and authorization policies.",
      "Privacy and classification inputs become data protection, retention, and compliance policies.",
      "Manual decisions record ambiguous service boundaries, relationship storage strategy, route authorization, and data protection obligations."
    ]
  },
  {
    meta: "PIM -> AWS PSM",
    title: "AWS realization with traceable decisions",
    body:
        "Realizes provider-independent architecture as AWS resources while keeping production-risk decisions attached to the model. The transformation creates AWS stages, SAM stacks, Lambda functions, API Gateway resources, DynamoDB, S3, SQS, SNS, EventBridge, Step Functions, Cognito, IAM, KMS, SSM, Secrets Manager, and CloudWatch constructs.",
    points: [
      "Rules include Function2AwsLambdaFunction, Api2HttpApi, DataStore2DynamoDbTable, Workflow2StepFunctionStateMachine.",
      "Relationship views make generated integrations explicit, such as API Gateway to Lambda or SQS to Lambda.",
      "Manual decisions capture missing regions, authorizers, DLQs, ordering keys, alarm thresholds, secret rotation, VPC details, and IAM wildcard review."
    ]
  },
  {
    meta: "AWS PSM -> Artifacts",
    title: "Model-to-text generation with protected regions",
    body:
        "The EGX/EGL generator plans each artifact, records trace metadata, writes generated files, preserves developer-owned regions where appropriate, and emits reports that explain generation output and export gates.",
    points: [
      "Generated outputs include SAM templates, Go handlers, OpenAPI, ASL, schemas, scripts, tests, CI workflows, docs, runbooks, security reports, and trace JSON.",
      "Protected regions preserve business logic, custom validation, fixtures, assertions, deployment notes, and project-specific documentation.",
      "Manual action reports and validation scripts help prevent unresolved production blockers from disappearing."
    ]
  }
];

const artifactRows = [
  {
    mark: "IAC",
    title: "SAM and CloudFormation infrastructure",
    body:
        "Generated stack templates, SAM globals, environment files, samconfig.toml, parameters, outputs, security resources, API Gateway, Lambda, DynamoDB, S3, messaging, events, workflows, identity, and observability."
  },
  {
    mark: "SRC",
    title: "Lambda handlers and shared runtime support",
    body:
        "Go handlers with protected regions, plus shared logging, tracing, metrics, validation, errors, config, idempotency, event publishing, and data-access helpers."
  },
  {
    mark: "API",
    title: "Contracts, schemas, events, and ASL",
    body:
        "OpenAPI documents, JSON schemas, generated event fixtures, catalog schemas, Step Functions ASL JSON, and structured documents derived from model contracts and business rules."
  },
  {
    mark: "TEST",
    title: "Generated verification surface",
    body:
        "Contract tests, unit tests, integration tests, event tests, workflow tests, e2e tests, security tests, and curated fixture documentation with protected regions for domain-specific assertions."
  },
  {
    mark: "OPS",
    title: "Operations, deployment, and runbooks",
    body:
        "Makefile targets, validate/build/test/deploy/package scripts, local API and invocation scripts, deployment docs, operations docs, incident response runbook, rollback runbook, and CI workflows."
  },
  {
    mark: "TRACE",
    title: "Traceability and production gates",
    body:
        "Artifact trace JSON, model trace JSON, protected region reports, manual action reports, IAM rationale, security review, generation reports, and model summaries."
  }
];

function escapeHtml(value) {
  return String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll("\"", "&quot;");
}

function renderList(points) {
  return `<ul>${points.map((point) => `<li>${escapeHtml(point)}</li>`).join(
      "")}</ul>`;
}

function renderDsmlCards() {
  const target = document.querySelector('[data-render="dsml"]');
  if (!target) {
    return;
  }
  target.innerHTML = dsmlCards
  .map((card) => `
      <article class="info-card reveal">
        <span class="info-card__tag">${escapeHtml(card.tag)}</span>
        <h3>${escapeHtml(card.title)}</h3>
        <p>${escapeHtml(card.body)}</p>
        ${renderList(card.points)}
      </article>
    `)
  .join("");
}

function renderRuleCards() {
  const target = document.querySelector('[data-render="rules"]');
  if (!target) {
    return;
  }
  target.innerHTML = transformationCards
  .map((card) => `
      <article class="rule-card reveal">
        <p class="rule-card__meta">${escapeHtml(card.meta)}</p>
        <h3>${escapeHtml(card.title)}</h3>
        <p>${escapeHtml(card.body)}</p>
        ${renderList(card.points)}
      </article>
    `)
  .join("");
}

function renderArtifacts() {
  const target = document.querySelector('[data-render="artifacts"]');
  if (!target) {
    return;
  }
  target.innerHTML = artifactRows
  .map((row) => `
      <article class="artifact-row reveal">
        <span class="artifact-row__mark" aria-hidden="true">${escapeHtml(
      row.mark)}</span>
        <div>
          <h3>${escapeHtml(row.title)}</h3>
          <p>${escapeHtml(row.body)}</p>
        </div>
      </article>
    `)
  .join("");
}

export function hydrateContent() {
  renderDsmlCards();
  renderRuleCards();
  renderArtifacts();
}

export function initStaticInteractions() {
  const header = document.querySelector("[data-site-header]");
  const updateHeader = () => {
    document.body.classList.toggle("is-scrolled", window.scrollY > 8);
  };
  updateHeader();
  window.addEventListener("scroll", updateHeader, {passive: true});

  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener("click", () => {
      const href = anchor.getAttribute("href");
      if (href === "#top" && header) {
        header.blur?.();
      }
    });
  });
}
