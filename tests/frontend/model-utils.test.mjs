import assert from "node:assert/strict";
import test from "node:test";

globalThis.window = {
  location: { origin: "http://localhost" },
};
globalThis.document = {
  getElementById: () => null,
};

const { state } = await import("../../apps/frontend/js/state.js");
const { applyModelingRuntimeConfig } = await import("../../apps/frontend/js/config.js");
const {
  modelingRelationshipElementTypes,
  initializeModelingRuntimeState,
} = await import("../../apps/frontend/js/modeling-config-data.js");
const { relationshipSemanticCopy } = await import("../../apps/frontend/js/model-utils.js");
const {
  serializeGraphAndViewsInto,
  serializeGraphAndViewsIntoAsync,
} = await import("../../apps/frontend/js/graph-store.js");

const modelingConfig = {
  levelOrder: ["pim"],
  levels: {
    pim: {
      apiType: "PIM",
      rootTemplate: { eClass: "PIMModel" },
      elements: [
        {
          type: "EventRoutingRule",
          references: [{ name: "targets", many: true }],
        },
        {
          type: "ServiceElementMembership",
          references: [{ name: "element", many: false }],
        },
      ],
      semanticEdgeObjectRules: [
        {
          eClass: "EventRoutingRule",
          defaultKind: "TARGETS",
          targetFeature: "targets",
          sourceFeature: "source",
        },
        {
          eClass: "ServiceElementMembership",
          defaultKind: "OWNS",
          targetFeature: "element",
          sourceFeature: "service",
        },
      ],
    },
  },
};
state.modelingConfig.config = modelingConfig;
applyModelingRuntimeConfig(modelingConfig);
state.activeType = "pim";

test("keeps one many-target semantic relationship as one XMI object", () => {
  const copy = relationshipSemanticCopy("pim", {
    eClass: "EventRoutingRule",
    id: "routing-1::target-a",
    semanticObjectId: "routing-1",
    kind: "TARGETS",
    sourceElementId: "bus-1",
    targetElementId: "target-a",
    source: "bus-1",
    target: "target-a",
    targets: ["target-a", "target-b"],
  });

  assert.equal(copy.id, "routing-1");
  assert.deepEqual(copy.targets, ["target-a", "target-b"]);
  assert.equal("semanticObjectId" in copy, false);
});

test("does not reuse a semantic id for expanded one-target relationships", () => {
  const copy = relationshipSemanticCopy("pim", {
    eClass: "ServiceElementMembership",
    id: "membership-1::element-b",
    semanticObjectId: "membership-1",
    kind: "OWNS",
    sourceElementId: "service-1",
    targetElementId: "element-b",
    source: "service-1",
    target: "element-b",
    element: "element-b",
  });

  assert.equal(copy.id, "membership-1::element-b");
  assert.equal(copy.element, "element-b");
  assert.equal("semanticObjectId" in copy, false);
});

test("classifies configured relationship elements as semantic root relationships", () => {
  const cimConfig = {
    apiType: "CIM",
    elements: [
      {
        type: "DomainRelationship",
        relationshipElement: true,
        creatable: true,
      },
    ],
    semanticEdgeObjectRules: [
      {
        eClass: "DomainRelationship",
        matchKinds: ["DOMAIN_RELATIONSHIP"],
        sourceFeature: "source",
        targetFeature: "target",
      },
    ],
  };
  state.modelingConfig.config.levels.cim = cimConfig;
  applyModelingRuntimeConfig({ levels: { cim: cimConfig } });

  assert.deepEqual(modelingRelationshipElementTypes("cim"), ["DomainRelationship"]);
});

test("does not classify contained-only relationship elements as root relationships", () => {
  const pimConfig = {
    apiType: "PIM",
    elements: [
      {
        type: "Schedule",
        relationshipElement: true,
        containedOnly: true,
        creatable: true,
      },
      {
        type: "ServiceElementMembership",
        relationshipElement: true,
        containedOnly: true,
        creatable: false,
      },
      {
        type: "DomainRelationship",
        relationshipElement: true,
        containedOnly: false,
        creatable: true,
      },
    ],
  };
  state.modelingConfig.config.levels.pim = pimConfig;
  applyModelingRuntimeConfig({ levels: { pim: pimConfig } });

  assert.deepEqual(modelingRelationshipElementTypes("pim"), ["DomainRelationship"]);
});

