import { state } from "./state.js";
import { el } from "./dom.js";
import { MODEL_TYPES } from "./config.js";
import { api } from "./api.js";
import { setStatus } from "./status.js";
import {
  startConnectionFromNode,
  cancelConnectionDraw,
  syncDiagramRenderer,
  syncRendererSelection,
} from "./canvas.js";
import { markModelDirty } from "./model-save-ui.js";
import { isMobileViewport } from "./responsive.js";
import { syncMobileDockState } from "./mobile-ui.js";
import {
  defaultRootModel,
  getDefaultNode,
  relationshipIdsFromModel,
  toDiagram,
} from "./diagram.js";
import { confirmAction } from "./confirm-action.js";
import { escapeHtml } from "./utils.js";
import {
  modelingContainmentsForType,
  modelingElementDefinition,
  isModelingLevel,
  modelingLegalKinds,
  modelingLevelConfig,
  modelingRelationshipKindLabel,
  modelingRootType,
  modelingSemanticEdgeObjectRules,
} from "./modeling-config-data.js";
import {
  addNodeToGraphAndActiveView,
  markGraphRelationshipsDirty,
  reconcileElementRelationships,
  removeElementFromGraph,
  removeRelationshipFromGraph,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import {
  addReferenceValue,
  modelTypeMatches,
  elementLabel,
  missingRequiredFeatures,
  nestedContainmentsForType,
  refIds,
} from "./model-utils.js";
import { captureDiagramUndoSnapshot, pushDiagramUndoSnapshot } from "./undo.js";

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function configuredTraceKind() {
  return String(modelingLevelConfig(state.activeType).relationshipSemantics?.traceKind || "");
}

function isTraceRelationship(relationship) {
  const traceKind = configuredTraceKind();
  if (traceKind && relationship.kind === traceKind) {
    return true;
  }
  const eClass = String(relationship.eClass || relationship.type || "");
  if (!eClass || !traceKind) {
    return false;
  }
  return modelingSemanticEdgeObjectRules(state.activeType).some(
    (rule) =>
      String(rule?.eClass || rule?.edgeObjectType || "") === eClass &&
      safeArray(rule?.matchKinds).map(String).includes(traceKind),
  );
}

// Fields managed by canvas – shown read-only
const READONLY_ATTR_KEYS = new Set(["id", "eClass", "x", "y"]);
// Fields skipped entirely (rendered via canvas label editing)
const SKIP_ATTR_KEYS = new Set(["label", "name", "tags", "status"]);
const ROOT_SKIP_ATTR_KEYS = new Set([
  "diagram",
  "graph",
  "views",
  "fragments",
  "activeViewId",
  "traceLinks",
  "assumptions",
  "validationIssues",
  "manualBacklog",
]);
const TRACE_ATTR_KEYS = new Set([
  "sourceReference",
  "sourceExcerpt",
  "sourceQualifiedName",
  "sourceUri",
  "sourceLine",
  "traceId",
  "generatedFrom",
  "generatedByTransformation",
  "rationale",
  "reviewStatus",
  "reviewNotes",
  "manuallyMaintained",
]);
let attrFieldSeq = 0;

function resetAttrFieldIds() {
  attrFieldSeq = 0;
}

function nextAttrFieldDomId(key) {
  attrFieldSeq += 1;
  const safeKey =
    String(key || "field")
      .trim()
      .replace(/[^a-zA-Z0-9_-]+/g, "-")
      .replace(/^-+|-+$/g, "") || "field";
  return `attr-${safeKey}-${attrFieldSeq}`;
}

const IDENTITY_FIELDS = new Set([
  "id",
  "name",
  "label",
  "summary",
  "description",
  "documentation",
  "modelTags",
  "externalId",
  "lifecycleStatus",
]);
const GOVERNANCE_FIELDS = new Set();
const POLICY_SECURITY_FIELDS = new Set();
const OVERVIEW_BADGE_FIELDS = [];
const OVERVIEW_BOOLEAN_BADGES = new Map();

// ── Open / close ──────────────────────────────────────────────────────────────

function activeModelName() {
  return (
    state.tabs[state.activeType]?.modelName ||
    state.baseModel?.name ||
    `${state.activeType}-model`
  ).trim();
}

function ensureRootModel() {
  if (!state.baseModel || typeof state.baseModel !== "object") {
    state.baseModel = defaultRootModel(state.activeType, activeModelName());
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].baseModel = state.baseModel;
    }
  }
  state.baseModel.eClass ||= modelingRootType(state.activeType);
  state.baseModel.name ||= activeModelName();
  return state.baseModel;
}

export function openRootModelAttributePanel() {
  if (!isModelingLevel(state.activeType)) {
    return;
  }
  const root = ensureRootModel();
  const rootType = modelingRootType(state.activeType);
  const definition = rootType ? modelingElementDefinition(state.activeType, rootType) : null;

  state.selectedRootModel = true;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;

  el.attrPanelType.textContent = definition?.displayName || rootType || "Model";
  el.attrPanelTitle.textContent = root.name || activeModelName();
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = false;
    el.attrPanelApplyBtn.textContent = "✓ Apply Model";
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = true;
  }

  renderRootModelFields(root, definition);

  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    syncMobileDockState();
  }
  syncRendererSelection();
}

export function openAttributePanel(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }

  state.selectedRootModel = false;
  state.selectedNodeId = nodeId;
  state.selectedNodeIds = new Set([nodeId]);
  state.selectedConnectionId = null;

  el.attrPanelType.textContent = node.type;
  el.attrPanelTitle.textContent = node.label;
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = false;
    el.attrPanelApplyBtn.textContent = "✓ Apply Changes";
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = false;
    el.attrPanelDeleteBtn.textContent = "🗑 Delete Element";
  }

  renderAttributeFields(node);

  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    syncMobileDockState();
  }
  syncRendererSelection();
}

export function closeAttributePanel() {
  if (state.connectMode && state.connectSourceId) {
    cancelConnectionDraw({ silent: true });
  }
  state.selectedRootModel = false;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  el.attributePanel.classList.add("hidden");
  el.workspace.classList.remove("attr-open");
  el.workspace.classList.remove("mobile-right-open");
  syncMobileDockState();
  syncRendererSelection();
}

export function openConnectionPanel(connectionId) {
  const connection = state.connectionsById.get(connectionId);
  if (!connection) {
    return;
  }
  state.selectedRootModel = false;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = connectionId;

  const source = state.nodesById.get(connection.sourceId);
  const target = state.nodesById.get(connection.targetId);
  el.attrPanelType.textContent = "Connection";
  el.attrPanelTitle.textContent = `${
    source?.label || connection.sourceId
  } → ${target?.label || connection.targetId}`;
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = false;
    el.attrPanelApplyBtn.textContent = "✓ Apply Connection";
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = false;
    el.attrPanelDeleteBtn.textContent = "🗑 Delete Connection";
  }

  renderConnectionFields(connection, source, target);

  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    syncMobileDockState();
  }
  syncRendererSelection();
}

function renderConnectionFields(connection, source, target) {
  renderConfiguredConnectionFields(connection, source, target);
}

function renderConfiguredConnectionFields(connection, source, target) {
  el.attrPanelBody.innerHTML = "";
  resetAttrFieldIds();
  const relationship = state.graph?.relationshipsById?.get(connection.id) || connection;
  const semanticType = relationship.eClass || "Connection";
  let definition = null;
  try {
    definition =
      semanticType !== "Connection"
        ? modelingElementDefinition(state.activeType, semanticType)
        : null;
  } catch {
    definition = null;
  }
  const sections = relationshipInspectorSections();
  sections.identity.appendChild(
    buildAttrField("kind", connection.kind, {
      fieldType: "text",
      readonly: true,
    }),
  );
  sections.identity.appendChild(
    buildAttrField("source", source?.label || connection.sourceId, {
      fieldType: "text",
      readonly: true,
    }),
  );
  sections.identity.appendChild(
    buildAttrField("target", target?.label || connection.targetId, {
      fieldType: "text",
      readonly: true,
    }),
  );
  sections.identity.appendChild(
    buildAttrField("id", connection.id, {
      fieldType: "text",
      readonly: true,
    }),
  );

  const rendered = new Set(["id", "kind", "source", "target", "name"]);
  const fields = [
    ...(definition?.attributes || []),
    ...(definition?.references || []).map((reference) => ({
      ...reference,
      fieldType: "reference",
    })),
  ];
  fields.forEach((field) => {
    if (!field?.name || rendered.has(field.name) || field.readonly) {
      return;
    }
    rendered.add(field.name);
    const value = Object.prototype.hasOwnProperty.call(relationship, field.name)
      ? relationship[field.name]
      : field.defaultValue;
    const section = field.fieldType === "reference" ? sections.relationships : sections.core;
    section.appendChild(buildAttrField(field.name, value, field));
  });
  Object.entries(relationship).forEach(([key, value]) => {
    if (
      rendered.has(key) ||
      [
        "sourceElementId",
        "targetElementId",
        "sourceType",
        "targetType",
        "semanticFeature",
        "semanticSourceElementId",
        "semanticTargetElementId",
        "visualOnly",
      ].includes(key)
    ) {
      return;
    }
    const section = TRACE_ATTR_KEYS.has(key) ? sections.trace : sections.core;
    section.appendChild(
      buildAttrField(key, value, {
        fieldType: inferFieldType(value),
        readonly: READONLY_ATTR_KEYS.has(key),
      }),
    );
  });
  appendConfiguredValidationSummary(sections.validation, relationship, semanticType);
  appendEmptyHints(sections);
  renderAttrTabs(sections);
}

