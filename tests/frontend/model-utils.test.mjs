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
const {
  relationshipSemanticCopy,
  semanticGraphHasChanges,
} = await import("../../apps/frontend/js/model-utils.js");
const {
  serializeGraphAndViewsInto,
  serializeGraphAndViewsIntoAsync,
} = await import("../../apps/frontend/js/graph-store.js");
const { buildSaveRootFromBase } = await import("../../apps/frontend/js/diagram.js");

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

test("preserves the semantic root name when the record name is only a display name", () => {
  const root = buildSaveRootFromBase("pim", "cim-pim", {
    eClass: "PIMModel",
    id: "pim-root-1",
    name: "ClimateReliefGrantsBusinessModel",
    domainName: "Climate Relief Grants",
  });

  assert.equal(root.name, "ClimateReliefGrantsBusinessModel");
  assert.equal(root.id, "pim-root-1");
  assert.equal(root.domainName, "Climate Relief Grants");
});

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

test("classifies contained edge primitives as relationships while keeping node primitives as elements", () => {
  const pimConfig = {
    apiType: "PIM",
    elements: [
      {
        type: "WorkflowTransition",
        relationshipElement: true,
        containedOnly: true,
        primitive: "state-transition-edge",
      },
      {
        type: "Schedule",
        relationshipElement: true,
        containedOnly: true,
        primitive: "schedule-card",
      },
    ],
  };
  state.modelingConfig.config.levels.pim = pimConfig;
  applyModelingRuntimeConfig({ levels: { pim: pimConfig } });

  assert.deepEqual(modelingRelationshipElementTypes("pim"), ["WorkflowTransition"]);
});

