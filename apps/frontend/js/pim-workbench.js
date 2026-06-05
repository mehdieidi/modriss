import {state} from './state.js';
import {el} from './dom.js';
import {escapeHtml} from './utils.js';
import {getDefaultNode} from './diagram.js';
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  saveCurrentTabGraphState,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingTypeMatches,
  modelingViewDefinition,
  modelingViewLenses
} from './modeling-config-data.js';
import {markModelDirty} from './model-save-ui.js';
import {setStatus} from './status.js';
import {
  activeWorkbenchRepresentation,
  bindWorkbenchInteractionShield,
  commitWorkbenchModelChange,
  downloadWorkbenchCsv,
  ensureWorkbenchSurface,
  renderLevelGuidePanel,
  renderWorkbenchSurfaceLayout,
  setWorkbenchRepresentation
} from './workbench-common.js';
import {
  addReferenceValue,
  elementLabel,
  missingRequiredFeatures,
  PIM_ROOT_CONTAINMENTS,
  refId,
  refIds
} from './pim-model-utils.js';

let surface = null;
let bound = false;
let renderDiagramCallback = null;
let renderPaletteCallback = null;
let openAttributePanelCallback = null;
let openConnectionPanelCallback = null;
let searchRenderTimer = 0;
let pimSliceMenuOpen = "";

const PIM_SLICE_KINDS = [
  ["", "All slices"],
  ["service", "Service"],
  ["environment", "Environment"],
  ["security", "Security"],
  ["production", "Production"],
  ["lifecycle", "Lifecycle"]
];

function scheduleSearchRender() {
  if (searchRenderTimer) {
    window.clearTimeout(searchRenderTimer);
  }
  searchRenderTimer = window.setTimeout(() => {
    searchRenderTimer = 0;
    renderPimWorkbenchSurface();
  }, 90);
}

const DEFAULT_REPRESENTATION_BY_PROFILE = {
  architecture: "diagram",
  api: "register",
  compute: "diagram",
  contracts: "register",
  data: "register",
  integration: "diagram",
  workflow: "diagram",
  security: "matrix",
  deployment: "matrix",
  policy: "register",
  configuration: "register",
  readiness: "board"
};

const PROFILE_REGISTERS = {
  architecture: "services",
  api: "apis",
  compute: "functions",
  contracts: "schemas",
  data: "dataStores",
  integration: "channels",
  workflow: "workflows",
  security: "principals",
  deployment: "deploymentUnits",
  policy: "policies",
  configuration: "configurations",
  readiness: "readiness"
};

const REGISTER_COLUMNS = {
  services: ["boundaryType", "ownerTeam", "externallyExposed", "ownsFunctions",
    "ownsApis", "ownsChannels", "ownsStores", "ownsWorkflows"],
  deploymentUnits: ["unitType", "independentlyDeployable", "contains",
    "targetEnvironments", "releaseStrategy", "versioningStrategy"],
  environments: ["environmentClass", "nameSuffix", "productionLike",
    "requiresApproval", "configurationSets", "parameters", "variables"],
  implementationProfile: ["primaryLanguage", "languageVersion",
    "packageManager", "sourceLayout", "testFramework", "buildCommand"],
  functions: ["functionKind", "executionModel", "publicEntryPoint",
    "stateless", "readsState", "writesState", "publishesEvents",
    "requiresIdempotency", "contract", "reads", "writes", "publishes",
    "subscribesTo", "callsAdapters", "usesSecrets"],
  apis: ["apiStyle", "publicName", "version", "basePath", "authRequired",
    "corsRequired", "externalConsumerFacing", "routes", "auth", "cors",
    "rateLimit"],
  schemas: ["schemaKind", "semanticVersion", "compatibility",
    "additionalPropertiesAllowed", "fields", "constraints",
    "externalSchemaUri"],
  eventTypes: ["semanticName", "version", "schema", "externalEvent",
    "auditEvent", "replayable", "containsPersonalData", "producedBy",
    "consumedBy"],
  channels: ["channelKind", "orderingRequirement", "deliverySemantics",
    "encrypted", "replayRequired", "deadLetterRequired", "eventTypes",
    "producers", "consumers"],
  schedules: ["scheduleExpression", "enabled", "timeZone"],
  triggers: ["triggerKind", "invocationMode", "enabled", "source",
    "invokesFunction", "startsWorkflow", "filterExpression"],
  dataStores: ["storeKind", "consistencyNeed", "persistent", "encrypted",
    "containsPersonalData", "ownedDataModels", "accessPatterns",
    "indexCandidates", "expectedDataVolume", "expectedAccessRate"],
  objectStores: ["objectTypes", "versioningRequired",
    "eventNotificationRequired",
    "objectMetadataSchemas", "emittedEvents"],
  dataAccesses: ["mode", "function", "store", "dataModels", "accessPatterns",
    "transactional", "purpose"],
  workflows: ["workflowKind", "executionSemantics", "longRunning",
    "stateful", "states", "startState", "endStates", "transitions",
    "humanApprovalRequired"],
  externalAdapters: ["externalSystemName", "protocolFamily",
    "endpointDescription", "credentialsRequired", "adapterFunctions",
    "credentials"],
  identityProviders: ["identityKind", "federationRequired", "mfaRequired",
    "tokenType", "principals"],
  principals: ["principalKind", "externalRef", "privileged", "permissions"],
  policies: ["policyScope", "productionRequired", "attachedTo",
    "authenticationRequired", "authorizationRequired",
    "encryptionAtRestRequired",
    "loggingEnabled", "metricsEnabled", "timeoutSeconds"],
  flows: ["flowPurpose", "criticalPath", "containsPersonalData", "source",
    "target", "eventType", "channel", "queue", "topic", "workflow", "adapter"],
  configurations: ["scope", "parameters", "environmentVariables",
    "environments", "appliesTo"],
  secrets: ["secretKind", "rotationRequired", "rotationFrequency",
    "environmentSpecific", "ownerTeam", "usedForCredentials"],
  readiness: ["readinessStatus", "transformationReady", "deploymentReady",
    "productionReady", "findings", "checks", "manualDecisions"],
  traceLinks: ["linkType", "source", "target", "sourceElementId",
    "targetElementId", "transformationRule", "confidence"]
};

