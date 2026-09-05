import { state } from "../state.js";
import {
  modelingElementDefinition,
  modelingLevelConfig,
  modelingRelationshipKindLabel,
  modelingRelationshipPresentation,
} from "../modeling-config-data.js";
import {
  measureIconNodeSize,
  resolveIconSource,
  routePointOnIconAnchor,
} from "./icon-node-layout.js";
import {
  canvasBackgroundColor,
  cssVar,
  edgeStyleForKind,
  G6_BASE_EDGE_TYPE,
  G6_BASE_NODE_TYPE,
  nodeAccent,
  nodeSizeForDiagram,
  stickyColor,
} from "./g6-style.js";

const elementDefinitionCache = new Map();

function cachedElementDefinition(typeKey, type) {
  const key = `${typeKey || ""}:${type || ""}`;
  if (elementDefinitionCache.has(key)) {
    return elementDefinitionCache.get(key);
  }
  let definition = null;
  try {
    definition = modelingElementDefinition(typeKey, type);
  } catch {
    definition = null;
  }
  elementDefinitionCache.set(key, definition);
  return definition;
}

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
  const lineFields = Array.isArray(definition.notation.detailFields)
    ? definition.notation.detailFields
    : Array.isArray(definition.notation.lineFields)
      ? definition.notation.lineFields
      : [];
  return {
    tag: definition.notation.tag || "element",
    line: (meta) => {
      for (const field of lineFields) {
        const value = meta?.[field];
        const text = Array.isArray(value)
          ? value.map(refLabel).filter(Boolean).slice(0, 3).join(", ")
          : compactRefCount(value);
        if (String(text || "").trim()) {
          return text;
        }
      }
      return "";
    },
  };
}

export function nodeNotation(typeKey, node) {
  const definition = cachedElementDefinition(typeKey, node.type);
  return notationFromDefinition(typeKey, node, definition);
}

function nodeToken(typeKey, node, notation) {
  const token = String(notation?.tag || "").trim();
  if (token) {
    return token;
  }
  return String(node?.type || "element")
    .replaceAll(/([a-z])([A-Z])/g, "$1 $2")
    .toLowerCase();
}

function humanizeType(value) {
  return String(value || "Element")
    .replaceAll("_", " ")
    .replaceAll(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\s+/g, " ")
    .trim();
}

export function nodeTypeLabel(definition, nodeType) {
  return definition?.displayName || definition?.label || humanizeType(nodeType);
}

function nodeDetailLine(node, notation, definition) {
  const meta = node?.meta || {};
  const candidates = [
    notation?.line?.(meta),
    meta.lifecycleStatus,
    meta.lifecycle,
    meta.status,
    meta.reviewStatus,
    definition?.category,
  ]
    .map((value) => String(value || "").trim())
    .filter(Boolean);
  return candidates.find((value) => value !== node?.label) || "";
}

function nodeIconSource(definition) {
  const ui = definition?.ui && typeof definition.ui === "object" ? definition.ui : {};
  const icon = String(ui.icon || definition?.icon || "").trim();
  return resolveIconSource(icon);
}

function badgeText(value) {
  return String(value || "")
    .trim()
    .replaceAll("_", " ");
}

function addBadge(badges, text) {
  const value = badgeText(text);
  if (!value || badges.includes(value) || badges.length >= 4) {
    return;
  }
  badges.push(value);
}

function nodeBadges(typeKey, node) {
  const meta = node?.meta || {};
  const badges = [];
  const rules = modelingLevelConfig(typeKey).badgeRules || [];
  rules.forEach((rule) => {
    const value = meta?.[rule?.field];
    const matches = Object.prototype.hasOwnProperty.call(rule || {}, "when")
      ? value === rule.when
      : rule?.useValue
        ? value !== undefined && value !== null && String(value).trim() !== ""
        : Boolean(value);
    if (matches) {
      addBadge(badges, rule.useValue ? value : rule.label || rule.field);
    }
  });
  return badges;
}