// ── Render attribute form fields ──────────────────────────────────────────────

function renderAttributeFields(node) {
  el.attrPanelBody.innerHTML = "";
  resetAttrFieldIds();
  const meta = node.meta || {};
  let definition = null;
  try {
    definition = modelingElementDefinition(state.activeType, node.type);
  } catch {
    definition = null;
  }
  renderConfiguredAttributeFields(node, meta, definition);
}

function rootEditableFields(definition) {
  return [
    ...(definition?.attributes || []),
    ...(definition?.references || [])
      .filter((reference) => !reference?.containment)
      .map((reference) => ({
        ...reference,
        fieldType: "reference",
      })),
  ].filter((field) => field?.name && !ROOT_SKIP_ATTR_KEYS.has(field.name));
}

function renderRootModelFields(root, definition) {
  el.attrPanelBody.innerHTML = "";
  resetAttrFieldIds();
  const sections = semanticInspectorSections();
  const rootType = modelingRootType(state.activeType);
  const rendered = new Set();

  sections.identity.appendChild(
    buildAttrField("name", root.name || activeModelName(), {
      fieldType: "text",
    }),
  );
  rendered.add("name");
  sections.identity.appendChild(
    buildAttrField("id", root.id || "", {
      fieldType: "text",
      readonly: true,
    }),
  );
  rendered.add("id");
  sections.identity.appendChild(
    buildAttrField("eClass", root.eClass || rootType, {
      fieldType: "text",
      readonly: true,
    }),
  );
  rendered.add("eClass");

  rootEditableFields(definition).forEach((field) => {
    const key = field.name;
    if (rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    const value = Object.prototype.hasOwnProperty.call(root, key) ? root[key] : field.defaultValue;
    semanticSectionForField(sections, key, field).appendChild(buildAttrField(key, value, field));
  });

  Object.entries(root).forEach(([key, value]) => {
    if (
      rendered.has(key) ||
      ROOT_SKIP_ATTR_KEYS.has(key) ||
      SKIP_ATTR_KEYS.has(key) ||
      Array.isArray(value) ||
      (value && typeof value === "object")
    ) {
      return;
    }
    rendered.add(key);
    semanticSectionForField(sections, key, {
      fieldType: inferFieldType(value),
      readonly: READONLY_ATTR_KEYS.has(key),
    }).appendChild(
      buildAttrField(key, value, {
        fieldType: inferFieldType(value),
        readonly: READONLY_ATTR_KEYS.has(key),
      }),
    );
  });

  appendConfiguredValidationSummary(sections.validation, root, rootType);
  appendEmptyHints(sections);
  renderAttrTabs(sections);
}

function renderConfiguredAttributeFields(node, meta, definition) {
  const sections = semanticInspectorSections();
  const rendered = new Set(["label", "name"]);

  appendElementOverviewSection(sections.overview, node, definition, { includeTitle: false });
  const labelKey = identityFieldForNode(node, definition);
  sections.identity.appendChild(
    buildAttrField(labelKey, node.label, {
      fieldType: "text",
    }),
  );
  sections.identity.appendChild(
    buildAttrField("id", node.id, {
      fieldType: "text",
      readonly: true,
    }),
  );
  rendered.add("id");

  const configuredFields = [
    ...(definition?.attributes || []),
    ...(definition?.references || []).map((reference) => ({
      ...reference,
      fieldType: "reference",
    })),
  ];
  configuredFields.forEach((field) => {
    const key = field?.name;
    if (!key || rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    const value = Object.prototype.hasOwnProperty.call(meta, key) ? meta[key] : field.defaultValue;
    semanticSectionForField(sections, key, field).appendChild(buildAttrField(key, value, field));
  });

  Object.entries(meta).forEach(([key, value]) => {
    if (rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    semanticSectionForField(sections, key, {
      fieldType: inferFieldType(value),
      readonly: READONLY_ATTR_KEYS.has(key),
    }).appendChild(
      buildAttrField(key, value, {
        fieldType: inferFieldType(value),
        readonly: READONLY_ATTR_KEYS.has(key),
      }),
    );
  });
  appendLegalOutgoingRelationships(node, sections.relationships);
  appendContainmentSections(node, sections.relationships);
  appendTraceabilitySection(node, sections.trace, { includeTitle: false, rendered });
  appendConfiguredValidationSummary(sections.validation, meta, node.type);
  appendEmptyHints(sections);
  renderAttrTabs(sections);
  bindContainmentSectionActions();
}

function identityFieldForNode(node, definition) {
  const configured = String(modelingLevelConfig(state.activeType).labelField || "").trim();
  const fieldNames = new Set([
    ...(definition?.attributes || []).map((field) => field.name),
    ...(definition?.references || []).map((field) => field.name),
  ]);
  if (configured && fieldNames.has(configured)) {
    return configured;
  }
  if (Object.prototype.hasOwnProperty.call(node?.meta || {}, "label")) {
    return "label";
  }
  return "name";
}

function _renderRelationshipAttributeFields(node, meta, definition) {
  const sections = relationshipInspectorSections();
  const rendered = new Set(["label", "name"]);

  appendElementOverviewSection(sections.overview, node, definition, { includeTitle: false });
  sections.identity.appendChild(
    buildAttrField("name", node.label, {
      fieldType: "text",
    }),
  );
  rendered.add("name");
  rendered.add("label");
  sections.identity.appendChild(
    buildAttrField("id", node.id, {
      fieldType: "text",
      readonly: true,
    }),
  );
  rendered.add("id");

  const configuredFields = [
    ...(definition?.attributes || []),
    ...(definition?.references || []).map((reference) => ({
      ...reference,
      fieldType: "reference",
    })),
  ];
  configuredFields.forEach((field) => {
    const key = field?.name;
    if (!key || rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    const value = Object.prototype.hasOwnProperty.call(meta, key) ? meta[key] : field.defaultValue;
    relationshipSectionForField(sections, key, field).appendChild(
      buildAttrField(key, value, field),
    );
  });

  Object.entries(meta).forEach(([key, value]) => {
    if (rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    relationshipSectionForField(sections, key, {
      fieldType: inferFieldType(value),
      readonly: READONLY_ATTR_KEYS.has(key),
    }).appendChild(
      buildAttrField(key, value, {
        fieldType: inferFieldType(value),
        readonly: READONLY_ATTR_KEYS.has(key),
      }),
    );
  });
  appendLegalOutgoingRelationships(node, sections.relationships);
  appendContainmentSections(node, sections.relationships);
  appendTraceabilitySection(node, sections.trace, { includeTitle: false, rendered });
  appendConfiguredValidationSummary(sections.validation, meta, node.type);
  appendEmptyHints(sections);
  renderAttrTabs(sections);
  bindContainmentSectionActions();
}

function _renderPlatformAttributeFields(node, meta, definition) {
  const sections = platformInspectorSections();
  const rendered = new Set(["label", "name"]);

  appendElementOverviewSection(sections.overview, node, definition, { includeTitle: false });
  sections.identity.appendChild(
    buildAttrField("name", node.label, {
      fieldType: "text",
    }),
  );
  rendered.add("name");
  rendered.add("label");
  sections.identity.appendChild(
    buildAttrField("id", node.id, {
      fieldType: "text",
      readonly: true,
    }),
  );
  rendered.add("id");

  const configuredFields = [
    ...(definition?.attributes || []),
    ...(definition?.references || []).map((reference) => ({
      ...reference,
      fieldType: "reference",
    })),
  ];
  configuredFields.forEach((field) => {
    const key = field?.name;
    if (!key || rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    const value = Object.prototype.hasOwnProperty.call(meta, key) ? meta[key] : field.defaultValue;
    platformSectionForField(sections, key, field).appendChild(buildAttrField(key, value, field));
  });

  Object.entries(meta).forEach(([key, value]) => {
    if (rendered.has(key) || SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    rendered.add(key);
    platformSectionForField(sections, key, {
      fieldType: inferFieldType(value),
      readonly: READONLY_ATTR_KEYS.has(key),
    }).appendChild(
      buildAttrField(key, value, {
        fieldType: inferFieldType(value),
        readonly: READONLY_ATTR_KEYS.has(key),
      }),
    );
  });
  appendLegalOutgoingRelationships(node, sections.relationships);
  appendContainmentSections(node, sections.containment);
  appendTraceabilitySection(node, sections.trace, { includeTitle: false, rendered });
  appendPlatformValidationSummary(sections.validation, meta, node.type);
  appendEmptyHints(sections);
  renderAttrTabs(sections);
  bindContainmentSectionActions();
}

function relationshipInspectorSections() {
  return {
    overview: createAttrTabSection("overview", "Overview"),
    identity: createAttrTabSection("identity", "Identity"),
    core: createAttrTabSection("core", "Core Properties"),
    relationships: createAttrTabSection("relationships", "Relationships"),
    policies: createAttrTabSection("policies", "Policies / Security"),
    trace: createAttrTabSection("trace", "Trace & Review"),
    validation: createAttrTabSection("validation", "Validation"),
  };
}

function semanticInspectorSections() {
  return {
    overview: createAttrTabSection("overview", "Overview"),
    identity: createAttrTabSection("identity", "Identity"),
    core: createAttrTabSection("core", "Business Fields"),
    relationships: createAttrTabSection("relationships", "Relationships"),
    governance: createAttrTabSection("governance", "Governance"),
    trace: createAttrTabSection("trace", "Trace & Review"),
    validation: createAttrTabSection("validation", "Validation"),
  };
}

function platformInspectorSections() {
  return {
    overview: createAttrTabSection("overview", "Overview"),
    identity: createAttrTabSection("identity", "Identity"),
    operations: createAttrTabSection("operations", "Runtime / Operations"),
    security: createAttrTabSection("security", "Security"),
    relationships: createAttrTabSection("relationships", "References"),
    containment: createAttrTabSection("containment", "Contained Details"),
    trace: createAttrTabSection("trace", "Trace & Review"),
    validation: createAttrTabSection("validation", "Validation"),
  };
}

function createAttrTabSection(tab, title) {
  const section = document.createElement("section");
  section.className = "attr-tab-section";
  section.dataset.attrTabPanel = tab;
  section.dataset.attrTabTitle = title;
  section.appendChild(buildAttrSectionTitle(title));
  return section;
}

function relationshipSectionForField(sections, key, field = {}) {
  if (TRACE_ATTR_KEYS.has(key)) {
    return sections.trace;
  }
  if (IDENTITY_FIELDS.has(key)) {
    return sections.identity;
  }
  if (isPolicySecurityField(key, field)) {
    return sections.policies;
  }
  if (field.fieldType === "reference" || field.kind === "reference") {
    return sections.relationships;
  }
  return sections.core;
}

function semanticSectionForField(sections, key, field = {}) {
  if (TRACE_ATTR_KEYS.has(key)) {
    return sections.trace;
  }
  if (IDENTITY_FIELDS.has(key)) {
    return sections.identity;
  }
  if (isGovernanceField(key, field)) {
    return sections.governance;
  }
  if (field.fieldType === "reference" || field.kind === "reference") {
    return sections.relationships;
  }
  return sections.core;
}

function platformSectionForField(sections, key, field = {}) {
  if (TRACE_ATTR_KEYS.has(key)) {
    return sections.trace;
  }
  if (field.containment) {
    return sections.containment;
  }
  if (field.fieldType === "reference" || field.kind === "reference") {
    return sections.relationships;
  }
  if (isSecurityField(key, field)) {
    return sections.security;
  }
  return sections.operations;
}

function isSecurityField(key, field = {}) {
  const targetType = String(field.targetType || "");
  return /role|policy|principal|auth|kms|secret|permission|public|cors|vpc|subnet|security/i.test(
    `${key} ${targetType}`,
  );
}

function configuredTargetText(field = {}) {
  const targetType = String(field.targetType || "");
  let targetDefinition = null;
  try {
    targetDefinition = targetType ? modelingElementDefinition(state.activeType, targetType) : null;
  } catch {
    targetDefinition = null;
  }
  return [
    field.category,
    field.group,
    targetDefinition?.category,
    targetDefinition?.visualRole,
    targetType,
  ]
    .filter(Boolean)
    .join(" ");
}

function isPolicySecurityField(key, field = {}) {
  return (
    POLICY_SECURITY_FIELDS.has(key) ||
    /policy|principal|protected|identity|secret/i.test(configuredTargetText(field))
  );
}

function isGovernanceField(key, field = {}) {
  return (
    GOVERNANCE_FIELDS.has(key) ||
    /govern|require|constraint|policy|risk|readiness/i.test(configuredTargetText(field))
  );
}

function appendEmptyHints(sections) {
  Object.values(sections).forEach((section) => {
    if (section.children.length <= 1) {
      const hint = document.createElement("div");
      hint.className = "attr-field-hint";
      hint.textContent = "No fields in this section.";
      section.appendChild(hint);
    }
  });
}

function renderAttrTabs(sections) {
  const entries = Object.entries(sections);
  const tabbar = document.createElement("div");
  tabbar.className = "attr-tabbar";
  entries.forEach(([tab, section], index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `attr-tab-btn${index === 0 ? " active" : ""}`;
    button.dataset.attrTab = tab;
    button.textContent = section.dataset.attrTabTitle || tab;
    button.addEventListener("click", () => activateAttrTab(tab));
    tabbar.appendChild(button);
    section.classList.toggle("hidden", index !== 0);
  });
  el.attrPanelBody.appendChild(tabbar);
  entries.forEach(([, section]) => el.attrPanelBody.appendChild(section));
}

function appendElementOverviewSection(host, node, definition, { includeTitle = true } = {}) {
  const overview = buildElementOverview(node, definition);
  if (!overview) {
    return;
  }
  if (includeTitle) {
    host.appendChild(buildAttrSectionTitle("Overview"));
  }
  host.appendChild(overview);
}

function buildElementOverview(node, definition) {
  const meta = node.meta || {};
  const container = document.createElement("div");
  container.className = "attr-element-overview";

  const badges = overviewBadges(node, definition);
  if (badges.length) {
    const badgeRow = document.createElement("div");
    badgeRow.className = "attr-overview-badges";
    badges.forEach(({ label, issue }) => {
      const badge = document.createElement("span");
      badge.className = `attr-overview-badge${issue ? " issue" : ""}`;
      badge.textContent = label;
      badgeRow.appendChild(badge);
    });
    container.appendChild(badgeRow);
  }

  const summary = document.createElement("div");
  summary.className = "attr-overview-summary";
  let hasSummary = false;

  const overviewRows = [];
  const visibleFields = Array.isArray(definition?.visibleFields) ? definition.visibleFields : [];
  visibleFields.slice(0, 8).forEach((field) => {
    const text = overviewValueText(meta[field]);
    if (text) {
      overviewRows.push([formatOverviewKey(field), text]);
    }
  });
  if (overviewRows.length) {
    hasSummary = true;
    summary.appendChild(buildOverviewGroup("Key Fields", overviewRows));
  }

  const referenceRows = (definition?.references || [])
    .filter((reference) => !reference.containment)
    .map((reference) => [
      formatOverviewKey(reference.name),
      overviewValueText(meta[reference.name]),
    ])
    .filter(([, value]) => value)
    .slice(0, 6);
  if (referenceRows.length) {
    hasSummary = true;
    summary.appendChild(buildOverviewGroup("References", referenceRows));
  }

  if (hasSummary) {
    container.appendChild(summary);
  }

  if (!container.children.length) {
    return null;
  }
  return container;
}

function buildOverviewGroup(title, rows) {
  const group = document.createElement("div");
  group.className = "attr-overview-group";
  const heading = document.createElement("strong");
  heading.textContent = title;
  group.appendChild(heading);
  rows.forEach(([label, value]) => {
    const row = document.createElement("div");
    row.className = "attr-overview-row";
    const key = document.createElement("span");
    key.textContent = label;
    const val = document.createElement("em");
    val.textContent = value;
    row.append(key, val);
    group.appendChild(row);
  });
  return group;
}

function overviewBadges(node, definition) {
  const meta = node.meta || {};
  const badges = [];
  const seen = new Set();
  const pushBadge = (label, issue = false) => {
    const text = String(label || "").trim();
    if (!text || seen.has(`${issue}:${text}`)) {
      return;
    }
    seen.add(`${issue}:${text}`);
    badges.push({ label: text, issue });
  };

  OVERVIEW_BADGE_FIELDS.forEach((field) => {
    const value = meta[field];
    if (typeof value === "string" && value.trim()) {
      pushBadge(value, /disabled|public|blocking|not recommended/i.test(value));
    }
  });
  OVERVIEW_BOOLEAN_BADGES.forEach((label, field) => {
    if (meta[field] === true) {
      pushBadge(label, /blocking/i.test(label));
    }
  });

  const visibleFields = Array.isArray(definition?.visibleFields) ? definition.visibleFields : [];
  visibleFields.slice(0, 6).forEach((field) => {
    const value = meta[field];
    if (typeof value === "boolean" && value) {
      pushBadge(formatOverviewKey(field));
    }
  });

  return badges;
}

function _semanticOverviewIssueBadges(_node) {
  return [];
}

function _isPastTenseBusinessEventName(value) {
  const text = String(value || "")
    .trim()
    .toLowerCase();
  if (!text) {
    return false;
  }
  const words = text.split(/\s+/).filter(Boolean);
  const first = words[0] || "";
  const last = words[words.length - 1] || "";
  return (
    first.endsWith("ed") ||
    last.endsWith("ed") ||
    /(?:submitted|created|updated|deleted|confirmed|rejected|approved|cancelled|canceled|completed|failed|paid|sent|received|placed|registered|enrolled|verified|accepted|declined)$/.test(
      first,
    )
  );
}

function overviewValueText(value) {
  if (Array.isArray(value)) {
    if (!value.length) {
      return "";
    }
    return (
      value
        .slice(0, 3)
        .map((item) => refSummaryLabel(item))
        .filter(Boolean)
        .join(", ") + (value.length > 3 ? ` +${value.length - 3}` : "")
    );
  }
  if (typeof value === "boolean") {
    return value ? "Yes" : "";
  }
  if (value && typeof value === "object") {
    return refSummaryLabel(value);
  }
  return String(value || "").trim();
}

function refSummaryLabel(value) {
  if (!value) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "object") {
    return value.name || value.label || value.$ref || value.id || "";
  }
  return String(value);
}

function formatOverviewKey(value) {
  return String(value || "")
    .replaceAll(/([A-Z])/g, " $1")
    .replaceAll(/[_-]+/g, " ")
    .trim()
    .replace(/^./, (match) => match.toUpperCase());
}

function activateAttrTab(tab) {
  el.attrPanelBody.querySelectorAll("[data-attr-tab]").forEach((button) => {
    button.classList.toggle("active", button.dataset.attrTab === tab);
  });
  el.attrPanelBody.querySelectorAll("[data-attr-tab-panel]").forEach((section) => {
    section.classList.toggle("hidden", section.dataset.attrTabPanel !== tab);
  });
}

function buildAttrSectionTitle(title) {
  const section = document.createElement("div");
  section.className = "attr-section-title";
  section.textContent = title;
  return section;
}

function appendTraceabilitySection(
  node,
  host = el.attrPanelBody,
  { includeTitle = true, rendered = null } = {},
) {
  if (!isModelingLevel(state.activeType)) {
    return;
  }
  if (includeTitle) {
    host.appendChild(buildAttrSectionTitle("Traceability / Review"));
  }
  const traceLinks = [];
  state.graph?.relationshipsById?.forEach((relationship) => {
    if (!isTraceRelationship(relationship)) {
      return;
    }
    if (
      relationship.sourceElementId === node.id ||
      relationship.targetElementId === node.id ||
      relationship.source === node.id ||
      relationship.target === node.id
    ) {
      traceLinks.push(relationship);
    }
  });
  const summary = document.createElement("div");
  summary.className = "attr-trace-summary";
  summary.innerHTML = traceLinks.length
    ? traceLinks
        .map((link) => {
          const direction =
            (link.sourceElementId || link.source) === node.id ? "outgoing" : "incoming";
          const otherId =
            direction === "outgoing"
              ? link.targetElementId || link.target
              : link.sourceElementId || link.source;
          const other = state.graph?.elementsById?.get(otherId);
          return `<div class="attr-trace-row"><span>${direction}</span><strong>${escapeAttr(
            link.linkType || link.kind || configuredTraceKind(),
          )}</strong><em>${escapeAttr(
            other?.name || other?.label || otherId || "external",
          )}</em></div>`;
        })
        .join("")
    : `<div class="attr-field-hint">No trace links for this element.</div>`;
  host.appendChild(summary);
  TRACE_ATTR_KEYS.forEach((key) => {
    if (rendered?.has(key) || host.querySelector(`[data-attr-key="${CSS.escape(key)}"]`)) {
      return;
    }
    const booleanField = key === "generatedByTransformation" || key === "manuallyMaintained";
    if (!Object.prototype.hasOwnProperty.call(node.meta || {}, key)) {
      node.meta[key] = booleanField ? false : "";
    }
    rendered?.add(key);
    host.appendChild(
      buildAttrField(key, node.meta?.[key], {
        fieldType: booleanField ? "boolean" : inferFieldType(node.meta?.[key]),
      }),
    );
  });
}

function legalOutgoingRelationshipOptions(node) {
  if (!node || !isModelingLevel(state.activeType)) {
    return [];
  }
  let level = null;
  try {
    level = modelingLevelConfig(state.activeType);
  } catch {
    return [];
  }
  const targetTypes = (level.elements || [])
    .map((entry) => String(entry?.type || "").trim())
    .filter(Boolean)
    .filter((type) => {
      try {
        const definition = modelingElementDefinition(state.activeType, type);
        return (
          !definition?.relationshipElement && !definition?.abstract && !definition?.supportOnly
        );
      } catch {
        return true;
      }
    });
  const byKind = new Map();
  targetTypes.forEach((targetType) => {
    let kinds = [];
    try {
      kinds = modelingLegalKinds(state.activeType, node.type, targetType);
    } catch {
      kinds = [];
    }
    kinds.forEach((kind) => {
      const key = String(kind || "").trim();
      if (!key) {
        return;
      }
      const entry = byKind.get(key) || {
        kind: key,
        label: modelingRelationshipKindLabel(state.activeType, key),
        targets: new Set(),
      };
      entry.targets.add(targetType);
      byKind.set(key, entry);
    });
  });
  return [...byKind.values()]
    .map((entry) => ({
      ...entry,
      targets: [...entry.targets].sort((a, b) => a.localeCompare(b)),
    }))
    .sort((a, b) => a.label.localeCompare(b.label) || a.kind.localeCompare(b.kind));
}

function connectionDrawActiveForNode(nodeId) {
  return Boolean(
    state.connectMode && state.connectSourceId === nodeId && state.preferredConnectionKind,
  );
}

function buildLegalRelationshipDrawBanner(node) {
  const kind = state.preferredConnectionKind;
  const label = modelingRelationshipKindLabel(state.activeType, kind);
  const banner = document.createElement("div");
  banner.className = "attr-relationship-draw-active";
  banner.setAttribute("role", "status");
  banner.dataset.legalRelationshipDrawBanner = "true";

  const text = document.createElement("span");
  text.textContent = `Drawing ${label} — click a highlighted target on the canvas`;

  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "btn btn-secondary btn-sm";
  cancel.textContent = "Cancel";
  cancel.title = "Exit draw mode (Esc)";
  cancel.addEventListener("click", () => {
    cancelConnectionDraw();
  });

  banner.append(text, cancel);
  return banner;
}

function renderLegalOutgoingRelationshipsSection(node) {
  const options = legalOutgoingRelationshipOptions(node);
  const section = document.createElement("div");
  section.className = "attr-section attr-legal-relationships";
  section.appendChild(buildAttrSectionTitle("Legal Outgoing Relationships"));

  const drawActive = connectionDrawActiveForNode(node.id);
  if (drawActive) {
    section.appendChild(buildLegalRelationshipDrawBanner(node));
  }

  if (!options.length) {
    const hint = document.createElement("div");
    hint.className = "attr-field-hint";
    hint.textContent = "No legal outgoing relationship types for this element.";
    section.appendChild(hint);
    return section;
  }

  const list = document.createElement("div");
  list.className = "attr-legal-relationship-list";
  options.forEach((option) => {
    const isActiveKind = drawActive && option.kind === state.preferredConnectionKind;
    const row = document.createElement("div");
    row.className = "attr-legal-relationship-row";
    if (isActiveKind) {
      row.classList.add("attr-relationship-draw-selected");
    }
    const body = document.createElement("div");
    body.className = "attr-legal-relationship-body";
    const title = document.createElement("strong");
    title.textContent = option.label || option.kind;
    const targets = document.createElement("span");
    const visibleTargets = option.targets.slice(0, 5).join(", ");
    targets.textContent = `${visibleTargets}${
      option.targets.length > 5 ? ` +${option.targets.length - 5}` : ""
    }`;
    body.append(title, targets);
    const action = document.createElement("button");
    action.type = "button";
    action.className = isActiveKind ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm";
    action.textContent = isActiveKind ? "Cancel" : "Draw";
    action.title = isActiveKind ? `Cancel drawing ${option.kind}` : `Draw ${option.kind}`;
    action.setAttribute("aria-pressed", isActiveKind ? "true" : "false");
    action.addEventListener("click", () => {
      if (isActiveKind) {
        cancelConnectionDraw();
        return;
      }
      startConnectionFromNode(node.id, option.kind);
    });
    row.append(body, action);
    list.appendChild(row);
  });
  section.appendChild(list);
  return section;
}

function syncLegalRelationshipDrawSection() {
  const nodeId = state.selectedNodeId;
  if (!nodeId || el.attributePanel?.classList.contains("hidden")) {
    return;
  }
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  const existing = el.attrPanelBody?.querySelector(".attr-legal-relationships");
  if (!existing) {
    return;
  }
  if (connectionDrawActiveForNode(nodeId)) {
    activateAttrTab("relationships");
  }
  existing.replaceWith(renderLegalOutgoingRelationshipsSection(node));
}

function appendLegalOutgoingRelationships(node, host = el.attrPanelBody) {
  host.appendChild(renderLegalOutgoingRelationshipsSection(node));
}

function containmentEntriesForType(type) {
  try {
    const configured = modelingContainmentsForType(state.activeType, type);
    if (configured.length) {
      return configured.filter((entry) => !entry.relationshipOnly && entry.types?.length);
    }
  } catch {
    return [];
  }
  const entries = [];
  entries.push(...nestedContainmentsForType(state.activeType, type));
  const byFeature = new Map();
  entries.forEach((entry) => {
    if (!entry?.feature) {
      return;
    }
    const current = byFeature.get(entry.feature) || {
      feature: entry.feature,
      types: [],
    };
    current.types = [...new Set([...current.types, ...(entry.types || [])])].filter(
      (childType) => childType,
    );
    byFeature.set(entry.feature, current);
  });
  return [...byFeature.values()].filter((entry) => entry.types.length);
}

function containmentChildren(parent, feature) {
  const parentElement = state.graph?.elementsById?.get(parent.id);
  const ids = new Set(refIds(parentElement?.[feature] ?? parent.meta?.[feature]));
  const children = [];
  state.graph?.elementsById?.forEach((element) => {
    if (
      ids.has(element.id) ||
      (element.__ownerId === parent.id && element.__containmentFeature === feature)
    ) {
      children.push(element);
    }
  });
  const seen = new Set();
  return children.filter((child) => {
    if (!child?.id || seen.has(child.id)) {
      return false;
    }
    seen.add(child.id);
    return true;
  });
}

function appendContainmentSections(node, host = el.attrPanelBody) {
  const entries = containmentEntriesForType(node.type);
  if (!entries.length) {
    return;
  }
  entries.forEach((entry) => {
    const section = document.createElement("div");
    section.className = "attr-section attr-containment-section";
    const children = containmentChildren(node, entry.feature);
    section.innerHTML = `
      <div class="attr-section-title">${escapeAttr(entry.feature)}</div>
      <div class="attr-containment-actions">
        ${entry.types
          .map(
            (type) => `<button class="btn btn-secondary btn-sm"
            data-add-contained-child="${escapeAttr(node.id)}"
            data-containment-feature="${escapeAttr(entry.feature)}"
            data-contained-type="${escapeAttr(type)}" type="button">Add ${escapeAttr(
              type,
            )}</button>`,
          )
          .join("")}
      </div>
      <div class="attr-contained-list">
        ${
          children.length
            ? containmentTableMarkup(children)
            : `<div class="attr-field-hint">No contained children.</div>`
        }
      </div>`;
    host.appendChild(section);
  });
}

function containmentTableMarkup(children) {
  const columns = containmentColumns(children);
  return `<div class="attr-contained-table-wrap">
    <table class="attr-contained-table">
      <thead>
        <tr><th class="attr-contained-element-col">Element</th>${columns
          .map(
            (column) =>
              `<th class="attr-contained-field-col" data-field="${escapeAttr(
                column,
              )}">${escapeAttr(column)}</th>`,
          )
          .join("")}<th></th></tr>
      </thead>
      <tbody>
        ${children
          .map(
            (child) => `<tr>
          <td class="attr-contained-element-col" data-label="Element">
            <button class="attr-contained-link"
                    data-open-contained-child="${escapeAttr(child.id)}"
                    type="button">${escapeAttr(elementLabel(child))}</button>
            <span>${escapeAttr(child.eClass || child.type || "Element")}</span>
          </td>
          ${columns
            .map(
              (column) =>
                `<td class="attr-contained-field-col" data-field="${escapeAttr(
                  column,
                )}" data-label="${escapeAttr(column)}">${containedCellMarkup(child, column)}</td>`,
            )
            .join("")}
          <td class="attr-contained-actions-col" data-label="Actions">
            <button class="btn btn-secondary btn-sm"
                    data-delete-contained-child="${escapeAttr(child.id)}"
                    type="button">Delete</button>
          </td>
        </tr>`,
          )
          .join("")}
      </tbody>
    </table>
  </div>`;
}