const POLICY_REGISTER_TYPES = [
  "DataProtectionPolicy", "CompliancePolicy", "ResiliencePolicy",
  "TimeoutPolicy", "IdempotencyPolicy", "ConcurrencyPolicy",
  "RateLimitPolicy", "BatchPolicy", "OrderingPolicy", "CachePolicy",
  "BackupPolicy", "RetentionPolicy", "CostPolicy", "ObservabilityConfig",
  "CorsPolicy", "SecurityPolicy", "AuthPolicy", "AuthorizationPolicy",
  "RetryPolicy", "DeadLetterPolicy", "LoggingPolicy", "MetricPolicy",
  "MetricDimension", "TracingPolicy", "AlertPolicy", "Slo"
];

const PROTECTED_RESOURCE_TYPES = [
  "Api", "ApiRoute", "Function", "Secret", "DataStore", "ObjectStore",
  "ExternalAdapter", "Queue", "Topic", "EventBus", "Schedule",
  "IdentityProvider", "Principal", "Workflow", "WorkflowState"
];

const PIM_EDGE_MODES = {
  both: {label: "Both"},
  flows: {label: "Flows"},
  references: {label: "Refs"}
};

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function ensureSurface() {
  surface = ensureWorkbenchSurface(surface, "pimWorkbenchSurface");
  return surface;
}

function normalizeViewText(value) {
  return String(value || "").trim().toLowerCase().replaceAll(/[^a-z0-9]+/g,
      " ");
}

function activePimViewProfile() {
  const view = activeView();
  try {
    const definition = modelingViewDefinition("pim", view);
    if (definition?.viewpoint) {
      return String(definition.viewpoint);
    }
  } catch {
    // infer from view text
  }
  const text = normalizeViewText([view?.id, view?.name, view?.kind,
    view?.layoutProfile].filter(Boolean).join(" "));
  if (text.includes("api")) {
    return "api";
  }
  if (text.includes("compute") || text.includes("trigger")) {
    return "compute";
  }
  if (text.includes("contract") || text.includes("schema")) {
    return "contracts";
  }
  if (text.includes("data")) {
    return "data";
  }
  if (text.includes("integration") || text.includes(
      "channel")) {
    return "integration";
  }
  if (text.includes("workflow")) {
    return "workflow";
  }
  if (text.includes("security") || text.includes("access")) {
    return "security";
  }
  if (text.includes("deployment") || text.includes(
      "environment")) {
    return "deployment";
  }
  if (text.includes("policy") || text.includes("operation")) {
    return "policy";
  }
  if (text.includes("configuration") || text.includes(
      "secret")) {
    return "configuration";
  }
  if (text.includes("readiness") || text.includes("trace")) {
    return "readiness";
  }
  return "architecture";
}

function activeRepresentation(profile) {
  const viewId = activeView()?.id || "pim";
  return activeWorkbenchRepresentation(state.pimWorkbench, viewId, profile,
      DEFAULT_REPRESENTATION_BY_PROFILE);
}

function setActiveRepresentation(mode) {
  const viewId = activeView()?.id || "pim";
  setWorkbenchRepresentation(state.pimWorkbench, viewId, mode,
      renderPimWorkbenchSurface, renderDiagramCallback, renderPaletteCallback);
}

function activeRegister(profile) {
  const viewId = activeView()?.id || "pim";
  return state.pimWorkbench.registerByViewId[viewId]
      || PROFILE_REGISTERS[profile] || "functions";
}

