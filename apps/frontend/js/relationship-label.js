import { state } from "./state.js";
import { modelingLevelConfig, modelingRelationshipKindLabel } from "./modeling-config-data.js";

function refLabel(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value && typeof value === "object") {
    return value.name || value.label || value.$ref || value.id || "";
  }
  return "";
}

export function edgeLabel(edge, typeKey = state.activeType) {
  const relationship = state.graph?.relationshipsById?.get(edge?.id) || edge || {};
  const config = modelingLevelConfig(typeKey);
  const type = relationship.eClass || edge?.eClass;
  const rule = (config.semanticEdgeObjectRules || []).find(
    (candidate) => candidate.eClass === type,
  );
  const ids = [edge?.id, relationship.id, relationship.semanticObjectId].filter(Boolean);
  const prefixes = [
    type,
    relationship.rootFeature,
    relationship.__containmentFeature,
    rule?.rootFeature,
  ].filter(Boolean);
  // Generated identity names are useful in the model, but should not hide the kind label.
  const isIdentityLabel = (value) =>
    ids.some((id) => value === id || prefixes.some((prefix) => value === `${prefix} ${id}`));
  const explicit = String(refLabel(edge?.label));
  if (explicit.trim() && !isIdentityLabel(explicit)) {
    return explicit;
  }
  if (edge?.bundle) {
    return "Bundled relations";
  }
  for (const field of config.relationshipLabelFields || []) {
    const text = refLabel(relationship[field]);
    if (String(text).trim() && !isIdentityLabel(text)) {
      return String(text);
    }
  }
  return modelingRelationshipKindLabel(typeKey, edge?.kind || relationship.kind);
}
