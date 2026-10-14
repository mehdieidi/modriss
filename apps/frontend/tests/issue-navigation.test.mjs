import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

globalThis.window = {
  location: { origin: "http://localhost" },
  addEventListener() {}, dispatchEvent() {}, setTimeout: () => 0,
};
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const { activateIssueTarget } = await import("../js/issue-navigation.js");
const { materializeActiveView } = await import("../js/view-materializer.js");
const { closeCanvasFocus, locateCanvasTarget, renderDiagramAsync } = await import("../js/canvas.js");
const { autoLayoutCurrentDiagram } = await import("../js/model-ops.js");
const { el } = await import("../js/dom.js");
const {
  activeView,
  persistentActiveViewId,
  saveCurrentTabGraphState,
  serializeRuntimeViews,
  serializeRuntimeViewsAsync,
  serializeGraphAndViewsInto,
  serializeGraphAndViewsIntoAsync,
  setActiveViewId,
} = await import("../js/graph-store.js");

function view(id, elementIds, extra = {}) {
  return {
    id,
    name: id,
    kind: "MAIN",
    nodes: elementIds.map((elementId) => ({ elementId, x: 20, y: 30 })),
    edges: [],
    filters: { elementTypes: [], relationshipKinds: [] },
    hidden: { elementIds: [], relationshipIds: [] },
    ...extra,
  };
}

beforeEach(() => {
  state.activeType = "pim";
  state.modelingConfig.config = {
    version: 1,
    levelOrder: ["pim"],
    levels: {
      pim: {
        rootTemplate: { eClass: "Model" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        elements: [
          { type: "Container", references: [{ name: "children", containment: true, many: true, targetType: "Detail" }] },
          { type: "Detail", containedOnly: true },
          { type: "Node" },
        ],
      },
    },
  };
  state.graph = {
    elementsById: new Map([
      ["parent", { id: "parent", eClass: "Container", name: "Parent" }],
      ["child", { id: "child", eClass: "Detail", name: "Child", __ownerId: "parent" }],
      ["other", { id: "other", eClass: "Node", name: "Other" }],
    ]),
    relationshipsById: new Map(),
    relationshipsBySource: new Map(),
    relationshipsByTarget: new Map(),
    relationshipsByKind: new Map(),
    parentByChild: new Map([["child", "parent"]]),
    containmentByParent: new Map([["parent", new Set(["child"])]]),
    traceLinksById: new Map(),
    assumptionsById: new Map(),
  };
  state.views = {
    byId: new Map([["main", view("main", ["parent", "other"])]]),
    activeViewId: "main",
  };
  state.fragments = { byId: new Map() };
  state.canvasFocusStack = [];
  state.tabs = { pim: {} };
  state.nodesById = new Map();
  state.issueLocateTargetId = null;
  materializeActiveView();
});

function addRelationship(id, extra = {}) {
  const relationship = {
    id, kind: "LINK", sourceElementId: "child", targetElementId: "other", ...extra,
  };
  state.graph.relationshipsById.set(id, relationship);
  for (const [index, key] of [
    [state.graph.relationshipsBySource, relationship.sourceElementId],
    [state.graph.relationshipsByTarget, relationship.targetElementId],
  ]) {
    if (!index.has(key)) index.set(key, new Set());
    index.get(key).add(id);
  }
}

function fakeElement(tagName = "div") {
  const classes = new Set(["hidden"]);
  const attributes = new Map();
  return {
    tagName: tagName.toUpperCase(), children: [], dataset: {},
    style: { setProperty() {} },
    classList: {
      add: (...names) => names.forEach((name) => classes.add(name)),
      remove: (...names) => names.forEach((name) => classes.delete(name)),
      toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name),
      contains: (name) => classes.has(name),
    },
    setAttribute: (name, value) => attributes.set(name, value),
    getAttribute: (name) => attributes.get(name),
    append(...children) { this.children.push(...children); },
    appendChild(child) { this.children.push(child); },
    addEventListener() {}, querySelector: () => null, querySelectorAll: () => [],
  };
}