function containmentColumns(children) {
  const preferred = [
    "name",
    "logicalId",
    "stageName",
    "stackName",
    "method",
    "pathTemplate",
    "fieldType",
    "literal",
    "stateKind",
    "effect",
    "targetResource",
    "propertyName",
    "key",
    "value",
    "lifecycleStatus",
  ];
  const configured = children.flatMap((child) => {
    try {
      return modelingElementDefinition(state.activeType, child.eClass)?.visibleFields || [];
    } catch {
      return [];
    }
  });
  return [...new Set([...preferred, ...configured])]
    .filter((column) => children.some((child) => child[column] !== undefined))
    .slice(0, 6);
}

function containedFieldDefinition(child, fieldName) {
  try {
    const definition = modelingElementDefinition(state.activeType, child.eClass || child.type);
    return (
      [
        ...(definition?.attributes || []),
        ...(definition?.references || []).map((reference) => ({
          ...reference,
          fieldType: "reference",
        })),
      ].find((field) => field.name === fieldName) || null
    );
  } catch {
    return null;
  }
}

function containedCellMarkup(child, fieldName) {
  const field = containedFieldDefinition(child, fieldName) || {};
  const value = child[fieldName];
  if (fieldName === "id") {
    const fullId = overviewValueText(value);
    return `<span class="attr-contained-readonly attr-contained-id-excerpt" title="${escapeAttr(
      fullId,
    )}">${escapeAttr(shortIdExcerpt(fullId))}</span>`;
  }
  if (field.readonly || READONLY_ATTR_KEYS.has(fieldName)) {
    return `<span class="attr-contained-readonly">${escapeAttr(overviewValueText(value))}</span>`;
  }
  if (field.kind === "reference" || field.fieldType === "reference") {
    return `<span class="attr-contained-readonly">${escapeAttr(overviewValueText(value))}</span>`;
  }
  if (field.fieldType === "select" && Array.isArray(field.options) && field.options.length) {
    const selectedText = String(value ?? "");
    return `<div class="attr-custom-select attr-contained-select" data-contained-select-root>
      <select class="attr-native-source"
                    data-contained-edit="${escapeAttr(child.id)}"
                    data-contained-field="${escapeAttr(fieldName)}">
        <option value=""></option>
        ${field.options
          .map(
            (option) =>
              `<option value="${escapeAttr(option)}" ${
                selectedText === String(option) ? "selected" : ""
              }>${escapeAttr(option)}</option>`,
          )
          .join("")}
      </select>
      <button class="attr-custom-select-trigger" type="button" aria-haspopup="listbox" aria-expanded="false">
        <span>${escapeAttr(selectedText || "Select...")}</span>
        <span class="attr-custom-select-caret" aria-hidden="true"></span>
      </button>
      <div class="attr-custom-select-menu hidden" role="listbox">
        <button class="attr-custom-select-option${selectedText ? "" : " is-active"}"
                data-select-value=""
                role="option"
                aria-selected="${selectedText ? "false" : "true"}"
                type="button">None</button>
        ${field.options
          .map((option) => {
            const optionText = String(option);
            const active = selectedText === optionText;
            return `<button class="attr-custom-select-option${active ? " is-active" : ""}"
                            data-select-value="${escapeAttr(optionText)}"
                            role="option"
                            aria-selected="${active ? "true" : "false"}"
                            type="button">${escapeAttr(optionText)}</button>`;
          })
          .join("")}
      </div>
    </div>`;
  }
  if (field.fieldType === "boolean" || typeof value === "boolean") {
    return `<input class="attr-contained-check"
                   data-contained-edit="${escapeAttr(child.id)}"
                   data-contained-field="${escapeAttr(fieldName)}"
                   type="checkbox" ${value ? "checked" : ""}>`;
  }
  const inputType = field.fieldType === "number" || typeof value === "number" ? "number" : "text";
  return `<input class="attr-contained-input"
                 data-contained-edit="${escapeAttr(child.id)}"
                 data-contained-field="${escapeAttr(fieldName)}"
                 type="${inputType}" value="${escapeAttr(value ?? "")}">`;
}

