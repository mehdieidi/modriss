import { MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { api } from "./api.js";
import { escapeHtml, genId } from "./utils.js";
import { ensureReadableLayout } from "./layout-engine.js";
import { getDefaultNode, legalKinds, saveStoredEdgeLayout } from "./diagram.js";
import {
  activeView,
  addConnectionToGraphAndActiveView,
  addNodeToGraphAndActiveView,
  persistNodePositionInActiveView,
  prepareViewNodeIndex,
  removeElementFromGraph,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { isContainerElement, materializeActiveView } from "./view-materializer.js";
import {
  modelingElementDefinition,
  modelingPalette,
  modelingRelationshipKindLabel,
  modelingRelationshipPresentation,
  modelingShortcutConnectorRules,
  modelingTypeMatches,
  modelingViewDefinition,
} from "./modeling-config-data.js";
import { setStatus } from "./status.js";
// NOTE: These imports form intentional circular references (ES module live bindings).
// All functions are only called at runtime (event handlers / async), never at module init.
import { markModelDirty } from "./model-save-ui.js";
import {
  closeAttributePanel,
  openAttributePanel,
  openBoundedContextPanel,
  openConnectionPanel,
} from "./attr-panel.js";
import { fetchImpact } from "./impact.js";
import {
  captureDiagramUndoSnapshot,
  captureNodePositionUndoSnapshot,
  pushDiagramUndoSnapshot,
} from "./undo.js";
import { renderCimWorkbenchSurface } from "./cim-workbench.js";
import { renderPimWorkbenchSurface } from "./pim-workbench.js";
import { renderPsmWorkbenchSurface } from "./psm-workbench.js";
import {
  addG6Edge,
  addG6Node,
  beginG6InlineLabelEdit,
  fitG6CanvasToDiagram,
  focusG6CanvasPoint,
  focusG6Node,
  getG6Editor,
  isG6Available,
  mountG6Editor,
  onG6ViewportChanged,
  refreshG6Edges,
  renderG6Diagram,
  resetG6CanvasView,
  setG6HoverEdge,
  setG6HoverNode,
  syncG6FromState,
  toGraphCoordinates,
  updateG6ConnectionState,
  updateG6ContextBoxes,
  updateG6Edge,
  updateG6ImpactState,
  updateG6Node,
  updateG6NodeIcons,
  updateG6Selection,
  updateG6Viewport,
  zoomG6CanvasBy,
} from "./graph-editor/g6-editor.js";

const DEFAULT_NODE_W = 228;
const DEFAULT_NODE_H = 112;
const CIM_NODE_W = 176;
const CIM_NODE_H = 96;
const DEFAULT_BOUNDED_CONTEXT_NAME = "Core";
const PLACEHOLDER_ICON = "/assets/icons/placeholder.svg";
const edgeIdsByNodeId = new Map(); // nodeId -> Set(edgeId)
const connectionsById = state.connectionsById;
let hoveredEdgeId = null;
let inlineLabelEditStartLabel = "";
let inlineLabelEditUndoSnapshot = null;

function workbenchSurfaces() {
  const canvasStage = el.canvasGrid?.closest(".canvas-stage");
  const surfaceClasses = [
    "cim-surface-active",
    "cim-surface-dock",
    "pim-surface-active",
    "pim-surface-dock",
    "psm-surface-active",
    "psm-surface-dock",
  ];
  el.canvasGrid?.classList.remove(...surfaceClasses);
  canvasStage?.classList.remove(...surfaceClasses);
  el.workspace?.classList.remove("palette-hidden");
  el.paletteRailToggleBtn?.classList.add("active");
  state.cimWorkbench.hidPalette = false;
  state.pimWorkbench.hidPalette = false;
  state.psmWorkbench.hidPalette = false;

  if (state.activeType === "cim") {
    renderCimWorkbenchSurface();
  } else if (state.activeType === "pim") {
    renderPimWorkbenchSurface();
  } else if (state.activeType === "psm") {
    renderPsmWorkbenchSurface();
  }
}

function syncCanvasIndexesFromState() {
  state.nodesById.clear();
  edgeIdsByNodeId.clear();
  connectionsById.clear();
  state.diagram.nodes.forEach((node) => {
    if (nodeVisibleInCurrentCanvasMode(node)) {
      state.nodesById.set(node.id, node);
    }
  });
  state.diagram.connections.forEach((edge) => {
    if (!state.nodesById.has(edge.sourceId) || !state.nodesById.has(edge.targetId)) {
      return;
    }
    connectionsById.set(edge.id, edge);
    [edge.sourceId, edge.targetId].forEach((nodeId) => {
      if (!edgeIdsByNodeId.has(nodeId)) {
        edgeIdsByNodeId.set(nodeId, new Set());
      }
      edgeIdsByNodeId.get(nodeId).add(edge.id);
    });
  });
}

const CIM_PROVIDER_TERMS = [
  "aws",
  "amazon",
  "lambda",
  "dynamodb",
  "dynamo",
  "s3",
  "sns",
  "sqs",
  "eventbridge",
  "cognito",
  "apigateway",
  "api gateway",
  "azure",
  "gcp",
  "google cloud",
  "pubsub",
  "cloud run",
  "cloud functions",
  "cosmos",
  "firebase",
  "kinesis",
  "rds",
  "cloudwatch",
];

const CIM_TABLE_LIKE_TYPES = new Set([
  "AcceptanceCriterion",
  "DecisionRule",
  "QualityScenario",
  "DataClassification",
]);

const CIM_PROFILE_LABELS = {
  dashboard: "Model dashboard",
  requirements: "Requirements and goals",
  capability: "Capability and context",
  actor: "Actor interactions",
  "bounded-context": "Bounded context",
  domain: "Domain model",
  aggregate: "Aggregate consistency",
  data: "Data dictionary",
  eventstorming: "EventStorming behavior",
  process: "Business process",
  decision: "Decision",
  governance: "Information and governance",
  readiness: "Transformation readiness",
  traceability: "Traceability",
};

function refLabel(value) {
  if (!value) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "object") {
    return value.name || value.label || value.$ref || value.id || "";
  }
  return "";
}

function cimEdgeLabel(edge) {
  if (state.activeType !== "cim") {
    if (edge.bundle) {
      return edge.label || "Bundled relations";
    }
    const key = String(edge.kind || "").toUpperCase();
    try {
      return (
        modelingRelationshipKindLabel(state.activeType, key) ||
        key.toLowerCase().replaceAll("_", " ")
      );
    } catch {
      return key.toLowerCase().replaceAll("_", " ");
    }
  }
  if (edge.label) {
    return edge.label;
  }
  const key = String(edge.kind || "").toUpperCase();
  let configuredLabel = "";
  try {
    configuredLabel = modelingRelationshipKindLabel("cim", key);
  } catch {
    configuredLabel = "";
  }
  return edge.bundle
    ? edge.label || "Bundled relations"
    : configuredLabel || key.toLowerCase().replaceAll("_", " ");
}

function cimEdgePresentation(edge) {
  if (!["cim", "pim", "psm"].includes(state.activeType)) {
    return { className: "", markerStart: "", markerEnd: "arrow" };
  }
  return modelingRelationshipPresentation(state.activeType, edge);
}

function cimNodeNotation(node) {
  if (!["cim", "pim", "psm"].includes(state.activeType)) {
    return null;
  }
  let definition = null;
  try {
    definition = modelingElementDefinition(state.activeType, node.type);
  } catch {
    definition = null;
  }
  if (definition?.notation) {
    const lineFields = Array.isArray(definition.notation.lineFields)
      ? definition.notation.lineFields
      : [];
    return {
      tag: definition.notation.tag || "element",
      line: (meta) => {
        for (const field of lineFields) {
          const value = meta?.[field];
          const text = Array.isArray(value) ? compactList(value) : compactRefCount(value);
          if (String(text || "").trim()) {
            return text;
          }
        }
        return "";
      },
    };
  }
  return null;
}

function normalizeViewText(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, " ");
}

function activeCimViewProfile() {
  if (state.activeType !== "cim") {
    return null;
  }
  const view = activeView();
  let definition = null;
  try {
    definition = modelingViewDefinition("cim", view);
  } catch {
    definition = null;
  }
  if (definition?.viewpoint) {
    return String(definition.viewpoint);
  }
  const text = normalizeViewText(
    [view?.id, view?.name, view?.layoutProfile, view?.description].filter(Boolean).join(" "),
  );
  if (!text) {
    return "eventstorming";
  }
  if (text.includes("readiness")) {
    return "readiness";
  }
  if (text.includes("dashboard") || text.includes("model map")) {
    return "dashboard";
  }
  if (text.includes("requirement") || text.includes("objective") || text.includes("goal")) {
    return "requirements";
  }
  if (text.includes("bounded") || text.includes("ubiquitous") || text.includes("language")) {
    return "bounded-context";
  }
  if (text.includes("capability") || text.includes("context")) {
    return "capability";
  }
  if (text.includes("actor") || text.includes("role") || text.includes("external system")) {
    return "actor";
  }
  if (text.includes("entity") || text.includes("aggregate") || text.includes("domain")) {
    return text.includes("aggregate") ? "aggregate" : "domain";
  }
  if (text.includes("data") || text.includes("dictionary") || text.includes("classification")) {
    return "data";
  }
  if (text.includes("process") || text.includes("timeline")) {
    return "process";
  }
  if (text.includes("decision") || text.includes("policy") || text.includes("rule")) {
    return "decision";
  }
  if (
    text.includes("governance") ||
    text.includes("security") ||
    text.includes("privacy") ||
    text.includes("nfr") ||
    text.includes("information")
  ) {
    return "governance";
  }
  if (
    text.includes("actor") ||
    text.includes("command") ||
    text.includes("query") ||
    text.includes("event") ||
    text.includes("storm")
  ) {
    return "eventstorming";
  }
  return "eventstorming";
}

function activePimViewProfile() {
  if (state.activeType !== "pim") {
    return null;
  }
  const view = activeView();
  let definition = null;
  try {
    definition = modelingViewDefinition("pim", view);
  } catch {
    definition = null;
  }
  if (definition?.viewpoint) {
    return String(definition.viewpoint);
  }
  const text = normalizeViewText(
    [view?.id, view?.name, view?.layoutProfile, view?.description].filter(Boolean).join(" "),
  );
  if (text.includes("api")) {
    return "api";
  }
  if (text.includes("compute") || text.includes("trigger")) {
    return "compute";
  }
  if (text.includes("contract") || text.includes("schema") || text.includes("event type")) {
    return "contracts";
  }
  if (text.includes("data")) {
    return "data";
  }
  if (text.includes("integration") || text.includes("channel") || text.includes("event")) {
    return "integration";
  }
  if (text.includes("workflow")) {
    return "workflow";
  }
  if (text.includes("security") || text.includes("access")) {
    return "security";
  }
  if (text.includes("deployment") || text.includes("environment")) {
    return "deployment";
  }
  if (text.includes("policy") || text.includes("operation")) {
    return "policy";
  }
  if (text.includes("configuration") || text.includes("secret")) {
    return "configuration";
  }
  if (text.includes("readiness") || text.includes("trace")) {
    return "readiness";
  }
  return "architecture";
}

function hasOwnValue(object, key) {
  return (
    object &&
    Object.prototype.hasOwnProperty.call(object, key) &&
    object[key] !== null &&
    object[key] !== undefined &&
    String(object[key]).trim() !== ""
  );
}

function firstValue(meta, keys) {
  for (const key of keys) {
    if (hasOwnValue(meta, key)) {
      return meta[key];
    }
  }
  return "";
}

function compactRefCount(value) {
  if (Array.isArray(value)) {
    return value.length ? `${value.length}` : "";
  }
  if (value && typeof value === "object") {
    return refLabel(value);
  }
  return value ? String(value) : "";
}

function compactList(value, limit = 3) {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return values.slice(0, limit).map(refLabel).filter(Boolean).join(", ");
}

function refsArray(value) {
  if (Array.isArray(value)) {
    return value;
  }
  return value ? [value] : [];
}

function isPastTenseBusinessEventName(value) {
  const text = String(value || "").trim();
  if (!text) {
    return false;
  }
  const words = text.split(/\s+/).filter(Boolean);
  const first = words[0]?.toLowerCase() || "";
  const last = words[words.length - 1]?.toLowerCase() || "";
  return (
    first.endsWith("ed") ||
    last.endsWith("ed") ||
    /(?:submitted|created|updated|deleted|confirmed|rejected|approved|cancelled|canceled|completed|failed|paid|sent|received|placed|registered|enrolled|verified|accepted|declined)$/.test(
      first,
    )
  );
}

function hasProviderTermInText(value) {
  const text = String(value || "").toLowerCase();
  return CIM_PROVIDER_TERMS.some((term) => text.includes(term));
}

function nodeProviderTermHit(node) {
  const meta = node?.meta || {};
  const values = [
    node?.label,
    node?.type,
    ...Object.values(meta).flatMap((value) => (Array.isArray(value) ? value : [value])),
  ];
  return values.some((value) => typeof value === "string" && hasProviderTermInText(value));
}

function cimNodeIssueBadges(node) {
  const badges = [];
  if (nodeProviderTermHit(node)) {
    badges.push("provider-independent");
  }
  if (node.type === "BusinessEvent") {
    const eventName = node.meta?.occurredInPastTenseName || node.label;
    if (!isPastTenseBusinessEventName(eventName)) {
      badges.push("past tense");
    }
  }
  if (
    node.type === "Hotspot" &&
    (node.meta?.productionBlocking || node.meta?.blocksTransformation)
  ) {
    badges.push("blocking");
  }
  return badges;
}

function detailRow(label, value) {
  const normalized = Array.isArray(value) ? compactList(value) : value;
  const text = String(normalized || "").trim();
  if (!text) {
    return "";
  }
  return `<div class="node-cim-row"><span>${escapeHtml(label)}</span><strong>${escapeHtml(
    text,
  )}</strong></div>`;
}

function detailBadge(label, issue = false) {
  const text = String(label || "").trim();
  if (!text) {
    return "";
  }
  return `<span class="node-cim-badge${issue ? " node-cim-issue" : ""}">${escapeHtml(text)}</span>`;
}

function detailCompartment(title, rows) {
  const content = rows.filter(Boolean).join("");
  if (!content) {
    return "";
  }
  return `<div class="node-cim-compartment"><div class="node-cim-compartment-title">${escapeHtml(
    title,
  )}</div>${content}</div>`;
}

function cimNodeDetailsHtml(node) {
  if (state.activeType === "pim") {
    return pimNodeDetailsHtml(node);
  }
  if (state.activeType === "psm") {
    return metadataNodeDetailsHtml("psm", node);
  }
  if (state.activeType !== "cim") {
    return "";
  }
  const meta = node.meta || {};
  const badges = cimNodeIssueBadges(node).map((label) => detailBadge(label, true));
  const addBadge = (value) => {
    const text = String(value || "").trim();
    if (text) {
      badges.push(detailBadge(text));
    }
  };
  const sections = [];
  switch (node.type) {
    case "Requirement":
      addBadge(firstValue(meta, ["requirementType", "priority"]));
      if (meta.mandatory) {
        addBadge("mandatory");
      }
      if (meta.productionBlocking) {
        badges.push(detailBadge("production blocking", true));
      }
      sections.push(
        detailCompartment("Fit", [
          detailRow("criterion", meta.fitCriterion),
          detailRow("acceptance", compactRefCount(meta.acceptanceCriteria)),
        ]),
      );
      break;
    case "BusinessGoal":
      addBadge(meta.priority);
      sections.push(
        detailCompartment("Outcome", [
          detailRow("success", meta.successCriterion),
          detailRow("value", meta.businessValue),
          detailRow("measured by", compactList(meta.measuredBy)),
        ]),
      );
      break;
    case "KPI":
      addBadge(firstValue(meta, ["metricType", "unit"]));
      sections.push(
        detailCompartment("Metric", [
          detailRow("name", meta.metricName),
          detailRow(
            "target",
            [meta.operator, meta.targetValue, meta.unit].filter(Boolean).join(" "),
          ),
          detailRow("measures", compactList(meta.measures)),
        ]),
      );
      break;
    case "Actor":
    case "ExternalSystem":
    case "Stakeholder":
      addBadge(firstValue(meta, ["actorType", "trustLevel", "stakeholderType"]));
      sections.push(
        detailCompartment("Participant", [
          detailRow("roles", compactList(meta.playsRoles)),
          detailRow("commands", compactRefCount(meta.issuesCommands)),
          detailRow("queries", compactRefCount(meta.issuesQueries)),
          detailRow("events", compactRefCount(meta.producedEvents || meta.observesEvents)),
        ]),
      );
      break;
    case "BusinessCapability":
      addBadge(firstValue(meta, ["criticality", "maturity"]));
      sections.push(
        detailCompartment("Scope", [
          detailRow("supports", compactList(meta.supports)),
          detailRow("realizes", compactRefCount(meta.realizesRequirements)),
          detailRow("commands", compactRefCount(meta.containsCommands)),
          detailRow("queries", compactRefCount(meta.containsQueries)),
          detailRow("events", compactRefCount(meta.containsEvents)),
          detailRow("entities", compactRefCount(meta.managesEntities)),
        ]),
      );
      break;
    case "BoundedContextCandidate":
      addBadge(firstValue(meta, ["languageBoundary", "ownershipBoundary"]));
      sections.push(
        detailCompartment("Boundary", [
          detailRow("capabilities", compactRefCount(meta.capabilities)),
          detailRow("concepts", compactRefCount(meta.entities)),
          detailRow("language", compactRefCount(meta.ubiquitousLanguage)),
        ]),
      );
      break;
    case "DomainEntity":
    case "ValueObject":
      addBadge(
        node.type === "ValueObject" && meta.immutable
          ? "immutable"
          : firstValue(meta, ["identityAttribute", "valueType"]),
      );
      sections.push(
        detailCompartment("Structure", [
          detailRow("identity", meta.identityAttribute || meta.identityDescription),
          detailRow("attributes", compactList(meta.attributes)),
          detailRow("invariants", compactList(meta.invariants)),
          detailRow("states", compactList(meta.lifecycleStates)),
        ]),
      );
      break;
    case "AggregateCandidate":
      addBadge(firstValue(meta, ["consistencyExpectation", "consistencyBoundaryRationale"]));
      sections.push(
        detailCompartment("Aggregate", [
          detailRow("root", refLabel(meta.root)),
          detailRow("members", compactList(meta.members)),
          detailRow("invariants", compactList(meta.invariants)),
        ]),
      );
      break;
    case "Command":
      addBadge(firstValue(meta, ["commandType", "priority"]));
      if (meta.auditRequired) {
        addBadge("audit");
      }
      sections.push(
        detailCompartment("Behavior", [
          detailRow("intent", meta.intent),
          detailRow("input", compactList(meta.input)),
          detailRow("expects", compactList(meta.expectedEvents)),
          detailRow("rejects", compactList(meta.rejectionEvents)),
          detailRow("errors", compactList(meta.possibleErrors)),
        ]),
      );
      break;
    case "Query":
      addBadge(firstValue(meta, ["queryType", "freshnessNeed"]));
      sections.push(
        detailCompartment("Read", [
          detailRow("input", compactList(meta.input)),
          detailRow("output", compactList(meta.output)),
          detailRow("reads", compactList(meta.reads)),
          detailRow("auth", meta.authorizationRule),
        ]),
      );
      break;
    case "BusinessEvent":
      addBadge(firstValue(meta, ["occurredInPastTenseName", "eventType"]));
      sections.push(
        detailCompartment("Event", [
          detailRow("payload", compactList(meta.payload)),
          detailRow("caused by", compactList(meta.causedByExternalSystems)),
          detailRow(
            "consumed by",
            compactList(meta.consumedByPolicies || meta.consumedByProcesses),
          ),
          detailRow("retention", meta.retentionNeed),
        ]),
      );
      break;
    case "Policy":
      addBadge(firstValue(meta, ["severity", "policyType"]));
      sections.push(
        detailCompartment("Policy", [
          detailRow("condition", meta.triggeringCondition || meta.expression),
          detailRow("guards", compactList(meta.guards)),
          detailRow(
            "emits",
            compactList([...refsArray(meta.emitsCommands), ...refsArray(meta.emitsEvents)]),
          ),
        ]),
      );
      break;
    case "BusinessProcess":
      addBadge(firstValue(meta, ["processKind", "completionCriterion"]));
      sections.push(
        detailCompartment("Flow", [
          detailRow("steps", compactRefCount(meta.steps)),
          detailRow("exceptions", compactRefCount(meta.exceptionScenarios)),
          detailRow("deadlines", compactRefCount(meta.temporalConstraints)),
        ]),
      );
      break;
    case "DecisionTable":
      addBadge(firstValue(meta, ["hitPolicy", "decisionOwner"]));
      sections.push(
        detailCompartment("Rules", [
          detailRow("inputs", compactList(meta.inputs)),
          detailRow("outputs", compactList(meta.outputs)),
          detailRow("rules", compactRefCount(meta.rules)),
        ]),
      );
      break;
    case "InformationItem":
      addBadge(firstValue(meta, ["type", "required"]));
      sections.push(
        detailCompartment("Data", [
          detailRow("business", meta.businessName),
          detailRow("classification", refLabel(meta.classification)),
          detailRow("privacy", meta.privacyPurpose),
          detailRow("retention", meta.retentionNeed),
        ]),
      );
      break;
    case "SecurityConstraint":
    case "PrivacyConstraint":
    case "ComplianceConstraint":
    case "NonFunctionalRequirement":
      addBadge(firstValue(meta, ["qualityType", "regulation", "law", "authenticationNeed"]));
      sections.push(
        detailCompartment("Constraint", [
          detailRow(
            "target",
            compactList(meta.constrainedElements || meta.scopedElements || meta.dataItems),
          ),
          detailRow(
            "rule",
            meta.authorizationRule || meta.controlId || meta.purpose || meta.scenario,
          ),
          detailRow("metric", [meta.metric, meta.target].filter(Boolean).join(" ")),
        ]),
      );
      break;
    case "Risk":
    case "Assumption":
    case "Hotspot":
      addBadge(firstValue(meta, ["severity", "impact", "status"]));
      sections.push(
        detailCompartment("Readiness", [
          detailRow("owner", meta.owner),
          detailRow("attached", compactList(meta.attachedTo || meta.affectedElements)),
          detailRow("rationale", meta.rationale),
        ]),
      );
      break;
    default:
      try {
        const definition = modelingElementDefinition("cim", node.type);
        const visibleFields = Array.isArray(definition?.visibleFields)
          ? definition.visibleFields
          : [];
        sections.push(
          detailCompartment(
            "Fields",
            visibleFields.slice(0, 7).map((field) => {
              const value = meta[field];
              return detailRow(
                field,
                Array.isArray(value) ? compactList(value) : compactRefCount(value),
              );
            }),
          ),
        );
      } catch {
        // Unknown CIM nodes remain editable through the property panel.
      }
  }
  if (!badges.length && !sections.length) {
    return "";
  }
  return `<div class="node-cim-details">${
    badges.length ? `<div class="node-cim-badges">${badges.join("")}</div>` : ""
  }${sections.join("")}</div>`;
}

