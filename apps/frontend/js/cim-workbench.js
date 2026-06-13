import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { getDefaultNode } from "./diagram.js";
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { modelingElementDefinition, modelingViewDefinition } from "./modeling-config-data.js";
import { markModelDirty } from "./model-save-ui.js";
import { setStatus } from "./status.js";
import {
  activeWorkbenchRepresentation,
  applyWorkbenchEdgeMode,
  bindWorkbenchInteractionShield,
  commitWorkbenchModelChange,
  downloadWorkbenchCsv,
  ensureWorkbenchSurface,
  renderLevelGuidePanel,
  renderWorkbenchBoard,
  renderWorkbenchBoardCard,
  renderWorkbenchControls,
  renderWorkbenchDashboard,
  renderWorkbenchDataTable,
  renderWorkbenchDetail,
  renderWorkbenchMatrixSection,
  renderWorkbenchRegister,
  renderWorkbenchSliceOption,
  renderWorkbenchSliceSelect,
  renderWorkbenchSurfaceLayout,
  renderWorkbenchToolbar,
  setWorkbenchRepresentation,
} from "./workbench-common.js";
import {
  addReferenceValue,
  CIM_ROOT_CONTAINMENTS,
  compactRefLabels,
  elementLabel,
  hasReferenceValue,
  isPresent,
  missingRequiredFeatures,
  refIds,
  removeReferenceValue,
} from "./cim-model-utils.js";

let surface = null;
let bound = false;
let renderDiagramCallback = null;
let renderPaletteCallback = null;
let openAttributePanelCallback = null;
let openConnectionPanelCallback = null;
let searchRenderTimer = 0;
let cimSliceMenuOpen = "";

const CIM_SLICE_KINDS = [
  ["", "All slices"],
  ["context", "Context"],
  ["capability", "Capability"],
  ["actor", "Actor"],
  ["aggregate", "Aggregate"],
  ["process", "Process"],
  ["classification", "Classification"],
  ["lifecycle", "Lifecycle"],
  ["blocking", "Blocking"],
  ["traceType", "Trace type"],
];

function scheduleSearchRender() {
  if (searchRenderTimer) {
    window.clearTimeout(searchRenderTimer);
  }
  searchRenderTimer = window.setTimeout(() => {
    searchRenderTimer = 0;
    renderCimWorkbenchSurface();
  }, 90);
}

const DEFAULT_REPRESENTATION_BY_PROFILE = {
  dashboard: "dashboard",
  requirements: "register",
  capability: "matrix",
  actor: "matrix",
  "bounded-context": "matrix",
  domain: "diagram",
  aggregate: "diagram",
  data: "register",
  eventstorming: "diagram",
  process: "diagram",
  decision: "matrix",
  governance: "matrix",
  readiness: "board",
  traceability: "matrix",
};

const PROFILE_REGISTERS = {
  dashboard: "goals",
  requirements: "requirements",
  capability: "capabilities",
  actor: "actors",
  "bounded-context": "boundedContexts",
  domain: "entities",
  aggregate: "aggregates",
  data: "informationItems",
  eventstorming: "commands",
  process: "processes",
  decision: "decisionTables",
  governance: "requirements",
  readiness: "risks",
  traceability: "traceLinks",
};

