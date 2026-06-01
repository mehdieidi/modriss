import {state} from '../state.js';
import {
  modelingElementDefinition,
  modelingRelationshipKindLabel
} from '../modeling-config-data.js';
import {
  canvasBackgroundColor,
  cssVar,
  edgeStyleForKind,
  G6_BASE_EDGE_TYPE,
  G6_BASE_NODE_TYPE,
  nodeAccent,
  nodeSizeForDiagram,
  stickyColor
} from './g6-style.js';

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

function compactRefCount(value) {
  if (Array.isArray(value)) {
    return value.length ? `${value.length}` : "";
  }
  if (value && typeof value === "object") {
    return refLabel(value);
  }
  return value ? String(value) : "";
}

function notationFromDefinition(typeKey, node, definition) {
  if (!definition?.notation) {
    return null;
  }
  const lineFields = Array.isArray(definition.notation.lineFields)
      ? definition.notation.lineFields : [];
  return {
    tag: definition.notation.tag || "element",
    line: (meta) => {
      for (const field of lineFields) {
        const value = meta?.[field];
        const text = Array.isArray(value) ? value.map(refLabel).filter(Boolean)
        .slice(0, 3).join(", ") : compactRefCount(value);
        if (String(text || "").trim()) {
          return text;
        }
      }
      return "";
    }
  };
}

export function nodeNotation(typeKey, node) {
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, node.type);
  } catch {
    definition = null;
  }
  return notationFromDefinition(typeKey, node, definition);
}

function nodeToken(typeKey, node, notation) {
  const token = String(notation?.tag || "").trim();
  if (token) {
    return token;
  }
  return String(node?.type || "element").replaceAll(/([a-z])([A-Z])/g,
      "$1 $2").toLowerCase();
}

function humanizeType(value) {
  return String(value || "Element")
  .replaceAll("_", " ")
  .replaceAll(/([a-z])([A-Z])/g, "$1 $2")
  .replace(/\s+/g, " ")
  .trim();
}

function nodeDetailLine(node, notation, definition) {
  const meta = node?.meta || {};
  const candidates = [
    notation?.line?.(meta),
    meta.lifecycleStatus,
    meta.lifecycle,
    meta.status,
    meta.reviewStatus,
    definition?.category
  ].map((value) => String(value || "").trim()).filter(Boolean);
  return candidates.find((value) => value !== node?.label) || "";
}

function badgeText(value) {
  return String(value || "").trim().replaceAll("_", " ");
}

function addBadge(badges, text) {
  const value = badgeText(text);
  if (!value || badges.includes(value) || badges.length >= 4) {
    return;
  }
  badges.push(value);
}

function nodeBadges(typeKey, node, definition) {
  const meta = node?.meta || {};
  const badges = [];
  if (meta.generated || meta.generatedFrom || meta.transformationRuleId) {
    addBadge(badges, "generated");
  }
  if (meta.manual || meta.manualReviewRequired || meta.requiresManualReview
      || meta.reviewStatus === "NEEDS_REVIEW") {
    addBadge(badges, "review");
  }
  if (meta.productionBlocking || meta.blocksTransformation
      || meta.blocking === true) {
    addBadge(badges, "blocking");
  }
  if (meta.severity) {
    addBadge(badges, meta.severity);
  }
  if (meta.lifecycle || meta.lifecycleStatus || meta.status) {
    addBadge(badges, meta.lifecycle || meta.lifecycleStatus || meta.status);
  }
  if (meta.required || meta.productionRequired || meta.authRequired
      || meta.authenticationRequired || meta.authorizationRequired) {
    addBadge(badges, "required");
  }
  if (meta.encryptionRequired || meta.encryption || meta.sseEnabled) {
    addBadge(badges, "encrypted");
  }
  if (meta.rotationRequired) {
    addBadge(badges, "rotation");
  }
  if (meta.tracingEnabled || meta.xrayTracingEnabled) {
    addBadge(badges, "tracing");
  }
  if (meta.retentionDays || meta.retentionPolicy) {
    addBadge(badges, "retention");
  }
  if (meta.imported || meta.resourceImport || meta.importMode) {
    addBadge(badges, "import");
  }
  if (meta.deletionPolicy) {
    addBadge(badges, meta.deletionPolicy);
  }
  if (typeKey === "psm" && definition?.category) {
    addBadge(badges, definition.category);
  }
  return badges;
}