function metadataNodeDetailsHtml(typeKey, node) {
  const meta = node.meta || {};
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, node.type);
  } catch {
    definition = null;
  }
  const badges = [];
  const addBadge = (label, issue = false) => {
    const text = String(label || "").trim();
    if (text) {
      badges.push(detailBadge(text, issue));
    }
  };
  const riskFields = [
    "productionCritical",
    "importedResource",
    "retainInProduction",
    "tracingEnabled",
    "metricsEnabled",
    "accessLogsEnabled",
    "deletionProtectionEnabled",
    "pointInTimeRecoveryEnabled",
    "eventBridgeNotificationEnabled",
    "enableKeyRotation",
    "rotationRequired",
    "publicAccessMode",
    "xrayDefault",
  ];
  riskFields.forEach((field) => {
    const value = meta[field];
    if (value === true) {
      addBadge(field.replaceAll(/([A-Z])/g, " $1").toLowerCase());
    } else if (typeof value === "string" && value.trim()) {
      addBadge(value, /disabled|public|not_recommended/i.test(value));
    }
  });
  const visibleFields = Array.isArray(definition?.visibleFields) ? definition.visibleFields : [];
  const sections = [
    detailCompartment(
      definition?.notation?.shape === "resource-card" ? "Resource" : "Detail",
      visibleFields
        .slice(0, 8)
        .map((field) =>
          detailRow(
            field,
            Array.isArray(meta[field]) ? compactList(meta[field]) : compactRefCount(meta[field]),
          ),
        ),
    ),
  ];
  const referenceRows = (definition?.references || [])
    .filter((reference) => !reference.containment && meta[reference.name])
    .slice(0, 5)
    .map((reference) =>
      detailRow(
        reference.name,
        Array.isArray(meta[reference.name])
          ? compactList(meta[reference.name])
          : compactRefCount(meta[reference.name]),
      ),
    );
  if (referenceRows.length) {
    sections.push(detailCompartment("References", referenceRows));
  }
  return badges.length || sections.some(Boolean)
    ? `<div class="node-cim-details psm-node-details">${
        badges.length ? `<div class="node-cim-badges">${badges.join("")}</div>` : ""
      }${sections.join("")}</div>`
    : "";
}

function pimNodeDetailsHtml(node) {
  const meta = node.meta || {};
  const badges = [];
  const addBadge = (value, issue = false) => {
    const text = String(value || "").trim();
    if (text) {
      badges.push(detailBadge(text, issue));
    }
  };
  const boolBadge = (field, label = field) => {
    if (meta[field]) {
      addBadge(label);
    }
  };
  const sections = [];
  switch (node.type) {
    case "ServerlessService":
      addBadge(meta.boundaryType);
      boolBadge("externallyExposed", "external");
      boolBadge("ownsData", "owns data");
      sections.push(
        detailCompartment("Ownership", [
          detailRow("functions", compactRefCount(meta.ownsFunctions)),
          detailRow("apis", compactRefCount(meta.ownsApis)),
          detailRow("channels", compactRefCount(meta.ownsChannels)),
          detailRow("stores", compactRefCount(meta.ownsStores)),
        ]),
      );
      break;
    case "Function":
      addBadge(meta.functionKind);
      boolBadge("publicEntryPoint", "public");
      boolBadge("requiresIdempotency", "idempotent");
      sections.push(
        detailCompartment("Function", [
          detailRow("responsibility", meta.responsibility),
          detailRow("handler", meta.handlerResponsibility),
          detailRow("reads", compactRefCount(meta.reads)),
          detailRow("writes", compactRefCount(meta.writes)),
          detailRow("publishes", compactRefCount(meta.publishes)),
        ]),
      );
      break;
    case "Api":
      addBadge(meta.apiStyle);
      boolBadge("authRequired", "auth");
      boolBadge("externalConsumerFacing", "external");
      sections.push(
        detailCompartment("Surface", [
          detailRow("public", meta.publicName),
          detailRow("base", meta.basePath),
          detailRow("routes", compactRefCount(meta.routes)),
        ]),
      );
      break;
    case "ApiRoute":
      addBadge([meta.method, meta.pathTemplate].filter(Boolean).join(" "));
      boolBadge("publicRoute", "public");
      boolBadge("authRequired", "auth");
      sections.push(
        detailCompartment("Integration", [
          detailRow("operation", meta.operationId),
          detailRow("function", refLabel(meta.functionIntegration)),
          detailRow("workflow", refLabel(meta.workflowIntegration)),
          detailRow("success", meta.expectedSuccessStatus),
        ]),
      );
      break;
    case "Schema":
      addBadge(meta.schemaKind);
      addBadge(meta.compatibility);
      sections.push(
        detailCompartment("Schema", [
          detailRow("version", meta.semanticVersion),
          detailRow("fields", compactRefCount(meta.fields)),
          detailRow("constraints", compactRefCount(meta.constraints)),
        ]),
      );
      break;
    case "EventType":
      addBadge(meta.semanticName);
      boolBadge("replayable", "replayable");
      boolBadge("containsPersonalData", "personal data");
      sections.push(
        detailCompartment("Event", [
          detailRow("schema", refLabel(meta.schema)),
          detailRow("producers", compactRefCount(meta.producedBy)),
          detailRow("consumers", compactRefCount(meta.consumedBy)),
        ]),
      );
      break;
    case "DataStore":
      addBadge(meta.storeKind);
      addBadge(meta.consistencyNeed);
      boolBadge("encrypted", "encrypted");
      boolBadge("containsPersonalData", "personal data");
      sections.push(
        detailCompartment("Data", [
          detailRow("models", compactRefCount(meta.ownedDataModels)),
          detailRow("access", compactRefCount(meta.accessPatterns)),
          detailRow("indexes", compactRefCount(meta.indexCandidates)),
          detailRow("volume", meta.expectedDataVolume),
        ]),
      );
      break;
    case "ObjectStore":
      addBadge("object store");
      boolBadge("versioningRequired", "versioned");
      boolBadge("eventNotificationRequired", "events");
      sections.push(
        detailCompartment("Objects", [
          detailRow("types", compactList(meta.objectTypes)),
          detailRow("schemas", compactRefCount(meta.objectMetadataSchemas)),
        ]),
      );
      break;
    case "Queue":
    case "Topic":
    case "EventBus":
      addBadge(node.type);
      addBadge(meta.deliverySemantics);
      boolBadge("encrypted", "encrypted");
      boolBadge("deadLetterRequired", "dlq");
      sections.push(
        detailCompartment("Channel", [
          detailRow("events", compactRefCount(meta.eventTypes)),
          detailRow("producers", compactRefCount(meta.producers)),
          detailRow("consumers", compactRefCount(meta.consumers)),
        ]),
      );
      break;
    case "Schedule":
      addBadge(meta.enabled === false ? "disabled" : "enabled");
      sections.push(
        detailCompartment("Timer", [
          detailRow("expression", meta.scheduleExpression),
          detailRow("time zone", meta.timeZone),
        ]),
      );
      break;
    case "Workflow":
      addBadge(meta.workflowKind);
      boolBadge("longRunning", "long running");
      boolBadge("stateful", "stateful");
      sections.push(
        detailCompartment("States", [
          detailRow("start", refLabel(meta.startState)),
          detailRow("end", compactList(meta.endStates)),
          detailRow("states", compactRefCount(meta.states)),
          detailRow("transitions", compactRefCount(meta.transitions)),
        ]),
      );
      break;
    case "WorkflowState":
      addBadge(meta.stateKind);
      boolBadge("terminal", "terminal");
      sections.push(
        detailCompartment("Invocation", [
          detailRow("function", refLabel(meta.invokesFunction)),
          detailRow("adapter", refLabel(meta.invokesAdapter)),
          detailRow("workflow", refLabel(meta.nestedWorkflow)),
        ]),
      );
      break;
    case "ExternalAdapter":
      addBadge(meta.protocolFamily);
      boolBadge("credentialsRequired", "credentials");
      sections.push(
        detailCompartment("External", [
          detailRow("system", meta.externalSystemName),
          detailRow("endpoint", meta.endpointDescription),
          detailRow("sla", meta.expectedSla),
        ]),
      );
      break;
    case "DeploymentUnit":
      addBadge(meta.unitType);
      boolBadge("independentlyDeployable", "independent");
      sections.push(
        detailCompartment("Package", [
          detailRow("contains", compactRefCount(meta.contains)),
          detailRow("envs", compactRefCount(meta.targetEnvironments)),
          detailRow("release", meta.releaseStrategy),
        ]),
      );
      break;
    case "Environment":
      addBadge(meta.environmentClass);
      boolBadge("productionLike", "prod-like");
      boolBadge("requiresApproval", "approval");
      sections.push(
        detailCompartment("Config", [
          detailRow("suffix", meta.nameSuffix),
          detailRow("sets", compactRefCount(meta.configurationSets)),
        ]),
      );
      break;
    case "IdentityProvider":
    case "Principal":
      addBadge(meta.identityKind || meta.principalKind);
      boolBadge("privileged", "privileged");
      boolBadge("mfaRequired", "mfa");
      sections.push(
        detailCompartment("Access", [
          detailRow("principals", compactRefCount(meta.principals)),
          detailRow("permissions", compactRefCount(meta.permissions)),
          detailRow("external", meta.externalRef),
        ]),
      );
      break;
    case "Secret":
    case "ConfigurationSet":
      addBadge(meta.secretKind || meta.scope);
      boolBadge("rotationRequired", "rotation");
      sections.push(
        detailCompartment("Configuration", [
          detailRow("parameters", compactRefCount(meta.parameters)),
          detailRow("env vars", compactRefCount(meta.environmentVariables)),
          detailRow("owner", meta.ownerTeam),
        ]),
      );
      break;
    default:
      try {
        const definition = modelingElementDefinition("pim", node.type);
        const visibleFields = Array.isArray(definition?.visibleFields)
          ? definition.visibleFields
          : [];
        sections.push(
          detailCompartment(
            "Fields",
            visibleFields
              .slice(0, 7)
              .map((field) =>
                detailRow(
                  field,
                  Array.isArray(meta[field])
                    ? compactList(meta[field])
                    : compactRefCount(meta[field]),
                ),
              ),
          ),
        );
      } catch {
        // Unknown PIM nodes remain editable through the property panel.
      }
  }
  if (meta.lifecycleStatus) {
    addBadge(meta.lifecycleStatus);
  }
  return badges.length || sections.length
    ? `<div class="node-cim-details">${
        badges.length ? `<div class="node-cim-badges">${badges.join("")}</div>` : ""
      }${sections.join("")}</div>`
    : "";
}

function commitUndoSnapshot(snapshot) {
  if (!snapshot) {
    return false;
  }
  return pushDiagramUndoSnapshot(snapshot);
}

function dragUndoSnapshot(nodeIds = []) {
  return nodeIds.length ? captureNodePositionUndoSnapshot(nodeIds) : captureDiagramUndoSnapshot();
}

function getNodeWidth() {
  return state.activeType === "cim" ? CIM_NODE_W : DEFAULT_NODE_W;
}

function getNodeHeight() {
  return state.activeType === "cim" ? CIM_NODE_H : DEFAULT_NODE_H;
}

export function getCurrentDiagramNodeSize() {
  return { width: getNodeWidth(), height: getNodeHeight() };
}

function normalizePinPoint(point) {
  const x = Number(point?.x);
  const y = Number(point?.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return null;
  }
  return {
    x: Math.round(x),
    y: Math.round(y),
  };
}

function normalizeEdgeAnchor(anchor) {
  if (!anchor || typeof anchor !== "object") {
    return null;
  }
  const side = anchor.side === "left" ? "left" : anchor.side === "right" ? "right" : null;
  const offsetY = Math.round(Number(anchor.offsetY));
  if (!side || !Number.isFinite(offsetY)) {
    return null;
  }
  return { side, offsetY };
}

export function edgePresentationFromLayout(layout, sourceNode, targetNode) {
  const pinPoints = Array.isArray(layout?.bendPoints)
    ? layout.bendPoints.map(normalizePinPoint).filter(Boolean)
    : [];
  const sections = Array.isArray(layout?.sections) ? layout.sections : [];
  const firstSection = sections[0];
  const lastSection = sections[sections.length - 1];
  const sourceAnchor =
    sourceNode && firstSection?.startPoint
      ? normalizeEdgeAnchor({
          side:
            Number(firstSection.startPoint.x) >= sourceNode.x + getNodeWidth() / 2
              ? "right"
              : "left",
          offsetY: Number(firstSection.startPoint.y) - sourceNode.y,
        })
      : null;
  const targetAnchor =
    targetNode && lastSection?.endPoint
      ? normalizeEdgeAnchor({
          side:
            Number(lastSection.endPoint.x) >= targetNode.x + getNodeWidth() / 2 ? "right" : "left",
          offsetY: Number(lastSection.endPoint.y) - targetNode.y,
        })
      : null;
  return {
    pinPoints,
    sourceAnchor,
    targetAnchor,
  };
}

export function pinPointsFromEdgeLayout(layout) {
  return edgePresentationFromLayout(layout, null, null).pinPoints;
}

function persistEdgePinPoints(edge) {
  if (!edge?.id) {
    return;
  }
  saveStoredEdgeLayout(state.activeType, edge.id, {
    pinPoints: Array.isArray(edge.pinPoints)
      ? edge.pinPoints.map(normalizePinPoint).filter(Boolean)
      : [],
    sourceAnchor: normalizeEdgeAnchor(edge.sourceAnchor),
    targetAnchor: normalizeEdgeAnchor(edge.targetAnchor),
  });
}

function clearTransientEdgeLayouts(edgeIds = null) {
  const edges = edgeIds
    ? [...edgeIds].map((edgeId) => connectionsById.get(edgeId)).filter(Boolean)
    : state.diagram.connections;
  edges.forEach((edge) => {
    if (!Array.isArray(edge.pinPoints) && edge.layout) {
      const presentation = edgePresentationFromLayout(edge.layout, null, null);
      edge.pinPoints = presentation.pinPoints;
      edge.sourceAnchor = normalizeEdgeAnchor(edge.sourceAnchor) || presentation.sourceAnchor;
      edge.targetAnchor = normalizeEdgeAnchor(edge.targetAnchor) || presentation.targetAnchor;
      persistEdgePinPoints(edge);
    }
    delete edge.layout;
  });
}

function normalizeContextName(value) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ");
}

function isValidContextName(value) {
  return /^[A-Za-z0-9 _-]{1,80}$/.test(value);
}

function assignContextName(node, contextName) {
  if (!node || !contextName) {
    return false;
  }
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  node.meta.contextName = contextName;
  syncNodeMetaToGraph(node);
  return true;
}

export function contextNameFromNode(node) {
  const meta = node?.meta || {};
  const explicit = normalizeContextName(meta.contextName);
  if (explicit) {
    return explicit;
  }
  const context = meta.context;
  if (typeof context === "string") {
    return normalizeContextName(context);
  }
  if (context && typeof context === "object") {
    return normalizeContextName(context.name || context.id || context.$ref);
  }
  return "";
}

function contextNodes(contextName) {
  return state.diagram.nodes.filter((node) => contextNameFromNode(node) === contextName);
}

function isBoundedContextNode(node) {
  return node?.type === "BoundedContextCandidate";
}

function boundedContextNameFromContextNode(node) {
  return normalizeContextName(node?.label || node?.meta?.name || node?.id);
}

function contextNodeForName(contextName) {
  const normalized = normalizeContextName(contextName);
  return (
    state.diagram.nodes.find(
      (node) =>
        isBoundedContextNode(node) && boundedContextNameFromContextNode(node) === normalized,
    ) || null
  );
}

function syncNodeMetaToGraph(node) {
  if (!node?.id || !state.graph?.elementsById) {
    return;
  }
  const element = state.graph.elementsById.get(node.id);
  if (!element) {
    return;
  }
  Object.assign(element, node.meta || {}, {
    id: node.id,
    eClass: node.type || element.eClass,
    name: node.label || element.name,
    label: node.label || element.label,
    x: node.x,
    y: node.y,
  });
}

