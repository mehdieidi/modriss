const reserved = new Set([
  "id",
  "eClass",
  "graph",
  "views",
  "fragments",
  "diagram",
  "activeViewId",
  "traceLinks",
  "manualBacklog",
  "validationIssues",
  "_emfId",
  "_resourceRoots",
  "x",
  "y",
  "label",
  "status",
  "tags",
  "visualOnly",
  "bundle",
  "countsByKind",
  "underlyingRelationshipIds",
  "sourceType",
  "targetType",
  "semanticFeature",
  "semanticSourceElementId",
  "semanticTargetElementId",
  "semanticDirection",
  "rootFeature",
  "__ownerId",
  "__containmentFeature",
  "kind",
  "lifecycleStatus",
]);
export const fieldKey = (feature) =>
  feature.fieldName || (reserved.has(feature.name) ? `_semantic_${feature.name}` : feature.name);
export const typeKey = (type, schema) =>
  type.typeKey ||
  (schema.filter((item) => item.name === type.name).length > 1
    ? `${type.namespace}#${type.name}`
    : type.name);
const title = (name) => name.replace(/([a-z0-9])([A-Z])/g, "$1 $2");

export function defaultPsmNotation(schema) {
  const concrete = schema.filter((type) => !type.abstract);
  if (!concrete.length) throw new Error("This metamodel has no concrete EClass to instantiate.");
  const contained = new Set(
    schema.flatMap((type) =>
      (type.features || [])
        .filter((feature) => feature.containment)
        .map((feature) => `${feature.typeNamespace}#${feature.type}`),
    ),
  );
  const roots = concrete.filter((type) => !contained.has(`${type.namespace}#${type.name}`));
  const root = [...(roots.length ? roots : concrete)].sort(
    (a, b) =>
      (b.features || []).filter((f) => f.containment).length -
      (a.features || []).filter((f) => f.containment).length,
  )[0];
  return {
    version: 1,
    rootType: typeKey(root, schema),
    paletteGroups: [{ id: "resources", name: "Resources" }],
    elements: concrete.map((type) => ({
      type: typeKey(type, schema),
      label: title(type.name),
      icon: "category",
      group: "resources",
      role: "node",
      color: { light: "#475569", dark: "#94a3b8" },
    })),
    views: [
      {
        id: "cloud-model",
        name: "Cloud model",
        types: concrete.map((type) => typeKey(type, schema)),
      },
    ],
  };
}

function defaultValue(feature) {
  if (feature.many) return [];
  if (feature.defaultValue != null) {
    if (/Boolean/.test(feature.type))
      return feature.defaultValue === true || feature.defaultValue === "true";
    if (/Int|Long|Short|Byte|Float|Double|Decimal/.test(feature.type))
      return Number(feature.defaultValue);
    return feature.defaultValue;
  }
  return /Boolean/.test(feature.type) ? false : feature.kind === "reference" ? null : "";
}

