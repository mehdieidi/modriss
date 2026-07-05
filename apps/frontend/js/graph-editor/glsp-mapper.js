import { state } from "../state.js";
import { mapEdgeToG6, mapNodeToG6 } from "./g6-mapper.js";
import { finiteNumber } from "./glsp-shapes.js";

export function mapNodeToGlsp(node, options = {}) {
  const g6Node = mapNodeToG6(node, options);
  return {
    id: node.id,
    type: node.type,
    x: Number(node.x || 0),
    y: Number(node.y || 0),
    width: finiteNumber(g6Node.style.width, 120),
    height: finiteNumber(g6Node.style.height, 118),
    label: g6Node.data.label || node.label || node.id,
    lines: g6Node.data.detailText || "",
    g6: {
      ...g6Node.data,
      accent: g6Node.data.accent,
      sticky: g6Node.data.sticky,
      kindText: g6Node.data.kindText,
      tokenText: g6Node.data.tokenText,
      notationGeometry: g6Node.data.notationGeometry,
      notationShape: g6Node.data.notationShape,
      iconSrc: g6Node.data.iconSrc,
      badges: g6Node.data.badges,
      container: g6Node.data.container,
      detailText: g6Node.data.detailText,
      diagramType: g6Node.data.diagramType,
      elementId: g6Node.style.elementId || node.id,
      detailLevel: g6Node.data.detailLevel,
    },
  };
}

export function mapEdgeToGlsp(edge, options = {}) {
  const g6Edge = mapEdgeToG6(edge, {
    ...options,
    nodesById: state.nodesById,
    selected: options.selectedEdgeId === edge.id,
    hovered: options.hoveredEdgeId === edge.id,
  });
  const style = g6Edge.style || {};
  return {
    id: edge.id,
    sourceId: edge.sourceId,
    targetId: edge.targetId,
    kind: edge.kind,
    shortcut: Boolean(edge.shortcut || edge.data?.shortcut),
    args: {
      stroke: style.stroke,
      lineDash: style.lineDash,
      showLabel: options.showLabels !== false,
      selected: options.selectedEdgeId === edge.id,
      label: g6Edge.data?.label,
    },
  };
}

export function mapDiagramToGlsp(
  { nodes = [], connections = [], overlays = {} } = {},
  options = {},
) {
  return {
    nodes: nodes.map((node) => mapNodeToGlsp(node, options)),
    connections: connections.map((edge) => mapEdgeToGlsp(edge, options)),
    overlays,
  };
}
