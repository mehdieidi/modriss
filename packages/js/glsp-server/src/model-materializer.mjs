function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function elementType(element) {
  return String(element?.eClass || element?.type || "Element");
}

function elementMatchesFilterTypes(element, filterTypes) {
  if (!filterTypes.size) {
    return true;
  }
  return filterTypes.has(elementType(element));
}

function relationshipKindMatches(kind, filterKinds) {
  if (!filterKinds.size) {
    return true;
  }
  return filterKinds.has(
    String(kind || "")
      .trim()
      .toUpperCase(),
  );
}

function relationshipDedupeKey(relationship) {
  return [
    String(relationship?.sourceElementId || relationship?.source || ""),
    String(relationship?.targetElementId || relationship?.target || ""),
    String(relationship?.kind || "").toUpperCase(),
  ].join("|");
}

function viewNodeByElement(view) {
  return new Map(
    safeArray(view?.nodes)
      .map((node) => [String(node?.elementId || node?.id || ""), node])
      .filter(([id]) => id),
  );
}

export function buildRuntimeGraph(model) {
  const elementsById = new Map();
  const relationshipsById = new Map();

  for (const element of safeArray(model?.graph?.elements)) {
    const id = String(element?.id || "").trim();
    if (!id) {
      continue;
    }
    elementsById.set(id, {
      ...element,
      id,
      eClass: elementType(element),
    });
  }

  if (!elementsById.size) {
    for (const element of safeArray(model?.diagram?.elements)) {
      const id = String(element?.id || "").trim();
      if (!id) {
        continue;
      }
      const layout = element.layout || {};
      elementsById.set(id, {
        ...element,
        id,
        eClass: elementType(element),
        x: layout.x ?? element.x,
        y: layout.y ?? element.y,
        width: layout.width ?? element.width,
        height: layout.height ?? element.height,
      });
    }
  }

  for (const relationship of safeArray(model?.graph?.relationships)) {
    const id = String(relationship?.id || "").trim();
    if (!id) {
      continue;
    }
    const sourceElementId = String(
      relationship.sourceElementId || relationship.source || "",
    ).trim();
    const targetElementId = String(
      relationship.targetElementId || relationship.target || "",
    ).trim();
    if (!sourceElementId || !targetElementId) {
      continue;
    }
    if (!elementsById.has(sourceElementId) || !elementsById.has(targetElementId)) {
      continue;
    }
    relationshipsById.set(id, {
      ...relationship,
      id,
      sourceElementId,
      targetElementId,
      kind: relationship.kind || relationship.type || "DEPENDS_ON",
    });
  }

  return { elementsById, relationshipsById };
}

export function selectElementIdsForView(graph, view) {
  const hidden = new Set(safeArray(view?.hidden?.elementIds).map(String));
  const pinned = new Set(safeArray(view?.pinnedElementIds).map(String));
  const filterTypes = new Set(safeArray(view?.filters?.elementTypes).map(String));

  const explicitNodeIds = safeArray(view?.nodes)
    .map((node) => String(node?.elementId || node?.id || ""))
    .filter(Boolean);

  const hasExplicitNodes = explicitNodeIds.length > 0;
  let candidates;

  if (hasExplicitNodes) {
    candidates = new Set(explicitNodeIds);
  } else {
    candidates = new Set(graph.elementsById.keys());
  }

  for (const elementId of pinned) {
    if (graph.elementsById.has(elementId)) {
      candidates.add(elementId);
    }
  }

  const selected = [];
  for (const elementId of candidates) {
    const element = graph.elementsById.get(elementId);
    if (!element || hidden.has(elementId)) {
      continue;
    }
    if (hasExplicitNodes || pinned.has(elementId)) {
      selected.push(elementId);
      continue;
    }
    if (elementMatchesFilterTypes(element, filterTypes)) {
      selected.push(elementId);
    }
  }

  if (!selected.length && !filterTypes.size) {
    return [...graph.elementsById.keys()].filter((elementId) => !hidden.has(elementId));
  }

  return selected;
}