function shortIdExcerpt(value, edgeLength = 5) {
  const text = String(value ?? "");
  if (text.length <= edgeLength * 2 + 1) {
    return text;
  }
  return `${text.slice(0, edgeLength)}…${text.slice(-edgeLength)}`;
}

function appendConfiguredValidationSummary(section, element, type) {
  const normalized = {
    ...(element || {}),
    eClass: type || element?.eClass || element?.type,
  };
  const missing = missingRequiredFeatures(state.activeType, normalized);
  const impactedBy = [];
  state.graph?.elementsById?.forEach((candidate) => {
    if (
      refIds(candidate.affectedElements).includes(element?.id) ||
      refIds(candidate.attachedTo).includes(element?.id)
    ) {
      impactedBy.push(candidate);
    }
  });
  const summary = document.createElement("div");
  summary.className = "attr-validation-summary";
  summary.innerHTML = `
    ${
      missing.length
        ? `<div class="attr-validation-block is-error">
      <strong>Missing required</strong>
      ${missing.map((field) => `<span>${escapeAttr(field)}</span>`).join("")}
    </div>`
        : `<div class="attr-validation-block is-ok">
      <strong>Required fields complete</strong>
    </div>`
    }
    ${
      impactedBy.length
        ? `<div class="attr-validation-block">
      <strong>Linked findings</strong>
      ${impactedBy
        .map((item) => `<span>${escapeAttr(elementLabel(item) || item.id)}</span>`)
        .join("")}
    </div>`
        : `<div class="attr-field-hint">No linked findings.</div>`
    }`;
  section.appendChild(summary);
}