async function withAttributePanelDom(run) {
  const keys = ["attrPanelBody", "attrPanelTitle", "attrPanelType", "attributePanel", "workspace", "canvasFocusBackBtn"];
  const previous = keys.map((key) => el[key]);
  const previousMatchMedia = window.matchMedia;
  document.documentElement = fakeElement();
  document.createElement = fakeElement;
  document.body = fakeElement();
  document.querySelector = () => null;
  window.matchMedia = () => ({ matches: false });
  keys.forEach((key) => { el[key] = fakeElement(); });
  try {
    await run();
  } finally {
    // Renderer mounting yields to microtasks even when no browser renderer is present.
    await new Promise((resolve) => setImmediate(resolve));
    keys.forEach((key, index) => { el[key] = previous[index]; });
    window.matchMedia = previousMatchMedia;
  }
}

test("relationship navigation selects and highlights the exact target in its existing view", async () => {
  state.views.byId.set("details", view("details", ["child"], {
    kind: "FOCUS", scope: { rootElementId: "parent", scopeKind: "CONTAINER" },
  }));
  await withAttributePanelDom(async () => {
    const result = await locateCanvasTarget({ id: "child" });
    assert.equal(result.viewId, "details");
    assert.equal(result.temporary, false);
    assert.equal(state.selectedNodeId, "child");
    assert.deepEqual([...state.selectedNodeIds], ["child"]);
    assert.equal(state.issueLocateTargetId, "child");
    assert.equal(el.attrPanelTitle.textContent, "Child");
  });
});

test("relationship navigation opens an absent target for editing and discards its view on Back", async () => {
  await withAttributePanelDom(async () => {
    const result = await locateCanvasTarget({ id: "child" });
    assert.equal(result.temporary, true);
    assert.equal(state.selectedNodeId, "child");
    assert.equal(state.issueLocateTargetId, "child");
    assert.deepEqual(state.diagram.nodes.map((node) => node.id), ["child"]);
    assert.equal(el.canvasFocusBackBtn.classList.contains("hidden"), false);
    state.nodesById.get("child").label = "Edited child";
    assert.equal(closeCanvasFocus(), true);
    await renderDiagramAsync({ full: true });
    assert.equal(state.views.activeViewId, "main");
    assert.equal(state.views.byId.has(result.viewId), false);
    assert.equal(state.graph.elementsById.get("child").name, "Edited child");
    assert.equal(state.graph.elementsById.get("child").__ownerId, "parent");
  });
});

test("keeps the current view when the exact target is already visible", () => {
  state.views.byId.set("alternative", view("alternative", ["other"]));
  const result = activateIssueTarget({ id: "other" });
  assert.deepEqual(result, {
    id: "other", kind: "node", viewId: "main", changedView: false, temporary: false,
  });
  assert.equal(state.views.activeViewId, "main");
});

test("finds the child in another view instead of locating its visible parent", () => {
  state.views.byId.set("details", view("details", ["child"], {
    kind: "FOCUS", scope: { rootElementId: "parent", scopeKind: "CONTAINER" },
  }));
  const result = activateIssueTarget({ id: "child", targetIds: ["child", "parent"] });
  assert.equal(result.id, "child");
  assert.equal(result.temporary, false);
  assert.equal(state.views.activeViewId, "details");
  assert.ok(materializeActiveView().nodes.some((node) => node.id === "child"));
});

test("materializes a contained-only target in a temporary view when no view shows it", () => {
  const result = activateIssueTarget({ id: "child", targetIds: ["child", "parent"] });
  assert.equal(result.id, "child");
  assert.equal(result.temporary, true);
  assert.deepEqual(materializeActiveView().nodes.map((node) => node.id), ["child"]);
  assert.equal(state.canvasFocusStack[0].previousViewId, "main");
});