export function edgeLabel(edge, typeKey = state.activeType) {
  if (edge?.label) {
    return edge.label;
  }
  const relationship = state.graph?.relationshipsById?.get(edge?.id) || edge || {};
  const labelFields = modelingLevelConfig(typeKey).relationshipLabelFields || [];
  for (const field of labelFields) {
    const text = refLabel(relationship?.[field]);
    if (String(text || "").trim()) {
      return String(text);
    }
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
  return modelingRelationshipPresentation(typeKey, edge);
}

function routeEndpoint(node, anchor, typeKey, fallbackSide = "right", detailLevel = "normal") {
  if (!node) {
    return null;
  }
  const size = renderedNodeSize(node, typeKey, detailLevel);
  const side = anchor?.side === "left" || anchor?.side === "right" ? anchor.side : fallbackSide;
  const requestedOffsetY = Number(anchor?.offsetY);
  const offsetY = Number.isFinite(requestedOffsetY)
    ? Math.max(8, Math.min(size.height - 8, requestedOffsetY))
    : undefined;
  return routePointOnIconAnchor(
    Number(node.x || 0),
    Number(node.y || 0),
    size.width,
    size.height,
    side,
    offsetY,
    detailLevel === "low",
    node.label || node.id || "",
  );
}

function renderedNodeSize(node, typeKey, detailLevel) {
  const configured = nodeSizeForDiagram(typeKey, node);
  if (Number.isFinite(Number(node?.width)) && Number.isFinite(Number(node?.height))) {
    return configured;
  }
  const measured = measureIconNodeSize(node.label || node.id || "", {
    width: configured.width,
    low: detailLevel === "low",
  });
  return { width: configured.width, height: measured.height };
}

export function mapNodeToG6(
  node,
  {
    typeKey = state.activeType,
    detailLevel = "normal",
    isContainer = () => false,
    contextNameFromNode = () => "",
    viewProfile = "",
  } = {},
) {
  const size = renderedNodeSize(node, typeKey, detailLevel);
  const definition = cachedElementDefinition(typeKey, node.type);
  const notation = notationFromDefinition(typeKey, node, definition);
  const accent = nodeAccent(node, definition);
  const sticky = stickyColor(node, definition);
  const kindText = nodeTypeLabel(definition, node.type);
  const detailText = nodeDetailLine(node, notation, definition);
  const iconSrc = nodeIconSource(definition);
  const token = nodeToken(typeKey, node, notation);
  const badges = nodeBadges(typeKey, node);
  const container = Boolean(isContainer(node));
  const contextName = contextNameFromNode(node);
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
      iconSrc,
      kindText,
      detailText,
      tokenText: token,
      badges,
      showHandles: Boolean(node.showHandles),
      accent,
      sticky,
      viewProfile,
      contextName,
      container,
      detailLevel,
    },
    style: {
      x: Math.round(Number(node.x || 0) + size.width / 2),
      y: Math.round(Number(node.y || 0) + size.height / 2),
      size: [size.width, size.height],
      width: size.width,
      height: size.height,
      diagramType: typeKey,
      elementId: node.id,
      nodeType: node.type,
      labelText: node.label || node.id,
      labelFill: "rgba(227, 232, 242, 0.96)",
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
      iconSrc,
      badges,
      showHandles: Boolean(node.showHandles),
      accent,
      sticky,
      fill: "transparent",
      stroke: "transparent",
      lineWidth: 0,
      radius: 8,
      shadowColor: "transparent",
      shadowBlur: 0,
      detailLevel,
      isContainer: container,
    },
  };
}

export function mapEdgeToG6(
  edge,
  {
    typeKey = state.activeType,
    detailLevel = "normal",
    showLabels = true,
    selected = false,
    hovered = false,
    nodesById = state.nodesById,
  } = {},
) {
  const presentation = edgePresentation(edge, typeKey);
  const style = edgeStyleForKind(edge.kind, presentation);
  const label = edgeLabel(edge, typeKey);
  const sourceNode = nodesById?.get?.(edge.sourceId);
  const targetNode = nodesById?.get?.(edge.targetId);
  const routeStart = routeEndpoint(
    sourceNode,
    edge.sourceAnchor,
    typeKey,
    "right",
    detailLevel,
  );
  const routeEnd = routeEndpoint(targetNode, edge.targetAnchor, typeKey, "left", detailLevel);
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
      targetAnchor: edge.targetAnchor || null,
      routeStart,
      routeEnd,
    },
    style: {
      stroke: style.stroke,
      lineWidth: style.lineWidth,
      lineDash: style.lineDash,
      opacity: style.opacity,
      endArrow: presentation.markerEnd !== "",
      startArrow: Boolean(presentation.markerStart),
      router: {
        type: "orth",
      },
      pinPoints: Array.isArray(edge.pinPoints) ? edge.pinPoints : [],
      routeStart,
      routeEnd,
      // Pin handles are editing affordances. Rendering them for every labeled
      // edge multiplies canvas objects in dense regions, despite only being
      // actionable on the selected or hovered relationship.
      showPins: selected || hovered,
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
      labelBackgroundShadowColor:
        selected || hovered
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
      hovered,
    },
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
  const nodesById = new Map();
  const g6Nodes = [];
  nodes.forEach((node) => {
    if (!visibleNode(node)) {
      return;
    }
    visibleNodeIds.add(node.id);
    nodesById.set(node.id, node);
    g6Nodes.push(
      mapNodeToG6(node, {
        typeKey,
        detailLevel,
        ...nodeOptions,
      }),
    );
  });
  const g6Edges = edges
    .filter(
      (edge) =>
        !edge?.bundle &&
        String(edge?.kind || "").toUpperCase() !== "EDGE_BUNDLE" &&
        visibleNodeIds.has(edge.sourceId) &&
        visibleNodeIds.has(edge.targetId),
    )
    .map((edge) =>
      mapEdgeToG6(edge, {
        typeKey,
        showLabels,
        selected: selectedEdgeId === edge.id,
        hovered: hoveredEdgeId === edge.id,
        nodesById,
      }),
    );
  return { nodes: g6Nodes, edges: g6Edges };
}
