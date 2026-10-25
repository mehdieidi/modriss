import assert from "node:assert/strict";
import { test } from "node:test";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {}, dispatchEvent() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const { applyModelingRuntimeConfig } = await import("../js/config.js");
const { initializeModelingRuntimeState, modelingContainmentEntriesForChildType } = await import("../js/modeling-config-data.js");
const {
  installGraphAndViews, addNodeToGraphAndActiveView, addConnectionToGraphAndActiveView,
  removeElementFromGraph, removeRelationshipFromGraph, selectElementIdsForView, serializeGraphAndViewsInto, serializeGraphAndViewsIntoAsync,
  restoreTabGraphState,
} = await import("../js/graph-store.js");
const { isContainerElement, materializeActiveView } = await import("../js/view-materializer.js");
const { semanticElementsFromRoot, semanticObjectById } = await import("../js/model-utils.js");
const { captureConnectionUndoSnapshot, applyDiagramUndoSnapshot } = await import("../js/undo.js");

function setup(level = "cim", { nested = false, multiTarget = false } = {}) {
  const reference = (name, targetType, many = true) => ({ name, targetType, containment: true, many });
  const config = {
    version: 1, levelOrder: [level], levels: { [level]: {
      modelNameTemplate: "Example",
      rootTemplate: { eClass: "Model", modelLevel: level.toUpperCase() },
      relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
      semanticEdgeObjectRules: [{
        eClass: "Association", matchKinds: ["ASSOCIATION"], sourceFeature: "source", targetFeature: "target",
        rootFeature: "associations", ...(nested ? { ownerType: "Group" } : {}),
      }],
      workbench: { defaultViewDefinitionId: "main" },
      viewDefinitions: [{ id: "main", viewType: "main" }],
      containers: [{ elementType: "Association", palette: ["Multiplicity"], canvas: ["Multiplicity"], relationshipKinds: [] }],
      elements: [
        { type: "Model", references: nested ? [reference("groups", "Group")] : [reference("entities", "Entity"), reference("associations", "Association")] },
        { type: "Group", references: [reference("entities", "Entity"), reference("associations", "Association")] },
        { type: "Entity", visualRole: "node" },
        { type: "Association", relationshipElement: true, visualRole: "relationship", references: [
          reference("sourceMultiplicity", "Multiplicity", false), reference("targetMultiplicity", "Multiplicity", false),
          { name: "source", targetType: "Entity", many: false }, { name: "target", targetType: "Entity", many: multiTarget },
        ] },
        { type: "Multiplicity", containedOnly: true, creatable: true, visualRole: "detail" },
        { type: "LeafRelationship", relationshipElement: true, visualRole: "relationship", references: [{ name: "target", targetType: "Entity" }] },
        { type: "ReadOnlyRelationship", relationshipElement: true, references: [{ ...reference("detail", "Multiplicity", false), readonly: true }] },
      ],
    } },
  };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  applyModelingRuntimeConfig(config);
  state.activeType = level;
  state.tabs = { [level]: {} };
  state.canvasFocusStack = [];
  const contents = {
    entities: [{ id: "a", eClass: "Entity" }, { id: "b", eClass: "Entity" }, { id: "c", eClass: "Entity" }],
    associations: [{
      id: "link", eClass: "Association", source: "a", target: multiTarget ? ["b", "c"] : "b",
      sourceMultiplicity: { id: "source-m", eClass: "Multiplicity", lower: 0, upper: 1 },
    }],
  };
  const model = { id: "root", eClass: "Model", ...(nested ? { groups: [{ id: "group", eClass: "Group", ...contents }] } : contents) };
  installGraphAndViews(level, model, "Example", { skipFragments: true, skipClientLayout: true });
  state.baseModel = model;
  const focus = {
    id: "relationship-focus", name: "Association Contents", kind: "FOCUS", level: level.toUpperCase(),
    scope: { rootElementId: "link", scopeKind: "CONTAINER" },
    canvas: ["Multiplicity"], palette: ["Multiplicity"], nodes: [{ elementId: "source-m", x: 142, y: 265 }], edges: [],
  };
  state.views.byId.set(focus.id, focus);
  state.views.activeViewId = focus.id;
  materializeActiveView();
  return { model, focus };
}

for (const level of ["cim", "pim", "psm"]) {
  test(`${level}: edge containment uses semantic ownership and a contents-only canvas`, () => {
    const { model, focus } = setup(level);
    assert.equal(isContainerElement("Association"), true);
    assert.equal(isContainerElement("LeafRelationship"), false);
    assert.equal(isContainerElement("ReadOnlyRelationship"), false);
    assert.equal(state.graph.elementsById.has("link"), false, "An edge owner must not become a canvas node.");
    assert.equal(state.graph.parentByChild.get("source-m"), "link");
    assert.deepEqual([...state.graph.containmentByParent.get("link")], ["source-m"]);
    assert.equal(semanticElementsFromRoot(level, model).some((element) => element.id === "source-m"), true);
    assert.deepEqual(selectElementIdsForView(state.graph, focus, level), ["source-m"]);
    const diagram = materializeActiveView();
    assert.deepEqual(diagram.nodes.map(({ id, x, y }) => ({ id, x, y })), [{ id: "source-m", x: 142, y: 265 }]);
    assert.deepEqual(diagram.connections, []);
  });
}