function _appendRelationshipValidationSummary(section, element, type) {
  appendConfiguredValidationSummary(section, element, type);
}

function _appendSemanticValidationSummary(section, element, type) {
  appendConfiguredValidationSummary(section, element, type);
}

function appendPlatformValidationSummary(section, element, type) {
  appendConfiguredValidationSummary(section, element, type);
}

function _metadataMissingRequiredFeatures(typeKey, element) {
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, element.eClass || element.type);
  } catch {
    return [];
  }
  return [...(definition?.attributes || []), ...(definition?.references || [])]
    .filter((field) => field.required && !field.readonly)
    .filter((field) => {
      const value = element[field.name];
      if (Array.isArray(value)) {
        return !value.length;
      }
      return value === null || value === undefined || String(value).trim() === "";
    })
    .map((field) => field.name);
}

function initializeContainedChildDefaults(child, parent, feature) {
  child.meta.__ownerId = parent.id;
  child.meta.__containmentFeature = feature;
  const definition = modelingElementDefinition(state.activeType, child.type);
  [...safeArray(definition?.attributes), ...safeArray(definition?.references)].forEach((field) => {
    if (!field?.name || field.readonly || child.meta[field.name] !== undefined) {
      return;
    }
    child.meta[field.name] =
      field.defaultValue == null ? field.defaultValue : structuredClone(field.defaultValue);
  });
  const containment = (modelingContainmentsForType(state.activeType, parent.type) || []).find(
    (entry) => entry.feature === feature,
  );
  if (parent?.meta && containment?.many !== false) {
    parent.meta[feature] = [...new Set([...refIds(parent.meta[feature]), child.id])];
  }
}