test("preserves semantic root assumptions when the graph projection is empty", () => {
  state.activeType = "pim";
  state.modelingConfig.config = {
    levelOrder: ["pim"],
    levels: { pim: { apiType: "PIM", rootTemplate: { eClass: "PIMModel" } } },
  };
  state.graph = {
    elementsById: new Map(),
    relationshipsById: new Map(),
    traceLinksById: new Map(),
    assumptionsById: new Map(),
    validationIssues: [],
    manualBacklog: [],
  };
  state.views = {
    byId: new Map(),
    activeViewId: null,
    visibleNodeIds: new Set(),
    visibleRelationshipIds: new Set(),
    expandedContainers: new Set(),
  };

  const root = {
    eClass: "PIMModel",
    assumptions: [{ id: "semantic-assumption" }],
  };
  serializeGraphAndViewsInto(root, { syncView: false });

  assert.deepEqual(root.assumptions, [{ id: "semantic-assumption" }]);
});

test("preserves semantic root assumptions in the async save serializer", async () => {
  const root = {
    eClass: "PIMModel",
    assumptions: [{ id: "semantic-assumption-async" }],
  };
  await serializeGraphAndViewsIntoAsync(root, { syncView: false });

  assert.deepEqual(root.assumptions, [{ id: "semantic-assumption-async" }]);
});

test("serializes a newly drawn contained transition under its common semantic owner", async () => {
  const cimLevel = {
    apiType: "CIM",
    rootTemplate: { eClass: "CIMModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      {
        type: "CIMModel",
        references: [{ name: "processes", targetType: "BusinessProcess", containment: true, many: true }],
      },
      {
        type: "BusinessProcess",
        references: [
          { name: "steps", targetType: "ProcessStep", containment: true, many: true },
          { name: "transitions", targetType: "ProcessTransition", containment: true, many: true },
        ],
      },
      { type: "ProcessStep" },
      { type: "StartStep", supertypes: ["ProcessStep"] },
      { type: "CommandStep", supertypes: ["ProcessStep"] },
      { type: "EndStep", supertypes: ["ProcessStep"] },
      {
        type: "ProcessTransition",
        relationshipElement: true,
        references: [
          { name: "source", targetType: "ProcessStep" },
          { name: "target", targetType: "ProcessStep" },
        ],
      },
    ],
    semanticEdgeObjectRules: [
      {
        eClass: "ProcessTransition",
        matchKinds: ["TRANSITION"],
        sourceType: "ProcessStep",
        targetType: "ProcessStep",
        rootFeature: "transitions",
        sourceFeature: "source",
        targetFeature: "target",
      },
    ],
  };
  const cimConfig = { levelOrder: ["cim"], levels: { cim: cimLevel } };
  state.modelingConfig.config = cimConfig;
  initializeModelingRuntimeState(cimConfig);
  state.activeType = "cim";

  const model = {
    eClass: "CIMModel",
    processes: [
      {
        eClass: "BusinessProcess",
        id: "process-1",
        name: "Process 1",
        steps: [
          { eClass: "StartStep", id: "start-1", name: "Start" },
          { eClass: "CommandStep", id: "command-1", name: "Command" },
          { eClass: "EndStep", id: "end-1", name: "End" },
        ],
        transitions: [],
      },
    ],
  };
  const { installGraphAndViews, addConnectionToGraphAndActiveView } = await import(
    "../../apps/frontend/js/graph-store.js"
  );
  installGraphAndViews("cim", model, "process-model");

  // Simulate the scoped editor path: endpoint nodes are present, but their transient owner
  // fields are unavailable. The containment graph still carries the authoritative ownership.
  ["start-1", "command-1", "end-1"].forEach((id) => {
    const element = state.graph.elementsById.get(id);
    delete element.__ownerId;
    delete element.__containmentFeature;
  });
  state.graph.parentByChild.set("start-1", "process-1");
  state.graph.parentByChild.set("command-1", "process-1");
  state.graph.parentByChild.set("end-1", "process-1");

  addConnectionToGraphAndActiveView({
    id: "transition-1",
    sourceId: "start-1",
    targetId: "command-1",
    kind: "TRANSITION",
  });
  addConnectionToGraphAndActiveView({
    id: "transition-2",
    sourceId: "command-1",
    targetId: "end-1",
    kind: "TRANSITION",
  });

  const root = { eClass: "CIMModel" };
  serializeGraphAndViewsInto(root, { syncView: false });
  assert.deepEqual(
    root.processes[0].transitions.map(({ source, target }) => ({ source, target })),
    [
      { source: "start-1", target: "command-1" },
      { source: "command-1", target: "end-1" },
    ],
  );
});