function setActiveRegister(feature) {
  const viewId = activeView()?.id || "pim";
  state.pimWorkbench.registerByViewId[viewId] = feature;
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

function pimLensEntries() {
  try {
    return modelingViewLenses("pim");
  } catch {
    return [{key: "all", label: "All", types: []}];
  }
}

function pimLens(lensKey) {
  return pimLensEntries().find((entry) => entry.key === lensKey) || null;
}

function typeInPimLens(type, lens) {
  return safeArray(lens?.types).some((expected) => {
    try {
      return modelingTypeMatches("pim", expected, type);
    } catch {
      return expected === type;
    }
  });
}

function rootContainment(feature) {
  return PIM_ROOT_CONTAINMENTS.find((entry) => entry.feature === feature)
      || null;
}

function registerRows(feature) {
  if (feature === "traceLinks") {
    return relationships().filter((relationship) =>
        relationship.eClass === "TraceLink" || relationship.kind === "TRACE");
  }
  if (feature === "readiness") {
    return elementsMatchingTypes(["ProductionReadinessAssessment",
      "ReadinessFinding", "ReadinessCheck", "ManualDecision"]);
  }
  if (feature === "policies") {
    return dedupeById(elementsMatchingTypes(POLICY_REGISTER_TYPES));
  }
  const containment = rootContainment(feature);
  if (!containment) {
    return [];
  }
  const rows = elementsMatchingTypes(containment.types);
  if (containment.relationshipOnly) {
    rows.push(...relationships().filter((relationship) =>
        containment.types.includes(relationship.eClass)));
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

function refIdLabel(value) {
  const id = refId(value);
  const element = state.graph.elementsById.get(id);
  return element ? elementLabel(element) : String(id || "");
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

function matchesSearch(row) {
  const query = String(state.pimWorkbench.search || "").trim().toLowerCase();
  if (!query) {
    return true;
  }
  const haystack = [
    row.id, row.eClass, row.name, row.label, row.summary, row.description,
    row.modelTags, ...Object.values(row).filter((value) => typeof value
        === "string")
  ].join(" ").toLowerCase();
  return haystack.includes(query);
}

function rowInSlice(row) {
  const kind = String(state.pimWorkbench.sliceKind || "");
  const value = String(state.pimWorkbench.sliceValue || "");
  if (!kind) {
    return true;
  }
  if (kind === "lifecycle") {
    return !value || String(row.lifecycleStatus || "") === value;
  }
  if (kind === "environment") {
    if (!value) {
      return true;
    }
    return refIds(row.targetEnvironments).includes(value)
        || refIds(row.environments).includes(value);
  }
  if (kind === "service") {
    if (!value) {
      return true;
    }
    return ["ownsFunctions", "ownsApis", "ownsChannels", "ownsStores",
      "ownsWorkflows", "ownsAdapters", "services"].some((feature) =>
        refIds(row[feature]).includes(value));
  }
  if (kind === "security") {
    return Boolean(row.privileged || row.authRequired || row.secretsRequired
        || row.encryptionAtRestRequired || row.encrypted);
  }
  if (kind === "production") {
    return Boolean(row.productionRequired || row.requiredForProduction
        || row.productionLike);
  }
  return true;
}

function rowInActiveLens(row) {
  const lensKey = state.pimWorkbench.activeLens || "all";
  const lens = pimLens(lensKey);
  if (!lens || !lens.types.length) {
    return true;
  }
  const type = row?.eClass || row?.type;
  if (typeInPimLens(type, lens)) {
    return true;
  }
  return Object.values(row || {}).some((value) => {
    const ids = refIds(value);
    return ids.some((id) => {
      const target = state.graph?.elementsById?.get(id);
      return typeInPimLens(target?.eClass, lens);
    });
  });
}

function applyPimLens(lensKey) {
  const lens = pimLens(lensKey);
  if (!lens) {
    return;
  }
  state.pimWorkbench.activeLens = lensKey;
  const view = activeView();
  if (!view) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  view.filters ??= {};
  if (!Array.isArray(view.__pimBaseElementTypes)) {
    view.__pimBaseElementTypes = safeArray(view.filters.elementTypes).map(
        String);
  }
  const baseTypes = safeArray(view.__pimBaseElementTypes);
  const lensTypes = safeArray(lens.types);
  view.filters.elementTypes = lensTypes.length
      ? (baseTypes.length
          ? lensTypes.filter((type) => baseTypes.some((baseType) => {
            try {
              return modelingTypeMatches("pim", baseType, type)
                  || modelingTypeMatches("pim", type, baseType);
            } catch {
              return baseType === type;
            }
          }))
          : lensTypes)
      : baseTypes;
  if (lensTypes.length && !view.filters.elementTypes.length) {
    view.filters.elementTypes = lensTypes;
  }
  materializeActiveView();
  saveCurrentTabGraphState("pim");
  renderPimWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
  setStatus(`${lens.label} lens applied`);
}

function pimFlowRelationshipKinds(allKinds) {
  let labels = {};
  try {
    labels = modelingLevelConfig("pim").relationshipKindLabels || {};
  } catch {
    labels = {};
  }
  return safeArray(allKinds).filter((kind) => {
    const text = `${kind} ${labels[kind] || ""}`.toLowerCase();
    return /flow|invoke|route|target|transition|subscription|event|message|request|response|data access|external/.test(
        text);
  });
}

function applyPimEdgeMode(mode) {
  if (!PIM_EDGE_MODES[mode]) {
    return;
  }
  state.pimWorkbench.edgeMode = mode;
  const view = activeView();
  if (!view) {
    return;
  }
  syncActiveViewFromVisibleGraph();
  const allKinds = [...state.graph.relationshipsByKind.keys()].sort();
  const flowKinds = pimFlowRelationshipKinds(allKinds);
  view.filters ??= {};
  if (mode === "both") {
    view.filters.relationshipKinds = [];
  } else if (mode === "flows") {
    view.filters.relationshipKinds = flowKinds.length ? flowKinds
        : ["__PIM_NO_FLOW_EDGES__"];
  } else {
    const flowKindSet = new Set(flowKinds);
    const referenceKinds = allKinds.filter((kind) => !flowKindSet.has(kind));
    view.filters.relationshipKinds = referenceKinds.length ? referenceKinds
        : ["__PIM_NO_REFERENCE_EDGES__"];
  }
  materializeActiveView();
  saveCurrentTabGraphState("pim");
  renderPimWorkbenchSurface();
  renderDiagramCallback?.();
  renderPaletteCallback?.();
  setStatus(`${PIM_EDGE_MODES[mode].label} edge mode applied`);
}

function filteredRows(feature) {
  const rows = registerRows(feature).filter(matchesSearch).filter(rowInSlice)
  .filter(rowInActiveLens);
  const filtered = state.pimWorkbench.missingOnly ? rows.filter((row) =>
      missingRequiredFeatures(row).length) : rows;
  const sortKey = state.pimWorkbench.sortKey || "name";
  return filtered.sort((a, b) => valueText(a?.[sortKey] || a.name)
  .localeCompare(valueText(b?.[sortKey] || b.name)));
}

function fieldDefinition(type, fieldName) {
  try {
    const definition = modelingElementDefinition("pim", type);
    return [...safeArray(definition?.attributes),
      ...safeArray(definition?.references)].find((field) => field.name
        === fieldName) || null;
  } catch {
    return null;
  }
}

function controlForField(row, field) {
  const definition = fieldDefinition(row.eClass, field) || {};
  const value = row[field];
  const readonly = definition.readonly || field === "id";
  if (readonly) {
    return `<span class="cim-cell-readonly">${escapeHtml(
        valueText(value))}</span>`;
  }
  if (definition.kind === "reference" || Array.isArray(value)
      || (value && typeof value === "object")) {
    return `<span class="cim-ref-cell">${escapeHtml(valueText(value))}</span>`;
  }
  if (definition.fieldType === "boolean" || typeof value === "boolean") {
    return `<input class="cim-table-check" data-pim-field="${escapeHtml(field)}"
        data-pim-row="${escapeHtml(row.id)}" type="checkbox" ${
        value ? "checked" : ""}>`;
  }
  if (definition.fieldType === "select" && Array.isArray(definition.options)) {
    return `<select class="cim-table-input" data-pim-field="${escapeHtml(field)}"
        data-pim-row="${escapeHtml(row.id)}">
      <option value=""></option>
      ${definition.options.map((option) => `<option value="${escapeHtml(option)}"
          ${String(value || "") === String(option) ? "selected" : ""}>${
        escapeHtml(option)}</option>`).join("")}
    </select>`;
  }
  const type = definition.fieldType === "number" || typeof value === "number"
      ? "number" : "text";
  return `<input class="cim-table-input" data-pim-field="${escapeHtml(field)}"
      data-pim-row="${escapeHtml(row.id)}" type="${type}" value="${escapeHtml(
      valueText(value))}">`;
}

function rowTypeBadge(row) {
  const missing = missingRequiredFeatures(row);
  return `<span class="cim-type-badge">${escapeHtml(row.eClass || row.kind
      || "Element")}</span>${missing.length ? `<span class="cim-missing-badge"
      title="${escapeHtml(missing.join(", "))}">${missing.length}</span>`
      : ""}`;
}

function registerOptionsMarkup(activeFeature) {
  const entries = [
    ...PIM_ROOT_CONTAINMENTS.map((entry) => ({
      feature: entry.feature,
      title: entry.title
    })),
    {feature: "traceLinks", title: "Trace Links"}
  ];
  return entries.map((entry) => `<option value="${escapeHtml(entry.feature)}" ${
      activeFeature === entry.feature ? "selected" : ""}>${escapeHtml(
      entry.title)}</option>`).join("");
}

function renderRegister(profile) {
  const feature = activeRegister(profile);
  const rows = filteredRows(feature);
  const containment = rootContainment(feature);
  const columns = ["name", "lifecycleStatus",
    ...(REGISTER_COLUMNS[feature] || safeArray(containment?.types).flatMap(
        (type) => safeArray(modelingElementDefinition("pim", type)
            ?.visibleFields)))].filter(Boolean);
  const uniqueColumns = [...new Set(columns)].slice(0, 13);
  const addType = containment?.types?.find((type) => {
    try {
      return modelingElementDefinition("pim", type)?.creatable !== false;
    } catch {
      return false;
    }
  });
  return `
    <div class="cim-toolbar">
      <select class="cim-select" data-pim-register>${registerOptionsMarkup(
      feature)}</select>
      ${addType ? `<button class="cim-action" data-pim-add-type="${escapeHtml(
      addType)}"
          type="button">Add ${escapeHtml(addType)}</button>` : ""}
      <button class="cim-action" data-pim-export="${escapeHtml(feature)}" type="button">Export CSV</button>
    </div>
    <div class="cim-table-wrap">
      <table class="cim-table">
        <thead><tr>
          <th>Element</th>
          ${uniqueColumns.map(
      (column) => `<th data-pim-sort="${escapeHtml(column)}">${
          escapeHtml(column)}</th>`).join("")}
          <th></th>
        </tr></thead>
        <tbody>
          ${rows.length ? rows.map((row) => `<tr>
            <td>
              <button class="cim-link" data-pim-open="${escapeHtml(row.id)}"
                  type="button">${escapeHtml(elementLabel(row))}</button>
              <div class="cim-row-meta">${rowTypeBadge(row)}</div>
            </td>
            ${uniqueColumns.map(
      (column) => `<td>${controlForField(row, column)}</td>`).join("")}
            <td><button class="cim-icon-action" data-pim-open="${escapeHtml(
      row.id)}"
                type="button">Open</button></td>
          </tr>`).join("") : `<tr><td colspan="${uniqueColumns.length + 2}"
              class="cim-empty">No rows in this slice.</td></tr>`}
        </tbody>
      </table>
    </div>`;
}