function boundedContextNameSet() {
  const names = new Set();
  if (state.activeType !== "cim") {
    return names;
  }
  (state.baseModel?.boundedContexts || []).forEach((context) => {
    const name = normalizeContextName(context?.name);
    if (name) {
      names.add(name);
    }
  });
  state.diagram.nodes.forEach((node) => {
    if (isBoundedContextNode(node)) {
      const name = boundedContextNameFromContextNode(node);
      if (name) {
        names.add(name);
      }
      return;
    }
    const name = contextNameFromNode(node);
    if (name) {
      names.add(name);
    }
  });
  return names;
}

function boundsForContextMembers(memberIds = []) {
  const memberNodes = memberIds
    .map((nodeId) => state.nodesById.get(nodeId))
    .filter((node) => node && !isBoundedContextNode(node));
  if (!memberNodes.length) {
    return null;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const minX = Math.min(...memberNodes.map((node) => node.x));
  const minY = Math.min(...memberNodes.map((node) => node.y));
  const maxX = Math.max(...memberNodes.map((node) => node.x + nodeW));
  const maxY = Math.max(...memberNodes.map((node) => node.y + nodeH));
  return { minX, minY, maxX, maxY };
}

function contextNodePosition(contextName, memberIds = []) {
  const bounds = boundsForContextMembers(memberIds);
  if (bounds) {
    return {
      x: Math.max(40, bounds.minX - getNodeWidth() - 70),
      y: Math.max(40, bounds.minY),
    };
  }
  const existingCount = state.diagram.nodes.filter(isBoundedContextNode).length;
  const rect = el.canvasViewport?.getBoundingClientRect();
  const center = rect
    ? toCanvasCoordinates(rect.left + rect.width / 2, rect.top + rect.height / 2)
    : { x: 160, y: 140 };
  return {
    x: Math.round(center.x + (existingCount % 3) * 220),
    y: Math.round(center.y + Math.floor(existingCount / 3) * 150),
  };
}

function ensureBoundedContextNodeForName(contextName, memberIds = []) {
  const normalized = normalizeContextName(contextName);
  if (!normalized || state.activeType !== "cim") {
    return null;
  }
  const existing = contextNodeForName(normalized);
  if (existing) {
    return existing;
  }
  const position = contextNodePosition(normalized, memberIds);
  const node = getDefaultNode("cim", "BoundedContextCandidate", position.x, position.y);
  node.label = normalized;
  node.meta.name = normalized;
  node.meta.label = normalized;
  node.meta.languageBoundary ??= "";
  node.meta.ownershipBoundary ??= "";
  node.meta.externalIntegrationBoundary ??= false;
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function ensureBoundedContextNodesForAllNames() {
  let created = 0;
  boundedContextNameSet().forEach((contextName) => {
    const memberIds = contextNodes(contextName).map((node) => node.id);
    if (
      !contextNodeForName(contextName) &&
      ensureBoundedContextNodeForName(contextName, memberIds)
    ) {
      created += 1;
    }
  });
  if (created) {
    syncActiveViewFromVisibleGraph();
  }
  return created;
}

function boundedContextFeatureForNode(node) {
  return (
    {
      BusinessCapability: "capabilities",
      DomainEntity: "entities",
      Command: "commands",
      Query: "queries",
      BusinessEvent: "events",
      Policy: "policies",
    }[node?.type] || ""
  );
}

function syncBoundedContextMembershipRefs(contextName) {
  const contextNode = contextNodeForName(contextName);
  if (!contextNode) {
    return;
  }
  const refs = {
    capabilities: [],
    entities: [],
    commands: [],
    queries: [],
    events: [],
    policies: [],
  };
  contextNodes(contextName).forEach((node) => {
    const feature = boundedContextFeatureForNode(node);
    if (feature) {
      refs[feature].push(node.id);
    }
  });
  contextNode.meta =
    contextNode.meta && typeof contextNode.meta === "object" ? contextNode.meta : {};
  Object.assign(contextNode.meta, refs);
  syncNodeMetaToGraph(contextNode);
  const graphElement = state.graph?.elementsById?.get(contextNode.id);
  if (graphElement) {
    Object.assign(graphElement, refs);
  }
}

function nodeVisibleInBoundedContextMode(node) {
  if (state.activeType !== "cim") {
    return true;
  }
  if (state.boundedContextViewMode === "overview") {
    return isBoundedContextNode(node);
  }
  if (state.boundedContextViewMode === "focus") {
    const active = normalizeContextName(state.activeBoundedContextName);
    return Boolean(active && contextNameFromNode(node) === active);
  }
  return !isBoundedContextNode(node);
}

function visibleBoundedContextNodeIds() {
  return new Set(
    state.diagram.nodes.filter(nodeVisibleInBoundedContextMode).map((node) => node.id),
  );
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function elementLabel(element) {
  return String(element?.name || element?.label || element?.id || "Element");
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function focusStack() {
  state.canvasFocusStack = Array.isArray(state.canvasFocusStack) ? state.canvasFocusStack : [];
  return state.canvasFocusStack;
}

export function activeCanvasFocus() {
  const stack = focusStack();
  const current = stack[stack.length - 1] || null;
  if (
    !current ||
    current.typeKey !== state.activeType ||
    state.views.activeViewId !== current.focusViewId ||
    !state.views.byId.has(current.focusViewId)
  ) {
    return null;
  }
  return current;
}

export function canvasFocusLabel() {
  const focus = activeCanvasFocus();
  return focus ? `${focus.label || focus.elementId}` : "";
}

function collectContainedDescendantIds(elementId, into = new Set()) {
  const children = state.graph?.containmentByParent?.get(elementId);
  if (!children) {
    return into;
  }
  children.forEach((childId) => {
    if (!childId || into.has(childId)) {
      return;
    }
    into.add(childId);
    collectContainedDescendantIds(childId, into);
  });
  return into;
}

function collectNeighborhoodElementIds(elementId, maxDepth = 1) {
  const normalizedDepth = Math.max(1, Math.min(2, Number(maxDepth) || 1));
  const visited = new Set([elementId]);
  const queue = [{ id: elementId, depth: 0 }];
  while (queue.length) {
    const current = queue.shift();
    if (!current || current.depth >= normalizedDepth) {
      continue;
    }
    const connected = new Set();
    state.graph?.relationshipsBySource?.get(current.id)?.forEach((relationshipId) => {
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const targetId = relationship?.targetElementId || relationship?.target;
      if (targetId) {
        connected.add(targetId);
      }
    });
    state.graph?.relationshipsByTarget?.get(current.id)?.forEach((relationshipId) => {
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const sourceId = relationship?.sourceElementId || relationship?.source;
      if (sourceId) {
        connected.add(sourceId);
      }
    });
    connected.forEach((nextId) => {
      if (!nextId || visited.has(nextId) || !state.graph.elementsById.has(nextId)) {
        return;
      }
      visited.add(nextId);
      queue.push({ id: nextId, depth: current.depth + 1 });
    });
  }
  return visited;
}

function relationshipsWithinElementSet(elementSet) {
  const relationships = [];
  const seen = new Set();
  if (!elementSet?.size) {
    return relationships;
  }
  if (!state.graph?.relationshipsBySource?.size) {
    state.graph?.relationshipsById?.forEach((relationship) => {
      const sourceId = relationship.sourceElementId || relationship.source;
      const targetId = relationship.targetElementId || relationship.target;
      if (elementSet.has(sourceId) && elementSet.has(targetId)) {
        relationships.push(relationship);
      }
    });
    return relationships;
  }
  elementSet.forEach((sourceId) => {
    state.graph.relationshipsBySource.get(sourceId)?.forEach((relationshipId) => {
      if (seen.has(relationshipId)) {
        return;
      }
      const relationship = state.graph.relationshipsById.get(relationshipId);
      const targetId = relationship?.targetElementId || relationship?.target;
      if (!relationship || !elementSet.has(targetId)) {
        return;
      }
      seen.add(relationshipId);
      relationships.push(relationship);
    });
  });
  return relationships;
}

function viewNodesByElement(view) {
  return new Map(safeArray(view?.nodes).map((node) => [node.elementId, node]));
}

function viewEdgesByRelationship(view) {
  return new Map(safeArray(view?.edges).map((edge) => [edge.relationshipId, edge]));
}

function nodeForFocusElement(elementId, viewNode) {
  const element = state.graph?.elementsById?.get(elementId);
  if (!element) {
    return null;
  }
  const x = Number.isFinite(Number(viewNode?.x))
    ? Number(viewNode.x)
    : Number.isFinite(Number(element.x))
      ? Number(element.x)
      : 0;
  const y = Number.isFinite(Number(viewNode?.y))
    ? Number(viewNode.y)
    : Number.isFinite(Number(element.y))
      ? Number(element.y)
      : 0;
  return {
    id: elementId,
    type: elementType(element),
    label: elementLabel(element),
    x,
    y,
    meta: structuredClone(element),
  };
}

function edgeForFocusRelationship(relationship, viewEdge = null) {
  if (!relationship) {
    return null;
  }
  return {
    id: relationship.id,
    sourceId: relationship.sourceElementId || relationship.source,
    targetId: relationship.targetElementId || relationship.target,
    kind: relationship.kind,
    pinPoints: safeArray(viewEdge?.pinPoints).map((point) => structuredClone(point)),
    sourceAnchor: viewEdge?.sourceAnchor ? structuredClone(viewEdge.sourceAnchor) : undefined,
    targetAnchor: viewEdge?.targetAnchor ? structuredClone(viewEdge.targetAnchor) : undefined,
  };
}

function focusViewIdFor(elementId) {
  return `view-${state.activeType}-focus-${String(elementId || "").replaceAll(
    /[^A-Za-z0-9_-]+/g,
    "-",
  )}-${Date.now().toString(36)}`;
}

function createContainerFocusView(node) {
  const previousView = activeView();
  const descendantIds = [...collectContainedDescendantIds(node.id)];
  const descendantSet = new Set(descendantIds);
  const viewNodePositions = viewNodesByElement(previousView);
  const edges = relationshipsWithinElementSet(descendantSet).map((relationship) => {
    const sourceId = relationship.sourceElementId || relationship.source;
    const targetId = relationship.targetElementId || relationship.target;
    return {
      relationshipId: relationship.id,
      sourceId,
      targetId,
      visible: true,
    };
  });
  const focusNodes = descendantIds.map((elementId) => ({
    elementId,
    ...(viewNodePositions.get(elementId) || {}),
  }));
  const fakeNodes = focusNodes.map((entry, index) => {
    const element = state.graph.elementsById.get(entry.elementId) || {};
    return {
      id: entry.elementId,
      x: Number.isFinite(Number(entry.x))
        ? Number(entry.x)
        : Number.isFinite(Number(element.x))
          ? Number(element.x)
          : 80 + (index % 3) * 250,
      y: Number.isFinite(Number(entry.y))
        ? Number(entry.y)
        : Number.isFinite(Number(element.y))
          ? Number(element.y)
          : 80 + Math.floor(index / 3) * 170,
    };
  });
  ensureReadableLayout(fakeNodes, edges, getCurrentDiagramNodeSize());
  const positionById = new Map(fakeNodes.map((entry) => [entry.id, entry]));
  const edgePositions = viewEdgesByRelationship(previousView);
  return {
    id: focusViewIdFor(node.id),
    name: `${node.label || node.id} Contents`,
    level: String(state.activeType || "").toUpperCase(),
    kind: "FOCUS",
    scope: {
      rootElementId: node.id,
      scopeKind: "CONTAINER",
      depth: 999,
    },
    filters: { elementTypes: [], relationshipKinds: [] },
    layoutProfile: "CONTAINER_FOCUS",
    autoLayoutApplied: false,
    nodes: focusNodes.map((entry) => {
      const position = positionById.get(entry.elementId) || entry;
      return {
        ...entry,
        x: Number(position.x) || 0,
        y: Number(position.y) || 0,
      };
    }),
    edges: edges.map((edge) => ({
      relationshipId: edge.relationshipId,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      visible: true,
      ...(edgePositions.get(edge.relationshipId) || {}),
    })),
    hidden: { elementIds: [], relationshipIds: [] },
  };
}

function createNeighborhoodFocusView(node, depth = 1) {
  const previousView = activeView();
  const neighborhoodSet = collectNeighborhoodElementIds(node.id, depth);
  const elementIds = [...neighborhoodSet];
  const viewNodePositions = viewNodesByElement(previousView);
  const edgePositions = viewEdgesByRelationship(previousView);
  const edges = relationshipsWithinElementSet(neighborhoodSet).map((relationship) => {
    const sourceId = relationship.sourceElementId || relationship.source;
    const targetId = relationship.targetElementId || relationship.target;
    return {
      relationshipId: relationship.id,
      sourceId,
      targetId,
      visible: true,
    };
  });
  const focusNodes = elementIds.map((elementId) => ({
    elementId,
    ...(viewNodePositions.get(elementId) || {}),
  }));
  const fakeNodes = focusNodes.map((entry, index) => {
    const element = state.graph.elementsById.get(entry.elementId) || {};
    return {
      id: entry.elementId,
      x: Number.isFinite(Number(entry.x))
        ? Number(entry.x)
        : Number.isFinite(Number(element.x))
          ? Number(element.x)
          : 80 + (index % 4) * 250,
      y: Number.isFinite(Number(entry.y))
        ? Number(entry.y)
        : Number.isFinite(Number(element.y))
          ? Number(element.y)
          : 80 + Math.floor(index / 4) * 170,
    };
  });
  ensureReadableLayout(fakeNodes, edges, getCurrentDiagramNodeSize());
  const positionById = new Map(fakeNodes.map((entry) => [entry.id, entry]));
  const normalizedDepth = Math.max(1, Math.min(2, Number(depth) || 1));
  return {
    id: focusViewIdFor(`${node.id}-n${normalizedDepth}`),
    name: `${node.label || node.id} Neighborhood ${normalizedDepth}`,
    level: String(state.activeType || "").toUpperCase(),
    kind: "FOCUS",
    scope: {
      rootElementId: node.id,
      scopeKind: "NEIGHBORHOOD",
      depth: normalizedDepth,
    },
    filters: { elementTypes: [], relationshipKinds: [] },
    layoutProfile: "FOCUS_NEIGHBORHOOD",
    autoLayoutApplied: false,
    nodes: focusNodes.map((entry) => {
      const position = positionById.get(entry.elementId) || entry;
      return {
        ...entry,
        x: Number(position.x) || 0,
        y: Number(position.y) || 0,
      };
    }),
    edges: edges.map((edge) => ({
      relationshipId: edge.relationshipId,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      visible: true,
      ...(edgePositions.get(edge.relationshipId) || {}),
    })),
    hidden: { elementIds: [], relationshipIds: [] },
  };
}

export function openContainerFocus(elementId) {
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node || !isContainerElement(node)) {
    return false;
  }
  const descendants = collectContainedDescendantIds(node.id);
  if (!descendants.size) {
    setStatus("This container has no contained elements yet.");
    return false;
  }
  const previousViewId = state.views.activeViewId;
  const focusView = createContainerFocusView(node);
  prepareViewNodeIndex(focusView);
  state.views.byId.set(focusView.id, focusView);
  state.views.activeViewId = focusView.id;
  focusStack().push({
    typeKey: state.activeType,
    elementId: node.id,
    elementType: node.type,
    label: node.label || node.id,
    previousViewId,
    focusViewId: focusView.id,
  });
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  materializeActiveView();
  renderDiagram();
  notifyModelToolsChanged();
  setStatus(`Opened ${node.label || node.id}. Use Back to return.`);
  return true;
}

export function openNeighborhoodFocus(elementId, depth = 1) {
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node) {
    setStatus("Select an element before opening a neighborhood focus.");
    return false;
  }
  const neighborhood = collectNeighborhoodElementIds(node.id, depth);
  if (neighborhood.size <= 1) {
    setStatus("This element has no connected neighbors in the current model.");
    return false;
  }
  const previousViewId = state.views.activeViewId;
  const normalizedDepth = Math.max(1, Math.min(2, Number(depth) || 1));
  const focusView = createNeighborhoodFocusView(node, normalizedDepth);
  prepareViewNodeIndex(focusView);
  state.views.byId.set(focusView.id, focusView);
  state.views.activeViewId = focusView.id;
  focusStack().push({
    typeKey: state.activeType,
    elementId: node.id,
    elementType: node.type,
    label: `${node.label || node.id} depth ${normalizedDepth}`,
    previousViewId,
    focusViewId: focusView.id,
  });
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  materializeActiveView();
  renderDiagram();
  notifyModelToolsChanged();
  setStatus(`Opened ${node.label || node.id} neighborhood depth ${normalizedDepth}.`);
  return true;
}

export function closeCanvasFocus() {
  const focus = activeCanvasFocus();
  if (!focus) {
    focusStack().length = 0;
    return false;
  }
  state.views.byId.delete(focus.focusViewId);
  focusStack().pop();
  state.views.activeViewId =
    focus.previousViewId && state.views.byId.has(focus.previousViewId)
      ? focus.previousViewId
      : state.views.byId.keys().next().value || null;
  materializeActiveView();
  renderDiagram();
  notifyModelToolsChanged();
  setStatus("Returned to previous canvas");
  return true;
}

function nodeVisibleInContainerMode(node) {
  return Boolean(node);
}

function nodeVisibleInCurrentCanvasMode(node) {
  return nodeVisibleInBoundedContextMode(node) && nodeVisibleInContainerMode(node);
}

function clearContextDraftSelection() {
  state.boundedContextDraftNodeIds = new Set();
}

function notifyModelToolsChanged() {
  window.dispatchEvent(new Event("model-tools-state-change"));
}

function g6ContextBoxes() {
  if (state.activeType !== "cim" || state.boundedContextViewMode === "overview") {
    return [];
  }
  const byContext = new Map();
  state.diagram.nodes.forEach((node) => {
    const contextName = contextNameFromNode(node);
    if (!contextName) {
      return;
    }
    if (
      state.boundedContextViewMode === "focus" &&
      normalizeContextName(state.activeBoundedContextName) !== contextName
    ) {
      return;
    }
    if (!byContext.has(contextName)) {
      byContext.set(contextName, []);
    }
    byContext.get(contextName).push(node);
  });
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const paddingX = 22;
  const paddingY = 26;
  const boxes = [];
  byContext.forEach((nodes, name) => {
    if (!nodes.length) {
      return;
    }
    const minX = Math.min(...nodes.map((node) => node.x)) - paddingX;
    const minY = Math.min(...nodes.map((node) => node.y)) - paddingY;
    const maxX = Math.max(...nodes.map((node) => node.x + nodeW)) + paddingX;
    const maxY = Math.max(...nodes.map((node) => node.y + nodeH)) + paddingY;
    boxes.push({ name, minX, minY, maxX, maxY });
  });
  return boxes;
}

function g6ConnectionTargetState(source, target) {
  if (!source || !target || source.id === target.id) {
    return "illegal";
  }
  const forward = legalKindsForConnection(source.type, target.type);
  const reverse = legalKindsForConnection(target.type, source.type);
  const legal = forward.length ? forward : reverse;
  const preferred = state.preferredConnectionKind;
  return preferred
    ? legal.includes(preferred)
      ? "legal"
      : "illegal"
    : legal.length
      ? "legal"
      : "illegal";
}

function g6ConnectionTargetTypes(source, { availableTypes = [] } = {}) {
  if (!source) {
    return [];
  }
  return availableTypes.filter((targetType) => {
    if (!targetType) {
      return false;
    }
    const forward = legalKindsForConnection(source.type, targetType);
    const reverse = legalKindsForConnection(targetType, source.type);
    const legal = forward.length ? forward : reverse;
    const preferred = state.preferredConnectionKind;
    return preferred ? legal.includes(preferred) : legal.length > 0;
  });
}

function moveG6ConnectionDrag(sourceId, targetId) {
  if (!state.linkDrag || state.linkDrag.sourceId !== sourceId) {
    return;
  }
  const nextTargetId = targetId || null;
  if (state.linkDrag.hoveredTargetId === nextTargetId) {
    return;
  }
  state.linkDrag.hoveredTargetId = nextTargetId;
  updateG6ConnectionState();
}

function ensureG6Canvas() {
  window.modlessEnsureG6Canvas = ensureG6Canvas;
  window.modlessG6State = {
    ...(window.modlessG6State || {}),
    ensureCalled: true,
    rendererRequested: "antv-g6",
    activeType: state.activeType,
    stateNodes: Array.isArray(state.diagram?.nodes) ? state.diagram.nodes.length : 0,
    stateEdges: Array.isArray(state.diagram?.connections) ? state.diagram.connections.length : 0,
  };
  if (getG6Editor()) {
    return true;
  }
  el.canvasGrid?.classList.add("g6-renderer-active");
  if (!isG6Available()) {
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    window.modlessG6State = {
      ...(window.modlessG6State || {}),
      available: false,
      mounted: false,
      lastError: "AntV G6 failed to load",
    };
    setStatus("AntV G6 failed to load; model canvas renderer unavailable");
    return true;
  }
  try {
    mountG6Editor(el.g6EditorHost, {
      mapper: {
        visibleNode: nodeVisibleInCurrentCanvasMode,
        isContainer: (node) =>
          isContainerElement(node) && !(state.activeType === "cim" && isBoundedContextNode(node)),
        contextNameFromNode,
        viewProfile:
          activeCimViewProfile() || activePimViewProfile() || activeView()?.viewpoint || "",
      },
      callbacks: {
        onNodeClick: handleG6NodeClick,
        onNodeDoubleClick: handleG6NodeDoubleClick,
        onNodeHover: setHoveredNode,
        onEdgeClick: (edgeId) => selectConnection(edgeId, { openPicker: true }),
        onEdgeHover: setHoveredEdge,
        onCanvasClick: handleG6CanvasClick,
        onEscape: handleG6CanvasClick,
        onCanvasPointerDown: closeEdgeKindPicker,
        onCanvasPointerMove: () => {},
        onNodeDragStart: startG6NodeDrag,
        onNodeDrag: moveG6NodeDrag,
        onNodeDragEnd: endG6NodeDrag,
        onConnectionDragStart: startG6ConnectionDrag,
        onConnectionPointerMove: moveG6ConnectionDrag,
        onConnectionDragEnd: () => {
          state.linkDrag = null;
          updateG6ConnectionState();
        },
        onConnectionComplete: (sourceId, targetId) =>
          addConnection(sourceId, targetId, {
            interactivePicker: true,
            preferredKind: state.preferredConnectionKind,
          }),
        onConnectionCancel: () => {
          state.linkDrag = null;
          updateG6ConnectionState();
          setStatus("Connection canceled");
        },
        connectionTargetState: g6ConnectionTargetState,
        connectionTargetTypes: g6ConnectionTargetTypes,
        contextBoxes: g6ContextBoxes,
        onContextSelect: selectBoundedContext,
        onContextOpen: openBoundedContextFocus,
        onOpenContainer: openG6ContainerTool,
        onViewportChange: onG6ViewportChanged,
        onViewportSynced: () => {},
      },
    });
    el.canvasGrid?.classList.add("g6-renderer-active");
    el.canvasGrid?.classList.remove("g6-renderer-unavailable");
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6");
    return true;
  } catch (error) {
    console.error("G6 editor mount failed", error);
    el.canvasGrid?.classList.add("g6-renderer-unavailable");
    el.canvasGrid?.setAttribute("data-renderer", "antv-g6-unavailable");
    window.modlessG6State = {
      ...(window.modlessG6State || {}),
      available: isG6Available(),
      mounted: false,
      lastError: error.message || String(error),
    };
    setStatus("AntV G6 renderer failed; model canvas renderer unavailable");
    return true;
  }
}

export function initializeModelingRenderer() {
  const mountedOrUnavailable = ensureG6Canvas();
  if (mountedOrUnavailable && getG6Editor()) {
    syncCanvasIndexesFromState();
    syncG6FromState({ full: true });
  }
  return mountedOrUnavailable;
}

export function getModelingRendererDebug() {
  const editor = getG6Editor();
  const host = el.g6EditorHost;
  const hostRect = host?.getBoundingClientRect?.();
  const canvasGrid = el.canvasGrid;
  return {
    renderer: canvasGrid?.dataset?.renderer || "",
    ensureCalled: Boolean(window.modlessG6State?.ensureCalled),
    g6Available: isG6Available(),
    mounted: Boolean(editor),
    graphReady: Boolean(editor?.graph),
    activeType: state.activeType,
    stateNodes: Array.isArray(state.diagram?.nodes) ? state.diagram.nodes.length : 0,
    stateEdges: Array.isArray(state.diagram?.connections) ? state.diagram.connections.length : 0,
    hostExists: Boolean(host),
    hostMountedClass: Boolean(host?.classList?.contains("is-mounted")),
    hostRect: hostRect
      ? {
          width: Math.round(hostRect.width),
          height: Math.round(hostRect.height),
          top: Math.round(hostRect.top),
          left: Math.round(hostRect.left),
        }
      : null,
    hostChildren: host?.children?.length || 0,
    hasCanvasDescendant: Boolean(host?.querySelector?.("canvas")),
    lastError: window.modlessG6State?.lastError || "",
    g6State: window.modlessG6State || null,
  };
}

export function setContextCreateMode(enabled) {
  const isEnabled = Boolean(enabled && state.activeType === "cim");
  state.boundedContextCreateMode = isEnabled;
  if (!isEnabled) {
    clearContextDraftSelection();
    state.boundedContextDraftName = "";
  }
  renderPalette();
  applyNodeSelectionStyles();
  notifyModelToolsChanged();
}

function startBoundedContextAssignment(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  if (state.boundedContextCreateMode && state.boundedContextDraftName === normalized) {
    setContextCreateMode(false);
    setStatus(`Bounded context "${normalized}" selection canceled`);
    return;
  }
  state.boundedContextDraftName = normalized;
  state.boundedContextCreateMode = true;
  state.boundedContextDraftNodeIds = new Set();
  ensureBaseBoundedContext(normalized);
  ensureBoundedContextNodeForName(normalized);
  renderPalette();
  applyNodeSelectionStyles();
  notifyModelToolsChanged();
  setStatus(`Bounded context "${normalized}" is active. Select elements, then Done.`);
}

function ensureBaseWorkshopBoundedContexts() {
  if (!state.baseModel || typeof state.baseModel !== "object") {
    return null;
  }
  if (!Array.isArray(state.baseModel.boundedContexts)) {
    state.baseModel.boundedContexts = [];
  }
  return state.baseModel.boundedContexts;
}

function ensureBaseBoundedContext(contextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  if (contexts.some((context) => normalizeContextName(context?.name) === contextName)) {
    return;
  }
  contexts.push({ name: contextName, ubiquitousLanguage: "Domain language" });
}

function renameBaseBoundedContext(oldName, nextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  contexts.forEach((context) => {
    if (normalizeContextName(context?.name) !== oldName) {
      return;
    }
    context.name = nextName;
  });
}

function removeBaseBoundedContext(contextName) {
  const contexts = ensureBaseWorkshopBoundedContexts();
  if (!contexts) {
    return;
  }
  const kept = contexts.filter((context) => normalizeContextName(context?.name) !== contextName);
  contexts.length = 0;
  kept.forEach((context) => contexts.push(context));
}

function applyBoundedContextToNodes(nodeIds, contextName) {
  let updated = 0;
  nodeIds.forEach((nodeId) => {
    const node = state.nodesById.get(nodeId);
    if (!node) {
      return;
    }
    if (assignContextName(node, contextName)) {
      updated += 1;
    }
  });
  if (updated) {
    ensureBaseBoundedContext(contextName);
    ensureBoundedContextNodeForName(contextName, nodeIds);
    syncBoundedContextMembershipRefs(contextName);
  }
  return updated;
}

async function persistActiveWorkbenchOperation(operation) {
  if (!state.modelId || !operation?.opType) {
    return false;
  }
  return true;
}

function showBoundedContextNameModal(defaultValue = "") {
  if (
    !el.boundedContextNameOverlay ||
    !el.boundedContextNameInput ||
    !el.boundedContextNameSaveBtn ||
    !el.boundedContextNameCancelBtn
  ) {
    return Promise.resolve(null);
  }
  el.boundedContextNameInput.value = defaultValue;
  el.boundedContextNameOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  el.boundedContextNameInput.focus();
  el.boundedContextNameInput.select();
  return new Promise((resolve) => {
    const close = (value) => {
      el.boundedContextNameOverlay.classList.add("hidden");
      document.body.classList.remove("modal-open");
      el.boundedContextNameSaveBtn.removeEventListener("click", onSave);
      el.boundedContextNameCancelBtn.removeEventListener("click", onCancel);
      el.boundedContextNameOverlay.removeEventListener("click", onOverlayClick);
      el.boundedContextNameInput.removeEventListener("keydown", onKeyDown);
      resolve(value);
    };
    const onSave = () => close(el.boundedContextNameInput.value);
    const onCancel = () => close(null);
    const onOverlayClick = (event) => {
      if (event.target === el.boundedContextNameOverlay) {
        onCancel();
      }
    };
    const onKeyDown = (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onSave();
      } else if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
      }
    };
    el.boundedContextNameSaveBtn.addEventListener("click", onSave);
    el.boundedContextNameCancelBtn.addEventListener("click", onCancel);
    el.boundedContextNameOverlay.addEventListener("click", onOverlayClick);
    el.boundedContextNameInput.addEventListener("keydown", onKeyDown);
  });
}

