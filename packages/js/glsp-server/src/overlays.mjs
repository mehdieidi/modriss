export function buildOverlayPayload({ levelConfig, diagram, options = {} }) {
  const boundedContext = levelConfig.boundedContext || {};
  const contextBoxes = boundedContext.enabled ? computeContextBoxes(diagram, boundedContext) : [];
  const validationByElement = indexValidation(options.validationIssues || []);
  const impactByElement = indexImpact(options.impactState || {});

  return {
    contextBoxes,
    validationByElement,
    impactByElement,
    selection: options.selection || { nodeId: null, edgeId: null },
    connectionDrag: options.connectionDrag || null,
  };
}

function computeContextBoxes(diagram, policy) {
  const features = policy.membershipFeatures || [];
  const boxes = [];
  for (const feature of features) {
    const members = diagram.nodes.filter((node) => node.type === feature.targetType);
    if (!members.length) {
      continue;
    }
    const bounds = boundsForNodes(members);
    boxes.push({
      id: `ctx-${feature.sourceType}-${feature.feature}`,
      label: feature.sourceType,
      ...bounds,
      memberIds: members.map((node) => node.id),
    });
  }
  return boxes;
}

function boundsForNodes(nodes) {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const node of nodes) {
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + (node.width || 228));
    maxY = Math.max(maxY, node.y + (node.height || 112));
  }
  return {
    x: minX - 24,
    y: minY - 32,
    width: maxX - minX + 48,
    height: maxY - minY + 56,
  };
}

function indexValidation(issues) {
  const map = {};
  for (const issue of issues) {
    const id = String(issue.elementId || issue.targetId || "");
    if (!id) {
      continue;
    }
    map[id] = map[id] || [];
    map[id].push(issue);
  }
  return map;
}

function indexImpact(impactState) {
  const highlighted = new Set((impactState.highlightedIds || []).map(String));
  const map = {};
  for (const id of highlighted) {
    map[id] = { severity: impactState.severity || "info" };
  }
  return map;
}