function count(type) {
  return elementsMatchingTypes(Array.isArray(type) ? type : [type]).length;
}

function renderDashboard() {
  const rootMissing = [];
  if (!count("ServerlessService")) {
    rootMissing.push("service");
  }
  if (!count("DeploymentUnit")) {
    rootMissing.push("deployment unit");
  }
  if (!count("Environment")) {
    rootMissing.push("environment");
  }
  if (!count("Function")) {
    rootMissing.push("function");
  }
  const cards = [
    ["Services", count("ServerlessService")],
    ["Functions", count("Function")],
    ["APIs", count("Api")],
    ["Schemas", count("Schema")],
    ["Events", count("EventType")],
    ["Channels", count(["Queue", "Topic", "EventBus"])],
    ["Stores", count(["DataStore", "ObjectStore"])],
    ["Workflows", count("Workflow")],
    ["Policies", count(["ResiliencePolicy", "ObservabilityConfig",
      "SecurityPolicy", "AuthPolicy", "AuthorizationPolicy"])],
    ["Missing",
      elements().filter((item) => missingRequiredFeatures(item).length).length]
  ];
  return `
    <div class="cim-dashboard-grid">
      ${cards.map(([label, value]) => `<div class="cim-metric">
        <strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span>
      </div>`).join("")}
    </div>
    <div class="cim-dashboard-actions">
      <button class="cim-action" data-pim-create-root type="button">
        ${rootMissing.length ? "Complete PIM Root" : "Refresh Root Links"}
      </button>
    </div>`;
}

function renderMatrix(profile) {
  if (profile === "deployment") {
    const units = filteredRows("deploymentUnits");
    const environments = elementsMatchingTypes(["Environment"]);
    return matrixTable("Deployment Units by Environment", units, environments,
        (unit, environment) => refIds(unit.targetEnvironments).includes(
            environment.id) ? "target" : false,
        "Create deployment units and environments to populate the matrix.");
  }
  if (profile === "security") {
    const principals = filteredRows("principals");
    const resources = elementsMatchingTypes(PROTECTED_RESOURCE_TYPES);
    return matrixTable("Principal Permissions by Protected Resource",
        principals, resources, (principal, resource) => {
          const permissionIds = refIds(principal.permissions);
          const matches = elementsMatchingTypes(["Permission"]).filter(
              (permission) => permissionIds.includes(permission.id)
                  && refIds(permission.targetResource).includes(resource.id));
          return matches.length ? `${matches.length}` : false;
        },
        "Create principals, permissions and protected resources to populate the matrix.");
  }
  if (profile === "policy") {
    const policies = filteredRows("policies");
    const targets = elements().filter((item) => item.id && item.eClass
        && !POLICY_REGISTER_TYPES.includes(item.eClass));
    return matrixTable("Policy Attachments", policies, targets,
        (policy, target) => refIds(policy.attachedTo).includes(target.id)
            ? (policy.productionRequired ? "prod" : "x") : false,
        "Create policies and attach them to targets to populate the matrix.");
  }
  if (profile === "data") {
    return matrixTable("Functions by Data Stores",
        elementsMatchingTypes(["Function"]),
        elementsMatchingTypes(["DataStore", "ObjectStore"]),
        (fn, store) => refIds(fn.reads).includes(store.id) ? "read"
            : refIds(fn.writes).includes(store.id) ? "write"
                : relationshipExists(fn.id, store.id),
        "Create functions and stores to populate the matrix.");
  }
  if (profile === "integration") {
    return matrixTable("Event Channels by Consumers",
        elementsMatchingTypes(["Queue", "Topic", "EventBus"]),
        elementsMatchingTypes(["Function", "Workflow", "ExternalAdapter"]),
        (channel, target) => refIds(channel.consumers).includes(target.id)
            || refIds(channel.workflowConsumers).includes(target.id)
            || relationshipExists(channel.id, target.id),
        "Create channels and consumers to populate the matrix.");
  }
  const register = activeRegister(profile);
  const rows = filteredRows(register).slice(0, 80);
  const columns = profile === "deployment"
      ? elementsMatchingTypes(["Environment"])
      : profile === "security"
          ? elementsMatchingTypes(["Api", "ApiRoute", "Function", "Secret",
            "DataStore", "ObjectStore", "Workflow", "Queue", "Topic",
            "EventBus"])
          : elements().slice(0, 16);
  return `<div class="cim-matrix-wrap">
    <table class="cim-matrix">
      <thead><tr><th>${escapeHtml(register)}</th>${columns.map((column) =>
      `<th>${escapeHtml(elementLabel(column))}</th>`).join("")}</tr></thead>
      <tbody>${rows.map((row) => `<tr>
        <th><button class="cim-link" data-pim-open="${escapeHtml(row.id)}"
            type="button">${escapeHtml(elementLabel(row))}</button></th>
        ${columns.map((column) => {
    const linked = rowReferences(row).has(column.id)
        || relationshipExists(row.id, column.id)
        || relationshipExists(column.id, row.id);
    return `<td class="${linked ? "is-linked" : ""}">${linked ? "x" : ""}</td>`;
  }).join("")}
      </tr>`).join("")}</tbody>
    </table>
  </div>`;
}