export async function finalizeBoundedContextDraft() {
  const selectedIds = [...state.boundedContextDraftNodeIds];
  if (!selectedIds.length) {
    setStatus("Select one or more CIM elements first");
    return;
  }
  const contextOptions = availableBoundedContexts();
  const presetName = normalizeContextName(state.boundedContextDraftName);
  const chosen =
    presetName ||
    (await showBoundedContextNameModal(contextOptions[0] || DEFAULT_BOUNDED_CONTEXT_NAME));
  if (chosen === null) {
    setStatus("Bounded context creation canceled");
    return;
  }
  const contextName = normalizeContextName(chosen);
  if (!contextName) {
    setStatus("Bounded context name cannot be empty");
    return;
  }
  if (!isValidContextName(contextName)) {
    setStatus("Use 1-80 chars: letters, numbers, spaces, '-' or '_'");
    return;
  }
  pushDiagramUndoSnapshot();
  const updated = applyBoundedContextToNodes(selectedIds, contextName);
  syncActiveViewFromVisibleGraph();
  await persistActiveWorkbenchOperation({
    opType: "BOUNDED_CONTEXT_ASSIGN",
    viewId: state.views.activeViewId,
    contextName,
    elementIds: selectedIds,
  });
  state.selectedBoundedContextName = contextName;
  setContextCreateMode(false);
  renderDiagram();
  markModelDirty();
  notifyModelToolsChanged();
  openBoundedContextPanel(contextName);
  setStatus(
    `Assigned ${updated} element${updated !== 1 ? "s" : ""} to bounded context "${contextName}"`,
  );
}

function clearNodeMultiSelection() {
  state.selectedNodeIds = new Set();
}

function commitNodeLabel(node, rawText) {
  if (!node) {
    return "";
  }
  const previous = String(node.label || "").trim();
  const next = String(rawText ?? "").trim();
  const resolved = next || previous;
  node.label = resolved;
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  if (state.activeType === "cim") {
    node.meta.label = resolved;
  } else {
    node.meta.name = resolved;
  }
  if (state.selectedNodeId === node.id && el.attrPanelTitle) {
    el.attrPanelTitle.textContent = resolved;
  }
  return resolved;
}

function setNodeMultiSelection(ids) {
  state.selectedNodeIds = new Set(ids);
  state.selectedBoundedContextName = null;
}

function deselectEdges() {
  state.selectedConnectionId = null;
  ensureG6Canvas();
  updateG6Selection();
}

function applyNodeSelectionStyles() {
  ensureG6Canvas();
  updateG6Selection();
  updateG6ImpactState();
}

function applyHoverFocusStyles() {
  ensureG6Canvas();
  setG6HoverNode(state.hoveredNodeId);
}

function setHoveredNode(nodeId) {
  const nextHoveredNodeId = typeof nodeId === "string" && nodeId.trim() ? nodeId : null;
  if (state.hoveredNodeId === nextHoveredNodeId) {
    if (!nextHoveredNodeId) {
      applyHoverFocusStyles();
    }
    return;
  }
  state.hoveredNodeId = nextHoveredNodeId;
  applyHoverFocusStyles();
  if (state.connectSourceId) {
    updateG6ConnectionState();
  }
}

function toggleNodeInSelection(nodeId) {
  const nextSelection = new Set(state.selectedNodeIds);
  if (nextSelection.has(nodeId)) {
    nextSelection.delete(nodeId);
  } else {
    nextSelection.add(nodeId);
  }
  setNodeMultiSelection(nextSelection);
  state.selectedNodeId = nextSelection.size === 1 ? [...nextSelection][0] : null;
  deselectEdges();
  el.attributePanel.classList.add("hidden");
  el.workspace.classList.remove("attr-open", "mobile-right-open");
  if (el.mobileBackdrop) {
    el.mobileBackdrop.classList.add("hidden");
  }
  applyNodeSelectionStyles();
  if (nextSelection.size) {
    setStatus(`${nextSelection.size} element${nextSelection.size > 1 ? "s" : ""} selected`);
  } else {
    setStatus("Selection cleared");
  }
}

function availableBoundedContexts() {
  const contexts = new Set();
  if (state.activeType === "cim") {
    (state.baseModel?.boundedContexts || []).forEach((context) => {
      const name = normalizeContextName(context?.name);
      if (name) {
        contexts.add(name);
      }
    });
    state.diagram.nodes.forEach((node) => {
      if (isBoundedContextNode(node)) {
        const contextNodeName = boundedContextNameFromContextNode(node);
        if (contextNodeName) {
          contexts.add(contextNodeName);
        }
        return;
      }
      const name = contextNameFromNode(node);
      if (name) {
        contexts.add(name);
      }
    });
  }
  return [...contexts];
}

export function renameBoundedContext(oldName, nextName) {
  const normalizedOld = normalizeContextName(oldName);
  const normalizedNext = normalizeContextName(nextName);
  if (!normalizedOld || !normalizedNext) {
    return false;
  }
  if (normalizedOld === normalizedNext) {
    return true;
  }
  if (!isValidContextName(normalizedNext)) {
    setStatus("Use 1-80 chars: letters, numbers, spaces, '-' or '_'");
    return false;
  }
  const nodes = contextNodes(normalizedOld);
  const contextNode = contextNodeForName(normalizedOld);
  if (!nodes.length && !contextNode) {
    setStatus("No elements found for selected bounded context");
    return false;
  }
  pushDiagramUndoSnapshot();
  nodes.forEach((node) => assignContextName(node, normalizedNext));
  if (contextNode) {
    contextNode.meta =
      contextNode.meta && typeof contextNode.meta === "object" ? contextNode.meta : {};
    contextNode.label = normalizedNext;
    contextNode.meta.name = normalizedNext;
    contextNode.meta.label = normalizedNext;
    syncNodeMetaToGraph(contextNode);
  }
  renameBaseBoundedContext(normalizedOld, normalizedNext);
  ensureBaseBoundedContext(normalizedNext);
  syncBoundedContextMembershipRefs(normalizedNext);
  state.selectedBoundedContextName = normalizedNext;
  syncDiagramRenderer({ workbench: true });
  return true;
}

export function removeElementFromBoundedContext(elementId, contextName) {
  const normalized = normalizeContextName(contextName);
  const node = state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node || !normalized || contextNameFromNode(node) !== normalized) {
    return false;
  }
  pushDiagramUndoSnapshot();
  if (node.meta && typeof node.meta === "object") {
    delete node.meta.contextName;
    if (typeof node.meta.context === "string") {
      delete node.meta.context;
    }
  }
  const graphElement = state.graph?.elementsById?.get(elementId);
  if (graphElement) {
    delete graphElement.contextName;
    if (typeof graphElement.context === "string") {
      delete graphElement.context;
    }
  }
  if (!contextNodes(normalized).length && !contextNodeForName(normalized)) {
    removeBaseBoundedContext(normalized);
    state.selectedBoundedContextName = null;
  } else {
    syncBoundedContextMembershipRefs(normalized);
  }
  syncDiagramRenderer({ workbench: true });
  markModelDirty();
  setStatus(`Removed ${node.label || node.id} from "${normalized}"`);
  return true;
}

export function deleteBoundedContext(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return false;
  }
  const nodes = contextNodes(normalized);
  const contextNode = contextNodeForName(normalized);
  if (!nodes.length && !contextNode) {
    return false;
  }
  pushDiagramUndoSnapshot();
  nodes.forEach((node) => {
    if (!node.meta || typeof node.meta !== "object") {
      return;
    }
    if (normalizeContextName(node.meta.contextName) === normalized) {
      delete node.meta.contextName;
    }
    if (
      typeof node.meta.context === "string" &&
      normalizeContextName(node.meta.context) === normalized
    ) {
      delete node.meta.context;
    }
    syncNodeMetaToGraph(node);
  });
  if (contextNode) {
    state.diagram.nodes = state.diagram.nodes.filter((node) => node.id !== contextNode.id);
    state.diagram.connections = state.diagram.connections.filter(
      (edge) => edge.sourceId !== contextNode.id && edge.targetId !== contextNode.id,
    );
    removeElementFromGraph(contextNode.id);
  }
  removeBaseBoundedContext(normalized);
  state.selectedBoundedContextName = null;
  syncDiagramRenderer({ workbench: true });
  return true;
}

// ── Viewport helpers ──────────────────────────────────────────────────────────

export function toCanvasCoordinates(clientX, clientY) {
  ensureG6Canvas();
  return toGraphCoordinates(clientX, clientY);
}

let viewportUpdateScheduled = false;

