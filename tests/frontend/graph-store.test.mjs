import assert from "node:assert/strict";
import test from "node:test";

globalThis.window = {
  MODLESS_BACKEND_BASE_URL: "",
  location: { origin: "http://localhost" },
};
globalThis.document = { getElementById: () => null };

const { state } = await import("../../apps/frontend/js/state.js");
const {
  addConnectionToGraphAndActiveView,
  installGraphAndViews,
  selectElementIdsForView,
  selectRelationshipIdsForView,
  serializeGraphAndViewsInto,
} = await import("../../apps/frontend/js/graph-store.js");

function configure(elements, rootType = "Root", viewDefinitions = []) {
  state.activeType = "test";
  state.modelingConfig.config = {
    defaultLevel: "test",
    levelOrder: ["test"],
    levels: {
      test: {
        displayName: "Test",
        chatType: "TEST",
        rootTemplate: { eClass: rootType },
        elements,
        viewDefinitions,
        relationshipKinds: ["CONTAINS", "INVOKES"],
        relationshipSemantics: {
          containmentKind: "CONTAINS",
          containmentKinds: ["CONTAINS"],
          traceKind: "TRACE",
        },
        semanticReferenceRules: [],
        semanticEdgeObjectRules: [],
        canvasPolicy: {},
      },
    },
  };
}

test("metadata-backed views retain relationship endpoints outside the primary type filter", () => {
  configure(
    [
      { type: "Root", references: [] },
      { type: "Route", references: [], supertypes: [] },
      { type: "Function", references: [], supertypes: [] },
    ],
    "Root",
    [{ id: "routes", elementTypes: ["Route"], relationshipKinds: ["INVOKES"] }],
  );
  const graph = {
    elementsById: new Map([
      ["route", { id: "route", eClass: "Route" }],
      ["function", { id: "function", eClass: "Function" }],
    ]),
    relationshipsById: new Map([
      [
        "edge",
        {
          id: "edge",
          kind: "INVOKES",
          sourceElementId: "route",
          targetElementId: "function",
        },
      ],
    ]),
    relationshipsBySource: new Map(),
    relationshipsByTarget: new Map(),
    containmentByParent: new Map(),
    parentByChild: new Map(),
  };
  const view = {
    id: "routes",
    definitionId: "routes",
    filters: { elementTypes: ["Route"], relationshipKinds: ["INVOKES"] },
    hidden: { elementIds: [], relationshipIds: [] },
    nodes: [],
    edges: [],
  };

  const elementIds = selectElementIdsForView(graph, view, "test");
  assert.deepEqual(new Set(elementIds), new Set(["route", "function"]));
  assert.deepEqual(selectRelationshipIdsForView(graph, view, elementIds), ["edge"]);
});

test("root-only semantic models synthesize containment edges", () => {
  configure([
    {
      type: "Root",
      references: [
        {
          name: "parents",
          targetType: "Parent",
          containment: true,
          many: true,
          readonly: false,
        },
      ],
      supertypes: [],
    },
    {
      type: "Parent",
      references: [
        {
          name: "children",
          targetType: "Child",
          containment: true,
          many: true,
          readonly: false,
        },
      ],
      supertypes: [],
      creatable: true,
      visualRole: "container",
    },
    {
      type: "Child",
      references: [],
      supertypes: [],
      creatable: true,
      visualRole: "node",
    },
  ]);

  const installed = installGraphAndViews(
    "test",
    {
      eClass: "Root",
      id: "root",
      parents: [
        {
          eClass: "Parent",
          id: "parent",
          children: [{ eClass: "Child", id: "child" }],
        },
      ],
    },
    "test-model",
  );

  const containment = [...installed.graph.relationshipsById.values()].find(
    (relationship) =>
      relationship.sourceElementId === "parent" && relationship.targetElementId === "child",
  );
  assert.equal(containment?.kind, "CONTAINS");
  assert.equal(containment?.semanticFeature, "children");
});