const REGISTER_COLUMNS = {
  requirements: [
    "requirementType",
    "sourceType",
    "priority",
    "mandatory",
    "productionBlocking",
    "fitCriterion",
    "supportsGoals",
    "constrains",
    "dependsOn",
    "conflictsWith",
  ],
  goals: ["successCriterion", "businessValue", "priority", "measuredBy", "refinedBy", "owners"],
  kpis: ["metricName", "operator", "targetValue", "unit", "measures"],
  stakeholders: ["stakeholderType", "influenceLevel", "ownsGoals", "providesRequirements"],
  actors: [
    "actorType",
    "trustLevel",
    "playsRoles",
    "issuesCommands",
    "issuesQueries",
    "observesEvents",
    "producedEvents",
    "consumedEvents",
    "exchangedInformation",
  ],
  roles: ["responsibility", "businessPermissionSummary", "privileged", "assignedTo"],
  capabilities: [
    "criticality",
    "supports",
    "owner",
    "realizesRequirements",
    "containsCommands",
    "containsQueries",
    "containsEvents",
    "managesEntities",
    "ownsProcesses",
    "constrainedBy",
  ],
  boundedContexts: [
    "capabilities",
    "entities",
    "commands",
    "queries",
    "events",
    "policies",
    "languageBoundary",
    "ownershipBoundary",
  ],
  glossary: ["term", "definition", "synonyms", "forbiddenSynonyms", "exampleUsage", "context"],
  entities: [
    "identityDescription",
    "identityAttribute",
    "attributes",
    "lifecycleDescription",
    "auditRelevant",
    "owningCapability",
    "lifecycleStates",
    "invariants",
  ],
  valueObjects: [
    "glossaryDefinition",
    "valueType",
    "immutable",
    "attributes",
    "equalityAttributes",
  ],
  relationships: [
    "relationshipType",
    "source",
    "target",
    "sourceRole",
    "sourceMultiplicity",
    "targetRole",
    "targetMultiplicity",
    "ownership",
  ],
  aggregates: [
    "root",
    "members",
    "handledCommands",
    "emittedEvents",
    "strongConsistencyRequired",
    "consistencyExpectation",
    "conflictResolutionPolicy",
    "idempotencyBusinessKey",
  ],
  informationItems: [
    "businessName",
    "type",
    "required",
    "collection",
    "multiplicity",
    "allowedValues",
    "formatHint",
    "minValue",
    "maxValue",
    "pattern",
    "unit",
    "sourceOfTruth",
    "derived",
    "classification",
    "privacyConstraints",
    "complianceConstraints",
    "externallyShared",
    "auditRelevant",
    "searchRelevant",
    "reportingRelevant",
    "retentionRelevant",
  ],
  classifications: [
    "kind",
    "confidentialityLevel",
    "identifiability",
    "regulatoryCategory",
    "encryptionExpected",
    "maskingExpected",
    "minimizationRequired",
    "consentRequired",
    "auditAccessRequired",
  ],
  commands: [
    "commandType",
    "intent",
    "issuedBy",
    "targetCapability",
    "targetAggregate",
    "input",
    "preconditions",
    "expectedEvents",
    "rejectionEvents",
    "possibleErrors",
    "authorizationRequired",
    "authorizationRule",
    "auditRequired",
    "idempotencyBusinessKey",
    "duplicateSubmissionPossible",
    "interactionExpectation",
    "priority",
  ],
  queries: [
    "queryType",
    "intent",
    "issuedBy",
    "targetCapability",
    "input",
    "output",
    "reads",
    "freshnessNeed",
    "containsPersonalData",
    "authorizationRequired",
    "auditRequired",
    "paginationExpectation",
    "filteringExpectation",
    "sortingExpectation",
  ],
  events: [
    "occurredInPastTenseName",
    "payload",
    "affects",
    "causedByPolicies",
    "causedByExternalSystems",
    "consumedByPolicies",
    "consumedByProcesses",
    "consumedByExternalSystems",
    "correlationBusinessKey",
    "causationBusinessKey",
    "externallyVisible",
    "auditRelevant",
    "retentionRelevant",
  ],
  businessErrors: ["errorCode", "userVisibleMessage", "recoverable", "emittedEvents"],
  conditions: [
    "naturalLanguage",
    "expressionLanguage",
    "expression",
    "referencedInformation",
    "referencedConcepts",
  ],
  processes: [
    "processKind",
    "criticality",
    "owningCapability",
    "trigger",
    "longRunning",
    "humanApprovalPossible",
    "compensationExpected",
    "steps",
    "preconditions",
    "postconditions",
    "exceptions",
    "temporalConstraints",
  ],
  policies: [
    "policyType",
    "naturalLanguageRule",
    "triggeredBy",
    "guards",
    "constrainsQueries",
    "emitsCommands",
    "emitsEvents",
    "decisionTable",
  ],
  decisionTables: ["hitPolicy", "inputs", "outputs", "defaultOutcome", "complete", "rules"],
  risks: [
    "riskStatement",
    "probability",
    "impact",
    "mitigation",
    "productionBlocking",
    "affectedElements",
  ],
  assumptions: [
    "assumptionStatement",
    "businessArea",
    "accepted",
    "validationApproach",
    "affectedElements",
  ],
  hotspots: [
    "question",
    "impact",
    "owner",
    "dueDate",
    "blocksTransformation",
    "blocksProduction",
    "attachedTo",
  ],
  traceLinks: [
    "linkType",
    "source",
    "target",
    "sourceElementId",
    "targetElementId",
    "transformationRule",
    "confidence",
  ],
  readiness: [
    "readinessStatus",
    "transformationReady",
    "deploymentReady",
    "productionReady",
    "findings",
    "checks",
    "manualDecisions",
  ],
  documents: ["format", "content", "externalUri", "sourceReference", "sourceUri", "reviewStatus"],
  annotations: ["key", "value", "source", "__ownerId", "__containmentFeature"],
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function ensureSurface() {
  surface = ensureWorkbenchSurface(surface, "cimWorkbenchSurface");
  return surface;
}

function normalizeViewText(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

function activeCimViewProfile() {
  const view = activeView();
  try {
    const definition = modelingViewDefinition("cim", view);
    if (definition?.viewpoint) {
      return String(definition.viewpoint);
    }
  } catch {
    // fall through to name inference
  }
  const text = normalizeViewText(
    [view?.id, view?.name, view?.kind, view?.layoutProfile].filter(Boolean).join(" "),
  );
  if (text.includes("dashboard")) {
    return "dashboard";
  }
  if (text.includes("requirement") || text.includes("goal")) {
    return "requirements";
  }
  if (text.includes("capability")) {
    return "capability";
  }
  if (text.includes("actor") || text.includes("external")) {
    return "actor";
  }
  if (text.includes("bounded") || text.includes("language")) {
    return "bounded-context";
  }
  if (text.includes("aggregate")) {
    return "aggregate";
  }
  if (text.includes("domain")) {
    return "domain";
  }
  if (text.includes("data") || text.includes("classification")) {
    return "data";
  }
  if (text.includes("process")) {
    return "process";
  }
  if (text.includes("decision") || text.includes("policy")) {
    return "decision";
  }
  if (text.includes("governance") || text.includes("security") || text.includes("privacy")) {
    return "governance";
  }
  if (text.includes("readiness")) {
    return "readiness";
  }
  if (text.includes("trace")) {
    return "traceability";
  }
  return "eventstorming";
}

function activeRepresentation(profile) {
  const viewId = activeView()?.id || "cim";
  return activeWorkbenchRepresentation(
    state.cimWorkbench,
    viewId,
    profile,
    DEFAULT_REPRESENTATION_BY_PROFILE,
  );
}

function setActiveRepresentation(mode) {
  const viewId = activeView()?.id || "cim";
  setWorkbenchRepresentation(
    state.cimWorkbench,
    viewId,
    mode,
    renderCimWorkbenchSurface,
    renderDiagramCallback,
    renderPaletteCallback,
  );
}

function applyCimEdgeMode(mode) {
  applyWorkbenchEdgeMode({
    typeKey: "cim",
    workbenchState: state.cimWorkbench,
    mode,
    graph: state.graph,
    renderWorkbench: renderCimWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback,
  });
}

function activeRegister(profile) {
  const viewId = activeView()?.id || "cim";
  return (
    state.cimWorkbench.registerByViewId[viewId] || PROFILE_REGISTERS[profile] || "requirements"
  );
}

function setActiveRegister(feature) {
  const viewId = activeView()?.id || "cim";
  state.cimWorkbench.registerByViewId[viewId] = feature;
}

function elements() {
  return [...(state.graph?.elementsById || new Map()).values()];
}

function relationships() {
  return [...(state.graph?.relationshipsById || new Map()).values()];
}

function elementsMatchingTypes(types) {
  const allowed = new Set(types);
  return elements().filter((element) => allowed.has(element.eClass));
}

function rootContainment(feature) {
  return CIM_ROOT_CONTAINMENTS.find((entry) => entry.feature === feature) || null;
}

function registerRows(feature) {
  if (feature === "traceLinks") {
    return [
      ...relationships().filter(
        (relationship) => relationship.eClass === "TraceLink" || relationship.kind === "TRACE",
      ),
      ...elementsMatchingTypes(["TraceLink"]),
    ];
  }
  if (feature === "readiness") {
    return elementsMatchingTypes([
      "ProductionReadinessAssessment",
      "ReadinessFinding",
      "ReadinessCheck",
      "ManualDecision",
    ]);
  }
  if (feature === "documents") {
    return elementsMatchingTypes(["StructuredDocument"]);
  }
  if (feature === "annotations") {
    return elementsMatchingTypes(["Annotation", "KeyValue"]);
  }
  const containment = rootContainment(feature);
  if (!containment) {
    return [];
  }
  const rows = elementsMatchingTypes(containment.types);
  if (containment.relationshipOnly) {
    rows.push(
      ...relationships().filter((relationship) => containment.types.includes(relationship.eClass)),
    );
  }
  return dedupeById(rows);
}

function dedupeById(items) {
  const seen = new Set();
  return items.filter((item) => {
    const id = String(item?.id || "");
    if (!id || seen.has(id)) {
      return false;
    }
    seen.add(id);
    return true;
  });
}

function valueText(value) {
  if (Array.isArray(value)) {
    return value.map((item) => refIdLabel(item)).join(", ");
  }
  if (value && typeof value === "object") {
    return refIdLabel(value);
  }
  return String(value ?? "");
}

function refIdLabel(value) {
  const id = typeof value === "string" ? value : value?.$ref || value?.id || value?.elementId || "";
  const element = state.graph.elementsById.get(id);
  return element ? elementLabel(element) : String(id || "");
}

function matchesSearch(row) {
  const query = String(state.cimWorkbench.search || "")
    .trim()
    .toLowerCase();
  if (!query) {
    return true;
  }
  const haystack = [
    row.id,
    row.eClass,
    row.name,
    row.label,
    row.summary,
    row.description,
    row.modelTags,
    row.sourceReference,
    row.sourceUri,
    row.term,
    ...Object.values(row).filter((value) => typeof value === "string"),
  ]
    .join(" ")
    .toLowerCase();
  return haystack.includes(query);
}

function rowInSlice(row) {
  const kind = String(state.cimWorkbench.sliceKind || "");
  const value = String(state.cimWorkbench.sliceValue || "");
  if (!kind || !value) {
    return true;
  }
  if (kind === "lifecycle") {
    return String(row.lifecycleStatus || "") === value;
  }
  if (kind === "classification") {
    return (
      refIds(row.classification).includes(value) || refIds(row.classificationId).includes(value)
    );
  }
  if (kind === "blocking") {
    return Boolean(
      row.productionBlocking || row.blocksTransformation || row.blocksProduction || row.blocking,
    );
  }
  if (kind === "traceType") {
    return String(row.linkType || "") === value;
  }
  const featureByKind = {
    context: ["context", "boundedContext", "boundedContexts"],
    capability: [
      "targetCapability",
      "owningCapability",
      "supports",
      "containsCommands",
      "containsQueries",
      "containsEvents",
      "managesEntities",
      "ownsProcesses",
    ],
    actor: ["issuedBy", "owner", "assignedTo", "playsRoles", "constrainedActors", "dataSubjects"],
    aggregate: ["targetAggregate", "root", "members"],
    process: ["consumedByProcesses", "process", "trigger"],
  };
  return (featureByKind[kind] || []).some((feature) => refIds(row[feature]).includes(value));
}

function filteredRows(feature) {
  const rows = registerRows(feature).filter(matchesSearch).filter(rowInSlice);
  const missingOnly = Boolean(state.cimWorkbench.missingOnly);
  const filtered = missingOnly ? rows.filter((row) => missingRequiredFeatures(row).length) : rows;
  const sortKey = state.cimWorkbench.sortKey || "name";
  return filtered.sort((a, b) =>
    valueText(a?.[sortKey] || a.name).localeCompare(valueText(b?.[sortKey] || b.name)),
  );
}

function fieldDefinition(type, fieldName) {
  try {
    const definition = modelingElementDefinition("cim", type);
    return (
      [...safeArray(definition?.attributes), ...safeArray(definition?.references)].find(
        (field) => field.name === fieldName,
      ) || null
    );
  } catch {
    return null;
  }
}

function normalizeOppositeName(value) {
  const raw = String(value || "").trim();
  const slashIndex = raw.lastIndexOf("/");
  const hashIndex = raw.lastIndexOf("#");
  return raw.substring(Math.max(slashIndex, hashIndex) + 1).replace(/^@?/, "");
}

function syncOppositeReference(source, feature, targetId, checked) {
  const definition = fieldDefinition(source?.eClass, feature);
  const opposite = normalizeOppositeName(definition?.opposite);
  const target = state.graph.elementsById.get(targetId);
  if (!opposite || !target) {
    return;
  }
  const oppositeDefinition = fieldDefinition(target.eClass, opposite);
  const many = oppositeDefinition?.many !== false;
  if (checked) {
    addReferenceValue(target, opposite, source.id, many);
  } else {
    removeReferenceValue(target, opposite, source.id, many);
  }
  const visibleTarget = state.nodesById.get(target.id);
  if (visibleTarget?.meta) {
    visibleTarget.meta[opposite] = target[opposite];
  }
}

function controlForField(row, field) {
  const definition = fieldDefinition(row.eClass, field) || {};
  const value = row[field];
  const readonly = definition.readonly || field === "id";
  if (readonly) {
    return `<span class="cim-cell-readonly">${escapeHtml(valueText(value))}</span>`;
  }
  if (
    definition.kind === "reference" ||
    Array.isArray(value) ||
    (value && typeof value === "object")
  ) {
    return `<span class="cim-ref-cell">${escapeHtml(
      compactRefLabels(value, state.graph.elementsById, 4) || "",
    )}</span>`;
  }
  if (definition.fieldType === "boolean" || typeof value === "boolean") {
    return `<input class="cim-table-check" data-cim-field="${escapeHtml(
      field,
    )}" data-cim-row="${escapeHtml(row.id)}" type="checkbox" ${value ? "checked" : ""}>`;
  }
  if (definition.fieldType === "select" && Array.isArray(definition.options)) {
    return `<select class="cim-table-input" data-cim-field="${escapeHtml(
      field,
    )}" data-cim-row="${escapeHtml(row.id)}">
      <option value=""></option>
      ${definition.options
        .map(
          (option) =>
            `<option value="${escapeHtml(option)}" ${
              String(value || "") === String(option) ? "selected" : ""
            }>${escapeHtml(option)}</option>`,
        )
        .join("")}
    </select>`;
  }
  const type = definition.fieldType === "number" || typeof value === "number" ? "number" : "text";
  return `<input class="cim-table-input" data-cim-field="${escapeHtml(field)}"
      data-cim-row="${escapeHtml(row.id)}" type="${type}" value="${escapeHtml(valueText(value))}">`;
}

function rowTypeBadge(row) {
  const missing = missingRequiredFeatures(row);
  return `<span class="cim-type-badge">${escapeHtml(row.eClass || row.kind || "Element")}</span>${
    missing.length
      ? `<span class="cim-missing-badge"
      title="${escapeHtml(missing.join(", "))}">${missing.length}</span>`
      : ""
  }`;
}

function registerOptionsMarkup(activeFeature) {
  const entries = [
    ...CIM_ROOT_CONTAINMENTS.map((entry) => ({
      feature: entry.feature,
      title: entry.title,
    })),
    { feature: "traceLinks", title: "Trace Links" },
    { feature: "readiness", title: "Readiness Items" },
    { feature: "documents", title: "Structured Documents" },
    { feature: "annotations", title: "Annotations" },
  ];
  return entries
    .map(
      (entry) =>
        `<option value="${escapeHtml(entry.feature)}" ${
          activeFeature === entry.feature ? "selected" : ""
        }>${escapeHtml(entry.title)}</option>`,
    )
    .join("");
}

function renderRegister(profile) {
  const feature = activeRegister(profile);
  const rows = filteredRows(feature);
  const containment = rootContainment(feature);
  const columns = [
    "name",
    "lifecycleStatus",
    ...(REGISTER_COLUMNS[feature] ||
      safeArray(containment?.types).flatMap((type) =>
        safeArray(modelingElementDefinition("cim", type)?.visibleFields),
      )),
  ].filter(Boolean);
  const uniqueColumns = [...new Set(columns)].slice(0, 13);
  const virtualAddType = {
    documents: "StructuredDocument",
    annotations: "Annotation",
  }[feature];
  const addType =
    virtualAddType ||
    containment?.types?.find((type) => {
      try {
        return modelingElementDefinition("cim", type)?.creatable !== false;
      } catch {
        return false;
      }
    });
  const toolbarHtml = renderWorkbenchToolbar([
    `<select class="cim-select" data-cim-register>${registerOptionsMarkup(feature)}</select>`,
    addType
      ? `<button class="cim-action" data-cim-add-type="${escapeHtml(
          addType,
        )}" type="button">Add ${escapeHtml(addType)}</button>`
      : "",
    `<button class="cim-action" data-cim-export="${escapeHtml(
      feature,
    )}" type="button">Export CSV</button>`,
    `<label class="cim-action cim-file-action">Import CSV
        <input class="hidden" data-cim-import="${escapeHtml(feature)}" type="file" accept=".csv,text/csv">
      </label>`,
  ]);
  return renderWorkbenchRegister({
    typeKey: "cim",
    toolbarHtml,
    rows,
    columns: uniqueColumns,
    getLabel: elementLabel,
    getBadgeHtml: rowTypeBadge,
    renderCell: controlForField,
    emptyText: "No rows in this slice.",
  });
}

function count(type) {
  return elementsMatchingTypes(Array.isArray(type) ? type : [type]).length;
}

function rootValue(key) {
  return state.baseModel?.[key] ?? "";
}

function rootMissing() {
  return [
    !isPresent(rootValue("domainName")) ? "domainName" : "",
    count("BusinessGoal") < 1 ? "goals" : "",
    count(["Actor", "ExternalSystem"]) < 1 ? "actors" : "",
    count("BusinessCapability") < 1 ? "capabilities" : "",
  ].filter(Boolean);
}

function renderDashboard() {
  const missing = rootMissing();
  const metrics = [
    ["Goals", count("BusinessGoal")],
    ["Actors", count(["Actor", "ExternalSystem"])],
    ["Capabilities", count("BusinessCapability")],
    ["Entities", count("DomainEntity")],
    ["Commands", count("Command")],
    ["Events", count("BusinessEvent")],
    ["Policies", count("Policy")],
    ["Risks", count("Risk")],
    ["Hotspots", count("Hotspot")],
    [
      "Errors",
      state.validation?.issues?.filter(
        (issue) => String(issue?.severity || "").toUpperCase() === "ERROR",
      ).length || 0,
    ],
  ];
  const views = [...state.views.byId.values()].filter((view) => !view.scope?.rootElementId);
  const rootHtml = `<section class="cim-root-form">
        <div class="cim-section-title">Model Root</div>
        ${["domainName", "businessScope", "organizationName", "modelingDate", "language"]
          .map(
            (field) => `
          <label class="cim-form-field">
            <span>${escapeHtml(field)}</span>
            <input data-cim-root-field="${escapeHtml(field)}" type="${
              field === "modelingDate" ? "date" : "text"
            }" value="${escapeHtml(rootValue(field))}">
          </label>`,
          )
          .join("")}
        ${
          missing.length
            ? `<div class="cim-alert">Missing required root data:
          ${escapeHtml(missing.join(", "))}</div>
          <button class="cim-action cim-action-primary" data-cim-create-root
                  type="button">Create Required Root Elements</button>`
            : `
          <div class="cim-ok">Root requirements are satisfied.</div>`
        }
      </section>`;
  const viewsHtml = `<section class="cim-view-entry-list">
        <div class="cim-section-title">Views</div>
        ${views
          .map(
            (view) => `<button class="cim-view-entry"
            data-cim-view="${escapeHtml(view.id)}" type="button">
          <span>${escapeHtml(view.name || view.id)}</span>
          <strong>${escapeHtml(
            String(view.layoutProfile || view.kind || "view").toLowerCase(),
          )}</strong>
        </button>`,
          )
          .join("")}
      </section>`;
  return renderWorkbenchDashboard({
    metrics,
    primaryHtml: rootHtml,
    secondaryHtml: viewsHtml,
  });
}

function matrixCell(row, feature, column) {
  const checked = hasReferenceValue(row, feature, column.id);
  return `<td><input data-cim-toggle-ref="${escapeHtml(row.id)}"
      data-cim-feature="${escapeHtml(feature)}"
      data-cim-target="${escapeHtml(column.id)}" type="checkbox" ${checked ? "checked" : ""}></td>`;
}

function matrixTable(title, rows, columns, featureForColumn, empty = "") {
  if (!rows.length || !columns.length) {
    return `<section class="cim-matrix-section"><div class="cim-section-title">${escapeHtml(
      title,
    )}</div><div class="cim-empty">${escapeHtml(
      empty || "No rows or columns available.",
    )}</div></section>`;
  }
  return renderWorkbenchMatrixSection({
    title,
    rows,
    columns,
    typeKey: "cim",
    getRowLabel: elementLabel,
    getColumnLabel: elementLabel,
    getColumnMeta: (column) => column.eClass,
    getCellHtml: (row, column) => matrixCell(row, featureForColumn(column, row), column),
    emptyText: empty || "No rows or columns available.",
  });
}

function renderMatrix(profile) {
  if (profile === "actor") {
    const actors = elementsMatchingTypes(["Actor", "ExternalSystem"]);
    const commands = elementsMatchingTypes(["Command"]);
    const queries = elementsMatchingTypes(["Query"]);
    const events = elementsMatchingTypes(["BusinessEvent"]);
    return (
      matrixTable("Actors by Commands", actors, commands, () => "issuesCommands") +
      matrixTable("Actors by Queries", actors, queries, () => "issuesQueries") +
      matrixTable("Actors by Observed Events", actors, events, () => "observesEvents")
    );
  }
  if (profile === "capability") {
    const capabilities = elementsMatchingTypes(["BusinessCapability"]);
    const contents = elementsMatchingTypes([
      "Command",
      "Query",
      "BusinessEvent",
      "DomainEntity",
      "BusinessProcess",
    ]);
    const feature = (column) =>
      ({
        Command: "containsCommands",
        Query: "containsQueries",
        BusinessEvent: "containsEvents",
        DomainEntity: "managesEntities",
        BusinessProcess: "ownsProcesses",
      })[column.eClass] || "supports";
    return (
      matrixTable("Capability Contents", capabilities, contents, feature) +
      matrixTable(
        "Goals Supported by Capabilities",
        capabilities,
        elementsMatchingTypes(["BusinessGoal"]),
        () => "supports",
      )
    );
  }
  if (profile === "bounded-context") {
    const contexts = elementsMatchingTypes(["BoundedContextCandidate"]);
    const members = elementsMatchingTypes([
      "BusinessCapability",
      "DomainEntity",
      "Command",
      "Query",
      "BusinessEvent",
      "Policy",
    ]);
    const feature = (column) =>
      ({
        BusinessCapability: "capabilities",
        DomainEntity: "entities",
        Command: "commands",
        Query: "queries",
        BusinessEvent: "events",
        Policy: "policies",
      })[column.eClass] || "entities";
    return matrixTable("Bounded Context Membership", contexts, members, feature);
  }
  if (profile === "aggregate") {
    const aggregates = elementsMatchingTypes(["AggregateCandidate"]);
    return (
      matrixTable(
        "Aggregate Members",
        aggregates,
        elementsMatchingTypes(["DomainEntity"]),
        () => "members",
      ) +
      matrixTable(
        "Aggregate Commands",
        aggregates,
        elementsMatchingTypes(["Command"]),
        () => "handledCommands",
      ) +
      matrixTable(
        "Aggregate Events",
        aggregates,
        elementsMatchingTypes(["BusinessEvent"]),
        () => "emittedEvents",
      )
    );
  }
  if (profile === "data") {
    return (
      matrixTable(
        "Query Outputs",
        elementsMatchingTypes(["Query"]),
        elementsMatchingTypes(["InformationItem"]),
        () => "output",
      ) +
      matrixTable(
        "Command Inputs",
        elementsMatchingTypes(["Command"]),
        elementsMatchingTypes(["InformationItem"]),
        () => "input",
      ) +
      matrixTable(
        "Event Payloads",
        elementsMatchingTypes(["BusinessEvent"]),
        elementsMatchingTypes(["InformationItem"]),
        () => "payload",
      ) +
      matrixTable(
        "Privacy and Compliance Data",
        elementsMatchingTypes(["PrivacyConstraint", "ComplianceConstraint", "SecurityConstraint"]),
        elementsMatchingTypes(["InformationItem"]),
        (column, row) =>
          ({
            PrivacyConstraint: "dataItems",
            SecurityConstraint: "constrainedInformation",
            ComplianceConstraint: "scopedElements",
          })[row.eClass] || "constrainedElements",
      )
    );
  }
  if (profile === "governance") {
    const constraints = elementsMatchingTypes([
      "PrivacyConstraint",
      "ComplianceConstraint",
      "SecurityConstraint",
      "NonFunctionalRequirement",
    ]);
    const targets = elementsMatchingTypes([
      "InformationItem",
      "Actor",
      "Command",
      "Query",
      "ExternalSystem",
      "BusinessCapability",
      "DomainEntity",
    ]);
    const feature = (column, row) => {
      if (row.eClass === "PrivacyConstraint") {
        return "dataItems";
      }
      if (row.eClass === "ComplianceConstraint") {
        return "scopedElements";
      }
      if (row.eClass === "SecurityConstraint" && column.eClass === "Actor") {
        return "constrainedActors";
      }
      if (row.eClass === "SecurityConstraint" && column.eClass === "Command") {
        return "constrainedCommands";
      }
      if (row.eClass === "SecurityConstraint" && column.eClass === "Query") {
        return "constrainedQueries";
      }
      if (row.eClass === "SecurityConstraint" && column.eClass === "InformationItem") {
        return "constrainedInformation";
      }
      return "constrainedElements";
    };
    return matrixTable("Governance Constraints", constraints, targets, feature);
  }
  if (profile === "decision") {
    const policies = elementsMatchingTypes(["Policy"]);
    return (
      matrixTable(
        "Policy Triggers",
        policies,
        elementsMatchingTypes(["BusinessEvent"]),
        () => "triggeredBy",
      ) +
      matrixTable(
        "Policy Guards Commands",
        policies,
        elementsMatchingTypes(["Command"]),
        () => "guards",
      ) +
      matrixTable(
        "Policy Emits Commands",
        policies,
        elementsMatchingTypes(["Command"]),
        () => "emitsCommands",
      ) +
      matrixTable(
        "Policy Emits Events",
        policies,
        elementsMatchingTypes(["BusinessEvent"]),
        () => "emitsEvents",
      ) +
      matrixTable(
        "Policy Constrains Queries",
        policies,
        elementsMatchingTypes(["Query"]),
        () => "constrainsQueries",
      ) +
      matrixTable(
        "Decision Table Inputs",
        elementsMatchingTypes(["DecisionTable"]),
        elementsMatchingTypes(["InformationItem"]),
        () => "inputs",
      ) +
      matrixTable(
        "Decision Table Outputs",
        elementsMatchingTypes(["DecisionTable"]),
        elementsMatchingTypes(["InformationItem"]),
        () => "outputs",
      )
    );
  }
  if (profile === "process") {
    return renderProcessStepTable();
  }
  if (profile === "traceability") {
    return renderTraceMatrix();
  }
  if (profile === "requirements") {
    return (
      matrixTable(
        "Goals measured by KPIs",
        elementsMatchingTypes(["BusinessGoal"]),
        elementsMatchingTypes(["KPI"]),
        () => "measuredBy",
      ) +
      matrixTable(
        "Requirements constraining elements",
        elementsMatchingTypes([
          "Requirement",
          "NonFunctionalRequirement",
          "SecurityConstraint",
          "PrivacyConstraint",
          "ComplianceConstraint",
        ]),
        elements().filter((element) => !element.eClass?.includes("Requirement")),
        () => "constrains",
      )
    );
  }
  return matrixTable(
    "Command to Event Outcomes",
    elementsMatchingTypes(["Command"]),
    elementsMatchingTypes(["BusinessEvent"]),
    () => "expectedEvents",
  );
}

function renderProcessStepTable() {
  const processes = elementsMatchingTypes(["BusinessProcess"]);
  if (!processes.length) {
    return `<section class="cim-matrix-section"><div class="cim-section-title">Process Steps</div><div class="cim-empty">No business processes available.</div></section>`;
  }
  return processes
    .map((process) => {
      const steps = childElements(process, "steps", "ProcessStep");
      const rows = steps.map((step) => {
        const requiredRef =
          {
            CommandStep: "command",
            QueryStep: "query",
            EventStep: "event",
            PolicyStep: "policy",
            ExternalInteractionStep: "externalSystem",
          }[step.eClass] || "";
        return `<tr>
            <td>${escapeHtml(elementLabel(step))}</td>
            <td>${escapeHtml(step.eClass || "ProcessStep")}</td>
            <td>${controlForField(step, "orderIndex")}</td>
            <td>${controlForField(step, "responsibility")}</td>
            <td>${
              requiredRef
                ? controlForField(step, requiredRef)
                : `<span class="cim-cell-readonly">none</span>`
            }</td>
            <td><button class="cim-icon-action" data-cim-open="${escapeHtml(
              step.id,
            )}" type="button">Open</button></td>
          </tr>`;
      });
      return renderWorkbenchDataTable({
        title: `${elementLabel(process)} Steps`,
        columns: ["Step", "Type", "Order", "Responsibility", "Required ref", ""],
        rows,
        emptyText: "No steps.",
      });
    })
    .join("");
}

function renderTraceMatrix() {
  const links = registerRows("traceLinks").filter(matchesSearch);
  const modelElements = elements().filter((element) => element.eClass !== "TraceLink");
  return renderWorkbenchDataTable({
    title: "Trace Links",
    columns: ["Link", "Type", "Source", "Target", "Confidence", ""],
    rows: links.map(
      (link) => `<tr>
          <td>${escapeHtml(elementLabel(link))}</td>
          <td>${controlForField(link, "linkType")}</td>
          <td>${traceEndpointSelect(link, "source", modelElements)}</td>
          <td>${traceEndpointSelect(link, "target", modelElements)}</td>
          <td>${controlForField(link, "confidence")}</td>
          <td><button class="cim-icon-action" data-cim-open="${escapeHtml(
            link.id,
          )}" type="button">Open</button></td>
        </tr>`,
    ),
    emptyText: "No trace links.",
  });
}

function traceEndpointSelect(link, field, options) {
  const current = refIds(link[field])[0] || link[`${field}ElementId`] || "";
  return `<select class="cim-table-input" data-cim-field="${escapeHtml(field)}"
      data-cim-row="${escapeHtml(link.id)}">
      <option value=""></option>
      ${options
        .map(
          (option) =>
            `<option value="${escapeHtml(option.id)}" ${
              current === option.id ? "selected" : ""
            }>${escapeHtml(elementLabel(option))} (${escapeHtml(option.eClass)})</option>`,
        )
        .join("")}
    </select>`;
}

function childElements(parent, feature, fallbackType) {
  const ids = refIds(parent?.[feature]);
  const byId = ids.map((id) => state.graph.elementsById.get(id)).filter(Boolean);
  const owned = elements().filter(
    (element) => element.__ownerId === parent.id && element.__containmentFeature === feature,
  );
  const inline = safeArray(parent?.[feature])
    .filter((item) => item && typeof item === "object")
    .map((item, index) => ({
      eClass: item.eClass || item.type || fallbackType,
      id: item.id || `${parent.id}-${feature}-${index + 1}`,
      name: item.name || item.label || `${fallbackType} ${index + 1}`,
      ...item,
    }));
  return dedupeById([...byId, ...owned, ...inline]);
}

function renderReadinessBoard() {
  const lanes = [
    [
      "Blocking",
      (item) =>
        item.productionBlocking ||
        item.blocksTransformation ||
        item.blocksProduction ||
        item.blocking,
    ],
    ["Open", (item) => !item.accepted && !item.decision && !item.passed],
    ["Accepted / Passed", (item) => item.accepted || item.decision || item.passed],
  ];
  const cards = elementsMatchingTypes([
    "Risk",
    "Assumption",
    "Hotspot",
    "ManualDecision",
    "ReadinessFinding",
    "ReadinessCheck",
  ]);
  return renderWorkbenchBoard({
    lanes: lanes.map(([title, predicate]) => {
      const laneCards = cards.filter(predicate);
      return {
        title,
        count: laneCards.length,
        cards: laneCards.map((item) =>
          renderWorkbenchBoardCard({
            typeKey: "cim",
            id: item.id,
            title: elementLabel(item),
            meta: item.eClass,
            body:
              item.riskStatement ||
              item.question ||
              item.message ||
              item.assumptionStatement ||
              item.recommendation ||
              "",
          }),
        ),
        emptyText: "No items",
      };
    }),
  });
}

function renderDetailProjection(profile) {
  const selected = state.selectedNodeId ? state.graph.elementsById.get(state.selectedNodeId) : null;
  const row = selected || filteredRows(activeRegister(profile))[0] || elements()[0];
  if (!row) {
    return renderWorkbenchDetail({
      typeKey: "cim",
      row: null,
      valueText,
      emptyText: "Select an element on the diagram or open one from a register.",
    });
  }
  const definition = modelingElementDefinition("cim", row.eClass);
  const fields = [...safeArray(definition?.attributes), ...safeArray(definition?.references)].map(
    (field) => field.name,
  );
  const missing = missingRequiredFeatures(row);
  const traces = relationships().filter(
    (relationship) =>
      relationship.sourceElementId === row.id || relationship.targetElementId === row.id,
  );
  return renderWorkbenchDetail({
    typeKey: "cim",
    row,
    fields,
    title: elementLabel(row),
    typeLabel: row.eClass,
    valueText,
    stats: [
      ["missing required", missing.join(", ") || "none"],
      ["trace links", traces.length],
    ],
  });
}

function sliceItems() {
  const kind = state.cimWorkbench.sliceKind || "";
  const collections = {
    context: elementsMatchingTypes(["BoundedContextCandidate"]),
    capability: elementsMatchingTypes(["BusinessCapability"]),
    actor: elementsMatchingTypes(["Actor", "ExternalSystem", "Role"]),
    aggregate: elementsMatchingTypes(["AggregateCandidate"]),
    process: elementsMatchingTypes(["BusinessProcess"]),
    classification: elementsMatchingTypes(["DataClassification"]),
    lifecycle: [
      ...new Set(
        elements()
          .map((item) => item.lifecycleStatus)
          .filter(Boolean),
      ),
    ].map((value) => ({ id: value, name: value })),
    traceType: [
      ...new Set(
        registerRows("traceLinks")
          .map((item) => item.linkType)
          .filter(Boolean),
      ),
    ].map((value) => ({
      id: value,
      name: value,
    })),
    blocking: [{ id: "true", name: "Blocking items" }],
  };
  return collections[kind] || [];
}

function cimSliceKindLabel(value = state.cimWorkbench.sliceKind) {
  return CIM_SLICE_KINDS.find(([key]) => key === value)?.[1] || "All slices";
}

function cimSliceValueLabel() {
  const value = state.cimWorkbench.sliceValue || "";
  if (!value) {
    return "Any";
  }
  const item = sliceItems().find((candidate) => candidate.id === value);
  return item ? elementLabel(item) : value;
}

function sliceOptionButton({ value, label, selected, optionKind }) {
  return renderWorkbenchSliceOption({
    typeKey: "cim",
    optionKind,
    value,
    label,
    selected,
  });
}

function cimSliceMenuMarkup(optionKind) {
  if (optionKind === "kind") {
    return CIM_SLICE_KINDS.map(([value, label]) =>
      sliceOptionButton({
        value,
        label,
        selected: state.cimWorkbench.sliceKind === value,
        optionKind,
      }),
    ).join("");
  }
  const options = [{ id: "", name: "Any" }, ...sliceItems()];
  return options
    .map((item) =>
      sliceOptionButton({
        value: item.id,
        label: item.id ? elementLabel(item) : item.name,
        selected: (state.cimWorkbench.sliceValue || "") === item.id,
        optionKind,
      }),
    )
    .join("");
}

function cimSliceSelectMarkup(optionKind) {
  const isKind = optionKind === "kind";
  const open = cimSliceMenuOpen === optionKind;
  const label = isKind ? cimSliceKindLabel() : cimSliceValueLabel();
  return renderWorkbenchSliceSelect({
    typeKey: "cim",
    optionKind,
    open,
    label,
    menuHtml: cimSliceMenuMarkup(optionKind),
  });
}

function renderControls(profile, representation) {
  return renderWorkbenchControls({
    typeKey: "cim",
    title: activeView()?.name || "CIM View",
    profile,
    representation,
    workbenchState: state.cimWorkbench,
    sliceControlsHtml: `${cimSliceSelectMarkup("kind")}${cimSliceSelectMarkup("value")}`,
    modes: [
      ["diagram", "Diagram"],
      ["dashboard", "Dashboard"],
      ["register", "Register"],
      ["matrix", "Matrix"],
      ["board", "Board"],
      ["detail", "Detail"],
      ["guide", "Guide"],
    ],
    searchPlaceholder: "Search CIM",
  });
}

function renderBody(profile, representation) {
  if (representation === "dashboard") {
    return renderDashboard();
  }
  if (representation === "register") {
    return renderRegister(profile);
  }
  if (representation === "matrix") {
    return renderMatrix(profile);
  }
  if (representation === "board") {
    return renderReadinessBoard();
  }
  if (representation === "detail") {
    return renderDetailProjection(profile);
  }
  if (representation === "guide") {
    return renderLevelGuidePanel("cim");
  }
  return "";
}

export function renderCimWorkbenchSurface() {
  const host = ensureSurface();
  const profile = activeCimViewProfile();
  const representation = activeRepresentation(profile);
  const controls = renderControls(profile, representation);
  renderWorkbenchSurfaceLayout({
    host,
    activeType: state.activeType,
    expectedType: "cim",
    minimized: state.modelingToolsMinimized,
    representation,
    controlsHtml: controls,
    bodyHtml: renderBody(profile, representation),
    workbenchState: state.cimWorkbench,
    surfaceActiveClass: "cim-surface-active",
    surfaceDockClass: "cim-surface-dock",
  });
}

function currentCenter() {
  const rect = el.canvasViewport?.getBoundingClientRect();
  if (!rect) {
    return { x: 120, y: 120 };
  }
  return {
    x: Math.round((rect.width / 2 - state.viewport.x) / state.viewport.scale),
    y: Math.round((rect.height / 2 - state.viewport.y) / state.viewport.scale),
  };
}

function addNode(type, x, y, name = "") {
  const node = getDefaultNode("cim", type, Math.round(x), Math.round(y));
  if (name) {
    node.label = name;
    node.meta.name = name;
    node.meta.label = name;
  }
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function attachChildToParent(childNode, parent, feature) {
  if (!childNode?.id || !parent?.id || !feature) {
    return;
  }
  childNode.meta.__ownerId = parent.id;
  childNode.meta.__containmentFeature = feature;
  addReferenceValue(parent, feature, childNode.id, true);
  const visibleParent = state.nodesById.get(parent.id);
  if (visibleParent?.meta) {
    visibleParent.meta[feature] = parent[feature];
  }
}

function firstElementOfType(types) {
  return elementsMatchingTypes(Array.isArray(types) ? types : [types])[0] || null;
}

function connect(source, target, kind) {
  if (!source?.id || !target?.id) {
    return null;
  }
  const edge = {
    id: `e-${source.id}-${target.id}-${kind}-${Date.now()}`,
    sourceId: source.id,
    targetId: target.id,
    kind,
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  return edge;
}

function createCimElement(type) {
  syncActiveViewFromVisibleGraph();
  const center = currentCenter();
  const node = addNode(type, center.x, center.y, type);
  switch (type) {
    case "BusinessCapability": {
      const goal =
        elementsMatchingTypes(["BusinessGoal"])[0] ||
        addNode("BusinessGoal", center.x - 260, center.y, "Business Goal");
      node.meta.supports = [goal.id];
      connect(node, goal, "SUPPORTS");
      break;
    }
    case "Command": {
      const event = addNode("BusinessEvent", center.x + 260, center.y, `${node.label} Completed`);
      event.meta.occurredInPastTenseName = event.label;
      node.meta.expectedEvents = [event.id];
      connect(node, event, "EXPECTS");
      break;
    }
    case "Query": {
      const output = addNode("InformationItem", center.x + 260, center.y, `${node.label} Result`);
      node.meta.output = [output.id];
      connect(node, output, "OUTPUT");
      break;
    }
    case "DomainEntity": {
      const idItem = addNode("InformationItem", center.x + 260, center.y, `${node.label}Id`);
      idItem.meta.type = "IDENTIFIER";
      idItem.meta.required = true;
      node.meta.identityAttribute = idItem.id;
      node.meta.attributes = [idItem.id];
      connect(node, idItem, "HAS_ATTRIBUTE");
      break;
    }
    case "AggregateCandidate": {
      const root = addNode("DomainEntity", center.x + 260, center.y, `${node.label} Root`);
      node.meta.root = root.id;
      node.meta.members = [root.id];
      connect(node, root, "ROOT");
      break;
    }
    case "BusinessProcess": {
      const start = addNode("StartStep", center.x - 220, center.y, "Start");
      const end = addNode("EndStep", center.x + 220, center.y, "End");
      start.meta.__ownerId = node.id;
      start.meta.__containmentFeature = "steps";
      end.meta.__ownerId = node.id;
      end.meta.__containmentFeature = "steps";
      node.meta.steps = [start.id, end.id];
      connect(start, end, "TRANSITION");
      break;
    }
    case "DecisionTable":
      addContainedChild(node.id, "rules", "DecisionRule");
      break;
    case "PrivacyConstraint": {
      const item =
        elementsMatchingTypes(["InformationItem"])[0] ||
        addNode("InformationItem", center.x + 260, center.y, "Protected Data");
      node.meta.dataItems = [item.id];
      break;
    }
    case "NonFunctionalRequirement": {
      const target = elements()[0];
      if (target) {
        node.meta.constrainedElements = [target.id];
      }
      break;
    }
    case "UbiquitousLanguageTerm":
      node.meta.term = node.label;
      break;
    case "ManualDecision":
      node.meta.question = node.label;
      attachChildToParent(
        node,
        firstElementOfType("TransformationProfile") ||
          addNode("TransformationProfile", center.x - 260, center.y, "Transformation Profile"),
        "requiredDecisions",
      );
      break;
    case "ReadinessFinding":
      node.meta.severity = node.meta.severity || "WARNING";
      attachChildToParent(
        node,
        firstElementOfType("ProductionReadinessAssessment") ||
          addNode(
            "ProductionReadinessAssessment",
            center.x - 260,
            center.y,
            "Readiness Assessment",
          ),
        "findings",
      );
      break;
    case "ReadinessCheck":
      node.meta.checkId = node.meta.checkId || node.id;
      node.meta.severity = node.meta.severity || "WARNING";
      attachChildToParent(
        node,
        firstElementOfType("ProductionReadinessAssessment") ||
          addNode(
            "ProductionReadinessAssessment",
            center.x - 260,
            center.y,
            "Readiness Assessment",
          ),
        "checks",
      );
      break;
    case "StructuredDocument":
      node.meta.format = node.meta.format || "TEXT";
      break;
    case "Annotation":
      node.meta.key = node.meta.key || node.label;
      node.meta.source = node.meta.source || "frontend";
      if (state.selectedNodeId && state.selectedNodeId !== node.id) {
        const selected = state.graph.elementsById.get(state.selectedNodeId);
        if (selected) {
          attachChildToParent(node, selected, "annotations");
        }
      }
      break;
    default:
      break;
  }
  commitModelChange(`Added ${type}`);
  openAttributePanelCallback?.(node.id);
}

function openCimDetail(id) {
  if (state.graph?.elementsById?.has(id) || state.nodesById?.has(id)) {
    openAttributePanelCallback?.(id);
    return;
  }
  if (state.graph?.relationshipsById?.has(id)) {
    openConnectionPanelCallback?.(id);
  }
}

function addContainedChild(parentId, feature, type) {
  const parent = state.graph.elementsById.get(parentId);
  if (!parent) {
    return null;
  }
  const center = currentCenter();
  const child = addNode(
    type,
    center.x + 180,
    center.y + 120,
    `${type} ${childElements(parent, feature, type).length + 1}`,
  );
  child.meta.__ownerId = parent.id;
  child.meta.__containmentFeature = feature;
  if (type === "DecisionRule") {
    child.meta.priorityOrder = childElements(parent, feature, type).length + 1;
    child.meta.condition = "";
    child.meta.outcome = "";
  }
  if (type === "AcceptanceCriterion") {
    child.meta.givenContext = "";
    child.meta.whenAction = "";
    child.meta.thenOutcome = "";
  }
  if (type === "LifecycleStateDefinition") {
    child.meta.stateName = child.label;
  }
  if (type === "QualityScenario") {
    child.meta.source = "";
    child.meta.stimulus = "";
    child.meta.response = "";
  }
  if (type === "BusinessInvariant") {
    child.meta.naturalLanguageStatement = "";
  }
  if (type === "ReadinessFinding") {
    child.meta.severity = child.meta.severity || "WARNING";
  }
  if (type === "ReadinessCheck") {
    child.meta.checkId = child.meta.checkId || child.id;
    child.meta.severity = child.meta.severity || "WARNING";
  }
  if (type === "ManualDecision") {
    child.meta.question = child.meta.question || child.label;
  }
  addReferenceValue(parent, feature, child.id, true);
  const visibleParent = state.nodesById.get(parent.id);
  if (visibleParent?.meta) {
    visibleParent.meta[feature] = parent[feature];
  }
  commitModelChange(`Added ${type}`);
  return child;
}

function createRequiredRoot() {
  state.baseModel ??= {};
  state.baseModel.domainName ||= "Core Domain";
  state.baseModel.businessScope ||= "Business capability and behavior model";
  state.baseModel.organizationName ||= "Organization";
  state.baseModel.language ||= "en";
  const center = currentCenter();
  const goal =
    elementsMatchingTypes(["BusinessGoal"])[0] ||
    addNode("BusinessGoal", center.x - 260, center.y - 80, "Fulfill Business Outcome");
  const actor =
    elementsMatchingTypes(["Actor", "ExternalSystem"])[0] ||
    addNode("Actor", center.x - 260, center.y + 80, "Business Actor");
  const capability =
    elementsMatchingTypes(["BusinessCapability"])[0] ||
    addNode("BusinessCapability", center.x, center.y, "Core Capability");
  capability.meta.supports = refIds(capability.meta.supports).length
    ? capability.meta.supports
    : [goal.id];
  connect(capability, goal, "SUPPORTS");
  commitModelChange("Completed CIM root model");
  openAttributePanelCallback?.(capability.id);
}

function commitModelChange(message) {
  commitWorkbenchModelChange({
    typeKey: "cim",
    renderWorkbench: renderCimWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback,
    message,
    syncActiveViewFromVisibleGraph,
    saveCurrentTabGraphState,
    markModelDirty,
    setStatus,
  });
}

function updateField(rowId, field, rawValue, inputType = "text") {
  const row = state.graph.elementsById.get(rowId) || state.graph.relationshipsById.get(rowId);
  if (!row) {
    return;
  }
  const definition = fieldDefinition(row.eClass, field);
  let value = rawValue;
  if (inputType === "checkbox") {
    value = Boolean(rawValue);
  } else if (definition?.fieldType === "number") {
    value = Number(rawValue);
  } else if (definition?.kind === "reference") {
    value = definition.many ? (rawValue ? [rawValue] : []) : rawValue || null;
    if (field === "source" || field === "target") {
      row[`${field}ElementId`] = rawValue || "";
    }
  }
  row[field] = value;
  const visible = state.nodesById.get(rowId);
  if (visible?.meta) {
    visible.meta[field] = value;
    if (field === "name") {
      visible.label = String(value || visible.label);
      visible.meta.name = visible.label;
      visible.meta.label = visible.label;
    }
  }
  commitModelChange(`Updated ${field}`);
}

function toggleReference(sourceId, feature, targetId, checked) {
  const source =
    state.graph.elementsById.get(sourceId) || state.graph.relationshipsById.get(sourceId);
  if (!source || !feature || !targetId) {
    return;
  }
  if (checked) {
    addReferenceValue(source, feature, targetId, true);
  } else {
    removeReferenceValue(source, feature, targetId, true);
  }
  syncOppositeReference(source, feature, targetId, checked);
  const visible = state.nodesById.get(sourceId);
  if (visible?.meta) {
    visible.meta[feature] = source[feature];
  }
  commitModelChange(`${checked ? "Linked" : "Unlinked"} ${feature}`);
}

function updateRootField(field, value) {
  state.baseModel ??= {};
  state.baseModel[field] = value;
  commitModelChange(`Updated ${field}`);
}

function exportCsv(feature) {
  const rows = filteredRows(feature);
  const columns = ["id", "eClass", "name", ...(REGISTER_COLUMNS[feature] || [])];
  downloadWorkbenchCsv(`cim-${feature}.csv`, rows, columns, valueText);
}

async function importCsv(feature, file) {
  const containment = rootContainment(feature);
  const type = containment?.types?.[0];
  if (!type || !file) {
    return;
  }
  const text = await file.text();
  const [headerLine, ...lines] = text.split(/\r?\n/).filter(Boolean);
  const headers = headerLine.split(",").map((item) => item.trim());
  lines.forEach((line, index) => {
    const values = line.split(",");
    const node = createImportedNode(type, index);
    headers.forEach((header, columnIndex) => {
      if (header && values[columnIndex] !== undefined) {
        node.meta[header] = values[columnIndex];
      }
    });
    node.label = node.meta.name || node.label;
    node.meta.name = node.label;
  });
  commitModelChange(`Imported ${lines.length} ${feature} row(s)`);
}

function createImportedNode(type, index) {
  const center = currentCenter();
  const offset = index * 34;
  const node = getDefaultNode("cim", type, center.x + offset, center.y + offset);
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function bindSurfaceEvents() {
  const host = ensureSurface();
  if (bound) {
    return;
  }
  bound = true;
  bindWorkbenchInteractionShield(host);
  host.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    const mode = target?.closest("[data-cim-mode]")?.dataset?.cimMode;
    if (mode) {
      setActiveRepresentation(mode);
      return;
    }
    const edgeMode = target?.closest("[data-cim-edge-mode]")?.dataset?.cimEdgeMode;
    if (edgeMode) {
      applyCimEdgeMode(edgeMode);
      return;
    }
    const addType = target?.closest("[data-cim-add-type]")?.dataset?.cimAddType;
    if (addType) {
      createCimElement(addType);
      return;
    }
    const openId = target?.closest("[data-cim-open]")?.dataset?.cimOpen;
    if (openId) {
      openCimDetail(openId);
      return;
    }
    const viewId = target?.closest("[data-cim-view]")?.dataset?.cimView;
    if (viewId && setActiveViewId(viewId)) {
      renderDiagramCallback?.();
      renderPaletteCallback?.();
      renderCimWorkbenchSurface();
      return;
    }
    if (target?.closest("[data-cim-create-root]")) {
      createRequiredRoot();
      return;
    }
    const exportFeature = target?.closest("[data-cim-export]")?.dataset?.cimExport;
    if (exportFeature) {
      exportCsv(exportFeature);
      return;
    }
    const sortKey = target?.closest("[data-cim-sort]")?.dataset?.cimSort;
    if (sortKey) {
      state.cimWorkbench.sortKey = sortKey;
      renderCimWorkbenchSurface();
      return;
    }
    const sliceToggle = target?.closest("[data-cim-slice-toggle]")?.dataset?.cimSliceToggle;
    if (sliceToggle) {
      cimSliceMenuOpen = cimSliceMenuOpen === sliceToggle ? "" : sliceToggle;
      renderCimWorkbenchSurface();
      return;
    }
    const sliceOption = target?.closest("[data-cim-slice-option]");
    if (sliceOption) {
      const optionKind = sliceOption.dataset.cimSliceOption;
      const value = sliceOption.dataset.cimSliceOptionValue || "";
      if (optionKind === "kind") {
        state.cimWorkbench.sliceKind = value;
        state.cimWorkbench.sliceValue = "";
      } else {
        state.cimWorkbench.sliceValue = value;
      }
      cimSliceMenuOpen = "";
      renderCimWorkbenchSurface();
      return;
    }
    const childButton = target?.closest("[data-cim-add-child]");
    if (childButton) {
      addContainedChild(
        childButton.dataset.cimAddChild,
        childButton.dataset.cimFeature,
        childButton.dataset.cimChildType,
      );
    }
  });
  host.addEventListener("change", (event) => {
    const target =
      event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement
        ? event.target
        : null;
    if (!target) {
      return;
    }
    if (target.dataset.cimRegister !== undefined) {
      setActiveRegister(target.value);
      renderCimWorkbenchSurface();
      return;
    }
    if (target.dataset.cimSearch !== undefined) {
      state.cimWorkbench.search = target.value;
      renderCimWorkbenchSurface();
      return;
    }
    if (target.dataset.cimSliceKind !== undefined) {
      state.cimWorkbench.sliceKind = target.value;
      state.cimWorkbench.sliceValue = "";
      renderCimWorkbenchSurface();
      return;
    }
    if (target.dataset.cimSliceValue !== undefined) {
      state.cimWorkbench.sliceValue = target.value;
      renderCimWorkbenchSurface();
      return;
    }
    if (target.dataset.cimMissingOnly !== undefined) {
      state.cimWorkbench.missingOnly = target.checked;
      renderCimWorkbenchSurface();
      return;
    }
    if (target.dataset.cimRootField) {
      updateRootField(target.dataset.cimRootField, target.value);
      return;
    }
    if (target.dataset.cimToggleRef) {
      toggleReference(
        target.dataset.cimToggleRef,
        target.dataset.cimFeature,
        target.dataset.cimTarget,
        target.checked,
      );
      return;
    }
    if (target.dataset.cimField && target.dataset.cimRow) {
      updateField(
        target.dataset.cimRow,
        target.dataset.cimField,
        target.type === "checkbox" ? target.checked : target.value,
        target.type,
      );
      return;
    }
    if (target.dataset.cimImport && target.files?.[0]) {
      void importCsv(target.dataset.cimImport, target.files[0]);
    }
  });
  host.addEventListener("input", (event) => {
    const target = event.target instanceof HTMLInputElement ? event.target : null;
    if (target?.dataset?.cimSearch !== undefined) {
      state.cimWorkbench.search = target.value;
      scheduleSearchRender();
    }
  });
  document.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (cimSliceMenuOpen && !target?.closest(".cim-slice-select-wrap")) {
      cimSliceMenuOpen = "";
      renderCimWorkbenchSurface();
    }
  });
}

export function initCimWorkbenchSurface({
  renderDiagram,
  renderPalette,
  openAttributePanel,
  openConnectionPanel,
} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  openAttributePanelCallback = openAttributePanel || openAttributePanelCallback;
  openConnectionPanelCallback = openConnectionPanel || openConnectionPanelCallback;
  ensureSurface();
  bindSurfaceEvents();
}