test("checks lazy view selection even when its stored nodes are empty", () => {
  state.views.byId.set("lazy", view("lazy", [], {
    _lazyContent: true, filters: { elementTypes: ["Node"], relationshipKinds: [] },
  }));
  state.views.byId.get("main").hidden.elementIds = ["other"];
  materializeActiveView();
  const result = activateIssueTarget({ id: "other" });
  assert.equal(result.temporary, false);
  assert.equal(state.views.activeViewId, "lazy");
  assert.ok(materializeActiveView().nodes.some((node) => node.id === "other"));
});

test("does not mistake stored but excluded child nodes for visible targets", () => {
  state.views.byId.get("main").nodes.push({ elementId: "child" });
  assert.equal(activateIssueTarget({ id: "child" }).temporary, true);
  assert.deepEqual(materializeActiveView().nodes.map((node) => node.id), ["child"]);
});

test("leaves a hidden target hidden in its permanent view", () => {
  state.views.byId.get("main").hidden.elementIds = ["other"];
  materializeActiveView();
  assert.equal(activateIssueTarget({ id: "other" }).temporary, true);
  assert.deepEqual(materializeActiveView().nodes.map((node) => node.id), ["other"]);
  assert.deepEqual(state.views.byId.get("main").hidden.elementIds, ["other"]);
});

test("uses a temporary view for targets pruned out of generated views", () => {
  state.views.byId.get("main").pruneIsolated = true;
  addRelationship("link", { sourceElementId: "parent", targetElementId: "parent" });
  materializeActiveView();
  assert.equal(activateIssueTarget({ id: "other" }).temporary, true);
});

test("locates a relationship using its exact edge and both endpoints", () => {
  addRelationship("link");
  const result = activateIssueTarget({ id: "link", targetIds: ["link", "parent"] });
  assert.equal(result.kind, "connection");
  assert.equal(result.temporary, true);
  const diagram = materializeActiveView();
  assert.deepEqual(diagram.nodes.map((node) => node.id), ["child", "other"]);
  assert.deepEqual(diagram.connections.map((edge) => edge.id), ["link"]);
});

test("resolves a semantic relationship ID to its canvas edge", () => {
  addRelationship("routing::other", { semanticObjectId: "routing" });
  assert.equal(activateIssueTarget({ id: "routing" }).id, "routing::other");
});

test("does not select an unrelated duplicate edge instead of the target", () => {
  addRelationship("first");
  addRelationship("second");
  activateIssueTarget({ id: "second" });
  assert.deepEqual(materializeActiveView().connections.map((edge) => edge.id), ["second"]);
});

test("Back deletes the temporary view and keeps model edits", () => {
  activateIssueTarget({ id: "child" });
  const temporaryId = state.views.activeViewId;
  materializeActiveView().nodes[0].label = "Fixed child";
  const focus = state.canvasFocusStack.pop();
  assert.ok(setActiveViewId(focus.previousViewId));
  materializeActiveView();
  assert.equal(state.views.byId.has(temporaryId), false);
  assert.equal(state.graph.elementsById.get("child").name, "Fixed child");
  assert.equal(state.graph.elementsById.get("child").__ownerId, "parent");
  assert.equal(state.views.activeViewId, "main");
});

test("shows the canvas Back button and cleans up through its actual handler", async () => {
  document.documentElement = fakeElement();
  document.createElement = fakeElement;
  document.body = fakeElement();
  document.querySelector = () => null;
  el.canvasFocusBackBtn = fakeElement();
  try {
    activateIssueTarget({ id: "child" });
    const temporaryId = state.views.activeViewId;
    materializeActiveView();
    await renderDiagramAsync({ full: true });
    assert.equal(el.canvasFocusBackBtn.classList.contains("hidden"), false);
    // Auto Layout must work locally even when this model has no stored backend record.
    await autoLayoutCurrentDiagram({ progress: false, status: false, busy: false, rethrow: true });
    assert.equal(activeView()._temporaryIssueView, true);
    assert.equal(closeCanvasFocus(), true);
    await renderDiagramAsync({ full: true });
    assert.equal(state.views.byId.has(temporaryId), false);
    assert.equal(state.views.activeViewId, "main");
    assert.equal(el.canvasFocusBackBtn.classList.contains("hidden"), true);
    assert.deepEqual(state.canvasFocusStack, []);
  } finally {
    el.canvasFocusBackBtn = null;
  }
});