export function selectRelationshipIdsForView(graph, view, elementIds) {
  const hidden = new Set(safeArray(view?.hidden?.relationshipIds).map(String));
  const filterKinds = new Set(
    safeArray(view?.filters?.relationshipKinds)
      .map((kind) =>
        String(kind || "")
          .trim()
          .toUpperCase(),
      )
      .filter(Boolean),
  );
  const elementSet = new Set(elementIds);
  const explicitEdgeVisibility = new Map(
    safeArray(view?.edges).map((edge) => [String(edge.relationshipId || ""), edge]),
  );
  const relationshipIds = [];
  const seenRelationshipKeys = new Set();

  for (const [relationshipId, relationship] of graph.relationshipsById.entries()) {
    if (hidden.has(relationshipId)) {
      continue;
    }
    const explicit = explicitEdgeVisibility.get(relationshipId);
    if (explicit && explicit.visible === false) {
      continue;
    }
    if (
      !elementSet.has(relationship.sourceElementId) ||
      !elementSet.has(relationship.targetElementId)
    ) {
      continue;
    }
    if (!relationshipKindMatches(relationship.kind, filterKinds)) {
      continue;
    }
    const dedupeKey = relationshipDedupeKey(relationship);
    if (seenRelationshipKeys.has(dedupeKey)) {
      continue;
    }
    seenRelationshipKeys.add(dedupeKey);
    relationshipIds.push(relationshipId);
  }

  return relationshipIds;
}

function mapRuntimeNode(element, viewNode) {
  const x = Number.isFinite(Number(viewNode?.x))
    ? Number(viewNode.x)
    : Number.isFinite(Number(element?.x))
      ? Number(element.x)
      : 0;
  const y = Number.isFinite(Number(viewNode?.y))
    ? Number(viewNode.y)
    : Number.isFinite(Number(element?.y))
      ? Number(element.y)
      : 0;
  const width = Number(viewNode?.width || element?.width || 228);
  const height = Number(viewNode?.height || element?.height || 112);

  return {
    id: String(element.id),
    type: elementType(element),
    x,
    y,
    width,
    height,
    data: {
      ...element,
      name: element.name || element.label || element.id,
    },
  };
}

function mapRuntimeConnection(relationship) {
  return {
    id: String(relationship.id),
    sourceId: String(relationship.sourceElementId),
    targetId: String(relationship.targetElementId),
    kind: String(relationship.kind || "DEPENDS_ON"),
    data: relationship,
    shortcut: Boolean(relationship.shortcut || relationship.data?.shortcut),
  };
}

export function materializeDiagramFromModel(model, viewId = "") {
  const graph = buildRuntimeGraph(model);
  const views = safeArray(model?.views);
  const activeView = viewId ? views.find((view) => view.id === viewId) : views[0];

  if (!activeView) {
    const nodes = [...graph.elementsById.values()].map((element) => mapRuntimeNode(element));
    const connections = [...graph.relationshipsById.values()].map(mapRuntimeConnection);
    return { nodes, connections };
  }

  const elementIds = selectElementIdsForView(graph, activeView);
  const relationshipIds = selectRelationshipIdsForView(graph, activeView, elementIds);
  const layoutByElement = viewNodeByElement(activeView);

  const nodes = elementIds
    .map((elementId) => {
      const element = graph.elementsById.get(elementId);
      return element ? mapRuntimeNode(element, layoutByElement.get(elementId)) : null;
    })
    .filter(Boolean);

  const connections = relationshipIds
    .map((relationshipId) => {
      const relationship = graph.relationshipsById.get(relationshipId);
      return relationship ? mapRuntimeConnection(relationship) : null;
    })
    .filter(Boolean);

  return { nodes, connections };
}