for (const nested of [false, true]) {
  for (const asyncSave of [false, true]) {
    test(`${nested ? "nested" : "root"} edge: edited and newly dropped children survive ${asyncSave ? "async" : "sync"} save/reload`, async () => {
      const { model, focus } = setup("cim", { nested });
      const owner = semanticObjectById(state.graph, "link");
      assert.deepEqual(modelingContainmentEntriesForChildType("cim", "Association", "Multiplicity", owner).map((entry) => entry.feature), ["targetMultiplicity"]);
      state.graph.elementsById.get("source-m").upper = 8;
      addNodeToGraphAndActiveView({
        id: "target-m", type: "Multiplicity", label: "Target multiplicity", x: 400, y: 100,
        meta: { lower: 1, upper: -1, __ownerId: "link", __containmentFeature: "targetMultiplicity" },
      });
      assert.equal(owner.targetMultiplicity, "target-m");
      assert.deepEqual(modelingContainmentEntriesForChildType("cim", "Association", "Multiplicity", owner), []);
      const saved = structuredClone(model);
      const serialize = asyncSave ? serializeGraphAndViewsIntoAsync : serializeGraphAndViewsInto;
      await serialize(saved, { syncView: false });
      const relationship = nested ? saved.groups[0].associations[0] : saved.associations[0];
      assert.equal(relationship.sourceMultiplicity.upper, 8);
      assert.equal(relationship.targetMultiplicity.lower, 1);
      assert.equal(relationship.targetMultiplicity.id, "target-m");
      installGraphAndViews("cim", saved, "Example", { skipFragments: true, skipClientLayout: true });
      assert.equal(state.graph.parentByChild.get("target-m"), "link");
      const loadedFocus = state.views.byId.get(focus.id);
      assert.ok(loadedFocus);
      assert.deepEqual(selectElementIdsForView(state.graph, loadedFocus, "cim").sort(), ["source-m", "target-m"]);
      assert.equal(loadedFocus.nodes.find((entry) => entry.elementId === "source-m").x, 142);
    });
  }
}

test("deleting a child releases its singleton feature and permits another drop", () => {
  setup();
  const owner = semanticObjectById(state.graph, "link");
  removeElementFromGraph("source-m");
  assert.equal(owner.sourceMultiplicity, null);
  assert.equal(state.graph.containmentByParent.get("link").has("source-m"), false);
  assert.deepEqual(modelingContainmentEntriesForChildType("cim", "Association", "Multiplicity", owner).map((entry) => entry.feature), ["sourceMultiplicity", "targetMultiplicity"]);
});

test("saving a partially populated relationship keeps its other same-type slot empty", () => {
  const { model } = setup();
  state.graph.elementsById.get("source-m").upper = 7;
  const saved = structuredClone(model);
  serializeGraphAndViewsInto(saved, { syncView: false });
  assert.equal(saved.associations[0].sourceMultiplicity.id, "source-m");
  assert.equal(saved.associations[0].targetMultiplicity, null);
});

test("saved relationship contents views adopt current CVS settings and retain geometry", () => {
  const { model, focus } = setup();
  const saved = structuredClone(model);
  serializeGraphAndViewsInto(saved, { syncView: false });
  state.modelingConfig.config.levels.cim.containers[0].canvas = [];
  state.modelingConfig.config.levels.cim.containers[0].palette = [];
  installGraphAndViews("cim", saved, "Example", { skipFragments: true, skipClientLayout: true });
  const updated = state.views.byId.get(focus.id);
  assert.deepEqual(updated.canvas, []);
  assert.deepEqual(updated.palette, []);
  assert.deepEqual(selectElementIdsForView(state.graph, updated, "cim"), []);
  assert.equal(updated.nodes.find((entry) => entry.elementId === "source-m").x, 142);
});

test("many-target edge projections share one semantic contents scope", () => {
  const { model, focus } = setup("cim", { multiTarget: true });
  assert.equal(semanticObjectById(state.graph, "link").semanticObjectId, "link");
  assert.deepEqual(selectElementIdsForView(state.graph, focus, "cim"), ["source-m"]);
  state.graph.elementsById.get("source-m").upper = 12;
  const saved = structuredClone(model);
  serializeGraphAndViewsInto(saved, { syncView: false });
  assert.equal(saved.associations.length, 1);
  assert.equal(saved.associations[0].sourceMultiplicity.upper, 12);
  assert.deepEqual(saved.associations[0].target.sort(), ["b", "c"]);
});