function rowReferences(row) {
  const ids = new Set();
  Object.values(row || {}).forEach((value) => refIds(value).forEach((id) =>
      ids.add(id)));
  return ids;
}

function relationshipExists(sourceId, targetId) {
  return relationships().some((relationship) => relationship.sourceElementId
      === sourceId && relationship.targetElementId === targetId);
}

function matrixTable(title, rows, columns, linked, empty = "No matrix rows.") {
  if (!rows.length || !columns.length) {
    return `<section class="cim-matrix-section">
      <div class="cim-section-title">${escapeHtml(title)}</div>
      <div class="cim-empty">${escapeHtml(empty)}</div>
    </section>`;
  }
  return `<section class="cim-matrix-section">
    <div class="cim-section-title">${escapeHtml(title)}</div>
    <div class="cim-matrix-wrap">
      <table class="cim-matrix">
        <thead><tr><th></th>${columns.map((column) =>
      `<th><span>${escapeHtml(elementLabel(column))}</span></th>`).join("")}</tr></thead>
        <tbody>${rows.map((row) => `<tr>
          <th><button class="cim-link" data-pim-open="${escapeHtml(row.id)}"
              type="button">${escapeHtml(elementLabel(row))}</button></th>
          ${columns.map((column) => {
    const value = linked(row, column);
    return `<td class="${value ? "is-linked" : ""}">${
        value ? escapeHtml(value === true ? "x" : value) : ""}</td>`;
  }).join("")}
        </tr>`).join("")}</tbody>
      </table>
    </div>
  </section>`;
}

function renderReadinessBoard() {
  const findings = elementsMatchingTypes(["ReadinessFinding"]);
  const checks = elementsMatchingTypes(["ReadinessCheck"]);
  const decisions = elementsMatchingTypes(["ManualDecision"]);
  const groups = [
    ["Findings", findings],
    ["Checks", checks],
    ["Decisions", decisions]
  ];
  return `<div class="cim-board">
    ${groups.map(([title, rows]) => `<section class="cim-board-lane">
      <div class="cim-section-title">${escapeHtml(title)} ${rows.length}</div>
      ${rows.length ? rows.map((row) => `<button class="cim-board-card"
          data-pim-open="${escapeHtml(row.id)}" type="button">
        <strong>${escapeHtml(elementLabel(row))}</strong>
        <span>${escapeHtml(
      row.message || row.question || row.checkId || row.severity || "")}</span>
      </button>`).join("") : `<div class="cim-empty">No items.</div>`}
    </section>`).join("")}
  </div>`;
}

function detailRowsFor(row) {
  const definition = modelingElementDefinition("pim", row.eClass);
  const fields = [
    "id", "name", "summary", "description", "lifecycleStatus",
    ...safeArray(definition?.visibleFields),
    ...safeArray(definition?.attributes).filter((field) => field?.required)
    .map((field) => field.name),
    ...safeArray(definition?.references).filter((field) => field?.required)
    .map((field) => field.name)
  ];
  return [...new Set(fields)].filter((field) =>
      Object.prototype.hasOwnProperty.call(row, field)).slice(0, 18);
}

function renderDetailProjection(profile) {
  const selected = state.selectedNodeId
      ? state.graph?.elementsById?.get(state.selectedNodeId) : null;
  const register = activeRegister(profile);
  const row = selected || filteredRows(register)[0] || elements()[0];
  if (!row) {
    return `<section class="cim-detail-projection">
      <div class="cim-empty">No PIM element selected.</div>
    </section>`;
  }
  const missing = missingRequiredFeatures(row);
  const traces = relationships().filter((relationship) =>
      relationship.eClass === "TraceLink" || relationship.kind
      === "TRACE").filter(
      (relationship) => relationship.sourceElementId === row.id
          || relationship.targetElementId === row.id);
  const readiness = elementsMatchingTypes(["ReadinessFinding", "ReadinessCheck",
    "ManualDecision"]).filter((item) => refIds(item.affectedElements).includes(
      row.id));
  const fields = detailRowsFor(row);
  return `<section class="cim-detail-projection">
    <div class="cim-detail-title">
      <strong>${escapeHtml(elementLabel(row))}</strong>
      <span>${escapeHtml(row.eClass || "PIM element")}</span>
    </div>
    <div class="cim-detail-grid">
      ${fields.map((field) => `<div>
        <span>${escapeHtml(field)}</span>
        <strong>${escapeHtml(valueText(row[field]))}</strong>
      </div>`).join("")}
      <div><span>missing required</span><strong>${escapeHtml(
      missing.join(", ") || "none")}</strong></div>
      <div><span>trace links</span><strong>${traces.length}</strong></div>
      <div><span>readiness items</span><strong>${readiness.length}</strong></div>
    </div>
    <div class="cim-dashboard-actions">
      <button class="cim-action" data-pim-open="${escapeHtml(row.id)}"
          type="button">Open Full Inspector</button>
    </div>
  </section>`;
}