/** Deterministic Ecore -> workbench concrete syntax. Only presentation is customizable. */
export function inferPsmLevel(platform, schema, syntax = defaultPsmNotation(schema)) {
  const resolve = (name, namespace) => {
    const type = schema.find((item) => item.name === name && item.namespace === namespace);
    return type ? typeKey(type, schema) : name;
  };
  const root = schema.find((type) => typeKey(type, schema) === syntax.rootType);
  if (!root || root.abstract) throw new Error("Choose a concrete root EClass in Visual notation.");
  const elements = schema.map((type) => {
    const key = typeKey(type, schema);
    const visual = syntax.elements?.find((element) => element.type === key) || {};
    const attributes = (type.features || [])
      .filter((f) => f.kind === "attribute")
      .map((f) => ({
        ...f,
        name: fieldKey(f),
        label: f.name,
        readonly: f.changeable === false || f.derived,
        fieldType: f.many
          ? "json"
          : f.literals
            ? "select"
            : /Boolean/.test(f.type)
              ? "boolean"
              : /Int|Long|Short|Byte|Float|Double|Decimal/.test(f.type)
                ? "number"
                : "text",
        options: f.literals,
        defaultValue: defaultValue(f),
      }));
    const references = (type.features || [])
      .filter((f) => f.kind === "reference")
      .map((f) => ({
        ...f,
        name: fieldKey(f),
        targetType: resolve(f.type, f.typeNamespace),
        kind: f.containment ? "containment" : "reference",
        readonly: f.changeable === false || f.derived,
        defaultValue: f.many ? [] : null,
      }));
    if (key === syntax.rootType)
      references.push({
        name: "_resourceRoots",
        targetType: "*",
        containment: true,
        many: true,
        kind: "containment",
        defaultValue: [],
      });
    return {
      type: key,
      label: visual.label || title(type.name),
      displayName: visual.label || title(type.name),
      abstract: type.abstract,
      supertypes: (type.supertypes || []).map((value) => {
        const at = value.lastIndexOf("#");
        return resolve(value.slice(at + 1), value.slice(0, at));
      }),
      attributes,
      references,
      visibleFields: attributes.map((attribute) => attribute.name),
      creatable: !type.abstract && key !== syntax.rootType,
      containedOnly: false,
      supportOnly: visual.role === "support",
      visualRole: visual.role || "node",
      icon: visual.icon || "category",
      color: visual.color?.light || "#475569",
      themeColor: visual.color || { light: "#475569", dark: "#94a3b8" },
      category:
        syntax.paletteGroups?.find((group) => group.id === visual.group)?.name || "Resources",
      notation: {
        shape: "resource-card",
        tag: platform.name,
        lineFields: attributes
          .filter((a) => a.name !== "name")
          .slice(0, 2)
          .map((a) => a.name),
      },
    };
  });
  const rootElement = elements.find((element) => element.type === syntax.rootType);
  const rootTemplate = {
    eClass: syntax.rootType,
    ...Object.fromEntries(
      [...rootElement.attributes, ...rootElement.references].map((field) => [
        field.name,
        structuredClone(field.defaultValue),
      ]),
    ),
  };
  const rules = elements.flatMap((element) =>
    element.references
      .filter((reference) => !reference.containment && !reference.readonly)
      .map((reference) => ({
        sourceType: element.type,
        targetType: reference.targetType,
        allowedKinds: [`REF_${reference.name.toUpperCase()}`],
      })),
  );
  const semanticReferenceRules = elements.flatMap((element) =>
    element.references
      .filter((reference) => !reference.containment)
      .map((reference) => ({
        sourceType: element.type,
        targetType: reference.targetType,
        feature: reference.name,
        field: reference.name,
        kind: `REF_${reference.name.toUpperCase()}`,
        many: reference.many,
      })),
  );
  const kinds = [...new Set(["CONTAINS", ...semanticReferenceRules.map((rule) => rule.kind)])];
  const views = syntax.views.map((view) => ({
    id: view.id,
    displayName: view.name,
    viewType: view.id,
    palette: view.types.filter((key) => key !== syntax.rootType),
    canvas: view.types,
    elementTypes: view.types,
    relationshipKinds: kinds,
    layoutHint: "DEFAULT_LAYERED",
  }));
  return {
    displayName: platform.name,
    apiType: "psm",
    chatType: "PSM",
    modelNameTemplate: `${platform.name} model`,
    assistantEnabled: false,
    elementsPath: "/graph/elements",
    relationshipsPath: "/graph/relationships",
    labelField: "name",
    elements,
    rootTemplate,
    views,
    viewDefinitions: views,
    relationshipKinds: kinds,
    relationshipRules: rules,
    semanticReferenceRules,
    relationshipSemantics: {
      containmentKind: "CONTAINS",
      containmentKinds: ["CONTAINS"],
      traceKind: "TRACE",
    },
    relationshipKindLabels: Object.fromEntries(
      semanticReferenceRules.map((rule) => [rule.kind, rule.field]),
    ),
    relationshipVisualRules: [],
    badgeRules: [],
    semanticEdgeObjectRules: [],
    shortcutConnectorRules: [],
    containers: [],
    containmentPalettes: {},
    canvasPolicy: {
      standalonePaletteRoles: ["node", "container", "detail"],
      roleSizes: {
        node: { width: 244, height: 118 },
        container: { width: 360, height: 190 },
        detail: { width: 244, height: 88 },
      },
    },
    strictnessModes: ["exploration", "methodology", "production"],
    workbench: { defaultViewDefinitionId: views[0].id, includeMainView: false },
    complexityManagement: [],
    scaffoldRecipes: [],
    boundedContext: {},
    constraints: [],
  };
}
