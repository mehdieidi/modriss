import { expandShortcutEdges } from "./shortcut-edges.mjs";
import { applyComplexityPolicy } from "./complexity.mjs";
import { buildOverlayPayload } from "./overlays.mjs";

export function materializeDiagram(model, viewId) {
  const graph = model.graph || { nodes: [], edges: [] };
  const views = Array.isArray(model.views) ? model.views : [];
  const activeView = viewId ? views.find((view) => view.id === viewId) : views[0];
  const nodeIds = new Set(
    activeView?.nodeIds?.map(String) || (graph.nodes || []).map((node) => String(node.id)),
  );
  const edgeIds = new Set(
    activeView?.edgeIds?.map(String) || (graph.edges || []).map((edge) => String(edge.id)),
  );

  const nodes = (graph.nodes || [])
    .filter((node) => nodeIds.has(String(node.id)))
    .map((node) => mapNode(node));

  let connections = (graph.edges || [])
    .filter((edge) => edgeIds.has(String(edge.id)))
    .map((edge) => mapConnection(edge));

  if (!nodes.length && Array.isArray(model.diagram?.elements)) {
    for (const element of model.diagram.elements) {
      const layout = element.layout || {};
      nodes.push(
        mapNode({
          id: element.id,
          type: element.type,
          x: layout.x,
          y: layout.y,
          width: layout.width,
          height: layout.height,
          data: element,
        }),
      );
    }
  }

  return { nodes, connections };
}

function mapNode(node) {
  return {
    id: String(node.id),
    type: String(node.type || node.elementType || "Unknown"),
    x: Number(node.x || 0),
    y: Number(node.y || 0),
    width: Number(node.width || 228),
    height: Number(node.height || 112),
    data: node.data || node,
  };
}

function mapConnection(edge) {
  return {
    id: String(edge.id),
    sourceId: String(edge.sourceId || edge.source),
    targetId: String(edge.targetId || edge.target),
    kind: String(edge.kind || edge.type || "DEPENDS_ON"),
    data: edge.data || edge,
    shortcut: Boolean(edge.shortcut || edge.data?.shortcut),
  };
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

    children.push({
      type: "node",
      id: node.id,
      position: { x: node.x, y: node.y },
      size: { width: node.width || 228, height: node.height || 112 },
      args: {
        elementType: node.type,
        color: def.color || mapping.color || "#475569",
        icon: def.icon || mapping.icon || "category",
        tag: notation.tag || card.tag || node.type.slice(0, 4).toUpperCase(),
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
      ],
    });
  }

  for (const edge of connections) {
    const presentation = edgePresentation(visualRules, edge.kind);
    children.push({
      type: "edge",
      id: edge.id,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
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
  for (const child of graph?.children || []) {
    if (child.type === "node") {
      nodes.push({
        id: child.id,
        type: child.args?.elementType || "Unknown",
        x: child.position?.x || 0,
        y: child.position?.y || 0,
        w: child.size?.width || 228,
        h: child.size?.height || 112,
        width: child.size?.width || 228,
        height: child.size?.height || 112,
        label: child.children?.[0]?.text || child.id,
        data: child.args || {},
      });
    }
    if (child.type === "edge") {
      connections.push({
        id: child.id,
        sourceId: child.sourceId,
        targetId: child.targetId,
        kind: child.args?.kind,
        shortcut: child.args?.shortcut,
      });
    }
  }
  return { nodes, connections, overlays: graph.overlays || {} };
}