function addContainedChildFromDrawer(parentId, feature, childType) {
  const parentNode = state.nodesById.get(parentId);
  const parentElement = state.graph?.elementsById?.get(parentId);
  if (!parentNode || !parentElement || !feature || !childType) {
    return;
  }
  const child = getDefaultNode(state.activeType, childType, parentNode.x + 180, parentNode.y + 120);
  child.label = `${childType} ${containmentChildren(parentNode, feature).length + 1}`;
  child.meta.name = child.label;
  child.meta.label = child.label;
  initializeContainedChildDefaults(child, parentNode, feature);
  state.diagram.nodes.push(child);
  addNodeToGraphAndActiveView(child);
  addReferenceValue(parentElement, feature, child.id, true);
  parentNode.meta[feature] = parentElement[feature];
  markModelDirty();
  syncDiagramRenderer({});
  openAttributePanel(parentId);
  setStatus(`Added ${childType}`);
}

function updateContainedChildField(childId, fieldName, rawValue, inputType) {
  const child = state.graph?.elementsById?.get(childId);
  const node = state.nodesById.get(childId);
  if (!child || !fieldName) {
    return;
  }
  const definition = containedFieldDefinition(child, fieldName);
  let value = rawValue;
  if (inputType === "checkbox") {
    value = Boolean(rawValue);
  } else if (definition?.fieldType === "number") {
    value = Number(rawValue);
  }
  child[fieldName] = value;
  if (node?.meta) {
    node.meta[fieldName] = value;
    if (
      fieldName === "name" ||
      fieldName === "logicalId" ||
      fieldName === "stageName" ||
      fieldName === "stackName"
    ) {
      node.label = String(value || node.label);
      node.meta.name = node.label;
      node.meta.label = node.label;
      child.name = node.label;
      child.label = node.label;
    }
  }
  markModelDirty();
  syncDiagramRenderer({});
  setStatus(`Updated ${fieldName}`);
}

function deleteContainedChild(childId) {
  const child = state.graph?.elementsById?.get(childId);
  if (!child) {
    return;
  }
  const parentId = child.__ownerId;
  const feature = child.__containmentFeature;
  const parent = parentId ? state.graph?.elementsById?.get(parentId) : null;
  const parentNode = parentId ? state.nodesById.get(parentId) : null;
  if (parent && feature) {
    const nextIds = refIds(parent[feature]).filter((id) => id !== childId);
    parent[feature] = nextIds;
    if (parentNode?.meta) {
      parentNode.meta[feature] = nextIds;
    }
    reconcileElementRelationships(parentId);
  }
  removeElementFromGraph(childId);
  const diagramNode = state.diagram?.nodes?.find((node) => node.id === childId);
  if (diagramNode) {
    state.diagram.nodes = state.diagram.nodes.filter((node) => node.id !== childId);
    state.diagram.connections = state.diagram.connections.filter(
      (edge) => edge.sourceId !== childId && edge.targetId !== childId,
    );
  }
  markModelDirty();
  syncDiagramRenderer({});
  if (parentId && state.nodesById.has(parentId)) {
    openAttributePanel(parentId);
  }
  setStatus(`Deleted ${child.eClass || child.type || "contained child"}`);
}

function bindContainmentSectionActions() {
  el.attrPanelBody.querySelectorAll("[data-contained-select-root]").forEach((root) => {
    const select = root.querySelector("select[data-contained-edit]");
    const trigger = root.querySelector(".attr-custom-select-trigger");
    const menu = root.querySelector(".attr-custom-select-menu");
    if (!select || !trigger || !menu) {
      return;
    }
    trigger.addEventListener("click", () => {
      const open = root.classList.contains("is-open");
      closeSiblingCustomSelects(root);
      root.classList.toggle("is-open", !open);
      menu.classList.toggle("hidden", open);
      trigger.setAttribute("aria-expanded", String(!open));
    });
    menu.querySelectorAll(".attr-custom-select-option").forEach((optionButton) => {
      optionButton.addEventListener("click", () => {
        select.value = optionButton.dataset.selectValue || "";
        select.dispatchEvent(new Event("change", { bubbles: true }));
        closeCustomSelect(root);
        syncCustomSelectState(root, select, "Select...");
      });
    });
    syncCustomSelectState(root, select, "Select...");
  });
  el.attrPanelBody.querySelectorAll("[data-add-contained-child]").forEach((button) => {
    button.addEventListener("click", () => {
      addContainedChildFromDrawer(
        button.dataset.addContainedChild,
        button.dataset.containmentFeature,
        button.dataset.containedType,
      );
    });
  });
  el.attrPanelBody.querySelectorAll("[data-open-contained-child]").forEach((button) => {
    button.addEventListener("click", () => {
      openAttributePanel(button.dataset.openContainedChild);
    });
  });
  el.attrPanelBody.querySelectorAll("[data-delete-contained-child]").forEach((button) => {
    button.addEventListener("click", async () => {
      const childId = button.dataset.deleteContainedChild;
      const child = state.graph?.elementsById?.get(childId);
      const label = child ? elementLabel(child) : childId;
      const confirmed = await confirmAction({
        title: "Delete Contained Element",
        message: `Delete contained element "${label}"?`,
        confirmLabel: "Delete",
        danger: true,
      });
      if (confirmed) {
        deleteContainedChild(childId);
      }
    });
  });
  el.attrPanelBody.querySelectorAll("[data-contained-edit]").forEach((input) => {
    input.addEventListener("change", () => {
      updateContainedChildField(
        input.dataset.containedEdit,
        input.dataset.containedField,
        input.type === "checkbox" ? input.checked : input.value,
        input.type,
      );
    });
  });
}

function escapeAttr(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (char) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[char],
  );
}

function inferFieldType(value) {
  if (Array.isArray(value) || (value && typeof value === "object")) {
    return "json";
  }
  if (typeof value === "boolean") {
    return "boolean";
  }
  if (typeof value === "number") {
    return "number";
  }
  if (typeof value === "string" && value.length > 80) {
    return "textarea";
  }
  return "text";
}

const EXPRESSION_FIELDS = new Set();

function isExpressionField(key, fieldType) {
  const normalized = String(key || "");
  return (
    fieldType !== "reference" &&
    (EXPRESSION_FIELDS.has(normalized) ||
      /expression|pattern|condition|mapping|query|filter|projection|json|document|definition|policy/i.test(
        normalized,
      ))
  );
}

function selectedOptionLabels(select) {
  return [...select.selectedOptions].map((option) => option.textContent || option.value);
}

function customSelectTriggerText(select, emptyLabel) {
  const labels = selectedOptionLabels(select).filter(Boolean);
  if (!labels.length) {
    return emptyLabel;
  }
  if (select.multiple) {
    return labels.length === 1 ? labels[0] : `${labels.length} selected`;
  }
  return labels[0];
}

function syncCustomSelectState(root, select, emptyLabel) {
  const triggerLabel = root.querySelector(".attr-custom-select-trigger span");
  if (triggerLabel) {
    triggerLabel.textContent = customSelectTriggerText(select, emptyLabel);
  }
  const selectedValues = new Set([...select.selectedOptions].map((option) => option.value));
  root.querySelectorAll(".attr-custom-select-option").forEach((optionButton) => {
    const active = selectedValues.has(optionButton.dataset.selectValue || "");
    optionButton.classList.toggle("is-active", active);
    optionButton.setAttribute("aria-selected", String(active));
  });
}

function resetCustomSelectSearch(root) {
  const searchInput = root.querySelector(".attr-custom-select-search-input");
  if (!searchInput) {
    return;
  }
  searchInput.value = "";
  filterCustomSelectMenu(root, "");
}

function filterCustomSelectMenu(root, query) {
  const normalized = String(query || "")
    .trim()
    .toLowerCase();
  let visibleCount = 0;
  root.querySelectorAll(".attr-custom-select-option").forEach((optionButton) => {
    const text = (optionButton.textContent || "").toLowerCase();
    const matches = !normalized || text.includes(normalized);
    optionButton.classList.toggle("hidden", !matches);
    optionButton.hidden = !matches;
    if (matches) {
      visibleCount += 1;
    }
  });
  const empty = root.querySelector(".attr-custom-select-empty");
  if (empty) {
    empty.classList.toggle("hidden", visibleCount > 0 || !normalized);
  }
}

function closeCustomSelect(root) {
  root.classList.remove("is-open");
  root.querySelector(".attr-custom-select-menu")?.classList.add("hidden");
  root.querySelector(".attr-custom-select-trigger")?.setAttribute("aria-expanded", "false");
  resetCustomSelectSearch(root);
}

function closeSiblingCustomSelects(root) {
  el.attrPanelBody?.querySelectorAll(".attr-custom-select.is-open").forEach((openRoot) => {
    if (openRoot !== root) {
      closeCustomSelect(openRoot);
    }
  });
}