export function edgeLabel(edge, typeKey = state.activeType) {
  if (edge?.label) {
    return edge.label;
  }
  const key = String(edge?.kind || "").toUpperCase();
  let configured = "";
  try {
    configured = modelingRelationshipKindLabel(typeKey, key);
  } catch {
    configured = "";
  }
  if (edge?.bundle) {
    return edge.label || "Bundled relations";
  }
  return configured || key.toLowerCase().replaceAll("_", " ");
}

export function edgePresentation(edge, typeKey = state.activeType) {
  const kind = String(edge?.kind || "").toUpperCase();
  const relationship = state.graph?.relationshipsById?.get(edge?.id) || edge
      || {};
  const presentation = {className: "", markerStart: "", markerEnd: "arrow"};
  if (typeKey === "pim") {
    if (kind === "TRACE" || relationship.eClass === "TraceLink") {
      presentation.className = " edge-trace-link";
    } else if (kind === "TRANSITION" || relationship.eClass
        === "WorkflowTransition") {
      presentation.className = " edge-process-transition";
    } else if (kind === "PERMISSION" || kind === "AUTHORIZED_BY") {
      presentation.className = " edge-conflict";
    } else if (["EVENT_FLOW", "MESSAGE_FLOW", "PUB_SUB", "PUBLISHES",
      "SUBSCRIBES_TO"].includes(kind)) {
      presentation.className = " edge-pim-event";
    } else if (["READS", "WRITES", "READ_WRITE", "DATA_ACCESS", "APPEND",
      "DELETE"].includes(kind)) {
      presentation.className = " edge-pim-data";
    } else if (["EXTERNAL_CALL", "CALLS"].includes(kind)) {
      presentation.className = " edge-domain-dependency";
    } else if (["DEPLOYS", "DEPLOYS_TO", "OWNS", "CONTAINS"].includes(kind)) {
      presentation.className = " edge-domain-ownership";
    }
    return presentation;
  }
  if (typeKey === "psm") {
    if (["CONTAINS", "DEPLOYS", "DEPLOYS_TO"].includes(kind)) {
      presentation.className = " edge-domain-ownership";
    } else if (["EVENT_FLOW", "MESSAGE_FLOW"].includes(kind)) {
      presentation.className = " edge-pim-event";
    } else if (["READS", "WRITES", "DATA_ACCESS"].includes(kind)) {
      presentation.className = " edge-pim-data";
    } else if (["USES_ROLE", "PERMISSION", "AUTHORIZED_BY",
      "USES_SECRET"].includes(kind)) {
      presentation.className = " edge-conflict";
    } else if (["OBSERVES", "WRITES_LOGS_TO"].includes(kind)) {
      presentation.className = " edge-trace-link";
    } else if (kind === "TRANSITION") {
      presentation.className = " edge-process-transition";
    }
    return presentation;
  }
  const relationshipType = String(relationship.relationshipType
      || "").toUpperCase();
  if (kind === "DOMAIN_RELATIONSHIP") {
    presentation.markerEnd = "";
    presentation.className = " edge-domain-relationship";
    if (relationshipType === "COMPOSITION") {
      presentation.markerStart = "diamond-filled";
      presentation.className += " edge-domain-composition";
    } else if (relationshipType === "AGGREGATION") {
      presentation.markerStart = "diamond-hollow";
      presentation.className += " edge-domain-aggregation";
    } else if (relationshipType === "GENERALIZATION") {
      presentation.markerEnd = "triangle-hollow";
      presentation.className += " edge-domain-generalization";
    } else if (relationshipType === "DEPENDENCY") {
      presentation.markerEnd = "arrow";
      presentation.className += " edge-domain-dependency";
    } else if (relationshipType === "OWNERSHIP") {
      presentation.markerEnd = "arrow";
      presentation.className += " edge-domain-ownership";
    }
  } else if (kind === "TRACE" || relationship.eClass === "TraceLink") {
    presentation.className = " edge-trace-link";
  } else if (kind === "CONFLICTS_WITH" || relationship.linkType
      === "CONFLICTS_WITH") {
    presentation.className = " edge-conflict";
    presentation.markerEnd = "conflict-cross";
  } else if (kind === "TRANSITION") {
    presentation.className = " edge-process-transition";
  } else if (relationship.eClass === "CapabilityDependency") {
    presentation.className = relationship.criticalPath
        ? " edge-critical-dependency" : " edge-capability-dependency";
  }
  return presentation;
}

