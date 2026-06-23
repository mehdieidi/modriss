import ELK from "elkjs/lib/elk.bundled.js";

const elk = new ELK();

export async function applyElkLayout(diagram, levelConfig = {}) {
  const layoutHint =
    levelConfig.viewDefinitions?.find((view) => view.id)?.layoutHint ||
    levelConfig.canvasPolicy?.defaultLayout ||
    "layered";

  const elkGraph = {
    id: "root",
    layoutOptions: layoutOptionsForHint(layoutHint),
    children: diagram.nodes.map((node) => ({
      id: node.id,
      width: node.width || 228,
      height: node.height || 112,
    })),
    edges: diagram.connections.map((edge) => ({
      id: edge.id,
      sources: [edge.sourceId],
      targets: [edge.targetId],
    })),
  };

  const laidOut = await elk.layout(elkGraph);
  const positions = new Map((laidOut.children || []).map((child) => [child.id, child]));
  for (const node of diagram.nodes) {
    const next = positions.get(node.id);
    if (next) {
      node.x = Number(next.x || 0);
      node.y = Number(next.y || 0);
    }
  }
  return diagram;
}

function layoutOptionsForHint(hint) {
  const base = {
    "elk.algorithm": "layered",
    "elk.direction": "DOWN",
    "elk.spacing.nodeNode": "48",
    "elk.layered.spacing.nodeNodeBetweenLayers": "64",
  };
  if (hint === "CONTAINER" || hint === "CONTAINER_FOCUS") {
    return { ...base, "elk.direction": "RIGHT", "elk.algorithm": "box" };
  }
  if (hint === "PROCESS") {
    return { ...base, "elk.direction": "RIGHT" };
  }
  if (hint === "DASHBOARD") {
    return { ...base, "elk.algorithm": "box", "elk.direction": "DOWN" };
  }
  return base;
}