test("projects a contained transition to an edge without losing its terminal endpoint", async () => {
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
        containedOnly: true,
        primitive: "control-flow-edge",
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
  const config = { levelOrder: ["cim"], levels: { cim: cimLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "cim";

  const model = {
    eClass: "CIMModel",
    processes: [
      {
        eClass: "BusinessProcess",
        id: "process-1",
        steps: [
          { eClass: "StartStep", id: "start-1" },
          { eClass: "CommandStep", id: "command-1" },
          { eClass: "EndStep", id: "end-1" },
        ],
        transitions: [
          { eClass: "ProcessTransition", id: "transition-1", source: "start-1", target: "command-1" },
          { eClass: "ProcessTransition", id: "transition-2", source: "command-1", target: "end-1" },
        ],
      },
    ],
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  const installed = installGraphAndViews("cim", model, "process-model", {
    skipFragments: true,
    skipClientLayout: true,
  });

  assert.equal(installed.graph.elementsById.has("transition-1"), false);
  assert.equal(installed.graph.elementsById.has("transition-2"), false);
  assert.deepEqual(
    ["transition-1", "transition-2"].map((id) => {
      const edge = installed.graph.relationshipsById.get(id);
      return { source: edge?.sourceElementId, target: edge?.targetElementId };
    }),
    [
      { source: "start-1", target: "command-1" },
      { source: "command-1", target: "end-1" },
    ],
  );
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

test("does not add an unsupported empty assumptions containment during layout serialization", () => {
  state.activeType = "pim";
  state.graph.assumptionsById = new Map();
  state.graph.assumptions = [];
  const root = { eClass: "PIMModel" };

  serializeGraphAndViewsInto(root, { syncView: false });

  assert.equal("assumptions" in root, false);
});

test("does not rebuild semantic root for a layout-only graph projection", async () => {
  const pimLevel = {
    apiType: "PIM",
    rootTemplate: { eClass: "PIMModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      {
        type: "PIMModel",
        references: [{ name: "services", targetType: "ServerlessService", containment: true, many: true }],
      },
      {
        type: "ServerlessService",
        references: [{ name: "channels", targetType: "Topic", containment: true, many: true }],
      },
      { type: "Topic", containedOnly: true },
    ],
  };
  const config = { levelOrder: ["pim"], levels: { pim: pimLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "pim";
  const model = {
    eClass: "PIMModel",
    services: [
      {
        eClass: "ServerlessService",
        id: "service-1",
        ownerTeam: "Operations",
        channels: [{ eClass: "Topic", id: "topic-1", name: "Events", topicName: "events" }],
      },
    ],
    graph: {
      elements: [
        { eClass: "ServerlessService", id: "service-1", ownerTeam: "Operations" },
        { eClass: "Topic", id: "topic-1", name: "Events", topicName: "events" },
      ],
      relationships: [],
    },
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("pim", model, "generated-pim", { skipClientLayout: true });
  const saved = structuredClone(model);
  await serializeGraphAndViewsIntoAsync(saved, { syncView: false });

  assert.equal(saved.services[0].ownerTeam, "Operations");
  assert.deepEqual(saved.services[0].channels, model.services[0].channels);
});

test("detects a removed semantic object as a graph change", async () => {
  const model = {
    eClass: "PIMModel",
    services: [
      {
        eClass: "ServerlessService",
        id: "service-removed-child-owner",
        channels: [{ eClass: "Topic", id: "topic-removed" }],
      },
    ],
    graph: {
      elements: [
        { eClass: "ServerlessService", id: "service-removed-child-owner" },
        { eClass: "Topic", id: "topic-removed" },
      ],
      relationships: [],
    },
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("pim", model, "removed-object-model", { skipClientLayout: true });
  state.graph.elementsById.delete("topic-removed");

  assert.equal(semanticGraphHasChanges("pim", model, state.graph), true);
});

test("preserves non-diagram nested containments after an unrelated generated PIM edit", async () => {
  const pimLevel = {
    apiType: "PIM",
    rootTemplate: { eClass: "PIMModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      {
        type: "PIMModel",
        references: [
          { name: "services", targetType: "ServerlessService", containment: true, many: true },
          { name: "principals", targetType: "Principal", containment: true, many: true },
        ],
      },
      {
        type: "ServerlessService",
        references: [
          { name: "channels", targetType: "Topic", containment: true, many: true },
          { name: "workflows", targetType: "Workflow", containment: true, many: true },
        ],
      },
      {
        type: "Topic",
        containedOnly: true,
        references: [
          { name: "subscriptions", targetType: "Subscription", containment: true, many: true },
        ],
      },
      { type: "Subscription", containedOnly: true },
      {
        type: "Principal",
        references: [
          { name: "permissions", targetType: "Permission", containment: true, many: true },
        ],
      },
      { type: "Permission", containedOnly: true },
      { type: "Workflow", containedOnly: true },
    ],
  };
  const config = { levelOrder: ["pim"], levels: { pim: pimLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "pim";
  const model = {
    eClass: "PIMModel",
    services: [
      {
        eClass: "ServerlessService",
        id: "service-1",
        channels: [
          {
            eClass: "Topic",
            id: "topic-1",
            filteringRequired: true,
            subscriptions: [
              { eClass: "Subscription", id: "subscription-1", filterExpression: "kind = 'A'" },
            ],
          },
        ],
        workflows: [
          { eClass: "Workflow", id: "workflow-1", executionSemantics: "generated" },
        ],
      },
    ],
    principals: [
      {
        eClass: "Principal",
        id: "principal-1",
        privileged: true,
        permissions: [{ eClass: "Permission", id: "permission-1", action: "approve" }],
      },
    ],
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("pim", model, "generated-pim");
  state.graph.elementsById.get("workflow-1").executionSemantics = "manually refined";
  // A contained semantic helper can also have a relationship projection for a focused view.
  // Saving must still emit one contained EObject, not duplicate the same identity from both
  // runtime indexes (EMF rejects/misplaces duplicate containment identities).
  state.graph.relationshipsById.set(
    "subscription-1",
    structuredClone(state.graph.elementsById.get("subscription-1")),
  );
  state.graph.relationshipsById.set(
    "permission-1",
    structuredClone(state.graph.elementsById.get("permission-1")),
  );

  const saved = structuredClone(model);
  await serializeGraphAndViewsIntoAsync(saved, { syncView: false });

  assert.deepEqual(
    saved.services[0].channels[0].subscriptions.map((item) => item.id),
    ["subscription-1"],
  );
  assert.deepEqual(
    saved.principals[0].permissions.map((item) => item.id),
    ["permission-1"],
  );
  assert.equal(saved.services[0].workflows[0].executionSemantics, "manually refined");
});

test("emits one required PSM policy statement when it also has a relationship projection", async () => {
  const psmLevel = {
    apiType: "PSM",
    rootTemplate: { eClass: "AwsPsmModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      {
        type: "AwsPsmModel",
        references: [{ name: "roles", targetType: "IamRole", containment: true, many: true }],
      },
      {
        type: "IamRole",
        references: [
          {
            name: "assumeRolePolicy",
            targetType: "IamPolicyDocument",
            containment: true,
            many: false,
          },
        ],
      },
      {
        type: "IamPolicyDocument",
        containedOnly: true,
        references: [
          { name: "statements", targetType: "IamStatement", containment: true, many: true },
        ],
      },
      { type: "IamStatement", containedOnly: true },
    ],
  };
  const config = { levelOrder: ["psm"], levels: { psm: psmLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "psm";
  const model = {
    eClass: "AwsPsmModel",
    roles: [
      {
        eClass: "IamRole",
        id: "role-1",
        assumeRolePolicy: {
          eClass: "IamPolicyDocument",
          id: "document-1",
          statements: [{ eClass: "IamStatement", id: "statement-1", actions: ["sts:AssumeRole"] }],
        },
      },
    ],
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("psm", model, "generated-psm");
  state.graph.relationshipsById.set(
    "statement-1",
    structuredClone(state.graph.elementsById.get("statement-1")),
  );

  const saved = structuredClone(model);
  await serializeGraphAndViewsIntoAsync(saved, { syncView: false });

  assert.deepEqual(
    saved.roles[0].assumeRolePolicy.statements.map((item) => item.id),
    ["statement-1"],
  );
});

test("restores scoped required containment from the graph ownership index", async () => {
  const psmLevel = {
    apiType: "PSM",
    rootTemplate: { eClass: "AwsPsmModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      { type: "AwsPsmModel", references: [{ name: "documents", targetType: "IamPolicyDocument", containment: true, many: true }] },
      { type: "IamPolicyDocument", containedOnly: true, references: [{ name: "statements", targetType: "IamStatement", containment: true, many: true }] },
      { type: "IamStatement", containedOnly: true },
    ],
  };
  const config = { levelOrder: ["psm"], levels: { psm: psmLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "psm";
  const model = {
    eClass: "AwsPsmModel",
    documents: [{ eClass: "IamPolicyDocument", id: "document-1", statements: [] }],
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("psm", model, "scoped-psm");
  state.graph.parentByChild.set("statement-1", "document-1");
  state.graph.elementsById.set("statement-1", {
    eClass: "IamStatement",
    id: "statement-1",
    actions: ["sts:AssumeRole"],
  });
  state.graph.relationshipsById.set("statement-1", {
    eClass: "IamStatement",
    id: "statement-1",
    actions: ["sts:AssumeRole"],
  });

  const saved = structuredClone(model);
  const { serializeGraphAndViewsIntoAsync } = await import("../../apps/frontend/js/graph-store.js");
  await serializeGraphAndViewsIntoAsync(saved, { syncView: false });

  assert.deepEqual(saved.documents[0].statements.map((item) => item.id), ["statement-1"]);
  assert.equal(
    saved.documents.filter((item) => item.id === "document-1").length,
    1,
    "A contained document must not also be emitted as a second root EObject.",
  );
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

test("preserves required decision rule expressions during async save", async () => {
  const pimLevel = {
    apiType: "PIM",
    rootTemplate: { eClass: "PIMModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "main" },
    viewDefinitions: [{ id: "main", viewType: "main" }],
    elements: [
      { type: "PIMModel", references: [{ name: "decisionModels", targetType: "DecisionModel", containment: true, many: true }] },
      { type: "DecisionModel", references: [{ name: "rules", targetType: "DecisionRule", containment: true, many: true, required: true }] },
      { type: "DecisionRule", references: [
        { name: "condition", targetType: "Expression", containment: true, many: false, required: true },
        { name: "outcome", targetType: "Expression", containment: true, many: false, required: true },
      ] },
      { type: "Expression" },
    ],
  };
  const config = { levelOrder: ["pim"], levels: { pim: pimLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "pim";
  const model = {
    eClass: "PIMModel",
    decisionModels: [{
      eClass: "DecisionModel",
      id: "decision-model-1",
      rules: [{
        eClass: "DecisionRule",
        id: "decision-rule-1",
        condition: { eClass: "Expression", id: "condition-1", body: "eligible" },
        outcome: { eClass: "Expression", id: "outcome-1", body: "approve" },
      }],
    }],
  };
  const { installGraphAndViews } = await import("../../apps/frontend/js/graph-store.js");
  installGraphAndViews("pim", model, "decision-pim");
  const saved = structuredClone(model);
  await serializeGraphAndViewsIntoAsync(saved, { syncView: false });
  assert.equal(saved.decisionModels[0].rules[0].condition.id, "condition-1");
  assert.equal(saved.decisionModels[0].rules[0].outcome.id, "outcome-1");
});

test("keeps explicit view canvas selections exact instead of expanding Ecore subtypes", async () => {
  const cimLevel = {
    apiType: "CIM",
    rootTemplate: { eClass: "CIMModel" },
    relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
    workbench: { defaultViewDefinitionId: "requirements" },
    viewDefinitions: [
      {
        id: "requirements",
        displayName: "Requirements",
        viewType: "REQUIREMENTS",
        palette: ["Requirement"],
        canvas: ["Requirement"],
      },
    ],
    elements: [
      {
        type: "CIMModel",
        references: [{ name: "requirements", targetType: "Requirement", containment: true, many: true }],
      },
      { type: "Requirement" },
      { type: "ComplianceConstraint", supertypes: ["Requirement"] },
    ],
  };
  const config = { levelOrder: ["cim"], levels: { cim: cimLevel } };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "cim";
  const model = {
    eClass: "CIMModel",
    requirements: [
      { eClass: "Requirement", id: "requirement-1" },
      { eClass: "ComplianceConstraint", id: "compliance-1" },
    ],
  };
  const { installGraphAndViews, selectElementIdsForView } = await import(
    "../../apps/frontend/js/graph-store.js"
  );
  const installed = installGraphAndViews("cim", model, "requirements-model");
  const view = [...installed.views.byId.values()].find((item) => item.definitionId === "requirements");

  assert.ok(view);
  assert.deepEqual(
    selectElementIdsForView(installed.graph, view, "cim"),
    ["requirement-1"],
  );
});