function updateZoomControlLabel() {
  if (el.canvasZoomValue) {
    el.canvasZoomValue.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

export function applyViewport() {
  ensureG6Canvas();
  updateG6Viewport();
  updateZoomControlLabel();
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  // Defer cursor rendering to batch with other updates, don't render on every pan
  if (!viewportUpdateScheduled) {
    viewportUpdateScheduled = true;
    window.requestAnimationFrame(() => {
      viewportUpdateScheduled = false;
    });
  }
}

export function resetCanvasView() {
  ensureG6Canvas();
  resetG6CanvasView();
}

export function zoomCanvasBy(multiplier = 1) {
  ensureG6Canvas();
  zoomG6CanvasBy(multiplier);
}

function diagramBounds({ includeContexts = true } = {}) {
  if (!state.diagram?.nodes?.length) {
    return null;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  let minX = Math.min(...state.diagram.nodes.map((node) => node.x));
  let minY = Math.min(...state.diagram.nodes.map((node) => node.y));
  let maxX = Math.max(...state.diagram.nodes.map((node) => node.x + nodeW));
  let maxY = Math.max(...state.diagram.nodes.map((node) => node.y + nodeH));
  if (includeContexts && state.activeType === "cim") {
    const byContext = new Map();
    state.diagram.nodes.forEach((node) => {
      const contextName = contextNameFromNode(node);
      if (!contextName) {
        return;
      }
      if (!byContext.has(contextName)) {
        byContext.set(contextName, []);
      }
      byContext.get(contextName).push(node);
    });
    byContext.forEach((nodes) => {
      const contextMinX = Math.min(...nodes.map((node) => node.x)) - 22;
      const contextMinY = Math.min(...nodes.map((node) => node.y)) - 26;
      const contextMaxX = Math.max(...nodes.map((node) => node.x + nodeW)) + 22;
      const contextMaxY = Math.max(...nodes.map((node) => node.y + nodeH)) + 26;
      minX = Math.min(minX, contextMinX);
      minY = Math.min(minY, contextMinY);
      maxX = Math.max(maxX, contextMaxX);
      maxY = Math.max(maxY, contextMaxY);
    });
  }
  return {
    minX,
    minY,
    maxX,
    maxY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY),
  };
}

export function centerViewportOnDiagram({ fit = false } = {}) {
  const bounds = diagramBounds();
  if (!bounds) {
    return;
  }
  ensureG6Canvas();
  fitG6CanvasToDiagram(bounds, { fit });
}

export function restoreCanvasCamera(camera = null) {
  if (!camera || typeof camera !== "object") {
    return false;
  }
  const scale = Number(camera.scale);
  state.viewport = {
    x: Number(camera.x) || 0,
    y: Number(camera.y) || 0,
    scale: Number.isFinite(scale) && scale > 0 ? scale : 1,
  };
  applyViewport();
  return true;
}

function createMaskIcon(className, src, { ariaHidden = true } = {}) {
  const icon = document.createElement("span");
  icon.className = `${className} icon-svg icon-mask`;
  if (ariaHidden) {
    icon.setAttribute("aria-hidden", "true");
  }
  icon.style.setProperty("--icon-src", `url('${src || PLACEHOLDER_ICON}')`);
  return icon;
}

function setMaskIconSource(icon, src) {
  if (!icon) {
    return;
  }
  const normalized = String(src || "").trim();
  const resolved =
    normalized.startsWith("/") || normalized.startsWith(".") || normalized.endsWith(".svg")
      ? normalized
      : PLACEHOLDER_ICON;
  icon.style.setProperty("--icon-src", `url('${resolved}')`);
}

function definitionUi(definition) {
  const ui = definition?.ui && typeof definition.ui === "object" ? definition.ui : {};
  return {
    icon: ui.icon || definition?.icon,
    color: ui.color || definition?.color,
  };
}

function applyDefinitionAccent(element, definition) {
  if (!element) {
    return;
  }
  const color = String(definitionUi(definition).color || "").trim();
  if (color) {
    element.style.setProperty("--node-accent", color);
  } else {
    element.style.removeProperty("--node-accent");
  }
}

// ── Palette ───────────────────────────────────────────────────────────────────

function createWizardEdge(sourceId, targetId, kind) {
  const edge = {
    id: genId("e"),
    sourceId,
    targetId,
    kind,
  };
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
}

function addScaffoldNode(type, x, y, label) {
  const node = getDefaultNode(state.activeType, type, Math.round(x), Math.round(y));
  node.label = label || node.label;
  node.meta.name = node.label;
  node.meta.label = node.label;
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function idsFromReference(value) {
  if (Array.isArray(value)) {
    return value
      .map((item) =>
        typeof item === "string" ? item : item?.$ref || item?.id || item?.elementId || "",
      )
      .filter(Boolean);
  }
  if (typeof value === "string") {
    return value ? [value] : [];
  }
  if (value && typeof value === "object") {
    const id = value.$ref || value.id || value.elementId || "";
    return id ? [id] : [];
  }
  return [];
}

function addContainmentReference(parent, feature, childId) {
  const ids = new Set(idsFromReference(parent.meta?.[feature]));
  ids.add(childId);
  parent.meta = parent.meta && typeof parent.meta === "object" ? parent.meta : {};
  parent.meta[feature] = [...ids];
}

function maybeCreateContainmentEdge(parent, child) {
  const exists = state.diagram.connections.some(
    (edge) => edge.sourceId === parent.id && edge.targetId === child.id && edge.kind === "CONTAINS",
  );
  if (!exists) {
    createWizardEdge(parent.id, child.id, "CONTAINS");
  }
}

function selectedOwnerNode(ownerTypes, childNode) {
  const selectedIds = [
    state.selectedNodeId,
    ...(state.selectedNodeIds instanceof Set ? [...state.selectedNodeIds] : []),
  ].filter(Boolean);
  for (const id of selectedIds) {
    const node = state.nodesById.get(id);
    if (node && node.id !== childNode.id && ownerTypes.includes(node.type)) {
      return node;
    }
  }
  return (
    state.diagram.nodes.find(
      (node) => node.id !== childNode.id && ownerTypes.includes(node.type),
    ) || null
  );
}

function ensureOwnerNode(ownerTypes, childNode) {
  const existing = selectedOwnerNode(ownerTypes, childNode);
  if (existing) {
    return existing;
  }
  const ownerType = ownerTypes[0];
  const owner = addScaffoldNode(ownerType, childNode.x - 250, childNode.y, `${ownerType} Owner`);
  createRequiredCimCompanions(owner);
  return owner;
}

function attachNestedNode(node, { ownerTypes, feature }) {
  if (!node || !ownerTypes?.length || !feature) {
    return null;
  }
  const owner = ensureOwnerNode(ownerTypes, node);
  node.meta.__ownerId = owner.id;
  node.meta.__containmentFeature = feature;
  addContainmentReference(owner, feature, node.id);
  syncNodeMetaToGraph(node);
  syncNodeMetaToGraph(owner);
  maybeCreateContainmentEdge(owner, node);
  return owner;
}

function createRequiredCimCompanions(node) {
  if (state.activeType === "pim") {
    createRequiredPimCompanions(node);
    return;
  }
  if (state.activeType !== "cim" || !node) {
    return;
  }
  const x = node.x;
  const y = node.y;
  switch (node.type) {
    case "DomainEntity": {
      const item = addScaffoldNode("InformationItem", x + 240, y, `${node.label}Id`);
      item.meta.type = "IDENTIFIER";
      item.meta.required = true;
      node.meta.identityAttribute = item.id;
      node.meta.attributes = [item.id];
      createWizardEdge(node.id, item.id, "HAS_ATTRIBUTE");
      break;
    }
    case "BusinessCapability": {
      const goal = addScaffoldNode("BusinessGoal", x - 260, y, `${node.label} Goal`);
      node.meta.supports = [goal.id];
      createWizardEdge(node.id, goal.id, "SUPPORTS");
      break;
    }
    case "Command": {
      const event = addScaffoldNode("BusinessEvent", x + 260, y, `${node.label} Completed`);
      event.meta.occurredInPastTenseName = event.label;
      node.meta.expectedEvents = [event.id];
      createWizardEdge(node.id, event.id, "EXPECTS");
      break;
    }
    case "Query": {
      const output = addScaffoldNode("InformationItem", x + 260, y, `${node.label} Result`);
      node.meta.output = [output.id];
      createWizardEdge(node.id, output.id, "OUTPUT");
      break;
    }
    case "AggregateCandidate": {
      const root = addScaffoldNode("DomainEntity", x + 260, y, `${node.label} Root`);
      const identity = addScaffoldNode("InformationItem", x + 500, y, `${root.label}Id`);
      identity.meta.type = "IDENTIFIER";
      identity.meta.required = true;
      root.meta.identityAttribute = identity.id;
      root.meta.attributes = [identity.id];
      node.meta.root = root.id;
      node.meta.members = [root.id];
      createWizardEdge(node.id, root.id, "ROOT");
      createWizardEdge(root.id, identity.id, "HAS_ATTRIBUTE");
      break;
    }
    case "PrivacyConstraint": {
      const item = addScaffoldNode("InformationItem", x + 260, y, `${node.label} Data`);
      node.meta.dataItems = [item.id];
      createWizardEdge(node.id, item.id, "CONSTRAINS");
      break;
    }
    case "NonFunctionalRequirement": {
      const target = state.diagram.nodes.find((candidate) => candidate.id !== node.id);
      if (target) {
        node.meta.constrainedElements = [target.id];
        createWizardEdge(node.id, target.id, "CONSTRAINS");
      }
      break;
    }
    case "UbiquitousLanguageTerm":
      node.meta.term = node.label;
      break;
    case "LifecycleStateDefinition":
      node.meta.stateName = node.label;
      attachNestedNode(node, {
        ownerTypes: ["DomainEntity"],
        feature: "lifecycleStates",
      });
      break;
    case "AcceptanceCriterion":
      attachNestedNode(node, {
        ownerTypes: [
          "Requirement",
          "NonFunctionalRequirement",
          "SecurityConstraint",
          "PrivacyConstraint",
          "ComplianceConstraint",
        ],
        feature: "acceptanceCriteria",
      });
      break;
    case "QualityScenario":
      attachNestedNode(node, {
        ownerTypes: [
          "NonFunctionalRequirement",
          "SecurityConstraint",
          "PrivacyConstraint",
          "ComplianceConstraint",
        ],
        feature: "scenarios",
      });
      break;
    case "BusinessInvariant":
      attachNestedNode(node, {
        ownerTypes: ["DomainEntity", "AggregateCandidate"],
        feature: "invariants",
      });
      break;
    case "BusinessProcess": {
      const start = addScaffoldNode("StartStep", x - 240, y + 150, "Start");
      const end = addScaffoldNode("EndStep", x + 240, y + 150, "End");
      start.meta.__ownerId = node.id;
      start.meta.__containmentFeature = "steps";
      end.meta.__ownerId = node.id;
      end.meta.__containmentFeature = "steps";
      node.meta.steps = [start.id, end.id];
      createWizardEdge(start.id, end.id, "TRANSITION");
      break;
    }
    case "StartStep":
    case "EndStep":
    case "HumanTaskStep":
    case "WaitStep":
    case "DecisionStep":
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    case "CommandStep": {
      const command = addScaffoldNode("Command", x + 240, y, `${node.label} Command`);
      const event = addScaffoldNode("BusinessEvent", x + 480, y, `${command.label} Completed`);
      event.meta.occurredInPastTenseName = event.label;
      command.meta.expectedEvents = [event.id];
      node.meta.command = command.id;
      createWizardEdge(command.id, event.id, "EXPECTS");
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    }
    case "QueryStep": {
      const query = addScaffoldNode("Query", x + 240, y, `${node.label} Query`);
      const output = addScaffoldNode("InformationItem", x + 480, y, `${query.label} Result`);
      query.meta.output = [output.id];
      node.meta.query = query.id;
      createWizardEdge(query.id, output.id, "OUTPUT");
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    }
    case "EventStep": {
      const event = addScaffoldNode("BusinessEvent", x + 240, y, `${node.label} Happened`);
      event.meta.occurredInPastTenseName = event.label;
      node.meta.event = event.id;
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    }
    case "PolicyStep": {
      const policy = addScaffoldNode("Policy", x + 240, y, `${node.label} Policy`);
      node.meta.policy = policy.id;
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    }
    case "ExternalInteractionStep": {
      const system = addScaffoldNode("ExternalSystem", x + 240, y, `${node.label} System`);
      node.meta.externalSystem = system.id;
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "steps",
      });
      break;
    }
    case "DecisionTable": {
      const rule = addScaffoldNode("DecisionRule", x + 240, y, `${node.label} Rule`);
      rule.meta.__ownerId = node.id;
      rule.meta.__containmentFeature = "rules";
      rule.meta.priorityOrder = 1;
      node.meta.rules = [rule.id];
      break;
    }
    case "DecisionRule":
      attachNestedNode(node, {
        ownerTypes: ["DecisionTable"],
        feature: "rules",
      });
      break;
    case "ExceptionScenario":
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "exceptions",
      });
      break;
    case "TemporalConstraint":
      attachNestedNode(node, {
        ownerTypes: ["BusinessProcess"],
        feature: "temporalConstraints",
      });
      break;
    case "ManualDecision":
      node.meta.question = node.label;
      if (selectedOwnerNode(["ProductionReadinessAssessment"], node)) {
        attachNestedNode(node, {
          ownerTypes: ["ProductionReadinessAssessment"],
          feature: "manualDecisions",
        });
      } else {
        attachNestedNode(node, {
          ownerTypes: ["TransformationProfile"],
          feature: "requiredDecisions",
        });
      }
      break;
    case "ReadinessFinding":
      node.meta.severity = node.meta.severity || "WARNING";
      attachNestedNode(node, {
        ownerTypes: ["ProductionReadinessAssessment"],
        feature: "findings",
      });
      break;
    case "ReadinessCheck":
      node.meta.checkId = node.meta.checkId || node.id;
      node.meta.severity = node.meta.severity || "WARNING";
      attachNestedNode(node, {
        ownerTypes: ["ProductionReadinessAssessment"],
        feature: "checks",
      });
      break;
    case "StructuredDocument":
      node.meta.format = node.meta.format || "TEXT";
      break;
    case "Annotation":
      node.meta.key = node.meta.key || node.label;
      node.meta.source = node.meta.source || "frontend";
      attachNestedNode(node, {
        ownerTypes: state.diagram.nodes
          .filter((candidate) => candidate.id !== node.id && candidate.type !== "Annotation")
          .map((candidate) => candidate.type),
        feature: "annotations",
      });
      break;
    default:
      break;
  }
  state.diagram.nodes.forEach(syncNodeMetaToGraph);
}

function createRequiredPimCompanions(node) {
  if (!node) {
    return;
  }
  const x = node.x;
  const y = node.y;
  switch (node.type) {
    case "Function": {
      node.meta.functionKind ||= "COMMAND_HANDLER";
      const contract = addScaffoldNode("FunctionContract", x - 260, y, `${node.label} Contract`);
      contract.meta.__ownerId = node.id;
      contract.meta.__containmentFeature = "contract";
      contract.meta.contractVersion = "1.0.0";
      node.meta.contract = contract.id;
      createWizardEdge(node.id, contract.id, "CONTAINS");
      break;
    }
    case "Api": {
      node.meta.apiStyle ||= "RESOURCE_ORIENTED_HTTP";
      const route = addScaffoldNode("ApiRoute", x + 260, y, `${node.label} Route`);
      route.meta.__ownerId = node.id;
      route.meta.__containmentFeature = "routes";
      route.meta.method = "GET";
      route.meta.pathTemplate = "/";
      route.meta.authRequired = Boolean(node.meta.authRequired);
      node.meta.routes = [route.id];
      createWizardEdge(node.id, route.id, "CONTAINS");
      break;
    }
    case "Schema": {
      node.meta.schemaKind ||= "ENTITY";
      const field = addScaffoldNode("SchemaField", x + 260, y, `${node.label} Field`);
      field.meta.__ownerId = node.id;
      field.meta.__containmentFeature = "fields";
      field.meta.fieldType = "STRING";
      field.meta.required = true;
      node.meta.fields = [field.id];
      createWizardEdge(node.id, field.id, "CONTAINS");
      break;
    }
    case "EventType": {
      node.meta.semanticName ||= node.label;
      const schema = addScaffoldNode("Schema", x - 260, y, `${node.label} Schema`);
      schema.meta.schemaKind = "EVENT";
      node.meta.schema = schema.id;
      createWizardEdge(node.id, schema.id, "USES");
      break;
    }
    case "DataStore": {
      node.meta.storeKind ||= "DOCUMENT";
      node.meta.consistencyNeed ||= "EVENTUAL";
      const schema = addScaffoldNode("Schema", x - 300, y + 150, `${node.label} Schema`);
      schema.meta.schemaKind = "ENTITY";
      const model = addScaffoldNode("DataModel", x, y + 150, `${node.label} Model`);
      model.meta.__ownerId = node.id;
      model.meta.__containmentFeature = "ownedDataModels";
      model.meta.dataModelKind = "ENTITY";
      model.meta.schema = schema.id;
      const access = addScaffoldNode("AccessPattern", x + 300, y + 150, `${node.label} Access`);
      access.meta.__ownerId = node.id;
      access.meta.__containmentFeature = "accessPatterns";
      access.meta.patternName = "Primary lookup";
      access.meta.operation = "Get";
      node.meta.ownedDataModels = [model.id];
      node.meta.accessPatterns = [access.id];
      createWizardEdge(node.id, model.id, "CONTAINS");
      createWizardEdge(node.id, access.id, "CONTAINS");
      createWizardEdge(model.id, schema.id, "USES");
      break;
    }
    case "Workflow": {
      node.meta.workflowKind ||= "ORCHESTRATION";
      const start = addScaffoldNode("WorkflowState", x - 240, y + 150, "Start");
      const end = addScaffoldNode("WorkflowState", x + 240, y + 150, "End");
      start.meta.__ownerId = node.id;
      start.meta.__containmentFeature = "states";
      start.meta.stateKind = "TASK";
      end.meta.__ownerId = node.id;
      end.meta.__containmentFeature = "states";
      end.meta.stateKind = "SUCCESS";
      end.meta.terminal = true;
      node.meta.states = [start.id, end.id];
      node.meta.startState = start.id;
      node.meta.endStates = [end.id];
      createWizardEdge(node.id, start.id, "CONTAINS");
      createWizardEdge(node.id, end.id, "CONTAINS");
      createWizardEdge(start.id, end.id, "TRANSITION");
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
    case "Schedule":
      node.meta.scheduleExpression ||= "rate(1 day)";
      node.meta.enabled = node.meta.enabled !== false;
      break;
    case "DeploymentUnit": {
      node.meta.unitType ||= "SERVICE";
      const env =
        state.diagram.nodes.find((candidate) => candidate.type === "Environment") ||
        addScaffoldNode("Environment", x + 280, y, "Dev");
      env.meta.environmentClass ||= "DEV";
      const deployable = state.diagram.nodes.find((candidate) =>
        [
          "Function",
          "Api",
          "Workflow",
          "Queue",
          "Topic",
          "EventBus",
          "Schedule",
          "DataStore",
          "ObjectStore",
          "ExternalAdapter",
          "IdentityProvider",
          "ConfigurationSet",
        ].includes(candidate.type),
      );
      if (deployable) {
        node.meta.contains = [deployable.id];
        createWizardEdge(node.id, deployable.id, "DEPLOYS");
      }
      node.meta.targetEnvironments = [env.id];
      createWizardEdge(node.id, env.id, "DEPLOYS_TO");
      break;
    }
    case "ServerlessService":
      node.meta.boundaryType ||= "CAPABILITY_BASED";
      break;
    case "Environment":
      node.meta.environmentClass ||= "DEV";
      break;
    case "ImplementationProfile":
      node.meta.primaryLanguage ||= "TYPESCRIPT";
      node.meta.packageManager ||= "NPM";
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
    case "WorkflowState":
      node.meta.stateKind ||= "TASK";
      attachNestedNode(node, { ownerTypes: ["Workflow"], feature: "states" });
      break;
    case "ApiRoute":
      node.meta.method ||= "GET";
      node.meta.pathTemplate ||= "/";
      attachNestedNode(node, { ownerTypes: ["Api"], feature: "routes" });
      break;
    case "SchemaField":
      node.meta.fieldType ||= "STRING";
      attachNestedNode(node, { ownerTypes: ["Schema"], feature: "fields" });
      break;
    case "DataModel":
      node.meta.dataModelKind ||= "ENTITY";
      attachNestedNode(node, { ownerTypes: ["DataStore"], feature: "ownedDataModels" });
      break;
    case "AccessPattern":
      node.meta.patternName ||= node.label;
      attachNestedNode(node, { ownerTypes: ["DataStore"], feature: "accessPatterns" });
      break;
    case "ConfigParameter":
      node.meta.scope ||= "APPLICATION";
      attachNestedNode(node, { ownerTypes: ["ConfigurationSet"], feature: "parameters" });
      break;
    case "EnvironmentVariable":
      node.meta.variableName ||= node.label.replaceAll(/[^A-Za-z0-9_]+/g, "_").toUpperCase();
      attachNestedNode(node, { ownerTypes: ["ConfigurationSet"], feature: "environmentVariables" });
      break;
    default:
      break;
  }
  state.diagram.nodes.forEach(syncNodeMetaToGraph);
}

function createModelingWizard(kind) {
  const rect = el.canvasViewport?.getBoundingClientRect();
  const origin = rect
    ? toCanvasCoordinates(rect.left + rect.width / 2, rect.top + rect.height / 2)
    : { x: 120, y: 120 };
  const specs = {
    cimCommandFlow: {
      nodes: [
        ["BusinessGoal", -260, -150],
        ["BusinessCapability", 0, -150],
        ["Actor", -220, -20],
        ["Command", 0, -20],
        ["BusinessEvent", 220, -20],
      ],
      edges: [
        [1, 0, "SUPPORTS"],
        [2, 3, "ISSUES"],
        [3, 4, "EXPECTS"],
      ],
      label: "Command flow",
    },
    pimCommandHandler: {
      nodes: [
        ["Function", 0, 0],
        ["FunctionContract", -260, 0],
        ["EventType", 260, -80],
        ["Queue", 260, 80],
        ["DataStore", 0, 160],
      ],
      edges: [
        [0, 1, "USES"],
        [0, 2, "PUBLISHES"],
        [3, 2, "CONTAINS"],
        [0, 4, "READS"],
      ],
      label: "Serverless command handler",
    },
    psmLambdaEndpoint: {
      nodes: [
        ["HttpApiRoute", -260, 0],
        ["ApiGatewayIntegration", -40, 0],
        ["AwsLambdaFunction", 190, 0],
        ["IamRole", 190, 150],
        ["CloudWatchLogGroup", 430, 0],
      ],
      edges: [
        [0, 1, "ROUTES_TO"],
        [1, 2, "INVOKES"],
        [2, 3, "USES_ROLE"],
        [2, 4, "WRITES_LOGS_TO"],
      ],
      label: "AWS Lambda endpoint",
    },
  };
  const spec = specs[kind];
  if (!spec) {
    return;
  }
  pushDiagramUndoSnapshot();
  const nodes = spec.nodes.map(([type, dx, dy]) => {
    const node = getDefaultNode(
      state.activeType,
      type,
      Math.round(origin.x + dx),
      Math.round(origin.y + dy),
    );
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    return node;
  });
  if (kind === "cimCommandFlow") {
    const [goal, capability, actor, command, event] = nodes;
    goal.label = "Fulfill Business Outcome";
    capability.label = "Handle Command";
    actor.label = "Business Actor";
    command.label = "Execute Command";
    event.label = "Command Completed";
    capability.meta = {
      ...(capability.meta || {}),
      supports: [goal.id],
    };
    actor.meta = {
      ...(actor.meta || {}),
      actorType: "HUMAN",
      issuesCommands: [command.id],
    };
    command.meta = {
      ...(command.meta || {}),
      intent: "Execute a user initiated business command.",
      commandType: "USER_INTENT",
      userInitiated: true,
      priority: "MEDIUM",
      issuedBy: [actor.id],
      targetCapability: capability.id,
      expectedEvents: [event.id],
    };
  }
  spec.edges.forEach(([sourceIndex, targetIndex, edgeKind]) => {
    createWizardEdge(nodes[sourceIndex].id, nodes[targetIndex].id, edgeKind);
  });
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  workbenchSurfaces();
  markModelDirty();
  setStatus(`Created ${spec.label}`);
}

function createPaletteActionButton(
  label,
  { title = "", active = false, done = false, compact = false } = {},
) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `palette-quick-action${active ? " active" : ""}${
    done ? " palette-quick-action-done" : ""
  }${compact ? " palette-quick-action-compact" : ""}`;
  button.title = title || label;
  const labelSpan = document.createElement("span");
  labelSpan.className = "palette-quick-action-label";
  labelSpan.textContent = label;
  button.appendChild(labelSpan);
  return button;
}

