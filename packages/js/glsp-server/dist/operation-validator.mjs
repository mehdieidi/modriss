export function legalKindsBetween(levelConfig, sourceType, targetType) {
  const rules = levelConfig.relationshipRules || [];
  const kinds = new Set();
  for (const rule of rules) {
    const source = String(rule.sourceType || "");
    const target = String(rule.targetType || "");
    if (!typeMatches(source, sourceType) || !typeMatches(target, targetType)) {
      continue;
    }
    for (const kind of rule.allowedKinds || []) {
      kinds.add(String(kind));
    }
  }
  return manualRelationshipKinds(levelConfig, kinds);
}

export function validateCreateEdge(levelConfig, sourceType, targetType, edgeKind) {
  const kinds = legalKindsBetween(levelConfig, sourceType, targetType);
  if (!kinds.length) {
    return { ok: false, message: `No legal edge between ${sourceType} and ${targetType}` };
  }
  if (edgeKind && !kinds.includes(edgeKind)) {
    return { ok: false, message: `Edge kind ${edgeKind} not allowed; legal: ${kinds.join(", ")}` };
  }
  return { ok: true, edgeKind: edgeKind || kinds[0], legalKinds: kinds };
}

export function validateCreateNode(levelConfig, elementType) {
  const elements = levelConfig.elements || [];
  const def = elements.find((item) => item.type === elementType);
  if (!def) {
    return { ok: false, message: `Unknown element type: ${elementType}` };
  }
  if (def.supportOnly || def.abstract || def.containedOnly || def.relationshipElement) {
    return { ok: false, message: `Type ${elementType} is not creatable on canvas` };
  }
  if (def.creatable === false) {
    return { ok: false, message: `Type ${elementType} is not creatable` };
  }
  return { ok: true };
}

function typeMatches(expected, actual) {
  if (!expected || expected === "*") {
    return true;
  }
  return expected === actual;
}

function manualRelationshipKinds(levelConfig, kinds) {
  const semantics = levelConfig.relationshipSemantics || {};
  const traceKind = String(semantics.traceKind || "")
    .trim()
    .toUpperCase();
  return [...kinds].filter((kind) => {
    const normalized = String(kind || "")
      .trim()
      .toUpperCase();
    return normalized && normalized !== traceKind;
  });
}