test("persisted metadata-backed views are repaired with missing relationship endpoints", () => {
  configure(
    [
      { type: "Root", references: [], supertypes: [] },
      { type: "Route", references: [], supertypes: [] },
      { type: "Function", references: [], supertypes: [] },
    ],
    "Root",
    [{ id: "routes", elementTypes: ["Route"], relationshipKinds: ["INVOKES"] }],
  );

  const installed = installGraphAndViews(
    "test",
    {
      graph: {
        elements: [
          { id: "route", eClass: "Route" },
          { id: "function", eClass: "Function" },
        ],
        relationships: [
          {
            id: "edge",
            kind: "INVOKES",
            sourceElementId: "route",
            targetElementId: "function",
          },
        ],
      },
      views: [
        {
          id: "saved-routes",
          definitionId: "routes",
          filters: { elementTypes: ["Route"], relationshipKinds: ["INVOKES"] },
          nodes: [{ elementId: "route", x: 10, y: 20 }],
          edges: [],
        },
      ],
    },
    "test-model",
  );

  const view = installed.views.byId.get("saved-routes");
  assert.deepEqual(
    new Set(view.nodes.map((node) => node.elementId)),
    new Set(["route", "function"]),
  );
  assert.deepEqual(
    view.edges.map((edge) => edge.relationshipId),
    ["edge"],
  );
});

test("class-based relationships preserve semantic kind and root containment", () => {
  configure([
    {
      type: "Root",
      references: [
        {
          name: "requirements",
          targetType: "Requirement",
          containment: true,
          many: true,
          readonly: false,
        },
        {
          name: "requirementRelationships",
          targetType: "RequirementRelationship",
          containment: true,
          many: true,
          readonly: false,
        },
      ],
      supertypes: [],
    },
    {
      type: "Requirement",
      attributes: [],
      references: [],
      supertypes: [],
      creatable: true,
      visualRole: "node",
    },
    {
      type: "RequirementRelationship",
      attributes: [
        { name: "kind", fieldType: "select", options: ["DEPENDS_ON"], required: true },
        { name: "blocking", fieldType: "boolean" },
      ],
      references: [
        { name: "source", targetType: "Requirement", many: false },
        { name: "target", targetType: "Requirement", many: false },
      ],
      supertypes: [],
      relationshipElement: true,
      creatable: false,
      visualRole: "relationship",
    },
  ]);
  state.modelingConfig.config.levels.test.semanticEdgeObjectRules = [
    {
      eClass: "RequirementRelationship",
      matchKinds: ["DEPENDS_ON"],
      sourceType: "Requirement",
      targetType: "Requirement",
      rootFeature: "requirementRelationships",
      sourceFeature: "source",
      targetFeature: "target",
      defaults: { blocking: false },
    },
  ];
  installGraphAndViews(
    "test",
    {
      eClass: "Root",
      id: "root",
      requirements: [
        { eClass: "Requirement", id: "source", name: "Source" },
        { eClass: "Requirement", id: "target", name: "Target" },
      ],
    },
    "test-model",
  );

  addConnectionToGraphAndActiveView({
    id: "requirement-edge",
    sourceId: "source",
    targetId: "target",
    kind: "DEPENDS_ON",
  });
  const root = { eClass: "Root", id: "root" };
  serializeGraphAndViewsInto(root);

  assert.equal(root.requirementRelationships.length, 1);
  assert.equal(root.requirementRelationships[0].eClass, "RequirementRelationship");
  assert.equal(root.requirementRelationships[0].kind, "DEPENDS_ON");
  assert.equal(root.requirementRelationships[0].source, "source");
  assert.equal(root.requirementRelationships[0].target, "target");
});

test("semantic relationship objects use the canonical configured view kind", () => {
  configure([
    {
      type: "Root",
      references: [
        { name: "steps", targetType: "Step", containment: true, many: true },
        {
          name: "transitions",
          targetType: "ProcessTransition",
          containment: true,
          many: true,
        },
      ],
      supertypes: [],
    },
    { type: "Step", references: [], supertypes: [] },
    {
      type: "ProcessTransition",
      references: [
        { name: "source", targetType: "Step", many: false },
        { name: "target", targetType: "Step", many: false },
      ],
      supertypes: [],
      relationshipElement: true,
    },
  ]);
  state.modelingConfig.config.levels.test.semanticEdgeObjectRules = [
    {
      eClass: "ProcessTransition",
      matchKinds: ["TRANSITION"],
      sourceFeature: "source",
      targetFeature: "target",
    },
  ];

  const installed = installGraphAndViews(
    "test",
    {
      eClass: "Root",
      id: "root",
      steps: [
        { eClass: "Step", id: "start" },
        { eClass: "Step", id: "end" },
      ],
      transitions: [
        { eClass: "ProcessTransition", id: "transition", source: "start", target: "end" },
      ],
    },
    "test-model",
  );

  assert.equal(installed.graph.relationshipsById.get("transition")?.kind, "TRANSITION");
});