test("a newly drawn relationship can own dropped details before its first save", () => {
  const { model } = setup();
  addConnectionToGraphAndActiveView({ id: "new-link", kind: "ASSOCIATION", sourceId: "a", targetId: "b" });
  addNodeToGraphAndActiveView({
    id: "new-m", type: "Multiplicity", label: "Multiplicity", x: 20, y: 30,
    meta: { lower: 2, upper: 4, __ownerId: "new-link", __containmentFeature: "sourceMultiplicity" },
  });
  const saved = structuredClone(model);
  serializeGraphAndViewsInto(saved, { syncView: false });
  assert.equal(saved.associations.find((entry) => entry.id === "new-link").sourceMultiplicity.id, "new-m");
});

test("empty serialized containment values do not occupy singleton slots", () => {
  setup();
  const owner = { id: "empty-link", eClass: "Association", sourceMultiplicity: [], targetMultiplicity: {} };
  assert.deepEqual(
    modelingContainmentEntriesForChildType("cim", "Association", "Multiplicity", owner).map((entry) => entry.feature),
    ["sourceMultiplicity", "targetMultiplicity"],
  );
});

test("a relationship stored in the graph projection still exposes its embedded children", () => {
  const { model } = setup();
  const projected = structuredClone(model);
  projected.graph = {
    elements: model.entities,
    relationships: [{ ...model.associations[0], kind: "ASSOCIATION", targetMultiplicity: { id: "target-m", eClass: "Multiplicity", lower: 0, upper: 5 } }],
  };
  delete projected.associations;
  installGraphAndViews("cim", projected, "Example", { skipFragments: true, skipClientLayout: true });
  const view = { id: "projected-focus", kind: "FOCUS", scope: { rootElementId: "link", scopeKind: "CONTAINER" }, canvas: ["Multiplicity"], nodes: [], edges: [] };
  assert.deepEqual(selectElementIdsForView(state.graph, view, "cim").sort(), ["source-m", "target-m"]);
});

test("a relationship's explicit containment IDs index projected children under their owner", () => {
  const { model } = setup();
  const projected = structuredClone(model);
  projected.associations[0].sourceMultiplicity = "source-m";
  projected.graph = { elements: [{ id: "source-m", eClass: "Multiplicity", lower: 0, upper: 1 }], relationships: [] };
  installGraphAndViews("cim", projected, "Example", { skipFragments: true, skipClientLayout: true });
  assert.equal(state.graph.parentByChild.get("source-m"), "link");
  assert.equal(state.graph.elementsById.get("source-m").__containmentFeature, "sourceMultiplicity");
});

test("restoring a saved tab indexes relationship contents even without child ownership metadata", () => {
  const { model, focus } = setup();
  state.tabs.cim.graph = {
    elements: [...model.entities, { id: "source-m", eClass: "Multiplicity", lower: 1, upper: 1 }],
    relationships: [{ ...model.associations[0], kind: "ASSOCIATION", sourceMultiplicity: "source-m" }],
  };
  state.tabs.cim.views = [focus];
  state.tabs.cim.activeViewId = focus.id;
  restoreTabGraphState("cim");
  assert.deepEqual(selectElementIdsForView(state.graph, state.views.byId.get(focus.id), "cim"), ["source-m"]);
});

test("empty stored containments do not create phantom children while loading", () => {
  const { model } = setup();
  model.associations[0].sourceMultiplicity = [];
  model.associations[0].targetMultiplicity = {};
  installGraphAndViews("cim", model, "Example", { skipFragments: true, skipClientLayout: true });
  assert.equal([...state.graph.elementsById.values()].filter((element) => element.eClass === "Multiplicity").length, 0);
});

test("deleting an edge removes its contained children and undo restores them", () => {
  setup();
  const snapshot = captureConnectionUndoSnapshot({ id: "link", kind: "ASSOCIATION", sourceId: "a", targetId: "b" });
  removeRelationshipFromGraph("link");
  assert.equal(state.graph.elementsById.has("source-m"), false);
  assert.equal(state.graph.parentByChild.has("source-m"), false);
  assert.equal(applyDiagramUndoSnapshot(snapshot), true);
  assert.ok(state.graph.relationshipsById.get("link"));
  assert.equal(state.graph.elementsById.get("source-m").upper, 1);
  assert.equal(state.graph.parentByChild.get("source-m"), "link");
});

test("removing one projection retains contents until the last edge is removed", () => {
  setup("cim", { multiTarget: true });
  removeRelationshipFromGraph("link::b");
  assert.ok(state.graph.elementsById.get("source-m"));
  removeRelationshipFromGraph("link::c");
  assert.equal(state.graph.elementsById.has("source-m"), false);
});
