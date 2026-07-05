import { expandShortcutEdges } from "./shortcut-edges.mjs";
import { applyComplexityPolicy } from "./complexity.mjs";
import { buildOverlayPayload } from "./overlays.mjs";
import { materializeDiagramFromModel } from "./model-materializer.mjs";

function humanizeType(value) {
  return String(value || "Element")
    .replaceAll("_", " ")
    .replaceAll(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\s+/g, " ")
    .trim();
}

function resolveIconSource(icon) {
  const normalized = String(icon || "").trim();
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("/") || normalized.startsWith(".") || normalized.endsWith(".svg")) {
    return normalized;
  }
  if (/^[a-z0-9_-]+$/i.test(normalized)) {
    return `/assets/icons/${normalized}.svg`;
  }
  return "";
}

export function materializeDiagram(model, viewId) {
  return materializeDiagramFromModel(model, viewId);
}

function nodePorts(nodeId, width, height) {
  const w = Number(width) || 120;
  const iconSize = 72;
  const iconLeft = (w - iconSize) / 2;
  const iconTop = 4;
  const gap = 2;
  const left = iconLeft - gap;
  const right = iconLeft + iconSize + gap;
  const top = iconTop - gap;
  const bottom = iconTop + iconSize + gap;
  const centerX = w / 2;
  const centerY = iconTop + iconSize / 2;
  const half = 4;
  return [
    {
      type: "port",
      id: `${nodeId}-port-n`,
      position: { x: centerX - half, y: top - half },
      size: { width: half * 2, height: half * 2 },
      args: { placement: "north" },
    },
    {
      type: "port",
      id: `${nodeId}-port-s`,
      position: { x: centerX - half, y: bottom - half },
      size: { width: half * 2, height: half * 2 },
      args: { placement: "south" },
    },
    {
      type: "port",
      id: `${nodeId}-port-e`,
      position: { x: right - half, y: centerY - half },
      size: { width: half * 2, height: half * 2 },
      args: { placement: "east" },
    },
    {
      type: "port",
      id: `${nodeId}-port-w`,
      position: { x: left - half, y: centerY - half },
      size: { width: half * 2, height: half * 2 },
      args: { placement: "west" },
    },
  ];
}