test("switching to another view destroys the temporary view and its Back entry", () => {
  state.views.byId.set("another", view("another", ["other"]));
  activateIssueTarget({ id: "child" });
  const temporaryId = state.views.activeViewId;
  materializeActiveView();
  assert.ok(setActiveViewId("another"));
  assert.equal(state.views.byId.has(temporaryId), false);
  assert.deepEqual(state.canvasFocusStack, []);
});

test("temporary navigation preserves Back through a previous container focus", () => {
  const containerView = view("details", ["child"], {
    kind: "FOCUS", scope: { rootElementId: "parent", scopeKind: "CONTAINER" },
  });
  state.views.byId.set("details", containerView);
  setActiveViewId("details");
  state.canvasFocusStack.push({
    typeKey: "pim", focusViewId: "details", previousViewId: "main",
  });
  state.views.byId.get("main").hidden.elementIds = ["other"];
  materializeActiveView();
  activateIssueTarget({ id: "other" });
  const temporaryId = state.views.activeViewId;
  materializeActiveView();
  assert.equal(persistentActiveViewId(), "details");
  assert.equal(persistentActiveViewId({ unwindFocus: true }), "main");
  assert.equal(closeCanvasFocus(), true);
  assert.equal(state.views.byId.has(temporaryId), false);
  assert.equal(state.views.activeViewId, "details");
  assert.equal(state.canvasFocusStack.length, 1);
  assert.equal(closeCanvasFocus(), true);
  assert.equal(state.views.activeViewId, "main");
});

test("repeated Locate reuses the current temporary view", () => {
  activateIssueTarget({ id: "child" });
  const temporaryId = state.views.activeViewId;
  materializeActiveView();
  const result = activateIssueTarget({ id: "child" });
  assert.equal(result.changedView, false);
  assert.equal(state.views.activeViewId, temporaryId);
  assert.equal(state.canvasFocusStack.length, 1);
});

test("locating another absent target replaces the temporary view and returns to the original view", () => {
  activateIssueTarget({ id: "child" });
  const firstTemporaryId = state.views.activeViewId;
  materializeActiveView();
  state.views.byId.get("main").hidden.elementIds = ["other"];
  activateIssueTarget({ id: "other" });
  assert.equal(state.views.byId.has(firstTemporaryId), false);
  assert.equal(activeView()._returnViewId, "main");
  assert.equal(state.canvasFocusStack[0].previousViewId, "main");
});

test("save and tab snapshots exclude temporary views and restore a durable active view", async () => {
  activateIssueTarget({ id: "child" });
  const temporaryId = state.views.activeViewId;
  materializeActiveView();
  for (const views of [serializeRuntimeViews(), await serializeRuntimeViewsAsync()]) {
    assert.equal(views.some((entry) => entry.id === temporaryId), false);
  }
  saveCurrentTabGraphState();
  assert.equal(state.tabs.pim.activeViewId, "main");
  assert.equal(state.tabs.pim.views.some((entry) => entry.id === temporaryId), false);
  for (const root of [
    serializeGraphAndViewsInto({ eClass: "Model" }),
    await serializeGraphAndViewsIntoAsync({ eClass: "Model" }),
  ]) {
    assert.equal(root.activeViewId, "main");
    assert.equal(root.views.some((entry) => entry.id === temporaryId), false);
    assert.ok(root.graph.elements.some((element) => element.id === "child"));
  }
  assert.equal(persistentActiveViewId(), "main");
});

test("missing targets do not create a view or navigate to a guessed parent", () => {
  assert.equal(activateIssueTarget({ id: "missing" }), null);
  assert.equal(state.views.activeViewId, "main");
  assert.equal(state.views.byId.size, 1);
});
