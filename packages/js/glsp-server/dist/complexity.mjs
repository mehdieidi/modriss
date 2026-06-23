/** Applies CVS complexity-management policy to reduce rendered detail at scale. */
export function applyComplexityPolicy(graph, levelConfig = {}, options = {}) {
  const zoom = Number(options.zoom || 1);
  const policies = levelConfig.complexityManagement || [];
  const canvasPolicy = levelConfig.canvasPolicy || {};
  const thresholds = canvasPolicy.semanticZoom || {
    labelMinZoom: 0.55,
    detailMinZoom: 0.75,
    badgeMinZoom: 0.65,
  };

  const nodeCount = (graph.children || []).filter((child) => child.type === "node").length;
  let lod = "full";
  for (const policy of policies) {
    const maxNodes = Number(policy.maxVisibleNodes || policy.threshold || 0);
    if (maxNodes > 0 && nodeCount > maxNodes) {
      lod = policy.lod || policy.level || "summary";
    }
  }
  if (zoom < thresholds.labelMinZoom) {
    lod = "minimal";
  } else if (zoom < thresholds.detailMinZoom) {
    lod = lod === "full" ? "compact" : lod;
  }

  for (const child of graph.children || []) {
    if (child.type !== "node") {
      continue;
    }
    child.args = {
      ...(child.args || {}),
      lod,
      showLabels: zoom >= thresholds.labelMinZoom,
      showBadges: zoom >= thresholds.badgeMinZoom,
      showDetailLines: zoom >= thresholds.detailMinZoom && lod !== "minimal",
    };
  }
  return graph;
}