function appendPaletteActionGroup(groupName, buildButtons) {
  if (!el.palette) {
    return false;
  }
  const buttons = buildButtons() || [];
  if (!buttons.length) {
    return false;
  }
  const group = document.createElement("div");
  group.className = "palette-group";
  const collapsed = isPaletteGroupCollapsed(groupName);
  group.classList.toggle("palette-group-collapsed", collapsed);

  const title = document.createElement("button");
  title.type = "button";
  title.className = "palette-group-title";
  title.setAttribute("aria-expanded", String(!collapsed));
  const titleMeta = document.createElement("span");
  titleMeta.className = "palette-group-meta";
  const titleChevron = document.createElement("span");
  titleChevron.className = "palette-group-chevron";
  titleChevron.setAttribute("aria-hidden", "true");
  const titleText = document.createElement("span");
  titleText.className = "palette-group-name";
  titleText.textContent = groupName;
  const titleCount = document.createElement("span");
  titleCount.className = "palette-group-count";
  const items = document.createElement("div");
  items.className = "palette-group-items palette-context-actions";

  titleCount.textContent = `${buttons.length} item${buttons.length === 1 ? "" : "s"}`;
  titleMeta.appendChild(titleChevron);
  titleMeta.appendChild(titleText);
  title.appendChild(titleMeta);
  title.appendChild(titleCount);
  title.addEventListener("click", () => {
    setPaletteGroupCollapsed(groupName, !isPaletteGroupCollapsed(groupName));
    renderPalette();
  });
  group.appendChild(title);

  buttons.forEach((button) => items.appendChild(button));
  group.appendChild(items);
  el.palette.appendChild(group);
  return true;
}

function activeViewElementTypeFilter() {
  return new Set((activeView()?.filters?.elementTypes || []).map(String));
}

function isActionableScopedPaletteType(type, creatableTypes) {
  return creatableTypes.has(type);
}

function filterScopedPaletteTypes(typeKey, scopedTypes, allTypes) {
  const creatableTypes = new Set(allTypes);
  const result = [];
  const seen = new Set();
  const add = (type) => {
    if (!type || seen.has(type)) {
      return;
    }
    seen.add(type);
    result.push(type);
  };
  scopedTypes.forEach((type) => {
    if (isActionableScopedPaletteType(type, creatableTypes)) {
      add(type);
      return;
    }
    allTypes.forEach((candidate) => {
      try {
        if (modelingTypeMatches(typeKey, type, candidate)) {
          add(candidate);
        }
      } catch {
        // Ignore stale metadata entries; missing types cannot be created.
      }
    });
  });
  return result;
}

function availableCimPaletteTypes(allTypes) {
  const view = activeView();
  if (
    !view ||
    String(view.id || "") === "view-cim-main" ||
    String(view.kind || "").toUpperCase() === "MAIN"
  ) {
    return allTypes;
  }
  let viewDefinition = null;
  try {
    viewDefinition = modelingViewDefinition("cim", view);
  } catch {
    viewDefinition = null;
  }
  const scoped = [
    ...(Array.isArray(viewDefinition?.palette) ? viewDefinition.palette : []),
    ...(Array.isArray(viewDefinition?.elementTypes) ? viewDefinition.elementTypes : []),
  ];
  return filterScopedPaletteTypes("cim", scoped, allTypes);
}

function availablePimPaletteTypes(allTypes) {
  const view = activeView();
  if (
    !view ||
    String(view.id || "") === "view-pim-main" ||
    String(view.kind || "").toUpperCase() === "MAIN"
  ) {
    return allTypes;
  }
  let viewDefinition = null;
  try {
    viewDefinition = modelingViewDefinition("pim", view);
  } catch {
    viewDefinition = null;
  }
  const scoped = viewDefinition
    ? [
        ...(Array.isArray(viewDefinition.palette) ? viewDefinition.palette : []),
        ...(Array.isArray(viewDefinition.elementTypes) ? viewDefinition.elementTypes : []),
      ]
    : [...activeViewElementTypeFilter()];
  const filtered = filterScopedPaletteTypes("pim", scoped, allTypes);
  return viewDefinition || scoped.length ? filtered : allTypes;
}

function availablePsmPaletteTypes(allTypes) {
  const view = activeView();
  if (
    !view ||
    String(view.id || "") === "view-psm-main" ||
    String(view.kind || "").toUpperCase() === "MAIN"
  ) {
    return allTypes;
  }
  let viewDefinition = null;
  try {
    viewDefinition = modelingViewDefinition("psm", view);
  } catch {
    viewDefinition = null;
  }
  const scoped = viewDefinition
    ? [
        ...(Array.isArray(viewDefinition.palette) ? viewDefinition.palette : []),
        ...(Array.isArray(viewDefinition.elementTypes) ? viewDefinition.elementTypes : []),
      ]
    : [...activeViewElementTypeFilter()];
  const filtered = filterScopedPaletteTypes("psm", scoped, allTypes);
  return viewDefinition || scoped.length ? filtered : allTypes;
}

function renderWizardActions() {
  const actions = (
    {
      cim: [["cimCommandFlow", "Command Flow"]],
      pim: [["pimCommandHandler", "Command Handler"]],
      psm: [["psmLambdaEndpoint", "Lambda Endpoint"]],
    }[state.activeType] || []
  ).filter(([kind]) => {
    const requiredTypes =
      {
        cimCommandFlow: ["Actor", "Command", "BusinessEvent"],
        pimCommandHandler: ["Function", "FunctionContract", "EventType", "Queue", "DataStore"],
        psmLambdaEndpoint: [
          "HttpApiRoute",
          "ApiGatewayIntegration",
          "AwsLambdaFunction",
          "IamRole",
          "CloudWatchLogGroup",
        ],
      }[kind] || [];
    const allowedTypes = activeViewElementTypeFilter();
    return !allowedTypes.size || requiredTypes.every((type) => allowedTypes.has(type));
  });
  if (!actions.length) {
    return false;
  }
  return appendPaletteActionGroup("Quick Actions", () => {
    return actions.map(([kind, label]) => {
      const button = createPaletteActionButton(label, {
        title: `Create ${label}`,
      });
      button.addEventListener("click", () => createModelingWizard(kind));
      return button;
    });
  });
}

function isPaletteGroupCollapsed(groupName) {
  const levelState = state.paletteGroupCollapsed?.[state.activeType] || {};
  if (!Object.prototype.hasOwnProperty.call(levelState, groupName)) {
    return false;
  }
  return Boolean(levelState[groupName]);
}

function setPaletteGroupCollapsed(groupName, collapsed) {
  state.paletteGroupCollapsed ??= { cim: {}, pim: {}, psm: {} };
  state.paletteGroupCollapsed[state.activeType] ??= {};
  state.paletteGroupCollapsed[state.activeType][groupName] = Boolean(collapsed);
}

function setAllPaletteGroupsCollapsed(groupNames, collapsed) {
  groupNames.forEach((groupName) => {
    if (groupName) {
      setPaletteGroupCollapsed(groupName, collapsed);
    }
  });
}

function createBoundedContextActionControls() {
  if (state.activeType !== "cim") {
    return [];
  }
  const controls = [];
  if (state.boundedContextViewMode !== "normal") {
    const back = createPaletteActionButton("Back", {
      done: true,
      title: "Return to full CIM model view",
    });
    back.addEventListener("click", closeBoundedContextSpecialView);
    controls.push(back);
  } else {
    const overview = createPaletteActionButton("Contexts", {
      title: "Show bounded-context overview",
    });
    overview.addEventListener("click", openBoundedContextOverview);
    controls.push(overview);
  }

  if (state.boundedContextCreateMode) {
    const done = createPaletteActionButton("Done", {
      done: true,
      compact: true,
      title: "Create bounded context from selected elements",
    });
    done.addEventListener("click", () => {
      finalizeBoundedContextDraft();
    });
    controls.push(done);
    const cancel = createPaletteActionButton("Cancel", {
      compact: true,
      title: "Cancel bounded context assignment",
    });
    cancel.addEventListener("click", () => {
      setContextCreateMode(false);
      setStatus("Bounded context assignment canceled");
    });
    controls.push(cancel);
  }
  const row = document.createElement("div");
  row.className = "palette-context-inline-row";
  controls.forEach((control) => row.appendChild(control));
  return [row];
}

function appendPaletteGuidance() {
  const guide = document.createElement("div");
  guide.className = "palette-guidance";
  guide.innerHTML = `
    <strong>Build this view</strong>
    <span>Drag standalone concepts. Open containers to work inside them. Add contained details in
    the inspector, and create relationships from node connection handles.</span>`;
  el.palette.appendChild(guide);
}

export function renderPalette() {
  const config = MODEL_TYPES[state.activeType];
  if (!config || !el.palette) {
    return;
  }
  const isModelingType = ["cim", "pim", "psm"].includes(state.activeType);
  if (el.paletteSearchInput) {
    el.paletteSearchInput.disabled = !isModelingType;
    el.paletteSearchInput.value = isModelingType ? state.paletteSearch[state.activeType] || "" : "";
    el.paletteSearchInput.placeholder = isModelingType ? "Search elements..." : "Search disabled";
  }
  let allTypes = [];
  if (isModelingType) {
    try {
      allTypes = modelingPalette(state.activeType);
    } catch (error) {
      console.error("Palette rendering failed", error);
      setStatus(error.message || "Backend modeling config is unavailable");
      allTypes = [];
    }
  } else {
    allTypes = Array.isArray(config.palette) ? config.palette : [];
  }
  const query = ((state.paletteSearch[state.activeType] || "") + "").trim().toLowerCase();
  const activeViewElementTypes = activeViewElementTypeFilter();
  const viewScopedTypes =
    state.activeType === "cim"
      ? availableCimPaletteTypes(allTypes)
      : state.activeType === "pim"
        ? availablePimPaletteTypes(allTypes)
        : state.activeType === "psm"
          ? availablePsmPaletteTypes(allTypes)
          : activeViewElementTypes.size
            ? allTypes.filter((type) => activeViewElementTypes.has(type))
            : allTypes;
  const actionableTypes = viewScopedTypes;
  const filteredTypes = query
    ? actionableTypes.filter((type) => type.toLowerCase().includes(query))
    : actionableTypes;
  const groupedTypes = groupPaletteTypes(filteredTypes);
  syncPaletteCollapsedUi();
  el.palette.innerHTML = "";
  el.palette.dataset.activeType = state.activeType;
  if (isModelingType) {
    appendPaletteGuidance();
  }
  const hasQuickActionsGroup = renderWizardActions();
  const namedGroups = groupedTypes.map(([groupName]) => groupName).filter(Boolean);
  if (hasQuickActionsGroup) {
    namedGroups.unshift("Quick Actions");
  }
  if (namedGroups.length > 1) {
    const groupToolbar = document.createElement("div");
    groupToolbar.className = "palette-group-toolbar";
    const toolbarLabel = document.createElement("div");
    toolbarLabel.className = "palette-group-toolbar-label";
    toolbarLabel.textContent = "Groupings";
    const toolbarActions = document.createElement("div");
    toolbarActions.className = "palette-group-toolbar-actions";
    const expandBtn = document.createElement("button");
    expandBtn.type = "button";
    expandBtn.className = "palette-group-toolbar-btn";
    expandBtn.textContent = "Expand All";
    expandBtn.addEventListener("click", () => {
      setAllPaletteGroupsCollapsed(namedGroups, false);
      renderPalette();
    });
    const collapseBtn = document.createElement("button");
    collapseBtn.type = "button";
    collapseBtn.className = "palette-group-toolbar-btn";
    collapseBtn.textContent = "Collapse All";
    collapseBtn.addEventListener("click", () => {
      setAllPaletteGroupsCollapsed(namedGroups, true);
      renderPalette();
    });
    toolbarActions.appendChild(expandBtn);
    toolbarActions.appendChild(collapseBtn);
    groupToolbar.appendChild(toolbarLabel);
    groupToolbar.appendChild(toolbarActions);
    el.palette.appendChild(groupToolbar);
  }
  groupedTypes.forEach(([groupName, types]) => {
    const group = document.createElement("div");
    group.className = "palette-group";
    const collapsed = isPaletteGroupCollapsed(groupName);
    group.classList.toggle("palette-group-collapsed", collapsed);
    if (groupName) {
      const title = document.createElement("button");
      title.type = "button";
      title.className = "palette-group-title";
      title.setAttribute("aria-expanded", String(!collapsed));
      const titleMeta = document.createElement("span");
      titleMeta.className = "palette-group-meta";
      const titleChevron = document.createElement("span");
      titleChevron.className = "palette-group-chevron";
      titleChevron.setAttribute("aria-hidden", "true");
      const titleText = document.createElement("span");
      titleText.className = "palette-group-name";
      titleText.textContent = groupName;
      const titleCount = document.createElement("span");
      titleCount.className = "palette-group-count";
      titleCount.textContent = `${types.length} item${types.length === 1 ? "" : "s"}`;
      titleMeta.appendChild(titleChevron);
      titleMeta.appendChild(titleText);
      title.appendChild(titleMeta);
      title.appendChild(titleCount);
      title.addEventListener("click", () => {
        setPaletteGroupCollapsed(groupName, !isPaletteGroupCollapsed(groupName));
        renderPalette();
      });
      group.appendChild(title);
    }
    const items = document.createElement("div");
    items.className = "palette-group-items";
    types.forEach((type) => {
      const definition = modelingElementDefinition(state.activeType, type);
      const label = definition?.displayName || type;
      const description = definition?.description || label;
      const item = document.createElement("div");
      item.className = "palette-item";
      item.draggable = true;
      item.dataset.nodeType = type;
      applyDefinitionAccent(item, definition);
      const iconImg = createMaskIcon(
        "palette-item-icon",
        definitionUi(definition).icon || PLACEHOLDER_ICON,
      );
      const labelSpan = document.createElement("span");
      labelSpan.className = "palette-item-label";
      labelSpan.textContent = label;
      item.appendChild(iconImg);
      item.appendChild(labelSpan);
      const visualRole = String(definition?.visualRole || "node");
      if (visualRole === "container") {
        const roleBadge = document.createElement("span");
        roleBadge.className = "palette-item-role";
        roleBadge.textContent = "Open";
        item.appendChild(roleBadge);
      }
      item.title =
        visualRole === "container"
          ? `${description}\nDrag to create; open it to model contained concepts.`
          : `${description}\nDrag to create.`;
      item.addEventListener("dragstart", (event) => {
        el.workspace?.classList.remove("mobile-left-open");
        el.mobileBackdrop?.classList.add("hidden");
        state.paletteDragType = type;
        event.dataTransfer.effectAllowed = "copy";
        event.dataTransfer.setData(
          "application/x-modless-palette-item",
          JSON.stringify({ level: state.activeType, type }),
        );
        event.dataTransfer.setData("application/x-modless-node-type", type);
        event.dataTransfer.setData("text/node-type", type);
        event.dataTransfer.setData("text/plain", type);
      });
      item.addEventListener("dragend", () => {
        state.paletteDragType = "";
      });
      items.appendChild(item);
    });
    group.appendChild(items);
    el.palette.appendChild(group);
  });
  if (!filteredTypes.length) {
    const empty = document.createElement("div");
    empty.className = "palette-empty";
    empty.textContent = isModelingType ? "No backend palette available" : "No matching elements";
    el.palette.appendChild(empty);
  }
}

export function syncPaletteCollapsedUi() {
  if (!el.palette) {
    return;
  }
  const shouldCollapsePalette = !!state.paletteCollapsed && state.activeType !== "artifact";
  el.palette.classList.toggle("palette-collapsed", !!state.paletteCollapsed);
  el.workspace?.classList.toggle("palette-collapsed", shouldCollapsePalette);
  const paletteSearchRow = el.paletteSearchInput?.closest(".palette-search-row");
  if (paletteSearchRow) {
    paletteSearchRow.classList.toggle("hidden", shouldCollapsePalette);
  }
}