export function toSprottyGraph(diagram, levelConfig = {}, options = {}) {
  const elements = levelConfig.elements || [];
  const mappings = levelConfig.elementMappings || [];
  const byType = new Map(elements.map((item) => [item.type, item]));
  const mappingByType = new Map(
    mappings.map((item) => [String(item.match?.eClass || ""), item]),
  );
  const primitives = {
    ...(levelConfig.cvsPrimitives || {}),
    ...(levelConfig.notationPrimitives || {}),
  };
  const visualRules = levelConfig.relationshipVisualRules || [];
  const badgeRules = levelConfig.badgeRules || [];

  const connections = expandShortcutEdges(diagram, levelConfig);
  const children = [];

  for (const node of diagram.nodes) {
    const def = byType.get(node.type) || {};
    const mapping = mappingByType.get(node.type) || {};
    const notation = def.notation || {};
    const card = mapping.card || {};
    const primitiveKey = mapping.primitive || notation.shape || "concept-card";
    const primitive = primitives[primitiveKey] || {};
    const lineFields = card.lineFields || notation.lineFields || [];
    const lines = lineFields
      .map((field) => node.data?.[field])
      .filter((value) => value !== undefined && value !== null && String(value).trim())
      .map((value) => String(value));

    const nodeWidth = node.width || 120;
    const nodeHeight = node.height || 118;

    children.push({
      type: "node",
      id: node.id,
      position: { x: node.x, y: node.y },
      size: { width: nodeWidth, height: nodeHeight },
      args: {
        elementType: node.type,
        color: def.color || mapping.color || "#475569",
        icon: def.icon || mapping.icon || "category",
        iconSrc: resolveIconSource(def.icon || mapping.icon || "category"),
        tag: notation.tag || card.tag || node.type.slice(0, 4).toUpperCase(),
        kindText:
          def.displayName ||
          def.label ||
          mapping.displayName ||
          mapping.label ||
          humanizeType(node.type),
        typeText: notation.tag || card.tag || "",
        category: mapping.category || def.category || "",
        detailText: lines.join(" · "),
        primitive: primitiveKey,
        geometry: primitive.geometry || primitive.sprottyShape || "rectangle",
        sprottyShape: primitive.sprottyShape || primitive.geometry || "rectangle",
        visualRole: mapping.visualRole || def.visualRole || "node",
        lineFields: lines,
        badges: computeBadges(node.data || {}, badgeRules),
        selected: options.selection?.nodeId === node.id,
        hovered: options.hoveredNodeId === node.id,
      },
      children: [
        { type: "label", id: `${node.id}-label`, text: String(node.data?.name || node.id) },
        { type: "label", id: `${node.id}-lines`, text: lines.join(" · ") },
        ...nodePorts(node.id, nodeWidth, nodeHeight),
      ],
    });
  }

  for (const edge of connections) {
    const presentation = edgePresentation(visualRules, edge.kind);
    const sourcePort = `${edge.sourceId}-port-s`;
    const targetPort = `${edge.targetId}-port-n`;
    children.push({
      type: "edge",
      id: edge.id,
      sourceId: sourcePort,
      targetId: targetPort,
      args: {
        kind: edge.kind || "DEPENDS_ON",
        shortcut: Boolean(edge.shortcut),
        stroke: presentation.stroke,
        lineDash: presentation.lineDash,
        className: presentation.className,
        selected: options.selection?.edgeId === edge.id,
        hovered: options.hoveredEdgeId === edge.id,
      },
      children: [{ type: "label", id: `${edge.id}-label`, text: edge.kind || "" }],
    });
  }

  let graph = { type: "graph", id: "root", children };
  graph = applyComplexityPolicy(graph, levelConfig, options);
  graph.overlays = buildOverlayPayload({
    levelConfig,
    diagram: { ...diagram, connections },
    options,
  });
  return graph;
}

function computeBadges(data, badgeRules) {
  const badges = [];
  for (const rule of badgeRules) {
    const field = rule.field;
    const value = data?.[field];
    if (rule.when !== undefined && value !== rule.when) {
      continue;
    }
    if (rule.useValue && value !== undefined && value !== null && String(value).trim()) {
      badges.push(String(value));
    } else if (rule.label) {
      badges.push(String(rule.label));
    }
  }
  return badges.slice(0, 3);
}

function edgePresentation(visualRules, kind) {
  const fallback = { stroke: "#64748b", lineDash: null, className: "edge-default" };
  for (const rule of visualRules) {
    const kinds = rule.matchKinds || [];
    if (kinds.includes(kind)) {
      return {
        stroke: rule.stroke || fallback.stroke,
        lineDash: rule.lineDash || null,
        className: rule.className || `edge-${String(kind).toLowerCase()}`,
      };
    }
  }
  if (kind === "TRACE") {
    return { stroke: "#64748b", lineDash: [4, 4], className: "edge-trace" };
  }
  return fallback;
}

export function graphToDiagram(graph) {
  const nodes = [];
  const connections = [];
  const portToNode = new Map();
  for (const child of graph?.children || []) {
    if (child.type === "node") {
      for (const port of child.children || []) {
        if (port.type === "port" && port.id) {
          portToNode.set(port.id, child.id);
        }
      }
      nodes.push({
        id: child.id,
        type: child.args?.elementType || "Unknown",
        x: child.position?.x || 0,
        y: child.position?.y || 0,
        w: child.size?.width || 228,
        h: child.size?.height || 112,
        width: child.size?.width || 228,
        height: child.size?.height || 112,
        label: child.children?.find((item) => item.type === "label")?.text || child.id,
        data: child.args || {},
      });
    }
    if (child.type === "edge") {
      const sourceId = portToNode.get(child.sourceId) || child.sourceId;
      const targetId = portToNode.get(child.targetId) || child.targetId;
      connections.push({
        id: child.id,
        sourceId,
        targetId,
        kind: child.args?.kind,
        shortcut: child.args?.shortcut,
      });
    }
  }
  return { nodes, connections, overlays: graph.overlays || {} };
}