export function mapNodeToG6(node, {
  typeKey = state.activeType,
  detailLevel = "normal",
  isContainer = () => false,
  contextNameFromNode = () => "",
  viewProfile = ""
} = {}) {
  const size = nodeSizeForDiagram(typeKey);
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, node.type);
  } catch {
    definition = null;
  }
  const notation = nodeNotation(typeKey, node);
  const accent = nodeAccent(node, definition);
  const sticky = typeKey === "cim" ? stickyColor(node, notation) : "";
  const kindText = humanizeType(node.type);
  const detailText = nodeDetailLine(node, notation, definition);
  const token = nodeToken(typeKey, node, notation);
  const badges = nodeBadges(typeKey, node, definition);
  return {
    id: node.id,
    type: G6_BASE_NODE_TYPE,
    data: {
      source: node,
      nodeType: node.type,
      label: node.label || node.id,
      meta: node.meta || {},
      diagramType: typeKey,
      notation: notation?.tag || "",
      kindText,
      detailText,
      tokenText: token,
      badges,
      showHandles: Boolean(node.showHandles),
      accent,
      sticky,
      viewProfile,
      contextName: contextNameFromNode(node),
      container: isContainer(node),
      detailLevel
    },
    style: {
      x: Math.round(Number(node.x || 0) + size.width / 2),
      y: Math.round(Number(node.y || 0) + size.height / 2),
      size: [size.width, size.height],
      width: size.width,
      height: size.height,
      diagramType: typeKey,
      nodeType: node.type,
      labelText: node.label || node.id,
      labelFill: typeKey === "cim" ? "rgba(24, 20, 14, 0.92)"
          : "rgba(227, 232, 242, 0.96)",
      labelFontSize: 12,
      labelFontWeight: 700,
      labelPlacement: "center",
      labelWordWrap: true,
      labelMaxWidth: Math.max(80, size.width - 24),
      typeText: token,
      kindText,
      fullTypeText: node.type,
      notationText: detailText,
      notation: notation?.tag || "",
      badges,
      showHandles: Boolean(node.showHandles),
      accent,
      sticky,
      fill: typeKey === "cim" ? sticky : "rgba(19, 25, 35, 0.98)",
      stroke: typeKey === "cim" ? "rgba(21, 28, 40, 0.24)"
          : "rgba(61, 73, 95, 0.92)",
      lineWidth: 1,
      radius: 2,
      shadowColor: typeKey === "cim" ? "rgba(12, 18, 28, 0.28)"
          : "rgba(6, 11, 20, 0.32)",
      shadowBlur: typeKey === "cim" ? 10 : 8,
      detailLevel,
      isContainer: isContainer(node)
    }
  };
}