function renderControls(profile, representation) {
  const modes = [
    ["diagram", "Diagram"],
    ["dashboard", "Dashboard"],
    ["register", "Register"],
    ["matrix", "Matrix"],
    ["board", "Board"],
    ["detail", "Detail"],
    ["guide", "Guide"]
  ];
  return `<div class="cim-surface-header">
    <div class="cim-surface-title">
      <strong>${escapeHtml(activeView()?.name || "PIM View")}</strong>
      <span>${escapeHtml(profile)}</span>
    </div>
    <div class="cim-mode-tabs">
      ${modes.map(([mode, label]) =>
      `<button class="${representation === mode ? "is-active" : ""}"
          data-pim-mode="${mode}" type="button">${escapeHtml(
          label)}</button>`).join("")}
    </div>
    <div class="pim-edge-toolbar" aria-label="PIM edge visibility">
      <span>Edges</span>
      <div class="pim-edge-buttons">
        ${Object.entries(PIM_EDGE_MODES).map(([key, mode]) =>
      `<button class="${(state.pimWorkbench.edgeMode || "both") === key
          ? "is-active" : ""}"
              data-pim-edge-mode="${key}" type="button">${escapeHtml(
          mode.label)}</button>`).join("")}
      </div>
    </div>
    <div class="cim-filter-row">
      <input data-pim-search placeholder="Search PIM" value="${escapeHtml(
      state.pimWorkbench.search || "")}">
      ${pimSliceSelectMarkup("kind")}
      ${pimSliceSelectMarkup("value")}
      <label class="cim-check-label"><input data-pim-missing-only type="checkbox" ${
      state.pimWorkbench.missingOnly ? "checked" : ""}> Missing required</label>
    </div>
  </div>`;
}

function sliceItems() {
  const kind = state.pimWorkbench.sliceKind;
  if (kind === "service") {
    return elementsMatchingTypes(["ServerlessService"]);
  }
  if (kind === "environment") {
    return elementsMatchingTypes(["Environment"]);
  }
  if (kind === "lifecycle") {
    return [...new Set(elements().map((item) =>
        String(item.lifecycleStatus || "")).filter(Boolean))]
    .map((status) => ({id: status, name: status}));
  }
  if (kind === "security") {
    return [{id: "true", name: "Security-sensitive items"}];
  }
  if (kind === "production") {
    return [{id: "true", name: "Production items"}];
  }
  return [];
}

function pimSliceKindLabel(value = state.pimWorkbench.sliceKind) {
  return PIM_SLICE_KINDS.find(([key]) => key === value)?.[1] || "All slices";
}

function pimSliceValueLabel() {
  const value = state.pimWorkbench.sliceValue || "";
  if (!value) {
    return "Any";
  }
  const item = sliceItems().find((candidate) => candidate.id === value);
  return item ? elementLabel(item) : value;
}

function sliceOptionButton({value, label, selected, optionKind}) {
  return `<button class="workbench-view-option${selected ? " is-active" : ""}"
            type="button"
            data-pim-slice-option="${escapeHtml(optionKind)}"
            data-pim-slice-option-value="${escapeHtml(value)}"
            role="option"
            aria-selected="${selected ? "true" : "false"}">
      <span class="workbench-view-option-label">${escapeHtml(label)}</span>
    </button>`;
}

function pimSliceMenuMarkup(optionKind) {
  if (optionKind === "kind") {
    return PIM_SLICE_KINDS.map(([value, label]) => sliceOptionButton({
      value,
      label,
      selected: state.pimWorkbench.sliceKind === value,
      optionKind
    })).join("");
  }
  const options = [{id: "", name: "Any"}, ...sliceItems()];
  return options.map((item) => sliceOptionButton({
    value: item.id,
    label: item.id ? elementLabel(item) : item.name,
    selected: (state.pimWorkbench.sliceValue || "") === item.id,
    optionKind
  })).join("");
}

function pimSliceSelectMarkup(optionKind) {
  const isKind = optionKind === "kind";
  const open = pimSliceMenuOpen === optionKind;
  const label = isKind ? pimSliceKindLabel() : pimSliceValueLabel();
  const dataAttr = isKind ? "data-pim-slice-kind" : "data-pim-slice-value";
  return `<div class="workbench-view-select-wrap workbench-slice-select-wrap${open
      ? " is-open" : ""}">
      <button class="sidebar-select workbench-view-select workbench-slice-select"
              type="button"
              ${dataAttr}
              data-pim-slice-toggle="${optionKind}"
              aria-haspopup="listbox"
              aria-expanded="${open ? "true" : "false"}">
        <span class="workbench-view-select-label">${escapeHtml(label)}</span>
      </button>
      <span class="workbench-view-select-caret" aria-hidden="true"></span>
      <div class="workbench-view-menu workbench-slice-menu${open ? "" : " hidden"}"
           role="listbox">
        ${pimSliceMenuMarkup(optionKind)}
      </div>
    </div>`;
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
    return renderLevelGuidePanel("pim");
  }
  return "";
}

export function renderPimWorkbenchSurface() {
  const host = ensureSurface();
  const profile = activePimViewProfile();
  const representation = activeRepresentation(profile);
  const controls = renderControls(profile, representation);
  renderWorkbenchSurfaceLayout({
    host,
    activeType: state.activeType,
    expectedType: "pim",
    minimized: state.modelingToolsMinimized,
    representation,
    controlsHtml: controls,
    bodyHtml: renderBody(profile, representation),
    workbenchState: state.pimWorkbench,
    surfaceActiveClass: "pim-surface-active",
    surfaceDockClass: "pim-surface-dock"
  });
}

function currentCenter() {
  const rect = el.canvasViewport?.getBoundingClientRect();
  if (!rect) {
    return {x: 120, y: 120};
  }
  return {
    x: Math.round((rect.width / 2 - state.viewport.x) / state.viewport.scale),
    y: Math.round((rect.height / 2 - state.viewport.y) / state.viewport.scale)
  };
}