document.addEventListener("click", (event) => {
  if (event.target?.closest?.(".attr-custom-select")) {
    return;
  }
  el.attrPanelBody
    ?.querySelectorAll(".attr-custom-select.is-open")
    .forEach((openRoot) => closeCustomSelect(openRoot));
});

function appendCustomSelectControl(
  wrapper,
  select,
  { emptyLabel = "Select...", searchable = false } = {},
) {
  select.classList.add("attr-native-source");
  const root = document.createElement("div");
  root.className = `attr-custom-select${select.multiple ? " is-multiple" : ""}`;

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "attr-custom-select-trigger";
  trigger.setAttribute("aria-haspopup", "listbox");
  trigger.setAttribute("aria-expanded", "false");
  trigger.innerHTML = `
    <span></span>
    <span class="attr-custom-select-caret" aria-hidden="true"></span>`;

  const menu = document.createElement("div");
  menu.className = `attr-custom-select-menu hidden${searchable ? " has-search" : ""}`;
  menu.setAttribute("role", "listbox");
  if (select.multiple) {
    menu.setAttribute("aria-multiselectable", "true");
  }

  let optionsContainer = menu;
  if (searchable) {
    const searchWrap = document.createElement("div");
    searchWrap.className = "attr-custom-select-search";
    const searchInput = document.createElement("input");
    searchInput.type = "search";
    searchInput.className = "attr-custom-select-search-input";
    searchInput.placeholder = "Search elements...";
    searchInput.autocomplete = "off";
    searchInput.setAttribute("aria-label", "Search elements");
    searchInput.addEventListener("input", () => filterCustomSelectMenu(root, searchInput.value));
    searchInput.addEventListener("click", (event) => event.stopPropagation());
    searchInput.addEventListener("keydown", (event) => event.stopPropagation());
    searchWrap.appendChild(searchInput);
    menu.appendChild(searchWrap);

    optionsContainer = document.createElement("div");
    optionsContainer.className = "attr-custom-select-options";
    optionsContainer.setAttribute("role", "presentation");
    menu.appendChild(optionsContainer);

    const emptyMessage = document.createElement("div");
    emptyMessage.className = "attr-custom-select-empty hidden";
    emptyMessage.textContent = "No matching elements";
    optionsContainer.appendChild(emptyMessage);
  }

  [...select.options].forEach((sourceOption) => {
    const optionButton = document.createElement("button");
    optionButton.type = "button";
    optionButton.className = "attr-custom-select-option";
    optionButton.dataset.selectValue = sourceOption.value;
    optionButton.setAttribute("role", "option");
    optionButton.textContent = sourceOption.textContent || sourceOption.value || "None";
    optionButton.addEventListener("click", () => {
      if (select.multiple) {
        sourceOption.selected = !sourceOption.selected;
      } else {
        select.value = sourceOption.value;
        closeCustomSelect(root);
      }
      select.dispatchEvent(new Event("change", { bubbles: true }));
      syncCustomSelectState(root, select, emptyLabel);
    });
    optionsContainer.appendChild(optionButton);
  });

  trigger.addEventListener("click", () => {
    const open = root.classList.contains("is-open");
    closeSiblingCustomSelects(root);
    const nextOpen = !open;
    root.classList.toggle("is-open", nextOpen);
    menu.classList.toggle("hidden", !nextOpen);
    trigger.setAttribute("aria-expanded", String(nextOpen));
    if (nextOpen) {
      resetCustomSelectSearch(root);
      const searchInput = root.querySelector(".attr-custom-select-search-input");
      if (searchInput) {
        requestAnimationFrame(() => searchInput.focus());
      }
    }
  });
  root.appendChild(trigger);
  root.appendChild(menu);
  wrapper.appendChild(root);
  syncCustomSelectState(root, select, emptyLabel);
}

function buildAttrField(key, value, field) {
  let fieldType = field?.fieldType || inferFieldType(value);
  const expressionField = isExpressionField(key, fieldType);
  if (expressionField && fieldType === "text") {
    fieldType = "textarea";
  }
  const readonly = Boolean(field?.readonly || READONLY_ATTR_KEYS.has(key));
  const wrapper = document.createElement("div");

  if (readonly) {
    wrapper.className = "attr-field";
    const lbl = document.createElement("label");
    lbl.textContent = key;
    const val = document.createElement("div");
    val.className = "attr-field-readonly";
    val.textContent = String(value);
    wrapper.appendChild(lbl);
    wrapper.appendChild(val);
    return wrapper;
  }

  if (fieldType === "boolean") {
    wrapper.className = "attr-field attr-field-checkbox";
    const input = document.createElement("input");
    const fieldId = nextAttrFieldDomId(key);
    input.type = "checkbox";
    input.id = fieldId;
    input.checked = Boolean(value);
    input.dataset.attrKey = key;
    input.dataset.attrType = "boolean";
    const lbl = document.createElement("label");
    lbl.htmlFor = fieldId;
    lbl.textContent = key;
    wrapper.appendChild(input);
    wrapper.appendChild(lbl);
    return wrapper;
  }

  wrapper.className = "attr-field";
  const fieldId = nextAttrFieldDomId(key);
  const lbl = document.createElement("label");
  lbl.htmlFor = fieldId;
  lbl.textContent = field?.required ? `${key} *` : key;
  wrapper.appendChild(lbl);

  let input;
  if (fieldType === "select" && Array.isArray(field?.options) && field.options.length) {
    input = document.createElement("select");
    const empty = document.createElement("option");
    empty.value = "";
    empty.textContent = "";
    input.appendChild(empty);
    field.options.forEach((optionValue) => {
      const option = document.createElement("option");
      option.value = String(optionValue);
      option.textContent = String(optionValue);
      option.selected = String(value ?? "") === String(optionValue);
      input.appendChild(option);
    });
  } else if (fieldType === "date") {
    input = document.createElement("input");
    input.type = "date";
    input.value = String(value ?? "").slice(0, 10);
  } else if (fieldType === "textarea") {
    input = document.createElement("textarea");
    input.textContent = String(value ?? "");
    if (expressionField) {
      input.classList.add("attr-expression-editor");
      input.spellcheck = false;
      input.rows = Math.max(3, String(value ?? "").split("\n").length);
    }
  } else if (fieldType === "reference") {
    input = buildReferenceInput(key, value, field);
  } else if (fieldType === "json") {
    input = document.createElement("textarea");
    input.textContent = JSON.stringify(value ?? null, null, 2);
  } else if (fieldType === "number") {
    input = document.createElement("input");
    input.type = "number";
    input.value = String(value ?? 0);
  } else {
    input = document.createElement("input");
    input.type = "text";
    input.value = String(value ?? "");
  }
  input.id = fieldId;
  input.dataset.attrKey = key;
  input.dataset.attrType = fieldType;
  if (field?.many) {
    input.dataset.attrMany = "true";
  }
  if (field?.required) {
    input.required = true;
  }
  wrapper.appendChild(input);
  if (input.tagName === "SELECT") {
    appendCustomSelectControl(wrapper, input, {
      emptyLabel: fieldType === "reference" ? "No reference" : "Select...",
      searchable: fieldType === "reference",
    });
  }
  if (field?.kind === "reference" && field?.targetType) {
    const hint = document.createElement("div");
    hint.className = "attr-field-hint";
    hint.textContent = field.many
      ? `References ${field.targetType}[]`
      : `References ${field.targetType}`;
    wrapper.appendChild(hint);
  }
  if (expressionField) {
    const hint = document.createElement("div");
    hint.className = "attr-field-hint";
    hint.textContent =
      "Expression text is preserved exactly; use the related language field when one exists.";
    wrapper.appendChild(hint);
  }
  return wrapper;
}

function referenceValueId(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value && typeof value === "object") {
    return value.$ref || value.id || value.elementId || "";
  }
  return "";
}

function referenceValueIds(value) {
  return Array.isArray(value)
    ? value.map(referenceValueId).filter(Boolean)
    : [referenceValueId(value)].filter(Boolean);
}

function elementMatchesReferenceTarget(element, targetType) {
  const expected = String(targetType || "").trim();
  if (!expected || expected === "*") {
    return true;
  }
  return modelTypeMatches(state.activeType, element, expected);
}

function referenceOptions(targetType) {
  const options = [];
  state.graph?.elementsById?.forEach((element) => {
    if (elementMatchesReferenceTarget(element, targetType)) {
      options.push({
        id: element.id,
        label: element.name || element.label || element.id,
        type: element.eClass || element.type || "Element",
      });
    }
  });
  return options.sort((a, b) => a.type.localeCompare(b.type) || a.label.localeCompare(b.label));
}

function buildReferenceInput(key, value, field) {
  const many = Boolean(field?.many);
  const input = document.createElement(many ? "select" : "select");
  input.classList.add("attr-reference-select");
  const selectedIds = new Set(referenceValueIds(value));
  input.multiple = many;
  if (many) {
    input.size = Math.min(8, Math.max(3, referenceOptions(field?.targetType).length || 3));
  } else {
    const empty = document.createElement("option");
    empty.value = "";
    empty.textContent = "";
    input.appendChild(empty);
  }
  referenceOptions(field?.targetType).forEach((entry) => {
    const option = document.createElement("option");
    option.value = entry.id;
    option.textContent = `${entry.label} (${entry.type})`;
    option.selected = selectedIds.has(entry.id);
    input.appendChild(option);
  });
  input.dataset.attrReferenceTarget = field?.targetType || "*";
  input.dataset.attrReferenceName = key;
  return input;
}