function paletteGroupForType(type) {
  if (state.activeType === "cim") {
    try {
      return modelingElementDefinition("cim", type)?.category || "CIM";
    } catch {
      return "CIM";
    }
  }
  if (state.activeType === "pim" || state.activeType === "psm") {
    try {
      return (
        modelingElementDefinition(state.activeType, type)?.category ||
        state.activeType.toUpperCase()
      );
    } catch {
      return state.activeType.toUpperCase();
    }
  }
  const groups = {
    cim: [
      [
        "Requirements & Goals",
        [
          "Requirement",
          "AcceptanceCriterion",
          "BusinessGoal",
          "Objective",
          "KPI",
          "Stakeholder",
          "StakeholderConcern",
        ],
      ],
      ["Actors & Systems", ["Actor", "Role", "Persona", "ExternalSystem"]],
      [
        "Capabilities & Contexts",
        [
          "BusinessCapability",
          "CapabilityDependency",
          "BoundedContextCandidate",
          "UbiquitousLanguageTerm",
        ],
      ],
      ["Commands & Events", ["Command", "Query", "BusinessEvent", "BusinessError"]],
      [
        "Processes & Decisions",
        [
          "BusinessProcess",
          "ProcessStep",
          "CommandStep",
          "QueryStep",
          "EventStep",
          "PolicyStep",
          "HumanTaskStep",
          "ExternalInteractionStep",
          "DecisionStep",
          "WaitStep",
          "ProcessTransition",
          "Condition",
          "Policy",
          "DecisionTable",
          "DecisionRule",
        ],
      ],
      [
        "Domain Model",
        [
          "DomainEntity",
          "ValueObject",
          "DomainRelationship",
          "AggregateCandidate",
          "LifecycleStateDefinition",
          "BusinessInvariant",
          "InformationItem",
          "DataClassification",
          "ConsentRequirement",
        ],
      ],
      [
        "Quality & Constraints",
        [
          "Precondition",
          "Postcondition",
          "ExceptionScenario",
          "TemporalConstraint",
          "NonFunctionalRequirement",
          "QualityScenario",
          "SecurityConstraint",
          "PrivacyConstraint",
          "ComplianceConstraint",
          "RegulatoryConstraint",
        ],
      ],
      [
        "Readiness & Traceability",
        [
          "Hotspot",
          "OpenQuestion",
          "Risk",
          "RequirementLink",
          "GoalSatisfactionLink",
          "Assumption",
          "TransformationProfile",
        ],
      ],
    ],
    pim: [
      [
        "Services & Compute",
        ["ServerlessService", "Function", "ExternalAdapter", "CredentialRequirement"],
      ],
      ["Deployment", ["DeploymentUnit", "Environment", "ImplementationProfile"]],
      [
        "Contracts & Schemas",
        [
          "Schema",
          "SchemaField",
          "SchemaConstraint",
          "FunctionContract",
          "ApiContract",
          "EventEnvelope",
        ],
      ],
      ["API", ["Api", "ApiRoute", "ErrorMapping", "RequestResponseFlow", "CorsPolicy"]],
      [
        "Events",
        [
          "EventSource",
          "Trigger",
          "EventChannel",
          "EventRoutingRule",
          "Queue",
          "Topic",
          "EventBus",
          "EventType",
          "Subscription",
          "EventFlow",
          "MessageFlow",
          "PubSubFlow",
          "ExternalIntegrationFlow",
        ],
      ],
      [
        "Workflow",
        [
          "Workflow",
          "WorkflowState",
          "WorkflowTransition",
          "ErrorHandler",
          "CompensationPolicy",
          "OrchestrationFlow",
          "Flow",
        ],
      ],
      [
        "Data",
        [
          "DataStore",
          "ObjectStore",
          "DataStructure",
          "DataField",
          "AccessPattern",
          "IndexCandidate",
        ],
      ],
      [
        "Security",
        [
          "IdentityProvider",
          "Principal",
          "Permission",
          "Secret",
          "SecurityPolicy",
          "AuthPolicy",
          "AuthorizationPolicy",
          "DataProtectionPolicy",
          "CompliancePolicy",
        ],
      ],
      ["Configuration", ["ConfigurationSet", "ConfigParameter", "EnvironmentVariable"]],
      [
        "Operations",
        [
          "ConfigParameter",
          "EnvironmentVariable",
          "ResiliencePolicy",
          "RetryPolicy",
          "DeadLetterPolicy",
          "TimeoutPolicy",
          "ObservabilityConfig",
          "LoggingPolicy",
          "MetricPolicy",
          "TracingPolicy",
          "AlertPolicy",
          "Slo",
          "IdempotencyPolicy",
          "ConcurrencyPolicy",
          "RateLimitPolicy",
          "BatchPolicy",
          "OrderingPolicy",
          "CachePolicy",
          "BackupPolicy",
          "RetentionPolicy",
          "CostPolicy",
        ],
      ],
    ],
    psm: [
      [
        "Stack & Governance",
        [
          "AwsStage",
          "SamStack",
          "CfnParameter",
          "CfnMapping",
          "CfnCondition",
          "CfnOutput",
          "SamGlobals",
          "AwsNamingPolicy",
          "AwsTaggingPolicy",
          "AwsSecurityBaseline",
          "AwsTag",
          "NativeProperty",
          "AwsNativeResource",
        ],
      ],
      [
        "Lambda Compute",
        [
          "AwsLambdaFunction",
          "LambdaLayerVersion",
          "LambdaVersion",
          "LambdaAlias",
          "LambdaProvisionedConcurrencyConfig",
          "LambdaPermission",
          "LambdaFunctionUrl",
          "LambdaFileSystemConfig",
          "CodeSigningConfig",
          "LambdaEventInvokeConfig",
          "LambdaTracingConfig",
          "LambdaLoggingConfig",
        ],
      ],
      [
        "Lambda Triggers",
        [
          "ApiGatewayTrigger",
          "ApiGatewayLambdaTrigger",
          "EventBridgeLambdaTarget",
          "SnsLambdaSubscription",
          "LambdaEventSourceMapping",
          "SqsLambdaEventSourceMapping",
          "DynamoDbStreamLambdaEventSourceMapping",
          "LambdaDeadLetterConfig",
          "LambdaDestinationConfig",
        ],
      ],
      [
        "API Gateway & Edge",
        [
          "ApiGatewayApi",
          "ApiGatewayRoute",
          "ApiGatewayIntegration",
          "ApiGatewayStage",
          "ApiGatewayAuthorizer",
          "JwtAuthorizer",
          "CognitoAuthorizer",
          "LambdaAuthorizer",
          "ApiGatewayDomainName",
          "ApiGatewayBasePathMapping",
          "WafWebAclAssociation",
        ],
      ],
      [
        "EventBridge",
        [
          "EventBridgeBus",
          "EventBridgeRule",
          "EventBridgeTarget",
          "EventBridgeInputTransformer",
          "EventBridgeArchive",
          "EventBridgeSchedule",
          "EventBridgePipe",
          "EventBridgeConnection",
          "EventBridgeApiDestination",
          "AwsRetryPolicy",
        ],
      ],
      [
        "Queues & Topics",
        [
          "SqsQueue",
          "SqsRedrivePolicy",
          "SqsRedriveAllowPolicy",
          "SqsQueuePolicy",
          "SnsTopic",
          "SnsSubscription",
          "SnsTopicPolicy",
          "EventSchema",
        ],
      ],
      [
        "Step Functions",
        [
          "StepFunctionStateMachine",
          "AslDocument",
          "AslState",
          "StepFunctionLoggingConfig",
          "StepFunctionTracingConfig",
          "StepFunctionEvent",
        ],
      ],
      [
        "Data & Storage",
        [
          "DynamoDbTable",
          "DynamoDbAttributeDefinition",
          "DynamoDbKeySchemaElement",
          "DynamoDbProjection",
          "DynamoDbProvisionedThroughput",
          "DynamoDbOnDemandThroughput",
          "DynamoDbLocalSecondaryIndex",
          "DynamoDbGlobalSecondaryIndex",
          "DynamoDbReplicaSpecification",
          "DynamoDbStreamSpecification",
          "DynamoDbTimeToLiveSpecification",
          "DynamoDbSseSpecification",
          "S3Bucket",
          "S3BucketEncryption",
          "S3LifecycleConfiguration",
          "S3LifecycleRule",
          "S3PublicAccessBlockConfiguration",
          "S3NotificationConfiguration",
          "S3NotificationRule",
          "S3ReplicationConfiguration",
          "S3BucketPolicy",
        ],
      ],
      [
        "Security",
        [
          "IamPolicy",
          "IamManagedPolicy",
          "IamStatement",
          "IamRole",
          "CognitoUserPool",
          "CognitoUserPoolClient",
          "CognitoUserPoolGroup",
          "CognitoUserPoolDomain",
          "CognitoIdentityPool",
          "SecretsManagerSecret",
          "SsmParameter",
          "KmsKey",
          "KmsAlias",
          "SecretRotationSchedule",
          "SecretsManagerResourcePolicy",
          "VpcConfig",
          "VpcEndpointReference",
          "SecurityGroup",
        ],
      ],
      ["Deployment", ["SamStack", "AwsStage", "EnvironmentConfig", "LambdaEnvironmentVariable"]],
      [
        "Observability",
        [
          "CloudWatchAlarm",
          "CloudWatchCompositeAlarm",
          "CloudWatchLogGroup",
          "CloudWatchMetricFilter",
          "CloudWatchLogSubscriptionFilter",
          "CloudWatchDashboard",
          "XRayTracingConfig",
        ],
      ],
    ],
  };
  for (const [name, members] of groups[state.activeType] || []) {
    if (members.includes(type)) {
      return name;
    }
  }
  return "Other";
}

function groupPaletteTypes(types) {
  const buckets = new Map();
  types.forEach((type) => {
    const groupName = paletteGroupForType(type);
    if (!buckets.has(groupName)) {
      buckets.set(groupName, []);
    }
    buckets.get(groupName).push(type);
  });
  return [...buckets.entries()]
    .sort(([left], [right]) => String(left).localeCompare(String(right)))
    .map(([groupName, members]) => [
      groupName,
      [...members].sort((left, right) => {
        const leftDefinition = modelingElementDefinition(state.activeType, left);
        const rightDefinition = modelingElementDefinition(state.activeType, right);
        return String(leftDefinition?.displayName || left).localeCompare(
          String(rightDefinition?.displayName || right),
        );
      }),
    ]);
}

// ── Node rendering ────────────────────────────────────────────────────────────

export function renderNodes() {
  // Compatibility wrapper: callers still use the old name, but node rendering
  // is handled exclusively by the G6 adapter.
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  updateG6Selection();
  updateG6ImpactState();
  return;
}

// ── Edge rendering ────────────────────────────────────────────────────────────

let edgeKindPickerBound = false;

function canvasToViewportPoint(x, y) {
  return {
    x: x * state.viewport.scale + state.viewport.x,
    y: y * state.viewport.scale + state.viewport.y,
  };
}

function setHoveredEdge(edgeId) {
  hoveredEdgeId = edgeId || null;
  ensureG6Canvas();
  setG6HoverEdge(hoveredEdgeId);
}

function clearHoveredEdge() {
  setHoveredEdge(null);
}

function closeEdgeKindPicker() {
  state.edgeKindPicker.open = false;
  state.edgeKindPicker.edgeId = null;
  state.edgeKindPicker.options = [];
  if (el.edgeKindPicker) {
    el.edgeKindPicker.classList.add("hidden");
    el.edgeKindPicker.classList.remove("edge-kind-picker-below");
    el.edgeKindPicker.style.removeProperty("left");
    el.edgeKindPicker.style.removeProperty("top");
  }
}

function buildDirectedKindOptions(source, target) {
  const options = [];
  const pushOption = (fromNode, toNode, kind) => {
    options.push({
      value: `${fromNode.id}|${toNode.id}|${kind}`,
      label: `${fromNode.label || fromNode.type} -> ${
        toNode.label || toNode.type
      }: ${cimEdgeLabel({ kind })}`,
      sourceId: fromNode.id,
      targetId: toNode.id,
      kind,
    });
  };
  legalKindsForConnection(source.type, target.type).forEach((kind) => {
    pushOption(source, target, kind);
  });
  legalKindsForConnection(target.type, source.type).forEach((kind) => {
    pushOption(target, source, kind);
  });
  const unique = [];
  const seen = new Set();
  options.forEach((option) => {
    if (seen.has(option.value)) {
      return;
    }
    seen.add(option.value);
    unique.push(option);
  });
  return unique;
}

function legalKindsForConnection(sourceType, targetType) {
  const kinds = new Set(legalKinds(state.activeType, sourceType, targetType));
  if (state.activeType === "cim" && state.preferredConnectionKind === "TRACE") {
    kinds.add("TRACE");
  }
  return [...kinds];
}

function updateEdgeKind(edgeId, nextKind) {
  const edge = state.diagram.connections.find((item) => item.id === edgeId);
  if (!edge || !nextKind) {
    return;
  }
  const [sourceId, targetId, kind] = String(nextKind).split("|");
  if (!sourceId || !targetId || !kind) {
    return;
  }
  if (edge.sourceId === sourceId && edge.targetId === targetId && edge.kind === kind) {
    return;
  }
  const undoSnapshot = captureDiagramUndoSnapshot();
  edge.sourceId = sourceId;
  edge.targetId = targetId;
  edge.kind = kind;
  addConnectionToGraphAndActiveView(edge);
  commitUndoSnapshot(undoSnapshot);
  ensureG6Canvas();
  updateG6Edge(edgeId);
  updateG6Selection();
  markModelDirty();
  setStatus(`Connection updated: ${kind}`);
}

function ensureEdgeKindPickerBindings() {
  if (edgeKindPickerBound || !el.edgeKindPicker || !el.edgeKindSelect) {
    return;
  }
  edgeKindPickerBound = true;
  el.edgeKindSelect.addEventListener("change", (event) => {
    const edgeId = state.edgeKindPicker.edgeId;
    updateEdgeKind(edgeId, event.target.value);
  });
  document.addEventListener("mousedown", (event) => {
    if (!state.edgeKindPicker.open) {
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    if (!target) {
      closeEdgeKindPicker();
      return;
    }
    if (target.closest(".edge-kind-picker")) {
      return;
    }
    closeEdgeKindPicker();
  });
  document.addEventListener(
    "touchstart",
    (event) => {
      if (!state.edgeKindPicker.open) {
        return;
      }
      const target = event.target instanceof Element ? event.target : null;
      if (!target) {
        closeEdgeKindPicker();
        return;
      }
      if (target.closest(".edge-kind-picker")) {
        return;
      }
      closeEdgeKindPicker();
    },
    { passive: true },
  );
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && state.edgeKindPicker.open) {
      closeEdgeKindPicker();
    }
  });
}

function openEdgeKindPicker(edgeId, options, canvasX, canvasY) {
  if (!el.edgeKindPicker || !el.edgeKindSelect) {
    return;
  }
  ensureEdgeKindPickerBindings();
  const edge = state.diagram.connections.find((item) => item.id === edgeId);
  if (!edge) {
    closeEdgeKindPicker();
    return;
  }
  state.edgeKindPicker.open = true;
  state.edgeKindPicker.edgeId = edgeId;
  state.edgeKindPicker.options = [...options];
  state.edgeKindPicker.x = canvasX;
  state.edgeKindPicker.y = canvasY;

  el.edgeKindSelect.innerHTML = options
    .map((option) => `<option value="${option.value}">${escapeHtml(option.label)}</option>`)
    .join("");
  const selectedValue = `${edge.sourceId}|${edge.targetId}|${edge.kind}`;
  const selectedExists = options.some((option) => option.value === selectedValue);
  el.edgeKindSelect.value = selectedExists ? selectedValue : options[0].value;
  const pos = canvasToViewportPoint(canvasX, canvasY);
  el.edgeKindPicker.classList.remove("hidden");
  const viewportRect = el.canvasViewport?.getBoundingClientRect?.();
  const pickerRect = el.edgeKindPicker.getBoundingClientRect();
  const margin = 12;
  const width = pickerRect?.width || 220;
  const height = pickerRect?.height || 64;
  const viewportWidth = viewportRect?.width || window.innerWidth;
  const viewportHeight = viewportRect?.height || window.innerHeight;
  const x = Math.min(Math.max(pos.x, width / 2 + margin), viewportWidth - width / 2 - margin);
  const enoughSpaceAbove = pos.y - height - margin >= 0;
  const y = enoughSpaceAbove
    ? Math.max(pos.y - margin, height + margin)
    : Math.min(pos.y + margin, viewportHeight - height - margin);
  el.edgeKindPicker.classList.toggle("edge-kind-picker-below", !enoughSpaceAbove);
  el.edgeKindPicker.style.left = `${Math.round(x)}px`;
  el.edgeKindPicker.style.top = `${Math.round(y)}px`;
  el.edgeKindSelect.focus();
}

function openG6EdgeKindPicker(edgeId) {
  const edge = connectionsById.get(edgeId);
  if (!edge || edge.bundle) {
    return;
  }
  const source = state.nodesById.get(edge.sourceId);
  const target = state.nodesById.get(edge.targetId);
  if (!source || !target) {
    return;
  }
  const options = buildDirectedKindOptions(source, target);
  if (!options.length) {
    return;
  }
  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  openEdgeKindPicker(
    edge.id,
    options,
    (source.x + nodeW / 2 + target.x + nodeW / 2) / 2,
    (source.y + nodeH / 2 + target.y + nodeH / 2) / 2,
  );
}

export function renderEdges() {
  ensureG6Canvas();
  if (
    state.selectedConnectionId &&
    !state.diagram.connections.some((edge) => edge.id === state.selectedConnectionId)
  ) {
    state.selectedConnectionId = null;
  }
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  updateG6Selection();
}

function hasModelArtifact(keys) {
  const root = state.baseModel && typeof state.baseModel === "object" ? state.baseModel : {};
  return keys.some((key) => {
    const value = root[key];
    return Array.isArray(value) ? value.length > 0 : Boolean(value);
  });
}

export function renderDiagram() {
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  renderG6Diagram();
  workbenchSurfaces();
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  el.workspace?.classList.toggle("cim-view-active", state.activeType === "cim");
  el.workspace?.classList.toggle("pim-view-active", state.activeType === "pim");
  el.workspace?.classList.toggle("psm-view-active", state.activeType === "psm");
  el.workspace?.setAttribute("data-cim-view-profile", activeCimViewProfile() || "");
  el.workspace?.setAttribute("data-pim-view-profile", activePimViewProfile() || "");
  el.workspace?.setAttribute(
    "data-psm-view-profile",
    state.activeType === "psm" ? activeView()?.viewpoint || "" : "",
  );
}

// Compatibility helper for non-canvas modules that changed semantic state.
// The public name is kept for existing callers, but the modeling surface is
// G6-only: this applies a diff to the graph renderer.
export function syncDiagramRenderer({ full = false, workbench = false } = {}) {
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full });
  if (workbench) {
    workbenchSurfaces();
  }
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  el.workspace?.classList.toggle("cim-view-active", state.activeType === "cim");
  el.workspace?.classList.toggle("pim-view-active", state.activeType === "pim");
  el.workspace?.classList.toggle("psm-view-active", state.activeType === "psm");
}

export function syncRendererSelection() {
  ensureG6Canvas();
  updateG6Selection();
  updateG6ContextBoxes(null, { useCache: true });
}

// ── Canvas event handlers ─────────────────────────────────────────────────────

function eventModifierState(event) {
  return {
    shiftKey: Boolean(event?.shiftKey),
    ctrlKey: Boolean(event?.ctrlKey),
    metaKey: Boolean(event?.metaKey),
  };
}