function addNode(type, x, y, name = "") {
  const node = getDefaultNode("pim", type, Math.round(x), Math.round(y));
  if (name) {
    node.label = name;
    node.meta.name = name;
    node.meta.label = name;
  }
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function connect(source, target, kind) {
  if (!source?.id || !target?.id) {
    return null;
  }
  const edge = {
    id: `e-${source.id}-${target.id}-${kind}-${Date.now()}`,
    sourceId: source.id,
    targetId: target.id,
    kind
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  return edge;
}

function attachChildToParent(childNode, parent, feature) {
  childNode.meta.__ownerId = parent.id;
  childNode.meta.__containmentFeature = feature;
  addReferenceValue(parent.meta || parent, feature, childNode.id, true);
  const visibleParent = state.nodesById.get(parent.id);
  if (visibleParent?.meta) {
    visibleParent.meta[feature] = parent.meta?.[feature] || parent[feature];
  }
  connect(parent, childNode, "CONTAINS");
}

function createPimElement(type) {
  syncActiveViewFromVisibleGraph();
  const center = currentCenter();
  const node = addNode(type, center.x, center.y, type);
  switch (type) {
    case "Function": {
      node.meta.functionKind = node.meta.functionKind || "COMMAND_HANDLER";
      const contract = addNode("FunctionContract", center.x - 260, center.y,
          "Function Contract");
      contract.meta.contractVersion = "1.0.0";
      attachChildToParent(contract, node, "contract");
      break;
    }
    case "Api": {
      node.meta.apiStyle ||= "RESOURCE_ORIENTED_HTTP";
      const route = addNode("ApiRoute", center.x + 260, center.y, "GET /");
      route.meta.method = "GET";
      route.meta.pathTemplate = "/";
      attachChildToParent(route, node, "routes");
      break;
    }
    case "EventType": {
      node.meta.semanticName ||= node.label;
      const schema = elementsMatchingTypes(["Schema"])[0]
          || addNode("Schema", center.x - 260, center.y, "Event Schema");
      schema.meta.schemaKind ||= "EVENT";
      node.meta.schema = schema.id;
      connect(node, schema, "USES");
      break;
    }
    case "DataStore": {
      node.meta.storeKind ||= "DOCUMENT";
      node.meta.consistencyNeed ||= "EVENTUAL";
      const model = addNode("DataModel", center.x - 220, center.y + 140,
          "Data Model");
      model.meta.dataModelKind = "ENTITY";
      const access = addNode("AccessPattern", center.x + 220, center.y + 140,
          "Access Pattern");
      access.meta.patternName = "Primary lookup";
      attachChildToParent(model, node, "ownedDataModels");
      attachChildToParent(access, node, "accessPatterns");
      break;
    }
    case "Workflow": {
      node.meta.workflowKind ||= "ORCHESTRATION";
      const start = addNode("WorkflowState", center.x - 220, center.y + 140,
          "Start");
      const end = addNode("WorkflowState", center.x + 220, center.y + 140,
          "End");
      start.meta.stateKind = "TASK";
      end.meta.stateKind = "SUCCESS";
      end.meta.terminal = true;
      attachChildToParent(start, node, "states");
      attachChildToParent(end, node, "states");
      node.meta.startState = start.id;
      node.meta.endStates = [end.id];
      connect(start, end, "TRANSITION");
      break;
    }
    case "Queue":
      node.meta.channelKind = "QUEUE";
      node.meta.orderingRequirement ||= "NONE";
      node.meta.deliverySemantics ||= "AT_LEAST_ONCE";
      break;
    case "Topic":
      node.meta.channelKind = "TOPIC";
      node.meta.orderingRequirement ||= "NONE";
      node.meta.deliverySemantics ||= "AT_LEAST_ONCE";
      break;
    case "EventBus":
      node.meta.channelKind = "EVENT_BUS";
      node.meta.orderingRequirement ||= "NONE";
      node.meta.deliverySemantics ||= "AT_LEAST_ONCE";
      break;
    case "DeploymentUnit": {
      node.meta.unitType ||= "SERVICE";
      const env = elementsMatchingTypes(["Environment"])[0]
          || addNode("Environment", center.x + 260, center.y, "Dev");
      env.meta.environmentClass ||= "DEV";
      node.meta.targetEnvironments = [env.id];
      connect(node, env, "DEPLOYS_TO");
      break;
    }
    case "Environment":
      node.meta.environmentClass ||= "DEV";
      break;
    case "ImplementationProfile":
      node.meta.primaryLanguage ||= "TYPESCRIPT";
      node.meta.packageManager ||= "NPM";
      break;
    case "Schedule":
      node.meta.scheduleExpression ||= "rate(1 day)";
      node.meta.enabled = true;
      break;
    case "IdentityProvider":
      node.meta.identityKind ||= "USER_DIRECTORY";
      break;
    case "Principal":
      node.meta.principalKind ||= "ROLE";
      break;
    case "Secret":
      node.meta.secretKind ||= "TOKEN";
      break;
    case "ConfigurationSet":
      node.meta.scope ||= "APPLICATION";
      break;
    default:
      break;
  }
  commitModelChange(`Added ${type}`);
  openAttributePanelCallback?.(node.id);
}

function createRequiredRoot() {
  state.baseModel ??= {};
  state.baseModel.architectureStyle ||= "EVENT_DRIVEN_SERVERLESS";
  state.baseModel.providerIndependent = true;
  const center = currentCenter();
  const service = elementsMatchingTypes(["ServerlessService"])[0]
      || addNode("ServerlessService", center.x - 280, center.y,
          "Serverless Service");
  service.meta.boundaryType ||= "CAPABILITY_BASED";
  const fn = elementsMatchingTypes(["Function"])[0]
      || addNode("Function", center.x, center.y, "Function");
  fn.meta.functionKind ||= "COMMAND_HANDLER";
  service.meta.ownsFunctions = [...new Set(
      [...refIds(service.meta.ownsFunctions), fn.id])];
  connect(service, fn, "OWNS");
  const env = elementsMatchingTypes(["Environment"])[0]
      || addNode("Environment", center.x + 300, center.y - 90, "Dev");
  env.meta.environmentClass ||= "DEV";
  const unit = elementsMatchingTypes(["DeploymentUnit"])[0]
      || addNode("DeploymentUnit", center.x + 300, center.y + 90,
          "Deployment Unit");
  unit.meta.unitType ||= "SERVICE";
  unit.meta.contains = [...new Set([...refIds(unit.meta.contains), fn.id])];
  unit.meta.targetEnvironments = [...new Set(
      [...refIds(unit.meta.targetEnvironments), env.id])];
  connect(unit, fn, "DEPLOYS");
  connect(unit, env, "DEPLOYS_TO");
  commitModelChange("Completed PIM root model");
  openAttributePanelCallback?.(fn.id);
}

function openPimDetail(id) {
  if (state.graph?.elementsById?.has(id) || state.nodesById?.has(id)) {
    openAttributePanelCallback?.(id);
    return;
  }
  if (state.graph?.relationshipsById?.has(id)) {
    openConnectionPanelCallback?.(id);
  }
}

function commitModelChange(message) {
  commitWorkbenchModelChange({
    typeKey: "pim",
    renderWorkbench: renderPimWorkbenchSurface,
    renderDiagram: renderDiagramCallback,
    renderPalette: renderPaletteCallback,
    message,
    syncActiveViewFromVisibleGraph,
    saveCurrentTabGraphState,
    markModelDirty,
    setStatus
  });
}

function updateField(rowId, field, rawValue, inputType = "text") {
  const row = state.graph.elementsById.get(rowId)
      || state.graph.relationshipsById.get(rowId);
  if (!row) {
    return;
  }
  const definition = fieldDefinition(row.eClass, field);
  let value = rawValue;
  if (inputType === "checkbox") {
    value = Boolean(rawValue);
  } else if (definition?.fieldType === "number") {
    value = Number(rawValue);
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

function updateRootField(field, value) {
  state.baseModel ??= {};
  state.baseModel[field] = value;
  commitModelChange(`Updated ${field}`);
}

function exportCsv(feature) {
  const rows = filteredRows(feature);
  const columns = ["id", "eClass", "name",
    ...(REGISTER_COLUMNS[feature] || [])];
  downloadWorkbenchCsv(`pim-${feature}.csv`, rows, columns, valueText);
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
    const mode = target?.closest("[data-pim-mode]")?.dataset?.pimMode;
    if (mode) {
      setActiveRepresentation(mode);
      return;
    }
    const edgeMode = target?.closest("[data-pim-edge-mode]")?.dataset
        ?.pimEdgeMode;
    if (edgeMode) {
      applyPimEdgeMode(edgeMode);
      return;
    }
    const addType = target?.closest("[data-pim-add-type]")?.dataset?.pimAddType;
    if (addType) {
      createPimElement(addType);
      return;
    }
    const openId = target?.closest("[data-pim-open]")?.dataset?.pimOpen;
    if (openId) {
      openPimDetail(openId);
      return;
    }
    if (target?.closest("[data-pim-create-root]")) {
      createRequiredRoot();
      return;
    }
    const exportFeature = target?.closest(
        "[data-pim-export]")?.dataset?.pimExport;
    if (exportFeature) {
      exportCsv(exportFeature);
      return;
    }
    const sortKey = target?.closest("[data-pim-sort]")?.dataset?.pimSort;
    if (sortKey) {
      state.pimWorkbench.sortKey = sortKey;
      renderPimWorkbenchSurface();
      return;
    }
    const sliceToggle = target?.closest("[data-pim-slice-toggle]")?.dataset
        ?.pimSliceToggle;
    if (sliceToggle) {
      pimSliceMenuOpen = pimSliceMenuOpen === sliceToggle ? "" : sliceToggle;
      renderPimWorkbenchSurface();
      return;
    }
    const sliceOption = target?.closest("[data-pim-slice-option]");
    if (sliceOption) {
      const optionKind = sliceOption.dataset.pimSliceOption;
      const value = sliceOption.dataset.pimSliceOptionValue || "";
      if (optionKind === "kind") {
        state.pimWorkbench.sliceKind = value;
        state.pimWorkbench.sliceValue = "";
      } else {
        state.pimWorkbench.sliceValue = value;
      }
      pimSliceMenuOpen = "";
      renderPimWorkbenchSurface();
    }
  });
  host.addEventListener("change", (event) => {
    const target = event.target instanceof HTMLInputElement
    || event.target instanceof HTMLSelectElement ? event.target : null;
    if (!target) {
      return;
    }
    if (target.dataset.pimRegister !== undefined) {
      setActiveRegister(target.value);
      renderPimWorkbenchSurface();
      return;
    }
    if (target.dataset.pimSearch !== undefined) {
      state.pimWorkbench.search = target.value;
      renderPimWorkbenchSurface();
      return;
    }
    if (target.dataset.pimSliceKind !== undefined) {
      state.pimWorkbench.sliceKind = target.value;
      state.pimWorkbench.sliceValue = "";
      renderPimWorkbenchSurface();
      return;
    }
    if (target.dataset.pimSliceValue !== undefined) {
      state.pimWorkbench.sliceValue = target.value;
      renderPimWorkbenchSurface();
      return;
    }
    if (target.dataset.pimMissingOnly !== undefined) {
      state.pimWorkbench.missingOnly = target.checked;
      renderPimWorkbenchSurface();
      return;
    }
    if (target.dataset.pimRootField) {
      updateRootField(target.dataset.pimRootField, target.value);
      return;
    }
    if (target.dataset.pimField && target.dataset.pimRow) {
      updateField(target.dataset.pimRow, target.dataset.pimField,
          target.type === "checkbox" ? target.checked : target.value,
          target.type);
    }
  });
  host.addEventListener("input", (event) => {
    const target = event.target instanceof HTMLInputElement ? event.target
        : null;
    if (target?.dataset?.pimSearch !== undefined) {
      state.pimWorkbench.search = target.value;
      scheduleSearchRender();
    }
  });
  document.addEventListener("click", (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (pimSliceMenuOpen && !target?.closest(".workbench-slice-select-wrap")) {
      pimSliceMenuOpen = "";
      renderPimWorkbenchSurface();
    }
  });
}

export function initPimWorkbenchSurface({
  renderDiagram,
  renderPalette,
  openAttributePanel,
  openConnectionPanel
} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderPaletteCallback = renderPalette || renderPaletteCallback;
  openAttributePanelCallback = openAttributePanel || openAttributePanelCallback;
  openConnectionPanelCallback = openConnectionPanel
      || openConnectionPanelCallback;
  ensureSurface();
  bindSurfaceEvents();
}
