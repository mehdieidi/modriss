// A node's identity includes its model. Names and provenance are never identities.
export function impactKey(item) {
  return JSON.stringify([item?.modelId, item?.elementId]);
}

export function uniqueImpactItems(items = []) {
  return [...new Map(items.map((item) => [impactKey(item), item])).values()];
}

export function isImpactFile(item) {
  return String(item?.modelType).toUpperCase() === "ARTIFACT";
}

function evidenceIndex(data, direction) {
  const index = new Map(
    [data.focalElement, ...(data.upstream || []), ...(data.downstream || [])]
      .filter(Boolean)
      .map((ref) => [impactKey(ref), ref]),
  );
  const reverse = direction === "upstream";
  const byNode = new Map();
  for (const edge of data.traceEdges || []) {
    const key = impactKey({
      modelId: reverse ? edge.sourceModelId : edge.targetModelId,
      elementId: reverse ? edge.sourceElementId : edge.targetElementId,
    });
    if (!byNode.has(key)) byNode.set(key, []);
    byNode.get(key).push({
      edge,
      peer: index.get(
        impactKey({
          modelId: reverse ? edge.targetModelId : edge.sourceModelId,
          elementId: reverse ? edge.targetElementId : edge.sourceElementId,
        }),
      ),
    });
  }
  return byNode;
}

export function impactEvidence(data, item, direction) {
  return evidenceIndex(data, direction).get(impactKey(item)) || [];
}

export function impactGroups(data, direction, query = "") {
  const groups = new Map();
  const term = query.trim().toLowerCase();
  const evidenceByNode = evidenceIndex(data, direction);
  for (const item of uniqueImpactItems(data[direction])) {
    const evidence = evidenceByNode.get(impactKey(item)) || [];
    const searchText = [
      item.elementName,
      item.elementId,
      item.elementType,
      item.modelName,
      item.modelType,
      ...evidence.flatMap(({ edge, peer }) => [edge.transformationRule, peer?.elementName]),
    ]
      .join(" ")
      .toLowerCase();
    if (term && !searchText.includes(term)) continue;
    if (!groups.has(item.modelId)) {
      groups.set(item.modelId, {
        modelId: item.modelId,
        modelType: item.modelType,
        modelName: item.modelName,
        files: isImpactFile(item),
        items: [],
      });
    }
    groups.get(item.modelId).items.push({ item, evidence });
  }
  for (const group of groups.values()) {
    group.items.sort((a, b) =>
      String(a.item.elementName).localeCompare(String(b.item.elementName)),
    );
  }
  return [...groups.values()];
}

export function impactCounts(data) {
  const downstream = uniqueImpactItems(data.downstream);
  return {
    upstream: uniqueImpactItems(data.upstream).length,
    downstream: downstream.filter((item) => !isImpactFile(item)).length,
    files: downstream.filter(isImpactFile).length,
  };
}