function handleG6NodeClick(nodeId, event = {}) {
  if (state.activeType === "cim" && state.boundedContextCreateMode) {
    const node = state.nodesById.get(nodeId);
    if (isBoundedContextNode(node)) {
      return;
    }
    const next = new Set(state.boundedContextDraftNodeIds);
    if (next.has(nodeId)) {
      next.delete(nodeId);
    } else {
      next.add(nodeId);
    }
    state.boundedContextDraftNodeIds = next;
    updateG6ImpactState();
    setStatus(`${next.size} element${next.size !== 1 ? "s" : ""} selected for bounded context`);
    return;
  }

  const modifiers = eventModifierState(event);
  if (modifiers.shiftKey || modifiers.ctrlKey || modifiers.metaKey) {
    toggleNodeInSelection(nodeId);
    return;
  }
  const node = state.nodesById.get(nodeId);
  if (state.activeType === "cim" && isBoundedContextNode(node)) {
    if (state.boundedContextViewMode === "overview") {
      setNodeMultiSelection([nodeId]);
      selectBoundedContext(boundedContextNameFromContextNode(node));
      return;
    }
    startBoundedContextAssignment(boundedContextNameFromContextNode(node));
    setNodeMultiSelection([nodeId]);
    updateG6Selection();
    return;
  }
  setNodeMultiSelection([nodeId]);
  activateNode(nodeId);
  updateG6Selection();
}

function handleG6NodeDoubleClick(nodeId, event = {}) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  event?.preventDefault?.();
  if (state.activeType === "cim" && isBoundedContextNode(node)) {
    openBoundedContextFocus(boundedContextNameFromContextNode(node));
    return;
  }
  if (isContainerElement(node)) {
    openContainerFocus(nodeId);
    return;
  }
  const undoSnapshot = captureDiagramUndoSnapshot();
  const startLabel = node.label;
  beginG6InlineLabelEdit(node, {
    onCommit: (rawText) => {
      const resolved = commitNodeLabel(node, rawText);
      syncNodeMetaToGraph(node);
      updateG6Node(node.id);
      if (resolved !== startLabel) {
        commitUndoSnapshot(undoSnapshot);
        markModelDirty();
      }
    },
  });
}

function handleG6CanvasClick() {
  clearHoveredEdge();
  closeEdgeKindPicker();
  closeAttributePanel();
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.selectedBoundedContextName = null;
  updateG6Selection();
}

function startG6NodeDrag(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }
  clearHoveredEdge();
  closeEdgeKindPicker();
  clearTransientEdgeLayouts(edgeIdsByNodeId.get(nodeId));
  state.dragNode = {
    id: nodeId,
    startX: 0,
    startY: 0,
    nodeX: node.x,
    nodeY: node.y,
    moved: false,
    undoSnapshot: dragUndoSnapshot([nodeId]),
  };
}

function moveG6NodeDrag(nodeId, position) {
  const node = state.nodesById.get(nodeId);
  if (!node || !position) {
    return;
  }
  node.x = Math.round(position.x);
  node.y = Math.round(position.y);
  node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
  node.meta.x = node.x;
  node.meta.y = node.y;
  state.dragNode = state.dragNode || { id: nodeId };
  state.dragNode.moved = true;
  updateG6NodeIcons();
}

function endG6NodeDrag(nodeId, position, { moved = false } = {}) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    state.dragNode = null;
    return;
  }
  if (position) {
    node.x = Math.round(position.x);
    node.y = Math.round(position.y);
    node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
    node.meta.x = node.x;
    node.meta.y = node.y;
  }
  syncNodeMetaToGraph(node);
  if (moved || state.dragNode?.moved) {
    commitUndoSnapshot(state.dragNode?.undoSnapshot);
    persistNodePositionInActiveView(node);
    markModelDirty();
  }
  refreshG6Edges([...(edgeIdsByNodeId.get(node.id) || [])]);
  updateG6ContextBoxes();
  updateG6Selection();
  state.dragNode = null;
}

function startG6ConnectionDrag(sourceId) {
  state.linkDrag = { sourceId, pointerX: 0, pointerY: 0, hoveredTargetId: null };
  updateG6ConnectionState();
  setStatus("Drag to another element to create a legal connection");
}

function openG6ContainerTool(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (state.activeType === "cim" && isBoundedContextNode(node)) {
    openBoundedContextFocus(boundedContextNameFromContextNode(node));
    return;
  }
  openContainerFocus(nodeId);
}

function selectConnection(connectionId, { openPicker = false } = {}) {
  if (!connectionId) {
    return;
  }
  if (state.selectedConnectionId === connectionId) {
    closeAttributePanel();
    ensureG6Canvas();
    state.selectedConnectionId = null;
    updateG6Selection();
    return;
  }
  openConnectionPanel(connectionId);
  ensureG6Canvas();
  updateG6Selection();
  updateG6Edge(connectionId);
  if (openPicker) {
    openG6EdgeKindPicker(connectionId);
  }
  setStatus("Connection selected (press Delete to remove)");
}

function activateNode(nodeId) {
  if (state.connectMode) {
    if (!state.connectSourceId) {
      state.connectSourceId = nodeId;
      setStatus(`Connection source: ${nodeId}. Select target.`);
      ensureG6Canvas();
      updateG6ConnectionState();
      return;
    }
    if (state.connectSourceId === nodeId) {
      setStatus("Source and target cannot be the same");
      return;
    }
    addConnection(state.connectSourceId, nodeId, {
      interactivePicker: true,
      preferredKind: state.preferredConnectionKind,
    });
    state.connectSourceId = null;
    ensureG6Canvas();
    updateG6ConnectionState();
    return;
  }

  // In impact mode, clicking a node fetches impact analysis for it
  if (state.impactMode) {
    if (!state.modelId) {
      setStatus("Save or load a model first to use impact analysis");
    } else {
      fetchImpact(nodeId);
    }
    return;
  }

  // Toggle: clicking the same node again closes the panel
  if (state.selectedNodeId === nodeId) {
    closeAttributePanel();
    clearNodeMultiSelection();
    return;
  }

  setNodeMultiSelection([nodeId]);
  openAttributePanel(nodeId);
}

function selectBoundedContext(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  state.selectedBoundedContextName = normalized;
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  applyNodeSelectionStyles();
  openBoundedContextPanel(normalized);
  ensureG6Canvas();
  updateG6ContextBoxes();
  updateG6Selection();
}

function openBoundedContextFocus(contextName) {
  const normalized = normalizeContextName(contextName);
  if (!normalized) {
    return;
  }
  state.boundedContextViewMode = "focus";
  state.activeBoundedContextName = normalized;
  state.boundedContextCreateMode = false;
  clearContextDraftSelection();
  renderDiagram();
  notifyModelToolsChanged();
  setStatus(`Opened bounded context "${normalized}". Use Back to return.`);
}

export function openBoundedContextOverview() {
  const createdContextNodes = ensureBoundedContextNodesForAllNames();
  const hasContextNodes = state.diagram.nodes.some(isBoundedContextNode);
  if (!hasContextNodes) {
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    state.boundedContextCreateMode = false;
    clearContextDraftSelection();
    renderDiagram();
    notifyModelToolsChanged();
    setStatus("No bounded contexts yet. Use New Context or add a BoundedContextCandidate.");
    return;
  }
  state.boundedContextViewMode = "overview";
  state.activeBoundedContextName = "";
  state.boundedContextCreateMode = false;
  clearContextDraftSelection();
  renderDiagram();
  if (createdContextNodes) {
    markModelDirty();
  }
  notifyModelToolsChanged();
  setStatus("Bounded context overview");
}

export function closeBoundedContextSpecialView() {
  state.boundedContextViewMode = "normal";
  state.activeBoundedContextName = "";
  renderDiagram();
  notifyModelToolsChanged();
  setStatus("Returned to full model view");
}

// ── Drag-and-drop from palette ────────────────────────────────────────────────

export function setupDnD() {
  ensureG6Canvas();
  renderG6Diagram();
  el.canvasViewport.addEventListener("dragover", (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
  });
  el.canvasViewport.addEventListener("drop", async (e) => {
    e.preventDefault();
    let paletteItem = null;
    try {
      paletteItem = JSON.parse(e.dataTransfer.getData("application/x-modless-palette-item"));
    } catch {
      paletteItem = null;
    }
    const type = String(
      paletteItem?.type ||
        e.dataTransfer.getData("application/x-modless-node-type") ||
        e.dataTransfer.getData("text/node-type") ||
        state.paletteDragType ||
        "",
    ).trim();
    state.paletteDragType = "";
    const allowedTypes = new Set(modelingPalette(state.activeType));
    if (
      !type ||
      (paletteItem?.level && paletteItem.level !== state.activeType) ||
      !allowedTypes.has(type)
    ) {
      setStatus(type ? `${type} is not a standalone palette element` : "Invalid palette drop");
      return;
    }
    pushDiagramUndoSnapshot();
    const pos = toCanvasCoordinates(e.clientX, e.clientY);
    const beforeNodeIds = new Set(state.diagram.nodes.map((item) => item.id));
    const beforeEdgeIds = new Set(state.diagram.connections.map((item) => item.id));
    const node = getDefaultNode(state.activeType, type, Math.round(pos.x), Math.round(pos.y));
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    materializeActiveView();
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = state.diagram;
    }
    syncCanvasIndexesFromState();
    ensureG6Canvas();
    const addedVisibleNodes = state.diagram.nodes.filter((item) => !beforeNodeIds.has(item.id));
    const addedVisibleEdges = state.diagram.connections.filter(
      (item) => !beforeEdgeIds.has(item.id),
    );
    if (addedVisibleNodes.length === 1 && !addedVisibleEdges.length) {
      addG6Node(addedVisibleNodes[0]);
    } else {
      syncG6FromState({ full: false });
    }
    updateG6Selection();
    updateG6ContextBoxes();
    workbenchSurfaces();
    const created = state.graph.elementsById.get(node.id);
    setStatus(`Added ${created?.eClass || type}`);
    markModelDirty();
  });
}

// ── Connection management ─────────────────────────────────────────────────────

export function addConnection(
  sourceId,
  targetId,
  { interactivePicker = false, preferredKind = null } = {},
) {
  const source = state.nodesById.get(sourceId);
  const target = state.nodesById.get(targetId);
  if (!source || !target) {
    return false;
  }
  if (source.id === target.id) {
    setStatus("Source and target cannot be the same");
    return false;
  }
  const forwardKinds = legalKindsForConnection(source.type, target.type);
  const reverseKinds = legalKindsForConnection(target.type, source.type);
  if (!forwardKinds.length && !reverseKinds.length) {
    if (createShortcutConnection(source, target)) {
      return true;
    }
    setStatus("Illegal connection type for selected nodes");
    return false;
  }
  let resolvedSource = source;
  let resolvedTarget = target;
  let resolvedKinds = forwardKinds;
  if (!forwardKinds.length && reverseKinds.length) {
    resolvedSource = target;
    resolvedTarget = source;
    resolvedKinds = reverseKinds;
  }
  let kind = resolvedKinds.includes(preferredKind) ? preferredKind : resolvedKinds[0];

  const exists = state.diagram.connections.some(
    (edge) => edge.sourceId === resolvedSource.id && edge.targetId === resolvedTarget.id,
  );
  if (exists) {
    setStatus("Connection already exists between these elements");
    return false;
  }

  const edge = {
    id: genId("e"),
    sourceId: resolvedSource.id,
    targetId: resolvedTarget.id,
    kind,
  };
  pushDiagramUndoSnapshot();
  state.diagram.connections.push(edge);
  addConnectionToGraphAndActiveView(edge);
  state.selectedConnectionId = edge.id;
  ensureG6Canvas();
  connectionsById.set(edge.id, edge);
  [edge.sourceId, edge.targetId].forEach((nodeId) => {
    if (!edgeIdsByNodeId.has(nodeId)) {
      edgeIdsByNodeId.set(nodeId, new Set());
    }
    edgeIdsByNodeId.get(nodeId).add(edge.id);
  });
  addG6Edge(edge);
  updateG6Selection();
  markModelDirty();
  if (interactivePicker) {
    const nodeW = getNodeWidth();
    const nodeH = getNodeHeight();
    const sx = resolvedSource.x + nodeW / 2;
    const sy = resolvedSource.y + nodeH / 2;
    const tx = resolvedTarget.x + nodeW / 2;
    const ty = resolvedTarget.y + nodeH / 2;
    const options = buildDirectedKindOptions(source, target);
    if (options.length) {
      openEdgeKindPicker(edge.id, options, (sx + tx) / 2, (sy + ty) / 2);
    }
  }
  if (!forwardKinds.length && reverseKinds.length) {
    setStatus(
      `Connection added with legal direction: ${
        resolvedSource.label || resolvedSource.type
      } -> ${resolvedTarget.label || resolvedTarget.type}`,
    );
  } else if (forwardKinds.length && reverseKinds.length) {
    setStatus(
      "Connection added. Both directions are legal; choose direction/type from the inline selector.",
    );
  } else {
    setStatus(
      interactivePicker
        ? "Connection added. Choose relationship type."
        : `Connection added: ${kind}`,
    );
  }
  return true;
}

function createShortcutConnection(source, target) {
  if (state.activeType !== "psm") {
    return false;
  }
  let rule = null;
  try {
    rule = modelingShortcutConnectorRules("psm").find(
      (candidate) =>
        modelingTypeMatchesSafe(candidate.sourceType, source.type) &&
        modelingTypeMatchesSafe(candidate.targetType, target.type),
    );
  } catch {
    rule = null;
  }
  if (!rule) {
    return false;
  }
  const intermediateTypes = Array.isArray(rule.intermediateTypes) ? rule.intermediateTypes : [];
  const edgeKinds = Array.isArray(rule.edgeKinds) ? rule.edgeKinds : [];
  if (!intermediateTypes.length || edgeKinds.length < intermediateTypes.length + 1) {
    return false;
  }
  pushDiagramUndoSnapshot();
  const chain = [source];
  intermediateTypes.forEach((type, index) => {
    const offsetX = 180 + index * 140;
    const offsetY = index % 2 === 0 ? 72 : -72;
    const node = getDefaultNode("psm", type, source.x + offsetX, source.y + offsetY);
    node.label = `${type}-${node.id.slice(-4)}`;
    node.meta.name = node.label;
    node.meta.label = node.label;
    state.diagram.nodes.push(node);
    addNodeToGraphAndActiveView(node);
    chain.push(node);
  });
  const viewNode = createShortcutViewNode(rule, source, target, chain.slice(1));
  chain.push(target);
  for (let index = 0; index < chain.length - 1; index++) {
    const edge = {
      id: genId("e"),
      sourceId: chain[index].id,
      targetId: chain[index + 1].id,
      kind: edgeKinds[index] || "DEPENDS_ON",
    };
    state.diagram.connections.push(edge);
    addConnectionToGraphAndActiveView(edge);
  }
  if (viewNode) {
    const summaryEdge = {
      id: genId("e"),
      sourceId: source.id,
      targetId: viewNode.id,
      kind: "CONTAINS",
    };
    const targetEdge = {
      id: genId("e"),
      sourceId: viewNode.id,
      targetId: target.id,
      kind: "INVOKES",
    };
    state.diagram.connections.push(summaryEdge, targetEdge);
    addConnectionToGraphAndActiveView(summaryEdge);
    addConnectionToGraphAndActiveView(targetEdge);
  }
  syncActiveViewFromVisibleGraph();
  ensureG6Canvas();
  syncCanvasIndexesFromState();
  syncG6FromState({ full: false });
  workbenchSurfaces();
  markModelDirty();
  setStatus(`Created ${rule.label || "PSM shortcut connector"}`);
  return true;
}

function createShortcutViewNode(rule, source, target, intermediates) {
  const viewType = String(rule.viewType || "").trim();
  if (!viewType) {
    return null;
  }
  const node = getDefaultNode(
    "psm",
    viewType,
    Math.round((source.x + target.x) / 2),
    Math.round((source.y + target.y) / 2 - 130),
  );
  node.label = `${rule.label || viewType}`;
  node.meta.name = node.label;
  node.meta.label = node.label;
  node.meta.generated = true;
  node.meta.source = source.id;
  node.meta.target = target.id;
  applyShortcutViewReferences(node, rule, source, target, intermediates);
  state.diagram.nodes.push(node);
  addNodeToGraphAndActiveView(node);
  return node;
}

function applyShortcutViewReferences(node, rule, source, target, intermediates) {
  const bindings = Array.isArray(rule.viewReferences) ? rule.viewReferences : [];
  bindings.forEach((binding) => {
    const feature = String(binding?.feature || "").trim();
    if (!feature) {
      return;
    }
    if (binding.role === "source") {
      node.meta[feature] = source.id;
      return;
    }
    if (binding.role === "target") {
      node.meta[feature] = target.id;
      return;
    }
    const expectedType = String(binding?.type || "").trim();
    const match = intermediates.find((item) => modelingTypeMatchesSafe(expectedType, item.type));
    if (match) {
      node.meta[feature] = match.id;
    } else if (binding.required === false) {
      node.meta[feature] = null;
    }
  });
}

function modelingTypeMatchesSafe(expected, actual) {
  try {
    return modelingTypeMatches(state.activeType, expected, actual);
  } catch {
    return expected === actual;
  }
}

// ── Connect mode ─────────────────────────────────────────────────────────────

export function setConnectMode(enabled) {
  state.connectMode = enabled;
  state.connectSourceId = null;
  if (!enabled) {
    state.preferredConnectionKind = null;
    closeEdgeKindPicker();
  }
  ensureG6Canvas();
  updateG6ConnectionState();
  setStatus(enabled ? "Connect mode enabled - click source then target" : "Connect mode disabled");
}

export function startConnectionFromNode(nodeId, preferredKind = null) {
  const node =
    state.nodesById.get(nodeId) || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
  if (!node) {
    return false;
  }
  state.connectMode = true;
  state.connectSourceId = nodeId;
  state.preferredConnectionKind = preferredKind || null;
  ensureG6Canvas();
  updateG6ConnectionState();
  setStatus(
    preferredKind
      ? `${preferredKind}: select a highlighted legal target`
      : "Select a highlighted legal target",
  );
  return true;
}

// ── Impact highlight (called by renderNodes and impact module) ────────────────

export function highlightImpactedNodes() {
  ensureG6Canvas();
  updateG6ImpactState();
}

export function scrollToNodeAndHighlight(elementId) {
  ensureG6Canvas();
  const node =
    state.nodesById.get(elementId) ||
    state.diagram.nodes.find((candidate) => candidate.id === elementId);
  if (!node) {
    return;
  }
  focusG6Node(elementId);
  const previousImpact = state.impactData;
  state.impactData = {
    ...(state.impactData || {}),
    focalElement: { elementId },
  };
  updateG6ImpactState();
  setTimeout(() => {
    state.impactData = previousImpact;
    updateG6ImpactState();
  }, 2500);
}

export function scrollToConnectionAndHighlight(connectionId) {
  const edge = state.diagram.connections.find((item) => item.id === connectionId);
  if (!edge) {
    return false;
  }
  const source = state.nodesById.get(edge.sourceId);
  const target = state.nodesById.get(edge.targetId);
  if (!source || !target) {
    return false;
  }

  const nodeW = getNodeWidth();
  const nodeH = getNodeHeight();
  const midX = (source.x + nodeW / 2 + target.x + nodeW / 2) / 2;
  const midY = (source.y + nodeH / 2 + target.y + nodeH / 2) / 2;
  ensureG6Canvas();
  state.selectedConnectionId = connectionId;
  focusG6CanvasPoint(midX, midY);
  updateG6Selection();
  updateG6Edge(connectionId);
  setTimeout(() => updateG6Edge(connectionId), 1800);
  return true;
}
