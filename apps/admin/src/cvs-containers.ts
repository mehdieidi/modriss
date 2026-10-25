type CvsDocument = Record<string, any>;

/** Container ownership applies to both node classes and class-based edge notation. */
export function containerEditingCatalog(doc: CvsDocument, ownerType?: string) {
  const featuresByOwner = doc.metamodelContainmentFeatures || {};
  const ownerTypes = Object.keys(featuresByOwner)
    .filter((type) => featuresByOwner[type]?.length > 0);
  const childTypes = [...new Set<string>(
    (featuresByOwner[ownerType || ""] || []).flatMap((feature: any) => feature.types || []),
  )];
  const edgeTypes = new Set<string>([
    ...(doc.elements || [])
      .filter((element: any) => element.relationshipElement || element.visualRole === "relationship")
      .map((element: any) => element.type),
    ...(doc.metamodelSemanticEdgeObjectRules || []).map((rule: any) => rule.eClass),
    ...(doc.semanticEdgeObjectRules || []).map((rule: any) => rule.eClass),
  ].filter(Boolean));
  return { ownerTypes, childTypes, edgeTypes };
}