export function mapEdgeToG6(edge, {
  typeKey = state.activeType,
  showLabels = true,
  selected = false,
  hovered = false
} = {}) {
  const presentation = edgePresentation(edge, typeKey);
  const style = edgeStyleForKind(edge.kind, presentation);
  const label = edgeLabel(edge, typeKey);
  return {
    id: edge.id,
    type: G6_BASE_EDGE_TYPE,
    source: edge.sourceId,
    target: edge.targetId,
    data: {
      source: edge,
      kind: edge.kind,
      label,
      presentationClass: presentation.className,
      markerStart: presentation.markerStart,
      markerEnd: presentation.markerEnd,
      pinPoints: Array.isArray(edge.pinPoints) ? edge.pinPoints : [],
      sourceAnchor: edge.sourceAnchor || null,
      targetAnchor: edge.targetAnchor || null
    },
    style: {
      stroke: style.stroke,
      lineWidth: style.lineWidth,
      lineDash: style.lineDash,
      opacity: style.opacity,
      endArrow: presentation.markerEnd !== "",
      startArrow: Boolean(presentation.markerStart),
      router: {
        type: "orth"
      },
      pinPoints: Array.isArray(edge.pinPoints) ? edge.pinPoints : [],
      showPins: showLabels || selected || hovered,
      labelText: showLabels || selected || hovered ? label : "",
      labelPlacement: "center",
      labelOffsetY: -14,
      labelTextAlign: "center",
      labelTextBaseline: "middle",
      labelFontFamily: cssVar("--font-ui", "sans-serif"),
      labelFontSize: selected || hovered ? 12 : 11,
      labelFontWeight: selected || hovered ? 800 : 750,
      labelFill: selected
          ? cssVar("--accent-light", "#7bd0ff")
          : cssVar("--text-strong", "#e7ecf5"),
      labelBackground: Boolean(showLabels || selected || hovered),
      labelBackgroundFill: cssVar("--surface-high", canvasBackgroundColor()),
      labelBackgroundFillOpacity: 0.96,
      labelBackgroundStroke: selected
          ? cssVar("--accent-select", "#5ecbff")
          : cssVar("--border", "#344055"),
      labelBackgroundLineWidth: selected ? 1.4 : 1,
      labelBackgroundRadius: 6,
      labelBackgroundShadowBlur: selected || hovered ? 10 : 5,
      labelBackgroundShadowColor: selected || hovered
          ? cssVar("--accent-glow", "rgba(0, 166, 224, 0.28)")
          : "rgba(8, 14, 24, 0.24)",
      labelPadding: [4, 8],
      labelMaxWidth: 160,
      labelMaxLines: 1,
      labelTextOverflow: "ellipsis",
      labelZIndex: 2,
      labelBackgroundZIndex: 1,
      edgeKind: edge.kind,
      selected,
      hovered
    }
  };
}

export function mapDiagramToG6({
  nodes = state.diagram.nodes,
  edges = state.diagram.connections,
  typeKey = state.activeType,
  visibleNode = () => true,
  detailLevel = "normal",
  showLabels = true,
  selectedEdgeId = state.selectedConnectionId,
  hoveredEdgeId = "",
  ...nodeOptions
} = {}) {
  const visibleNodeIds = new Set();
  const g6Nodes = [];
  nodes.forEach((node) => {
    if (!visibleNode(node)) {
      return;
    }
    visibleNodeIds.add(node.id);
    g6Nodes.push(mapNodeToG6(node, {
      typeKey,
      detailLevel,
      ...nodeOptions
    }));
  });
  const g6Edges = edges.filter((edge) =>
      visibleNodeIds.has(edge.sourceId) && visibleNodeIds.has(edge.targetId))
  .map((edge) => mapEdgeToG6(edge, {
    typeKey,
    showLabels,
    selected: selectedEdgeId === edge.id,
    hovered: hoveredEdgeId === edge.id
  }));
  return {nodes: g6Nodes, edges: g6Edges};
}