// ── Apply / delete ────────────────────────────────────────────────────────────

export function applyAttributePanel() {
  if (state.selectedRootModel) {
    applyRootModelPanel();
    return;
  }
  if (state.selectedConnectionId) {
    applyConnectionPanel();
    return;
  }
  const node = state.nodesById.get(state.selectedNodeId);
  if (!node) {
    return;
  }

  const labelKey = identityFieldForNode(
    node,
    modelingElementDefinition(state.activeType, node.type),
  );
  const undoSnapshot = captureDiagramUndoSnapshot();

  try {
    el.attrPanelBody.querySelectorAll("[data-attr-key]").forEach((input) => {
      const key = input.dataset.attrKey;
      const attrType = input.dataset.attrType;
      let value;

      if (attrType === "boolean") {
        value = input.checked;
      } else if (attrType === "number") {
        value = Number(input.value);
      } else if (attrType === "reference") {
        if (input.dataset.attrMany === "true") {
          value = [...input.selectedOptions].map((option) => option.value).filter(Boolean);
        } else {
          value = input.value || null;
        }
      } else if (attrType === "json") {
        value = JSON.parse(input.value || "null");
      } else if (input.tagName === "TEXTAREA") {
        value = input.value;
      } else {
        value = input.value;
      }

      if (key === labelKey || key === "label" || key === "name") {
        node.label = String(value) || node.label;
        node.meta[labelKey] = node.label;
        if (node.meta.label !== undefined) {
          node.meta.label = node.label;
        }
        if (node.meta.name !== undefined) {
          node.meta.name = node.label;
        }
      } else {
        node.meta[key] = value;
      }
    });
    syncActiveViewFromVisibleGraph({ rebuildIndexes: false });
    synchronizeOppositeReferences(node);
    markGraphRelationshipsDirty();
  } catch {
    setStatus("One property contains invalid JSON. Fix it before applying changes.");
    return;
  }

  if (undoSnapshot?.signature !== JSON.stringify(state.diagram || {})) {
    pushDiagramUndoSnapshot(undoSnapshot);
  }

  syncDiagramRenderer({});

  el.attrPanelTitle.textContent = node.label;
  markModelDirty();
  setStatus(`Attributes updated for ${node.id}`);
}

function applyRootModelPanel() {
  const root = ensureRootModel();
  const undoSnapshot = captureDiagramUndoSnapshot();
  try {
    el.attrPanelBody.querySelectorAll("[data-attr-key]").forEach((input) => {
      const key = input.dataset.attrKey;
      if (READONLY_ATTR_KEYS.has(key)) {
        return;
      }
      root[key] = readInputValue(input);
    });
  } catch {
    setStatus("One model property contains invalid JSON. Fix it before applying changes.");
    return;
  }
  if (!String(root.name || "").trim()) {
    root.name = activeModelName();
  }
  if (state.tabs[state.activeType]) {
    state.tabs[state.activeType].baseModel = root;
  }
  if (undoSnapshot?.signature !== JSON.stringify(state.diagram || {})) {
    pushDiagramUndoSnapshot(undoSnapshot);
  }
  el.attrPanelTitle.textContent = root.name || activeModelName();
  markModelDirty();
  setStatus("Root model attributes updated");
}

function readInputValue(input) {
  const attrType = input.dataset.attrType;
  if (attrType === "boolean") {
    return input.checked;
  }
  if (attrType === "number") {
    return Number(input.value);
  }
  if (attrType === "reference") {
    if (input.dataset.attrMany === "true") {
      return [...input.selectedOptions].map((option) => option.value).filter(Boolean);
    }
    return input.value || null;
  }
  if (attrType === "json") {
    return JSON.parse(input.value || "null");
  }
  if (input.tagName === "TEXTAREA") {
    return input.value;
  }
  return input.value;
}

function applyConnectionPanel() {
  const edge = state.connectionsById.get(state.selectedConnectionId);
  if (!edge) {
    return;
  }
  const relationship = state.graph?.relationshipsById?.get(edge.id);
  if (!relationship) {
    return;
  }
  try {
    el.attrPanelBody.querySelectorAll("[data-attr-key]").forEach((input) => {
      const key = input.dataset.attrKey;
      if (["id", "kind", "source", "target"].includes(key)) {
        return;
      }
      relationship[key] = readInputValue(input);
    });
  } catch {
    setStatus("One connection property contains invalid JSON. Fix it before applying changes.");
    return;
  }
  syncDiagramRenderer({});
  markModelDirty();
  setStatus(`Connection updated: ${edge.kind}`);
}

function asReferenceIds(value) {
  return Array.isArray(value)
    ? value.map(referenceValueId).filter(Boolean)
    : [referenceValueId(value)].filter(Boolean);
}

function setElementReference(element, key, sourceId, many) {
  if (!key || !element) {
    return;
  }
  if (many) {
    const ids = new Set(asReferenceIds(element[key]));
    ids.add(sourceId);
    element[key] = [...ids];
  } else {
    element[key] = sourceId;
  }
}

function removeElementReference(element, key, sourceId, many) {
  if (!key || !element) {
    return;
  }
  if (many) {
    element[key] = asReferenceIds(element[key]).filter((id) => id !== sourceId);
  } else if (referenceValueId(element[key]) === sourceId) {
    element[key] = null;
  }
}

function referenceDefinition(type, name) {
  try {
    return (
      (modelingElementDefinition(state.activeType, type)?.references || []).find(
        (reference) => reference.name === name,
      ) || null
    );
  } catch {
    return null;
  }
}

function synchronizeOppositeReferences(node) {
  if (!node?.id || !node?.meta) {
    return;
  }
  let definition = null;
  try {
    definition = modelingElementDefinition(state.activeType, node.type);
  } catch {
    return;
  }
  for (const reference of definition?.references || []) {
    const opposite = String(reference?.opposite || "").replace(/^#/, "");
    if (!opposite || reference.readonly) {
      continue;
    }
    const selectedIds = new Set(asReferenceIds(node.meta[reference.name]));
    state.graph?.elementsById?.forEach((targetElement, targetId) => {
      if (!elementMatchesReferenceTarget(targetElement, reference.targetType)) {
        return;
      }
      const targetDefinition = referenceDefinition(
        targetElement.eClass || targetElement.type,
        opposite,
      );
      const oppositeMany = targetDefinition?.many !== false;
      if (selectedIds.has(targetId)) {
        setElementReference(targetElement, opposite, node.id, oppositeMany);
      } else {
        removeElementReference(targetElement, opposite, node.id, oppositeMany);
      }
      const visibleTarget = state.nodesById?.get(targetId);
      if (visibleTarget?.meta) {
        visibleTarget.meta[opposite] = targetElement[opposite];
      }
    });
  }
}

export async function deleteSelection() {
  if (state.selectedConnectionId) {
    await deleteSelectedConnection();
    return;
  }
  const nodeId = state.selectedNodeId;
  if (!nodeId) {
    return;
  }
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }

  const confirmed = await confirmAction({
    title: "Delete Element",
    message: `Delete "${node.label}" (${node.type})?`,
    confirmLabel: "Delete",
    danger: true,
  });
  if (!confirmed) {
    return;
  }

  pushDiagramUndoSnapshot();
  state.diagram.nodes = state.diagram.nodes.filter((n) => n.id !== nodeId);
  state.diagram.connections = state.diagram.connections.filter(
    (c) => c.sourceId !== nodeId && c.targetId !== nodeId,
  );
  removeElementFromGraph(nodeId);

  closeAttributePanel();
  syncDiagramRenderer({});
  markModelDirty();
  setStatus(`Deleted ${node.type}: ${nodeId}`);
}

export const deleteSelectedNode = deleteSelection;

export async function deleteSelectedConnection() {
  const connectionId = state.selectedConnectionId;
  if (!connectionId) {
    return;
  }
  const connection = state.connectionsById.get(connectionId);
  if (!connection) {
    return;
  }
  const confirmed = await confirmAction({
    title: "Delete Connection",
    message: `Delete connection "${connection.kind}"?`,
    confirmLabel: "Delete",
    danger: true,
  });
  if (!confirmed) {
    return;
  }

  const undoSnapshot = captureDiagramUndoSnapshot();
  const persistedConnectionIds = new Set(
    relationshipIdsFromModel(state.activeType, state.baseModel || {}),
  );
  const shouldDeletePersistedRelationship = Boolean(
    state.modelId && persistedConnectionIds.has(connectionId),
  );

  if (shouldDeletePersistedRelationship) {
    const updated = await api(
      `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}/relationships/${encodeURIComponent(
        connectionId,
      )}`,
      {
        method: "DELETE",
      },
    );
    state.baseModel = structuredClone(updated.modelJson);
    state.diagram = toDiagram(state.activeType, updated.modelJson, updated.name);
  } else {
    state.diagram.connections = state.diagram.connections.filter(
      (edge) => edge.id !== connectionId,
    );
  }
  removeRelationshipFromGraph(connectionId);
  pushDiagramUndoSnapshot(undoSnapshot);

  closeAttributePanel();
  syncDiagramRenderer({});
  markModelDirty();
  setStatus(`Deleted connection: ${connection.kind}`);
}

export function bindConnectionDrawStateListener() {
  window.addEventListener("connection-draw-state-change", syncLegalRelationshipDrawSection);
}
